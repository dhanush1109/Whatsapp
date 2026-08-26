package app.relay.companion.ui.direct

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import app.relay.companion.R
import app.relay.companion.data.CountryCode
import app.relay.companion.data.CountryCodes
import app.relay.companion.data.RelaySettings
import app.relay.companion.ui.components.RelaySnackbarKind
import app.relay.companion.ui.components.showRelaySnackbar
import app.relay.companion.ui.shell.LocalSnackbarHostState
import app.relay.companion.ui.theme.LocalReducedMotion
import app.relay.companion.ui.theme.Motion
import app.relay.companion.ui.theme.RelayHapticEvent
import app.relay.companion.ui.theme.Spacing
import app.relay.companion.ui.theme.rememberRelayHaptics
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectScreen(settings: RelaySettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val haptics = rememberRelayHaptics(settings.hapticsEnabled)
    val genericError = stringResource(R.string.generic_error)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var country by remember { mutableStateOf(CountryCodes.all.first { it.iso == "IN" }) }
    var countryExpanded by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    val filteredCountries by remember(countryQuery) {
        derivedStateOf {
            val q = countryQuery.trim()
            if (q.isEmpty()) CountryCodes.all
            else CountryCodes.all.filter {
                it.name.contains(q, ignoreCase = true) || it.dialCode.contains(q) || it.iso.contains(q, true)
            }
        }
    }
    val phoneError = submitted && (phone.length !in 6..15)
    val digits = phone.filter(Char::isDigit)
    val canSubmit = digits.length in 6..15

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.direct_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.direct_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            ExposedDropdownMenuBox(
                expanded = countryExpanded,
                onExpandedChange = { countryExpanded = it },
            ) {
                OutlinedTextField(
                    value = if (countryExpanded) countryQuery else "${country.name} (+${country.dialCode})",
                    onValueChange = { countryQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    label = { Text(stringResource(R.string.direct_country_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                    isError = submitted && country.dialCode.isBlank(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = countryExpanded,
                    onDismissRequest = { countryExpanded = false },
                ) {
                    filteredCountries.take(40).forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.name} (+${item.dialCode})") },
                            onClick = {
                                country = item
                                countryQuery = ""
                                countryExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = phone,
                onValueChange = { incoming ->
                    phone = incoming.filter(Char::isDigit).take(15)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.direct_phone_label)) },
                supportingText = {
                    val spatialSpec = Motion.defaultSpatialSpec<IntOffset>()
                    val effectsSpec = Motion.defaultEffectsSpec<Float>()
                    val reducedMotion = LocalReducedMotion.current
                    AnimatedContent(
                        targetState = phoneError,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(effectsSpec).togetherWith(fadeOut(effectsSpec))
                            } else {
                                (slideInVertically(spatialSpec) { it / 2 } + fadeIn(effectsSpec))
                                    .togetherWith(slideOutVertically(spatialSpec) { -it / 2 } + fadeOut(effectsSpec))
                            }
                        },
                        label = "phoneErrorText",
                    ) { isError ->
                        Text(
                            if (isError) stringResource(R.string.direct_error_phone)
                            else stringResource(R.string.direct_phone_helper),
                        )
                    }
                },
                isError = phoneError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(1000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.direct_message_label)) },
                minLines = 3,
            )
            val containerColor by animateColorAsState(
                targetValue = if (canSubmit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                },
                animationSpec = Motion.defaultEffectsSpec(),
                label = "sendButtonColor",
            )
            Button(
                onClick = {
                    submitted = true
                    if (canSubmit) {
                        val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
                        val url = buildString {
                            append("https://wa.me/${country.dialCode}$digits")
                            if (message.isNotBlank()) append("?text=$encoded")
                        }
                        try {
                            haptics.perform(RelayHapticEvent.Confirm)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            scope.launch {
                                snackbarHostState.showRelaySnackbar(genericError, RelaySnackbarKind.Error)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.touch),
                shape = RoundedCornerShape(Spacing.buttonRadius),
                colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            ) {
                Text(stringResource(R.string.direct_open))
            }
        }
    }
}
