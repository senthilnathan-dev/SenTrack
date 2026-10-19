package `in`.senthilnathan_dev.sentrack

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import `in`.senthilnathan_dev.sentrack.ui.theme.SenTrackTheme
import androidx.activity.compose.BackHandler
import `in`.senthilnathan_dev.sentrack.ui.vault.ChangePassphraseScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.LockScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.SecuritySettingsScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.RecoverScreen
import `in`.senthilnathan_dev.sentrack.ui.vault.RecoveryCodeScreen
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
                        Stage.SETUP -> SetupScreen(state, vaultViewModel::createVault)
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
                                onBack = vaultViewModel::closeSettings,
                            )
                        }
                        Stage.CHANGE_PASSPHRASE -> {
                            BackHandler(onBack = vaultViewModel::closeChangePassphrase)
                            ChangePassphraseScreen(state, vaultViewModel::changePassphrase, vaultViewModel::closeChangePassphrase)
                        }
                    }
                }
            }
        }
    }
}
