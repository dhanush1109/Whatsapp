package app.relay.companion.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.relay.companion.R
import app.relay.companion.data.PreferencesRepository
import app.relay.companion.ui.theme.LocalReducedMotion
import app.relay.companion.ui.theme.Motion
import app.relay.companion.ui.theme.RelayHapticEvent
import app.relay.companion.ui.theme.Spacing
import app.relay.companion.ui.theme.rememberRelayHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    repo: PreferencesRepository,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val haptics = rememberRelayHaptics(hapticsEnabled)
    val reducedMotion = LocalReducedMotion.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val canBio = remember(activity) {
        activity != null &&
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    val biometricTitle = stringResource(R.string.lock_biometric_title)
    val biometricSubtitle = stringResource(R.string.lock_biometric_subtitle)
    val cancel = stringResource(R.string.lock_cancel)

    fun promptBio() {
        val host = activity ?: return
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    haptics.perform(RelayHapticEvent.Confirm)
                    unlocking = true
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(biometricTitle)
            .setSubtitle(biometricSubtitle)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    fun unlock() {
        scope.launch {
            if (repo.verifyPin(pin)) {
                haptics.perform(RelayHapticEvent.Confirm)
                unlocking = true
            } else {
                haptics.perform(RelayHapticEvent.Reject)
                error = true
            }
        }
    }

    LaunchedEffect(canBio) {
        if (canBio) promptBio()
    }

    LaunchedEffect(error) {
        if (error && !reducedMotion) {
            shake.animateTo(
                0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-12f) at 50
                    12f at 100
                    (-8f) at 150
                    8f at 200
                    (-4f) at 250
                    4f at 300
                    0f at 400
                },
            )
        }
    }

    LaunchedEffect(unlocking) {
        if (unlocking) {
            delay(if (reducedMotion) 80 else 220)
            onUnlocked()
        }
    }

    val handoffScale by animateFloatAsState(
        targetValue = if (unlocking) 1.05f else 1f,
        animationSpec = Motion.defaultSpatialSpec(),
        label = "unlockScale",
    )
    val handoffAlpha by animateFloatAsState(
        targetValue = if (unlocking) 0f else 1f,
        animationSpec = Motion.defaultEffectsSpec(),
        label = "unlockAlpha",
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = handoffScale
                scaleY = handoffScale
                alpha = handoffAlpha
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.lock_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(6)
                    error = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = shake.value.dp),
                label = { Text(stringResource(R.string.lock_pin_label)) },
                isError = error,
                supportingText = {
                    if (error) Text(stringResource(R.string.lock_error))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { unlock() }),
                singleLine = true,
            )
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = { unlock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.touch),
                shape = RoundedCornerShape(Spacing.buttonRadius),
            ) {
                Text(stringResource(R.string.lock_unlock))
            }
            if (canBio) {
                TextButton(onClick = { promptBio() }) {
                    Text(stringResource(R.string.lock_use_biometric))
                }
            }
        }
    }
}
