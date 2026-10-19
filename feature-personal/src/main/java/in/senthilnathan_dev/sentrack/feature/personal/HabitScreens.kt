package `in`.senthilnathan_dev.sentrack.feature.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitLogEntity
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.DateField
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PickerField
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import `in`.senthilnathan_dev.sentrack.core.ui.TimeField
import `in`.senthilnathan_dev.sentrack.core.ui.formatDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** Habits with their logs, joined for the screens. */
internal class HabitsData(val habits: List<HabitEntity>, logs: List<HabitLogEntity>) {
    private val byHabit: Map<String, Map<LocalDate, Double>> =
        logs.groupBy { it.habitId }.mapValues { (_, list) -> list.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.amount } }

    fun logMap(habitId: String): Map<LocalDate, Double> = byHabit[habitId] ?: emptyMap()
}

internal fun HabitEntity.period(): HabitPeriod = runCatching { HabitPeriod.valueOf(period) }.getOrDefault(HabitPeriod.DAY)

internal fun HabitEntity.days(): Set<DayOfWeek> =
    scheduledDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet()

internal fun HabitEntity.rule() = HabitRule(period(), target, days(), LocalDate.ofEpochDay(startEpochDay))

private fun n(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(Locale.ENGLISH, v)

// ---------------------------------------------------------------------------------------------------------------------
// List

/** The Habits section: habits for the chosen day, tick them off, see streaks. */
@Composable
internal fun ColumnScope.HabitsContent(
    data: HabitsData,
    onLog: (habitId: String, day: LocalDate, amount: Double) -> Unit,
    onOpen: (String) -> Unit,
    onOpenArchived: () -> Unit,
) {
    var dayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var amountDialog by remember { mutableStateOf<HabitEntity?>(null) }
    val today = LocalDate.now()
    val day = LocalDate.ofEpochDay(dayEpoch)
    val active = data.habits.filterNot { it.archived }
    val archivedCount = data.habits.count { it.archived }

    // Day chooser: step back to fill in a missed day, or forward to today.
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { dayEpoch -= 1 }) { Text("‹ Earlier") }
        Text(
            if (day == today) "Today" else if (day == today.minusDays(1)) "Yesterday" else formatDate(dayEpoch),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { dayEpoch += 1 }, enabled = day.isBefore(today)) { Text("Later ›") }
    }

    if (active.isEmpty()) {
        Section("No habits yet", "Tap “New habit” to start one: a daily walk, three workouts a week, a monthly review, a yearly check-up.") {}
    }

    // Habits not yet met in their current period come first.
    val ordered = active.sortedBy { h ->
        val rule = h.rule()
        if (HabitEngine.isMet(rule, data.logMap(h.id), HabitEngine.periodStart(rule.period, day))) 1 else 0
    }
    ordered.forEach { h ->
        HabitCard(h, data.logMap(h.id), day, today, onOpen = { onOpen(h.id) }, onTick = {
            if (h.numeric) amountDialog = h
            else onLog(h.id, day, if ((data.logMap(h.id)[day] ?: 0.0) > 0.0) 0.0 else 1.0)
        })
    }
    if (archivedCount > 0) SecondaryButton("Archived habits ($archivedCount)", onClick = onOpenArchived, icon = Icons.Filled.Archive)

    amountDialog?.let { h ->
        var text by remember { mutableStateOf(data.logMap(h.id)[day]?.let { n(it) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { amountDialog = null },
            title = { Text(h.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How much on ${if (day == today) "today" else formatDate(dayEpoch)}${h.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}? Leave empty to clear.")
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("Amount") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onLog(h.id, day, text.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)
                    amountDialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { amountDialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HabitCard(h: HabitEntity, logs: Map<LocalDate, Double>, day: LocalDate, today: LocalDate, onOpen: () -> Unit, onTick: () -> Unit) {
    val rule = h.rule()
    val start = HabitEngine.periodStart(rule.period, day)
    val progress = HabitEngine.progress(rule, logs, start)
    val met = progress >= rule.target - 1e-9
    val streak = HabitEngine.currentStreak(rule, logs, today)
    val doneThatDay = (logs[day] ?: 0.0) > 0.0
    val scheduled = HabitEngine.isScheduled(rule, day)

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.clickable(onClick = onOpen).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { (progress / rule.target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        HabitEngine.progressText(rule, h.unit, progress).replace(rule.period.thisPeriod, if (day == today || rule.period != HabitPeriod.DAY) rule.period.thisPeriod else "that day"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Streak", tint = Color(0xFFE08E45), modifier = Modifier.size(16.dp))
                            Text("$streak ${rule.period.noun}${if (streak == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!scheduled) Text("Not scheduled on this day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (h.numeric) {
                IconAction(Icons.Filled.Add, "Log an amount for ${h.name}", onTick)
            } else {
                IconAction(
                    if (doneThatDay) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    if (doneThatDay) "Undo done for ${h.name}" else "Mark ${h.name} done",
                    onTick,
                    tint = if (doneThatDay || met) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
            }
        }
    }
}

/** Habits that were archived: kept with their history, easy to restore. */
@Composable
internal fun ArchivedHabitsScreen(data: HabitsData, onOpen: (String) -> Unit, onRestore: (HabitEntity) -> Unit, onBack: () -> Unit) {
    val archived = data.habits.filter { it.archived }
    AppScreen("Archived habits", onBack = onBack) {
        if (archived.isEmpty()) {
            Section("Nothing archived", "Archive a habit from its page to hide it without losing its history.") {}
        } else {
            Section("Archived", "Tap one to look at its history, or restore it.") {
                archived.forEachIndexed { i, h ->
                    if (i > 0) HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clickable { onOpen(h.id) }.padding(vertical = 8.dp)) {
                            Text(h.name, style = MaterialTheme.typography.bodyLarge)
                            Text(h.period().label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconAction(Icons.Filled.Unarchive, "Restore ${h.name}", { onRestore(h) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Detail

/** One habit: streaks, completion rate, and a calendar of what you did. Tap a day to change it. */
@Composable
internal fun HabitDetailScreen(
    habit: HabitEntity,
    logs: Map<LocalDate, Double>,
    onLog: (LocalDate, Double) -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onBack: () -> Unit,
) {
    val rule = habit.rule()
    val today = LocalDate.now()
    var amountDay by remember { mutableStateOf<LocalDate?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }

    val current = HabitEngine.currentStreak(rule, logs, today)
    val best = HabitEngine.bestStreak(rule, logs, today)
    val rate = HabitEngine.completionRate(rule, logs, today, lastN = when (rule.period) {
        HabitPeriod.DAY -> 30; HabitPeriod.WEEK -> 12; HabitPeriod.MONTH -> 12; HabitPeriod.QUARTER -> 8; HabitPeriod.YEAR -> 5
    })
    val total = HabitEngine.periodsMet(rule, logs, today)

    fun tap(date: LocalDate) {
        if (habit.numeric) amountDay = date else onLog(date, if ((logs[date] ?: 0.0) > 0.0) 0.0 else 1.0)
    }

    AppScreen(
        habit.name,
        onBack = onBack,
        actions = {
            IconAction(Icons.Filled.Edit, "Edit habit", onEdit)
            IconAction(
                if (habit.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                if (habit.archived) "Restore habit" else "Archive habit",
                { if (habit.archived) onToggleArchive() else confirmArchive = true },
            )
        },
    ) {
        Section(
            habit.period().label + " · " + if (habit.numeric) "${n(habit.target)}${habit.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""} per ${rule.period.noun}"
            else "${n(habit.target)} time${if (habit.target == 1.0) "" else "s"} per ${rule.period.noun}",
            habit.note,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Current streak", "$current", Modifier.weight(1f))
                StatTile("Best streak", "$best", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Success rate", rate?.let { "${(it * 100).toInt()}%" } ?: "–", Modifier.weight(1f))
                StatTile("${rule.period.noun.replaceFirstChar { it.uppercase() }}s met", "$total", Modifier.weight(1f))
            }
            if (rate != null) Text("Success rate covers your last finished ${rule.period.noun}s.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (rule.period == HabitPeriod.DAY) {
            Section("Last 12 weeks", "Tap a day to change it. Darker means closer to the goal.") {
                DayHeatmap(rule, logs, today, weeks = 12, onDayClick = ::tap)
            }
        } else {
            Section("Recent ${rule.period.noun}s") {
                PeriodBars(rule, logs, today, count = 8)
                Text("Tap “Log” below to record today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton(
                    if ((logs[today] ?: 0.0) > 0.0 && !habit.numeric) "Undo today" else "Log for today",
                    onClick = { tap(today) },
                    icon = Icons.Filled.Check,
                )
            }
        }
    }

    amountDay?.let { date ->
        var text by remember(date) { mutableStateOf(logs[date]?.let { n(it) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { amountDay = null },
            title = { Text(formatDate(date.toEpochDay())) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Amount${habit.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}") },
                )
            },
            confirmButton = { TextButton(onClick = { onLog(date, text.trim().replace(',', '.').toDoubleOrNull() ?: 0.0); amountDay = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { amountDay = null }) { Text("Cancel") } },
        )
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive ${habit.name}?") },
            text = { Text("It leaves your habit list but keeps all its history, and any reminder stops. You can restore it from Archived habits.") },
            confirmButton = { TextButton(onClick = { confirmArchive = false; onToggleArchive() }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** A grid of days, oldest at the left, one column per week (Monday at the top). */
@Composable
private fun DayHeatmap(rule: HabitRule, logs: Map<LocalDate, Double>, today: LocalDate, weeks: Int, onDayClick: (LocalDate) -> Unit) {
    val firstColumn = HabitEngine.periodStart(HabitPeriod.WEEK, today).minusWeeks((weeks - 1).toLong())
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val full = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in 0 until weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (dow in 0..6) {
                        val date = firstColumn.plusWeeks(w.toLong()).plusDays(dow.toLong())
                        if (date.isAfter(today)) {
                            Spacer(Modifier.size(20.dp))
                        } else {
                            val fraction = ((logs[date] ?: 0.0) / rule.target).coerceIn(0.0, 1.0).toFloat()
                            val color = if (fraction > 0f) full.copy(alpha = 0.3f + 0.7f * fraction) else empty
                            val scheduled = HabitEngine.isScheduled(rule, date)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(if (scheduled) color else color.copy(alpha = color.alpha * 0.4f), RoundedCornerShape(4.dp))
                                    .clickable { onDayClick(date) },
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Rows are Monday to Sunday. Faded squares are days this habit does not apply to.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The last few weeks, months, quarters or years as bars: how close each came to the target. */
@Composable
private fun PeriodBars(rule: HabitRule, logs: Map<LocalDate, Double>, today: LocalDate, count: Int) {
    val periods = generateSequence(HabitEngine.periodStart(rule.period, today)) { prev ->
        when (rule.period) {
            HabitPeriod.WEEK -> prev.minusWeeks(1)
            HabitPeriod.MONTH -> prev.minusMonths(1)
            HabitPeriod.QUARTER -> prev.minusMonths(3)
            HabitPeriod.YEAR -> prev.minusYears(1)
            HabitPeriod.DAY -> prev.minusDays(1)
        }
    }.take(count).toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        periods.forEach { start ->
            val progress = HabitEngine.progress(rule, logs, start)
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (start == HabitEngine.periodStart(rule.period, today)) "This ${rule.period.noun}" else periodLabel(rule.period, start),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("${n(progress)} of ${n(rule.target)}", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(progress = { (progress / rule.target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun periodLabel(period: HabitPeriod, start: LocalDate): String = when (period) {
    HabitPeriod.DAY -> formatDate(start.toEpochDay())
    HabitPeriod.WEEK -> "Week of ${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    HabitPeriod.MONTH -> "${start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${start.year}"
    HabitPeriod.QUARTER -> "Q${(start.monthValue - 1) / 3 + 1} ${start.year}"
    HabitPeriod.YEAR -> "${start.year}"
}

// ---------------------------------------------------------------------------------------------------------------------
// Editor

/** What the editor hands back: the habit, plus when to remind (null = no reminder). */
internal class HabitDraft(val habit: HabitEntity, val reminderTime: LocalTime?, val reminderDay: DayOfWeek?)

/** Create or edit a habit. Reminders are available for daily and weekly habits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HabitEditorScreen(
    existing: HabitEntity?,
    initialReminderTime: LocalTime?,
    initialReminderDay: DayOfWeek?,
    onSave: (HabitDraft) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var period by rememberSaveable { mutableStateOf(existing?.period() ?: HabitPeriod.DAY) }
    var numeric by rememberSaveable { mutableStateOf(existing?.numeric ?: false) }
    var unit by rememberSaveable { mutableStateOf(existing?.unit.orEmpty()) }
    var target by rememberSaveable { mutableStateOf(existing?.let { n(it.target) } ?: "1") }
    var days by rememberSaveable { mutableStateOf((existing?.days() ?: emptySet()).map { it.value }.toSet()) }
    var start by rememberSaveable { mutableStateOf(existing?.startEpochDay ?: LocalDate.now().toEpochDay()) }
    var remind by rememberSaveable { mutableStateOf(initialReminderTime != null) }
    var timeSecs by rememberSaveable { mutableStateOf((initialReminderTime ?: LocalTime.of(20, 0)).toSecondOfDay()) }
    var weekday by rememberSaveable { mutableStateOf((initialReminderDay ?: DayOfWeek.SUNDAY).value) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val t = target.trim().replace(',', '.').toDoubleOrNull()
        when {
            name.isBlank() -> error = "Give the habit a name."
            t == null || t <= 0.0 -> error = "Enter a target greater than zero."
            else -> {
                error = null
                onSave(
                    HabitDraft(
                        habit = HabitEntity(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            note = note.trim().ifEmpty { null },
                            period = period.name,
                            numeric = numeric,
                            unit = if (numeric) unit.trim().ifEmpty { null } else null,
                            target = if (numeric) t else t.toLong().coerceAtLeast(1).toDouble(),
                            scheduledDays = if (period == HabitPeriod.DAY) days.sorted().joinToString(",") else "",
                            startEpochDay = start,
                            archived = existing?.archived ?: false,
                            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                        ),
                        reminderTime = if (remind && (period == HabitPeriod.DAY || period == HabitPeriod.WEEK)) LocalTime.ofSecondOfDay(timeSecs.toLong()) else null,
                        reminderDay = if (period == HabitPeriod.WEEK) DayOfWeek.of(weekday) else null,
                    ),
                )
            }
        }
    }

    AppScreen(
        if (existing == null) "New habit" else "Edit habit",
        onBack = onBack,
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", onClick = ::submit, icon = Icons.Filled.Check)
            }
        },
    ) {
        Section("Habit") {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (for example Walk, Read, Review budget)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Why it matters (optional)") }, modifier = Modifier.fillMaxWidth())
        }

        Section("How often") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitPeriod.entries.forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) }) }
            }
            if (period == HabitPeriod.DAY) {
                Text("On these days (none selected means every day)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = d.value in days,
                            onClick = { days = if (d.value in days) days - d.value else days + d.value },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                        )
                    }
                }
            }
            DateField("Start tracking from", start) { start = it }
        }

        Section("Goal") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !numeric, onClick = { numeric = false }, label = { Text("Done or not") })
                FilterChip(selected = numeric, onClick = { numeric = true }, label = { Text("A number") })
            }
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text(if (numeric) "Target per ${period.noun}" else "Times per ${period.noun}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (numeric) {
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (glasses, pages, km…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        if (period == HabitPeriod.DAY || period == HabitPeriod.WEEK) {
            Section("Reminder") {
                SwitchRow(title = "Remind me", subtitle = "A notification at this time, even when SenTrack is locked.", checked = remind, onChange = { remind = it })
                if (remind) {
                    if (period == HabitPeriod.WEEK) {
                        PickerField("On", DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, Locale.ENGLISH), DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) to it.value }) { weekday = it }
                    }
                    TimeField("At", LocalTime.ofSecondOfDay(timeSecs.toLong())) { timeSecs = it.toSecondOfDay() }
                }
            }
        } else {
            Section("Reminder", "Reminders for monthly, quarterly and yearly habits are coming later. For now, add a reminder from the bell icon.") {}
        }
    }
}
