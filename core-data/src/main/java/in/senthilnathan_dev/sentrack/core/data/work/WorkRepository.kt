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

    fun tasks(): Flow<List<WorkTaskEntity>> = dao().observeTasks()
    fun appreciations(): Flow<List<AppreciationEntity>> = dao().observeAppreciations()
    fun permissions(): Flow<List<WorkPermissionEntity>> = dao().observePermissions()
    fun attachments(): Flow<List<WorkAttachmentEntity>> = dao().observeWorkAttachments()

    suspend fun saveTask(task: WorkTaskEntity) = dao().upsertTask(task)
    suspend fun deleteTask(id: String) = dao().deleteTask(id)
    suspend fun saveAppreciation(a: AppreciationEntity) = dao().upsertAppreciation(a)
    suspend fun savePermission(p: WorkPermissionEntity) = dao().upsertPermission(p)
    suspend fun deletePermission(id: String) = dao().deletePermission(id)
    suspend fun saveAttachment(a: WorkAttachmentEntity) = dao().upsertWorkAttachment(a)
    suspend fun deleteAttachment(id: String) = dao().deleteWorkAttachment(id)

    /** Deletes an appreciation with its attachment records; returns the vault ids of its files so the caller erases them. */
    suspend fun deleteAppreciation(id: String): List<String> {
        val dao = dao()
        val files = dao.workAttachmentsOf(id).map { it.vaultFileId }
        dao.deleteWorkAttachmentsOf(id)
        dao.deleteAppreciation(id)
        return files
    }

    /**
     * Permanently deletes a company and everything recorded under it. Returns the vault ids of attachment files,
     * which the caller must erase from the file vault.
     */
    suspend fun deleteCompany(id: String): List<String> {
        val dao = dao()
        val files = dao.appreciationIdsOf(id).flatMap { a ->
            dao.workAttachmentsOf(a).map { it.vaultFileId }.also { dao.deleteWorkAttachmentsOf(a) }
        }
        dao.deleteAppreciationsOf(id)
        dao.deleteTasksOf(id)
        dao.deletePermissionsOf(id)
        dao.deleteAttendanceOf(id)
        dao.deleteLeavesOf(id)
        dao.deleteLeaveTypesOf(id)
        dao.deleteCompany(id)
        return files
    }
}
