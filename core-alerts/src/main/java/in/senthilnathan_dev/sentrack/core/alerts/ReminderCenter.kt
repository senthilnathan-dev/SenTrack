package `in`.senthilnathan_dev.sentrack.core.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The one door to the reminders engine: schedule, cancel, ring, snooze. It never touches the encrypted database,
 * so it works while the vault is locked (alarms ring, snooze works, the phone can reboot).
 */
class ReminderCenter(context: Context) {
    private val context = context.applicationContext
    val store = ReminderStore(this.context)
    private val scheduler = AlarmScheduler(this.context)
    private val notifier = ReminderNotifier(this.context)

    fun ensureChannels() = notifier.ensureChannels()

    var showText: Boolean
        get() = store.showText
        set(value) { store.showText = value }

    fun notificationsAllowed(): Boolean = notifier.areNotificationsAllowed()
    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    /** Schedules (or re-schedules) [def]. A one-off whose time has passed is recorded as completed instead. */
    fun upsert(def: ReminderDef, now: Long = System.currentTimeMillis()) {
        val next = ReminderTiming.nextFire(def, now)
        if (next == null) {
            scheduler.cancel(def.id)
            store.put(def, null, completed = true)
        } else {
            store.put(def, next, completed = false)
            scheduler.schedule(def, next)
        }
    }

    fun remove(id: String) {
        scheduler.cancel(id)
        notifier.cancel(id)
        store.remove(id)
    }

    /** One-off reminders that rang while the app could not update its database. Reconciled after the next unlock. */
    fun completedIds(): List<String> = store.all().filter { it.completed }.map { it.def.id }

    /** Forgets finished one-offs once the database has recorded them. */
    fun forget(ids: Collection<String>) = ids.forEach(store::remove)

    /**
     * Re-creates every alarm, for example after a restart or a time-zone change. A one-off that should have rung
     * while the phone was off is shown straight away instead of being lost.
     */
    fun rescheduleAll(now: Long = System.currentTimeMillis()) {
        ensureChannels()
        for (entry in store.all()) {
            if (entry.completed) continue
            val missed = entry.def.repeat == null && (entry.nextAtMillis ?: 0L) in 1..now
            if (missed) {
                notifier.show(entry.def, store.showText)
                store.put(entry.def, null, completed = true)
            } else {
                upsert(entry.def, now)
            }
        }
    }

    /** Called by the alarm when a reminder is due. */
    internal fun onFired(id: String, snoozed: Boolean, now: Long = System.currentTimeMillis()) {
        val entry = store.get(id) ?: return
        notifier.show(entry.def, store.showText)
        if (snoozed) return
        // Queue the next occurrence (repeating), or mark a one-off as done.
        val next = ReminderTiming.nextFire(entry.def, now)
        if (next == null) {
            store.put(entry.def, null, completed = true)
        } else {
            store.put(entry.def, next, completed = false)
            scheduler.schedule(entry.def, next)
        }
    }

    /** The Snooze button: ring again in [SNOOZE_MS] without touching the regular schedule. */
    internal fun snooze(id: String, now: Long = System.currentTimeMillis()) {
        notifier.cancel(id)
        val entry = store.get(id) ?: return
        scheduler.scheduleSnooze(entry.def, now + SNOOZE_MS)
    }

    /** The Done button. */
    internal fun done(id: String) {
        notifier.cancel(id)
        scheduler.cancelSnooze(id)
    }

    /** Shows a sample notification so the user can check that reminders reach them. */
    fun showTest() {
        notifier.ensureChannels()
        notifier.show(ReminderDef("test", "standalone", null, "Test reminder", AlertStyle.NOTIFICATION, null, null), showText = true)
    }

    companion object {
        const val SNOOZE_MS = 10 * 60 * 1000L
    }
}

/** Receives the alarms and the notification buttons. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlertIntents.EXTRA_ID) ?: return
        val center = ReminderCenter(context)
        when (intent.action) {
            AlertIntents.ACTION_FIRE -> center.onFired(id, snoozed = false)
            AlertIntents.ACTION_FIRE_SNOOZE -> center.onFired(id, snoozed = true)
            AlertIntents.ACTION_SNOOZE -> center.snooze(id)
            AlertIntents.ACTION_DONE -> center.done(id)
        }
    }
}

/** Restores alarms after a restart, an app update, or a clock or time-zone change (Android forgets them). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderCenter(context).rescheduleAll()
    }
}
