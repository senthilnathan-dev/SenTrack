package `in`.senthilnathan_dev.sentrack.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

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
 */
@Database(entities = [MetaEntry::class], version = 1, exportSchema = true)
abstract class SenTrackDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao
}
