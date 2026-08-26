package app.relay.companion.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.relay.companion.BuildConfig
import app.relay.companion.R
import app.relay.companion.data.PreferencesRepository
import app.relay.companion.data.RelaySettings
import app.relay.companion.data.ThemePreference
import app.relay.companion.session.SessionController
import app.relay.companion.ui.components.ScreenHeader
import app.relay.companion.ui.theme.Motion
import app.relay.companion.ui.theme.RelayHapticEvent
import app.relay.companion.ui.theme.Spacing
import app.relay.companion.ui.theme.rememberRelayHaptics
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: RelaySettings,
    repo: PreferencesRepository,
    controller: SessionController,
    onOpenSecond: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberRelayHaptics(settings.hapticsEnabled)
    var pinDialog by remember { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.touch)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "W",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.settings_profile_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_profile_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_appearance)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ThemeChip(
                    label = stringResource(R.string.settings_theme_system),
                    selected = settings.theme == ThemePreference.System,
                    onClick = { scope.launch { repo.setTheme(ThemePreference.System) } },
                )
                ThemeChip(
                    label = stringResource(R.string.settings_theme_light),
                    selected = settings.theme == ThemePreference.Light,
                    onClick = { scope.launch { repo.setTheme(ThemePreference.Light) } },
                )
                ThemeChip(
                    label = stringResource(R.string.settings_theme_dark),
                    selected = settings.theme == ThemePreference.Dark,
                    onClick = { scope.launch { repo.setTheme(ThemePreference.Dark) } },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_haptics)) },
                supportingContent = { Text(stringResource(R.string.settings_haptics_body)) },
                trailingContent = {
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { checked ->
                            haptics.perform(if (checked) RelayHapticEvent.ToggleOn else RelayHapticEvent.ToggleOff)
                            scope.launch { repo.setHapticsEnabled(checked) }
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        SettingsSection(title = stringResource(R.string.settings_privacy)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_lock)) },
                supportingContent = { Text(stringResource(R.string.settings_lock_body)) },
                trailingContent = {
                    Switch(
                        checked = settings.lockEnabled,
                        onCheckedChange = { checked ->
                            haptics.perform(if (checked) RelayHapticEvent.ToggleOn else RelayHapticEvent.ToggleOff)
                            if (checked && !settings.hasPin) {
                                pinDialog = true
                            } else {
                                scope.launch { repo.setLockEnabled(checked) }
                            }
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    val next = !settings.lockEnabled
                    if (next && !settings.hasPin) pinDialog = true
                    else scope.launch { repo.setLockEnabled(next) }
                },
            )
            AnimatedVisibility(
                visible = settings.lockEnabled,
                enter = expandVertically(Motion.defaultSpatialSpec()) + fadeIn(Motion.defaultEffectsSpec()),
                exit = shrinkVertically(Motion.defaultSpatialSpec()) + fadeOut(Motion.defaultEffectsSpec()),
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                if (settings.hasPin) R.string.settings_change_pin else R.string.settings_set_pin,
                            ),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { pinDialog = true },
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_session)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_session)) },
                supportingContent = { Text(stringResource(R.string.settings_clear_session_body)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { signOutDialog = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.session_open_second)) },
                supportingContent = { Text(stringResource(R.string.settings_second_session_body)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onOpenSecond),
            )
        }

        SettingsSection(title = stringResource(R.string.settings_about)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "WhatsApp ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pinDialog) {
        val mismatch = confirm.isNotEmpty() && pin != confirm
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text(stringResource(R.string.settings_set_pin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.lock_pin_label)) },
                        supportingText = { Text(stringResource(R.string.lock_pin_helper)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.lock_pin_confirm_label)) },
                        isError = mismatch,
                        supportingText = {
                            if (mismatch) Text(stringResource(R.string.lock_pin_mismatch))
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length in 4..6 && pin == confirm,
                    onClick = {
                        scope.launch {
                            repo.savePin(pin)
                            repo.setLockEnabled(true)
                            haptics.perform(RelayHapticEvent.Confirm)
                            pin = ""
                            confirm = ""
                            pinDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.lock_save_pin))
                }
            },
            dismissButton = {
                TextButton(onClick = { pinDialog = false }) {
                    Text(stringResource(R.string.lock_cancel))
                }
            },
        )
    }

    if (signOutDialog) {
        AlertDialog(
            onDismissRequest = { signOutDialog = false },
            title = { Text(stringResource(R.string.settings_clear_session)) },
            text = { Text(stringResource(R.string.settings_clear_session_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.signOut()
                        signOutDialog = false
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_session))
                }
            },
            dismissButton = {
                TextButton(onClick = { signOutDialog = false }) {
                    Text(stringResource(R.string.lock_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Card(
            shape = RoundedCornerShape(Spacing.cardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
