package `in`.senthilnathan_dev.sentrack.core.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.BudgetEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceDao
import `in`.senthilnathan_dev.sentrack.core.data.finance.GoalAccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.GoalEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity

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
 * History: 1 = meta table. 2 = Finance tables (accounts, categories, transactions, budgets, goals).
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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SenTrackDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao
    abstract fun financeDao(): FinanceDao
}
