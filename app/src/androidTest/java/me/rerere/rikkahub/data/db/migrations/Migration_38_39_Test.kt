package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_38_39_Test {
    private val testDb = "migration-38-39-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun addsWorkspacePromptColumnsWithDefaultMode() {
        helper.createDatabase(testDb, 38).apply {
            execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at) " +
                    "VALUES ('ws1', 'Workspace', '/workspace', 'READY', 1000, 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 39, true, Migration_38_39)

        db.query("SELECT prompt_mode, custom_prompt FROM workspaces WHERE id = 'ws1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("DEFAULT", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
        db.close()
    }
}
