package `in`.senthilnathan_dev.sentrack.feature.personal

import android.text.format.DateUtils
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteFolderEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteTagEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteTagLinkEntity
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section

/** Everything the notes screens need about the stored notes, pre-joined so screens stay simple. */
internal class NotesData(
    val notes: List<NoteEntity>,
    val folders: List<NoteFolderEntity>,
    val tags: List<NoteTagEntity>,
    val links: List<NoteTagLinkEntity>,
    val attachments: List<NoteAttachmentEntity>,
) {
    private val tagById = tags.associateBy { it.id }
    private val folderById = folders.associateBy { it.id }

    fun tagNames(noteId: String): List<String> =
        links.filter { it.noteId == noteId }.mapNotNull { tagById[it.tagId]?.name }.sortedBy { it.lowercase() }

    fun folderName(folderId: String?): String? = folderId?.let { folderById[it]?.name }

    fun attachmentsOf(noteId: String): List<NoteAttachmentEntity> = attachments.filter { it.noteId == noteId }

    /** Whether the note matches a search: title, formatted-text content, tags or folder name. */
    fun matches(note: NoteEntity, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return note.title.lowercase().contains(q) ||
            NoteFormat.plainText(note.body).lowercase().contains(q) ||
            tagNames(note.id).any { it.lowercase().contains(q) } ||
            (folderName(note.folderId)?.lowercase()?.contains(q) == true)
    }
}

/** The Notes section: search, folder and tag filters, and the notes themselves (pinned first). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.NotesContent(
    data: NotesData,
    onOpen: (String) -> Unit,
    onOpenArchived: () -> Unit,
    onOpenFolders: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var folderFilter by rememberSaveable { mutableStateOf<String?>(null) }   // null = all folders
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }      // tag name, null = any

    val active = data.notes.filterNot { it.archived }
    val archivedCount = data.notes.count { it.archived }
    val visible = active.filter { n ->
        data.matches(n, query) &&
            (folderFilter == null || n.folderId == folderFilter) &&
            (tagFilter == null || data.tagNames(n.id).any { it.equals(tagFilter, ignoreCase = true) })
    }
    val usedTags = data.tags.filter { t -> data.links.any { it.tagId == t.id } }
    val activeFolders = data.folders.filterNot { it.archived }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search notes") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (activeFolders.isNotEmpty() || usedTags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = folderFilter == null && tagFilter == null, onClick = { folderFilter = null; tagFilter = null }, label = { Text("All") })
            activeFolders.forEach { f ->
                FilterChip(
                    selected = folderFilter == f.id,
                    onClick = { folderFilter = if (folderFilter == f.id) null else f.id },
                    label = { Text(f.name) },
                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                )
            }
            usedTags.forEach { t ->
                FilterChip(
                    selected = tagFilter.equals(t.name, ignoreCase = true),
                    onClick = { tagFilter = if (tagFilter.equals(t.name, ignoreCase = true)) null else t.name },
                    label = { Text("#${t.name}") },
                )
            }
        }
    }

    if (visible.isEmpty()) {
        Section(
            if (active.isEmpty()) "No notes yet" else "Nothing found",
            if (active.isEmpty()) "Tap “New note” to write your first one. Notes can hold formatted text, photos, videos and files, all encrypted." else "No note matches the search and filters.",
        ) {}
    } else {
        visible.forEach { note -> NoteCard(note, data, onClick = { onOpen(note.id) }) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Folders", onClick = onOpenFolders, icon = Icons.Filled.Folder)
    }
    if (archivedCount > 0) SecondaryButton("Archived notes ($archivedCount)", onClick = onOpenArchived, icon = Icons.Filled.Archive)
}

@Composable
internal fun NoteCard(note: NoteEntity, data: NotesData, onClick: () -> Unit) {
    val context = LocalContext.current
    val tags = data.tagNames(note.id)
    val attachmentCount = data.attachmentsOf(note.id).size
    val meta = listOfNotNull(
        data.folderName(note.folderId),
        tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
        DateUtils.getRelativeTimeSpanString(note.updatedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
    ).joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (attachmentCount > 0) Icon(Icons.Filled.AttachFile, contentDescription = "$attachmentCount attachments", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (note.pinned) Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary)
            }
            val snippet = NoteFormat.snippet(note.body, 140)
            if (snippet.isNotBlank()) Text(snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Notes that were archived: hidden from the main list, kept safe, and easy to restore. */
@Composable
internal fun ArchivedNotesScreen(data: NotesData, onOpen: (String) -> Unit, onRestore: (NoteEntity) -> Unit, onBack: () -> Unit) {
    val archived = data.notes.filter { it.archived }
    AppScreen("Archived notes", onBack = onBack) {
        if (archived.isEmpty()) {
            Section("Nothing archived", "Archive a note from its page to hide it without losing it.") {}
        } else {
            archived.forEach { note ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) { NoteCard(note, data) { onOpen(note.id) } }
                    IconAction(Icons.Filled.Unarchive, "Restore ${note.title.ifBlank { "note" }}", { onRestore(note) })
                }
            }
        }
    }
}
