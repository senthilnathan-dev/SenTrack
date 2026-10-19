package `in`.senthilnathan_dev.sentrack.security

import android.app.Application
import `in`.senthilnathan_dev.sentrack.backup.BackupManager
import `in`.senthilnathan_dev.sentrack.backup.BackupScheduler
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderCenter
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import `in`.senthilnathan_dev.sentrack.backup.BackupSettings
import `in`.senthilnathan_dev.sentrack.backup.DriveAuthorizer
import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitRepository
import `in`.senthilnathan_dev.sentrack.core.data.personal.LifeRepository
import `in`.senthilnathan_dev.sentrack.core.data.career.CareerRepository
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkRepository
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteRepository
import `in`.senthilnathan_dev.sentrack.core.security.biometric.BiometricUnlock
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultManager
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultSession
import android.os.SystemClock
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

    init {
        // Short-lived readable copies (PDF previews) must never outlive the unlocked session or the process.
        val viewing = File(app.cacheDir, "viewing")
        viewing.deleteRecursively()
        session.addListener { unlocked -> if (!unlocked) viewing.deleteRecursively() }
    }

    /** Finance data access (accounts, transactions, budgets, goals, tax filings). */
    val finance = FinanceRepository(database)

    /** Reminders: alarms that ring even while locked (engine) and their encrypted records (repository). */
    val reminderCenter = ReminderCenter(app)
    val reminders = ReminderRepository(database, reminderCenter)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // After every unlock, record reminders that rang while locked and refresh the alarm list.
        session.addListener { unlocked ->
            if (unlocked) appScope.launch { runCatching { reminders.reconcile() } }
        }
    }

    /** Notes, folders, tags and attachment records. */
    val notes = NoteRepository(database)

    /** Habits and their day-by-day logs. */
    val habits = HabitRepository(database)
    val life = LifeRepository(database)
    val work = WorkRepository(database)
    val career = CareerRepository(database)
    val ai = AiSettingsStore(database)

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

    @Volatile
    private var externalFlowAt = 0L

    /**
     * Call right before opening a system screen the app does not control (file picker, Google sign-in). Returning
     * from it within [EXTERNAL_FLOW_GRACE_MS] will not trigger the background auto-lock.
     */
    fun beginExternalFlow() {
        externalFlowAt = SystemClock.elapsedRealtime()
    }

    /** True once if the app is returning from a deliberate external screen started recently. */
    fun consumeExternalFlow(): Boolean {
        val started = externalFlowAt
        externalFlowAt = 0L
        return started != 0L && SystemClock.elapsedRealtime() - started < EXTERNAL_FLOW_GRACE_MS
    }

    private companion object {
        const val EXTERNAL_FLOW_GRACE_MS = 5 * 60 * 1000L
    }
}
