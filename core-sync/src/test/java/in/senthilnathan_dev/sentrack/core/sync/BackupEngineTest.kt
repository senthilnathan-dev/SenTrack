package `in`.senthilnathan_dev.sentrack.core.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: File
    private lateinit var filesDir: File
    private lateinit var out: File
    private var installedHeader: ByteArray? = null
    private var quiesceCalls = 0
    private val header = "HEADER-WITH-PASSPHRASE".toByteArray()
    private val headerRecoveryOnly = "HEADER-RECOVERY-ONLY".toByteArray()
    private lateinit var fileIds: List<String>

    @Before
    fun setUp() {
        db = File(tmp.root, "app/databases/sentrack.db").also { it.parentFile!!.mkdirs(); it.writeBytes(ByteArray(5000) { i -> (i % 251).toByte() }) }
        filesDir = File(tmp.root, "app/vault_files").also { it.mkdirs() }
        fileIds = (1..3).map { UUID.randomUUID().toString() }
        fileIds.forEachIndexed { i, id -> File(filesDir, "$id.bin").writeBytes(ByteArray(1000 * (i + 1)) { b -> (b + i).toByte() }) }
        out = File(tmp.root, "out")
    }

    private fun engine() = BackupEngine(
        headerProvider = { includePass -> if (includePass) header else headerRecoveryOnly },
        headerInstaller = { installedHeader = it },
        databaseFile = db,
        vaultFilesDir = filesDir,
        runWithDatabaseQuiesced = { quiesceCalls++; it() },
        clock = { 1_700_000_000_000 },
    )

    @Test
    fun snapshot_containsEverything_andIsInspectable() {
        val info = engine().createSnapshot(out, includePassphraseSlot = false, appVersion = "1.2.3")
        assertEquals("sentrack-backup-20231114-221320.stbk", info.file.name)
        assertEquals(1, quiesceCalls)
        val read = engine().inspect(info.file)
        assertEquals(1_700_000_000_000, read.createdAtMillis)
        assertEquals("1.2.3", read.appVersion)
        assertFalse(read.includesPassphraseSlot)
        ZipFile(info.file).use { z ->
            val names = z.entries().asSequence().map { it.name }.toSet()
            assertTrue(names.containsAll(listOf("vault.hdr", "sentrack.db", "manifest.txt") + fileIds.map { "files/$it.bin" }))
        }
    }

    @Test
    fun header_choiceIsHonoured() {
        val withPass = engine().createSnapshot(File(out, "a"), true, "1")
        val without = engine().createSnapshot(File(out, "b"), false, "1")
        assertTrue(engine().inspect(withPass.file).includesPassphraseSlot)
        assertFalse(engine().inspect(without.file).includesPassphraseSlot)
        ZipFile(without.file).use { z ->
            assertArrayEquals(headerRecoveryOnly, z.getInputStream(z.getEntry("vault.hdr")).readBytes())
        }
    }

    @Test
    fun restore_roundTrip_replacesDataAndInstallsHeader() {
        val originalDb = db.readBytes()
        val originalFiles = fileIds.associateWith { File(filesDir, "$it.bin").readBytes() }
        val snap = engine().createSnapshot(out, true, "1").file

        // Change local state, as if on a different phone.
        db.writeBytes(ByteArray(10) { 9 })
        filesDir.listFiles()!!.forEach { it.delete() }
        File(filesDir, "${UUID.randomUUID()}.bin").writeBytes(byteArrayOf(1))

        engine().restore(snap, File(tmp.root, "staging"))

        assertArrayEquals(originalDb, db.readBytes())
        assertEquals(originalFiles.keys, filesDir.listFiles()!!.map { it.name.removeSuffix(".bin") }.toSet())
        originalFiles.forEach { (id, bytes) -> assertArrayEquals(bytes, File(filesDir, "$id.bin").readBytes()) }
        assertArrayEquals(header, installedHeader)
        assertFalse(File(tmp.root, "staging/restore-staging").exists())
    }

    @Test
    fun walFile_isIncludedAndRestored() {
        val wal = File(db.path + "-wal").also { it.writeBytes(ByteArray(300) { 7 }) }
        val snap = engine().createSnapshot(out, true, "1").file
        wal.delete(); File(db.path + "-shm").writeBytes(byteArrayOf(1))
        engine().restore(snap, File(tmp.root, "staging"))
        assertEquals(300, File(db.path + "-wal").length())
        assertFalse(File(db.path + "-shm").exists())
    }

    @Test
    fun tamperedPart_isRejected_andLocalDataUntouched() {
        val snap = engine().createSnapshot(out, true, "1").file
        val broken = File(tmp.root, "broken.stbk")
        rewrite(snap, broken) { name, bytes -> if (name == "sentrack.db") bytes.also { it[10] = (it[10].toInt() xor 1).toByte() } else bytes }
        val dbBefore = db.readBytes()
        try {
            engine().restore(broken, File(tmp.root, "staging")); fail()
        } catch (_: BackupFormatException) {
        }
        assertArrayEquals(dbBefore, db.readBytes())
        assertEquals(3, filesDir.listFiles()!!.size)
        assertEquals(null, installedHeader)
    }

    @Test
    fun missingPart_unknownEntry_andTraversalNames_areRejected() {
        val snap = engine().createSnapshot(out, true, "1").file

        val missing = File(tmp.root, "missing.stbk")
        rewrite(snap, missing, drop = "files/${fileIds[0]}.bin") { _, b -> b }
        expectFormatError(missing)

        val extra = File(tmp.root, "extra.stbk")
        rewrite(snap, extra, add = "evil.txt" to byteArrayOf(1)) { _, b -> b }
        expectFormatError(extra)

        // A manifest that tries to write outside the app folders.
        val traversal = File(tmp.root, "trav.stbk")
        rewrite(snap, traversal, add = "../../evil" to byteArrayOf(1)) { name, b ->
            if (name == "manifest.txt") (String(b) + "part\t../../evil\t1\t00\n").toByteArray() else b
        }
        expectFormatError(traversal)
    }

    @Test
    fun notABackup_isRejected() {
        val junk = File(tmp.root, "junk.stbk").also { it.writeBytes(ByteArray(100)) }
        try {
            engine().inspect(junk); fail()
        } catch (_: java.io.IOException) {
        }
    }

    @Test
    fun failedInstall_rollsBackToOldData() {
        val snap = engine().createSnapshot(out, true, "1").file
        val dbBefore = db.readBytes()
        val idsBefore = filesDir.listFiles()!!.map { it.name }.toSet()
        val failing = BackupEngine(
            headerProvider = { header }, headerInstaller = { throw IllegalStateException("boom") },
            databaseFile = db, vaultFilesDir = filesDir, runWithDatabaseQuiesced = { it() },
        )
        try {
            failing.restore(snap, File(tmp.root, "staging")); fail()
        } catch (_: IllegalStateException) {
        }
        assertArrayEquals(dbBefore, db.readBytes())
        assertEquals(idsBefore, filesDir.listFiles()!!.map { it.name }.toSet())
    }

    private fun expectFormatError(file: File) {
        try {
            engine().restore(file, File(tmp.root, "staging")); fail("expected rejection of ${file.name}")
        } catch (_: BackupFormatException) {
        }
    }

    /** Copies a zip, letting the test alter, drop or add entries. */
    private fun rewrite(
        from: File, to: File, drop: String? = null, add: Pair<String, ByteArray>? = null,
        transform: (String, ByteArray) -> ByteArray,
    ) {
        ZipOutputStream(to.outputStream()).use { zos ->
            ZipFile(from).use { z ->
                z.entries().asSequence().filter { it.name != drop }.forEach { e ->
                    val bytes = transform(e.name, z.getInputStream(e).readBytes())
                    zos.putNextEntry(ZipEntry(e.name)); zos.write(bytes); zos.closeEntry()
                }
            }
            add?.let { zos.putNextEntry(ZipEntry(it.first)); zos.write(it.second); zos.closeEntry() }
        }
    }
}
