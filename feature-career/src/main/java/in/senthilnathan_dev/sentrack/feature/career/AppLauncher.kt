package `in`.senthilnathan_dev.sentrack.feature.career

import android.content.Context
import android.content.Intent
import android.net.Uri
import `in`.senthilnathan_dev.sentrack.core.data.career.WorkspaceItemEntity

/** An installed app that can be launched. */
internal data class InstalledApp(val label: String, val packageName: String)

/** Lists launchable apps and opens apps and sites. Only apps with a launcher icon are visible (see the manifest queries). */
internal object AppLauncher {
    fun installedApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrNull()

    /** Opens the item. Returns false when the app is not installed or nothing can open the address. */
    fun launch(context: Context, item: WorkspaceItemEntity): Boolean = runCatching {
        val intent: Intent = (if (item.kind == "APP") {
            context.packageManager.getLaunchIntentForPackage(item.target)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(item.target))
        }) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
