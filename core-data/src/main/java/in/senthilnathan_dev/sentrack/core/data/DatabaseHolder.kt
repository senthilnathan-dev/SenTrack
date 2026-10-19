package `in`.senthilnathan_dev.sentrack.core.data

import android.content.Context
import androidx.room.Room
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens [SenTrackDatabase] with a key derived from the unlocked vault, and closes it again
 * the moment the vault locks, so no open handle outlives the key.
 *
 * The key is passed to SQLCipher as a raw 256-bit key (`x'<hex>'`), which skips SQLCipher's own
 * password stretching: the key is already random and strong, so extra PBKDF2 would only slow
 * every open.
 */
class DatabaseHolder(private val context: Context, private val session: VaultSession) {
    private var database: SenTrackDatabase? = null

    init {
        System.loadLibrary("sqlcipher")
        session.addListener { unlocked -> if (!unlocked) close() }
    }

    /** @throws in.senthilnathan_dev.sentrack.core.security.vault.VaultLockedException if locked. */
    @Synchronized
    fun get(): SenTrackDatabase {
        database?.let { return it }
        val key = session.deriveKey("database")
        val passphrase = "x'${key.joinToString("") { "%02x".format(it) }}'".toByteArray(Charsets.US_ASCII)
        key.fill(0)
        return Room.databaseBuilder(context.applicationContext, SenTrackDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
            .also { database = it }
    }

    @Synchronized
    fun close() {
        database?.close()
        database = null
    }

    /**
     * Runs [block] while the database is closed (closing flushes the write-ahead log into the main file), so
     * the file can be copied consistently. Other callers of [get] wait until [block] finishes, and the next
     * [get] reopens the database on demand.
     */
    @Synchronized
    fun runQuiesced(block: () -> Unit) {
        close()
        block()
    }

    companion object {
        /** File name inside the app's `databases` folder. */
        const val DB_NAME = "sentrack.db"
    }
}
