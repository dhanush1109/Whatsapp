package app.relay.companion.ui.session

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.relay.companion.R
import app.relay.companion.qr.QrEncoder
import app.relay.companion.session.LinkState
import app.relay.companion.ui.theme.LocalReducedMotion
import app.relay.companion.ui.theme.Motion
import app.relay.companion.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QR_RENDER_PX = 720
private val QR_MAX_WIDTH = 360.dp

/**
 * Shows WhatsApp Web's login QR as a native, full-size code.
 *
 * The web client draws its QR into a canvas sized for a desktop window, which on a
 * phone leaves it cramped and easy to miss; re-encoding the payload here keeps it
 * crisp and centred at any screen size. [onShowPage] stays available so flows the
 * overlay cannot serve, such as logging in with a phone number, remain reachable.
 */
@Composable
fun SessionLinkCard(
    state: LinkState.Qr,
    onShowPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val fadeSpec = Motion.defaultEffectsSpec<Float>()
    val fadeOutSpec = Motion.fastEffectsSpec<Float>()
    val scaleSpec = Motion.defaultSpatialSpec<Float>()
    val qr by produceState<ImageBitmap?>(null, state.payload, state.pngBase64) {
        value = withContext(Dispatchers.Default) { renderQr(state) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.session_link_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier
                    .padding(top = Spacing.lg)
                    .widthIn(max = QR_MAX_WIDTH)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Spacing.lg))
                    .background(Color.White)
                    .padding(Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = qr,
                    transitionSpec = {
                        val enter = if (reducedMotion) {
                            fadeIn(fadeSpec)
                        } else {
                            fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = 0.92f)
                        }
                        enter togetherWith fadeOut(fadeOutSpec)
                    },
                    label = "link-qr",
                ) { bitmap ->
                    if (bitmap == null) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.cd_link_qr),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(top = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LinkStep(1, stringResource(R.string.session_link_step_1))
                LinkStep(2, stringResource(R.string.session_link_step_2))
                LinkStep(3, stringResource(R.string.session_link_step_3))
            }

            TextButton(
                onClick = onShowPage,
                modifier = Modifier.padding(top = Spacing.md),
            ) {
                Text(stringResource(R.string.session_link_show_page))
            }
        }
    }
}

@Composable
private fun LinkStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(Spacing.lg)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

/**
 * Prefers re-encoding the payload, which stays sharp at any size, and falls back to
 * the canvas WhatsApp already rendered if the payload attribute ever disappears.
 */
private fun renderQr(state: LinkState.Qr): ImageBitmap? {
    state.payload?.let { payload ->
        QrEncoder.encode(
            text = payload,
            size = QR_RENDER_PX,
            foreground = 0xFF000000.toInt(),
            background = 0xFFFFFFFF.toInt(),
        )?.let { return it.asImageBitmap() }
    }
    state.pngBase64?.let { encoded ->
        runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.let { return it.asImageBitmap() }
    }
    return null
}
