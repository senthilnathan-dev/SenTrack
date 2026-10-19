package `in`.senthilnathan_dev.sentrack.backup

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.senthilnathan_dev.sentrack.SenTrackApp
import `in`.senthilnathan_dev.sentrack.core.sync.RemoteBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val vaultExists: Boolean = false,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Backups found in Drive, newest first; null until loaded. */
    val remote: List<RemoteBackup>? = null,
    /** Backup the user is being asked to confirm restoring. */
    val confirmRestore: RemoteBackup? = null,
    val lastSuccessMillis: Long = 0L,
    val lastError: String? = null,
    val retention: Int = BackupSettings.DEFAULT_RETENTION,
    val includePassphraseSlot: Boolean = false,
)

/** State and actions for the Backup screen: connect Drive, back up now, list and restore. */
class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as SenTrackApp).container
    private val settings = container.backupSettings
    private val manager = container.backupManager
    private val authorizer = container.driveAuthorizer

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private fun snapshot() = BackupUiState(
        vaultExists = container.vault.exists(),
        connected = settings.connected,
        lastSuccessMillis = settings.lastSuccessMillis,
        lastError = settings.lastError,
        retention = settings.retention,
        includePassphraseSlot = settings.includePassphraseSlot,
    )

    /** Re-reads settings and, when connected, the list of backups in Drive. */
    fun refresh() {
        _state.update { snapshot().copy(remote = it.remote, busy = it.busy, message = it.message, error = it.error) }
        if (settings.connected) loadRemote()
    }

    private fun loadRemote() {
        viewModelScope.launch {
            manager.listRemote()
                .onSuccess { list -> _state.update { it.copy(remote = list) } }
                .onFailure { e -> _state.update { it.copy(error = "Could not list backups: ${e.message}") } }
        }
    }

    /** Starts Google sign-in; if approval is needed, [launchConsent] opens Google's screen. */
    fun connect(launchConsent: (PendingIntent) -> Unit) {
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            when (val r = authorizer.authorize()) {
                is DriveAuthorizer.Result.Ready -> markConnected()
                is DriveAuthorizer.Result.NeedsConsent -> {
                    _state.update { it.copy(busy = false) }
                    launchConsent(r.pendingIntent)
                }
                is DriveAuthorizer.Result.Failed -> _state.update { it.copy(busy = false, error = r.message) }
            }
        }
    }

    /** Called with the result of Google's consent screen. */
    fun onConsentResult(data: Intent?) {
        if (authorizer.tokenFromConsentResult(data) != null) markConnected()
        else _state.update { it.copy(busy = false, error = "Google Drive access was not granted.") }
    }

    private fun markConnected() {
        settings.connected = true
        settings.lastError = null
        _state.update { snapshot().copy(remote = it.remote, message = "Connected to Google Drive.") }
        loadRemote()
    }

    fun backupNow() {
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val outcome = manager.backupNow()
            _state.update { snapshot().copy(remote = it.remote) }
            when (outcome) {
                BackupOutcome.Success -> { _state.update { it.copy(message = "Backup uploaded.") }; loadRemote() }
                BackupOutcome.NeedsSignIn -> _state.update { it.copy(error = "Connect Google Drive again to continue.") }
                is BackupOutcome.Failure -> _state.update { it.copy(error = outcome.message) }
            }
        }
    }

    fun setRetention(count: Int) {
        settings.retention = count
        _state.update { it.copy(retention = settings.retention) }
    }

    fun setIncludePassphraseSlot(include: Boolean) {
        settings.includePassphraseSlot = include
        _state.update { it.copy(includePassphraseSlot = include) }
    }

    fun askRestore(remote: RemoteBackup) = _state.update { it.copy(confirmRestore = remote) }

    fun cancelRestore() = _state.update { it.copy(confirmRestore = null) }

    /** Locks the vault, replaces local data with the chosen backup, then calls [onRestored]. */
    fun confirmRestore(onRestored: () -> Unit) {
        val remote = _state.value.confirmRestore ?: return
        _state.update { it.copy(busy = true, confirmRestore = null, error = null, message = null) }
        viewModelScope.launch {
            container.restoring = true            // keeps the UI on this screen while the vault locks
            container.session.lock()
            val outcome = manager.restore(remote)
            container.restoring = false
            when (outcome) {
                BackupOutcome.Success -> { _state.update { snapshot() }; onRestored() }
                BackupOutcome.NeedsSignIn -> _state.update { snapshot().copy(error = "Connect Google Drive again to continue.") }
                is BackupOutcome.Failure -> _state.update { snapshot().copy(error = outcome.message) }
            }
        }
    }
}
