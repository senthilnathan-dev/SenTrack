package `in`.senthilnathan_dev.sentrack.core.sync

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The backup file is malformed, incomplete or has been tampered with. */
class BackupFormatException(message: String) : IOException(message)

/** What a snapshot file says about itself, readable without unlocking anything. */
data class SnapshotInfo(
    val file: File,
    val createdAtMillis: Long,
    val appVersion: String,
    /** True if the vault key can be opened with the passphrase; false if only the recovery code works. */
    val includesPassphraseSlot: Boolean,
)

/**
 * Builds and restores backup snapshots (`.stbk` files).
 *
 * A snapshot is a zip of data that is **already encrypted at rest**: the SQLCipher database, the encrypted
 * attachment files and the vault header (wrapped keys). Nothing is decrypted to make a backup, so a backup can run
 * while the app is locked and no key is needed. A manifest lists every part with its SHA-256; restore verifies
 * all of them before replacing anything.
 *
 * Integrity note: the manifest itself is not signed (that would need the vault key). The parts are individually
 * authenticated by their own encryption (SQLCipher page MACs, AES-GCM), so a tampered part fails to open, but an
 * attacker with access to the Drive folder could still swap in an older, valid backup (rollback).
 *
 * @param headerProvider returns the vault header to store; the argument says whether to include the passphrase slot.
 * @param headerInstaller installs a restored header into the vault.
 * @param databaseFile the SQLCipher database file (its `-wal` sibling is included if present).
 * @param vaultFilesDir folder of encrypted attachments.
 * @param runWithDatabaseQuiesced runs the given block while the database is closed and cannot change.
 */
class BackupEngine(
    private val headerProvider: (includePassphraseSlot: Boolean) -> ByteArray,
    private val headerInstaller: (ByteArray) -> Unit,
    private val databaseFile: File,
    private val vaultFilesDir: File,
    private val runWithDatabaseQuiesced: (() -> Unit) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Part(val name: String, val size: Long, val sha256: String)

    /** Writes a new snapshot into [outDir] and returns its info. */
    fun createSnapshot(outDir: File, includePassphraseSlot: Boolean, appVersion: String): SnapshotInfo {
        outDir.mkdirs()
        val created = clock()
        val name = "sentrack-backup-${timestamp(created)}.stbk"
        val target = File(outDir, name)
        val tmp = File(outDir, "$name.tmp")
        val parts = mutableListOf<Part>()
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION) // contents are ciphertext: compression would gain nothing
                parts += addEntry(zip, HEADER_ENTRY, headerProvider(includePassphraseSlot).inputStream())
                runWithDatabaseQuiesced {
                    parts += addEntry(zip, DB_ENTRY, databaseFile.inputStream())
                    val wal = File(databaseFile.path + "-wal")
                    if (wal.isFile) parts += addEntry(zip, WAL_ENTRY, wal.inputStream())
                }
                // Listed after the database copy so every attachment the copied database refers to is included.
                vaultFilesDir.listFiles { f -> f.isFile && f.name.endsWith(".bin") }?.sortedBy { it.name }?.forEach { f ->
                    try {
                        parts += addEntry(zip, "$FILES_PREFIX${f.name}", f.inputStream())
                    } catch (_: java.nio.file.NoSuchFileException) {
                        // deleted while backing up: the database snapshot no longer needs it
                    } catch (_: java.io.FileNotFoundException) {
                    }
                }
                val manifest = buildString {
                    appendLine("$MAGIC $VERSION")
                    appendLine("created\t$created")
                    appendLine("app\t$appVersion")
                    appendLine("passphrase_slot\t${if (includePassphraseSlot) 1 else 0}")
                    parts.forEach { appendLine("part\t${it.name}\t${it.size}\t${it.sha256}") }
                }
                addEntry(zip, MANIFEST_ENTRY, manifest.toByteArray(Charsets.UTF_8).inputStream())
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tmp.delete()
        }
        return SnapshotInfo(target, created, appVersion, includePassphraseSlot)
    }

    /** Reads the manifest only. Cheap; use it to show a backup in a list before restoring. */
    fun inspect(snapshot: File): SnapshotInfo = ZipFile(snapshot).use { zip -> readManifest(zip).first }

    /**
     * Verifies [snapshot] completely, then replaces the local database, attachments and vault header with its
     * contents. The vault must be locked and the database closed. If anything fails, the previous data is put back.
     *
     * @param stagingDir scratch space with room for the snapshot's size; it is deleted afterwards.
     */
    fun restore(snapshot: File, stagingDir: File): SnapshotInfo {
        val staging = File(stagingDir, "restore-staging").also { it.deleteRecursively(); it.mkdirs() }
        try {
            ZipFile(snapshot).use { zip ->
                val (info, parts) = readManifest(zip)
                val stagedFiles = File(staging, "files").also { it.mkdirs() }
                var stagedHeader: ByteArray? = null
                for (part in parts) {
                    val entry = zip.getEntry(part.name) ?: throw BackupFormatException("Missing ${part.name}")
                    val out = when {
                        part.name == HEADER_ENTRY -> File(staging, "header")
                        part.name == DB_ENTRY -> File(staging, "db")
                        part.name == WAL_ENTRY -> File(staging, "db-wal")
                        else -> File(stagedFiles, part.name.removePrefix(FILES_PREFIX))
                    }
                    val actual = zip.getInputStream(entry).use { copyHashing(it, out) }
                    if (actual.second != part.size || actual.first != part.sha256) {
                        throw BackupFormatException("Checksum mismatch in ${part.name}")
                    }
                    if (part.name == HEADER_ENTRY) stagedHeader = out.readBytes()
                }
                if (!File(staging, "db").isFile) throw BackupFormatException("Backup has no database")
                val header = stagedHeader ?: throw BackupFormatException("Backup has no vault header")
                swapIn(staging, stagedFiles, header)
                return info
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun swapIn(staging: File, stagedFiles: File, header: ByteArray) {
        val dbSiblings = listOf("", "-wal", "-shm").map { File(databaseFile.path + it) }
        val oldDir = File(staging, "old").also { it.mkdirs() }
        val moved = mutableListOf<Pair<File, File>>()   // (original, parked copy)
        fun park(f: File) {
            if (!f.exists()) return
            val parked = File(oldDir, "${moved.size}-${f.name}")
            Files.move(f.toPath(), parked.toPath(), StandardCopyOption.REPLACE_EXISTING)
            moved += f to parked
        }
        var oldDataParked = false
        try {
            databaseFile.parentFile?.mkdirs()
            dbSiblings.forEach(::park)
            park(vaultFilesDir)
            oldDataParked = true
            Files.move(File(staging, "db").toPath(), databaseFile.toPath())
            File(staging, "db-wal").takeIf { it.isFile }?.let {
                Files.move(it.toPath(), File(databaseFile.path + "-wal").toPath())
            }
            vaultFilesDir.parentFile?.mkdirs()
            Files.move(stagedFiles.toPath(), vaultFilesDir.toPath())
            headerInstaller(header)
        } catch (e: Exception) {
            // Roll back to what was there before. New files are removed only once the old ones are safely
            // parked; before that point nothing of the new data has been installed yet.
            if (oldDataParked) {
                dbSiblings.forEach { it.delete() }
                vaultFilesDir.deleteRecursively()
            }
            moved.forEach { (original, parked) ->
                runCatching { Files.move(parked.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
            throw e
        }
    }

    private fun readManifest(zip: ZipFile): Pair<SnapshotInfo, List<Part>> {
        val entry = zip.getEntry(MANIFEST_ENTRY) ?: throw BackupFormatException("Not a SenTrack backup")
        val lines = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
        if (lines.firstOrNull() != "$MAGIC $VERSION") throw BackupFormatException("Unsupported backup version")
        var created = 0L
        var app = ""
        var passSlot = false
        val parts = mutableListOf<Part>()
        for (line in lines.drop(1)) {
            val f = line.split('\t')
            when (f[0]) {
                "created" -> created = f.getOrNull(1)?.toLongOrNull() ?: throw BackupFormatException("Bad manifest")
                "app" -> app = f.getOrElse(1) { "" }
                "passphrase_slot" -> passSlot = f.getOrNull(1) == "1"
                "part" -> {
                    if (f.size != 4) throw BackupFormatException("Bad manifest")
                    val name = f[1]
                    if (!isAllowedName(name)) throw BackupFormatException("Unexpected entry in backup")
                    parts += Part(name, f[2].toLongOrNull() ?: throw BackupFormatException("Bad manifest"), f[3])
                }
                else -> throw BackupFormatException("Bad manifest")
            }
        }
        if (parts.map { it.name }.toSet().size != parts.size) throw BackupFormatException("Duplicate entries")
        // No entry may exist that the manifest does not vouch for.
        val listed = parts.map { it.name }.toSet() + MANIFEST_ENTRY
        zip.entries().asSequence().forEach { if (it.name !in listed) throw BackupFormatException("Unlisted entry ${it.name}") }
        return SnapshotInfo(File(zip.name), created, app, passSlot) to parts
    }

    /** Only known names are accepted, which also rules out path traversal ("zip slip"). */
    private fun isAllowedName(name: String) =
        name == HEADER_ENTRY || name == DB_ENTRY || name == WAL_ENTRY || FILE_ENTRY.matches(name)

    private fun addEntry(zip: ZipOutputStream, name: String, source: InputStream): Part {
        zip.putNextEntry(ZipEntry(name))
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        source.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
                zip.write(buf, 0, n)
                size += n
            }
        }
        zip.closeEntry()
        return Part(name, size, digest.digest().toHex())
    }

    /** Copies [input] to [out] and returns (sha256 hex, size). */
    private fun copyHashing(input: InputStream, out: File): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        FileOutputStream(out).use { os: OutputStream ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
                os.write(buf, 0, n)
                size += n
            }
        }
        return digest.digest().toHex() to size
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    private companion object {
        const val MAGIC = "SENTRACK-BACKUP"
        const val VERSION = 1
        const val MANIFEST_ENTRY = "manifest.txt"
        const val HEADER_ENTRY = "vault.hdr"
        const val DB_ENTRY = "sentrack.db"
        const val WAL_ENTRY = "sentrack.db-wal"
        const val FILES_PREFIX = "files/"
        val FILE_ENTRY = Regex("files/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.bin")
    }
}
