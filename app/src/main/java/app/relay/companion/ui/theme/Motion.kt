package app.relay.companion.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * True when the system's Remove animations / Reduce motion setting is active
 * (animator or transition duration scale set to zero). Read this instead of
 * building infinite/repeating animations or spatial springs directly.
 */
val LocalReducedMotion = compositionLocalOf { false }

private fun settingsScale(context: android.content.Context, key: String): Float =
    runCatching { Settings.Global.getFloat(context.contentResolver, key, 1f) }.getOrDefault(1f)

private fun isReducedMotion(context: android.content.Context): Boolean =
    settingsScale(context, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f ||
        settingsScale(context, Settings.Global.TRANSITION_ANIMATION_SCALE) == 0f

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(isReducedMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isReducedMotion(context)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** Installs [LocalReducedMotion] for the subtree; call once near the theme root. */
@Composable
fun ProvideReducedMotion(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion(), content = content)
}

/**
 * Single motion vocabulary for Relay. Spatial specs are for bounds/position/size
 * (spring-based overshoot is fine); effects specs are for color/alpha (springs
 * wobble on fades, so these stay tween-based even at full motion). When reduced
 * motion is active every spec collapses to a short, linear/ease tween with no
 * bounce, and callers should skip infinite/repeating animations entirely by
 * checking [LocalReducedMotion] directly.
 */
object Motion {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> {
        if (LocalReducedMotion.current) return tween(120, easing = FastOutSlowInEasing)
        return MaterialTheme.motionScheme.fastSpatialSpec()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> {
        if (LocalReducedMotion.current) return tween(180, easing = FastOutSlowInEasing)
        return MaterialTheme.motionScheme.defaultSpatialSpec()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> {
        if (LocalReducedMotion.current) return tween(240, easing = FastOutSlowInEasing)
        return MaterialTheme.motionScheme.slowSpatialSpec()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> {
        if (LocalReducedMotion.current) return tween(90, easing = LinearEasing)
        return MaterialTheme.motionScheme.fastEffectsSpec()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> {
        if (LocalReducedMotion.current) return tween(140, easing = LinearEasing)
        return MaterialTheme.motionScheme.defaultEffectsSpec()
    }
}
