package app.relay.companion.ui.shell

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.relay.companion.R
import app.relay.companion.data.PreferencesRepository
import app.relay.companion.data.RelaySettings
import app.relay.companion.data.ThemePreference
import app.relay.companion.session.Session2Activity
import app.relay.companion.session.SessionController
import app.relay.companion.session.SessionViewModel
import app.relay.companion.ui.components.RelaySnackbarHost
import app.relay.companion.ui.direct.DirectScreen
import app.relay.companion.ui.lock.LockScreen
import app.relay.companion.ui.media.MediaScreen
import app.relay.companion.ui.qr.QrScreen
import app.relay.companion.ui.session.SessionPane
import app.relay.companion.ui.settings.SettingsScreen
import app.relay.companion.ui.theme.LocalReducedMotion
import app.relay.companion.ui.theme.Motion
import app.relay.companion.ui.theme.RelayColor
import app.relay.companion.ui.theme.RelayHapticEvent
import app.relay.companion.ui.theme.RelayTheme
import app.relay.companion.ui.theme.Spacing
import app.relay.companion.ui.theme.rememberRelayHaptics
import kotlinx.coroutines.CancellationException

private enum class RelayTab(val labelRes: Int, val iconOutline: Int, val iconFill: Int) {
    Session(R.string.tab_session, R.drawable.ic_forum_outline, R.drawable.ic_forum_fill),
    Direct(R.string.tab_direct, R.drawable.ic_alternate_email_outline, R.drawable.ic_alternate_email_fill),
    Scan(R.string.tab_scan, R.drawable.ic_qr_scanner, R.drawable.ic_qr_scanner),
    Media(R.string.tab_media, R.drawable.ic_photo_library_outline, R.drawable.ic_photo_library_fill),
    Settings(R.string.tab_settings, R.drawable.ic_settings_outline, R.drawable.ic_settings_fill),
}

/** App-level [SnackbarHostState] so any screen can surface a themed snackbar instead of a Toast. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalSnackbarHostState not provided")
}

@Composable
fun RelayRoot(
    sessionViewModel: SessionViewModel,
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { PreferencesRepository(context.applicationContext) }
    val settings by produceState<RelaySettings?>(initialValue = null, repo) {
        repo.settings.collect { value = it }
    }
    var tab by remember { mutableStateOf(RelayTab.Session) }
    var unlocked by remember { mutableStateOf(false) }
    val controller = sessionViewModel.controller
    val lifecycleOwner = LocalLifecycleOwner.current
    val theme = settings?.theme ?: ThemePreference.System
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settings != null) {
        if (settings != null) onReady()
    }

    DisposableEffect(lifecycleOwner, settings?.lockEnabled) {
        val enabled = settings?.lockEnabled == true
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_STOP) unlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openSecond() {
        context.startActivity(Intent(context, Session2Activity::class.java))
    }

    RelayTheme(themePreference = theme) {
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            when {
                settings == null -> {
                    val loading = stringResource(R.string.session_loading)
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = loading },
                        color = MaterialTheme.colorScheme.background,
                    ) {}
                }
                settings!!.lockEnabled && !unlocked -> {
                    LockScreen(
                        repo = repo,
                        onUnlocked = { unlocked = true },
                        hapticsEnabled = settings!!.hapticsEnabled,
                    )
                }
                else -> {
                    RelayShellContent(
                        tab = tab,
                        onTabChange = { tab = it },
                        settings = settings!!,
                        repo = repo,
                        controller = controller,
                        snackbarHostState = snackbarHostState,
                        onOpenSecond = { openSecond() },
                        onExitApp = { (context as? Activity)?.moveTaskToBack(true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayShellContent(
    tab: RelayTab,
    onTabChange: (RelayTab) -> Unit,
    settings: RelaySettings,
    repo: PreferencesRepository,
    controller: SessionController,
    snackbarHostState: SnackbarHostState,
    onOpenSecond: () -> Unit,
    onExitApp: () -> Unit,
) {
    val haptics = rememberRelayHaptics(settings.hapticsEnabled)
    var backProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { event -> backProgress = event.progress }
            backProgress = 0f
            if (tab != RelayTab.Session) {
                onTabChange(RelayTab.Session)
            } else if (!controller.goBack()) {
                onExitApp()
            }
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    val webViewReceded = tab != RelayTab.Session
    val webViewScale by animateFloatAsState(
        targetValue = if (webViewReceded) 0.96f else 1f,
        animationSpec = Motion.defaultSpatialSpec(),
        label = "webViewScale",
    )
    val webViewAlpha by animateFloatAsState(
        targetValue = if (webViewReceded) 0.9f else 1f,
        animationSpec = Motion.defaultEffectsSpec(),
        label = "webViewAlpha",
    )

    Scaffold(
        snackbarHost = { RelaySnackbarHost(snackbarHostState) },
        bottomBar = {
            RelayNavigationBar(tab = tab, onTabChange = { newTab ->
                if (newTab != tab) {
                    haptics.perform(RelayHapticEvent.Tick)
                    onTabChange(newTab)
                }
            })
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            SessionPane(
                controller = controller,
                onOpenSecond = onOpenSecond,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = webViewScale
                        scaleY = webViewScale
                        alpha = webViewAlpha
                    },
            )

            val backScale = 1f - backProgress * 0.06f
            val backAlpha = 1f - backProgress * 0.35f
            val tabSpatialSpec = Motion.defaultSpatialSpec<IntOffset>()
            val tabEffectsSpec = Motion.defaultEffectsSpec<Float>()
            val reducedMotion = LocalReducedMotion.current
            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = backScale
                        scaleY = backScale
                        alpha = backAlpha
                    },
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(animationSpec = tabEffectsSpec)
                            .togetherWith(fadeOut(animationSpec = tabEffectsSpec))
                    } else {
                        val forward = targetState.ordinal > initialState.ordinal
                        val enter = slideInHorizontally(animationSpec = tabSpatialSpec) { width ->
                            if (forward) width / 4 else -width / 4
                        } + fadeIn(animationSpec = tabEffectsSpec)
                        val exit = slideOutHorizontally(animationSpec = tabSpatialSpec) { width ->
                            if (forward) -width / 4 else width / 4
                        } + fadeOut(animationSpec = tabEffectsSpec)
                        enter.togetherWith(exit)
                    }
                },
                label = "tabContent",
            ) { currentTab ->
                if (currentTab != RelayTab.Session) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        when (currentTab) {
                            RelayTab.Direct -> DirectScreen(settings = settings)
                            RelayTab.Scan -> QrScreen(settings = settings)
                            RelayTab.Media -> MediaScreen(settings = settings, repo = repo)
                            RelayTab.Settings -> SettingsScreen(
                                settings = settings,
                                repo = repo,
                                controller = controller,
                                onOpenSecond = onOpenSecond,
                            )
                            RelayTab.Session -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayNavigationBar(
    tab: RelayTab,
    onTabChange: (RelayTab) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        RelayTab.entries.forEach { item ->
            val selected = tab == item
            val scale by animateFloatAsState(
                targetValue = if (selected && !reducedMotion) 1.06f else 1f,
                animationSpec = Motion.fastSpatialSpec(),
                label = "navIconScale",
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onTabChange(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RelayColor.Accent,
                    selectedTextColor = RelayColor.Accent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
                icon = {
                    Icon(
                        painter = painterResource(if (selected) item.iconFill else item.iconOutline),
                        contentDescription = stringResource(item.labelRes),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}
