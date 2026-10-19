package `in`.senthilnathan_dev.sentrack.core.data.career

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Something being learned: a course, a book, a skill. [kind] is `COURSE`, `BOOK`, `SKILL` or `OTHER`; [status] is
 * `PLANNED`, `LEARNING` or `DONE`; [progress] is 0 to 100.
 */
@Entity(tableName = "learning_item")
data class LearningItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val provider: String?,
    val kind: String,
    val status: String,
    val progress: Int,
    val targetEpochDay: Long?,
    val completedEpochDay: Long?,
    val note: String?,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/** A certification earned, with an optional expiry so renewals are not missed. The certificate itself is attached. */
@Entity(tableName = "certification")
data class CertificationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val issuer: String?,
    val earnedEpochDay: Long,
    val expiryEpochDay: Long?,
    val credentialId: String?,
    val note: String?,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/**
 * A job application. [status] runs `WISHLIST`, `APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER`, then ends as `ACCEPTED`,
 * `REJECTED` or `WITHDRAWN`. [resumeId] says which resume version was sent.
 */
@Entity(tableName = "job_application", indices = [Index("status")])
data class JobApplicationEntity(
    @PrimaryKey val id: String,
    val company: String,
    val role: String,
    val status: String,
    val appliedEpochDay: Long?,
    val source: String?,
    val location: String?,
    val salaryNote: String?,
    val link: String?,
    val resumeId: String?,
    val note: String?,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/** One interview round of an application. A reminder rings an hour before [atMillis]. */
@Entity(tableName = "interview", indices = [Index("applicationId")])
data class InterviewEntity(
    @PrimaryKey val id: String,
    val applicationId: String,
    val round: String,
    val atMillis: Long,
    val mode: String?,
    val note: String?,
    val done: Boolean,
)

/** A version of the resume. The file is encrypted in the vault; applications point to the version they used. */
@Entity(tableName = "resume")
data class ResumeEntity(
    @PrimaryKey val id: String,
    val label: String,
    val note: String?,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val vaultFileId: String,
    val archived: Boolean,
    val createdAtMillis: Long,
)

/** A file attached to a certification (the certificate), encrypted in the file vault. */
@Entity(tableName = "career_attachment", indices = [Index("ownerId")])
data class CareerAttachmentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val vaultFileId: String,
    val addedAtMillis: Long,
)

@Dao
interface CareerDao {
    @Query("SELECT * FROM learning_item ORDER BY archived, CASE status WHEN 'DONE' THEN 1 ELSE 0 END, createdAtMillis DESC")
    fun observeLearning(): Flow<List<LearningItemEntity>>

    @Upsert suspend fun upsertLearning(item: LearningItemEntity)

    @Query("DELETE FROM learning_item WHERE id = :id")
    suspend fun deleteLearning(id: String)

    @Query("SELECT * FROM certification ORDER BY archived, expiryEpochDay IS NULL, expiryEpochDay, earnedEpochDay DESC")
    fun observeCertifications(): Flow<List<CertificationEntity>>

    @Upsert suspend fun upsertCertification(c: CertificationEntity)

    @Query("DELETE FROM certification WHERE id = :id")
    suspend fun deleteCertification(id: String)

    @Query("SELECT * FROM job_application ORDER BY archived, createdAtMillis DESC")
    fun observeApplications(): Flow<List<JobApplicationEntity>>

    @Upsert suspend fun upsertApplication(a: JobApplicationEntity)

    @Query("DELETE FROM job_application WHERE id = :id")
    suspend fun deleteApplication(id: String)

    @Query("SELECT * FROM interview ORDER BY atMillis")
    fun observeInterviews(): Flow<List<InterviewEntity>>

    @Upsert suspend fun upsertInterview(i: InterviewEntity)

    @Query("DELETE FROM interview WHERE id = :id")
    suspend fun deleteInterview(id: String)

    @Query("SELECT id FROM interview WHERE applicationId = :applicationId")
    suspend fun interviewIdsOf(applicationId: String): List<String>

    @Query("DELETE FROM interview WHERE applicationId = :applicationId")
    suspend fun deleteInterviewsOf(applicationId: String)

    @Query("SELECT * FROM resume ORDER BY archived, createdAtMillis DESC")
    fun observeResumes(): Flow<List<ResumeEntity>>

    @Upsert suspend fun upsertResume(r: ResumeEntity)

    @Query("DELETE FROM resume WHERE id = :id")
    suspend fun deleteResume(id: String)

    @Query("UPDATE job_application SET resumeId = NULL WHERE resumeId = :resumeId")
    suspend fun clearResumeFrom(resumeId: String)

    @Query("SELECT * FROM career_attachment ORDER BY addedAtMillis")
    fun observeCareerAttachments(): Flow<List<CareerAttachmentEntity>>

    @Upsert suspend fun upsertCareerAttachment(a: CareerAttachmentEntity)

    @Query("DELETE FROM career_attachment WHERE id = :id")
    suspend fun deleteCareerAttachment(id: String)

    @Query("SELECT * FROM career_attachment WHERE ownerId = :ownerId")
    suspend fun careerAttachmentsOf(ownerId: String): List<CareerAttachmentEntity>

    @Query("DELETE FROM career_attachment WHERE ownerId = :ownerId")
    suspend fun deleteCareerAttachmentsOf(ownerId: String)
}
