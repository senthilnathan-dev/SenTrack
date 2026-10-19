package `in`.senthilnathan_dev.sentrack.core.security

import `in`.senthilnathan_dev.sentrack.core.security.biometric.BiometricBlob
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricBlobTest {
    private val id = ByteArray(8) { (it + 1).toByte() }
    private val iv = ByteArray(12) { (0x40 + it).toByte() }
    private val ct = ByteArray(48) { (0x80 + it).toByte() }

    @Test
    fun roundTrip() {
        val parsed = BiometricBlob.parse(BiometricBlob(id, iv, ct).toBytes())!!
        assertArrayEquals(id, parsed.keyId)
        assertArrayEquals(iv, parsed.iv)
        assertArrayEquals(ct, parsed.ciphertext)
    }

    @Test
    fun aliasIsUniquePerKeyId() {
        val other = ByteArray(8) { 9 }
        assertEquals("sentrack_bio_0102030405060708", BiometricBlob(id, iv, ct).keyAlias)
        assertNotEquals(BiometricBlob(id, iv, ct).keyAlias, BiometricBlob(other, iv, ct).keyAlias)
    }

    @Test
    fun legacyOrGarbageBlobs_areRejected() {
        assertNull(BiometricBlob.parse(null))
        assertNull(BiometricBlob.parse(ByteArray(0)))
        assertNull(BiometricBlob.parse(iv + ct))                      // old format: iv || ciphertext
        assertNull(BiometricBlob.parse(byteArrayOf(0xB1.toByte(), 2) + ByteArray(5)))   // too short
    }
}
