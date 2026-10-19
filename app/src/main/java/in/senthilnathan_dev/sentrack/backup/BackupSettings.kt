package `in`.senthilnathan_dev.sentrack.backup

import android.content.Context
import `in`.senthilnathan_dev.sentrack.core.sync.NetworkPolicy

/**
 * Backup preferences and the last-run status. Nothing secret: no tokens or keys are kept here
 * (Google's authorization client hands out short-lived tokens on demand).
 */
class BackupSettings(context: Context) {
    private val prefs = context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)

    /** True once the user has granted Drive access on this phone. */
    var connected: Boolean
        get() = prefs.getBoolean("connected", false)
        set(v) = prefs.edit().putBoolean("connected", v).apply()

    /** Whether scheduled backups run. */
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean("schedule_enabled", false)
        set(v) = prefs.edit().putBoolean("schedule_enabled", v).apply()

    /** Cron expression for scheduled backups. */
    var cron: String
        get() = prefs.getString("cron", DEFAULT_CRON) ?: DEFAULT_CRON
        set(v) = prefs.edit().putString("cron", v).apply()

    var network: NetworkPolicy
        get() = runCatching { NetworkPolicy.valueOf(prefs.getString("network", null) ?: "") }.getOrDefault(NetworkPolicy.WIFI_ONLY)
        set(v) = prefs.edit().putString("network", v.name).apply()

    var requireCharging: Boolean
        get() = prefs.getBoolean("charging", false)
        set(v) = prefs.edit().putBoolean("charging", v).apply()

    /** How many backups to keep in Drive. */
    var retention: Int
        get() = prefs.getInt("retention", DEFAULT_RETENTION).coerceIn(MIN_RETENTION, MAX_RETENTION)
        set(v) = prefs.edit().putInt("retention", v.coerceIn(MIN_RETENTION, MAX_RETENTION)).apply()

    /**
     * Whether backups also carry the passphrase-protected key slot. Off (default): only the recovery code can
     * open a backup, so a stolen backup has nothing to guess. On: passphrase restore works, but the passphrase
     * alone then protects the backup against offline guessing.
     */
    var includePassphraseSlot: Boolean
        get() = prefs.getBoolean("include_passphrase", false)
        set(v) = prefs.edit().putBoolean("include_passphrase", v).apply()

    var lastSuccessMillis: Long
        get() = prefs.getLong("last_success", 0L)
        set(v) = prefs.edit().putLong("last_success", v).apply()

    /** Human-readable result of the most recent attempt; null when it succeeded. */
    var lastError: String?
        get() = prefs.getString("last_error", null)
        set(v) = prefs.edit().putString("last_error", v).apply()

    companion object {
        const val DEFAULT_CRON = "0 2 * * *"
        const val DEFAULT_RETENTION = 3
        const val MIN_RETENTION = 1
        const val MAX_RETENTION = 10
    }
}
