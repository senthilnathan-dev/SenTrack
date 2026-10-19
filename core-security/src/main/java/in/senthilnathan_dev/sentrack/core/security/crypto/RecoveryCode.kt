package `in`.senthilnathan_dev.sentrack.core.security.crypto

import java.security.SecureRandom

/**
 * Human-friendly recovery code: 160 random bits written as 32 Base32 characters in
 * 8 groups of 4 (e.g. `ABCD-EFGH-...`). Shown once at setup; the user must keep it.
 * It is never stored by the app or uploaded to Drive.
 */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BYTES = 20
    private val random = SecureRandom()

    /** Generates a new code and returns its raw key material. */
    fun generateBytes(): ByteArray = ByteArray(BYTES).also(random::nextBytes)

    /** Formats raw bytes as the displayed code. */
    fun format(bytes: ByteArray): String {
        require(bytes.size == BYTES)
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return sb.toString().chunked(4).joinToString("-")
    }

    /** Parses user input (case, spaces and dashes are ignored). Returns null if malformed. */
    fun parse(input: String): ByteArray? {
        val clean = input.uppercase().filter { it != '-' && !it.isWhitespace() }
        if (clean.length != BYTES * 8 / 5) return null
        val out = ByteArray(BYTES)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out[index++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return out
    }
}
