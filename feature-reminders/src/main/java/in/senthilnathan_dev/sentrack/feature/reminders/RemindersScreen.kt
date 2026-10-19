package `in`.senthilnathan_dev.sentrack.feature.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.alerts.AlertStyle
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderCenter
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderRepository
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderTiming
import `in`.senthilnathan_dev.sentrack.core.alerts.toDef
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderEntity
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.LocalExternalFlowStarter
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import kotlinx.coroutines.launch

private sealed interface Target {
    data object New : Target
    data class Edit(val id: String) : Target
}

/**
 * Reminders: everything that will ring, with the settings that make it work (notification permission, exact timing,
 * whether notifications may show text). Reminders ring even while the vault is locked.
 */
@Composable
fun RemindersScreen(repo: ReminderRepository, center: ReminderCenter, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val beginExternalFlow = LocalExternalFlowStarter.current
    val reminders by remember(repo) { repo.reminders() }.collectAsState(initial = emptyList())
    var target by remember { mutableStateOf<Target?>(null) }
    // Bumped when we come back from a system settings screen, so the status lines below are re-read.
    var recheck by remember { mutableIntStateOf(0) }
    var testSent by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(center.showText) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { recheck++ }

    BackHandler { if (target != null) target = null else onBack() }

    when (val t = target) {
        null -> Unit
        Target.New -> {
            ReminderEditor(
                existing = null,
                onSave = { r -> scope.launch { repo.save(r); target = null } },
                onDelete = null,
                onBack = { target = null },
            )
            return
        }
        is Target.Edit -> {
            val existing = reminders.firstOrNull { it.id == t.id }
            if (existing == null) target = null else ReminderEditor(
                existing = existing,
                onSave = { r -> scope.launch { repo.save(r); target = null } },
                onDelete = { scope.launch { repo.delete(existing.id); target = null } },
                onBack = { target = null },
            )
            return
        }
    }

    val notificationsOn = remember(recheck) { center.notificationsAllowed() }
    val exactOn = remember(recheck) { center.canScheduleExact() }
    val active = reminders.filter { !it.completed }
    val done = reminders.filter { it.completed }

    AppScreen(
        "Reminders",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { target = Target.New },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New reminder") },
            )
        },
    ) {
        if (!notificationsOn) {
            Section("Notifications are off", "SenTrack cannot alert you until you allow notifications.") {
                SecondaryButton("Allow notifications", onClick = {
                    beginExternalFlow()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, icon = Icons.Filled.Notifications)
            }
        }

        Section("Your reminders") {
            if (active.isEmpty()) {
                Text("No reminders yet. Tap “New reminder”: bills, medicines, birthdays, anything.")
            }
            active.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider()
                ReminderRow(r, onClick = { target = Target.Edit(r.id) }, onToggle = { on -> scope.launch { repo.setEnabled(r.id, on) } })
            }
        }

        if (done.isNotEmpty()) {
            Section("Finished", "One-time reminders that have already rung.") {
                done.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider()
                    ReminderRow(r, onClick = { target = Target.Edit(r.id) }, onToggle = null)
                }
            }
        }

        Section("Settings", "Reminders ring even when SenTrack is locked.") {
            SwitchRow(
                title = "Show reminder text in notifications",
                subtitle = if (showText) {
                    "On: the title appears on the lock screen and is kept outside the encrypted database so it can ring while locked."
                } else {
                    "Off (private): notifications say only “SenTrack reminder”. Open the app to see the details."
                },
                checked = showText,
                onChange = { on ->
                    showText = on
                    center.showText = on
                    // Re-write every alarm entry so titles are stored (or removed) to match.
                    scope.launch { runCatching { repo.reconcile() } }
                },
            )
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (exactOn) "Exact timing: allowed" else "Exact timing: not allowed, so reminders may be a few minutes late.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!exactOn) {
                    SecondaryButton("Allow exact timing", onClick = {
                        beginExternalFlow()
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    }, icon = Icons.Filled.Alarm)
                }
                SecondaryButton("Send a test notification", onClick = { center.showTest(); testSent = true }, enabled = notificationsOn, icon = Icons.Filled.NotificationsActive)
                if (testSent) Message("Sent. If you did not see it, check your phone's notification settings for SenTrack.", MessageKind.INFO)
            }
        }
        Spacer(Modifier.height(72.dp))   // keeps the last card clear of the floating button
    }
}

@Composable
private fun ReminderRow(r: ReminderEntity, onClick: () -> Unit, onToggle: ((Boolean) -> Unit)?) {
    val context = LocalContext.current
    val def = remember(r) { r.toDef() }
    val repeat = remember(r) { ScheduleSpec.decode(r.repeatEncoded) }
    val next = remember(r) { ReminderTiming.nextFire(def, System.currentTimeMillis()) }
    val subtitle = when {
        r.completed -> "Rang " + DateUtils.formatDateTime(context, r.oneOffAtMillis ?: 0, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
        !r.enabled -> "Paused" + (repeat?.let { " · ${it.describe()}" } ?: "")
        else -> {
            val whenText = next?.let { DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH) }
            (repeat?.describe() ?: "Once") + (whenText?.let { " · next $it" } ?: "")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (def.style == AlertStyle.ALARM) Icons.Filled.Alarm else Icons.Filled.Notifications,
            contentDescription = if (def.style == AlertStyle.ALARM) "Alarm" else "Notification",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onToggle != null) Switch(checked = r.enabled, onCheckedChange = onToggle)
    }
}
