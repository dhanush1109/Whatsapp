package app.relay.companion.ui.lock

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Result of querying the device for usable biometric hardware. */
enum class BiometricAvailability {
    /** Hardware present and at least one biometric enrolled. */
    Ready,

    /** Hardware present but the user has not enrolled a face/fingerprint yet. */
    NotEnrolled,

    /** No usable biometric hardware (or permanently unavailable). */
    Unavailable,
}

/**
 * Rough guess at the primary biometric modality so we can label the UI as
 * "Face ID"/"face unlock" vs "fingerprint". Android has no direct API to ask
 * which sensor authenticated, so we infer from declared hardware features and
 * fall back to a neutral term.
 */
enum class BiometricKind { Face, Fingerprint, Generic }

object BiometricAuth {

    fun availability(context: Context): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            else -> BiometricAvailability.Unavailable
        }

    fun kind(context: Context): BiometricKind {
        val pm = context.packageManager
        val hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
        val hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        return when {
            hasFace && !hasFingerprint -> BiometricKind.Face
            hasFingerprint && !hasFace -> BiometricKind.Fingerprint
            hasFingerprint -> BiometricKind.Fingerprint
            hasFace -> BiometricKind.Face
            else -> BiometricKind.Generic
        }
    }

    /**
     * Shows the system biometric prompt. [onError] receives non-recoverable
     * errors (excluding the user cancelling), so the caller can fall back to PIN.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {},
        onError: (Int, CharSequence) -> Unit = { _, _ -> },
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errorCode, errString)
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
