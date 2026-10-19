package `in`.senthilnathan_dev.sentrack.security

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.OpenableColumns
import `in`.senthilnathan_dev.sentrack.core.ai.local.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Where the on-device model file lives: the app's private storage, `files/models`. The file is a public open model,
 * not personal data, so it is not put through the encrypted vault (which would slow every load for no gain).
 * It is excluded from backups simply because it is large and can be fetched again.
 */
class LocalModelStore(context: Context) {
    private val context = context.applicationContext
    private val dir = File(this.context.filesDir, "models")

    /** The imported model, if any. */
    fun modelFile(): File? = dir.listFiles { f -> f.isFile && f.name.endsWith(".litertlm") }?.firstOrNull()

    /**
     * Copies the chosen `.litertlm` file into private storage, replacing any earlier model, and returns it.
     * [onProgress] gets bytes copied so far. @throws IOException for a wrong file type or an unreadable file.
     */
    suspend fun import(uri: Uri, onProgress: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: throw IOException("Could not read the file name")
        if (!name.endsWith(".litertlm", ignoreCase = true)) {
            throw IOException("Choose a .litertlm model file. This one is called $name.")
        }
        dir.mkdirs()
        val temp = File(dir, "$name.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        total += n
                        onProgress(total)
                    }
                }
            } ?: throw IOException("Could not open the file")
            delete()                                   // remove the previous model only once the new one has copied fully
            val target = File(dir, name)
            if (!temp.renameTo(target)) throw IOException("Could not store the model")
            target
        } finally {
            temp.delete()
        }
    }

    fun delete() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Reads what the phone says about itself, for the compatibility checks. */
    fun readDeviceProfile(): DeviceProfile {
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
