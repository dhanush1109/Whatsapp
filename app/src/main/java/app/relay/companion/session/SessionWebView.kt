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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import app.relay.companion.BuildConfig
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
 * Deliberately narrow: do not override `matchMedia`. The layout script owns the
 * viewport so WhatsApp lays out at a desktop width and the WebView scales it.
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
 * WhatsApp Web sizes `#app` as `height: 100%` of `html`, which collapses to 0 in
 * Android WebView. Pin `#app` to the viewport, force an 800px layout width so the
 * desktop shell does not crush into phone CSS pixels, and hide the Windows promo.
 * The WebView then scales that desktop page to fit (`loadWithOverviewMode`).
 */
private val LAYOUT_FIX_JS = """
(function() {
  var id = 'relay-layout-fix';
  var css = [
    '#app{position:fixed !important;inset:0 !important;width:auto !important;height:auto !important;overflow:hidden !important;}',
    'html,body{height:100% !important;min-height:100% !important;}',
    '#app [data-icon="laptop"],#app [data-testid="download-promo"]{display:none !important;}',
    '.two > header,#pane-side > header,#side > header,[data-relay="pane-wrap"] > header{display:none !important;}',
    '.two > [data-relay="main-wrap"],.two > [data-relay="pane-wrap"]{transform:none !important;min-width:0 !important;max-width:none !important;left:0 !important;width:100% !important;flex:1 1 100% !important;}',
    'html body.relay-chat .two > [data-relay="main-wrap"]{flex:1 1 100% !important;width:100% !important;height:100% !important;transform:none !important;overflow:hidden !important;}',
    'html body.relay-chat #main{zoom:2.25 !important;transform:none !important;flex:none !important;position:fixed !important;top:0 !important;left:0 !important;right:auto !important;bottom:auto !important;width:100% !important;height:100% !important;min-width:0 !important;max-width:none !important;}',
    'html body.relay-chat #main [role="button"],html body.relay-chat #main button{min-width:0 !important;}',
    'body.relay-chat .two > *:not([data-relay="main-wrap"]):not(#wds-toast-container){display:none !important;}',
    'body.relay-list [data-relay="pane-wrap"]{flex:1 1 100% !important;width:100% !important;}',
    'body.relay-list .two > *:not([data-relay="pane-wrap"]):not(#wds-toast-container){display:none !important;}',
    '#pane-side,#main{position:relative !important;left:auto !important;width:100% !important;min-width:0 !important;max-width:none !important;flex:1 1 100% !important;}',
    '#app,#app *{overscroll-behavior-x:none !important;}',
    'html,body,#app{overflow-x:hidden !important;}',
    'body.relay-list #pane-side [role="listitem"],body.relay-list #pane-side [role="row"],body.relay-list #pane-side [data-testid="cell-frame-container"],body.relay-list #pane-side [tabindex="-1"]{padding-top:12px !important;padding-bottom:12px !important;box-sizing:border-box !important;}',
    'body.relay-list #pane-side [data-testid="cell-frame-container"]{min-height:80px !important;}',
    // Dark theme: WhatsApp Web sets the .dark body class from prefers-color-scheme
    // (answered by our matchMedia override), but leaves its surface/text CSS custom
    // properties on the light values. Remap the key WDS tokens to WA's dark palette
    // so panels and text are legible in dark mode.
    'html.relay-dark,html.relay-dark body{color-scheme:dark !important;--panel-background:#111b21 !important;--panel-background-deep:#0b141a !important;--panel-input-background:#2a3942 !important;--picker-background:#233138 !important;--app-background:#0b141a !important;--app-background-deeper:#000000 !important;--avatar-background:#6a7175 !important;--icon-in-cell-frame-background:#202c33 !important;--navbar-background:#202c33 !important;--WDS-surface-default:#111b21 !important;--WDS-surface-default-RGB:17,27,33 !important;--WDS-surface-elevated-default:#202c33 !important;--WDS-surface-elevated-emphasized:#2a3942 !important;--WDS-background-wash-plain:#0b141a !important;--WDS-background-wash-plain-RGB:11,20,26 !important;--WDS-background-elevated-wash-plain-RGB:32,44,51 !important;--WDS-systems-chat-surface-composer:#202c33 !important;--WDS-systems-chat-surface-composer-RGB:32,44,51 !important;--WDS-systems-chat-surface-tray:#202c33 !important;--WDS-systems-chat-background-wallpaper:#0b141a !important;--WDS-systems-bubble-surface-incoming:#202c33 !important;--WDS-systems-bubble-surface-outgoing:#005c4b !important;--WDS-components-surface-nav-bar:#202c33 !important;--WDS-components-profile-photo-surface-gray:#2a3942 !important;--button-round-background-inverted:#2a3942 !important;--product-placeholder-background:#202c33 !important;--bot-command-pill-background-color:#202c33 !important;}',
    'html.relay-dark,html.relay-dark body{--primary:#e9edef !important;--primary-strong:#e9edef !important;--primary-stronger:#e9edef !important;--primary-strong-rgb:233,237,239 !important;--primary-stronger-rgb:233,237,239 !important;--message-primary:#e9edef !important;--message-primary-rgb:233,237,239 !important;--WDS-content-default:#E9EDEF !important;--WDS-content-action-default:#E9EDEF !important;--text-primary-strong:#e9edef !important;--modal-title:#e9edef !important;--status-link-preview-title:#e9edef !important;--secondary:#8696a0 !important;--secondary-lighter:#8696a0 !important;--typography-sub-title:#8696a0 !important;--labels-icon:rgba(233,237,239,.6) !important;--block-quote-text:rgba(233,237,239,.7) !important;--WDS-systems-bubble-surface-system:rgba(32,44,51,.92) !important;--WDS-systems-bubble-surface-system-RGB:32,44,51 !important;--system-message-background-color:rgba(32,44,51,.92) !important;}'
  ].join('');
  function viewport() {
    var m = document.querySelector('meta[name="viewport"]');
    if (!m) {
      m = document.createElement('meta');
      m.setAttribute('name', 'viewport');
      (document.head || document.documentElement).appendChild(m);
    }
    m.setAttribute('content', 'width=800, initial-scale=1, maximum-scale=4, user-scalable=yes');
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
  function tag(el, name){ if (el) el.setAttribute('data-relay', name); }
  function paneState() {
    var b = document.body; if (!b) return;
    var main = document.querySelector('#main');
    var pane = document.querySelector('#pane-side');
    var row = document.querySelector('.two');
    tag(row, 'row');
    function wrapperOf(node) {
      var e = node;
      while (e && e.parentElement !== row) e = e.parentElement;
      return (e && e.parentElement === row) ? e : null;
    }
    var pw = pane ? wrapperOf(pane) : null;
    var mw = main ? wrapperOf(main) : null;
    if (!mw && pw && pw.nextElementSibling) mw = pw.nextElementSibling;
    tag(pw, 'pane-wrap');
    tag(mw, 'main-wrap');
    var inChat = !!(main && (
      main.querySelector('footer [contenteditable="true"]') ||
      main.querySelector('[data-testid="conversation-compose-box-input"]') ||
      main.querySelector('header img')
    ));
    b.classList.toggle('relay-chat', inChat);
    b.classList.toggle('relay-list', !inChat);
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
    hidePromos();
    paneState();
    if (!window.__relayPaneObs) {
      window.__relayPaneObs = new MutationObserver(function(){
        if (window.__relayPaneRaf) return;
        window.__relayPaneRaf = requestAnimationFrame(function(){
          window.__relayPaneRaf = 0;
          paneState();
        });
      });
      window.__relayPaneObs.observe(document.documentElement, {childList:true, subtree:true});
    }
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})();
""".trimIndent()

/**
 * Runs at document-start (before WhatsApp Web boots). WhatsApp Web themes itself
 * off the prefers-color-scheme media query, and Android WebView won't reliably
 * report dark for an embedded view. So we override matchMedia to answer
 * prefers-color-scheme from a persisted Relay flag, letting WA switch to its own
 * fully-native dark theme (correct colors, not a wash-out invert).
 */
private val THEME_BOOTSTRAP_JS = """
(function(){
  var dark = false;
  try { dark = localStorage.getItem('relay-dark') === '1'; } catch (e) {}
  var h = document.documentElement;
  if (h && dark) h.classList.add('relay-dark');
  if (window.__relayMMPatched) return;
  window.__relayMMPatched = true;
  function relayDark(){
    try { return localStorage.getItem('relay-dark') === '1'; } catch (e) { return false; }
  }
  var orig = window.matchMedia ? window.matchMedia.bind(window) : null;
  if (!orig) return;
  window.matchMedia = function(query){
    var mql = orig(query);
    if (typeof query === 'string' && query.indexOf('prefers-color-scheme') !== -1) {
      var wantsDark = query.indexOf('dark') !== -1;
      var wantsLight = query.indexOf('light') !== -1;
      try {
        Object.defineProperty(mql, 'matches', {
          configurable: true,
          get: function(){
            var d = relayDark();
            if (wantsDark) return d;
            if (wantsLight) return !d;
            return false;
          }
        });
      } catch (e) {}
    }
    return mql;
  };
})();
""".trimIndent()

private fun themeJs(dark: Boolean): String {
    val flag = if (dark) "true" else "false"
    return """
(function(){
  var dark = $flag;
  try { localStorage.setItem('relay-dark', dark ? '1' : '0'); } catch (e) {}
  var h = document.documentElement;
  if (h) h.classList.toggle('relay-dark', dark);
  try {
    var mq = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)');
    if (mq && typeof mq.dispatchEvent === 'function') {
      var ev;
      try { ev = new MediaQueryListEvent('change', { matches: dark, media: '(prefers-color-scheme: dark)' }); }
      catch (e) { ev = new Event('change'); }
      mq.dispatchEvent(ev);
    }
  } catch (e) {}
})();
""".trimIndent()
}

/**
 * Keep the focused compose box above the Android keyboard. WhatsApp Web is a
 * desktop layout: when the WebView shrinks, the visual viewport changes but
 * the focused contenteditable is not scrolled into view on its own.
 */
private val KEYBOARD_JS = """
(function() {
  if (window.__relayKeyboard) return;
  window.__relayKeyboard = true;
  function isEditable(el) {
    if (!el || el === document.body || el === document.documentElement) return false;
    if (el.isContentEditable) return true;
    var tag = (el.tagName || '').toLowerCase();
    return tag === 'textarea' || tag === 'input';
  }
  function composer() {
    var a = document.activeElement;
    if (isEditable(a)) return a;
    return document.querySelector('#main footer [contenteditable="true"]')
      || document.querySelector('#main [contenteditable="true"]')
      || document.querySelector('[data-testid="conversation-compose-box-input"]')
      || document.querySelector('footer [contenteditable="true"]');
  }
  function reveal() {
    var el = composer();
    var vv = window.visualViewport;
    var app = document.getElementById('app');
    var overlap = 0;
    if (vv) overlap = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
    if (app) app.style.setProperty('bottom', overlap + 'px', 'important');
    if (!el) return;
    try { el.scrollIntoView({block: 'end', inline: 'nearest'}); } catch (e) {
      try { el.scrollIntoView(false); } catch (e2) {}
    }
  }
  window.__relayRevealComposer = reveal;
  document.addEventListener('focusin', function(e) {
    if (isEditable(e.target)) setTimeout(reveal, 50);
  }, true);
  window.addEventListener('resize', function() { setTimeout(reveal, 50); });
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', function() { setTimeout(reveal, 50); });
    window.visualViewport.addEventListener('scroll', function() { setTimeout(reveal, 50); });
  }
})();
""".trimIndent()

private fun clickJs(kind: String): String = """
(function() {
  var kind = '$kind';
  function click(el) {
    if (!el) return false;
    var btn = el.closest('button') || el.closest('[role="button"]') || el;
    btn.click();
    return true;
  }
  function byLabel(re) {
    var nodes = document.querySelectorAll('button, [role="button"], [data-tab], [aria-label], [title]');
    for (var i = 0; i < nodes.length; i++) {
      var a = (nodes[i].getAttribute('aria-label') || '') + ' ' + (nodes[i].getAttribute('title') || '');
      if (re.test(a)) return click(nodes[i]);
    }
    var icons = document.querySelectorAll('[data-icon]');
    for (var j = 0; j < icons.length; j++) {
      var name = icons[j].getAttribute('data-icon') || '';
      if (re.test(name)) return click(icons[j]);
    }
    return false;
  }
  if (kind === 'search') {
    return byLabel(/search/i);
  }
  if (kind === 'new-chat') {
    return byLabel(/new chat|new-chat|new-chat-outline/i);
  }
  return false;
})();
""".trimIndent()

/**
 * Closes the open conversation back to the chat list. WhatsApp Web marks an
 * open thread with `#main`; the mobile back-to-list control lives in its
 * header. Escape is the desktop fallback when that button is not in the DOM.
 */
private const val CLOSE_CHAT_JS = """
(function() {
  var main = document.querySelector('#main');
  if (!main) return false;
  var back = main.querySelector('header [aria-label="Back"]')
    || main.querySelector('header [data-icon="back"]')
    || document.querySelector('[data-icon="back"]');
  if (back) { (back.closest('button') || back).click(); return true; }
  var esc = new KeyboardEvent('keydown', {key:'Escape', keyCode:27, which:27, bubbles:true});
  document.dispatchEvent(esc);
  document.body && document.body.dispatchEvent(esc);
  return !document.querySelector('#main');
})();
"""

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
    private var darkMode = false

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(appContext).apply {
        setBackgroundColor(Color.WHITE)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        // Desktop-width viewport (injected in LAYOUT_FIX_JS) + overview mode scales
        // the desktop shell to the phone screen. The native QR overlay stays readable.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        // Ignore the system font scale; at large scales WhatsApp's layout pushes the QR away.
        // Flavor fallback is 140%; the saved preference overrides this after prefs load.
        settings.textZoom = BuildConfig.WEB_TEXT_ZOOM
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
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (ime.bottom > 0) {
                v.post {
                    evaluateJavascript(
                        "window.__relayRevealComposer && window.__relayRevealComposer()",
                        null,
                    )
                }
            }
            insets
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, DESKTOP_SPOOF_JS, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(this, THEME_BOOTSTRAP_JS, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(this, LAYOUT_FIX_JS, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(this, KEYBOARD_JS, setOf("*"))
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
                    view?.evaluateJavascript(THEME_BOOTSTRAP_JS, null)
                    view?.evaluateJavascript(LAYOUT_FIX_JS, null)
                    view?.evaluateJavascript(KEYBOARD_JS, null)
                }
                view?.evaluateJavascript(themeJs(darkMode), null)
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
                view?.evaluateJavascript(KEYBOARD_JS, null)
                view?.evaluateJavascript(themeJs(darkMode), null)
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

    suspend fun isChatOpen(): Boolean {
        val raw = withTimeoutOrNull(500) {
            evaluate(
                "(function(){var b=document.body;if(b&&b.classList.contains('relay-chat'))return true;" +
                    "var m=document.querySelector('#main');if(!m)return false;" +
                    "return !!(m.querySelector('footer [contenteditable=\"true\"]')||" +
                    "m.querySelector('[data-testid=\"conversation-compose-box-input\"]')||" +
                    "m.querySelector('header img'));})()",
            )
        }
        return raw?.trim('"') == "true"
    }

    /** Closes the open conversation. Returns true if a chat was open and got closed. */
    suspend fun closeChat(): Boolean {
        val raw = withTimeoutOrNull(600) { evaluate(CLOSE_CHAT_JS) } ?: return false
        return raw == "true"
    }

    fun reload() {
        webView.reload()
    }

    fun setTextZoom(percent: Int) {
        val zoom = percent.coerceIn(100, 225)
        webView.post { webView.settings.textZoom = zoom }
    }

    fun setDarkMode(dark: Boolean) {
        darkMode = dark
        // WhatsApp Web themes itself from prefers-color-scheme, which our
        // document-start matchMedia override answers from the persisted flag.
        // No WebView algorithmic darkening (would double-darken WA's own theme).
        applyAlgorithmicDarkening(false)
        val bg = if (dark) Color.parseColor("#0B141A") else Color.WHITE
        webView.post {
            webView.setBackgroundColor(bg)
            webView.evaluateJavascript(themeJs(dark), null)
        }
    }

    private fun applyAlgorithmicDarkening(dark: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(
                webView.settings,
                if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
            )
        }
    }

    fun revealComposer() {
        webView.evaluateJavascript(KEYBOARD_JS, null)
        webView.evaluateJavascript(
            "window.__relayRevealComposer && window.__relayRevealComposer()",
            null,
        )
    }

    fun clickSearch() {
        webView.evaluateJavascript(clickJs("search"), null)
    }

    fun clickNewChat() {
        webView.evaluateJavascript(clickJs("new-chat"), null)
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
                webView.evaluateJavascript(KEYBOARD_JS, null)
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
