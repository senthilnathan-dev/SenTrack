package `in`.senthilnathan_dev.sentrack.core.security.vault

import `in`.senthilnathan_dev.sentrack.core.security.crypto.AesGcm
import `in`.senthilnathan_dev.sentrack.core.security.crypto.Argon2Params
import `in`.senthilnathan_dev.sentrack.core.security.crypto.AuthenticationFailedException
import `in`.senthilnathan_dev.sentrack.core.security.crypto.Kdf
import `in`.senthilnathan_dev.sentrack.core.security.crypto.RecoveryCode
import java.io.File
import java.io.IOException
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/** Outcome of an unlock attempt. */
enum class UnlockResult { SUCCESS, WRONG_CREDENTIALS, VAULT_MISSING, CORRUPT }

/** Passphrase rules enforced at setup and change. */
object PassphrasePolicy {
    const val MIN_LENGTH = 12

    fun isAcceptable(passphrase: CharArray): Boolean = passphrase.size >= MIN_LENGTH
}

/**
 * Creates, unlocks and re-keys the vault.
 *
 * Model: one random 256-bit data key (DEK) encrypts everything. The DEK is stored only
 * wrapped (encrypted) by independent key-encryption keys, one per unlock method, in
 * [headerFile]. Changing the passphrase re-wraps the DEK and never re-encrypts user data.
 *
 * All methods that run Argon2id are CPU- and memory-heavy: call them off the main thread.
 *
 * @param headerFile where the wrapped keys live; use a path excluded from backups
 *   (the app uses `noBackupFilesDir`).
 */
class VaultManager(
    private val headerFile: File,
    val session: VaultSession,
    private val argon2: Argon2Params = Argon2Params.DEFAULT,
) {
    private val random = SecureRandom()

    fun exists(): Boolean = headerFile.isFile

    /**
     * Creates a new vault protected by [passphrase], unlocks it, and returns the one-time
     * recovery code. The caller must show it to the user and must not store it.
     */
    fun create(passphrase: CharArray): String {
        require(PassphrasePolicy.isAcceptable(passphrase)) { "Passphrase too short" }
        check(!exists()) { "Vault already exists" }

        val dek = AesGcm.newKey()
        val passSalt = randomBytes(16)
        val recSalt = randomBytes(16)
        val recoveryBytes = RecoveryCode.generateBytes()

        val passKek = derivePassphraseKek(passphrase, passSalt, argon2)
        val recKek = deriveRecoveryKek(recoveryBytes, recSalt)
        try {
            val header = VaultHeader(
                params = argon2,
                passSalt = passSalt,
                passBlob = AesGcm.encrypt(passKek, dek, AAD_PASS),
                recSalt = recSalt,
                recBlob = AesGcm.encrypt(recKek, dek, AAD_RECOVERY),
                biometricBlob = null,
            )
            write(header)
            session.unlock(dek)
            return RecoveryCode.format(recoveryBytes)
        } finally {
            dek.fill(0); passKek.fill(0); recKek.fill(0); recoveryBytes.fill(0)
        }
    }

    fun unlockWithPassphrase(passphrase: CharArray): UnlockResult {
        val header = readOrNull() ?: return if (exists()) UnlockResult.CORRUPT else UnlockResult.VAULT_MISSING
        // A recovery-only backup was restored: there is no passphrase slot until one is set via recovery.
        if (header.passBlob.isEmpty()) return UnlockResult.WRONG_CREDENTIALS
        val kek = derivePassphraseKek(passphrase, header.passSalt, header.params)
        return try {
            val dek = AesGcm.decrypt(kek, header.passBlob, AAD_PASS)
            session.unlock(dek)
            dek.fill(0)
            UnlockResult.SUCCESS
        } catch (_: AuthenticationFailedException) {
            UnlockResult.WRONG_CREDENTIALS
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Forgotten-passphrase path: unlocks with the recovery code and sets [newPassphrase].
     * The recovery code stays valid.
     */
    fun unlockWithRecoveryCode(code: String, newPassphrase: CharArray): UnlockResult {
        require(PassphrasePolicy.isAcceptable(newPassphrase)) { "Passphrase too short" }
        val header = readOrNull() ?: return if (exists()) UnlockResult.CORRUPT else UnlockResult.VAULT_MISSING
        val recoveryBytes = RecoveryCode.parse(code) ?: return UnlockResult.WRONG_CREDENTIALS
        val recKek = deriveRecoveryKek(recoveryBytes, header.recSalt)
        return try {
            val dek = AesGcm.decrypt(recKek, header.recBlob, AAD_RECOVERY)
            try {
                write(rewrapPassphrase(header, dek, newPassphrase))
                session.unlock(dek)
            } finally {
                dek.fill(0)
            }
            UnlockResult.SUCCESS
        } catch (_: AuthenticationFailedException) {
            UnlockResult.WRONG_CREDENTIALS
        } finally {
            recoveryBytes.fill(0); recKek.fill(0)
        }
    }

    /** Verifies [current] and re-wraps the DEK under [new]. The vault must be unlocked. */
    fun changePassphrase(current: CharArray, new: CharArray): Boolean {
        require(PassphrasePolicy.isAcceptable(new)) { "Passphrase too short" }
        val header = readOrNull() ?: return false
        val kek = derivePassphraseKek(current, header.passSalt, header.params)
        return try {
            val dek = AesGcm.decrypt(kek, header.passBlob, AAD_PASS)
            try {
                write(rewrapPassphrase(header, dek, new))
            } finally {
                dek.fill(0)
            }
            true
        } catch (_: AuthenticationFailedException) {
            false
        } finally {
            kek.fill(0)
        }
    }

    /** Replaces the recovery code (old one stops working). The vault must be unlocked. */
    fun regenerateRecoveryCode(): String {
        val header = readOrNull() ?: throw IOException("Vault missing or corrupt")
        val recSalt = randomBytes(16)
        val recoveryBytes = RecoveryCode.generateBytes()
        val recKek = deriveRecoveryKek(recoveryBytes, recSalt)
        try {
            val blob = session.withDek { AesGcm.encrypt(recKek, it, AAD_RECOVERY) }
            write(header.copy(recSalt = recSalt, recBlob = blob))
            return RecoveryCode.format(recoveryBytes)
        } finally {
            recoveryBytes.fill(0); recKek.fill(0)
        }
    }

    // ---- Backup support

    /** False for a vault restored from a recovery-code-only backup, until a passphrase is set. */
    fun hasPassphraseSlot(): Boolean = readOrNull()?.passBlob?.isNotEmpty() == true

    /**
     * Header bytes that are safe to upload. The biometric slot is always removed (it is bound to this
     * phone's Keystore). With [includePassphrase] false the passphrase slot is removed too, so someone
     * who steals the backup has nothing to brute-force: only the 160-bit recovery code can open it.
     */
    fun exportBackupHeader(includePassphrase: Boolean): ByteArray {
        val header = readOrNull() ?: throw IOException("Vault missing or corrupt")
        val stripped = header.copy(
            biometricBlob = null,
            passSalt = if (includePassphrase) header.passSalt else ByteArray(0),
            passBlob = if (includePassphrase) header.passBlob else ByteArray(0),
        )
        return stripped.toBytes()
    }

    /** Installs a header taken from a backup (restore). The vault must be locked. */
    fun installBackupHeader(bytes: ByteArray) {
        check(!session.isUnlocked) { "Lock the vault before restoring" }
        write(VaultHeader.fromBytes(bytes).copy(biometricBlob = null))
    }

    // ---- Biometric slot: the Keystore work is done by BiometricUnlock; we only persist its blob.

    fun biometricBlob(): ByteArray? = readOrNull()?.biometricBlob

    fun saveBiometricBlob(blob: ByteArray?) {
        val header = readOrNull() ?: throw IOException("Vault missing or corrupt")
        write(header.copy(biometricBlob = blob))
    }

    /** Runs [block] with a copy of the DEK (for wrapping it under a biometric key). */
    fun <T> withDek(block: (ByteArray) -> T): T = session.withDek(block)

    /** Unlocks using a DEK recovered from the biometric slot. */
    fun unlockWithDek(dek: ByteArray) = session.unlock(dek)

    // ---- internals

    private fun rewrapPassphrase(header: VaultHeader, dek: ByteArray, passphrase: CharArray): VaultHeader {
        val salt = randomBytes(16)
        val kek = derivePassphraseKek(passphrase, salt, header.params)
        try {
            return header.copy(passSalt = salt, passBlob = AesGcm.encrypt(kek, dek, AAD_PASS))
        } finally {
            kek.fill(0)
        }
    }

    private fun readOrNull(): VaultHeader? = try {
        if (headerFile.isFile) VaultHeader.fromBytes(headerFile.readBytes()) else null
    } catch (_: IOException) {
        null
    }

    /** Writes via a temp file and atomic rename so a crash never leaves a half-written header. */
    private fun write(header: VaultHeader) {
        headerFile.parentFile?.mkdirs()
        val tmp = File(headerFile.parentFile, headerFile.name + ".tmp")
        tmp.writeBytes(header.toBytes())
        Files.move(tmp.toPath(), headerFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun randomBytes(n: Int) = ByteArray(n).also(random::nextBytes)

    private fun derivePassphraseKek(passphrase: CharArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val bytes = passphrase.toUtf8Bytes()
        try {
            return Kdf.argon2id(bytes, salt, params)
        } finally {
            bytes.fill(0)
        }
    }

    private fun deriveRecoveryKek(recoveryBytes: ByteArray, salt: ByteArray): ByteArray =
        Kdf.hkdfSha256(recoveryBytes, salt, "sentrack/v1/recovery-kek".toByteArray(), 32)

    private companion object {
        val AAD_PASS = "sentrack/v1/slot/passphrase".toByteArray()
        val AAD_RECOVERY = "sentrack/v1/slot/recovery".toByteArray()
    }
}

/** Encodes to UTF-8 without leaving an immutable String copy behind; wipe the result after use. */
internal fun CharArray.toUtf8Bytes(): ByteArray {
    val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
    val out = ByteArray(buffer.remaining())
    buffer.get(out)
    if (buffer.hasArray()) buffer.array().fill(0)
    return out
}
