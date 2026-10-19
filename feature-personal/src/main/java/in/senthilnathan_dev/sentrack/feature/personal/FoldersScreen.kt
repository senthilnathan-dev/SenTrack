package `in`.senthilnathan_dev.sentrack.feature.personal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteFolderEntity
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section

/** Create, rename and archive folders. Archived folders are hidden until you ask for them with the archive icon. */
@Composable
internal fun FoldersScreen(
    data: NotesData,
    onAdd: (String) -> Unit,
    onSave: (NoteFolderEntity) -> Unit,
    onBack: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<NoteFolderEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    val archivedCount = data.folders.count { it.archived }

    AppScreen(
        if (showArchived) "Archived folders" else "Folders",
        onBack = { if (showArchived) showArchived = false else onBack() },
        actions = {
            if (!showArchived && archivedCount > 0) IconAction(Icons.Filled.Archive, "Show archived folders ($archivedCount)", { showArchived = true })
        },
    ) {
        if (showArchived) {
            Section("Archived", "Their notes are kept. Restore a folder to use it again.") {
                data.folders.filter { it.archived }.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, modifier = Modifier.weight(1f))
                        IconAction(Icons.Filled.Unarchive, "Restore ${f.name}", { onSave(f.copy(archived = false)) })
                    }
                }
            }
            return@AppScreen
        }

        Section("New folder") {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Message(error, MessageKind.ERROR)
            PrimaryButton("Add folder", icon = Icons.Filled.Add, onClick = {
                val name = newName.trim()
                when {
                    name.isEmpty() -> error = "Enter a name."
                    data.folders.any { it.name.equals(name, ignoreCase = true) } -> error = "A folder with that name exists."
                    else -> { onAdd(name); newName = ""; error = null }
                }
            })
        }

        Section("Your folders", "Tap a folder to rename it.") {
            val active = data.folders.filterNot { it.archived }
            if (active.isEmpty()) Text("No folders yet.")
            active.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider()
                val count = data.notes.count { it.folderId == f.id && !it.archived }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f).clickable { renaming = f; renameText = f.name }.padding(vertical = 6.dp),
                    ) {
                        Text(f.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (count == 1) "1 note" else "$count notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconAction(Icons.Filled.Archive, "Archive ${f.name}", { onSave(f.copy(archived = true)) })
                }
            }
        }
    }

    renaming?.let { f ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename folder") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onSave(f.copy(name = renameText.trim()))
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}
