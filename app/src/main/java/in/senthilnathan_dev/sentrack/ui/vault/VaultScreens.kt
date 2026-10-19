package `in`.senthilnathan_dev.sentrack.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.security.SecuritySettings
import `in`.senthilnathan_dev.sentrack.ui.components.AppScreen
import `in`.senthilnathan_dev.sentrack.ui.components.CenteredScreen
import `in`.senthilnathan_dev.sentrack.ui.components.ChoiceRow
import `in`.senthilnathan_dev.sentrack.ui.components.Message
import `in`.senthilnathan_dev.sentrack.ui.components.MessageKind
import `in`.senthilnathan_dev.sentrack.ui.components.NavRow
import `in`.senthilnathan_dev.sentrack.ui.components.PrimaryButton
import `in`.senthilnathan_dev.sentrack.ui.components.SecondaryButton
import `in`.senthilnathan_dev.sentrack.ui.components.SecretField
import `in`.senthilnathan_dev.sentrack.ui.components.Section
import `in`.senthilnathan_dev.sentrack.ui.components.SwitchRow

// ---- Sign-in style screens (centred, no top bar)

@Composable
fun SetupScreen(state: VaultUiState, onCreate: (String, String) -> Unit, onRestore: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    CenteredScreen {
        Text("Create your vault", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Everything in SenTrack is encrypted with a key protected by this passphrase. " +
                "Nobody can reset it for you, so choose a phrase of several words you will remember.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SecretField("Passphrase (12+ characters)", pass, { pass = it }, !state.busy)
        SecretField("Confirm passphrase", confirm, { confirm = it }, !state.busy)
        Message(state.error, MessageKind.ERROR)
        PrimaryButton(if (state.busy) "Creating…" else "Create vault", onClick = { onCreate(pass, confirm) }, busy = state.busy)
        TextButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Already use SenTrack? Restore from Google Drive")
        }
    }
}

@Composable
fun RecoveryCodeScreen(code: String, onDone: () -> Unit) {
    var saved by rememberSaveable { mutableStateOf(false) }
    CenteredScreen {
        Text("Your recovery code", style = MaterialTheme.typography.headlineMedium)
        Message(
            "If you forget your passphrase, this code is the only way back in. Write it down or print it and keep it " +
                "somewhere safe. It is shown once and is not stored in the app or in Drive.",
            MessageKind.INFO,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                code,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text("I have saved my recovery code")
        }
        PrimaryButton("Continue", onClick = onDone, enabled = saved)
    }
}

@Composable
fun LockScreen(
    state: VaultUiState,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    CenteredScreen {
        Text("SenTrack is locked", style = MaterialTheme.typography.headlineMedium)
        Message(state.info, MessageKind.SUCCESS)
        if (state.passphraseSlotMissing) {
            // Restored from a recovery-only backup: there is no passphrase yet, so only the recovery path works.
            Message(state.error, MessageKind.ERROR)
            PrimaryButton("Enter recovery code", onClick = onForgot)
        } else {
            SecretField("Passphrase", pass, { pass = it }, !state.busy)
            Message(state.error, MessageKind.ERROR)
            PrimaryButton(if (state.busy) "Unlocking…" else "Unlock", onClick = { onUnlock(pass); pass = "" }, busy = state.busy)
            if (state.biometricEnabled && state.biometricAvailable) {
                SecondaryButton("Use biometrics", onClick = onBiometric, enabled = !state.busy)
            }
            TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) { Text("Forgot passphrase? Use recovery code") }
        }
    }
}

@Composable
fun RecoverScreen(state: VaultUiState, onRecover: (String, String, String) -> Unit, onCancel: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    CenteredScreen {
        Text("Recover access", style = MaterialTheme.typography.headlineMedium)
        Text("Enter your recovery code and choose a new passphrase.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Recovery code") },
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField("New passphrase (12+ characters)", pass, { pass = it }, !state.busy)
        SecretField("Confirm new passphrase", confirm, { confirm = it }, !state.busy)
        Message(state.error, MessageKind.ERROR)
        PrimaryButton(if (state.busy) "Recovering…" else "Recover", onClick = { onRecover(code, pass, confirm) }, busy = state.busy)
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

// ---- Screens inside the unlocked app (top bar + sections)

/** Temporary Phase 1 home: proves unlock works. Replaced by the real dashboard in Phase 3+. */
@Composable
fun UnlockedHome(
    state: VaultUiState,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckDatabase: () -> Unit,
) {
    LaunchedEffect(Unit) { onCheckDatabase() }
    AppScreen("SenTrack") {
        Section("Status") {
            Text("Your vault is unlocked. Finance, Personal and Work modules arrive in the next phases.")
            Text(
                "Encrypted database opened ${state.dbOpenCount ?: "…"} time(s).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Section("Quick actions") {
            PrimaryButton("Lock now", onClick = onLock)
            SecondaryButton("Settings", onClick = onOpenSettings)
        }
    }
}

/** Settings: auto-lock timing, biometrics, passphrase, recovery code and the link to backups. */
@Composable
fun SecuritySettingsScreen(
    state: VaultUiState,
    onSetAutoLock: (Int) -> Unit,
    onChangePassphrase: () -> Unit,
    onToggleBiometric: () -> Unit,
    onNewRecoveryCode: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
) {
    AppScreen("Settings", onBack = onBack) {
        Message(state.info, MessageKind.SUCCESS)
        Message(state.error, MessageKind.ERROR)

        Section("Auto-lock", "How long SenTrack may stay in the background before it locks. It also locks whenever the screen turns off.") {
            SecuritySettings.OPTIONS.forEach { seconds ->
                ChoiceRow(SecuritySettings.label(seconds), selected = state.autoLockSeconds == seconds) { onSetAutoLock(seconds) }
            }
        }

        Section("Unlocking") {
            if (state.biometricAvailable) {
                SwitchRow(
                    title = "Biometric unlock",
                    subtitle = "Use your fingerprint or face instead of typing the passphrase.",
                    checked = state.biometricEnabled,
                    enabled = !state.busy,
                    onChange = { onToggleBiometric() },
                )
            }
            NavRow("Change passphrase", "Your data is not re-encrypted, so this is quick.", onChangePassphrase)
        }

        Section(
            "Recovery code",
            "The only way back in if you forget your passphrase. Creating a new one makes the old one stop working.",
        ) {
            SecondaryButton("Generate new recovery code", onClick = onNewRecoveryCode, enabled = !state.busy)
        }

        Section("Backup") {
            NavRow("Backup to Google Drive", "Account, automatic schedule and restore", onOpenBackup)
        }
    }
}

@Composable
fun ChangePassphraseScreen(state: VaultUiState, onChange: (String, String, String) -> Unit, onBack: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AppScreen("Change passphrase", onBack = onBack) {
        Section("New passphrase", "Your recovery code stays valid. Use at least 12 characters.") {
            SecretField("Current passphrase", current, { current = it }, !state.busy)
            SecretField("New passphrase", new, { new = it }, !state.busy)
            SecretField("Confirm new passphrase", confirm, { confirm = it }, !state.busy)
        }
        Message(state.error, MessageKind.ERROR)
        PrimaryButton(
            if (state.busy) "Changing…" else "Change passphrase",
            onClick = { onChange(current, new, confirm) },
            busy = state.busy,
        )
    }
}
