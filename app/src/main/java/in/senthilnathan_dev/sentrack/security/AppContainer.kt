package `in`.senthilnathan_dev.sentrack.security

import android.app.Application
import `in`.senthilnathan_dev.sentrack.backup.BackupManager
import `in`.senthilnathan_dev.sentrack.backup.BackupScheduler
import `in`.senthilnathan_dev.sentrack.backup.BackupSettings
import `in`.senthilnathan_dev.sentrack.backup.DriveAuthorizer
import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import `in`.senthilnathan_dev.sentrack.core.security.biometric.BiometricUnlock
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultManager
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultSession
import java.io.File

/**
 * Hand-wired dependencies (manual DI for now; Hilt arrives with the feature modules).
 * The vault header lives in `noBackupFilesDir`, which Android never backs up.
 */
class AppContainer(app: Application) {
    val session = VaultSession()
    val vault = VaultManager(File(app.noBackupFilesDir, "vault.hdr"), session)
    val biometric = BiometricUnlock(app, vault)
    val database = DatabaseHolder(app, session)
    val settings = SecuritySettings(app)

    /** Encrypted attachment store. Lives in `noBackupFilesDir`, like the vault header. */
    val files = FileVault(File(app.noBackupFilesDir, "vault_files"), session)

    val backupSettings = BackupSettings(app)
    val driveAuthorizer = DriveAuthorizer(app, backupSettings)
    val backupManager = BackupManager(app, vault, database, backupSettings, driveAuthorizer)
    val backupScheduler = BackupScheduler(app, backupSettings)

    /** True while a backup is being restored: the UI must not jump to the lock screen when the vault locks. */
    @Volatile
    var restoring: Boolean = false

    /**
     * True while a one-time secret (the recovery code) is on screen. Auto-lock is paused so the
     * user cannot lose the code mid-copy.
     */
    @Volatile
    var lockSuppressed: Boolean = false
}
