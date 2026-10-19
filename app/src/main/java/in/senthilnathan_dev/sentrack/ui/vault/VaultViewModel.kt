package `in`.senthilnathan_dev.sentrack.ui.vault

import android.app.Application
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.senthilnathan_dev.sentrack.SenTrackApp
import `in`.senthilnathan_dev.sentrack.security.SecuritySettings
import `in`.senthilnathan_dev.sentrack.core.security.biometric.BiometricFailure
import `in`.senthilnathan_dev.sentrack.core.security.vault.PassphrasePolicy
import `in`.senthilnathan_dev.sentrack.core.security.vault.UnlockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import `in`.senthilnathan_dev.sentrack.core.data.MetaEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which vault screen is showing. */
enum class Stage { SETUP, SHOW_RECOVERY, LOCKED, RECOVER, UNLOCKED, SETTINGS, CHANGE_PASSPHRASE, BACKUP }

data class VaultUiState(
    val stage: Stage,
    val busy: Boolean = false,
    val error: String? = null,
    /** One-off success message (e.g. "Passphrase changed"). */
    val info: String? = null,
    val autoLockSeconds: Int = SecuritySettings.DEFAULT_AUTO_LOCK_SECONDS,
    /** True after restoring a recovery-only backup: only the recovery code can open the vault. */
    val passphraseSlotMissing: Boolean = false,
    val recoveryCode: String? = null,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** Phase 1 proof that the encrypted database opens: how many times it has been opened. */
    val dbOpenCount: Int? = null,
)

/** Drives setup, unlock, recovery and lock screens. Heavy key derivation runs off the main thread. */
class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as SenTrackApp).container
    private val vault = container.vault
    private val biometric = container.biometric

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    private var recoveryFromSettings = false
    private var failures = 0
    private var blockedUntil = 0L

    private val lockListener: (Boolean) -> Unit = { unlocked ->
        if (!unlocked && !container.restoring) {
            recoveryFromSettings = false
            _state.update { stateFor(Stage.LOCKED) }
        }
    }

    init {
        container.session.addListener(lockListener)
    }

    override fun onCleared() {
        container.session.removeListener(lockListener)
    }

    private fun initialState() = when {
        !vault.exists() -> stateFor(Stage.SETUP)
        container.session.isUnlocked -> stateFor(Stage.UNLOCKED)
        else -> stateFor(Stage.LOCKED)
    }

    private fun stateFor(
        stage: Stage,
        error: String? = null,
        code: String? = null,
        info: String? = null,
    ) = VaultUiState(
        stage = stage,
        error = error,
        info = info,
        autoLockSeconds = container.settings.autoLockSeconds,
        passphraseSlotMissing = vault.exists() && !vault.hasPassphraseSlot(),
        recoveryCode = code,
        biometricAvailable = biometric.isAvailable(),
        biometricEnabled = vault.exists() && biometric.isEnabled(),
    )

    fun createVault(passphrase: String, confirm: String) {
        val chars = passphrase.toCharArray()
        when {
            chars.size < PassphrasePolicy.MIN_LENGTH ->
                return fail("Use at least ${PassphrasePolicy.MIN_LENGTH} characters. A phrase of several words works well.")
            passphrase != confirm -> return fail("The two passphrases do not match.")
        }
        runBusy {
            val code = withContext(Dispatchers.Default) { vault.create(chars) }
            chars.fill('\u0000')
            container.lockSuppressed = true
            _state.update { stateFor(Stage.SHOW_RECOVERY, code = code) }
        }
    }

    /** User confirmed they saved the recovery code. */
    fun acknowledgeRecovery() {
        container.lockSuppressed = false
        _state.update { stateFor(if (recoveryFromSettings) Stage.SETTINGS else Stage.UNLOCKED) }
        recoveryFromSettings = false
    }

    // ---- Navigation inside the unlocked area

    fun openSettings() = _state.update { stateFor(Stage.SETTINGS) }

    fun closeSettings() = _state.update { stateFor(Stage.UNLOCKED) }

    fun openChangePassphrase() = _state.update { stateFor(Stage.CHANGE_PASSPHRASE) }

    fun closeChangePassphrase() = _state.update { stateFor(Stage.SETTINGS) }

    fun openBackup() = _state.update { stateFor(Stage.BACKUP) }

    /** Back from the Backup screen: to Settings if a vault exists, else to the first-run screen. */
    fun closeBackup() = _state.update { stateFor(if (vault.exists()) Stage.SETTINGS else Stage.SETUP) }

    /** A backup has just replaced the local vault: show the lock screen with guidance. */
    fun afterRestore() {
        val note = if (vault.hasPassphraseSlot()) "Backup restored. Unlock with your passphrase."
        else "Backup restored. Use your recovery code to set a new passphrase."
        failures = 0
        _state.update { stateFor(Stage.LOCKED, info = note) }
    }

    fun setAutoLock(seconds: Int) {
        container.settings.autoLockSeconds = seconds
        _state.update { it.copy(autoLockSeconds = seconds) }
    }

    /** Verifies [current] and re-wraps the vault key under [new]. Your data is not re-encrypted. */
    fun changePassphrase(current: String, new: String, confirm: String) {
        throttleMessage()?.let { return fail(it) }
        val newChars = new.toCharArray()
        when {
            newChars.size < PassphrasePolicy.MIN_LENGTH ->
                return fail("New passphrase needs at least ${PassphrasePolicy.MIN_LENGTH} characters.")
            new != confirm -> return fail("The two new passphrases do not match.")
            new == current -> return fail("The new passphrase must differ from the current one.")
        }
        val currentChars = current.toCharArray()
        runBusy {
            val ok = withContext(Dispatchers.Default) { vault.changePassphrase(currentChars, newChars) }
            currentChars.fill('\u0000'); newChars.fill('\u0000')
            if (ok) {
                failures = 0
                _state.update { stateFor(Stage.SETTINGS, info = "Passphrase changed.") }
            } else {
                registerFailure()
                fail("Your current passphrase is incorrect.")
            }
        }
    }

    fun unlock(passphrase: String) {
        throttleMessage()?.let { return fail(it) }
        val chars = passphrase.toCharArray()
        runBusy {
            val result = withContext(Dispatchers.Default) { vault.unlockWithPassphrase(chars) }
            chars.fill('\u0000')
            when (result) {
                UnlockResult.SUCCESS -> { failures = 0; _state.update { stateFor(Stage.UNLOCKED) } }
                UnlockResult.WRONG_CREDENTIALS -> { registerFailure(); fail("Incorrect passphrase.") }
                UnlockResult.CORRUPT -> fail("The vault file is damaged. Restore from your Drive backup.")
                UnlockResult.VAULT_MISSING -> _state.update { stateFor(Stage.SETUP) }
            }
        }
    }

    /** Re-checks whether biometric unlock is usable; called whenever the lock screen appears. */
    fun refreshBiometricState() = _state.update {
        it.copy(
            biometricAvailable = biometric.isAvailable(),
            biometricEnabled = vault.exists() && biometric.isEnabled(),
        )
    }

    fun showRecover() = _state.update { stateFor(Stage.RECOVER) }

    fun cancelRecover() = _state.update { stateFor(Stage.LOCKED) }

    fun recover(code: String, newPassphrase: String, confirm: String) {
        throttleMessage()?.let { return fail(it) }
        val chars = newPassphrase.toCharArray()
        when {
            chars.size < PassphrasePolicy.MIN_LENGTH -> return fail("New passphrase needs at least ${PassphrasePolicy.MIN_LENGTH} characters.")
            newPassphrase != confirm -> return fail("The two passphrases do not match.")
        }
        runBusy {
            val result = withContext(Dispatchers.Default) { vault.unlockWithRecoveryCode(code, chars) }
            chars.fill('\u0000')
            when (result) {
                UnlockResult.SUCCESS -> { failures = 0; _state.update { stateFor(Stage.UNLOCKED) } }
                UnlockResult.WRONG_CREDENTIALS -> { registerFailure(); fail("That recovery code is not valid.") }
                else -> fail("The vault could not be opened.")
            }
        }
    }

    /** Opens the encrypted database, bumps a counter and shows it (temporary Phase 1 check). */
    fun checkDatabase() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                val dao = container.database.get().metaDao()
                val next = (dao.get("open_count")?.toIntOrNull() ?: 0) + 1
                dao.put(MetaEntry("open_count", next.toString()))
                next
            }
            _state.update { it.copy(dbOpenCount = count) }
        }
    }

    fun lock() = container.session.lock()

    fun unlockWithBiometrics(activity: FragmentActivity) {
        biometric.unlock(activity) { result ->
            result.onSuccess { _state.update { stateFor(Stage.UNLOCKED) } }
            // Cancelling the prompt is silent; any real failure is shown so it can be diagnosed.
            result.onFailure { e ->
                val cancelled = (e as? BiometricFailure)?.userCancelled == true
                _state.update { stateFor(Stage.LOCKED, error = if (cancelled) null else e.message) }
            }
        }
    }

    fun toggleBiometrics(activity: FragmentActivity) {
        if (biometric.isEnabled()) {
            biometric.disable()
            _state.update { stateFor(Stage.SETTINGS) }
        } else {
            biometric.enable(activity) { result ->
                val e = result.exceptionOrNull()
                val cancelled = (e as? BiometricFailure)?.userCancelled == true
                _state.update { stateFor(Stage.SETTINGS, error = if (e == null || cancelled) null else e.message) }
            }
        }
    }

    /** Issues a new recovery code (the old one stops working) and shows it. */
    fun regenerateRecoveryCode() = runBusy {
        val code = withContext(Dispatchers.Default) { vault.regenerateRecoveryCode() }
        recoveryFromSettings = true
        container.lockSuppressed = true
        _state.update { stateFor(Stage.SHOW_RECOVERY, code = code) }
    }

    private fun fail(message: String) = _state.update { it.copy(busy = false, error = message) }

    private fun runBusy(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** After 5 wrong attempts, wait 2^(n-5) seconds (max 5 min) before the next one. */
    private fun registerFailure() {
        failures++
        if (failures >= 5) {
            val seconds = minOf(300L, 1L shl (failures - 5).coerceAtMost(8))
            blockedUntil = SystemClock.elapsedRealtime() + seconds * 1000
        }
    }

    private fun throttleMessage(): String? {
        val remaining = blockedUntil - SystemClock.elapsedRealtime()
        return if (remaining > 0) "Too many attempts. Try again in ${remaining / 1000 + 1} seconds." else null
    }
}
