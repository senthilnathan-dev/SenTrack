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

@Dao
interface WorkDao {
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
