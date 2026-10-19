package `in`.senthilnathan_dev.sentrack.core.security.crypto

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming AES-256-GCM for large files (photos, videos, PDFs) without loading them into memory.
 *
 * The plaintext is cut into 64 KiB chunks. Each chunk is sealed with its own nonce
 * (`8-byte random prefix || 4-byte chunk counter`), so chunks cannot be reordered or duplicated.
 * Each chunk's AAD carries a "final" flag, so truncating or extending the file is detected:
 * a chunk sealed as non-final fails if it is read as the last one, and vice versa.
 *
 * A chunk is "final" exactly when no bytes follow it; an empty input produces one empty final chunk.
 */
object ChunkedAead {
    const val NONCE_PREFIX_BYTES = 8
    private const val PLAIN_CHUNK = 64 * 1024
    private const val TAG_BYTES = 16
    private const val CIPHER_CHUNK = PLAIN_CHUNK + TAG_BYTES

    fun encrypt(key: ByteArray, noncePrefix: ByteArray, input: InputStream, output: OutputStream) {
        require(noncePrefix.size == NONCE_PREFIX_BYTES)
        var current = ByteArray(PLAIN_CHUNK)
        var next = ByteArray(PLAIN_CHUNK)
        var length = readFully(input, current)
        var counter = 0
        while (true) {
            val nextLength = if (length == PLAIN_CHUNK) readFully(input, next) else 0
            val final = nextLength == 0
            output.write(crypt(Cipher.ENCRYPT_MODE, key, noncePrefix, counter++, current, 0, length, final))
            if (final) return
            val swap = current; current = next; next = swap
            length = nextLength
        }
    }

    /** Returns a stream of verified plaintext. Any tampering surfaces as [AuthenticationFailedException] on read. */
    fun decrypt(key: ByteArray, noncePrefix: ByteArray, input: InputStream): InputStream {
        require(noncePrefix.size == NONCE_PREFIX_BYTES)
        return DecryptingStream(key.copyOf(), noncePrefix, input)
    }

    private fun crypt(
        mode: Int, key: ByteArray, prefix: ByteArray, counter: Int,
        data: ByteArray, offset: Int, length: Int, final: Boolean,
    ): ByteArray {
        val nonce = ByteBuffer.allocate(AesGcm.NONCE_BYTES).put(prefix).putInt(counter).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
        cipher.updateAAD(byteArrayOf(if (final) 1 else 0))
        return cipher.doFinal(data, offset, length)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private class DecryptingStream(
        private val key: ByteArray,
        private val prefix: ByteArray,
        private val source: InputStream,
    ) : InputStream() {
        private var block = ByteArray(CIPHER_CHUNK)
        private var spare = ByteArray(CIPHER_CHUNK)
        private var blockLength = readFully(source, block)
        private var plain = ByteArray(0)
        private var position = 0
        private var counter = 0
        private var finished = false

        /** Decrypts the next chunk into [plain]; false once the final chunk has been consumed. */
        private fun advance(): Boolean {
            if (finished) return false
            if (blockLength < TAG_BYTES) throw AuthenticationFailedException()
            val nextLength = if (blockLength == CIPHER_CHUNK) readFully(source, spare) else 0
            val final = nextLength == 0
            plain = try {
                crypt(Cipher.DECRYPT_MODE, key, prefix, counter++, block, 0, blockLength, final)
            } catch (e: GeneralSecurityException) {
                throw AuthenticationFailedException(e)
            }
            position = 0
            if (final) finished = true else {
                val swap = block; block = spare; spare = swap
                blockLength = nextLength
            }
            return true
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (position >= plain.size) {
                if (!advance()) return -1
            }
            val n = minOf(len, plain.size - position)
            plain.copyInto(b, off, position, position + n)
            position += n
            return n
        }

        override fun close() {
            key.fill(0)
            plain.fill(0)
            try {
                source.close()
            } catch (_: IOException) {
            }
        }
    }
}
