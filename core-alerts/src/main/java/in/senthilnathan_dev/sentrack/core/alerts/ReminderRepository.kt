package `in`.senthilnathan_dev.sentrack.core.alerts

import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderEntity
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import kotlinx.coroutines.flow.Flow

/** Turns a stored reminder into what the alarm machinery needs. */
fun ReminderEntity.toDef() = ReminderDef(
    id = id,
    ownerKind = ownerKind,
    ownerId = ownerId,
    title = title,
    style = runCatching { AlertStyle.valueOf(style) }.getOrDefault(AlertStyle.NOTIFICATION),
    oneOffAtMillis = oneOffAtMillis,
    repeat = ScheduleSpec.decode(repeatEncoded),
)

/**
 * Reminders: the encrypted database holds the full record, and every change is mirrored to the alarm engine so
 * it keeps ringing while the vault is locked.
 */
class ReminderRepository(private val holder: DatabaseHolder, private val center: ReminderCenter) {
    private fun dao() = holder.get().reminderDao()

    fun reminders(): Flow<List<ReminderEntity>> = dao().observeAll()

    /** Saves a reminder and (re)schedules or cancels its alarm to match. */
    suspend fun save(reminder: ReminderEntity) {
        dao().upsert(reminder)
        sync(reminder)
    }

    suspend fun delete(id: String) {
        dao().delete(id)
        center.remove(id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = dao().get(id) ?: return
        save(current.copy(enabled = enabled, completed = if (enabled) false else current.completed))
    }

    /**
     * Run after every unlock: records one-off reminders that rang while the vault was locked, then re-pushes all
     * active reminders to the alarm engine (which also refreshes titles after the notification-text setting changed).
     */
    suspend fun reconcile() {
        val dao = dao()
        val rungWhileLocked = center.completedIds().toSet()
        val all = dao.all()
        for (r in all) {
            val updated = if (r.id in rungWhileLocked && r.repeatEncoded == null) r.copy(completed = true) else r
            if (updated != r) dao.upsert(updated)
            sync(updated)
        }
        center.forget(rungWhileLocked)
    }

    private fun sync(reminder: ReminderEntity) {
        if (reminder.enabled && !reminder.completed) center.upsert(reminder.toDef()) else center.remove(reminder.id)
    }
}
