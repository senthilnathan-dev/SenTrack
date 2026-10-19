package `in`.senthilnathan_dev.sentrack.feature.career

import android.text.format.Formatter
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.attachments.attachmentIcon
import `in`.senthilnathan_dev.sentrack.core.data.career.CareerAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.CertificationEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.LearningItemEntity
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

internal val LEARNING_KINDS = listOf("COURSE" to "Course", "BOOK" to "Book", "SKILL" to "Skill", "OTHER" to "Other")
internal val LEARNING_STATUSES = listOf("PLANNED" to "Planned", "LEARNING" to "Learning", "DONE" to "Done")

/** Shared archive/delete buttons for the top bar of a career editor. */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.ArchiveDeleteActions(archived: Boolean?, onToggleArchive: () -> Unit, onDelete: () -> Unit) {
    if (archived != null) {
        IconAction(if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive, if (archived) "Restore" else "Archive", onToggleArchive)
        IconAction(Icons.Filled.Delete, "Delete permanently", onDelete)
    }
}

@Composable
internal fun ConfirmDelete(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete permanently?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("“$what” and anything attached to it will be erased for good. Archive it instead to keep it out of sight.")
                OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = typed.trim() == "DELETE") { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Files attached to a certification. */
@Composable
internal fun FilesPanel(
    title: String,
    hint: String,
    files: List<CareerAttachmentEntity>,
    importing: Boolean,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onExport: (CareerAttachmentEntity) -> Unit,
    onDelete: (CareerAttachmentEntity) -> Unit,
) {
    val context = LocalContext.current
    var removing by remember { mutableStateOf<CareerAttachmentEntity?>(null) }
    Section(title, hint) {
        files.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.weight(1f).clickable { onOpen(a.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(attachmentIcon(a.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                        Text(Formatter.formatShortFileSize(context, a.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconAction(Icons.Filled.Download, "Save a readable copy of ${a.name}", { onExport(a) })
                IconAction(Icons.Filled.Delete, "Remove ${a.name}", { removing = a })
            }
        }
        SecondaryButton(if (importing) "Attaching…" else "Attach files", onClick = onAdd, enabled = !importing, icon = Icons.Filled.AttachFile)
    }
    removing?.let { a ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${a.name}?") },
            confirmButton = { TextButton(onClick = { onDelete(a); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Learning

@Composable
internal fun ColumnScope.LearningContent(items: List<LearningItemEntity>, onOpen: (String) -> Unit) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val active = items.filterNot { it.archived }
    val archived = items.filter { it.archived }
    if (active.isEmpty()) Section("Nothing being learned yet", "Add a course, a book or a skill you are working on. Track how far you are and when you want to finish.") {}
    active.forEach { LearningCard(it, onOpen) }
    if (archived.isNotEmpty()) {
        SecondaryButton(if (showArchived) "Hide archived (${archived.size})" else "Archived (${archived.size})", onClick = { showArchived = !showArchived }, icon = Icons.Filled.Archive)
        if (showArchived) archived.forEach { LearningCard(it, onOpen) }
    }
}

@Composable
private fun LearningCard(item: LearningItemEntity, onOpen: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.clickable { onOpen(item.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            if (item.status != "PLANNED") LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
            val facts = listOfNotNull(
                LEARNING_KINDS.firstOrNull { it.first == item.kind }?.second, item.provider,
                LEARNING_STATUSES.firstOrNull { it.first == item.status }?.second.let { s -> if (item.status == "LEARNING") "${item.progress}%" else s },
                item.targetEpochDay?.takeIf { item.status != "DONE" }?.let { "by ${formatDate(it)}" },
            )
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LearningEditorScreen(
    id: String,
    existing: LearningItemEntity?,
    onSave: (LearningItemEntity) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var provider by rememberSaveable { mutableStateOf(existing?.provider.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: "COURSE") }
    var status by rememberSaveable { mutableStateOf(existing?.status ?: "PLANNED") }
    var progress by rememberSaveable { mutableStateOf(existing?.progress ?: 0) }
    var hasTarget by rememberSaveable { mutableStateOf(existing?.targetEpochDay != null) }
    var target by rememberSaveable { mutableStateOf(existing?.targetEpochDay ?: LocalDate.now().plusMonths(1).toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppScreen(
        if (existing == null) "New learning" else "Learning",
        onBack = onBack,
        actions = {
            AiAssistButton(
                uses = listOf(AiUse.STUDY_PLAN),
                text = { listOfNotNull(title.ifBlank { null }, provider.ifBlank { null }?.let { "by $it" }).joinToString(" ") },
                onApply = { _, result -> note = (note.trim() + "\n\n" + result).trim() },
                applyLabel = { "Add to notes" },
            )
            ArchiveDeleteActions(existing?.archived, onToggleArchive) { confirmDelete = true }
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    if (title.isBlank()) error = "Give it a title." else onSave(
                        LearningItemEntity(
                            id, title.trim(), provider.trim().ifEmpty { null }, kind, status,
                            if (status == "DONE") 100 else if (status == "PLANNED") 0 else progress,
                            if (hasTarget) target else null,
                            if (status == "DONE") (existing?.completedEpochDay ?: LocalDate.now().toEpochDay()) else null,
                            note.trim().ifEmpty { null }, existing?.archived ?: false, existing?.createdAtMillis ?: System.currentTimeMillis(),
                        ),
                    )
                })
            }
        },
    ) {
        Section("What") {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("Provider or author (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEARNING_KINDS.forEach { (k, label) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) }) }
            }
        }
        Section("Progress") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEARNING_STATUSES.forEach { (s, label) -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(label) }) }
            }
            if (status == "LEARNING") {
                Text("$progress% done", style = MaterialTheme.typography.bodyMedium)
                Slider(value = progress.toFloat(), onValueChange = { progress = it.toInt() }, valueRange = 0f..100f, steps = 19)
            }
            SwitchRow(title = "Target date", checked = hasTarget, onChange = { hasTarget = it })
            if (hasTarget) DateField("Finish by", target) { target = it }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete && existing != null) ConfirmDelete(existing.title, { confirmDelete = false; onDelete() }, { confirmDelete = false })
}

// ---------------------------------------------------------------------------------------------------------------------
// Certifications

@Composable
internal fun ColumnScope.CertificationsContent(certs: List<CertificationEntity>, attachmentCount: (String) -> Int, onOpen: (String) -> Unit) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val active = certs.filterNot { it.archived }
    val archived = certs.filter { it.archived }
    if (active.isEmpty()) Section("No certifications yet", "Record what you have earned, when it expires, and attach the certificate. SenTrack reminds you a month before it lapses.") {}
    (active + if (showArchived) archived else emptyList()).forEach { c ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.clickable { onOpen(c.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                val files = attachmentCount(c.id)
                Text(
                    listOfNotNull(c.issuer, "earned ${formatDate(c.earnedEpochDay)}", files.takeIf { it > 0 }?.let { "$it file${if (it == 1) "" else "s"}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (c.expiryEpochDay != null) {
                    Text(
                        CareerMath.expiryText(c.expiryEpochDay, today), style = MaterialTheme.typography.bodySmall,
                        color = if (CareerMath.needsAttention(c.expiryEpochDay, today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (archived.isNotEmpty()) SecondaryButton(if (showArchived) "Hide archived (${archived.size})" else "Archived (${archived.size})", onClick = { showArchived = !showArchived }, icon = Icons.Filled.Archive)
}

@Composable
internal fun CertificationEditorScreen(
    id: String,
    existing: CertificationEntity?,
    attachments: List<CareerAttachmentEntity>,
    hasReminder: Boolean,
    importing: Boolean,
    notice: String?,
    problem: String?,
    onSave: (CertificationEntity) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onAddAttachment: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onExportAttachment: (CareerAttachmentEntity) -> Unit,
    onDeleteAttachment: (CareerAttachmentEntity) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var issuer by rememberSaveable { mutableStateOf(existing?.issuer.orEmpty()) }
    var credential by rememberSaveable { mutableStateOf(existing?.credentialId.orEmpty()) }
    var earned by rememberSaveable { mutableStateOf(existing?.earnedEpochDay ?: LocalDate.now().toEpochDay()) }
    var hasExpiry by rememberSaveable { mutableStateOf(existing?.expiryEpochDay != null) }
    var expiry by rememberSaveable { mutableStateOf(existing?.expiryEpochDay ?: LocalDate.now().plusYears(2).toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppScreen(
        if (existing == null) "New certification" else "Certification",
        onBack = onBack,
        actions = { ArchiveDeleteActions(existing?.archived, onToggleArchive) { confirmDelete = true } },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    when {
                        name.isBlank() -> error = "Enter the certification name."
                        hasExpiry && expiry < earned -> error = "The expiry is before the date earned."
                        else -> onSave(
                            CertificationEntity(
                                id, name.trim(), issuer.trim().ifEmpty { null }, earned, if (hasExpiry) expiry else null,
                                credential.trim().ifEmpty { null }, note.trim().ifEmpty { null }, existing?.archived ?: false,
                                existing?.createdAtMillis ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                })
            }
        },
    ) {
        Section("Certification") {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = issuer, onValueChange = { issuer = it }, label = { Text("Issued by (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = credential, onValueChange = { credential = it }, label = { Text("Credential ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField("Earned on", earned) { earned = it }
            SwitchRow(title = "Expires", subtitle = "SenTrack reminds you 30 days before, at 9 am.", checked = hasExpiry, onChange = { hasExpiry = it })
            if (hasExpiry) DateField("Expires on", expiry) { expiry = it }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        if (existing != null) {
            Message(notice, MessageKind.INFO)
            Message(problem, MessageKind.ERROR)
            FilesPanel("Certificate", "The certificate or badge, encrypted on this phone.", attachments, importing, onAddAttachment, onOpenAttachment, onExportAttachment, onDeleteAttachment)
        } else {
            Section("Certificate", "Save first, then open it again to attach the certificate.") {}
        }
    }
    if (confirmDelete && existing != null) ConfirmDelete(existing.name, { confirmDelete = false; onDelete() }, { confirmDelete = false })
}
