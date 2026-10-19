package `in`.senthilnathan_dev.sentrack.core.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Intent actions and extras shared by the alarm machinery. */
internal object AlertIntents {
    const val ACTION_FIRE = "in.senthilnathan_dev.sentrack.alerts.FIRE"
    const val ACTION_FIRE_SNOOZE = "in.senthilnathan_dev.sentrack.alerts.FIRE_SNOOZE"
    const val ACTION_SNOOZE = "in.senthilnathan_dev.sentrack.alerts.SNOOZE"
    const val ACTION_DONE = "in.senthilnathan_dev.sentrack.alerts.DONE"
    const val EXTRA_ID = "reminder_id"

    /** A broadcast to [AlarmReceiver]. The distinct [part] keeps the main, snooze and button intents separate. */
    fun broadcast(context: Context, action: String, id: String, part: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("sentrack-reminder://$id/$part"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** Sets and cancels the actual Android alarms. */
class AlarmScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    /** True if the user has allowed exact alarms (Settings, special app access). Otherwise reminders may be late. */
    fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    /**
     * Schedules [def] for [atMillis]. ALARM style uses Android's alarm-clock API (shows the alarm icon, never
     * delayed by battery saving). NOTIFICATION style is exact when allowed, otherwise as close as Android permits.
     */
    fun schedule(def: ReminderDef, atMillis: Long) {
        val pending = AlertIntents.broadcast(context, AlertIntents.ACTION_FIRE, def.id, "main")
        set(def.style, atMillis, pending)
    }

    /** A one-off alarm for the Snooze button; it does not disturb the reminder's regular schedule. */
    fun scheduleSnooze(def: ReminderDef, atMillis: Long) {
        val pending = AlertIntents.broadcast(context, AlertIntents.ACTION_FIRE_SNOOZE, def.id, "snooze")
        set(def.style, atMillis, pending)
    }

    fun cancelSnooze(id: String) {
        alarms.cancel(AlertIntents.broadcast(context, AlertIntents.ACTION_FIRE_SNOOZE, id, "snooze"))
    }

    fun cancel(id: String) {
        alarms.cancel(AlertIntents.broadcast(context, AlertIntents.ACTION_FIRE, id, "main"))
        cancelSnooze(id)
    }

    private fun set(style: AlertStyle, atMillis: Long, pending: PendingIntent) {
        if (style == AlertStyle.ALARM) {
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, open ?: pending), pending)
            return
        }
        try {
            if (canScheduleExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (_: SecurityException) {
            // The exact-alarm permission was withdrawn between the check and the call: fall back to a flexible alarm.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }
}
