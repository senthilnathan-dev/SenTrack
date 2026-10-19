package `in`.senthilnathan_dev.sentrack.feature.personal

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.attachments.attachmentIcon
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteFolderEntity
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
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

private fun TextFieldValue.toEdit() = NoteEditing.Edit(text, selection.start, selection.end)
private fun NoteEditing.Edit.toValue() = TextFieldValue(text, TextRange(selStart, selEnd))

/**
 * Write or read one note, the way modern notes apps work: a borderless page with live formatting, a toolbar docked
 * above the keyboard (undo, redo, formats, attach), word suggestions from your own notes, lists that continue when
 * you press Enter, and chips for folder and tags. Everything saves itself.
 *
 * @param noteId the note's id; a new note already has one, and its row is created at the first save with content.
 * @param vocabulary word counts from all notes, for the suggestion strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteEditorScreen(
    noteId: String,
    existing: NoteEntity?,
    data: NotesData,
    vocabulary: Map<String, Int>,
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
    var newFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var tagDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var typedDelete by remember { mutableStateOf("") }
    var deletingAttachment by remember { mutableStateOf<NoteAttachmentEntity?>(null) }
    val history = remember { EditHistory(body.text) }
    var historyTick by remember { mutableStateOf(0) }   // bumped so the undo and redo buttons refresh

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

    /** Typing: keeps lists going on Enter, and feeds the undo history. */
    fun onBodyChange(new: TextFieldValue) {
        val assisted = EditAssist.continueList(body.toEdit(), new.toEdit())
        val result = assisted?.toValue() ?: new
        history.record(EditHistory.Snapshot(result.text, result.selection.start, result.selection.end), System.currentTimeMillis(), forceNewStep = assisted != null)
        body = result
        historyTick++
    }

    fun applyAction(action: FormatAction) {
        val result = applyFormat(body, action)
        history.record(EditHistory.Snapshot(result.text, result.selection.start, result.selection.end), System.currentTimeMillis(), forceNewStep = true)
        body = result
        historyTick++
    }

    fun restore(s: EditHistory.Snapshot?) {
        if (s != null) { body = TextFieldValue(s.text, TextRange(s.selStart, s.selEnd)); historyTick++ }
    }

    val prefix = if (!reading && body.selection.collapsed) EditAssist.prefixAt(body.text, body.selection.end) else ""
    val suggestions = remember(vocabulary, prefix) { EditAssist.suggest(vocabulary, prefix) }

    AppScreen(
        title = "",
        largeHeader = false,
        onBack = ::leave,
        actions = {
            IconAction(if (reading) Icons.Filled.Edit else Icons.Filled.Visibility, if (reading) "Edit the text" else "Read the note", { reading = !reading })
            IconAction(
                Icons.Filled.PushPin, if (pinned) "Unpin" else "Pin to the top", { pinned = !pinned },
                tint = if (pinned) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            IconAction(if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive, if (archived) "Restore from archive" else "Archive this note", { archived = !archived })
            if (existing != null) IconAction(Icons.Filled.Delete, "Delete this note permanently", { typedDelete = ""; confirmDelete = true })
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Message(notice, MessageKind.SUCCESS)
                    Message(problem, MessageKind.ERROR)
                    if (!reading) {
                        if (suggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                suggestions.forEach { word ->
                                    SuggestionChip(
                                        onClick = {
                                            val done = EditAssist.complete(body.toEdit(), word)
                                            history.record(EditHistory.Snapshot(done.text, done.selStart, done.selEnd), System.currentTimeMillis(), forceNewStep = true)
                                            body = done.toValue()
                                            historyTick++
                                        },
                                        label = { Text(word) },
                                    )
                                }
                            }
                        }
                        // historyTick is read so this bar updates after every undo, redo or edit.
                        val ticked = historyTick
                        FormatToolbar(
                            canUndo = ticked >= 0 && history.canUndo,
                            canRedo = history.canRedo,
                            onUndo = { restore(history.undo()) },
                            onRedo = { restore(history.redo()) },
                            onAction = ::applyAction,
                            onAttach = { saveNow(); onPickAttachments() },
                        )
                    }
                }
            }
        },
    ) {
        // ---- Title
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            },
        )

        // ---- Folder and tags as chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FolderChip(
                name = data.folderName(folderId),
                folders = data.folders.filter { !it.archived || it.id == folderId },
                onSelect = { folderId = it },
                onNew = { newFolderName = ""; newFolderDialog = true },
            )
            tags.forEach { t -> FilterChip(selected = true, onClick = { tags = tags - t }, label = { Text("#$t  ✕") }) }
            AssistChip(onClick = { tagDialog = true }, label = { Text("Add tag") }, leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) })
        }

        HorizontalDivider()

        // ---- Body
        if (reading) {
            if (body.text.isBlank()) {
                Text("This note is empty. Tap the pencil to write.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MarkdownView(
                    body.text,
                    onToggleCheck = { line ->
                        val updated = NoteFormat.toggleCheck(body.text, line)
                        history.record(EditHistory.Snapshot(updated, updated.length, updated.length), System.currentTimeMillis(), forceNewStep = true)
                        body = TextFieldValue(updated, TextRange(updated.length))
                        historyTick++
                    },
                )
            }
        } else {
            LiveBodyField(body, ::onBodyChange)
        }

        // ---- Attachments
        if (attachments.isNotEmpty() || importing) {
            HorizontalDivider()
            Text(
                if (importing) "Attaching…" else "Attachments",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            attachments.forEach { a ->
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

    if (tagDialog) {
        var tagText by remember { mutableStateOf("") }
        val unused = data.tags.filter { s -> tags.none { it.equals(s.name, ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { tagDialog = false },
            title = { Text("Add a tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = tagText, onValueChange = { tagText = it }, label = { Text("New tag") }, singleLine = true)
                    if (unused.isNotEmpty()) {
                        Text("Or pick one you already use", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            unused.take(12).forEach { s ->
                                SuggestionChip(onClick = { tags = tags + s.name; tagDialog = false }, label = { Text("#${s.name}") })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = tagText.trim().trimStart('#')
                    if (name.isNotEmpty() && tags.none { it.equals(name, ignoreCase = true) }) tags = tags + name
                    tagDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { tagDialog = false }) { Text("Cancel") } },
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

/** The folder as a chip; tapping it opens a menu of folders. */
@Composable
private fun FolderChip(name: String?, folders: List<NoteFolderEntity>, onSelect: (String?) -> Unit, onNew: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(name ?: "No folder") }, leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("No folder") }, onClick = { onSelect(null); open = false })
            folders.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { onSelect(f.id); open = false }) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("+ New folder") }, onClick = { open = false; onNew() })
        }
    }
}
