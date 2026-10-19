package `in`.senthilnathan_dev.sentrack.core.security.vault

import `in`.senthilnathan_dev.sentrack.core.security.crypto.Kdf
import java.util.concurrent.CopyOnWriteArrayList

/** Thrown when vault keys are requested while the vault is locked. */
class VaultLockedException : IllegalStateException("Vault is locked")

/**
 * Holds the master data key (DEK) in memory while the vault is unlocked.
 * [lock] overwrites it with zeros. The DEK is never written to disk unwrapped.
 * Feature code obtains purpose-specific sub-keys via [deriveKey] and never sees the DEK.
 */
class VaultSession {
    @Volatile
    private var dek: ByteArray? = null
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    val isUnlocked: Boolean get() = dek != null

    /** Registers a callback invoked with the new unlocked state on every change. */
    fun addListener(listener: (Boolean) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners -= listener
    }

    internal fun unlock(key: ByteArray) {
        dek?.fill(0)
        dek = key.copyOf()
        listeners.forEach { it(true) }
    }

    fun lock() {
        val wasUnlocked = dek != null
        dek?.fill(0)
        dek = null
        if (wasUnlocked) listeners.forEach { it(false) }
    }

    /** Derives a 32-byte sub-key bound to [label] (e.g. "database", "files"). */
    fun deriveKey(label: String): ByteArray {
        val key = dek ?: throw VaultLockedException()
        return Kdf.hkdfSha256(key, null, "sentrack/v1/$label".toByteArray(Charsets.UTF_8), 32)
    }

    /** Runs [block] with a copy of the DEK, wiping the copy afterwards. For key-wrapping only. */
    internal fun <T> withDek(block: (ByteArray) -> T): T {
        val copy = (dek ?: throw VaultLockedException()).copyOf()
        try {
            return block(copy)
        } finally {
            copy.fill(0)
        }
    }
}
