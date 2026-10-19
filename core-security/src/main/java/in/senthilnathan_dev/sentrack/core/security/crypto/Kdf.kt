package `in`.senthilnathan_dev.sentrack.core.security.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Argon2id cost parameters. They are stored in the vault header so they can be raised
 * later without breaking existing vaults.
 */
data class Argon2Params(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        /** 64 MiB, 3 passes: roughly 0.5 s on a mid-range phone, costly for an offline attacker. */
        val DEFAULT = Argon2Params(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)
    }
}

/** Key derivation: Argon2id for passphrases, HKDF-SHA256 for deriving sub-keys from strong keys. */
object Kdf {
    /** Stretches a low-entropy secret (the passphrase) into a 32-byte key. */
    fun argon2id(secret: ByteArray, salt: ByteArray, params: Argon2Params, outBytes: Int = 32): ByteArray {
        val built = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(params.memoryKiB)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .build()
        val generator = Argon2BytesGenerator().apply { init(built) }
        return ByteArray(outBytes).also { generator.generateBytes(secret, it) }
    }

    /** HKDF-SHA256 (RFC 5869). Only for inputs that already have high entropy. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, outBytes: Int): ByteArray {
        require(outBytes in 1..(255 * 32)) { "Invalid HKDF output length" }
        val macName = "HmacSHA256"
        val extract = Mac.getInstance(macName)
        extract.init(SecretKeySpec(salt?.takeIf { it.isNotEmpty() } ?: ByteArray(32), macName))
        val prk = extract.doFinal(ikm)

        val expand = Mac.getInstance(macName)
        expand.init(SecretKeySpec(prk, macName))
        val out = ByteArray(outBytes)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < outBytes) {
            expand.update(previous)
            expand.update(info)
            expand.update(counter.toByte())
            previous = expand.doFinal()
            val n = minOf(previous.size, outBytes - written)
            previous.copyInto(out, written, 0, n)
            written += n
            counter++
        }
        prk.fill(0)
        return out
    }
}
