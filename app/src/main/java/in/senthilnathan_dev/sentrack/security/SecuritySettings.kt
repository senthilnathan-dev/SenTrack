package `in`.senthilnathan_dev.sentrack.security

import android.content.Context

/**
 * Non-secret security preferences. Stored in plain SharedPreferences on purpose: they must be readable
 * while the vault is locked and hold nothing sensitive. (Android backup is disabled for the whole app.)
 */
class SecuritySettings(context: Context) {
    private val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)

    /** How long the app may stay in the background before locking. 0 = lock immediately. */
    var autoLockSeconds: Int
        get() = prefs.getInt(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK_SECONDS).takeIf { it in OPTIONS } ?: DEFAULT_AUTO_LOCK_SECONDS
        set(value) {
            require(value in OPTIONS)
            prefs.edit().putInt(KEY_AUTO_LOCK, value).apply()
        }

    companion object {
        const val DEFAULT_AUTO_LOCK_SECONDS = 30

        /** Allowed values in seconds: immediately, 30 s, 1 min, 5 min. */
        val OPTIONS = listOf(0, 30, 60, 300)

        private const val KEY_AUTO_LOCK = "auto_lock_seconds"

        fun label(seconds: Int) = when (seconds) {
            0 -> "Immediately"
            30 -> "After 30 seconds"
            60 -> "After 1 minute"
            300 -> "After 5 minutes"
            else -> "After $seconds seconds"
        }
    }
}
