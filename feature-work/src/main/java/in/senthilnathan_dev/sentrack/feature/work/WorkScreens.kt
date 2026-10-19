package `in`.senthilnathan_dev.sentrack.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.work.AttendanceEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.CompanyEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.LeaveEntryEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.LeaveTypeEntity
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
import `in`.senthilnathan_dev.sentrack.core.ui.formatDate
import `in`.senthilnathan_dev.sentrack.core.ui.formatTime12
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private fun localTime(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

private fun hoursText(millis: Long): String {
    val minutes = millis / 60_000
    return "${minutes / 60}h ${minutes % 60}m"
}

/** Picks which company the Today and Leave pages are showing. Hidden when there is only one. */
@Composable
internal fun CompanyPicker(companies: List<CompanyEntity>, selected: CompanyEntity?, onSelect: (String) -> Unit) {
    if (companies.size > 1 && selected != null) {
        PickerField("Company", selected.name, companies.map { it.name to it.id }, onSelect = onSelect)
    }
}

@Composable
internal fun ColumnScope.NoCompanyHint(onAdd: () -> Unit) {
    Section("Add your company first", "Attendance and leave are kept per company, so you can move jobs without mixing records up.") {
        PrimaryButton("Add a company", onClick = onAdd, icon = Icons.Filled.Add)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Today: punch in / out

@Composable
internal fun ColumnScope.TodayContent(
    company: CompanyEntity,
    records: List<AttendanceEntity>,
    notice: String?,
    busy: Boolean,
    onPunch: () -> Unit,
    onDelete: (AttendanceEntity) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    val todays = records.filter { it.dayEpoch == today }.sortedBy { it.inMillis }
    val open = todays.firstOrNull { it.outMillis == null }
    val workedToday = todays.sumOf { (it.outMillis ?: System.currentTimeMillis()) - it.inMillis }

    Section(if (open == null) "Not punched in" else "Working since ${formatTime12(localTime(open.inMillis))}") {
        if (todays.isNotEmpty()) Text("Today: ${hoursText(workedToday)}${if (open != null) " so far" else ""}", style = MaterialTheme.typography.titleMedium)
        PrimaryButton(
            if (open == null) "Punch in" else "Punch out",
            onClick = onPunch, busy = busy,
            icon = if (open == null) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
        )
        Message(notice, MessageKind.INFO)
        Text(
            "Your location is noted only at the moment you tap, and only if you allow it. It stays on this phone.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val byDay = records.groupBy { it.dayEpoch }.toSortedMap(compareByDescending<Long> { it }).entries.take(14)
    if (byDay.isNotEmpty()) {
        Section("Recent days") {
            byDay.forEachIndexed { i, (day, list) ->
                if (i > 0) HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (day == today) "Today" else formatDate(day), style = MaterialTheme.typography.bodyLarge)
                        Text(hoursText(list.sumOf { (it.outMillis ?: it.inMillis) - it.inMillis }), style = MaterialTheme.typography.bodyLarge)
                    }
                    list.sortedBy { it.inMillis }.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${formatTime12(localTime(r.inMillis))} – ${r.outMillis?.let { formatTime12(localTime(it)) } ?: "still in"}" +
                                    if (r.inLat != null) " · location noted" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconAction(Icons.Filled.Delete, "Delete this punch", { onDelete(r) })
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Leave

@Composable
internal fun ColumnScope.LeaveContent(
    company: CompanyEntity,
    types: List<LeaveTypeEntity>,
    leaves: List<LeaveEntryEntity>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val today = LocalDate.now()
    val year = LeaveMath.leaveYear(today, company.leaveYearStartMonth)
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val shown = YearMonth.parse(month)

    Section("Leave balance ${LeaveMath.leaveYearLabel(year)}") {
        if (types.isEmpty()) Text("No leave types yet. Add them on the company page.", style = MaterialTheme.typography.bodyMedium)
        types.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider()
            val used = leaves.filter { it.leaveTypeId == t.id }.sumOf { LeaveMath.daysWithin(it, year) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(t.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${LeaveMath.format(used)} used of ${LeaveMath.format(t.yearlyQuotaDays)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${LeaveMath.format(t.yearlyQuotaDays - used)} left", style = MaterialTheme.typography.titleMedium, color = if (t.yearlyQuotaDays - used < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }

    Section("Calendar") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconAction(Icons.Filled.ChevronLeft, "Previous month", { month = shown.minusMonths(1).toString() })
            Text("${shown.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${shown.year}", style = MaterialTheme.typography.titleMedium)
            IconAction(Icons.Filled.ChevronRight, "Next month", { month = shown.plusMonths(1).toString() })
        }
        MonthGrid(shown, LeaveMath.coveredDays(leaves), today)
    }

    SecondaryButton("Add leave", onClick = onAdd, icon = Icons.Filled.Add, enabled = types.isNotEmpty())

    val inYear = leaves.filter { it.endEpochDay >= year.start.toEpochDay() && it.startEpochDay <= year.endInclusive.toEpochDay() }.sortedByDescending { it.startEpochDay }
    if (inYear.isNotEmpty()) {
        Section("Leave taken") {
            inYear.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider()
                val type = types.firstOrNull { it.id == l.leaveTypeId }?.name ?: "Leave"
                Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(l.id) }.padding(vertical = 6.dp)) {
                    Text(
                        if (l.startEpochDay == l.endEpochDay) formatDate(l.startEpochDay) + if (l.halfDay) " (half day)" else ""
                        else "${formatDate(l.startEpochDay)} – ${formatDate(l.endEpochDay)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "$type · ${LeaveMath.format(LeaveMath.workingDays(LocalDate.ofEpochDay(l.startEpochDay), LocalDate.ofEpochDay(l.endEpochDay), l.halfDay))} working days" +
                            (l.reason?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A month as a grid, Monday first. Leave days are filled; today has a ring. */
@Composable
private fun MonthGrid(month: YearMonth, leaveDays: Set<LocalDate>, today: LocalDate) {
    val lead = month.atDay(1).dayOfWeek.value - 1
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    val cells = lead + month.lengthOfMonth()
    for (row in 0 until (cells + 6) / 7) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (col in 0 until 7) {
                val n = row * 7 + col - lead + 1
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (n in 1..month.lengthOfMonth()) {
                        val date = month.atDay(n)
                        val onLeave = date in leaveDays
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                .then(if (onLeave) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
                                .then(if (date == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$n", style = MaterialTheme.typography.bodySmall,
                                color = if (date.dayOfWeek.value >= 6) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LeaveEditorScreen(
    id: String,
    company: CompanyEntity,
    types: List<LeaveTypeEntity>,
    existing: LeaveEntryEntity?,
    onSave: (LeaveEntryEntity) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var typeId by rememberSaveable { mutableStateOf(existing?.leaveTypeId ?: types.firstOrNull()?.id.orEmpty()) }
    var from by rememberSaveable { mutableStateOf(existing?.startEpochDay ?: LocalDate.now().toEpochDay()) }
    var to by rememberSaveable { mutableStateOf(existing?.endEpochDay ?: LocalDate.now().toEpochDay()) }
    var half by rememberSaveable { mutableStateOf(existing?.halfDay ?: false) }
    var reason by rememberSaveable { mutableStateOf(existing?.reason.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val days = LeaveMath.workingDays(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to), half)

    AppScreen(
        if (existing == null) "Add leave" else "Edit leave",
        onBack = onBack,
        actions = { if (existing != null) IconAction(Icons.Filled.Delete, "Delete this leave", { confirmDelete = true }) },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    when {
                        types.none { it.id == typeId } -> error = "Choose a leave type."
                        to < from -> error = "The last day is before the first day."
                        else -> onSave(LeaveEntryEntity(id, company.id, typeId, from, if (half) from else to, half, reason.trim().ifEmpty { null }, existing?.createdAtMillis ?: System.currentTimeMillis()))
                    }
                })
            }
        },
    ) {
        Section(company.name) {
            PickerField("Leave type", types.firstOrNull { it.id == typeId }?.name ?: "Choose", types.map { it.name to it.id }) { typeId = it }
            DateField(if (half) "Day" else "From", from) { from = it; if (to < it) to = it }
            if (!half) DateField("To (inclusive)", to) { to = it }
            SwitchRow(title = "Half day", checked = half, onChange = { half = it; if (it) to = from })
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
            Text("${LeaveMath.format(days)} working day(s). Saturdays and Sundays are not counted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this leave?") },
            text = { Text("Your balance goes back up by the days it used.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Companies

@Composable
internal fun ColumnScope.CompaniesContent(
    companies: List<CompanyEntity>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenArchived: () -> Unit,
) {
    val active = companies.filterNot { it.archived }
    if (active.isEmpty()) Section("No company yet", "Add the company you work for. When you leave, mark it as left: its records stay, tucked away.") {}
    active.forEach { c ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.clickable { onOpen(c.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(c.role, "since ${formatDate(c.startEpochDay)}").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    SecondaryButton("Add a company", onClick = onAdd, icon = Icons.Filled.Add)
    val archivedCount = companies.count { it.archived }
    if (archivedCount > 0) SecondaryButton("Companies I have left ($archivedCount)", onClick = onOpenArchived, icon = Icons.Filled.Archive)
}

@Composable
internal fun ArchivedCompaniesScreen(companies: List<CompanyEntity>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val left = companies.filter { it.archived }
    AppScreen("Companies I have left", onBack = onBack) {
        if (left.isEmpty()) Section("Nothing here", "Companies you mark as left appear here, with all their records.") {}
        else Section("Past companies") {
            left.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider()
                Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(vertical = 8.dp)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${formatDate(c.startEpochDay)} – ${c.endEpochDay?.let { formatDate(it) } ?: "?"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class LeaveRow(val id: String?, val name: String, val quota: String)

/** Create or edit a company, including its leave types. Leaving the company and permanent deletion live here too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompanyEditorScreen(
    id: String,
    existing: CompanyEntity?,
    types: List<LeaveTypeEntity>,
    onSave: (CompanyEntity, List<Triple<String?, String, Double>>) -> Unit,
    onToggleLeft: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var role by rememberSaveable { mutableStateOf(existing?.role.orEmpty()) }
    var employeeId by rememberSaveable { mutableStateOf(existing?.employeeId.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(existing?.startEpochDay ?: LocalDate.now().toEpochDay()) }
    var yearStart by rememberSaveable { mutableStateOf(existing?.leaveYearStartMonth ?: 4) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    val rows = remember {
        mutableStateListOf<LeaveRow>().also { l ->
            if (types.isNotEmpty()) l.addAll(types.map { LeaveRow(it.id, it.name, LeaveMath.format(it.yearlyQuotaDays)) })
            else l.addAll(listOf(LeaveRow(null, "Casual leave", "12"), LeaveRow(null, "Sick leave", "6"), LeaveRow(null, "Earned leave", "15")))
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLeft by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    AppScreen(
        if (existing == null) "New company" else "Company",
        onBack = onBack,
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    val parsed = rows.filter { it.name.isNotBlank() }.map { Triple(it.id, it.name, it.quota.trim().replace(',', '.').toDoubleOrNull()) }
                    when {
                        name.isBlank() -> error = "Enter the company name."
                        parsed.any { it.third == null || it.third!! < 0 } -> error = "Each leave type needs a yearly quota in days."
                        else -> onSave(
                            CompanyEntity(
                                id, name.trim(), role.trim().ifEmpty { null }, employeeId.trim().ifEmpty { null }, start,
                                existing?.endEpochDay, yearStart, note.trim().ifEmpty { null }, existing?.archived ?: false,
                                existing?.createdAtMillis ?: System.currentTimeMillis(),
                            ),
                            parsed.map { Triple(it.first, it.second, it.third!!) },
                        )
                    }
                })
            }
        },
    ) {
        Section("Company") {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Your role (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = employeeId, onValueChange = { employeeId = it }, label = { Text("Employee ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField("Joined on", start) { start = it }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        Section("Leave policy", "Each company has its own leave year and quotas.") {
            Text("Leave year starts in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = yearStart == 1, onClick = { yearStart = 1 }, label = { Text("January") })
                FilterChip(selected = yearStart == 4, onClick = { yearStart = 4 }, label = { Text("April") })
            }
            rows.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = r.name, onValueChange = { rows[i] = r.copy(name = it) }, label = { Text("Leave type") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(
                        value = r.quota, onValueChange = { rows[i] = r.copy(quota = it) }, label = { Text("Days / year") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
            }
            SecondaryButton("Add a leave type", onClick = { rows.add(LeaveRow(null, "", "")) }, icon = Icons.Filled.Add)
        }
        if (existing != null) {
            Section("Leaving this company", "Marking it as left keeps every record but tucks the company away. Nothing is erased.") {
                SecondaryButton(
                    if (existing.archived) "I work here again" else "I have left this company",
                    onClick = { if (existing.archived) onToggleLeft() else confirmLeft = true },
                    icon = if (existing.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                )
            }
            Section("Delete everything", "Permanently erases this company with its attendance and leave. This cannot be undone.") {
                SecondaryButton("Delete permanently", onClick = { typed = ""; confirmDelete = true }, icon = Icons.Filled.Delete)
            }
        }
    }

    if (confirmLeft) {
        AlertDialog(
            onDismissRequest = { confirmLeft = false },
            title = { Text("Mark as left?") },
            text = { Text("Your last day will be recorded as today. All records stay safe in “Companies I have left”.") },
            confirmButton = { TextButton(onClick = { confirmLeft = false; onToggleLeft() }) { Text("Mark as left") } },
            dismissButton = { TextButton(onClick = { confirmLeft = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All attendance and leave recorded for this company will be erased for good. Marking it as left keeps them instead.")
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Type the company name to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = typed.trim() == existing.name) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

internal fun newId(): String = UUID.randomUUID().toString()
