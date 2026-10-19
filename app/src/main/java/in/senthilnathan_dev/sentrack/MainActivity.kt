package `in`.senthilnathan_dev.sentrack

import android.accounts.AccountManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import `in`.senthilnathan_dev.sentrack.backup.BackupActions
import `in`.senthilnathan_dev.sentrack.backup.BackupScreen
import `in`.senthilnathan_dev.sentrack.backup.BackupViewModel
import `in`.senthilnathan_dev.sentrack.ui.theme.SenTrackTheme
import `in`.senthilnathan_dev.sentrack.ui.vault.ChangePassphraseScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.LockScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.RecoverScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.RecoveryCodeScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.SecuritySettingsScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.SetupScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.Stage
import `in`.senthilnathan_dev.sentrack.ui.vault.UnlockedHome
import `in`.senthilnathan_dev.sentrack.ui.vault.VaultViewModel

/**
 * Single-activity entry point. (FragmentActivity, not plain ComponentActivity, because the
 * biometric prompt requires it.)
 */
class MainActivity : FragmentActivity() {
    private val vaultViewModel: VaultViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()

    /** Google's one-time "allow SenTrack to use its Drive app folder" screen. */
    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        backupViewModel.onConsentResult(result.data)
    }

    /** Android's own Google-account chooser; returns the chosen account's address. */
    private val accountPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        backupViewModel.onAccountChosen(name) { pending ->
            driveConsent.launch(IntentSenderRequest.Builder(pending).build())
        }
    }

    private fun chooseGoogleAccount() {
        @Suppress("DEPRECATION")
        val intent = AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)
        accountPicker.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording and the recent-apps thumbnail of app content.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            SenTrackTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    val state by vaultViewModel.state.collectAsState()
                    when (state.stage) {
                        Stage.SETUP -> SetupScreen(state, vaultViewModel::createVault, vaultViewModel::openBackup)
                        Stage.SHOW_RECOVERY -> RecoveryCodeScreen(
                            code = state.recoveryCode.orEmpty(),
                            onDone = vaultViewModel::acknowledgeRecovery,
                        )
                        Stage.LOCKED -> LockScreen(
                            state = state,
                            onUnlock = vaultViewModel::unlock,
                            onBiometric = { vaultViewModel.unlockWithBiometrics(this@MainActivity) },
                            onForgot = vaultViewModel::showRecover,
                        )
                        Stage.RECOVER -> RecoverScreen(state, vaultViewModel::recover, vaultViewModel::cancelRecover)
                        Stage.UNLOCKED -> UnlockedHome(
                            state = state,
                            onLock = vaultViewModel::lock,
                            onOpenSettings = vaultViewModel::openSettings,
                            onCheckDatabase = vaultViewModel::checkDatabase,
                        )
                        Stage.SETTINGS -> {
                            BackHandler(onBack = vaultViewModel::closeSettings)
                            SecuritySettingsScreen(
                                state = state,
                                onSetAutoLock = vaultViewModel::setAutoLock,
                                onChangePassphrase = vaultViewModel::openChangePassphrase,
                                onToggleBiometric = { vaultViewModel.toggleBiometrics(this@MainActivity) },
                                onNewRecoveryCode = vaultViewModel::regenerateRecoveryCode,
                                onOpenBackup = vaultViewModel::openBackup,
                                onBack = vaultViewModel::closeSettings,
                            )
                        }
                        Stage.CHANGE_PASSPHRASE -> {
                            BackHandler(onBack = vaultViewModel::closeChangePassphrase)
                            ChangePassphraseScreen(state, vaultViewModel::changePassphrase, vaultViewModel::closeChangePassphrase)
                        }
                        Stage.BACKUP -> {
                            BackHandler(onBack = vaultViewModel::closeBackup)
                            LaunchedEffect(Unit) { backupViewModel.refresh() }
                            val backupState by backupViewModel.state.collectAsState()
                            BackupScreen(
                                state = backupState,
                                actions = BackupActions(
                                    onChooseAccount = this@MainActivity::chooseGoogleAccount,
                                    onDisconnect = backupViewModel::disconnect,
                                    onBackupNow = backupViewModel::backupNow,
                                    onSetRetention = backupViewModel::setRetention,
                                    onSetIncludePassphrase = backupViewModel::setIncludePassphraseSlot,
                                    onSetScheduleEnabled = backupViewModel::setScheduleEnabled,
                                    onSetCron = backupViewModel::setCron,
                                    onSetNetwork = backupViewModel::setNetwork,
                                    onSetRequireCharging = backupViewModel::setRequireCharging,
                                    onAskRestore = backupViewModel::askRestore,
                                    onCancelRestore = backupViewModel::cancelRestore,
                                    onConfirmRestore = { backupViewModel.confirmRestore(vaultViewModel::afterRestore) },
                                    onBack = vaultViewModel::closeBackup,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
