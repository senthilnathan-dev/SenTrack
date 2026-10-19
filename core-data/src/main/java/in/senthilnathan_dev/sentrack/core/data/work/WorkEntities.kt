package `in`.senthilnathan_dev.sentrack.core.data.work

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * An employer. People change jobs, so everything work-related hangs off a company. Leaving a company
 * ("offboarding") sets [endEpochDay] and archives it; the history stays. Deleting is separate and needs strong approval.
 *
 * @param leaveYearStartMonth month the leave year starts (1 = January, 4 = April).
 */
@Entity(tableName = "company")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String?,
    val employeeId: String?,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val leaveYearStartMonth: Int,
    val note: String?,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/** A kind of leave at one company (Casual, Sick, Earned...) with its yearly quota in days. */
@Entity(tableName = "leave_type", indices = [Index("companyId")])
data class LeaveTypeEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val yearlyQuotaDays: Double,
    val sortOrder: Int,
)

/** Leave taken from [startEpochDay] to [endEpochDay] inclusive. A half day has equal dates and [halfDay] = true. */
@Entity(tableName = "leave_entry", indices = [Index("companyId"), Index("startEpochDay")])
data class LeaveEntryEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val leaveTypeId: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val halfDay: Boolean,
    val reason: String?,
    val createdAtMillis: Long,
)

/**
 * One working day's punch. Location is captured only at the moment you tap In or Out, never in the background,
 * and is optional (null when permission is off or no fix was available).
 */
@Entity(tableName = "attendance", indices = [Index("companyId"), Index("dayEpoch")])
data class AttendanceEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val dayEpoch: Long,
    val inMillis: Long,
    val outMillis: Long?,
    val inLat: Double?,
    val inLon: Double?,
    val outLat: Double?,
    val outLon: Double?,
    val note: String?,
)

/** A work task. [status] is `OPEN` or `DONE`; [priority] 0 = none to 3 = high. */
@Entity(tableName = "work_task", indices = [Index("companyId"), Index("dueEpochDay")])
data class WorkTaskEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val title: String,
    val note: String?,
    val status: String,
    val priority: Int,
    val dueEpochDay: Long?,
    val completedEpochDay: Long?,
    val createdAtMillis: Long,
)

/** Praise worth keeping: a mail, a message, a shout-out. The proof itself is attached ([WorkAttachmentEntity]). */
@Entity(tableName = "appreciation", indices = [Index("companyId"), Index("dateEpochDay")])
data class AppreciationEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val title: String,
    val fromPerson: String?,
    val text: String?,
    val dateEpochDay: Long,
    val createdAtMillis: Long,
)

/**
 * A short permission (late arrival, early leave, a few hours off). [category] is free text with suggestions, so each
 * company's own wording works. [minutes] is how long, if known.
 */
@Entity(tableName = "work_permission", indices = [Index("companyId"), Index("dayEpoch")])
data class WorkPermissionEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val dayEpoch: Long,
    val category: String,
    val minutes: Int?,
    val reason: String?,
    val createdAtMillis: Long,
)

/** A file attached to an appreciation (screenshot, mail export, PDF), encrypted in the file vault. */
@Entity(tableName = "work_attachment", indices = [Index("ownerId")])
data class WorkAttachmentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val vaultFileId: String,
    val addedAtMillis: Long,
)

@Dao
interface WorkDao {
    @Query("SELECT * FROM work_task ORDER BY CASE status WHEN 'DONE' THEN 1 ELSE 0 END, dueEpochDay IS NULL, dueEpochDay, priority DESC")
    fun observeTasks(): Flow<List<WorkTaskEntity>>

    @Upsert
    suspend fun upsertTask(task: WorkTaskEntity)

    @Query("DELETE FROM work_task WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM work_task WHERE companyId = :companyId")
    suspend fun deleteTasksOf(companyId: String)

    @Query("SELECT * FROM appreciation ORDER BY dateEpochDay DESC")
    fun observeAppreciations(): Flow<List<AppreciationEntity>>

    @Upsert
    suspend fun upsertAppreciation(a: AppreciationEntity)

    @Query("DELETE FROM appreciation WHERE id = :id")
    suspend fun deleteAppreciation(id: String)

    @Query("SELECT id FROM appreciation WHERE companyId = :companyId")
    suspend fun appreciationIdsOf(companyId: String): List<String>

    @Query("DELETE FROM appreciation WHERE companyId = :companyId")
    suspend fun deleteAppreciationsOf(companyId: String)

    @Query("SELECT * FROM work_permission ORDER BY dayEpoch DESC")
    fun observePermissions(): Flow<List<WorkPermissionEntity>>

    @Upsert
    suspend fun upsertPermission(p: WorkPermissionEntity)

    @Query("DELETE FROM work_permission WHERE id = :id")
    suspend fun deletePermission(id: String)

    @Query("DELETE FROM work_permission WHERE companyId = :companyId")
    suspend fun deletePermissionsOf(companyId: String)

    @Query("SELECT * FROM work_attachment ORDER BY addedAtMillis")
    fun observeWorkAttachments(): Flow<List<WorkAttachmentEntity>>

    @Upsert
    suspend fun upsertWorkAttachment(a: WorkAttachmentEntity)

    @Query("DELETE FROM work_attachment WHERE id = :id")
    suspend fun deleteWorkAttachment(id: String)

    @Query("SELECT * FROM work_attachment WHERE ownerId = :ownerId")
    suspend fun workAttachmentsOf(ownerId: String): List<WorkAttachmentEntity>

    @Query("DELETE FROM work_attachment WHERE ownerId = :ownerId")
    suspend fun deleteWorkAttachmentsOf(ownerId: String)

    @Query("SELECT * FROM company ORDER BY archived, name COLLATE NOCASE")
    fun observeCompanies(): Flow<List<CompanyEntity>>

    @Upsert
    suspend fun upsertCompany(company: CompanyEntity)

    @Query("DELETE FROM company WHERE id = :id")
    suspend fun deleteCompany(id: String)

    @Query("SELECT * FROM leave_type ORDER BY companyId, sortOrder")
    fun observeLeaveTypes(): Flow<List<LeaveTypeEntity>>

    @Upsert
    suspend fun upsertLeaveTypes(types: List<LeaveTypeEntity>)

    @Query("DELETE FROM leave_type WHERE companyId = :companyId")
    suspend fun deleteLeaveTypesOf(companyId: String)

    @Query("SELECT * FROM leave_entry ORDER BY startEpochDay DESC")
    fun observeLeaves(): Flow<List<LeaveEntryEntity>>

    @Upsert
    suspend fun upsertLeave(entry: LeaveEntryEntity)

    @Query("DELETE FROM leave_entry WHERE id = :id")
    suspend fun deleteLeave(id: String)

    @Query("DELETE FROM leave_entry WHERE companyId = :companyId")
    suspend fun deleteLeavesOf(companyId: String)

    @Query("SELECT * FROM attendance ORDER BY dayEpoch DESC, inMillis DESC")
    fun observeAttendance(): Flow<List<AttendanceEntity>>

    @Upsert
    suspend fun upsertAttendance(entry: AttendanceEntity)

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteAttendance(id: String)

    @Query("DELETE FROM attendance WHERE companyId = :companyId")
    suspend fun deleteAttendanceOf(companyId: String)
}
