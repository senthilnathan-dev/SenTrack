package `in`.senthilnathan_dev.sentrack.core.data.personal

import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Habits and their day-by-day logs. Screens go through this, never straight to Room. */
class HabitRepository(private val holder: DatabaseHolder) {
    private fun dao() = holder.get().habitDao()

    fun habits(): Flow<List<HabitEntity>> = dao().observeHabits()
    fun logs(): Flow<List<HabitLogEntity>> = dao().observeLogs()

    suspend fun save(habit: HabitEntity) = dao().upsertHabit(habit)

    /** Permanently removes the habit and its whole history. */
    suspend fun delete(id: String) {
        val dao = dao()
        dao.deleteLogsFor(id)
        dao.deleteHabit(id)
    }

    /** Records [amount] for [dayEpoch]; an amount of zero or less clears that day. */
    suspend fun setLog(habitId: String, dayEpoch: Long, amount: Double) {
        val dao = dao()
        if (amount <= 0.0) {
            dao.deleteLog(habitId, dayEpoch)
        } else {
            val existing = dao.logOn(habitId, dayEpoch)
            dao.upsertLog(HabitLogEntity(existing?.id ?: UUID.randomUUID().toString(), habitId, dayEpoch, amount))
        }
    }
}
