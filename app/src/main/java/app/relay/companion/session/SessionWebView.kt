package app.relay.companion.session

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.relay.companion.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

const val WEB_CLIENT_URL = "https://web.whatsapp.com/"

private const val CHROME_MAJOR = "142"
private const val CHROME_FULL = "142.0.7444.59"

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/$CHROME_FULL Safari/537.36"

/**
 * web.whatsapp.com serves a dead-end "open this on your computer" page at /mobile/
 * unless the client looks like desktop Chrome. The desktop [DESKTOP_UA] does the
 * heavy lifting; this script only keeps the JS-visible identity consistent with it.
 *
 * Deliberately narrow: overriding `matchMedia` or the page's viewport breaks
 * WhatsApp's own responsive layout, which is what hid the login QR before.
 */
private val DESKTOP_SPOOF_JS = """
(function() {
  var brands = [
    {brand: 'Not=A?Brand', version: '8'},
    {brand: 'Chromium', version: '$CHROME_MAJOR'},
    {brand: 'Google Chrome', version: '$CHROME_MAJOR'}
  ];
  var fullVersionList = [
    {brand: 'Not=A?Brand', version: '8.0.0.0'},
    {brand: 'Chromium', version: '$CHROME_FULL'},
    {brand: 'Google Chrome', version: '$CHROME_FULL'}
  ];
  var uaData = {
    brands: brands,
    mobile: false,
    platform: 'Windows',
    getHighEntropyValues: function() {
      return Promise.resolve({
        architecture: 'x86',
        bitness: '64',
        brands: brands,
        fullVersionList: fullVersionList,
        mobile: false,
        model: '',
        platform: 'Windows',
        platformVersion: '15.0.0',
        uaFullVersion: '$CHROME_FULL',
        wow64: false
      });
    },
    toJSON: function() {
      return {brands: brands, mobile: false, platform: 'Windows'};
    }
  };
  try { Object.defineProperty(navigator, 'userAgentData', {get: function() { return uaData; }, configurable: true}); } catch (e) {}
  try { Object.defineProperty(navigator, 'platform', {get: function() { return 'Win32'; }, configurable: true}); } catch (e) {}
  try { Object.defineProperty(navigator, 'vendor', {get: function() { return 'Google Inc.'; }, configurable: true}); } catch (e) {}
  try { Object.defineProperty(navigator, 'maxTouchPoints', {get: function() { return 0; }, configurable: true}); } catch (e) {}
})();
""".trimIndent()

/**
 * After a successful scan, WhatsApp Web mounts a desktop app shell that sizes
 * `#app` as `height: 100%` of `html`. In Android WebView that percentage collapses
 * to 0, so the chat list is in the DOM but clipped to a blank grey screen.
 * `position: fixed; inset: 0` sizes against the actual viewport instead.
 */
private val LAYOUT_FIX_JS = """
(function() {
  var id = 'relay-layout-fix';
  var css = '#app{position:fixed !important;top:0 !important;right:0 !important;bottom:0 !important;left:0 !important;width:auto !important;height:auto !important;min-height:100vh !important;overflow:hidden !important;}html,body{height:100vh !important;min-height:100vh !important;width:100% !important;max-width:100% !important;}.two{min-width:0 !important;width:100% !important;height:100% !important;min-height:100% !important;}';
  function viewport() {
    var m = document.querySelector('meta[name="viewport"]');
    if (!m) {
      m = document.createElement('meta');
      m.setAttribute('name', 'viewport');
      (document.head || document.documentElement).appendChild(m);
    }
    m.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no');
  }
  function apply() {
    viewport();
    var s = document.getElementById(id);
    if (!s) {
      s = document.createElement('style');
      s.id = id;
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = css;
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})();
""".trimIndent()

/**
 * Desktop identity stays (needed to link). Layout is list OR thread, never both
 * stacked. Do not set flex-direction:column — that is what crushed the desktop pane.
 */
private val PHONE_UI_JS = """
(function() {
  var id = 'relay-phone-ui';
  var last = 0;
  var baseCss = [
    'html,body,#app{width:100% !important;max-width:100% !important;}',
    '#app > div > header,[data-testid="chat-nav"]{display:none !important;}',
    '#app [data-icon="laptop"],#app [data-testid="download-promo"]{display:none !important;}',
    '.two,#app > div{min-width:0 !important;max-width:100% !important;}'
  ].join('');
  var listCss = baseCss + '#pane-side,#side{display:flex !important;width:100% !important;max-width:100% !important;flex:1 1 100% !important;min-width:0 !important;}#main{display:none !important;}';
  var threadCss = baseCss + '#pane-side,#side{display:none !important;}#main{display:flex !important;position:fixed !important;inset:0 !important;width:100% !important;height:100% !important;z-index:20 !important;}';
  function conversationOpen() {
    var main = document.getElementById('main');
    if (!main) return false;
    return !!main.querySelector('footer');
  }
  function hidePromos() {
    var nodes = document.querySelectorAll('[role="dialog"],[data-animate-modal-popup],[data-animate-modal-backdrop]');
    for (var i = 0; i < nodes.length; i++) {
      var t = (nodes[i].innerText || '').slice(0, 160);
      if (/whatsapp for windows|download the app|get the app/i.test(t)) {
        nodes[i].style.setProperty('display','none','important');
      }
    }
  }
  function apply() {
    var now = Date.now();
    if (now - last < 200) return;
    last = now;
    var s = document.getElementById(id);
    if (!s) {
      s = document.createElement('style');
      s.id = id;
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = conversationOpen() ? threadCss : listCss;
    hidePromos();
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
  document.addEventListener('click', function() { setTimeout(apply, 50); }, true);
  if (window.__relayPhoneUi) return;
  window.__relayPhoneUi = true;
  var obs = new MutationObserver(function() { apply(); });
  obs.observe(document.documentElement, {childList:true, subtree:true});
})();
""".trimIndent()

/**
 * Reads the login QR out of the page. WhatsApp keeps the payload the phone has to
 * read in the `data-ref` attribute of the QR container and draws the same string
 * into a square canvas, so `data-ref` is preferred (it re-encodes crisply at any
 * size) with the rendered canvas as a fallback.
 */
private const val LINK_PROBE_JS = """
(function() {
  function out(o) { return JSON.stringify(o); }
  var linked = document.querySelector('#pane-side') ||
               document.querySelector('[aria-label="Chat list"]') ||
               document.querySelector('[data-testid="chat-list"]');
  if (linked) return out({state: 'linked'});
  var refEl = document.querySelector('[data-ref]');
  var ref = refEl ? refEl.getAttribute('data-ref') : null;
  var best = null;
  var canvases = document.querySelectorAll('canvas');
  for (var i = 0; i < canvases.length; i++) {
    var c = canvases[i];
    var w = c.width, h = c.height;
    if (w >= 120 && h >= 120 && Math.abs(w - h) <= 8) {
      if (!best || w > best.width) best = c;
    }
  }
  if (ref) return out({state: 'qr', ref: ref});
  if (best) {
    var png = null;
    try { png = best.toDataURL('image/png'); } catch (e) { png = null; }
    if (png) return out({state: 'qr', png: png});
  }
  return out({state: 'waiting'});
})();
"""

class SessionCallbacks {
    var onFileChooser: ((ValueCallback<Array<Uri>>?, Array<String>?) -> Boolean)? = null
    var onPermissionRequest: ((PermissionRequest) -> Unit)? = null
    var onTitle: ((String) -> Unit)? = null
}

/** What web.whatsapp.com is currently asking for. */
sealed interface LinkState {
    /** Still loading, or already past the QR step (syncing, phone-number login, chats). */
    data object None : LinkState

    /** A login QR is on screen. [payload] re-encodes natively; [pngBase64] is the raw canvas. */
    data class Qr(val payload: String?, val pngBase64: String?) : LinkState

    /** Chat list is up, so this session is linked. */
    data object Linked : LinkState
}

private const val LOG_TAG = "RelaySession"

class SessionController(context: Context) {
    private val appContext = context.applicationContext
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()
    private val _pageReady = MutableStateFlow(false)
    val pageReady: StateFlow<Boolean> = _pageReady.asStateFlow()
    private val _linkState = MutableStateFlow<LinkState>(LinkState.None)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()
    val callbacks = SessionCallbacks()
    private var bouncedToWebClient = false

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(appContext).apply {
        setBackgroundColor(Color.WHITE)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        // WhatsApp Web ships `width=device-width` and lays its login QR out responsively,
        // so the page's own viewport is honoured as-is. Forcing a desktop-width viewport
        // or an initial scale here parks the centred QR off-screen.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        // Ignore the system font scale; at large scales WhatsApp's layout pushes the QR away.
        settings.textZoom = 100
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = DESKTOP_UA
        settings.setSupportMultipleWindows(false)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            settings.safeBrowsingEnabled = true
        }
        applyDesktopClientHints(settings)
        if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        isFocusable = true
        isFocusableInTouchMode = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, DESKTOP_SPOOF_JS, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(this, LAYOUT_FIX_JS, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(this, PHONE_UI_JS, setOf("*"))
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val uri = request.url
                if (shouldBounceToWebClient(uri)) {
                    if (!bouncedToWebClient) {
                        bouncedToWebClient = true
                        view.loadUrl(WEB_CLIENT_URL)
                    }
                    return true
                }
                val host = uri.host.orEmpty()
                val scheme = uri.scheme.orEmpty()
                if (scheme == "blob" || scheme == "data") return false
                val allowedWeb =
                    host.endsWith("whatsapp.com") ||
                        host.endsWith("whatsapp.net") ||
                        host.endsWith("wa.me") ||
                        host.endsWith("facebook.com") ||
                        host.endsWith("fbcdn.net")
                if (allowedWeb && (scheme == "https" || scheme == "http")) {
                    return false
                }
                return try {
                    appContext.startActivity(
                        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                _pageReady.value = false
                _linkState.value = LinkState.None
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view?.evaluateJavascript(DESKTOP_SPOOF_JS, null)
                    view?.evaluateJavascript(LAYOUT_FIX_JS, null)
                    view?.evaluateJavascript(PHONE_UI_JS, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(LOG_TAG, "loaded $url")
                val uri = url?.toUri()
                if (uri != null && shouldBounceToWebClient(uri)) {
                    Log.w(LOG_TAG, "served the mobile dead-end page, retrying as desktop")
                    if (!bouncedToWebClient) {
                        bouncedToWebClient = true
                        view?.loadUrl(WEB_CLIENT_URL)
                    }
                    return
                }
                _progress.value = 100
                _pageReady.value = true
                view?.evaluateJavascript(LAYOUT_FIX_JS, null)
                view?.evaluateJavascript(PHONE_UI_JS, null)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    _pageReady.value = true
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _progress.value = newProgress
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.w(LOG_TAG, "page error: ${message.message()}")
                }
                return true
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { callbacks.onTitle?.invoke(it) }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val handler = callbacks.onPermissionRequest
                if (handler != null) {
                    handler(request)
                } else {
                    request.deny()
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                val accept = fileChooserParams?.acceptTypes
                return callbacks.onFileChooser?.invoke(filePathCallback, accept) == true
            }
        }

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(appContext, url, userAgent, contentDisposition, mimeType)
        }
    }

    fun loadHome() {
        if (webView.url.isNullOrBlank()) {
            bouncedToWebClient = false
            webView.loadUrl(WEB_CLIENT_URL)
        }
    }

    fun goBack(): Boolean {
        return if (webView.canGoBack()) {
            webView.goBack()
            true
        } else {
            false
        }
    }

    fun reload() {
        webView.reload()
    }

    /**
     * Reads the login QR currently on the page into [linkState]. WhatsApp rotates the
     * payload roughly every 20 seconds, so callers poll this while the QR is up.
     */
    suspend fun refreshLinkState() {
        val raw = withTimeoutOrNull(2_000) { evaluate(LINK_PROBE_JS) } ?: return
        val json = runCatching {
            (JSONTokener(raw).nextValue() as? String)?.let { JSONObject(it) }
        }.getOrNull() ?: return
        _linkState.value = when (json.optString("state")) {
            "linked" -> {
                webView.evaluateJavascript(LAYOUT_FIX_JS, null)
                webView.evaluateJavascript(PHONE_UI_JS, null)
                LinkState.Linked
            }
            "qr" -> LinkState.Qr(
                payload = json.optString("ref").ifBlank { null },
                pngBase64 = json.optString("png")
                    .substringAfter("base64,", "")
                    .ifBlank { null },
            )
            else -> LinkState.None
        }
    }

    private suspend fun evaluate(script: String): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            webView.evaluateJavascript(script) { value ->
                if (cont.isActive) cont.resume(value)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    fun signOut() {
        webView.stopLoading()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()
        bouncedToWebClient = false
        _pageReady.value = false
        _linkState.value = LinkState.None
        webView.loadUrl(WEB_CLIENT_URL)
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    private fun applyDesktopClientHints(settings: WebSettings) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
        val brands = listOf(
            brand("Not=A?Brand", "8", "8.0.0.0"),
            brand("Chromium", CHROME_MAJOR, CHROME_FULL),
            brand("Google Chrome", CHROME_MAJOR, CHROME_FULL),
        )
        val metadata = UserAgentMetadata.Builder()
            .setPlatform("Windows")
            .setPlatformVersion("15.0.0")
            .setArchitecture("x86")
            .setMobile(false)
            .setModel("")
            .setBitness(64)
            .setWow64(false)
            .setFullVersion(CHROME_FULL)
            .setBrandVersionList(brands)
            .build()
        WebSettingsCompat.setUserAgentMetadata(settings, metadata)
    }

    private fun brand(name: String, major: String, full: String): UserAgentMetadata.BrandVersion =
        UserAgentMetadata.BrandVersion.Builder()
            .setBrand(name)
            .setMajorVersion(major)
            .setFullVersion(full)
            .build()

    private fun shouldBounceToWebClient(uri: Uri): Boolean {
        val host = uri.host.orEmpty().lowercase()
        // /mobile/ is the "open this on a computer" dead end, and it means the desktop
        // identity did not stick — retrying the root is the only way back to the QR.
        if (host == "web.whatsapp.com") {
            return uri.path.orEmpty().lowercase().startsWith("/mobile")
        }
        if (host == "www.whatsapp.com" || host == "whatsapp.com") {
            val path = uri.path.orEmpty().lowercase()
            return path.isEmpty() || path == "/" || path.startsWith("/download") ||
                path.startsWith("/android") || path.startsWith("/mobile")
        }
        return false
    }
}

fun enqueueDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
) {
    try {
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(url.toUri()).apply {
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent ?: DESKTOP_UA)
            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrBlank()) {
                addRequestHeader("Cookie", cookies)
            }
            setTitle(name)
            setDescription(context.getString(R.string.download_started))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        context.getSystemService<DownloadManager>()?.enqueue(request)
        Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, R.string.generic_error, Toast.LENGTH_SHORT).show()
    }
}
