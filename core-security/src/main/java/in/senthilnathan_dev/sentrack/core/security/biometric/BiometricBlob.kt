package `in`.senthilnathan_dev.sentrack.core.security.biometric

/**
 * Storage format of the biometric slot: `magic (2) || key id (8) || IV (12) || ciphertext`.
 *
 * The key id names the Android Keystore key that wrapped this blob (`sentrack_bio_<hex id>`). A fresh id is used
 * every time biometrics are enabled, so a stale key left over from an earlier enrollment can never be mistaken for
 * the current one (some phones return the old key when a key is deleted and re-created under the same name).
 */
internal class BiometricBlob(val keyId: ByteArray, val iv: ByteArray, val ciphertext: ByteArray) {
    val keyAlias: String get() = aliasFor(keyId)

    fun toBytes(): ByteArray = MAGIC + keyId + iv + ciphertext

    companion object {
        const val KEY_ID_BYTES = 8
        const val IV_BYTES = 12
        private val MAGIC = byteArrayOf(0xB1.toByte(), 0x02)
        private const val HEADER = 2 + KEY_ID_BYTES + IV_BYTES

        /** Alias of the key used before key ids existed; still cleaned up when found. */
        const val LEGACY_ALIAS = "sentrack_biometric_wrap_v1"

        fun aliasFor(keyId: ByteArray) = "sentrack_bio_" + keyId.joinToString("") { "%02x".format(it) }

        /** @return the parsed blob, or null if [bytes] is not in the current format (for example an older version). */
        fun parse(bytes: ByteArray?): BiometricBlob? {
            if (bytes == null || bytes.size <= HEADER) return null
            if (bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1]) return null
            return BiometricBlob(
                keyId = bytes.copyOfRange(2, 2 + KEY_ID_BYTES),
                iv = bytes.copyOfRange(2 + KEY_ID_BYTES, HEADER),
                ciphertext = bytes.copyOfRange(HEADER, bytes.size),
            )
        }
    }
}
