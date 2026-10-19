package `in`.senthilnathan_dev.sentrack.security

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Locks the vault when the screen turns off, and when the app returns from the background
 * after longer than the user's auto-lock setting. (If Android kills the process, the key is gone with it.)
 */
class AutoLocker(
    private val app: Application,
    private val container: AppContainer,
) : DefaultLifecycleObserver {

    private var stoppedAt = 0L

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = lockNow()
        }
        ContextCompat.registerReceiver(
            app, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (stoppedAt == 0L) return
        val timeoutMs = container.settings.autoLockSeconds * 1000L  // read live: changes apply at once
        if (SystemClock.elapsedRealtime() - stoppedAt >= timeoutMs) lockNow()
    }

    private fun lockNow() {
        if (!container.lockSuppressed) container.session.lock()
    }
}
