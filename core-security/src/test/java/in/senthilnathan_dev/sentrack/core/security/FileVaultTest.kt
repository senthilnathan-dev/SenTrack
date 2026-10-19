package `in`.senthilnathan_dev.sentrack.core.security

import `in`.senthilnathan_dev.sentrack.core.security.crypto.Argon2Params
import `in`.senthilnathan_dev.sentrack.core.security.crypto.AuthenticationFailedException
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultLockedException
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultManager
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Random

class FileVaultTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var session: VaultSession
    private lateinit var vault: FileVault
    private lateinit var dir: File
    private val chunk = 64 * 1024

    @Before
    fun setUp() {
        session = VaultSession()
        VaultManager(File(tmp.root, "v.hdr"), session, Argon2Params(64, 1, 1)).create("correct horse battery".toCharArray())
        dir = File(tmp.root, "files")
        vault = FileVault(dir, session)
    }

    private fun bytes(n: Int) = ByteArray(n).also { Random(n.toLong()).nextBytes(it) }

    private fun roundTrip(n: Int) {
        val data = bytes(n)
        val id = vault.store(ByteArrayInputStream(data))
        vault.open(id).use { assertArrayEquals("size $n", data, it.readBytes()) }
    }

    @Test
    fun roundTrip_variousSizes_includingChunkBoundaries() {
        for (n in listOf(0, 1, 100, chunk - 1, chunk, chunk + 1, 2 * chunk, 3 * chunk + 17)) roundTrip(n)
    }

    @Test
    fun storedFile_hasNoPlaintext_andOpaqueName() {
        val marker = "TOP-SECRET-MARKER-1234567890".toByteArray()
        val id = vault.store(ByteArrayInputStream(marker + ByteArray(500)))
        val raw = File(dir, "$id.bin").readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("TOP-SECRET-MARKER"))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun tamperedChunk_isDetected() {
        val id = vault.store(ByteArrayInputStream(bytes(3 * chunk)))
        val f = File(dir, "$id.bin")
        val raw = f.readBytes()
        raw[raw.size / 2] = (raw[raw.size / 2].toInt() xor 1).toByte()
        f.writeBytes(raw)
        assertFails(id)
    }

    @Test
    fun truncatedFile_isDetected() {
        val id = vault.store(ByteArrayInputStream(bytes(3 * chunk)))
        val f = File(dir, "$id.bin")
        val raw = f.readBytes()
        f.writeBytes(raw.copyOf(raw.size - (chunk + 16))) // drop the whole last chunk
        assertFails(id)
    }

    @Test
    fun extendedFile_isDetected() {
        val id = vault.store(ByteArrayInputStream(bytes(2 * chunk)))
        val f = File(dir, "$id.bin")
        f.writeBytes(f.readBytes() + ByteArray(40) { 1 })
        assertFails(id)
    }

    @Test
    fun swappedFiles_areDetected() {
        val a = vault.store(ByteArrayInputStream(bytes(500)))
        val b = vault.store(ByteArrayInputStream(bytes(900)))
        File(dir, "$a.bin").copyTo(File(dir, "$b.bin"), overwrite = true)
        assertFails(b)
    }

    @Test
    fun locked_vaultRefusesAccess() {
        val id = vault.store(ByteArrayInputStream(bytes(10)))
        session.lock()
        try {
            vault.open(id); fail()
        } catch (_: VaultLockedException) {
        }
    }

    @Test
    fun pathTraversalIds_areRejected() {
        try {
            vault.open("../../etc/passwd"); fail()
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(vault.exists("../x"))
    }

    @Test
    fun delete_removesFile() {
        val id = vault.store(ByteArrayInputStream(bytes(10)))
        assertTrue(vault.exists(id))
        assertTrue(vault.delete(id))
        assertFalse(vault.exists(id))
    }

    private fun assertFails(id: String) {
        try {
            vault.open(id).use { it.readBytes() }
            fail("Expected AuthenticationFailedException")
        } catch (_: AuthenticationFailedException) {
        }
    }
}
