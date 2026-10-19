package `in`.senthilnathan_dev.sentrack.core.data.career

import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import kotlinx.coroutines.flow.Flow

/** Learning, certifications, job applications, interviews and resumes. Screens go through this, never straight to Room. */
class CareerRepository(private val holder: DatabaseHolder) {
    private fun dao() = holder.get().careerDao()

    fun learning(): Flow<List<LearningItemEntity>> = dao().observeLearning()
    fun certifications(): Flow<List<CertificationEntity>> = dao().observeCertifications()
    fun applications(): Flow<List<JobApplicationEntity>> = dao().observeApplications()
    fun interviews(): Flow<List<InterviewEntity>> = dao().observeInterviews()
    fun resumes(): Flow<List<ResumeEntity>> = dao().observeResumes()
    fun attachments(): Flow<List<CareerAttachmentEntity>> = dao().observeCareerAttachments()

    suspend fun saveLearning(item: LearningItemEntity) = dao().upsertLearning(item)
    suspend fun deleteLearning(id: String) = dao().deleteLearning(id)
    suspend fun saveCertification(c: CertificationEntity) = dao().upsertCertification(c)
    suspend fun saveApplication(a: JobApplicationEntity) = dao().upsertApplication(a)
    suspend fun saveInterview(i: InterviewEntity) = dao().upsertInterview(i)
    suspend fun deleteInterview(id: String) = dao().deleteInterview(id)
    suspend fun saveResume(r: ResumeEntity) = dao().upsertResume(r)
    suspend fun saveAttachment(a: CareerAttachmentEntity) = dao().upsertCareerAttachment(a)
    suspend fun deleteAttachment(id: String) = dao().deleteCareerAttachment(id)

    /** Deletes a certification with its attachment records; returns vault ids of its files for the caller to erase. */
    suspend fun deleteCertification(id: String): List<String> {
        val dao = dao()
        val files = dao.careerAttachmentsOf(id).map { it.vaultFileId }
        dao.deleteCareerAttachmentsOf(id)
        dao.deleteCertification(id)
        return files
    }

    /** Deletes an application and its interviews; returns the interview ids so their reminders can be removed. */
    suspend fun deleteApplication(id: String): List<String> {
        val dao = dao()
        val interviews = dao.interviewIdsOf(id)
        dao.deleteInterviewsOf(id)
        dao.deleteApplication(id)
        return interviews
    }

    /** Deletes a resume version; applications that used it simply forget which version it was. Returns its vault id. */
    suspend fun deleteResume(resume: ResumeEntity): String {
        dao().clearResumeFrom(resume.id)
        dao().deleteResume(resume.id)
        return resume.vaultFileId
    }
}
