package `in`.senthilnathan_dev.sentrack.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Shared layout: scrollable centred column with consistent padding. */
@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) { content() }
}

/**
 * Passphrase input. Kept in `remember` (not `rememberSaveable`) on purpose: secrets must not be
 * written into the saved-instance-state bundle.
 */
@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
}

@Composable
fun SetupScreen(state: VaultUiState, onCreate: (String, String) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    ScreenColumn {
        Text("Create your vault", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Everything in SenTrack is encrypted with a key protected by this passphrase. " +
                "It cannot be reset by anyone, so choose something memorable: a phrase of several words.",
        )
        SecretField("Passphrase (12+ characters)", pass, { pass = it }, !state.busy)
        SecretField("Confirm passphrase", confirm, { confirm = it }, !state.busy)
        ErrorText(state.error)
        Button(
            onClick = { onCreate(pass, confirm) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.busy) "Creating…" else "Create vault") }
    }
}

@Composable
fun RecoveryCodeScreen(code: String, onDone: () -> Unit) {
    var saved by rememberSaveable { mutableStateOf(false) }
    ScreenColumn {
        Text("Your recovery code", style = MaterialTheme.typography.headlineMedium)
        Text(
            "If you forget your passphrase, this code is the only way back in. Write it down or print it " +
                "and keep it somewhere safe. It is shown once and is not stored in the app or in Drive.",
        )
        Text(code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text("I have saved my recovery code")
        }
        Button(onClick = onDone, enabled = saved, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
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
    ScreenColumn {
        Text("SenTrack is locked", style = MaterialTheme.typography.headlineMedium)
        SecretField("Passphrase", pass, { pass = it }, !state.busy)
        ErrorText(state.error)
        Button(onClick = { onUnlock(pass); pass = "" }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Unlocking…" else "Unlock")
        }
        if (state.biometricEnabled && state.biometricAvailable) {
            OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) { Text("Use biometrics") }
        }
        TextButton(onClick = onForgot) { Text("Forgot passphrase? Use recovery code") }
    }
}

@Composable
fun RecoverScreen(state: VaultUiState, onRecover: (String, String, String) -> Unit, onCancel: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    ScreenColumn {
        Text("Recover access", style = MaterialTheme.typography.headlineMedium)
        Text("Enter your recovery code and choose a new passphrase.")
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
        ErrorText(state.error)
        Button(onClick = { onRecover(code, pass, confirm) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Recovering…" else "Recover")
        }
        TextButton(onClick = onCancel) { Text("Back") }
    }
}

/** Temporary Phase 1 home: proves unlock works and exposes the security controls. */
@Composable
fun UnlockedHome(
    state: VaultUiState,
    onLock: () -> Unit,
    onToggleBiometric: () -> Unit,
    onNewRecoveryCode: () -> Unit,
    onCheckDatabase: () -> Unit,
) {
    LaunchedEffect(Unit) { onCheckDatabase() }
    ScreenColumn {
        Text("SenTrack", style = MaterialTheme.typography.headlineLarge)
        Text("Vault unlocked. Modules arrive in the next phases.")
        Text("Encrypted database opened ${state.dbOpenCount ?: "…"} time(s).")
        ErrorText(state.error)
        Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("Lock now") }
        if (state.biometricAvailable) {
            OutlinedButton(onClick = onToggleBiometric, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.biometricEnabled) "Disable biometric unlock" else "Enable biometric unlock")
            }
        }
        OutlinedButton(onClick = onNewRecoveryCode, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Generate new recovery code")
        }
        Spacer(Modifier.height(8.dp))
    }
}
