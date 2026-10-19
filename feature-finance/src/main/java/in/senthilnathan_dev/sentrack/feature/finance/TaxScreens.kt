package `in`.senthilnathan_dev.sentrack.feature.finance

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocCategory
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocumentEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxFilingEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxStatus
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import java.time.LocalDate
import java.util.UUID

/** The tax filings list shown in the Plan tab: one row per financial year, with its status and document count. */
@Composable
internal fun ColumnScope.TaxFilingsSection(
    filings: List<TaxFilingEntity>,
    documents: List<TaxDocumentEntity>,
    onOpen: (String) -> Unit,
    onAddYear: (Int) -> Unit,
    onOpenArchived: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val shown = filings.filterNot { it.archived }   // archived years stay out of sight until asked for
    val archivedCount = filings.count { it.archived }
    Section(
        "Tax filings",
        "Keep each financial year's filing details and documents (Form 16, AIS, ITR, acknowledgement) encrypted in one place.",
    ) {
        if (shown.isEmpty()) {
            Text("No financial year added yet.")
        } else {
            shown.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider()
                val count = documents.count { it.filingId == f.id }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(f.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(FinanceInsights.financialYearLabel(f.financialYearStart), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Assessment year ${f.financialYearStart + 1}-${((f.financialYearStart + 2) % 100).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(f.status.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (count == 1) "1 document" else "$count documents",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        SecondaryButton("Add a financial year", onClick = { picking = true }, icon = Icons.Filled.Add)
        if (archivedCount > 0) {
            SecondaryButton("Archived tax years ($archivedCount)", onClick = onOpenArchived, icon = Icons.Filled.Archive)
        }
    }

    if (picking) {
        val current = FinanceInsights.financialYearOf(LocalDate.now())
        val options = (current downTo current - 6).filter { y -> filings.none { it.financialYearStart == y } }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Which financial year?") },
            text = {
                if (options.isEmpty()) {
                    Text("Every recent year is already added.")
                } else {
                    Column {
                        options.forEach { y ->
                            TextButton(onClick = { picking = false; onAddYear(y) }, modifier = Modifier.fillMaxWidth()) {
                                Text(FinanceInsights.financialYearLabel(y))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

private fun TaxDocumentEntity.icon() = when {
    mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf
    mimeType.startsWith("image/") -> Icons.Filled.Image
    else -> Icons.Filled.Description
}

/** One financial year: status and key numbers (saved with the pinned button), and its encrypted documents. */
@Composable
internal fun FilingDetailScreen(
    filing: TaxFilingEntity,
    documents: List<TaxDocumentEntity>,
    importing: Boolean,
    notice: String?,
    problem: String?,
    onSave: (TaxFilingEntity) -> Unit,
    onPickDocuments: (TaxDocCategory) -> Unit,
    onOpenDocument: (String) -> Unit,
    onExportDocument: (TaxDocumentEntity) -> Unit,
    onDeleteDocument: (TaxDocumentEntity) -> Unit,
    onToggleArchive: (TaxFilingEntity) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var status by rememberSaveable { mutableStateOf(filing.status) }
    var filedOn by rememberSaveable { mutableStateOf(filing.filedOnEpochDay) }
    var ack by rememberSaveable { mutableStateOf(filing.acknowledgementNumber.orEmpty()) }
    var taxPaid by rememberSaveable { mutableStateOf(filing.taxPaidPaise?.let { Money.toInput(it) }.orEmpty()) }
    var refund by rememberSaveable { mutableStateOf(filing.refundPaise?.let { Money.toInput(it) }.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(filing.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var choosingCategory by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(TaxDocCategory.FORM_16) }
    var confirmArchive by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaxDocumentEntity?>(null) }
    var typed by remember { mutableStateOf("") }

    /** The filing as currently typed, or null (with an explanation in [error]) if a number is not valid. */
    fun readFields(): TaxFilingEntity? {
        val paid = if (taxPaid.isBlank()) null else Money.parse(taxPaid)
        val back = if (refund.isBlank()) null else Money.parse(refund)
        return when {
            taxPaid.isNotBlank() && paid == null -> { error = "Enter tax paid as a number, or leave it empty."; null }
            refund.isNotBlank() && back == null -> { error = "Enter the refund as a number, or leave it empty."; null }
            else -> {
                error = null
                filing.copy(
                    status = status, filedOnEpochDay = filedOn, acknowledgementNumber = ack.trim().ifEmpty { null },
                    taxPaidPaise = paid, refundPaise = back, note = note.trim().ifEmpty { null },
                )
            }
        }
    }

    fun submit() {
        readFields()?.let(onSave)
    }

    /** Saves quietly if anything changed and is valid, so details are never lost by leaving or by adding a document. */
    fun saveIfChanged() {
        val current = readFields()
        if (current != null && current != filing) onSave(current)
    }

    /** Archives or restores this year together with anything typed, so one save cannot overwrite the other. */
    fun toggleArchive() {
        onToggleArchive((readFields() ?: filing).copy(archived = !filing.archived))
    }

    // The system Back button and the top-bar arrow both keep what was typed.
    BackHandler {
        saveIfChanged()
        onBack()
    }

    AppScreen(
        FinanceInsights.financialYearLabel(filing.financialYearStart),
        onBack = { saveIfChanged(); onBack() },
        actions = {
            IconAction(
                if (filing.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                if (filing.archived) "Restore this tax year" else "Archive this tax year",
                { if (filing.archived) toggleArchive() else confirmArchive = true },
            )
        },
        // Save stays pinned at the bottom; its confirmation or error is shown right above it, where you are looking.
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                Message(notice, MessageKind.SUCCESS)
                Message(problem, MessageKind.ERROR)
                PrimaryButton("Save details", onClick = ::submit, icon = Icons.Filled.Check)
            }
        },
    ) {

        Section("Filing") {
            PickerField("Status", status.label, TaxStatus.entries.map { it.label to it }) { status = it }
            val date = filedOn
            if (date == null) {
                SecondaryButton("Set the filing date", onClick = { filedOn = LocalDate.now().toEpochDay() })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) { DateField("Filed on", date) { filedOn = it } }
                    IconAction(Icons.Filled.Close, "Clear the filing date", { filedOn = null })
                }
            }
            OutlinedTextField(
                value = ack,
                onValueChange = { ack = it },
                label = { Text("Acknowledgement number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = taxPaid,
                onValueChange = { taxPaid = it },
                label = { Text("Tax paid (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = refund,
                onValueChange = { refund = it },
                label = { Text("Refund (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Documents", "Encrypted on this phone. File names and types are hidden from other apps and from Google Drive backups.") {
            if (documents.isEmpty()) Text("No documents yet.")
            documents.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f).clickable { onOpenDocument(d.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(d.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${d.category.label} · ${Formatter.formatShortFileSize(context, d.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconAction(Icons.Filled.Download, "Save a readable copy of ${d.name}", { onExportDocument(d) })
                    IconAction(Icons.Filled.Delete, "Delete ${d.name}", { typed = ""; deleting = d })
                }
            }
            PrimaryButton(
                if (importing) "Adding…" else "Add documents",
                onClick = { saveIfChanged(); choosingCategory = true },
                busy = importing,
                icon = Icons.Filled.AttachFile,
            )
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive ${FinanceInsights.financialYearLabel(filing.financialYearStart)}?") },
            text = { Text("It moves out of your tax filings list. Its details and documents are kept and can be restored any time from Archived tax years.") },
            confirmButton = { TextButton(onClick = { confirmArchive = false; toggleArchive() }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } },
        )
    }

    if (choosingCategory) {
        AlertDialog(
            onDismissRequest = { choosingCategory = false },
            title = { Text("What kind of document?") },
            text = {
                PickerField("Type", category.label, TaxDocCategory.entries.map { it.label to it }) { category = it }
            },
            confirmButton = { TextButton(onClick = { choosingCategory = false; onPickDocuments(category) }) { Text("Choose files") } },
            dismissButton = { TextButton(onClick = { choosingCategory = false }) { Text("Cancel") } },
        )
    }

    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${doc.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This permanently removes the encrypted file from this phone. It stays in older Drive backups only until they are replaced. Type DELETE to confirm.")
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, label = { Text("Type DELETE") })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim() == "DELETE",
                    onClick = { deleting = null; onDeleteDocument(doc) },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Convenience used by the Finance tab when a filing for a year is first added. */
internal fun newFiling(financialYearStart: Int) = TaxFilingEntity(
    id = UUID.randomUUID().toString(),
    financialYearStart = financialYearStart,
    status = TaxStatus.NOT_STARTED,
    filedOnEpochDay = null,
    acknowledgementNumber = null,
    taxPaidPaise = null,
    refundPaise = null,
    note = null,
    createdAtMillis = System.currentTimeMillis(),
)

/** Tax years that were archived. Open one to look, or restore it with the icon. */
@Composable
internal fun ArchivedTaxYearsScreen(
    archived: List<TaxFilingEntity>,
    documents: List<TaxDocumentEntity>,
    onOpen: (String) -> Unit,
    onRestore: (String) -> Unit,
    onBack: () -> Unit,
) {
    AppScreen("Archived tax years", onBack = onBack) {
        if (archived.isEmpty()) {
            Section("Nothing archived", "Archive a tax year from its page to tidy your list without losing anything.") {}
        } else {
            Section("Archived", "Details and documents are kept. Restore a year to bring it back to the list.") {
                archived.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider()
                    val count = documents.count { it.filingId == f.id }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(f.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(FinanceInsights.financialYearLabel(f.financialYearStart), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${f.status.label} · ${if (count == 1) "1 document" else "$count documents"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconAction(
                            Icons.Filled.Unarchive,
                            "Restore ${FinanceInsights.financialYearLabel(f.financialYearStart)}",
                            { onRestore(f.id) },
                        )
                    }
                }
            }
        }
    }
}
