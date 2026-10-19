package `in`.senthilnathan_dev.sentrack.core.data.personal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A habit. It is tracked per [period] (day, week, month, quarter or year): either done / not done, or a number such
 * as glasses of water. [target] is how many times (or how much) counts as meeting the period.
 *
 * @param period `DAY`, `WEEK`, `MONTH`, `QUARTER` or `YEAR`.
 * @param numeric true for a quantity habit (then [unit] names it); false for done / not done.
 * @param scheduledDays for daily habits, the weekdays it applies to as ISO numbers (1 = Monday ... 7 = Sunday), comma
 *   separated; empty means every day.
 */
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String?,
    val period: String,
    val numeric: Boolean,
    val unit: String?,
    val target: Double,
    val scheduledDays: String,
    val startEpochDay: Long,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/** What was done on one day: 1 for a done / not done habit, or the amount for a numeric one. One row per habit per day. */
@Entity(tableName = "habit_log", indices = [Index(value = ["habitId", "dateEpochDay"], unique = true)])
data class HabitLogEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val dateEpochDay: Long,
    val amount: Double,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit ORDER BY archived, createdAtMillis")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_log")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Upsert
    suspend fun upsertHabit(habit: HabitEntity)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun deleteHabit(id: String)

    @Query("DELETE FROM habit_log WHERE habitId = :habitId")
    suspend fun deleteLogsFor(habitId: String)

    @Query("SELECT * FROM habit_log WHERE habitId = :habitId AND dateEpochDay = :day")
    suspend fun logOn(habitId: String, day: Long): HabitLogEntity?

    @Upsert
    suspend fun upsertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_log WHERE habitId = :habitId AND dateEpochDay = :day")
    suspend fun deleteLog(habitId: String, day: Long)
}
