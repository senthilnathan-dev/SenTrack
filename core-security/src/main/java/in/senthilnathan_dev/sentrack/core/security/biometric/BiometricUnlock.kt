package `in`.senthilnathan_dev.sentrack.core.security.biometric

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import `in`.senthilnathan_dev.sentrack.core.security.vault.VaultManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A biometric step failed. [message] is written for the user; [userCancelled] is true when they simply
 * dismissed the prompt, which the UI should not report as an error.
 */
class BiometricFailure(message: String, val userCancelled: Boolean = false) : Exception(message)

/**
 * Optional fingerprint/face unlock.
 *
 * The data key is wrapped by an Android Keystore AES key that:
 *  - lives in secure hardware (StrongBox if present, else the TEE) and never leaves it,
 *  - can only be used after a fresh BIOMETRIC_STRONG authentication (per-use, bound to a CryptoObject),
 *  - is permanently invalidated if new biometrics are enrolled, so a stranger who adds their
 *    fingerprint to the phone cannot open the vault.
 *
 * The passphrase stays the root credential: biometrics are a convenience and can be removed any time.
 */
class BiometricUnlock(private val context: Context, private val vault: VaultManager) {

    fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(): Boolean = vault.biometricBlob() != null

    /** Wraps the unlocked vault's DEK under a new biometric-bound key. Vault must be unlocked. */
    fun enable(activity: FragmentActivity, onResult: (Result<Unit>) -> Unit) {
        val cipher = try {
            createKey()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, loadKey()) }
        } catch (e: Exception) {
            onResult(Result.failure(BiometricFailure("Could not set up biometric unlock (${describe(e)})."))); return
        }
        authenticate(activity, cipher, "Enable biometric unlock", onFailure = { onResult(Result.failure(it)) }) { c ->
            try {
                val blob = vault.withDek { dek -> c.iv + c.doFinal(dek) }
                vault.saveBiometricBlob(blob)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(BiometricFailure("Could not set up biometric unlock (${describe(e)}).")))
            }
        }
    }

    /** Prompts and, on success, unlocks the vault. */
    fun unlock(activity: FragmentActivity, onResult: (Result<Unit>) -> Unit) {
        val blob = vault.biometricBlob()
            ?: return onResult(Result.failure(BiometricFailure("Biometric unlock is not set up.")))
        val cipher = try {
            val iv = blob.copyOfRange(0, IV_BYTES)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(TAG_BITS, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Biometrics were changed on the phone, so Android destroyed the key: fall back to the passphrase.
            disable()
            return onResult(Result.failure(BiometricFailure("Your biometrics changed, so biometric unlock was switched off. Unlock with your passphrase, then enable it again in Settings.")))
        } catch (e: Exception) {
            // Typically the Keystore key is gone (for example after a restore or a reset): repair by switching off.
            disable()
            return onResult(Result.failure(BiometricFailure("Biometric unlock was reset (${describe(e)}). Unlock with your passphrase, then enable it again in Settings.")))
        }
        authenticate(activity, cipher, "Unlock SenTrack", onFailure = { onResult(Result.failure(it)) }) { c ->
            try {
                val dek = c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
                vault.unlockWithDek(dek)
                dek.fill(0)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(BiometricFailure("Could not open the vault with biometrics (${describe(e)}). Use your passphrase.")))
            }
        }
    }

    /** Removes the biometric slot and its Keystore key. */
    fun disable() {
        vault.saveBiometricBlob(null)
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        onFailure: (Throwable) -> Unit,
        onSuccess: (Cipher) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher
                    if (c == null) onFailure(IllegalStateException("No cipher returned")) else onSuccess(c)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancelling the prompt is a normal choice, not an error worth showing.
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onFailure(BiometricFailure("Biometric check failed: $errString (code $errorCode)", cancelled))
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use passphrase")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun loadKey(): SecretKey =
        keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: throw IllegalStateException("the biometric key is missing")

    private fun describe(e: Exception) = "${e.javaClass.simpleName}: ${e.message}"

    private fun createKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "sentrack_biometric_wrap_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
