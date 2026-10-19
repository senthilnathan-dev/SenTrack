package `in`.senthilnathan_dev.sentrack.backup

import android.content.Context
import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultManager
import `in`.senthilnathan_dev.sentrack.core.sync.BackupEngine
import `in`.senthilnathan_dev.sentrack.core.sync.BackupFormatException
import `in`.senthilnathan_dev.sentrack.core.sync.BackupStorage
import `in`.senthilnathan_dev.sentrack.core.sync.DriveAuthException
import `in`.senthilnathan_dev.sentrack.core.sync.DriveBackupStorage
import `in`.senthilnathan_dev.sentrack.core.sync.RemoteBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Result of a backup or restore attempt, phrased for the UI. */
sealed interface BackupOutcome {
    data object Success : BackupOutcome

    /** Google access is missing or was revoked: the user must connect again. */
    data object NeedsSignIn : BackupOutcome
    data class Failure(val message: String) : BackupOutcome
}

/**
 * Ties the pieces together: snapshot ([BackupEngine]) -> Drive ([BackupStorage]) -> retention clean-up.
 * Used by the "Back up now" button and, later, by the scheduled worker. Only one run happens at a time.
 */
class BackupManager(
    private val context: Context,
    private val vault: VaultManager,
    private val database: DatabaseHolder,
    private val settings: BackupSettings,
    private val authorizer: DriveAuthorizer,
) {
    private val mutex = Mutex()
    private val workDir = File(context.noBackupFilesDir, "backup_work")

    private val engine = BackupEngine(
        headerProvider = vault::exportBackupHeader,
        headerInstaller = vault::installBackupHeader,
        databaseFile = context.getDatabasePath(DatabaseHolder.DB_NAME),
        vaultFilesDir = File(context.noBackupFilesDir, "vault_files"),
        runWithDatabaseQuiesced = database::runQuiesced,
    )

    /** A Drive client whose token comes from Google's authorizer on demand (call on a background thread). */
    private fun storage(): BackupStorage = DriveBackupStorage(tokenProvider = {
        when (val r = runBlocking { authorizer.authorize() }) {
            is DriveAuthorizer.Result.Ready -> r.accessToken
            is DriveAuthorizer.Result.NeedsConsent -> throw DriveAuthException("Approval needed")
            is DriveAuthorizer.Result.Failed -> throw DriveAuthException(r.message)
        }
    })

    /** Creates a snapshot, uploads it, and trims old backups to the configured count. */
    suspend fun backupNow(): BackupOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!vault.exists()) return@withContext BackupOutcome.Failure("Nothing to back up yet")
            workDir.mkdirs()
            var snapshot: File? = null
            try {
                val info = engine.createSnapshot(workDir, settings.includePassphraseSlot, appVersion())
                snapshot = info.file
                val storage = storage()
                storage.upload(info.file, info.file.name)
                // Retention: keep the newest N of OUR backups; list() is newest first.
                storage.list().filter { it.name.startsWith("sentrack-backup-") }
                    .drop(settings.retention).forEach { storage.delete(it.id) }
                settings.lastSuccessMillis = System.currentTimeMillis()
                settings.lastError = null
                BackupOutcome.Success
            } catch (e: DriveAuthException) {
                settings.connected = false
                settings.lastError = "Google Drive access needed. Open Backup and connect again."
                BackupOutcome.NeedsSignIn
            } catch (e: IOException) {
                settings.lastError = "Backup failed: ${e.message ?: "network or storage problem"}"
                BackupOutcome.Failure(settings.lastError!!)
            } finally {
                snapshot?.delete()
            }
        }
    }

    suspend fun listRemote(): Result<List<RemoteBackup>> = withContext(Dispatchers.IO) {
        try {
            Result.success(storage().list().filter { it.name.startsWith("sentrack-backup-") })
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Downloads [remote] and replaces the local vault, database and attachments with it. The caller must have
     * locked the vault first. Local data is only touched after the whole backup has been verified.
     */
    suspend fun restore(remote: RemoteBackup): BackupOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            workDir.mkdirs()
            val download = File(workDir, "restore.stbk")
            try {
                storage().download(remote.id, download)
                database.close()
                engine.restore(download, workDir)
                BackupOutcome.Success
            } catch (e: DriveAuthException) {
                settings.connected = false
                BackupOutcome.NeedsSignIn
            } catch (e: BackupFormatException) {
                BackupOutcome.Failure("That backup is damaged or not a SenTrack backup.")
            } catch (e: IOException) {
                BackupOutcome.Failure("Restore failed: ${e.message ?: "network or storage problem"}")
            } finally {
                download.delete()
            }
        }
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
}
