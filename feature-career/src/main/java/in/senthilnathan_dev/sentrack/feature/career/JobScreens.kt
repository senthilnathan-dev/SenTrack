package `in`.senthilnathan_dev.sentrack.feature.career

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.career.InterviewEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.JobApplicationEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.ResumeEntity
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
import `in`.senthilnathan_dev.sentrack.core.ui.formatTime12
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val zone get() = ZoneId.systemDefault()
private fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
private fun timeOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
private fun millisOf(day: Long, time: LocalTime) = LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli()
internal fun interviewWhen(millis: Long) = "${formatDate(dayOf(millis))}, ${formatTime12(timeOf(millis))}"

// ---------------------------------------------------------------------------------------------------------------------
// Job list

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.JobsContent(
    applications: List<JobApplicationEntity>,
    interviews: List<InterviewEntity>,
    resumes: List<ResumeEntity>,
    onOpen: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("OPEN") }   // OPEN, CLOSED, ARCHIVED
    val now = System.currentTimeMillis()
    val visible = applications.filter { a ->
        when (filter) {
            "ARCHIVED" -> a.archived
            "CLOSED" -> !a.archived && !JobStatus.of(a.status).open
            else -> !a.archived && JobStatus.of(a.status).open
        }
    }
    val rate = CareerMath.responseRate(applications.filterNot { it.archived }.map { JobStatus.of(it.status) })

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = filter == "OPEN", onClick = { filter = "OPEN" }, label = { Text("In progress") })
        FilterChip(selected = filter == "CLOSED", onClick = { filter = "CLOSED" }, label = { Text("Closed") })
        if (applications.any { it.archived }) FilterChip(selected = filter == "ARCHIVED", onClick = { filter = "ARCHIVED" }, label = { Text("Archived") })
    }
    if (rate != null && filter == "OPEN") Text("$rate% of your applications have had a reply.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (visible.isEmpty()) Section("Nothing here", if (filter == "OPEN") "Tap “New application” to track a job you want or have applied for, with its interviews and the resume you sent." else null) {}
    visible.forEach { a ->
        val next = interviews.filter { it.applicationId == a.id && !it.done && it.atMillis >= now }.minByOrNull { it.atMillis }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.clickable { onOpen(a.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${a.role} · ${a.company}", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(JobStatus.of(a.status).label, a.appliedEpochDay?.let { "applied ${formatDate(it)}" }, resumes.firstOrNull { it.id == a.resumeId }?.let { "resume: ${it.label}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (next != null) Text("Next: ${next.round}, ${interviewWhen(next.atMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Job editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JobEditorScreen(
    id: String,
    existing: JobApplicationEntity?,
    interviews: List<InterviewEntity>,
    resumes: List<ResumeEntity>,
    onSave: (JobApplicationEntity) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onSaveInterview: (InterviewEntity) -> Unit,
    onDeleteInterview: (InterviewEntity) -> Unit,
    onBack: () -> Unit,
) {
    var company by rememberSaveable { mutableStateOf(existing?.company.orEmpty()) }
    var role by rememberSaveable { mutableStateOf(existing?.role.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(JobStatus.of(existing?.status ?: "WISHLIST")) }
    var hasApplied by rememberSaveable { mutableStateOf(existing?.appliedEpochDay != null) }
    var applied by rememberSaveable { mutableStateOf(existing?.appliedEpochDay ?: LocalDate.now().toEpochDay()) }
    var source by rememberSaveable { mutableStateOf(existing?.source.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(existing?.location.orEmpty()) }
    var salary by rememberSaveable { mutableStateOf(existing?.salaryNote.orEmpty()) }
    var link by rememberSaveable { mutableStateOf(existing?.link.orEmpty()) }
    var resumeId by rememberSaveable { mutableStateOf(existing?.resumeId.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingInterview by remember { mutableStateOf<InterviewEntity?>(null) }

    AppScreen(
        if (existing == null) "New application" else "Application",
        onBack = onBack,
        actions = {
            AiAssistButton(
                uses = listOf(AiUse.FOLLOW_UP, AiUse.INTERVIEW_PREP),
                text = {
                    buildString {
                        append("Role: ${role.ifBlank { "(not set)" }}\nCompany: ${company.ifBlank { "(not set)" }}\nStatus: ${status.label}")
                        if (hasApplied) append("\nApplied on: ${formatDate(applied)}")
                        if (note.isNotBlank()) append("\nNotes: $note")
                    }
                },
                onApply = { _, result -> note = (note.trim() + "\n\n" + result).trim() },
                applyLabel = { "Add to notes" },
            )
            ArchiveDeleteActions(existing?.archived, onToggleArchive) { confirmDelete = true }
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    if (company.isBlank() || role.isBlank()) error = "Enter the company and the role." else onSave(
                        JobApplicationEntity(
                            id, company.trim(), role.trim(), status.name, if (hasApplied) applied else null,
                            source.trim().ifEmpty { null }, location.trim().ifEmpty { null }, salary.trim().ifEmpty { null },
                            link.trim().ifEmpty { null }, resumeId.ifEmpty { null }, note.trim().ifEmpty { null },
                            existing?.archived ?: false, existing?.createdAtMillis ?: System.currentTimeMillis(),
                        ),
                    )
                })
            }
        },
    ) {
        Section("Job") {
            OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Where it stands", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JobStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }) }
            }
            SwitchRow(title = "Applied", checked = hasApplied, onChange = { hasApplied = it })
            if (hasApplied) DateField("Applied on", applied) { applied = it }
        }
        Section("Details") {
            OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Found through (portal, referral…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("Pay discussed (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Link to the posting (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PickerField(
                "Resume sent", resumes.firstOrNull { it.id == resumeId }?.label ?: "None",
                listOf("None" to "") + resumes.filterNot { it.archived || false }.map { it.label to it.id },
            ) { resumeId = it }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        if (existing != null) {
            Section("Interviews", "A reminder rings an hour before each one.") {
                if (interviews.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodyMedium)
                interviews.forEachIndexed { i, iv ->
                    if (i > 0) HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clickable { editingInterview = iv }.padding(vertical = 6.dp)) {
                            Text(iv.round, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(interviewWhen(iv.atMillis), iv.mode).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconAction(
                            if (iv.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            if (iv.done) "Mark ${iv.round} not done" else "Mark ${iv.round} done",
                            { onSaveInterview(iv.copy(done = !iv.done)) },
                        )
                        IconAction(Icons.Filled.Delete, "Delete ${iv.round}", { onDeleteInterview(iv) })
                    }
                }
                SecondaryButton("Add interview", icon = Icons.Filled.Add, onClick = {
                    editingInterview = InterviewEntity("", id, "", millisOf(LocalDate.now().plusDays(1).toEpochDay(), LocalTime.of(10, 0)), null, null, false)
                })
            }
        } else {
            Section("Interviews", "Save first, then open the application again to add interviews.") {}
        }
    }

    editingInterview?.let { iv ->
        var round by remember { mutableStateOf(iv.round) }
        var day by remember { mutableStateOf(dayOf(iv.atMillis)) }
        var time by remember { mutableStateOf(timeOf(iv.atMillis).withSecond(0).withNano(0)) }
        var mode by remember { mutableStateOf(iv.mode.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingInterview = null },
            title = { Text(if (iv.id.isEmpty()) "Add interview" else "Edit interview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = round, onValueChange = { round = it }, label = { Text("Round (HR, technical…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    DateField("Date", day) { day = it }
                    TimeField("Time", time) { time = it }
                    OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Where or how (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = round.isNotBlank(), onClick = {
                    onSaveInterview(iv.copy(id = iv.id.ifEmpty { newId() }, round = round.trim(), atMillis = millisOf(day, time), mode = mode.trim().ifEmpty { null }))
                    editingInterview = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingInterview = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && existing != null) ConfirmDelete("${existing.role}, ${existing.company}", { confirmDelete = false; onDelete() }, { confirmDelete = false })
}

// ---------------------------------------------------------------------------------------------------------------------
// Resumes

@Composable
internal fun ColumnScope.ResumesContent(
    resumes: List<ResumeEntity>,
    applications: List<JobApplicationEntity>,
    importing: Boolean,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val active = resumes.filterNot { it.archived }
    val archived = resumes.filter { it.archived }
    if (active.isEmpty()) Section("No resumes yet", "Keep each version of your resume here, then pick the one you sent on each application.") {}
    (active + if (showArchived) archived else emptyList()).forEach { r ->
        val used = applications.count { it.resumeId == r.id }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.clickable { onOpen(r.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(r.fileName, "used for $used application${if (used == 1) "" else "s"}").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    SecondaryButton(if (importing) "Adding…" else "Add a resume version", onClick = onAdd, enabled = !importing, icon = Icons.Filled.Add)
    if (archived.isNotEmpty()) SecondaryButton(if (showArchived) "Hide archived (${archived.size})" else "Archived (${archived.size})", onClick = { showArchived = !showArchived }, icon = Icons.Filled.Archive)
}

@Composable
internal fun ResumeEditorScreen(
    resume: ResumeEntity,
    usedBy: List<JobApplicationEntity>,
    onSave: (ResumeEntity) -> Unit,
    onOpenFile: () -> Unit,
    onExport: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(resume.label) }
    var note by rememberSaveable { mutableStateOf(resume.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    AppScreen(
        "Resume",
        onBack = onBack,
        actions = { ArchiveDeleteActions(resume.archived, onToggleArchive) { confirmDelete = true } },
        bottomBar = {
            ActionBar {
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = { onSave(resume.copy(label = label.trim().ifEmpty { resume.fileName }, note = note.trim().ifEmpty { null })) })
            }
        },
    ) {
        Section("Version") {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (for example Backend, 2026)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("What changed (optional)") }, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Open ${resume.fileName}", onClick = onOpenFile)
            SecondaryButton("Save a readable copy", onClick = onExport, icon = Icons.Filled.Download)
        }
        Section("Used for") {
            if (usedBy.isEmpty()) Text("No application yet.", style = MaterialTheme.typography.bodyMedium)
            usedBy.forEach { Text("• ${it.role}, ${it.company}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (confirmDelete) ConfirmDelete(resume.label, { confirmDelete = false; onDelete() }, { confirmDelete = false })
}

internal fun newId(): String = java.util.UUID.randomUUID().toString()
