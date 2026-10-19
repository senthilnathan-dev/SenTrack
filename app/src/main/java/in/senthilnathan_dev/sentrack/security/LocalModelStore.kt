package `in`.senthilnathan_dev.sentrack.security

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import `in`.senthilnathan_dev.sentrack.core.ai.local.DeviceProfile
import `in`.senthilnathan_dev.sentrack.core.ai.local.LocalModels
import `in`.senthilnathan_dev.sentrack.core.ai.local.ModelOption
import java.io.File

/**
 * Where the on-device model lives: the app's private storage, `files/models`. The model is a public open model, not
 * personal data, so it is not put through the encrypted vault (which would slow every load for no gain). It lives
 * outside the backup, simply because it is large and can be fetched again.
 */
class LocalModelStore(context: Context) {
    private val context = context.applicationContext
    val dir = File(this.context.filesDir, "models")

    /** The downloaded, verified model file, if any (a half-finished `.part` download does not count). */
    fun modelFile(): File? = dir.listFiles { f -> f.isFile && f.name.endsWith(".litertlm") }?.firstOrNull()

    /** Which catalog entry the stored file is, if it is one we know. */
    fun installedOption(): ModelOption? = modelFile()?.let { LocalModels.byFileName(it.name) }

    /** Bytes of an interrupted download of [option], so the screen can offer Resume. */
    fun partialBytes(option: ModelOption): Long = File(dir, option.fileName + ".part").length()

    /** Removes the model and any partial download. */
    fun delete() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** True when the current connection is metered (mobile data or a capped hotspot). */
    fun isMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Reads what the phone says about itself, for the compatibility checks. */
    fun readDeviceProfile(): DeviceProfile {
        dir.mkdirs()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(context.filesDir.path)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return DeviceProfile(
            totalRamMb = info.totalMem / MB,
            availableRamMb = info.availMem / MB,
            freeStorageMb = stat.availableBytes / MB,
            abis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
            lowRamDevice = am.isLowRamDevice,
            thermalStatus = runCatching { pm.currentThermalStatus }.getOrNull(),
        )
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}
