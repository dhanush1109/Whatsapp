package app.relay.companion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.relay.companion.data.ThemePreference

private val LightScheme = lightColorScheme(
    primary = RelayColor.Accent,
    onPrimary = RelayColor.OnAccent,
    primaryContainer = Color(0xFFD9FDD3),
    onPrimaryContainer = RelayColor.ForegroundLight,
    secondary = RelayColor.Fab,
    onSecondary = Color(0xFF00351C),
    secondaryContainer = RelayColor.MutedLight,
    onSecondaryContainer = RelayColor.ForegroundLight,
    tertiary = RelayColor.Accent,
    onTertiary = RelayColor.OnAccent,
    tertiaryContainer = RelayColor.OutgoingLight,
    onTertiaryContainer = RelayColor.ForegroundLight,
    background = RelayColor.BackgroundLight,
    onBackground = RelayColor.ForegroundLight,
    surface = RelayColor.SurfaceLight,
    onSurface = RelayColor.ForegroundLight,
    surfaceVariant = RelayColor.MutedLight,
    onSurfaceVariant = RelayColor.SecondaryTextLight,
    surfaceContainer = RelayColor.MutedLight,
    surfaceContainerLow = RelayColor.BackgroundLight,
    surfaceContainerHigh = RelayColor.MutedLight,
    surfaceContainerHighest = RelayColor.MutedLight,
    outline = RelayColor.BorderLight,
    outlineVariant = RelayColor.BorderLight,
    error = RelayColor.DestructiveLight,
    onError = RelayColor.OnDestructiveLight,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = RelayColor.ForegroundLight,
    inverseOnSurface = RelayColor.BackgroundLight,
    inversePrimary = RelayColor.Accent,
    scrim = Color(0x990B141A),
)

private val DarkScheme = darkColorScheme(
    primary = RelayColor.Accent,
    onPrimary = RelayColor.OnAccent,
    primaryContainer = RelayColor.OutgoingDark,
    onPrimaryContainer = RelayColor.ForegroundDark,
    secondary = RelayColor.Fab,
    onSecondary = Color(0xFF00351C),
    secondaryContainer = RelayColor.MutedDark,
    onSecondaryContainer = RelayColor.ForegroundDark,
    tertiary = RelayColor.Accent,
    onTertiary = RelayColor.OnAccent,
    tertiaryContainer = RelayColor.OutgoingDark,
    onTertiaryContainer = RelayColor.ForegroundDark,
    background = RelayColor.BackgroundDark,
    onBackground = RelayColor.ForegroundDark,
    surface = RelayColor.SurfaceDark,
    onSurface = RelayColor.ForegroundDark,
    surfaceVariant = RelayColor.MutedDark,
    onSurfaceVariant = RelayColor.SecondaryTextDark,
    surfaceContainer = RelayColor.AppBarDark,
    surfaceContainerLow = RelayColor.SurfaceDark,
    surfaceContainerHigh = RelayColor.AppBarDark,
    surfaceContainerHighest = RelayColor.AppBarDark,
    outline = RelayColor.BorderDark,
    outlineVariant = RelayColor.BorderDark,
    error = RelayColor.DestructiveDark,
    onError = RelayColor.OnDestructiveDark,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = RelayColor.ForegroundDark,
    inverseOnSurface = RelayColor.BackgroundDark,
    inversePrimary = RelayColor.Accent,
    scrim = Color(0xCC000000),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RelayTheme(
    themePreference: ThemePreference = ThemePreference.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themePreference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !dark
            insets.isAppearanceLightNavigationBars = !dark
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = RelayTypography,
    ) {
        ProvideReducedMotion(content = content)
    }
}
