package `in`.senthilnathan_dev.sentrack.core.alerts

import android.content.Context
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import org.json.JSONObject

/**
 * The list of upcoming alarms, kept in private app storage OUTSIDE the encrypted database.
 *
 * Why: an alarm has to ring while the vault is locked, and then the database key does not exist in memory. So
 * this small mirror holds only what ringing needs. Privacy trade-off, on purpose: the reminder's title is stored
 * here only when "show reminder text in notifications" is on; otherwise the notification says just
 * "SenTrack reminder" and the details appear after unlocking. The full reminder always lives in the encrypted
 * database.
 */
class ReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)

    /** One scheduled reminder as stored. [completed] means a one-off has already rung. */
    data class Entry(val def: ReminderDef, val nextAtMillis: Long?, val completed: Boolean)

    /** Whether notifications may show the reminder's text on the lock screen and in the shade. Off by default. */
    var showText: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TEXT, value).apply()

    fun put(def: ReminderDef, nextAtMillis: Long?, completed: Boolean) {
        val json = JSONObject()
            .put("id", def.id)
            .put("kind", def.ownerKind)
            .put("title", if (showText) def.title else "")
            .put("style", def.style.name)
            .put("next", nextAtMillis ?: -1L)
            .put("completed", completed)
        def.ownerId?.let { json.put("owner", it) }
        def.oneOffAtMillis?.let { json.put("oneOff", it) }
        def.repeat?.let { json.put("repeat", it.encode()) }
        prefs.edit().putString(PREFIX + def.id, json.toString()).apply()
    }

    fun remove(id: String) = prefs.edit().remove(PREFIX + id).apply()

    fun get(id: String): Entry? = prefs.getString(PREFIX + id, null)?.let(::parse)

    fun all(): List<Entry> = prefs.all.filterKeys { it.startsWith(PREFIX) }.values.mapNotNull { (it as? String)?.let(::parse) }

    private fun parse(text: String): Entry? = runCatching {
        val j = JSONObject(text)
        val next = j.optLong("next", -1L).takeIf { it >= 0 }
        Entry(
            def = ReminderDef(
                id = j.getString("id"),
                ownerKind = j.optString("kind", "standalone"),
                ownerId = if (j.has("owner")) j.getString("owner") else null,
                title = j.optString("title", ""),
                style = AlertStyle.valueOf(j.optString("style", AlertStyle.NOTIFICATION.name)),
                oneOffAtMillis = if (j.has("oneOff")) j.getLong("oneOff") else null,
                repeat = if (j.has("repeat")) ScheduleSpec.decode(j.getString("repeat")) else null,
            ),
            nextAtMillis = next,
            completed = j.optBoolean("completed", false),
        )
    }.getOrNull()

    private companion object {
        const val PREFIX = "def_"
        const val KEY_SHOW_TEXT = "show_text"
    }
}
