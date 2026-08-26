package app.relay.companion.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * WhatsApp Android tokens. Dark values are authored on their own so body text
 * stays ≥ 4.5:1. Accent green is for large controls and selected icons only —
 * it fails contrast as small text on white.
 */
object RelayColor {
    val Accent = Color(0xFF00A884)
    val Fab = Color(0xFF25D366)
    val Unread = Color(0xFF25D366)
    val OnAccent = Color(0xFFFFFFFF)

    val BackgroundLight = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFFFFFFF)
    val AppBarLight = Color(0xFFFFFFFF)
    val ForegroundLight = Color(0xFF0B141A)
    val SecondaryTextLight = Color(0xFF667781)
    val MutedLight = Color(0xFFF0F2F5)
    val BorderLight = Color(0xFFE9EDEF)
    val OutgoingLight = Color(0xFFD9FDD3)
    val IncomingLight = Color(0xFFFFFFFF)
    val DestructiveLight = Color(0xFFE53935)
    val OnDestructiveLight = Color(0xFFFFFFFF)

    val BackgroundDark = Color(0xFF0B141A)
    val SurfaceDark = Color(0xFF111B21)
    val AppBarDark = Color(0xFF202C33)
    val ForegroundDark = Color(0xFFE9EDEF)
    val SecondaryTextDark = Color(0xFF8696A0)
    val MutedDark = Color(0xFF202C33)
    val BorderDark = Color(0xFF2A3942)
    val OutgoingDark = Color(0xFF005C4B)
    val IncomingDark = Color(0xFF202C33)
    val DestructiveDark = Color(0xFFF15C6D)
    val OnDestructiveDark = Color(0xFF000000)
}
