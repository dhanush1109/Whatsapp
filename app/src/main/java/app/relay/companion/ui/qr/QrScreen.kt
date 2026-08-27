@file:OptIn(ExperimentalGetImage::class, ExperimentalMaterial3ExpressiveApi::class)

package app.relay.companion.ui.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.relay.companion.R
import app.relay.companion.data.RelaySettings
import app.relay.companion.media.MediaRepository
import app.relay.companion.qr.QrEncoder
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QrScreen(settings: RelaySettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberRelayHaptics(settings.hapticsEnabled)
    val snackbarHostState = LocalSnackbarHostState.current
    val genericError = stringResource(R.string.generic_error)
    val emptyMessage = stringResource(R.string.qr_empty)
    val savedMessage = stringResource(R.string.qr_saved)
    val mediaRepo = remember { MediaRepository(context.applicationContext) }
    var tab by remember { mutableIntStateOf(0) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showBurst by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        denied = !ok
    }

    LaunchedEffect(result) {
        if (result != null) {
            haptics.perform(RelayHapticEvent.Confirm)
            showBurst = true
            delay(600)
            showBurst = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.sm, bottom = Spacing.md),
    ) {
        ScreenHeader(title = stringResource(R.string.qr_title))
        Spacer(Modifier.height(Spacing.md))
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.qr_scan_tab)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.qr_create_tab)) },
            )
        }
        Spacer(Modifier.height(Spacing.md))
        if (tab == 0) {
            when {
                granted -> {
                    val spatialSpec = Motion.defaultSpatialSpec<Float>()
                    val effectsSpec = Motion.defaultEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = result,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            (scaleIn(spatialSpec, initialScale = 0.94f) + fadeIn(effectsSpec))
                                .togetherWith(scaleOut(spatialSpec, targetScale = 0.94f) + fadeOut(effectsSpec))
                        },
                        label = "scanResult",
                    ) { value ->
                        if (value == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                QrCameraPreview(
                                    onQr = { v -> if (v != result) result = v },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ScanReticle()
                                Text(
                                    text = stringResource(R.string.qr_scan_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = Spacing.xl),
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    ResultCard(value)
                                    if (!LocalReducedMotion.current) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showBurst,
                                            enter = fadeIn(),
                                            exit = fadeOut(Motion.defaultEffectsSpec()),
                                        ) {
                                            ScanSuccessBurst(modifier = Modifier.size(72.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(Spacing.md))
                                OutlinedButton(
                                    onClick = { result = null },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(Spacing.touch),
                                    shape = RoundedCornerShape(Spacing.buttonRadius),
                                ) {
                                    Text(stringResource(R.string.qr_scan_again))
                                }
                            }
                        }
                    }
                }
                denied -> PrimingCard(
                    icon = R.drawable.ic_photo_camera,
                    title = stringResource(R.string.qr_camera_title),
                    body = stringResource(R.string.qr_camera_denied),
                    actionLabel = stringResource(R.string.qr_open_settings),
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )
                else -> PrimingCard(
                    icon = R.drawable.ic_photo_camera,
                    title = stringResource(R.string.qr_camera_title),
                    body = stringResource(R.string.qr_camera_body),
                    actionLabel = stringResource(R.string.qr_camera_cta),
                    onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(2000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.qr_content_label)) },
                    minLines = 3,
                )
                Button(
                    onClick = {
                        scope.launch {
                            if (content.isBlank()) {
                                snackbarHostState.showRelaySnackbar(emptyMessage, RelaySnackbarKind.Info)
                                return@launch
                            }
                            val encoded = withContext(Dispatchers.Default) { QrEncoder.encode(content) }
                            bitmap = encoded
                            if (encoded == null) {
                                snackbarHostState.showRelaySnackbar(genericError, RelaySnackbarKind.Error)
                            } else {
                                haptics.perform(RelayHapticEvent.Confirm)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.touch),
                    shape = RoundedCornerShape(Spacing.buttonRadius),
                ) {
                    Text(stringResource(R.string.qr_create))
                }
                AnimatedVisibility(
                    visible = bitmap != null,
                    enter = scaleIn(Motion.defaultSpatialSpec(), initialScale = 0.85f) +
                        fadeIn(Motion.defaultEffectsSpec()),
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_qr_preview),
                                modifier = Modifier
                                    .sizeIn(maxWidth = 280.dp)
                                    .aspectRatio(1f),
                                contentScale = ContentScale.Fit,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch { shareQr(context, bmp, snackbarHostState, genericError) }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(Spacing.touch),
                                    shape = RoundedCornerShape(Spacing.buttonRadius),
                                ) {
                                    Text(stringResource(R.string.qr_share))
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val ok = mediaRepo.saveBitmap(bmp)
                                            if (ok) haptics.perform(RelayHapticEvent.Confirm)
                                            snackbarHostState.showRelaySnackbar(
                                                message = if (ok) savedMessage else genericError,
                                                kind = if (ok) RelaySnackbarKind.Success else RelaySnackbarKind.Error,
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(Spacing.touch),
                                    shape = RoundedCornerShape(Spacing.buttonRadius),
                                ) {
                                    Text(stringResource(R.string.qr_save))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanReticle(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val scale = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "reticle")
        val animated by transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "reticleScale",
        )
        animated
    }
    Box(
        modifier = modifier
            .size(220.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(28.dp)),
    )
}

@Composable
private fun ScanSuccessBurst(modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Sunny) }
    val progress = remember { Animatable(0f) }
    val color = MaterialTheme.colorScheme.primary
    val burstPath = remember { Path() }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
    }
    Canvas(modifier = modifier) {
        val t = progress.value
        val radius = size.minDimension / 2f * (0.5f + t * 0.9f)
        val alpha = (1f - t).coerceIn(0f, 1f)
        val composePath = morph.toPath(t, burstPath)
        val matrix = Matrix()
        matrix.scale(radius, radius)
        composePath.transform(matrix)
        translate(size.width / 2f, size.height / 2f) {
            drawPath(composePath, color = color.copy(alpha = alpha))
        }
    }
}

@Composable
private fun ResultCard(value: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val copiedMessage = stringResource(R.string.qr_copied)
    val genericError = stringResource(R.string.generic_error)
    val isUrl = value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.qr_result), style = MaterialTheme.typography.titleSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        context.getSystemService<ClipboardManager>()
                            ?.setPrimaryClip(ClipData.newPlainText("qr", value))
                        scope.launch {
                            snackbarHostState.showRelaySnackbar(copiedMessage, RelaySnackbarKind.Success)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(Spacing.touch),
                ) {
                    Text(stringResource(R.string.qr_copy))
                }
                if (isUrl) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                            } catch (_: Exception) {
                                scope.launch {
                                    snackbarHostState.showRelaySnackbar(genericError, RelaySnackbarKind.Error)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(Spacing.touch),
                    ) {
                        Text(stringResource(R.string.qr_open_link))
                    }
                }
            }
        }
    }
}

@Composable
fun QrCameraPreview(
    onQr: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) { PreviewView(context) }
    val onQrState by rememberUpdatedState(onQr)
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX,
                )
                .build(),
        )
    }
    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener(
            {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.rawValue?.let(onQrState)
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            },
            executor,
        )
        onDispose {
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }
    AndroidView(
        factory = {
            previewView.apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = modifier,
    )
}

private suspend fun shareQr(
    context: android.content.Context,
    bitmap: Bitmap,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    genericError: String,
) {
    try {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "qr").apply { mkdirs() }
            File(dir, "relay-qr.png").also { out ->
                FileOutputStream(out).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("qr", uri)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.qr_share)))
    } catch (_: Exception) {
        snackbarHostState.showRelaySnackbar(genericError, RelaySnackbarKind.Error)
    }
}
