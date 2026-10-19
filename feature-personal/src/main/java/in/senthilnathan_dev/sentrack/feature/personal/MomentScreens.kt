package `in`.senthilnathan_dev.sentrack.feature.personal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.personal.AchievementEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.ImportantDateEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.PersonalAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AiAssistButton
import `in`.senthilnathan_dev.sentrack.core.ui.AiUse
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
import java.time.LocalDate
import java.time.LocalTime

internal enum class DateKind(val label: String, val repeatsByDefault: Boolean) {
    BIRTHDAY("Birthday", true), ANNIVERSARY("Anniversary", true), EVENT("Event", false), MILESTONE("Milestone", false), OTHER("Other", false)
}

internal fun ImportantDateEntity.kindOf(): DateKind = runCatching { DateKind.valueOf(kind) }.getOrDefault(DateKind.OTHER)

/** The date this entry next falls on (or last fell on, for one-offs already past), plus whether it is still ahead. */
internal class Upcoming(val entity: ImportantDateEntity, val on: LocalDate, val ahead: Boolean) {
    val title: String get() = entity.title

    /** "Turns 36", "5th anniversary", or null when there is no count to show. */
    fun milestone(): String? {
        if (!entity.yearly) return null
        val years = DateMath.yearsAt(LocalDate.ofEpochDay(entity.dateEpochDay), on)
        if (years <= 0) return null
        return when (entity.kindOf()) {
            DateKind.BIRTHDAY -> "Turns $years"
            DateKind.ANNIVERSARY -> "${DateMath.ordinal(years)} anniversary"
            else -> "$years years"
        }
    }
}

/** Achievements, important dates and their attachments, joined for the screens. */
internal class MomentsData(val achievements: List<AchievementEntity>, val dates: List<ImportantDateEntity>, val attachments: List<PersonalAttachmentEntity>) {
    fun attachmentsOf(kind: String, id: String) = attachments.filter { it.ownerKind == kind && it.ownerId == id }

    /** Active dates sorted by how soon they come round; one-offs already past are listed last. */
    fun upcoming(today: LocalDate): List<Upcoming> =
        dates.filterNot { it.archived }.map { d ->
            val origin = LocalDate.ofEpochDay(d.dateEpochDay)
            val next = DateMath.nextOccurrence(origin, d.yearly, today)
            if (next != null) Upcoming(d, next, true) else Upcoming(d, origin, false)
        }.sortedWith(compareBy<Upcoming> { !it.ahead }.thenBy { if (it.ahead) it.on.toEpochDay() else -it.on.toEpochDay() })
}

// ---------------------------------------------------------------------------------------------------------------------
// List

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.MomentsContent(
    data: MomentsData,
    onOpenAchievement: (String) -> Unit,
    onOpenDate: (String) -> Unit,
    onNewAchievement: () -> Unit,
    onNewDate: () -> Unit,
    onOpenArchived: () -> Unit,
) {
    var showDates by rememberSaveable { mutableStateOf(true) }
    val today = LocalDate.now()
    val archivedCount = data.achievements.count { it.archived } + data.dates.count { it.archived }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = showDates, onClick = { showDates = true }, label = { Text("Important dates") })
        FilterChip(selected = !showDates, onClick = { showDates = false }, label = { Text("Achievements") })
    }

    if (showDates) {
        val upcoming = data.upcoming(today)
        if (upcoming.isEmpty()) Section("No dates yet", "Birthdays, anniversaries, deadlines, events. SenTrack counts down to them and can remind you.") {}
        upcoming.forEach { u ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.clickable { onOpenDate(u.entity.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(u.title, style = MaterialTheme.typography.titleMedium)
                    val countdown = if (u.ahead) DateMath.countdown(DateMath.daysUntil(u.on, today)) else "Passed"
                    val facts = listOfNotNull(u.entity.kindOf().label, formatDate(u.on.toEpochDay()), u.milestone(), countdown)
                    Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SecondaryButton("Add a date", onClick = onNewDate, icon = Icons.Filled.Add)
    } else {
        val list = data.achievements.filterNot { it.archived }
        if (list.isEmpty()) Section("No achievements yet", "Certifications, promotions, races finished, things you are proud of. Your story, in order.") {}
        list.forEach { a ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.clickable { onOpenAchievement(a.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                    Text("${a.category} · ${formatDate(a.dateEpochDay)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    a.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
                }
            }
        }
        SecondaryButton("Add an achievement", onClick = onNewAchievement, icon = Icons.Filled.Add)
    }
    if (archivedCount > 0) SecondaryButton("Archived moments ($archivedCount)", onClick = onOpenArchived, icon = Icons.Filled.Archive)
}

@Composable
internal fun ArchivedMomentsScreen(
    data: MomentsData,
    onOpenAchievement: (String) -> Unit,
    onOpenDate: (String) -> Unit,
    onRestoreAchievement: (AchievementEntity) -> Unit,
    onRestoreDate: (ImportantDateEntity) -> Unit,
    onBack: () -> Unit,
) {
    val achievements = data.achievements.filter { it.archived }
    val dates = data.dates.filter { it.archived }
    AppScreen("Archived moments", onBack = onBack) {
        if (achievements.isEmpty() && dates.isEmpty()) Section("Nothing archived", "Archive an item from its page to hide it without losing it.") {}
        if (dates.isNotEmpty()) Section("Dates") {
            dates.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).clickable { onOpenDate(d.id) }.padding(vertical = 8.dp)) {
                        Text(d.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${d.kindOf().label} · ${formatDate(d.dateEpochDay)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconAction(Icons.Filled.Unarchive, "Restore ${d.title}", { onRestoreDate(d) })
                }
            }
        }
        if (achievements.isNotEmpty()) Section("Achievements") {
            achievements.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).clickable { onOpenAchievement(a.id) }.padding(vertical = 8.dp)) {
                        Text(a.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${a.category} · ${formatDate(a.dateEpochDay)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconAction(Icons.Filled.Unarchive, "Restore ${a.title}", { onRestoreAchievement(a) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Editors

private val CATEGORIES = listOf("Career", "Education", "Health", "Family", "Sport", "Creative", "Personal")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AchievementEditorScreen(
    id: String,
    existing: AchievementEntity?,
    attachments: List<PersonalAttachmentEntity>,
    importing: Boolean,
    notice: String?,
    problem: String?,
    onSave: (AchievementEntity) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onAddAttachment: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onExportAttachment: (PersonalAttachmentEntity) -> Unit,
    onDeleteAttachment: (PersonalAttachmentEntity) -> Unit,
    onBack: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: CATEGORIES.first()) }
    var day by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppScreen(
        if (existing == null) "New achievement" else "Achievement",
        onBack = onBack,
        actions = {
            AiAssistButton(
                uses = listOf(AiUse.POLISH, AiUse.FIX, AiUse.REWRITE),
                text = { if (description.isBlank()) title else "$title\n$description" },
                onApply = { _, result -> description = result },
                applyLabel = { "Use as the details" },
            )
            if (existing != null) {
                IconAction(if (existing.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, if (existing.archived) "Restore" else "Archive", onToggleArchive)
                IconAction(Icons.Filled.Delete, "Delete permanently", { confirmDelete = true })
            }
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    if (title.isBlank()) error = "Give it a title." else {
                        error = null
                        onSave(
                            AchievementEntity(
                                id = id, title = title.trim(), description = description.trim().ifEmpty { null },
                                category = category.trim().ifEmpty { "Personal" }, dateEpochDay = day,
                                archived = existing?.archived ?: false, createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                })
            }
        },
    ) {
        Section("Achievement") {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What did you achieve?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Story or details (optional)") }, modifier = Modifier.fillMaxWidth())
            DateField("When", day) { day = it }
            Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (CATEGORIES + listOfNotNull(category.takeIf { it !in CATEGORIES })).forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                }
            }
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Or type your own category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (existing != null) {
            Message(notice, MessageKind.INFO)
            Message(problem, MessageKind.ERROR)
            AttachmentsPanel(attachments, importing, onAddAttachment, onOpenAttachment, onExportAttachment, onDeleteAttachment)
        } else {
            Section("Attachments", "Save first, then open the achievement again to attach a certificate, photo or mail.") {}
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDeleteDialog(existing.title, onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}

private val LEAD_DAYS = listOf("On the day" to 0, "1 day before" to 1, "2 days before" to 2, "3 days before" to 3, "1 week before" to 7, "2 weeks before" to 14, "1 month before" to 30)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateEditorScreen(
    id: String,
    existing: ImportantDateEntity?,
    onSave: (ImportantDateEntity) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(existing?.kindOf() ?: DateKind.BIRTHDAY) }
    var day by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var yearly by rememberSaveable { mutableStateOf(existing?.yearly ?: true) }
    var remind by rememberSaveable { mutableStateOf((existing?.remindDaysBefore ?: 1) >= 0) }
    var lead by rememberSaveable { mutableStateOf(existing?.remindDaysBefore?.takeIf { it >= 0 } ?: 1) }
    var timeSecs by rememberSaveable { mutableStateOf((existing?.remindMinuteOfDay ?: (9 * 60)) * 60) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppScreen(
        if (existing == null) "New date" else "Edit date",
        onBack = onBack,
        actions = {
            if (existing != null) {
                IconAction(if (existing.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, if (existing.archived) "Restore" else "Archive", onToggleArchive)
                IconAction(Icons.Filled.Delete, "Delete permanently", { confirmDelete = true })
            }
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    if (title.isBlank()) error = "Give it a title." else {
                        error = null
                        onSave(
                            ImportantDateEntity(
                                id = id, title = title.trim(), kind = kind.name, dateEpochDay = day, yearly = yearly,
                                note = note.trim().ifEmpty { null },
                                remindDaysBefore = if (remind) lead else -1, remindMinuteOfDay = timeSecs / 60,
                                archived = existing?.archived ?: false, createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                })
            }
        },
    ) {
        Section("Date") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k; yearly = k.repeatsByDefault }, label = { Text(k.label) })
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (for example Amma’s birthday)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField(if (yearly) "Date (the first one counts, for ages and anniversaries)" else "Date", day) { day = it }
            SwitchRow(title = "Repeats every year", checked = yearly, onChange = { yearly = it })
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        Section("Reminder") {
            SwitchRow(title = "Remind me", subtitle = "A notification, even when SenTrack is locked.", checked = remind, onChange = { remind = it })
            if (remind) {
                PickerField("When", LEAD_DAYS.first { it.second == lead }.first, LEAD_DAYS) { lead = it }
                TimeField("At", LocalTime.ofSecondOfDay(timeSecs.toLong())) { timeSecs = it.toSecondOfDay() }
            }
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDeleteDialog(existing.title, onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}
