package `in`.senthilnathan_dev.sentrack.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.alerts.AlertStyle
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderTiming
import `in`.senthilnathan_dev.sentrack.core.alerts.toDef
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderEntity
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.ChoiceRow
import `in`.senthilnathan_dev.sentrack.core.ui.DateField
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.TimeField
import `in`.senthilnathan_dev.sentrack.core.ui.formatDate
import `in`.senthilnathan_dev.sentrack.core.ui.formatTime12
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private enum class RepeatMode(val label: String) {
    NONE("Does not repeat"),
    DAILY("Every day"),
    WEEKLY("On chosen days of the week"),
    MONTHLY("Every month on this date"),
    YEARLY("Every year on this date"),
    KEEP("Keep the existing schedule"),
}

/**
 * Create or edit a reminder: what, when, how it rings, and how often. Pick one date and time; the repeat choices
 * (daily, weekly, monthly, yearly) are worked out from them, so there is never a schedule to type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderEditor(
    existing: ReminderEntity?,
    onSave: (ReminderEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val existingSpec = existing?.let { ScheduleSpec.decode(it.repeatEncoded) }
    val initial: LocalDateTime = run {
        val at = existing?.oneOffAtMillis
            ?: existing?.toDef()?.let { ReminderTiming.nextFire(it, System.currentTimeMillis()) }
        at?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            ?: LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
    }

    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var style by rememberSaveable { mutableStateOf(existing?.style?.let { runCatching { AlertStyle.valueOf(it) }.getOrNull() } ?: AlertStyle.NOTIFICATION) }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate().toEpochDay()) }
    var timeSecs by rememberSaveable { mutableStateOf(initial.toLocalTime().toSecondOfDay()) }
    var mode by rememberSaveable {
        mutableStateOf(
            when (existingSpec) {
                null -> RepeatMode.NONE
                is ScheduleSpec.Daily -> RepeatMode.DAILY
                is ScheduleSpec.Weekly -> RepeatMode.WEEKLY
                is ScheduleSpec.Monthly -> RepeatMode.MONTHLY
                is ScheduleSpec.Yearly -> RepeatMode.YEARLY
                else -> RepeatMode.KEEP
            },
        )
    }
    var weekdays by rememberSaveable {
        mutableStateOf(
            ((existingSpec as? ScheduleSpec.Weekly)?.days ?: setOf(initial.dayOfWeek)).map { it.value }.toSet(),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val time = LocalTime.ofSecondOfDay(timeSecs.toLong())
    val localDate = LocalDate.ofEpochDay(date)

    /** The repeating schedule implied by the chosen date, time and repeat choice (null = one-off). */
    fun spec(): ScheduleSpec? = when (mode) {
        RepeatMode.NONE -> null
        RepeatMode.DAILY -> ScheduleSpec.Daily(time)
        RepeatMode.WEEKLY -> ScheduleSpec.Weekly(weekdays.map { DayOfWeek.of(it) }.toSet(), time)
        RepeatMode.MONTHLY -> ScheduleSpec.Monthly(localDate.dayOfMonth.coerceAtMost(28), time)
        RepeatMode.YEARLY -> ScheduleSpec.Yearly(localDate.monthValue, localDate.dayOfMonth, time)
        RepeatMode.KEEP -> existingSpec
    }

    fun submit() {
        val repeat = spec()
        val at = LocalDateTime.of(localDate, time).atZone(zone).toInstant().toEpochMilli()
        when {
            title.isBlank() -> error = "Give the reminder a title."
            mode == RepeatMode.WEEKLY && weekdays.isEmpty() -> error = "Choose at least one day."
            repeat == null && at <= System.currentTimeMillis() -> error = "Pick a time in the future."
            else -> {
                error = null
                onSave(
                    ReminderEntity(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        note = note.trim().ifEmpty { null },
                        ownerKind = existing?.ownerKind ?: "standalone",
                        ownerId = existing?.ownerId,
                        style = style.name,
                        oneOffAtMillis = if (repeat == null) at else null,
                        repeatEncoded = repeat?.encode(),
                        enabled = true,
                        completed = false,
                        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    AppScreen(
        if (existing == null) "New reminder" else "Edit reminder",
        onBack = onBack,
        actions = { if (onDelete != null) IconAction(Icons.Filled.Delete, "Delete this reminder", { confirmDelete = true }) },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", onClick = ::submit, icon = Icons.Filled.Check)
            }
        },
    ) {
        Section("What") {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Remind me to…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional, stays private)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("When") {
            DateField(if (mode == RepeatMode.NONE) "Date" else "Starting from", date) { date = it }
            TimeField("Time", time) { timeSecs = it.toSecondOfDay() }
        }

        Section("Repeat") {
            RepeatMode.entries.filter { it != RepeatMode.KEEP || existingSpec != null }.forEach { m ->
                ChoiceRow(
                    label = if (m == RepeatMode.KEEP) "Keep: ${existingSpec?.describe()}" else m.label,
                    selected = mode == m,
                    subtitle = when (m) {
                        RepeatMode.MONTHLY -> if (localDate.dayOfMonth > 28) "Runs on the 28th, because not every month has a ${localDate.dayOfMonth}th." else null
                        RepeatMode.YEARLY -> "On ${localDate.dayOfMonth} ${localDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
                        else -> null
                    },
                ) { mode = m }
            }
            if (mode == RepeatMode.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = d.value in weekdays,
                            onClick = { weekdays = if (d.value in weekdays) weekdays - d.value else weekdays + d.value },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                        )
                    }
                }
            }
            val preview = spec()
            Text(
                if (preview != null) preview.describe() else "${formatDate(date)} at ${formatTime12(time)}",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Section("How it rings") {
            AlertStyle.entries.forEach { s ->
                ChoiceRow(
                    label = s.label,
                    selected = style == s,
                    subtitle = if (s == AlertStyle.ALARM) "Rings like a clock alarm and is never delayed by battery saving." else "A normal notification.",
                ) { style = s }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            text = { Text("It will no longer ring. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
