package `in`.senthilnathan_dev.sentrack.backup

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.sync.NetworkPolicy
import `in`.senthilnathan_dev.sentrack.core.sync.RemoteBackup
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.ChoiceRow
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

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
    val onSetSchedule: (ScheduleSpec) -> Unit,
    val onSetCustomCron: (String) -> Unit,
    val onSetNetwork: (NetworkPolicy) -> Unit,
    val onSetRequireCharging: (Boolean) -> Unit,
    val onAskRestore: (RemoteBackup) -> Unit,
    val onCancelRestore: () -> Unit,
    val onConfirmRestore: () -> Unit,
    val onBack: () -> Unit,
)

/** Backup screen: account, back up now, automatic schedule, contents, and the backups stored in Drive. */
@Composable
fun BackupScreen(state: BackupUiState, actions: BackupActions) {
    val context = LocalContext.current
    AppScreen("Backup", onBack = actions.onBack) {
        Message(state.message, MessageKind.SUCCESS)
        Message(state.error, MessageKind.ERROR)

        AccountSection(state, actions)

        if (state.vaultExists && state.connected) {
            Section("Back up now") {
                Text(
                    if (state.lastSuccessMillis > 0) "Last backup: ${whenText(context, state.lastSuccessMillis)}" else "No backup made yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Message(state.lastError, MessageKind.ERROR)
                PrimaryButton(if (state.busy) "Working…" else "Back up now", onClick = actions.onBackupNow, busy = state.busy, icon = Icons.Filled.CloudUpload)
            }

            AutomaticSection(state, actions)

            Section("What a backup contains", "Backups hold only data that is already encrypted on this phone. Google cannot read them.") {
                SwitchRow(
                    title = "Allow restoring with my passphrase",
                    subtitle = if (state.includePassphraseSlot) {
                        "On: someone who obtains your backup could try to guess your passphrase offline. Use a long passphrase of several random words."
                    } else {
                        "Off (safest): a backup can only be opened with your recovery code, which cannot be guessed."
                    },
                    checked = state.includePassphraseSlot,
                    onChange = actions.onSetIncludePassphrase,
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Keep the last ${state.retention} backups", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconAction(Icons.Filled.Remove, "Keep fewer backups", { actions.onSetRetention(state.retention - 1) })
                    IconAction(Icons.Filled.Add, "Keep more backups", { actions.onSetRetention(state.retention + 1) })
                }
            }
        }

        if (state.connected) BackupsInDriveSection(state, actions)
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

@Composable
private fun AccountSection(state: BackupUiState, actions: BackupActions) {
    Section(
        "Google account",
        "Backups go to your own Google Drive, in a hidden app folder that only SenTrack can open.",
    ) {
        if (state.connected) {
            Text(
                state.accountEmail ?: "Connected (account not recorded: choose it again to see it here)",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onChooseAccount, enabled = !state.busy) { Icon(Icons.Filled.SwitchAccount, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Switch account") }
                IconAction(Icons.Filled.LinkOff, "Disconnect Google Drive", actions.onDisconnect, enabled = !state.busy)
            }
            Text(
                "Switching shows the other account's backups. Backups already made stay in the first account's Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PrimaryButton(if (state.busy) "Connecting…" else "Choose Google account", onClick = actions.onChooseAccount, busy = state.busy, icon = Icons.Filled.AccountCircle)
        }
    }
}

// ---- Automatic backups

private enum class Repeat(val label: String) {
    HOURS("Every few hours"),
    DAILY("Every day"),
    WEEKLY("On chosen days of the week"),
    MONTHLY("Once a month"),
    CUSTOM("Advanced (custom schedule)"),
}

private fun repeatOf(spec: ScheduleSpec) = when (spec) {
    is ScheduleSpec.EveryHours -> Repeat.HOURS
    is ScheduleSpec.Daily -> Repeat.DAILY
    is ScheduleSpec.Weekly -> Repeat.WEEKLY
    is ScheduleSpec.Monthly -> Repeat.MONTHLY
    is ScheduleSpec.Yearly -> Repeat.CUSTOM   // not offered for backups; shown with the advanced option
    is ScheduleSpec.MonthlyClamped -> Repeat.MONTHLY
    is ScheduleSpec.Custom -> Repeat.CUSTOM
}

private fun timeOf(spec: ScheduleSpec): LocalTime = when (spec) {
    is ScheduleSpec.Daily -> spec.time
    is ScheduleSpec.Weekly -> spec.time
    is ScheduleSpec.Monthly -> spec.time
    is ScheduleSpec.MonthlyClamped -> spec.time
    else -> LocalTime.of(2, 0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomaticSection(state: BackupUiState, actions: BackupActions) {
    val context = LocalContext.current
    val spec = state.schedule
    // "Advanced" is a mode of the editor: the saved schedule only changes once a valid expression is typed.
    var advanced by remember { mutableStateOf(spec is ScheduleSpec.Custom) }
    val repeat = if (advanced) Repeat.CUSTOM else repeatOf(spec)

    Section("Automatic backups") {
        SwitchRow(
            title = "Back up automatically",
            subtitle = if (state.scheduleEnabled) spec.describe() else "Off: backups happen only when you tap Back up now.",
            checked = state.scheduleEnabled,
            onChange = actions.onSetScheduleEnabled,
        )
        if (!state.scheduleEnabled) return@Section

        state.nextRuns.firstOrNull()?.let {
            Message("Next backup: about ${whenText(context, it)}", MessageKind.INFO)
        }
        HorizontalDivider()

        Text("How often", style = MaterialTheme.typography.titleSmall)
        Repeat.entries.forEach { option ->
            ChoiceRow(option.label, selected = repeat == option) {
                advanced = option == Repeat.CUSTOM
                when (option) {
                    Repeat.HOURS -> actions.onSetSchedule(ScheduleSpec.EveryHours((spec as? ScheduleSpec.EveryHours)?.hours ?: 6))
                    Repeat.DAILY -> actions.onSetSchedule(ScheduleSpec.Daily(timeOf(spec)))
                    Repeat.WEEKLY -> actions.onSetSchedule(
                        ScheduleSpec.Weekly((spec as? ScheduleSpec.Weekly)?.days ?: setOf(DayOfWeek.SUNDAY), timeOf(spec)),
                    )
                    Repeat.MONTHLY -> actions.onSetSchedule(
                        when (spec) {
                            is ScheduleSpec.Monthly -> spec
                            is ScheduleSpec.MonthlyClamped -> spec
                            else -> ScheduleSpec.Monthly(1, timeOf(spec))
                        },
                    )
                    Repeat.CUSTOM -> Unit
                }
            }
        }

        HorizontalDivider()
        when (repeat) {
            Repeat.HOURS -> {
                Text("Repeat every", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleSpec.HOUR_CHOICES.forEach { h ->
                        FilterChip(
                            selected = (spec as? ScheduleSpec.EveryHours)?.hours == h,
                            onClick = { actions.onSetSchedule(ScheduleSpec.EveryHours(h)) },
                            label = { Text(if (h == 1) "1 hour" else "$h hours") },
                        )
                    }
                }
            }
            Repeat.DAILY -> TimeButton("Time:", timeOf(spec)) { actions.onSetSchedule(ScheduleSpec.Daily(it)) }
            Repeat.WEEKLY -> {
                val weekly = spec as? ScheduleSpec.Weekly
                Text("On these days", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = weekly?.days?.contains(day) == true
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) weekly.days - day else (weekly?.days ?: emptySet()) + day
                                if (next.isNotEmpty()) actions.onSetSchedule(ScheduleSpec.Weekly(next, timeOf(spec)))
                            },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                        )
                    }
                }
                TimeButton("Time:", timeOf(spec)) { t -> weekly?.let { actions.onSetSchedule(it.copy(time = t)) } }
            }
            Repeat.MONTHLY -> {
                val day = when (spec) {
                    is ScheduleSpec.Monthly -> spec.dayOfMonth
                    is ScheduleSpec.MonthlyClamped -> spec.dayOfMonth
                    else -> 1
                }
                /** Days 1 to 28 are exact; 29 to 31 mean "that day, or the last day of a shorter month". */
                fun setDay(newDay: Int) = actions.onSetSchedule(
                    if (newDay <= 28) ScheduleSpec.Monthly(newDay, timeOf(spec)) else ScheduleSpec.MonthlyClamped(newDay, timeOf(spec)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (day == 31) "On the last day of each month" else "On the ${ScheduleSpec.ordinal(day)} of each month",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    )
                    IconAction(Icons.Filled.Remove, "Earlier day of the month", { setDay((day - 1).coerceAtLeast(1)) })
                    IconAction(Icons.Filled.Add, "Later day of the month", { setDay((day + 1).coerceAtMost(31)) })
                }
                if (day > 28) {
                    Text(
                        "In a month with fewer days, the backup runs on that month's last day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TimeButton("Time:", timeOf(spec)) { t ->
                    actions.onSetSchedule(if (day <= 28) ScheduleSpec.Monthly(day, t) else ScheduleSpec.MonthlyClamped(day, t))
                }
            }
            Repeat.CUSTOM -> OutlinedTextField(
                value = state.customCron,
                onValueChange = actions.onSetCustomCron,
                label = { Text("Custom schedule") },
                supportingText = {
                    Text(
                        state.customCronError
                            ?: "For experts: 5 parts (minute hour day month weekday). Example: 30 21 * * MON-FRI means weekdays at 9:30 PM. " +
                            "At least 15 minutes apart.",
                    )
                },
                isError = state.customCronError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()
        Text("Network", style = MaterialTheme.typography.titleSmall)
        ChoiceRow("Wi-Fi only", state.network == NetworkPolicy.WIFI_ONLY) { actions.onSetNetwork(NetworkPolicy.WIFI_ONLY) }
        ChoiceRow("Mobile data only", state.network == NetworkPolicy.MOBILE_ONLY) { actions.onSetNetwork(NetworkPolicy.MOBILE_ONLY) }
        ChoiceRow("Wi-Fi or mobile data", state.network == NetworkPolicy.ANY) { actions.onSetNetwork(NetworkPolicy.ANY) }
        SwitchRow(title = "Only while charging", checked = state.requireCharging, onChange = actions.onSetRequireCharging)
        Text(
            "Times use this phone's clock. Android may start a backup a few minutes late to save battery, and waits until " +
                "the network and charging conditions are met.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A button showing the chosen time; tapping it opens a clock to change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SecondaryButton("$label ${ScheduleSpec.formatTime(time)}", onClick = { open = true })
    if (open) {
        val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = { onChange(LocalTime.of(picker.hour, picker.minute)); open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackupsInDriveSection(state: BackupUiState, actions: BackupActions) {
    val context = LocalContext.current
    Section("Backups in Drive") {
        val list = state.remote
        when {
            list == null -> Text("Loading…")
            list.isEmpty() -> Text("None yet.")
            else -> list.forEachIndexed { index, b ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${whenText(context, b.createdAtMillis)} · ${Formatter.formatShortFileSize(context, b.sizeBytes)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconAction(Icons.Filled.Restore, "Restore this backup", { actions.onAskRestore(b) }, enabled = !state.busy)
                }
            }
        }
    }
}
