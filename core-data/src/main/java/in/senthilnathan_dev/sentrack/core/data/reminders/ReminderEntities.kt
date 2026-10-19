package `in`.senthilnathan_dev.sentrack.core.data.reminders

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A reminder as the user sees it. The alarm machinery keeps its own small copy outside the encrypted database so
 * it can ring while the vault is locked; this row is the full, authoritative record.
 *
 * @param ownerKind what created it: `standalone`, or a feature such as `finance:bill` or `personal:date`.
 * @param style `NOTIFICATION` or `ALARM`.
 * @param repeatEncoded a stored repeating schedule, or null for a one-off at [oneOffAtMillis].
 * @param completed a one-off that has already rung.
 */
@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String?,
    val ownerKind: String,
    val ownerId: String?,
    val style: String,
    val oneOffAtMillis: Long?,
    val repeatEncoded: String?,
    val enabled: Boolean,
    val completed: Boolean,
    val createdAtMillis: Long,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY completed, enabled DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: String)
}
