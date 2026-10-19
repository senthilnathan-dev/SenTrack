package `in`.senthilnathan_dev.sentrack.core.security.vault

import `in`.senthilnathan_dev.sentrack.core.security.crypto.Argon2Params
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * On-disk description of how the data key (DEK) is wrapped. It holds no plaintext secrets:
 * every blob is AES-GCM ciphertext and salts are public by design.
 *
 * Slots (each independently unwraps the same DEK):
 *  - passphrase: KEK = Argon2id(passphrase, passSalt)
 *  - recovery:   KEK = HKDF(recovery code bytes, recSalt)
 *  - biometric:  Android Keystore key (optional); blob = iv || ciphertext
 */
internal class VaultHeader(
    val params: Argon2Params,
    val passSalt: ByteArray,
    val passBlob: ByteArray,
    val recSalt: ByteArray,
    val recBlob: ByteArray,
    val biometricBlob: ByteArray?,
) {
    fun copy(
        params: Argon2Params = this.params,
        passSalt: ByteArray = this.passSalt,
        passBlob: ByteArray = this.passBlob,
        recSalt: ByteArray = this.recSalt,
        recBlob: ByteArray = this.recBlob,
        biometricBlob: ByteArray? = this.biometricBlob,
    ) = VaultHeader(params, passSalt, passBlob, recSalt, recBlob, biometricBlob)

    fun toBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            out.writeInt(MAGIC)
            out.writeByte(VERSION)
            out.writeInt(params.memoryKiB)
            out.writeInt(params.iterations)
            out.writeInt(params.parallelism)
            out.writeBlob(passSalt)
            out.writeBlob(passBlob)
            out.writeBlob(recSalt)
            out.writeBlob(recBlob)
            out.writeBoolean(biometricBlob != null)
            if (biometricBlob != null) out.writeBlob(biometricBlob)
        }
        return bos.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x53545654 // "STVT"
        private const val VERSION = 1
        private const val MAX_BLOB = 4096

        fun fromBytes(bytes: ByteArray): VaultHeader {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC) throw IOException("Not a SenTrack vault header")
                val version = input.readUnsignedByte()
                if (version != VERSION) throw IOException("Unsupported vault version $version")
                val params = Argon2Params(input.readInt(), input.readInt(), input.readInt())
                // Sanity bounds so a corrupted header cannot trigger a huge allocation.
                if (params.memoryKiB !in 8..(1024 * 1024) || params.iterations !in 1..64 ||
                    params.parallelism !in 1..16
                ) throw IOException("Invalid KDF parameters")
                val passSalt = input.readBlob()
                val passBlob = input.readBlob()
                val recSalt = input.readBlob()
                val recBlob = input.readBlob()
                val bio = if (input.readBoolean()) input.readBlob() else null
                return VaultHeader(params, passSalt, passBlob, recSalt, recBlob, bio)
            }
        }

        private fun DataOutputStream.writeBlob(b: ByteArray) {
            writeShort(b.size)
            write(b)
        }

        private fun DataInputStream.readBlob(): ByteArray {
            val n = readUnsignedShort()
            if (n > MAX_BLOB) throw IOException("Blob too large")
            return ByteArray(n).also { readFully(it) }
        }
    }
}
