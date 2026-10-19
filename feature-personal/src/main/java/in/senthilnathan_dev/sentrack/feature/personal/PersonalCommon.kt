package `in`.senthilnathan_dev.sentrack.feature.personal

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.attachments.attachmentIcon
import `in`.senthilnathan_dev.sentrack.core.data.personal.PersonalAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import java.util.Locale
import kotlin.math.roundToLong

/** Files attached to a plan item or achievement. Same look and behaviour as the note editor's attachment list. */
@Composable
internal fun AttachmentsPanel(
    attachments: List<PersonalAttachmentEntity>,
    importing: Boolean,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onExport: (PersonalAttachmentEntity) -> Unit,
    onDelete: (PersonalAttachmentEntity) -> Unit,
) {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<PersonalAttachmentEntity?>(null) }
    Section("Attachments", "Photos, PDFs and videos are encrypted on this phone.") {
        attachments.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.weight(1f).clickable { onOpen(a.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(attachmentIcon(a.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                        Text(Formatter.formatShortFileSize(context, a.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconAction(Icons.Filled.Download, "Save a readable copy of ${a.name}", { onExport(a) })
                IconAction(Icons.Filled.Delete, "Remove ${a.name}", { deleting = a })
            }
        }
        SecondaryButton(if (importing) "Attaching…" else "Attach files", onClick = onAdd, enabled = !importing, icon = Icons.Filled.AttachFile)
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${a.name}?") },
            text = { Text("The encrypted copy is deleted from this phone.") },
            confirmButton = { TextButton(onClick = { onDelete(a); deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Permanent deletion needs the word DELETE typed, so it cannot happen by a stray tap. */
@Composable
internal fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete permanently?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("“$what” and its attachments will be erased and cannot be recovered. Archive it instead to keep it out of sight.")
                OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = typed.trim() == "DELETE") { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "₹12,500" or "₹12,500.50" from paise. */
internal fun rupees(paise: Long): String {
    val whole = paise / 100
    val frac = paise % 100
    val grouped = String.format(Locale.ENGLISH, "%,d", whole)
    return if (frac == 0L) "₹$grouped" else "₹$grouped.%02d".format(Locale.ENGLISH, frac)
}

/** Parses "12500" or "12,500.50" into paise; null when it is not a number. */
internal fun parseRupees(text: String): Long? =
    text.trim().replace(",", "").toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 100).roundToLong() }
