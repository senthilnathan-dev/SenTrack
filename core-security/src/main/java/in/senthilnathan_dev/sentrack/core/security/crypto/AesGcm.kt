package `in`.senthilnathan_dev.sentrack.core.security.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when ciphertext fails authentication: wrong key, wrong context, or tampered data. */
class AuthenticationFailedException(cause: Throwable? = null) :
    GeneralSecurityException("Authentication failed", cause)

/**
 * AES-256-GCM authenticated encryption.
 *
 * Output format: `nonce (12 bytes) || ciphertext || tag (16 bytes)`.
 * A fresh random 96-bit nonce is used per call; this is safe for the message counts a
 * single-user vault produces. The `aad` (additional authenticated data) binds a ciphertext
 * to its purpose, so a blob from one slot cannot be replayed into another.
 */
object AesGcm {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 key required" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    /** @throws AuthenticationFailedException if the key, aad or data do not match. */
    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 key required" }
        if (blob.size < NONCE_BYTES + TAG_BITS / 8) throw AuthenticationFailedException()
        val nonce = blob.copyOfRange(0, NONCE_BYTES)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(blob, NONCE_BYTES, blob.size - NONCE_BYTES)
        } catch (e: GeneralSecurityException) {
            throw AuthenticationFailedException(e)
        }
    }

    /** Returns a new random AES-256 key. */
    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)
}
