package `in`.senthilnathan_dev.sentrack.core.security.vault

import `in`.senthilnathan_dev.sentrack.core.security.crypto.AesGcm
import `in`.senthilnathan_dev.sentrack.core.security.crypto.ChunkedAead
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.UUID

/**
 * Encrypted storage for attachments (tax PDFs, note images and videos, appreciation e-mails, resumes).
 *
 * Each file gets its own random key. That key is wrapped by the vault's "files" sub-key and bound to the
 * file's id, so a stored file cannot be swapped for another. Contents are encrypted in 64 KiB authenticated
 * chunks ([ChunkedAead]), so large videos stream without loading into memory.
 *
 * File name = random UUID: names, types and sizes of your documents are not visible in the file system.
 * Keep the original name and type in the database record.
 *
 * @param dir where encrypted blobs are kept; use a path excluded from backups.
 */
class FileVault(private val dir: File, private val session: VaultSession) {
    private val random = SecureRandom()

    /** Encrypts [source] into the vault and returns its id. The caller closes [source]. */
    fun store(source: InputStream): String {
        val id = UUID.randomUUID().toString()
        val fileKey = AesGcm.newKey()
        val prefix = ByteArray(ChunkedAead.NONCE_PREFIX_BYTES).also(random::nextBytes)
        val filesKey = session.deriveKey(KEY_LABEL)
        dir.mkdirs()
        val tmp = File(dir, "$id.tmp")
        try {
            val wrapped = AesGcm.encrypt(filesKey, fileKey, aad(id))
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                out.writeInt(MAGIC)
                out.writeByte(VERSION)
                out.writeShort(wrapped.size)
                out.write(wrapped)
                out.write(prefix)
                ChunkedAead.encrypt(fileKey, prefix, source, out)
            }
            Files.move(tmp.toPath(), target(id).toPath(), StandardCopyOption.ATOMIC_MOVE)
            return id
        } finally {
            fileKey.fill(0); filesKey.fill(0)
            tmp.delete()
        }
    }

    /**
     * Opens a verified plaintext stream. Tampering raises
     * [in.senthilnathan_dev.sentrack.core.security.crypto.AuthenticationFailedException] while reading.
     * The caller must close the stream.
     */
    fun open(id: String): InputStream {
        val input = DataInputStream(BufferedInputStream(FileInputStream(target(id))))
        try {
            if (input.readInt() != MAGIC) throw IOException("Not a vault file")
            if (input.readUnsignedByte() != VERSION) throw IOException("Unsupported vault file version")
            val wrapped = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
            val prefix = ByteArray(ChunkedAead.NONCE_PREFIX_BYTES).also { input.readFully(it) }
            val filesKey = session.deriveKey(KEY_LABEL)
            val fileKey = try {
                AesGcm.decrypt(filesKey, wrapped, aad(id))
            } finally {
                filesKey.fill(0)
            }
            return try {
                ChunkedAead.decrypt(fileKey, prefix, input)
            } finally {
                fileKey.fill(0)
            }
        } catch (e: Exception) {
            input.close()
            throw e
        }
    }

    fun exists(id: String): Boolean = isValidId(id) && target(id).isFile

    /** Permanently removes the encrypted file. */
    fun delete(id: String): Boolean = isValidId(id) && target(id).delete()

    private fun target(id: String): File {
        require(isValidId(id)) { "Invalid file id" }  // blocks path traversal
        return File(dir, "$id.bin")
    }

    private fun isValidId(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    private fun aad(id: String) = "sentrack/v1/filekey/$id".toByteArray(Charsets.UTF_8)

    private companion object {
        const val MAGIC = 0x53544656 // "STFV"
        const val VERSION = 1
        const val KEY_LABEL = "files"
    }
}
