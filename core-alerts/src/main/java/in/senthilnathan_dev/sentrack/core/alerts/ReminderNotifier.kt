package `in`.senthilnathan_dev.sentrack.core.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Builds and shows reminder notifications. */
class ReminderNotifier(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    /** Creates the two notification channels (safe to call repeatedly). */
    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set in SenTrack"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders set to ring like an alarm"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            },
        )
    }

    fun areNotificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Shows the reminder. Unless [showText] is on, the notification is generic ("SenTrack reminder") so nothing
     * private appears on the lock screen; the details are visible after unlocking the app.
     */
    @android.annotation.SuppressLint("MissingPermission")   // checked via areNotificationsAllowed()
    fun show(def: ReminderDef, showText: Boolean) {
        if (!areNotificationsAllowed()) return
        val channel = if (def.style == AlertStyle.ALARM) CHANNEL_ALARMS else CHANNEL_REMINDERS
        val title = if (showText && def.title.isNotBlank()) def.title else "SenTrack reminder"
        val text = if (showText && def.title.isNotBlank()) "Reminder" else "Open SenTrack to see the details"

        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("SenTrack reminder")
            .setContentText("Unlock to see the details")
            .build()

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, def.id.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val snooze = AlertIntents.broadcast(context, AlertIntents.ACTION_SNOOZE, def.id, "act-snooze")
        val done = AlertIntents.broadcast(context, AlertIntents.ACTION_DONE, def.id, "act-done")

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (def.style == AlertStyle.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Snooze 10 min", snooze)
            .addAction(0, "Done", done)
            .build()
        NotificationManagerCompat.from(context).notify(def.id.hashCode(), notification)
    }

    fun cancel(id: String) = NotificationManagerCompat.from(context).cancel(id.hashCode())

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_ALARMS = "alarms"
    }
}
