package `in`.senthilnathan_dev.sentrack.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that a database created by any earlier release upgrades to the current schema without losing data.
 * Runs on a phone or emulator: `./gradlew :core-data:connectedDebugAndroidTest`.
 *
 * Each test creates the old schema from its exported JSON, writes a row, migrates to the latest version through the
 * app's real auto-migrations, and checks the row survived and every current table exists. Add a test for every release
 * that users may still have installed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SenTrackDatabase::class.java)

    private fun latestVersion() = 13

    @Test
    fun migrate_fromFirstVersion_throughEveryStep() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO meta (`key`, value) VALUES ('keep', 'me')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, latestVersion(), true)
        db.query("SELECT value FROM meta WHERE `key` = 'keep'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("me", c.getString(0))
        }
        assertAllCurrentTablesExist(db)
    }

    @Test
    fun migrate_fromNotesRelease_keepsNotes() {
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO note (id, folderId, title, body, pinned, archived, createdAtMillis, updatedAtMillis) VALUES ('n1', NULL, 'Title', 'Body', 0, 0, 1, 2)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, latestVersion(), true)
        db.query("SELECT title, body FROM note WHERE id = 'n1'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Title", c.getString(0))
            assertEquals("Body", c.getString(1))
        }
        assertAllCurrentTablesExist(db)
    }

    @Test
    fun migrate_fromPersonalRelease_keepsPlans() {
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO plan_item (id, kind, title, note, status, priority, targetEpochDay, costPaise, completedEpochDay, archived, createdAtMillis, updatedAtMillis) " +
                    "VALUES ('p1', 'GOAL', 'Goal', NULL, 'TODO', 1, NULL, NULL, NULL, 0, 1, 2)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, latestVersion(), true)
        db.query("SELECT title FROM plan_item WHERE id = 'p1'").use { c ->
            assertEquals(1, c.count)
        }
        assertAllCurrentTablesExist(db)
    }

    private fun assertAllCurrentTablesExist(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val expected = listOf(
            "company", "leave_type", "leave_entry", "attendance", "work_task", "appreciation", "work_permission", "work_attachment",
            "fin_recurring", "learning_item", "certification", "job_application", "interview", "resume", "career_attachment", "workspace_item", "checklist_item",
            "plan_item", "plan_step", "achievement", "important_date", "personal_attachment", "habit", "habit_log", "reminder",
        )
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            val found = buildSet { while (c.moveToNext()) add(c.getString(0)) }
            expected.forEach { assertEquals("table $it missing after migration", true, it in found) }
        }
    }
}
