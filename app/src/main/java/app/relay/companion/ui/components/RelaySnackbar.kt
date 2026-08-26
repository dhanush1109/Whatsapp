package app.relay.companion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.relay.companion.R
import app.relay.companion.ui.theme.Spacing

/** The significance of a snackbar message, driving its icon and tint. */
enum class RelaySnackbarKind { Success, Error, Info }

private class RelaySnackbarVisuals(
    override val message: String,
    val kind: RelaySnackbarKind,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
) : SnackbarVisuals

/** Shows a themed snackbar carrying an icon, message, and optional action — never a bare Toast. */
suspend fun SnackbarHostState.showRelaySnackbar(
    message: String,
    kind: RelaySnackbarKind = RelaySnackbarKind.Info,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
) = showSnackbar(
    RelaySnackbarVisuals(
        message = message,
        kind = kind,
        actionLabel = actionLabel,
        withDismissAction = false,
        duration = duration,
    ),
)

/** Drop-in replacement for [SnackbarHost] that renders [RelaySnackbarVisuals] with an icon. */
@Composable
fun RelaySnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        val visuals = data.visuals as? RelaySnackbarVisuals
        val tint = when (visuals?.kind) {
            RelaySnackbarKind.Success -> Color(0xFF34D399)
            RelaySnackbarKind.Error -> MaterialTheme.colorScheme.error
            RelaySnackbarKind.Info, null -> MaterialTheme.colorScheme.inverseOnSurface
        }
        val icon = when (visuals?.kind) {
            RelaySnackbarKind.Success -> R.drawable.ic_check_circle
            RelaySnackbarKind.Error -> R.drawable.ic_error
            RelaySnackbarKind.Info, null -> R.drawable.ic_info
        }
        Snackbar(
            action = visuals?.actionLabel?.let { label ->
                {
                    TextButton(onClick = { data.performAction() }) {
                        Text(label, color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Text(data.visuals.message)
            }
        }
    }
}
