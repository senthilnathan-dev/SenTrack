package `in`.senthilnathan_dev.sentrack.feature.work

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.attachments.attachmentIcon
import `in`.senthilnathan_dev.sentrack.core.data.work.AppreciationEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.CompanyEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkPermissionEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkTaskEntity
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AiAssistButton
import `in`.senthilnathan_dev.sentrack.core.ui.AiUse
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.DateField
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import `in`.senthilnathan_dev.sentrack.core.ui.formatDate
import java.time.LocalDate

private val PRIORITIES = listOf("None", "Low", "Medium", "High")

/** What a due date means today: overdue, today, soon, later. Used for the daily summary and for sorting cues. */
internal object TaskMath {
    fun dueText(task: WorkTaskEntity, today: Long): String? = task.dueEpochDay?.let { due ->
        when {
            task.status == "DONE" -> null
            due < today -> "Overdue by ${today - due} day${if (today - due == 1L) "" else "s"}"
            due == today -> "Due today"
            due == today + 1 -> "Due tomorrow"
            else -> "Due ${formatDate(due)}"
        }
    }

    fun overdue(tasks: List<WorkTaskEntity>, today: Long) = tasks.filter { it.status != "DONE" && (it.dueEpochDay ?: Long.MAX_VALUE) < today }
    fun dueToday(tasks: List<WorkTaskEntity>, today: Long) = tasks.filter { it.status != "DONE" && it.dueEpochDay == today }
}

// ---------------------------------------------------------------------------------------------------------------------
// Today's summary (shown on the Today page)

@Composable
internal fun ColumnScope.DailySummary(tasks: List<WorkTaskEntity>, permissionsToday: List<WorkPermissionEntity>, onOpenTasks: () -> Unit) {
    val today = LocalDate.now().toEpochDay()
    val overdue = TaskMath.overdue(tasks, today)
    val due = TaskMath.dueToday(tasks, today)
    val open = tasks.count { it.status != "DONE" }
    val doneToday = tasks.count { it.completedEpochDay == today }
    Section("Today at a glance") {
        Text(
            when {
                open == 0 -> "No open tasks. Enjoy the quiet."
                else -> "$open open task${if (open == 1) "" else "s"}" + (if (due.isNotEmpty()) ", ${due.size} due today" else "") + (if (overdue.isNotEmpty()) ", ${overdue.size} overdue" else "")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        (overdue + due).take(5).forEach { t ->
            Text("• ${t.title}  (${TaskMath.dueText(t, today)})", style = MaterialTheme.typography.bodyMedium, color = if (t in overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        if (doneToday > 0) Text("Finished today: $doneToday", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        permissionsToday.forEach { Text("Permission: ${it.category}${it.minutes?.let { m -> " ($m min)" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (open > 0) SecondaryButton("Open tasks", onClick = onOpenTasks)
        if (open > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Not sure where to start?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                AiAssistButton(
                    uses = listOf(AiUse.DAY_PLAN),
                    text = {
                        tasks.filter { it.status != "DONE" }.joinToString("\n") { t ->
                            "- ${t.title}" + (TaskMath.dueText(t, today)?.let { " ($it)" } ?: "") + (if (t.priority >= 2) " [priority]" else "")
                        }
                    },
                    onApply = { _, _ -> },
                    applyLabel = { "Got it" },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Tasks

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.TasksContent(tasks: List<WorkTaskEntity>, onOpen: (String) -> Unit, onToggle: (WorkTaskEntity) -> Unit) {
    var showDone by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val open = tasks.filter { it.status != "DONE" }
    val done = tasks.filter { it.status == "DONE" }.sortedByDescending { it.completedEpochDay }

    if (open.isEmpty()) Section("No open tasks", "Tap “New task” to add what you need to get done. Pending work stays here until you tick it.") {}
    (open).forEach { t -> TaskCard(t, today, onOpen, onToggle) }
    if (done.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showDone, onClick = { showDone = !showDone }, label = { Text("Completed (${done.size})") })
        }
        if (showDone) done.forEach { t -> TaskCard(t, today, onOpen, onToggle) }
    }
}

@Composable
private fun TaskCard(t: WorkTaskEntity, today: Long, onOpen: (String) -> Unit, onToggle: (WorkTaskEntity) -> Unit) {
    val done = t.status == "DONE"
    val due = TaskMath.dueText(t, today)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.clickable { onOpen(t.id) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium)
                val facts = listOfNotNull(
                    due ?: t.completedEpochDay?.let { "Done ${formatDate(it)}" },
                    PRIORITIES[t.priority.coerceIn(0, 3)].takeIf { t.priority > 0 }?.let { "$it priority" },
                )
                if (facts.isNotEmpty()) {
                    Text(
                        facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = if (due?.startsWith("Overdue") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconAction(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                if (done) "Reopen ${t.title}" else "Mark ${t.title} done", { onToggle(t) },
                tint = if (done) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskEditorScreen(
    id: String,
    company: CompanyEntity,
    existing: WorkTaskEntity?,
    onSave: (WorkTaskEntity) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var priority by rememberSaveable { mutableStateOf(existing?.priority ?: 0) }
    var hasDue by rememberSaveable { mutableStateOf(existing?.dueEpochDay != null) }
    var due by rememberSaveable { mutableStateOf(existing?.dueEpochDay ?: LocalDate.now().toEpochDay()) }
    var done by rememberSaveable { mutableStateOf(existing?.status == "DONE") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppScreen(
        if (existing == null) "New task" else "Edit task",
        onBack = onBack,
        actions = {
            AiAssistButton(
                uses = listOf(AiUse.STEPS, AiUse.REWRITE),
                text = { if (note.isBlank()) title else "$title\n$note" },
                onApply = { use, result -> note = if (use == AiUse.STEPS) (note.trim() + "\n" + result).trim() else result },
                applyLabel = { u -> if (u == AiUse.STEPS) "Add to details" else "Replace details" },
            )
            if (existing != null) IconAction(Icons.Filled.Delete, "Delete task", { confirmDelete = true })
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    if (title.isBlank()) error = "Give the task a title." else onSave(
                        WorkTaskEntity(
                            id, company.id, title.trim(), note.trim().ifEmpty { null }, if (done) "DONE" else "OPEN", priority,
                            if (hasDue) due else null,
                            if (done) (existing?.completedEpochDay ?: LocalDate.now().toEpochDay()) else null,
                            existing?.createdAtMillis ?: System.currentTimeMillis(),
                        ),
                    )
                })
            }
        },
    ) {
        Section(company.name) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth())
            Text("Priority", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRIORITIES.forEachIndexed { i, p -> FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(p) }) }
            }
            SwitchRow(title = "Due date", checked = hasDue, onChange = { hasDue = it })
            if (hasDue) DateField("Due on", due) { due = it }
            if (existing != null) SwitchRow(title = "Done", checked = done, onChange = { done = it })
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this task?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Records: appreciation and permissions

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.RecordsContent(
    appreciations: List<AppreciationEntity>,
    permissions: List<WorkPermissionEntity>,
    attachmentCount: (String) -> Int,
    onOpenAppreciation: (String) -> Unit,
    onNewAppreciation: () -> Unit,
    onOpenPermission: (String) -> Unit,
    onNewPermission: () -> Unit,
) {
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !showPermissions, onClick = { showPermissions = false }, label = { Text("Appreciation") })
        FilterChip(selected = showPermissions, onClick = { showPermissions = true }, label = { Text("Permissions") })
    }
    if (!showPermissions) {
        if (appreciations.isEmpty()) Section("No appreciation saved", "Keep the mails, messages and shout-outs that recognise your work. Attach the screenshot or mail so it is there at appraisal time.") {}
        appreciations.forEach { a ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.clickable { onOpenAppreciation(a.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                    val n = attachmentCount(a.id)
                    Text(
                        listOfNotNull(formatDate(a.dateEpochDay), a.fromPerson?.let { "from $it" }, n.takeIf { it > 0 }?.let { "$it attachment${if (it == 1) "" else "s"}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    a.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
                }
            }
        }
        SecondaryButton("Add appreciation", onClick = onNewAppreciation, icon = Icons.Filled.Add)
    } else {
        if (permissions.isEmpty()) Section("No permissions recorded", "Log late arrivals, early leaves and short permissions with the reason, so you have the history when asked.") {}
        permissions.forEach { p ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.clickable { onOpenPermission(p.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.category, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(formatDate(p.dayEpoch), p.minutes?.let { "$it min" }, p.reason).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SecondaryButton("Add permission", onClick = onNewPermission, icon = Icons.Filled.Add)
    }
}

@Composable
internal fun AppreciationEditorScreen(
    id: String,
    company: CompanyEntity,
    existing: AppreciationEntity?,
    attachments: List<WorkAttachmentEntity>,
    importing: Boolean,
    notice: String?,
    problem: String?,
    onSave: (AppreciationEntity) -> Unit,
    onDelete: () -> Unit,
    onAddAttachment: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onExportAttachment: (WorkAttachmentEntity) -> Unit,
    onDeleteAttachment: (WorkAttachmentEntity) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var from by rememberSaveable { mutableStateOf(existing?.fromPerson.orEmpty()) }
    var text by rememberSaveable { mutableStateOf(existing?.text.orEmpty()) }
    var day by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<WorkAttachmentEntity?>(null) }

    AppScreen(
        if (existing == null) "New appreciation" else "Appreciation",
        onBack = onBack,
        actions = {
            AiAssistButton(
                uses = listOf(AiUse.POLISH, AiUse.FIX, AiUse.SUMMARIZE),
                text = { if (text.isBlank()) title else "$title\n$text" },
                onApply = { _, result -> text = result },
                applyLabel = { "Use as what they said" },
            )
            if (existing != null) IconAction(Icons.Filled.Delete, "Delete permanently", { confirmDelete = true })
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    if (title.isBlank()) error = "Give it a title." else onSave(
                        AppreciationEntity(id, company.id, title.trim(), from.trim().ifEmpty { null }, text.trim().ifEmpty { null }, day, existing?.createdAtMillis ?: System.currentTimeMillis()),
                    )
                })
            }
        },
    ) {
        Section(company.name) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What was it for?") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text("From (person or team, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField("Date", day) { day = it }
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("What they said (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        if (existing != null) {
            Message(notice, MessageKind.INFO)
            Message(problem, MessageKind.ERROR)
            Section("Proof", "Screenshots, mail exports or PDFs, encrypted on this phone.") {
                attachments.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.weight(1f).clickable { onOpenAttachment(a.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(attachmentIcon(a.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge)
                                Text(Formatter.formatShortFileSize(context, a.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconAction(Icons.Filled.Download, "Save a readable copy of ${a.name}", { onExportAttachment(a) })
                        IconAction(Icons.Filled.Delete, "Remove ${a.name}", { removing = a })
                    }
                }
                SecondaryButton(if (importing) "Attaching…" else "Attach files", onClick = onAddAttachment, enabled = !importing, icon = Icons.Filled.AttachFile)
            }
        } else {
            Section("Proof", "Save first, then open it again to attach the screenshot or mail.") {}
        }
    }
    removing?.let { a ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${a.name}?") },
            confirmButton = { TextButton(onClick = { onDeleteAttachment(a); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete permanently?") },
            text = { Text("“${existing.title}” and its attachments are erased and cannot be recovered.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val DEFAULT_CATEGORIES = listOf("Late arrival", "Early leave", "Personal permission", "Medical", "Work from home")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermissionEditorScreen(
    id: String,
    company: CompanyEntity,
    existing: WorkPermissionEntity?,
    usedCategories: List<String>,
    onSave: (WorkPermissionEntity) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf(existing?.category ?: DEFAULT_CATEGORIES.first()) }
    var day by rememberSaveable { mutableStateOf(existing?.dayEpoch ?: LocalDate.now().toEpochDay()) }
    var minutes by rememberSaveable { mutableStateOf(existing?.minutes?.toString().orEmpty()) }
    var reason by rememberSaveable { mutableStateOf(existing?.reason.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val categories = (DEFAULT_CATEGORIES + usedCategories + category).filter { it.isNotBlank() }.distinct()

    AppScreen(
        if (existing == null) "New permission" else "Permission",
        onBack = onBack,
        actions = { if (existing != null) IconAction(Icons.Filled.Delete, "Delete", { confirmDelete = true }) },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    val m = minutes.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    when {
                        category.isBlank() -> error = "Choose or type a category."
                        minutes.isNotBlank() && (m == null || m <= 0) -> error = "Minutes must be a whole number."
                        else -> onSave(WorkPermissionEntity(id, company.id, day, category.trim(), m, reason.trim().ifEmpty { null }, existing?.createdAtMillis ?: System.currentTimeMillis()))
                    }
                })
            }
        },
    ) {
        Section(company.name) {
            Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            }
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Or type your own") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField("Date", day) { day = it }
            OutlinedTextField(
                value = minutes, onValueChange = { minutes = it }, label = { Text("How long, in minutes (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this record?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
