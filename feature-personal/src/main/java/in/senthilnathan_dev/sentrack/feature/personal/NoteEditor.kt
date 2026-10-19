package `in`.senthilnathan_dev.sentrack.feature.personal

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.attachments.attachmentIcon
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteEntity
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PickerField
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import kotlinx.coroutines.delay

/** Everything typed in the editor, compared to know whether anything changed since the last save. */
private data class Draft(
    val title: String,
    val body: String,
    val folderId: String?,
    val tags: List<String>,
    val pinned: Boolean,
    val archived: Boolean,
)

/**
 * Write or read one note. Changes are saved automatically a moment after you stop typing, when you leave, and
 * before attaching a file, so nothing is lost and there is no Save button to remember.
 *
 * @param noteId the note's id; a new note already has one, and its row is created at the first save with content.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteEditorScreen(
    noteId: String,
    existing: NoteEntity?,
    data: NotesData,
    attachments: List<NoteAttachmentEntity>,
    notice: String?,
    problem: String?,
    importing: Boolean,
    onSave: (NoteEntity, Set<String>) -> Unit,
    onAddFolder: (String) -> Unit,
    onPickAttachments: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onExportAttachment: (NoteAttachmentEntity) -> Unit,
    onDeleteAttachment: (NoteAttachmentEntity) -> Unit,
    onDeleteNote: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val createdAt = rememberSaveable { existing?.createdAtMillis ?: System.currentTimeMillis() }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existing?.body.orEmpty(), TextRange((existing?.body ?: "").length)))
    }
    var folderId by rememberSaveable { mutableStateOf(existing?.folderId) }
    var tags by rememberSaveable { mutableStateOf(existing?.let { data.tagNames(it.id) } ?: emptyList()) }
    var pinned by rememberSaveable { mutableStateOf(existing?.pinned ?: false) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var reading by rememberSaveable { mutableStateOf(existing != null && existing.body.isNotBlank()) }
    var newTag by rememberSaveable { mutableStateOf("") }
    var newFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var typedDelete by remember { mutableStateOf("") }
    var deletingAttachment by remember { mutableStateOf<NoteAttachmentEntity?>(null) }

    fun draft() = Draft(title, body.text, folderId, tags, pinned, archived)
    var lastSaved by remember {
        mutableStateOf(existing?.let { Draft(it.title, it.body, it.folderId, data.tagNames(it.id), it.pinned, it.archived) })
    }
    val hasContent = title.isNotBlank() || body.text.isNotBlank() || attachments.isNotEmpty() || tags.isNotEmpty()

    fun saveNow() {
        val current = draft()
        if (current == lastSaved || (!hasContent && existing == null)) return
        lastSaved = current
        onSave(
            NoteEntity(
                id = noteId, folderId = folderId, title = title.trim(), body = body.text, pinned = pinned,
                archived = archived, createdAtMillis = createdAt, updatedAtMillis = System.currentTimeMillis(),
            ),
            tags.toSet(),
        )
    }

    // Save a moment after the last change.
    LaunchedEffect(title, body.text, folderId, tags, pinned, archived) {
        delay(900)
        saveNow()
    }

    fun leave() {
        saveNow()
        onBack()
    }
    BackHandler { leave() }

    AppScreen(
        if (existing == null && !hasContent) "New note" else title.ifBlank { "Note" },
        onBack = ::leave,
        actions = {
            IconAction(if (reading) Icons.Filled.Edit else Icons.Filled.Visibility, if (reading) "Edit the text" else "Read the note", { reading = !reading })
            IconAction(Icons.Filled.PushPin, if (pinned) "Unpin" else "Pin to the top", { pinned = !pinned }, tint = if (pinned) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified)
            IconAction(if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive, if (archived) "Restore from archive" else "Archive this note", { archived = !archived })
            if (existing != null) IconAction(Icons.Filled.Delete, "Delete this note permanently", { typedDelete = ""; confirmDelete = true })
        },
        bottomBar = {
            ActionBar {
                Message(notice, MessageKind.SUCCESS)
                Message(problem, MessageKind.ERROR)
                PrimaryButton("Done", onClick = ::leave, icon = Icons.Filled.Check)
            }
        },
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (reading) {
            MarkdownView(
                body.text,
                onToggleCheck = { line ->
                    val updated = NoteFormat.toggleCheck(body.text, line)
                    body = TextFieldValue(updated, TextRange(updated.length))
                },
            )
        } else {
            BodyEditor(body) { body = it }
        }

        Section("Organise") {
            val folderName = data.folderName(folderId)
            PickerField(
                "Folder",
                folderName ?: "No folder",
                listOf(Pair<String, String?>("No folder", null)) + data.folders.filter { !it.archived || it.id == folderId }.map { Pair<String, String?>(it.name, it.id) },
            ) { folderId = it }
            TextButton(onClick = { newFolderName = ""; newFolderDialog = true }) { Text("+ New folder") }

            Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    FilterChip(selected = true, onClick = { tags = tags - t }, label = { Text("#$t  ✕") })
                }
                data.tags.filter { s -> tags.none { it.equals(s.name, ignoreCase = true) } }.take(12).forEach { s ->
                    FilterChip(selected = false, onClick = { tags = tags + s.name }, label = { Text("#${s.name}") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Add a tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconAction(Icons.Filled.Add, "Add the tag", {
                    val name = newTag.trim().trimStart('#')
                    if (name.isNotEmpty() && tags.none { it.equals(name, ignoreCase = true) }) tags = tags + name
                    newTag = ""
                })
            }
        }

        Section("Attachments", "Photos, videos, PDFs and any other files. Encrypted on this phone.") {
            if (attachments.isEmpty()) Text("Nothing attached.")
            attachments.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.weight(1f).clickable { saveNow(); onOpenAttachment(a.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(attachmentIcon(a.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                Formatter.formatShortFileSize(context, a.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconAction(Icons.Filled.Download, "Save a readable copy of ${a.name}", { onExportAttachment(a) })
                    IconAction(Icons.Filled.Delete, "Remove ${a.name}", { deletingAttachment = a })
                }
            }
            SecondaryButton(
                if (importing) "Adding…" else "Attach files",
                onClick = { saveNow(); onPickAttachments() },
                enabled = !importing,
                icon = Icons.Filled.AttachFile,
            )
        }
    }

    if (newFolderDialog) {
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text("New folder") },
            text = { OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) onAddFolder(newFolderName.trim())
                    newFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("Cancel") } },
        )
    }

    deletingAttachment?.let { a ->
        AlertDialog(
            onDismissRequest = { deletingAttachment = null },
            title = { Text("Remove ${a.name}?") },
            text = { Text("The encrypted file is permanently deleted from this phone. It stays in older Drive backups until they are replaced.") },
            confirmButton = { TextButton(onClick = { deletingAttachment = null; onDeleteAttachment(a) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deletingAttachment = null }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("The note and all its attachments are permanently deleted. Archiving keeps them instead. Type DELETE to confirm.")
                    OutlinedTextField(value = typedDelete, onValueChange = { typedDelete = it }, singleLine = true, label = { Text("Type DELETE") })
                }
            },
            confirmButton = {
                TextButton(enabled = typedDelete.trim() == "DELETE", onClick = { confirmDelete = false; onDeleteNote() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
