package `in`.senthilnathan_dev.sentrack.core.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** A file that has been moved into the encrypted vault. */
data class ImportedFile(val name: String, val mimeType: String, val sizeBytes: Long, val vaultFileId: String)

/**
 * Moves files between the outside world and the encrypted file vault, for every module that has attachments
 * (tax documents, note attachments, achievement certificates...).
 *
 * Import reads the chosen file straight into the vault, encrypted as it is written. Export decrypts to a place the
 * user picks, which is the one deliberate way a readable copy leaves SenTrack. Previews use a short-lived decrypted
 * copy in the app's private cache that is removed on close and whenever the vault locks.
 */
class AttachmentService(context: Context, private val files: FileVault) {
    private val context = context.applicationContext

    /** Encrypts the file behind [uri] into the vault. @throws IOException if it cannot be read. */
    suspend fun importFile(uri: Uri): ImportedFile = withContext(Dispatchers.IO) {
        val (name, size) = describe(uri)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
        val vaultId = input.use { files.store(it) }
        ImportedFile(name, context.contentResolver.getType(uri) ?: guessMime(name), size, vaultId)
    }

    /** Writes a decrypted copy to [target]. Returns false if anything went wrong. */
    suspend fun export(vaultFileId: String, target: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(target) ?: return@withContext false
            output.use { out -> files.open(vaultFileId).use { it.copyTo(out) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Removes the encrypted file permanently. */
    suspend fun delete(vaultFileId: String): Boolean = withContext(Dispatchers.IO) { files.delete(vaultFileId) }

    /** Decrypts into the private cache for a viewer that needs a real file (PDF, video). The caller deletes it afterwards. */
    suspend fun decryptToCache(vaultFileId: String, extension: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, VIEWING_DIR).also { it.mkdirs() }
        val target = File(dir, UUID.randomUUID().toString() + "." + extension)
        files.open(vaultFileId).use { input -> target.outputStream().use { input.copyTo(it) } }
        target
    }

    private fun describe(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"
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

    companion object {
        /** Folder in the app cache for short-lived decrypted copies; emptied on lock and at start. */
        const val VIEWING_DIR = "viewing"

        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "mp4", "m4v" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "xml" -> "text/xml"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }

        /** File extension to give a decrypted copy so players recognise it. */
        fun extensionFor(mime: String): String = when {
            mime == "application/pdf" -> "pdf"
            mime == "video/mp4" -> "mp4"
            mime == "video/3gpp" -> "3gp"
            mime == "video/webm" -> "webm"
            mime == "video/x-matroska" -> "mkv"
            mime == "video/quicktime" -> "mov"
            mime.startsWith("video/") -> "mp4"
            mime.startsWith("image/") -> "img"
            else -> "bin"
        }
    }
}
