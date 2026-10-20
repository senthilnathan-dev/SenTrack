package `in`.senthilnathan_dev.sentrack.core.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import `in`.senthilnathan_dev.sentrack.core.data.career.CareerAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.CareerDao
import `in`.senthilnathan_dev.sentrack.core.data.career.ChecklistItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.WorkspaceItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.CertificationEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.InterviewEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.JobApplicationEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.LearningItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.ResumeEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.BudgetEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceDao
import `in`.senthilnathan_dev.sentrack.core.data.finance.GoalAccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.GoalEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.RecurringEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocumentEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxFilingEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.AchievementEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitDao
import `in`.senthilnathan_dev.sentrack.core.data.personal.ImportantDateEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.LifeDao
import `in`.senthilnathan_dev.sentrack.core.data.personal.PersonalAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.PlanItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.PlanStepEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitLogEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteDao
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteFolderEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteTagEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteTagLinkEntity
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderDao
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.AppreciationEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.AttendanceEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkPermissionEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkTaskEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.CompanyEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.LeaveEntryEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.LeaveTypeEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkDao

/** Simple key/value settings stored inside the encrypted database (counters, flags, versions). */
@Entity(tableName = "meta")
data class MetaEntry(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: MetaEntry)
}

/**
 * The app's single encrypted database. Feature entities are added here phase by phase;
 * every schema change bumps [version] and ships a tested migration (schemas are exported).
 *
 * History (full table in docs/15-data-model.md): 1 = meta. 2 = Finance. 3 = tax filings and documents. 4 = tax-year archive
 * flag (never shipped alone). 5 = reminders. 6 = notes. 7 = habits (never shipped alone). 8 = plans, achievements, important
 * dates. 9 to 11 = Work and Career steps (never built alone). 12 = Work and Career tables. 13 = repeating finance entries.
 * Versions that never reached a phone have no schema file, so the auto-migration chain skips them (see AutoMigration below).
 */
@Database(
    entities = [
        MetaEntry::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        GoalAccountEntity::class,
        TaxFilingEntity::class,
        TaxDocumentEntity::class,
        ReminderEntity::class,
        NoteFolderEntity::class,
        NoteEntity::class,
        NoteTagEntity::class,
        NoteTagLinkEntity::class,
        NoteAttachmentEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        PlanItemEntity::class,
        PlanStepEntity::class,
        AchievementEntity::class,
        ImportantDateEntity::class,
        PersonalAttachmentEntity::class,
        CompanyEntity::class,
        LeaveTypeEntity::class,
        LeaveEntryEntity::class,
        AttendanceEntity::class,
        WorkTaskEntity::class,
        AppreciationEntity::class,
        WorkPermissionEntity::class,
        WorkAttachmentEntity::class,
        LearningItemEntity::class,
        CertificationEntity::class,
        JobApplicationEntity::class,
        InterviewEntity::class,
        ResumeEntity::class,
        CareerAttachmentEntity::class,
        WorkspaceItemEntity::class,
        ChecklistItemEntity::class,
        RecurringEntity::class,
    ],
    version = 13,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 8), AutoMigration(from = 8, to = 12), AutoMigration(from = 12, to = 13)],
)
abstract class SenTrackDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao
    abstract fun financeDao(): FinanceDao
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun habitDao(): HabitDao
    abstract fun lifeDao(): LifeDao
    abstract fun workDao(): WorkDao
    abstract fun careerDao(): CareerDao
}
