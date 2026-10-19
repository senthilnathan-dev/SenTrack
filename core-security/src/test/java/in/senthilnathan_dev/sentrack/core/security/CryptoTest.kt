package `in`.senthilnathan_dev.sentrack.core.security

import `in`.senthilnathan_dev.sentrack.core.security.crypto.AesGcm
import `in`.senthilnathan_dev.sentrack.core.security.crypto.AuthenticationFailedException
import `in`.senthilnathan_dev.sentrack.core.security.crypto.Kdf
import `in`.senthilnathan_dev.sentrack.core.security.crypto.RecoveryCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CryptoTest {
    private val aad = "test".toByteArray()

    @Test
    fun aesGcm_roundTrip() {
        val key = AesGcm.newKey()
        val blob = AesGcm.encrypt(key, "hello".toByteArray(), aad)
        assertEquals("hello", String(AesGcm.decrypt(key, blob, aad)))
    }

    @Test
    fun aesGcm_nonceIsFresh() {
        val key = AesGcm.newKey()
        val a = AesGcm.encrypt(key, "same".toByteArray(), aad)
        val b = AesGcm.encrypt(key, "same".toByteArray(), aad)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun aesGcm_rejectsWrongKeyAadAndTampering() {
        val key = AesGcm.newKey()
        val blob = AesGcm.encrypt(key, "secret".toByteArray(), aad)
        assertFails { AesGcm.decrypt(AesGcm.newKey(), blob, aad) }
        assertFails { AesGcm.decrypt(key, blob, "other".toByteArray()) }
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFails { AesGcm.decrypt(key, tampered, aad) }
        assertFails { AesGcm.decrypt(key, ByteArray(5), aad) }
    }

    /** RFC 5869 test case 1. */
    @Test
    fun hkdf_matchesRfc5869Vector() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = Kdf.hkdfSha256(ikm, salt, info, 42)
        val expected = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
        assertEquals(expected, okm.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun recoveryCode_roundTripAndFormat() {
        val bytes = RecoveryCode.generateBytes()
        val text = RecoveryCode.format(bytes)
        assertEquals(8 * 4 + 7, text.length)
        assertArrayEquals(bytes, RecoveryCode.parse(text))
        assertArrayEquals(bytes, RecoveryCode.parse(text.lowercase().replace("-", " ")))
    }

    @Test
    fun recoveryCode_rejectsMalformed() {
        assertNull(RecoveryCode.parse("too-short"))
        assertNull(RecoveryCode.parse("1111-1111-1111-1111-1111-1111-1111-1111")) // '1' not in alphabet
        assertNotNull(RecoveryCode.parse(RecoveryCode.format(ByteArray(20))))
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block(); fail("Expected AuthenticationFailedException")
        } catch (_: AuthenticationFailedException) {
        }
    }
}
