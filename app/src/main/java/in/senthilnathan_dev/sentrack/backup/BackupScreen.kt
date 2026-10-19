package `in`.senthilnathan_dev.sentrack.backup

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.sync.NetworkPolicy
import `in`.senthilnathan_dev.sentrack.core.sync.RemoteBackup
import `in`.senthilnathan_dev.sentrack.core.sync.SchedulePresets
import `in`.senthilnathan_dev.sentrack.ui.vault.ErrorText
import `in`.senthilnathan_dev.sentrack.ui.vault.ScreenColumn

private fun whenText(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

/** All actions the Backup screen can trigger, grouped so the screen signature stays readable. */
class BackupActions(
    val onChooseAccount: () -> Unit,
    val onDisconnect: () -> Unit,
    val onBackupNow: () -> Unit,
    val onSetRetention: (Int) -> Unit,
    val onSetIncludePassphrase: (Boolean) -> Unit,
    val onSetScheduleEnabled: (Boolean) -> Unit,
    val onSetCron: (String) -> Unit,
    val onSetNetwork: (NetworkPolicy) -> Unit,
    val onSetRequireCharging: (Boolean) -> Unit,
    val onAskRestore: (RemoteBackup) -> Unit,
    val onCancelRestore: () -> Unit,
    val onConfirmRestore: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

/** Backup screen: choose the Google account, back up now, schedule, choose contents, restore. */
@Composable
fun BackupScreen(state: BackupUiState, actions: BackupActions) {
    val context = LocalContext.current
    ScreenColumn {
        Text("Backup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Backups are stored in a hidden app folder in your own Google Drive. They contain only data that is already " +
                "encrypted on this phone; Google cannot read them.",
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        ErrorText(state.error)

        // ---- Account
        Text("Google account", style = MaterialTheme.typography.titleMedium)
        when {
            state.connected -> {
                Text(state.accountEmail?.let { "Backing up to: $it" } ?: "Connected (account not recorded: choose it again to see it here)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.onChooseAccount, enabled = !state.busy) { Text("Switch account") }
                    TextButton(onClick = actions.onDisconnect, enabled = !state.busy) { Text("Disconnect") }
                }
                Text(
                    "Switching shows the backups of the other account. Backups already made stay in the first account's Drive.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            else -> Button(onClick = actions.onChooseAccount, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Connecting…" else "Choose Google account")
            }
        }

        if (state.vaultExists && state.connected) {
            // ---- Manual backup
            Text(
                if (state.lastSuccessMillis > 0) "Last backup: ${whenText(context, state.lastSuccessMillis)}" else "No backup made yet",
            )
            state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = actions.onBackupNow, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Working…" else "Back up now")
            }

            // ---- Schedule
            Text("Automatic backups", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Back up on a schedule", state.scheduleEnabled, onChange = actions.onSetScheduleEnabled)
            if (state.scheduleEnabled) {
                state.nextRuns.firstOrNull()?.let { Text("Next backup: about ${whenText(context, it)}") }
            }
            Text("How often", style = MaterialTheme.typography.titleSmall)
            SchedulePresets.ALL.forEach { preset ->
                ChoiceRow(preset.label, selected = state.cronText.trim() == preset.cron) { actions.onSetCron(preset.cron) }
            }
            OutlinedTextField(
                value = state.cronText,
                onValueChange = actions.onSetCron,
                label = { Text("Custom schedule (cron)") },
                supportingText = {
                    Text(
                        state.cronError ?: "minute hour day month weekday. Example: 30 21 * * MON-FRI = weekdays at 9:30 PM. " +
                            "At least 15 minutes apart.",
                    )
                },
                isError = state.cronError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.cronError == null && state.nextRuns.isNotEmpty()) {
                Text(
                    "Next runs: " + state.nextRuns.joinToString("; ") { whenText(context, it) },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Network to use", style = MaterialTheme.typography.titleSmall)
            ChoiceRow("Wi-Fi only", state.network == NetworkPolicy.WIFI_ONLY) { actions.onSetNetwork(NetworkPolicy.WIFI_ONLY) }
            ChoiceRow("Mobile data only", state.network == NetworkPolicy.MOBILE_ONLY) { actions.onSetNetwork(NetworkPolicy.MOBILE_ONLY) }
            ChoiceRow("Wi-Fi or mobile data", state.network == NetworkPolicy.ANY) { actions.onSetNetwork(NetworkPolicy.ANY) }
            SwitchRow("Only while charging", state.requireCharging, onChange = actions.onSetRequireCharging)
            Text(
                "Android may start a backup a few minutes late to save battery, and waits until the network and charging " +
                    "conditions are met.",
                style = MaterialTheme.typography.bodySmall,
            )

            // ---- Contents and retention
            Text("What a backup contains", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Allow restoring with my passphrase", state.includePassphraseSlot, onChange = actions.onSetIncludePassphrase)
            Text(
                if (state.includePassphraseSlot) {
                    "On: anyone who obtains your backup could try to guess your passphrase offline. Use a long passphrase of several random words."
                } else {
                    "Off (safest): a backup can only be opened with your recovery code, which cannot be guessed. Keep it safe."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Keep last ${state.retention} backups", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { actions.onSetRetention(state.retention - 1) }) { Text("−") }
                OutlinedButton(onClick = { actions.onSetRetention(state.retention + 1) }) { Text("+") }
            }
        }

        // ---- Backups in Drive
        if (state.connected) {
            Text("Backups in Drive", style = MaterialTheme.typography.titleMedium)
            val list = state.remote
            when {
                list == null -> Text("Loading…")
                list.isEmpty() -> Text("None yet.")
                else -> list.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${whenText(context, b.createdAtMillis)} · ${Formatter.formatShortFileSize(context, b.sizeBytes)}",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { actions.onAskRestore(b) }, enabled = !state.busy) { Text("Restore") }
                    }
                }
            }
        }
        TextButton(onClick = actions.onBack) { Text("Back") }
    }

    state.confirmRestore?.let { b ->
        AlertDialog(
            onDismissRequest = actions.onCancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This replaces ALL SenTrack data on this phone with the backup from ${whenText(context, b.createdAtMillis)}. " +
                        "You will then unlock it with your recovery code or passphrase. This cannot be undone.",
                )
            },
            confirmButton = { TextButton(onClick = actions.onConfirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = actions.onCancelRestore) { Text("Cancel") } },
        )
    }
}
