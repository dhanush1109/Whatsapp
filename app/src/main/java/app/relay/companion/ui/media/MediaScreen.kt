package app.relay.companion.ui.media

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.relay.companion.R
import app.relay.companion.data.PreferencesRepository
import app.relay.companion.data.RelaySettings
import app.relay.companion.media.MediaRepository
import app.relay.companion.media.StatusItem
import app.relay.companion.ui.components.PrimingCard
import app.relay.companion.ui.components.RelaySnackbarKind
import app.relay.companion.ui.components.ScreenHeader
import app.relay.companion.ui.components.showRelaySnackbar
import app.relay.companion.ui.shell.LocalSnackbarHostState
import app.relay.companion.ui.theme.LocalReducedMotion
import app.relay.companion.ui.theme.Motion
import app.relay.companion.ui.theme.RelayHapticEvent
import app.relay.companion.ui.theme.Spacing
import app.relay.companion.ui.theme.rememberRelayHaptics
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaScreen(
    settings: RelaySettings,
    repo: PreferencesRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberRelayHaptics(settings.hapticsEnabled)
    val snackbarHostState = LocalSnackbarHostState.current
    val savedMessage = stringResource(R.string.media_saved)
    val errorMessage = stringResource(R.string.media_error)
    val mediaRepo = remember { MediaRepository(context.applicationContext) }
    val treeUri = settings.statusTreeUri?.let(Uri::parse)
    var items by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var savedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selected by remember { mutableStateOf<StatusItem?>(null) }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        scope.launch { repo.setStatusTreeUri(uri.toString()) }
    }

    LaunchedEffect(treeUri) {
        items = if (treeUri == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { mediaRepo.list(treeUri) }
        }
    }

    fun save(item: StatusItem) {
        scope.launch {
            val ok = mediaRepo.save(item)
            if (ok) {
                haptics.perform(RelayHapticEvent.Confirm)
                savedUris = savedUris + item.uri
            }
            snackbarHostState.showRelaySnackbar(
                message = if (ok) savedMessage else errorMessage,
                kind = if (ok) RelaySnackbarKind.Success else RelaySnackbarKind.Error,
            )
        }
    }

    val detailEffectsSpec = Motion.defaultEffectsSpec<Float>()
    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(detailEffectsSpec).togetherWith(fadeOut(detailEffectsSpec))
            },
            label = "mediaDetail",
        ) { current ->
            if (current == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.sm),
                ) {
                    ScreenHeader(title = stringResource(R.string.media_title))
                    if (treeUri == null) {
                        PrimingCard(
                            icon = R.drawable.ic_folder,
                            title = stringResource(R.string.media_priming_title),
                            body = stringResource(R.string.media_priming_body),
                            actionLabel = stringResource(R.string.media_choose_folder),
                            onAction = { treeLauncher.launch(null) },
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                    } else {
                        TextButton(onClick = { treeLauncher.launch(null) }) {
                            Text(stringResource(R.string.media_change_folder))
                        }
                        if (items.isEmpty()) {
                            Text(
                                text = stringResource(R.string.media_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(140.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = Spacing.lg),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                itemsIndexed(items, key = { _, item -> item.uri }) { index, item ->
                                    StatusCard(
                                        item = item,
                                        index = index,
                                        saved = item.uri in savedUris,
                                        sharedScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onSave = { save(item) },
                                        onOpen = { selected = item },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                MediaDetail(
                    item = current,
                    sharedScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                    onClose = { selected = null },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StatusCard(
    item: StatusItem,
    index: Int,
    saved: Boolean,
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) delay((index % 12) * 35L)
        visible = true
    }
    val enterTransition = if (reducedMotion) {
        fadeIn(Motion.defaultEffectsSpec())
    } else {
        fadeIn(Motion.defaultEffectsSpec()) + slideInVertically(Motion.defaultSpatialSpec()) { it / 4 }
    }
    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        modifier = modifier,
    ) {
        with(sharedScope) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Spacing.sm))
                    .sharedElement(
                        rememberSharedContentState(key = item.uri),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    .clickable(onClick = onOpen),
            ) {
                AsyncImage(
                    model = Uri.parse(item.uri),
                    contentDescription = stringResource(
                        if (item.isVideo) R.string.cd_video else R.string.cd_status_item,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White,
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.xs)
                        .size(Spacing.touch),
                ) {
                    AnimatedContent(targetState = saved, label = "saveIcon") { isSaved ->
                        Icon(
                            painter = painterResource(
                                if (isSaved) R.drawable.ic_check_circle else R.drawable.ic_download,
                            ),
                            contentDescription = stringResource(R.string.cd_save_media),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MediaDetail(
    item: StatusItem,
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    with(sharedScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
        ) {
            AsyncImage(
                model = Uri.parse(item.uri),
                contentDescription = stringResource(R.string.cd_status_item),
                modifier = Modifier
                    .fillMaxSize()
                    .sharedElement(
                        rememberSharedContentState(key = item.uri),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.md),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cd_close),
                    tint = Color.White,
                )
            }
        }
    }
}
