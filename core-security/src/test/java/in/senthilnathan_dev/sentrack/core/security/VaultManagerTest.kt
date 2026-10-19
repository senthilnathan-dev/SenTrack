package `in`.senthilnathan_dev.sentrack.core.security

import `in`.senthilnathan_dev.sentrack.core.security.crypto.Argon2Params
import `in`.senthilnathan_dev.sentrack.core.security.vault.UnlockResult
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultLockedException
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultManager
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Cheap Argon2 settings keep tests fast; production uses Argon2Params.DEFAULT.
    private val fast = Argon2Params(memoryKiB = 64, iterations = 1, parallelism = 1)
    private val pass = "correct horse battery".toCharArray()

    private fun manager(file: File = File(tmp.root, "vault.hdr")) = VaultManager(file, VaultSession(), fast)

    @Test
    fun create_unlocksAndWritesHeader() {
        val m = manager()
        assertFalse(m.exists())
        val code = m.create(pass)
        assertTrue(m.exists())
        assertTrue(m.session.isUnlocked)
        assertEquals(39, code.length)
    }

    @Test
    fun unlock_withCorrectPassphrase_sameKeysAsBefore() {
        val file = File(tmp.root, "v.hdr")
        val first = manager(file)
        first.create(pass)
        val dbKey = first.session.deriveKey("database")

        val second = manager(file)
        assertEquals(UnlockResult.SUCCESS, second.unlockWithPassphrase(pass))
        assertArrayEquals(dbKey, second.session.deriveKey("database"))
    }

    @Test
    fun unlock_withWrongPassphrase_failsAndStaysLocked() {
        val file = File(tmp.root, "v.hdr")
        manager(file).create(pass)
        val m = manager(file)
        assertEquals(UnlockResult.WRONG_CREDENTIALS, m.unlockWithPassphrase("wrong passphrase!!".toCharArray()))
        assertFalse(m.session.isUnlocked)
    }

    @Test
    fun subKeys_differByLabel_andLockWipesAccess() {
        val m = manager()
        m.create(pass)
        assertNotEquals(m.session.deriveKey("database").toList(), m.session.deriveKey("files").toList())
        m.session.lock()
        try {
            m.session.deriveKey("database"); fail()
        } catch (_: VaultLockedException) {
        }
    }

    @Test
    fun recoveryCode_resetsPassphrase() {
        val file = File(tmp.root, "v.hdr")
        val code = manager(file).create(pass)
        val m = manager(file)
        val newPass = "a brand new passphrase".toCharArray()
        assertEquals(UnlockResult.SUCCESS, m.unlockWithRecoveryCode(code, newPass))

        val after = manager(file)
        assertEquals(UnlockResult.WRONG_CREDENTIALS, after.unlockWithPassphrase(pass))
        assertEquals(UnlockResult.SUCCESS, after.unlockWithPassphrase(newPass))
    }

    @Test
    fun wrongRecoveryCode_isRejected() {
        val file = File(tmp.root, "v.hdr")
        manager(file).create(pass)
        val m = manager(file)
        val bad = "AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA"
        assertEquals(UnlockResult.WRONG_CREDENTIALS, m.unlockWithRecoveryCode(bad, "another long passphrase".toCharArray()))
        assertFalse(m.session.isUnlocked)
    }

    @Test
    fun changePassphrase_requiresCurrent() {
        val file = File(tmp.root, "v.hdr")
        val m = manager(file)
        m.create(pass)
        val next = "second passphrase ok".toCharArray()
        assertFalse(m.changePassphrase("nope nope nope nope".toCharArray(), next))
        assertTrue(m.changePassphrase(pass, next))
        assertEquals(UnlockResult.SUCCESS, manager(file).unlockWithPassphrase(next))
    }

    @Test
    fun regenerateRecoveryCode_invalidatesOldOne() {
        val file = File(tmp.root, "v.hdr")
        val m = manager(file)
        val oldCode = m.create(pass)
        val newCode = m.regenerateRecoveryCode()
        assertNotEquals(oldCode, newCode)
        val other = "x long enough pass".toCharArray()
        assertEquals(UnlockResult.WRONG_CREDENTIALS, manager(file).unlockWithRecoveryCode(oldCode, other))
        assertEquals(UnlockResult.SUCCESS, manager(file).unlockWithRecoveryCode(newCode, other))
    }

    @Test
    fun corruptedHeader_isReportedNotCrashing() {
        val file = File(tmp.root, "v.hdr")
        manager(file).create(pass)
        file.writeBytes(ByteArray(40) { 7 })
        assertEquals(UnlockResult.CORRUPT, manager(file).unlockWithPassphrase(pass))
    }

    @Test
    fun shortPassphrase_isRejected() {
        try {
            manager().create("short".toCharArray()); fail()
        } catch (_: IllegalArgumentException) {
        }
    }
}
