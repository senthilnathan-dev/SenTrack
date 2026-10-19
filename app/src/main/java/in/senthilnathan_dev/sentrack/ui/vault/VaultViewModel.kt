package `in`.senthilnathan_dev.sentrack.ui.vault

import android.app.Application
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.senthilnathan_dev.sentrack.SenTrackApp
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
enum class Stage { SETUP, SHOW_RECOVERY, LOCKED, RECOVER, UNLOCKED }

data class VaultUiState(
    val stage: Stage,
    val busy: Boolean = false,
    val error: String? = null,
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

    private var failures = 0
    private var blockedUntil = 0L

    private val lockListener: (Boolean) -> Unit = { unlocked ->
        if (!unlocked) _state.update { stateFor(Stage.LOCKED) }
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

    private fun stateFor(stage: Stage, error: String? = null, code: String? = null) = VaultUiState(
        stage = stage,
        error = error,
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
        _state.update { stateFor(Stage.UNLOCKED) }
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
            // Failures and cancellations simply leave the passphrase field available.
            result.onFailure { _state.update { stateFor(Stage.LOCKED) } }
        }
    }

    fun toggleBiometrics(activity: FragmentActivity) {
        if (biometric.isEnabled()) {
            biometric.disable()
            _state.update { stateFor(Stage.UNLOCKED) }
        } else {
            biometric.enable(activity) { result ->
                _state.update { stateFor(Stage.UNLOCKED, error = result.exceptionOrNull()?.let { "Could not enable biometrics." }) }
            }
        }
    }

    /** Issues a new recovery code (the old one stops working) and shows it. */
    fun regenerateRecoveryCode() = runBusy {
        val code = withContext(Dispatchers.Default) { vault.regenerateRecoveryCode() }
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
