package `in`.senthilnathan_dev.sentrack.feature.finance

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocCategory
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocumentEntity
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** Outcome of importing a batch of files. */
internal data class ImportResult(val imported: Int, val failed: List<String>)

/**
 * Moves tax documents between the outside world and the encrypted vault.
 *
 * Import reads the chosen file straight into the vault (encrypted as it is written). Export decrypts to a
 * destination the user picks, which is the one deliberate way a readable copy leaves SenTrack.
 */
internal class TaxDocumentService(
    private val context: Context,
    private val repo: FinanceRepository,
    private val files: FileVault,
) {
    suspend fun importFiles(filingId: String, category: TaxDocCategory, uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        val failed = mutableListOf<String>()
        for (uri in uris) {
            val (name, size) = describe(uri)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
                val vaultId = input.use { files.store(it) }
                repo.saveTaxDocument(
                    TaxDocumentEntity(
                        id = UUID.randomUUID().toString(),
                        filingId = filingId,
                        name = name,
                        category = category,
                        mimeType = context.contentResolver.getType(uri) ?: guessMime(name),
                        sizeBytes = size,
                        vaultFileId = vaultId,
                        addedAtMillis = System.currentTimeMillis(),
                    ),
                )
                imported++
            } catch (e: Exception) {
                failed += name
            }
        }
        ImportResult(imported, failed)
    }

    /** Writes a decrypted copy of [doc] to [target]. Returns false if anything went wrong. */
    suspend fun export(doc: TaxDocumentEntity, target: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(target) ?: return@withContext false
            output.use { out -> files.open(doc.vaultFileId).use { it.copyTo(out) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Removes the record and the encrypted file. */
    suspend fun delete(doc: TaxDocumentEntity) = withContext(Dispatchers.IO) {
        repo.deleteTaxDocument(doc.id)
        files.delete(doc.vaultFileId)
    }

    /** Decrypts [doc] into the app's private cache for the PDF renderer. The caller must delete the file after use. */
    suspend fun decryptToCache(doc: TaxDocumentEntity): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, VIEWING_DIR).also { it.mkdirs() }
        val target = File(dir, UUID.randomUUID().toString() + ".pdf")
        files.open(doc.vaultFileId).use { input -> target.outputStream().use { input.copyTo(it) } }
        target
    }

    private fun describe(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "document"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        return name to size
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "xml" -> "text/xml"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    companion object {
        /** Folder in the app cache for short-lived decrypted copies; emptied on lock and at start. */
        const val VIEWING_DIR = "viewing"
    }
}
