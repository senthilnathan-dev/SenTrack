package `in`.senthilnathan_dev.sentrack.core.alerts

import `in`.senthilnathan_dev.sentrack.core.sync.CronSchedule
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** How a reminder announces itself. */
enum class AlertStyle(val label: String) {
    /** A normal notification. */
    NOTIFICATION("Notification"),

    /** Clock-alarm style: shown as an alarm (status-bar alarm icon), ignores battery-saving delays. */
    ALARM("Alarm"),
}

/**
 * Everything the alarm machinery needs to ring a reminder, deliberately small: it is kept outside the encrypted
 * database so a reminder can fire while the vault is locked.
 *
 * @param ownerKind what created it, for example `standalone`, `finance:bill`, `personal:date`.
 * @param oneOffAtMillis the single fire time when [repeat] is null.
 * @param repeat the repeating schedule; its time of day is the fire time.
 */
data class ReminderDef(
    val id: String,
    val ownerKind: String,
    val ownerId: String?,
    val title: String,
    val style: AlertStyle,
    val oneOffAtMillis: Long?,
    val repeat: ScheduleSpec?,
)

/** When a reminder fires next. Pure logic, so it is unit-tested without a phone. */
object ReminderTiming {
    /**
     * The first fire time strictly after [afterMillis], or null if there is none (a one-off in the past).
     * Repeating reminders follow their schedule in the phone's current time zone.
     */
    fun nextFire(def: ReminderDef, afterMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val repeat = def.repeat
        if (repeat != null) {
            val from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), zone)
            return CronSchedule.parse(repeat.toCron()).nextAfter(from).toInstant().toEpochMilli()
        }
        return def.oneOffAtMillis?.takeIf { it > afterMillis }
    }
}
