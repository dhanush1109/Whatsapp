package app.relay.companion.ui.session

import android.Manifest
import android.net.Uri
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.companion.R
import app.relay.companion.session.LinkState
import app.relay.companion.session.SessionController
import app.relay.companion.ui.components.SessionSkeleton
import app.relay.companion.ui.theme.Motion
import app.relay.companion.ui.theme.RelayColor
import app.relay.companion.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val QR_POLL_INTERVAL_MS = 900L
private val TopBarHeight = 56.dp

@Composable
fun SessionPane(
    controller: SessionController,
    modifier: Modifier = Modifier,
    onOpenSecond: (() -> Unit)? = null,
) {
    val progress by controller.progress.collectAsStateWithLifecycle()
    val pageReady by controller.pageReady.collectAsStateWithLifecycle()
    val linkState by controller.linkState.collectAsStateWithLifecycle()
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var permissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var preferWebPage by remember { mutableStateOf(false) }
    var sawQr by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val request = permissionRequest ?: return@rememberLauncherForActivityResult
        permissionRequest = null
        val allowed = request.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> granted[Manifest.permission.CAMERA] == true
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> granted[Manifest.permission.RECORD_AUDIO] == true
                else -> true
            }
        }.toTypedArray()
        if (allowed.isEmpty()) request.deny() else request.grant(allowed)
    }

    DisposableEffect(controller) {
        controller.callbacks.onFileChooser = { callback, accept ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            val mime = if (accept.isNullOrEmpty() || accept.all { it.isBlank() }) {
                arrayOf("*/*")
            } else {
                accept
            }
            fileLauncher.launch(mime)
            true
        }
        controller.callbacks.onPermissionRequest = { request ->
            permissionRequest = request
            val needed = buildList {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources) add(Manifest.permission.CAMERA)
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) add(Manifest.permission.RECORD_AUDIO)
            }
            if (needed.isEmpty()) {
                request.grant(request.resources)
            } else {
                permissionLauncher.launch(needed.toTypedArray())
            }
        }
        onDispose {
            controller.callbacks.onFileChooser = null
            controller.callbacks.onPermissionRequest = null
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            permissionRequest?.deny()
            permissionRequest = null
        }
    }

    LaunchedEffect(controller) {
        controller.loadHome()
    }

    LaunchedEffect(controller) {
        while (true) {
            controller.refreshLinkState()
            delay(QR_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(progress, pageReady) {
        if (progress == 100 || pageReady) {
            hasLoadedOnce = true
            refreshing = false
        }
    }

    LaunchedEffect(controller) {
        delay(8_000)
        hasLoadedOnce = true
        refreshing = false
    }

    LaunchedEffect(linkState) {
        when (linkState) {
            is LinkState.Qr -> sawQr = true
            LinkState.Linked -> sawQr = false
            else -> Unit
        }
    }

    val qrState = linkState as? LinkState.Qr
    val showLinkCard = qrState != null && !preferWebPage
    val showOpening = sawQr && linkState is LinkState.None && !preferWebPage
    val showTopBar = !showLinkCard
    val dark = isSystemInDarkTheme()

    Column(modifier) {
        if (showTopBar) {
            Surface(
                color = if (dark) RelayColor.AppBarDark else RelayColor.Accent,
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopBarHeight),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.session_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            preferWebPage = false
                            controller.reload()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.cd_reload),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    preferWebPage = false
                    controller.reload()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                AndroidView(
                    factory = {
                        controller.webView.apply {
                            (parent as? ViewGroup)?.removeView(this)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(rememberNestedScrollInteropConnection()),
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = progress in 1..99,
                enter = fadeIn(Motion.defaultEffectsSpec()),
                exit = fadeOut(Motion.defaultEffectsSpec()),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !hasLoadedOnce,
                exit = fadeOut(Motion.defaultEffectsSpec()),
                modifier = Modifier.fillMaxSize(),
            ) {
                SessionSkeleton(modifier = Modifier.fillMaxSize())
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showLinkCard,
                enter = fadeIn(Motion.defaultEffectsSpec()),
                exit = fadeOut(Motion.defaultEffectsSpec()),
                modifier = Modifier.fillMaxSize(),
            ) {
                qrState?.let { qr ->
                    SessionLinkCard(
                        state = qr,
                        onShowPage = { preferWebPage = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showOpening,
                enter = fadeIn(Motion.defaultEffectsSpec()),
                exit = fadeOut(Motion.defaultEffectsSpec()),
                modifier = Modifier.fillMaxSize(),
            ) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.session_opening),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                        Text(
                            text = stringResource(R.string.session_opening_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}
