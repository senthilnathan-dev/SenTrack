package `in`.senthilnathan_dev.sentrack.backup

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import `in`.senthilnathan_dev.sentrack.core.sync.RemoteBackup
import `in`.senthilnathan_dev.sentrack.ui.vault.ErrorText
import `in`.senthilnathan_dev.sentrack.ui.vault.ScreenColumn

private fun whenText(context: android.content.Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

/** Backup screen: connect Google Drive, back up now, choose what a backup contains, restore. */
@Composable
fun BackupScreen(
    state: BackupUiState,
    onConnect: () -> Unit,
    onBackupNow: () -> Unit,
    onSetRetention: (Int) -> Unit,
    onSetIncludePassphrase: (Boolean) -> Unit,
    onAskRestore: (RemoteBackup) -> Unit,
    onCancelRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    ScreenColumn {
        Text("Backup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Backups are stored in a hidden app folder in your own Google Drive. They contain only data that is already " +
                "encrypted on this phone; Google cannot read them.",
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        ErrorText(state.error)

        if (!state.connected) {
            Button(onClick = onConnect, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Connecting…" else "Connect Google Drive")
            }
        } else {
            Text("Google Drive: connected", style = MaterialTheme.typography.titleMedium)
        }

        if (state.vaultExists && state.connected) {
            Text(
                if (state.lastSuccessMillis > 0) "Last backup: ${whenText(context, state.lastSuccessMillis)}" else "No backup made yet",
            )
            state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onBackupNow, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Working…" else "Back up now")
            }

            Text("What a backup contains", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Allow restoring with my passphrase", modifier = Modifier.weight(1f))
                Switch(checked = state.includePassphraseSlot, onCheckedChange = onSetIncludePassphrase)
            }
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
                OutlinedButton(onClick = { onSetRetention(state.retention - 1) }) { Text("−") }
                OutlinedButton(onClick = { onSetRetention(state.retention + 1) }) { Text("+") }
            }
        }

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
                        TextButton(onClick = { onAskRestore(b) }, enabled = !state.busy) { Text("Restore") }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }

    state.confirmRestore?.let { b ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This replaces ALL SenTrack data on this phone with the backup from ${whenText(context, b.createdAtMillis)}. " +
                        "You will then unlock it with your recovery code or passphrase. This cannot be undone.",
                )
            },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancel") } },
        )
    }
}
