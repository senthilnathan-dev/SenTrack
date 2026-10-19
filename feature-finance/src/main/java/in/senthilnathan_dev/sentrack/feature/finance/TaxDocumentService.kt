package `in`.senthilnathan_dev.sentrack.feature.finance

import android.net.Uri
import `in`.senthilnathan_dev.sentrack.core.attachments.AttachmentService
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocCategory
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocumentEntity
import java.util.UUID

/** Outcome of importing a batch of files. */
internal data class ImportResult(val imported: Int, val failed: List<String>)

/** Tax documents: the encrypted file handling comes from [AttachmentService]; this adds the tax records. */
internal class TaxDocumentService(
    private val repo: FinanceRepository,
    private val attachments: AttachmentService,
) {
    suspend fun importFiles(filingId: String, category: TaxDocCategory, uris: List<Uri>): ImportResult {
        var imported = 0
        val failed = mutableListOf<String>()
        for (uri in uris) {
            try {
                val file = attachments.importFile(uri)
                repo.saveTaxDocument(
                    TaxDocumentEntity(
                        id = UUID.randomUUID().toString(),
                        filingId = filingId,
                        name = file.name,
                        category = category,
                        mimeType = file.mimeType,
                        sizeBytes = file.sizeBytes,
                        vaultFileId = file.vaultFileId,
                        addedAtMillis = System.currentTimeMillis(),
                    ),
                )
                imported++
            } catch (e: Exception) {
                failed += (uri.lastPathSegment ?: "a file")
            }
        }
        return ImportResult(imported, failed)
    }

    /** Writes a decrypted copy of [doc] to [target]. Returns false if anything went wrong. */
    suspend fun export(doc: TaxDocumentEntity, target: Uri): Boolean = attachments.export(doc.vaultFileId, target)

    /** Removes the record and the encrypted file. */
    suspend fun delete(doc: TaxDocumentEntity) {
        repo.deleteTaxDocument(doc.id)
        attachments.delete(doc.vaultFileId)
    }
}
