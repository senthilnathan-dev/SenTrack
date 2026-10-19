package `in`.senthilnathan_dev.sentrack.core.data.personal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A goal, plan, wishlist item or bucket-list item.
 *
 * @param kind `GOAL`, `PLAN`, `WISHLIST` or `BUCKET`.
 * @param status `TODO`, `DOING` or `DONE`.
 * @param priority 0 = none, 1 = low, 2 = medium, 3 = high.
 * @param costPaise an optional price or budget (wishlist, bucket list), in paise.
 */
@Entity(tableName = "plan_item", indices = [Index("kind")])
data class PlanItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val note: String?,
    val status: String,
    val priority: Int,
    val targetEpochDay: Long?,
    val costPaise: Long?,
    val completedEpochDay: Long?,
    val archived: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** One step of a plan or goal. Progress is the share of steps done. */
@Entity(tableName = "plan_step", indices = [Index("itemId")])
data class PlanStepEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val title: String,
    val done: Boolean,
    val sortOrder: Int,
)

/** Something achieved: a certification, a promotion, a race finished. [category] is a free label such as Career. */
@Entity(tableName = "achievement", indices = [Index("dateEpochDay")])
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val category: String,
    val dateEpochDay: Long,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/**
 * A date that matters: a birthday, an anniversary, an event. Yearly dates repeat every year (and show ages or
 * anniversaries); others happen once.
 *
 * @param kind `BIRTHDAY`, `ANNIVERSARY`, `EVENT`, `MILESTONE` or `OTHER`.
 * @param remindDaysBefore -1 for no reminder, otherwise how many days before the date to remind (0 = on the day).
 * @param remindMinuteOfDay time of the reminder as minutes after midnight.
 */
@Entity(tableName = "important_date", indices = [Index("dateEpochDay")])
data class ImportantDateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val dateEpochDay: Long,
    val yearly: Boolean,
    val note: String?,
    val remindDaysBefore: Int,
    val remindMinuteOfDay: Int,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/** A file attached to a plan item or achievement ([ownerKind] says which). The file is encrypted in the file vault. */
@Entity(tableName = "personal_attachment", indices = [Index("ownerId")])
data class PersonalAttachmentEntity(
    @PrimaryKey val id: String,
    val ownerKind: String,
    val ownerId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val vaultFileId: String,
    val addedAtMillis: Long,
)

@Dao
interface LifeDao {
    // ---- Plans
    @Query("SELECT * FROM plan_item ORDER BY archived, CASE status WHEN 'DONE' THEN 1 ELSE 0 END, priority DESC, updatedAtMillis DESC")
    fun observePlanItems(): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_step ORDER BY itemId, sortOrder")
    fun observePlanSteps(): Flow<List<PlanStepEntity>>

    @Upsert
    suspend fun upsertPlanItem(item: PlanItemEntity)

    @Query("DELETE FROM plan_item WHERE id = :id")
    suspend fun deletePlanItem(id: String)

    @Query("DELETE FROM plan_step WHERE itemId = :itemId")
    suspend fun deleteStepsFor(itemId: String)

    @Upsert
    suspend fun upsertSteps(steps: List<PlanStepEntity>)

    // ---- Achievements
    @Query("SELECT * FROM achievement ORDER BY archived, dateEpochDay DESC")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Upsert
    suspend fun upsertAchievement(achievement: AchievementEntity)

    @Query("DELETE FROM achievement WHERE id = :id")
    suspend fun deleteAchievement(id: String)

    // ---- Important dates
    @Query("SELECT * FROM important_date ORDER BY archived, dateEpochDay")
    fun observeImportantDates(): Flow<List<ImportantDateEntity>>

    @Upsert
    suspend fun upsertImportantDate(date: ImportantDateEntity)

    @Query("DELETE FROM important_date WHERE id = :id")
    suspend fun deleteImportantDate(id: String)

    // ---- Attachments
    @Query("SELECT * FROM personal_attachment ORDER BY addedAtMillis")
    fun observePersonalAttachments(): Flow<List<PersonalAttachmentEntity>>

    @Upsert
    suspend fun upsertPersonalAttachment(attachment: PersonalAttachmentEntity)

    @Query("DELETE FROM personal_attachment WHERE id = :id")
    suspend fun deletePersonalAttachment(id: String)

    @Query("SELECT * FROM personal_attachment WHERE ownerId = :ownerId")
    suspend fun attachmentsOf(ownerId: String): List<PersonalAttachmentEntity>

    @Query("DELETE FROM personal_attachment WHERE ownerId = :ownerId")
    suspend fun deleteAttachmentsOf(ownerId: String)
}
