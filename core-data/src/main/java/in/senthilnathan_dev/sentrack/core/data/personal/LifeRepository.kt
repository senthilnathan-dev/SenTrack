package `in`.senthilnathan_dev.sentrack.core.data.personal

import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Plans, achievements, important dates and their attachments. Screens go through this, never straight to Room. */
class LifeRepository(private val holder: DatabaseHolder) {
    private fun dao() = holder.get().lifeDao()

    fun planItems(): Flow<List<PlanItemEntity>> = dao().observePlanItems()
    fun planSteps(): Flow<List<PlanStepEntity>> = dao().observePlanSteps()
    fun achievements(): Flow<List<AchievementEntity>> = dao().observeAchievements()
    fun importantDates(): Flow<List<ImportantDateEntity>> = dao().observeImportantDates()
    fun attachments(): Flow<List<PersonalAttachmentEntity>> = dao().observePersonalAttachments()

    /** Saves a plan item and sets its steps to exactly [steps] (titles with done flags, in order). */
    suspend fun savePlanItem(item: PlanItemEntity, steps: List<Pair<String, Boolean>>) {
        val dao = dao()
        dao.upsertPlanItem(item)
        dao.deleteStepsFor(item.id)
        dao.upsertSteps(
            steps.filter { it.first.isNotBlank() }.mapIndexed { i, (title, done) ->
                PlanStepEntity(UUID.randomUUID().toString(), item.id, title.trim(), done, i)
            },
        )
    }

    /** Permanently deletes a plan item with its steps and attachment records; returns the vault ids of its files. */
    suspend fun deletePlanItem(id: String): List<String> = deleteWithAttachments(id) {
        dao().deleteStepsFor(id)
        dao().deletePlanItem(id)
    }

    suspend fun saveAchievement(achievement: AchievementEntity) = dao().upsertAchievement(achievement)

    suspend fun deleteAchievement(id: String): List<String> = deleteWithAttachments(id) { dao().deleteAchievement(id) }

    suspend fun saveImportantDate(date: ImportantDateEntity) = dao().upsertImportantDate(date)

    suspend fun deleteImportantDate(id: String) = dao().deleteImportantDate(id)

    suspend fun saveAttachment(attachment: PersonalAttachmentEntity) = dao().upsertPersonalAttachment(attachment)

    suspend fun deleteAttachment(id: String) = dao().deletePersonalAttachment(id)

    private suspend fun deleteWithAttachments(ownerId: String, delete: suspend () -> Unit): List<String> {
        val dao = dao()
        val files = dao.attachmentsOf(ownerId).map { it.vaultFileId }
        dao.deleteAttachmentsOf(ownerId)
        delete()
        return files
    }
}
