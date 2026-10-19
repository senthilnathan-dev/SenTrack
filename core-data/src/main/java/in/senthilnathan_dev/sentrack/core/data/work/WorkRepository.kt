package `in`.senthilnathan_dev.sentrack.core.data.work

import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Companies, leave and attendance. Screens go through this, never straight to Room. */
class WorkRepository(private val holder: DatabaseHolder) {
    private fun dao() = holder.get().workDao()

    fun companies(): Flow<List<CompanyEntity>> = dao().observeCompanies()
    fun leaveTypes(): Flow<List<LeaveTypeEntity>> = dao().observeLeaveTypes()
    fun leaves(): Flow<List<LeaveEntryEntity>> = dao().observeLeaves()
    fun attendance(): Flow<List<AttendanceEntity>> = dao().observeAttendance()

    suspend fun saveCompany(company: CompanyEntity) = dao().upsertCompany(company)

    /** Sets a company's leave types to exactly [types] (name, yearly days), in order. */
    suspend fun saveLeaveTypes(companyId: String, types: List<Triple<String?, String, Double>>) {
        val dao = dao()
        // Keep ids of types that already exist so past leave entries stay linked; new rows get fresh ids.
        dao.upsertLeaveTypes(
            types.filter { it.second.isNotBlank() }.mapIndexed { i, (id, name, days) ->
                LeaveTypeEntity(id ?: UUID.randomUUID().toString(), companyId, name.trim(), days, i)
            },
        )
    }

    suspend fun saveLeave(entry: LeaveEntryEntity) = dao().upsertLeave(entry)
    suspend fun deleteLeave(id: String) = dao().deleteLeave(id)
    suspend fun saveAttendance(entry: AttendanceEntity) = dao().upsertAttendance(entry)
    suspend fun deleteAttendance(id: String) = dao().deleteAttendance(id)

    /** Permanently deletes a company and everything recorded under it. */
    suspend fun deleteCompany(id: String) {
        val dao = dao()
        dao.deleteAttendanceOf(id)
        dao.deleteLeavesOf(id)
        dao.deleteLeaveTypesOf(id)
        dao.deleteCompany(id)
    }
}
