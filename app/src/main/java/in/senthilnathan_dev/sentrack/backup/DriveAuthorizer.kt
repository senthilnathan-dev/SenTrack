package `in`.senthilnathan_dev.sentrack.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Gets short-lived Google Drive access tokens for the hidden app folder (scope `drive.appdata`).
 *
 * Uses Google's Authorization API: the first time the user must approve once (a Google screen); afterwards tokens
 * are issued silently, which is what lets scheduled backups run in the background. SenTrack never sees the
 * user's Google password and keeps no long-lived token.
 */
class DriveAuthorizer(context: Context) {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    sealed interface Result {
        data class Ready(val accessToken: String) : Result

        /** The user must approve access; launch [pendingIntent] from an Activity. */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun authorize(): Result = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { res ->
                val token = res.accessToken
                val pending = res.pendingIntent
                cont.resume(
                    when {
                        res.hasResolution() && pending != null -> Result.NeedsConsent(pending)
                        token != null -> Result.Ready(token)
                        else -> Result.Failed("Google returned no access token")
                    },
                )
            }
            .addOnFailureListener { cont.resume(Result.Failed(it.message ?: "Google sign-in failed")) }
    }

    /** Reads the token out of the result of the consent screen. */
    fun tokenFromConsentResult(data: Intent?): String? =
        runCatching { client.getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
