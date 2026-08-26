package app.relay.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * App-level vocabulary for haptic feedback, mapped to the platform's documented
 * meanings rather than raw [HapticFeedbackType] calls scattered through screens.
 */
enum class RelayHapticEvent {
    /** Switching tabs or discrete scan-line ticks. */
    Tick,

    /** Save, unlock, or scan succeeded. */
    Confirm,

    /** Wrong PIN or an action failed. */
    Reject,

    /** A switch or toggle moved to on. */
    ToggleOn,

    /** A switch or toggle moved to off. */
    ToggleOff,
}

/** Wraps [HapticFeedback] and honors the user's haptics-enabled preference. */
class RelayHaptics internal constructor(
    private val hapticFeedback: HapticFeedback,
    private val enabled: Boolean,
) {
    fun perform(event: RelayHapticEvent) {
        if (!enabled) return
        val type = when (event) {
            RelayHapticEvent.Tick -> HapticFeedbackType.SegmentTick
            RelayHapticEvent.Confirm -> HapticFeedbackType.Confirm
            RelayHapticEvent.Reject -> HapticFeedbackType.Reject
            RelayHapticEvent.ToggleOn -> HapticFeedbackType.ToggleOn
            RelayHapticEvent.ToggleOff -> HapticFeedbackType.ToggleOff
        }
        hapticFeedback.performHapticFeedback(type)
    }
}

@Composable
fun rememberRelayHaptics(enabled: Boolean): RelayHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(hapticFeedback, enabled) { RelayHaptics(hapticFeedback, enabled) }
}
