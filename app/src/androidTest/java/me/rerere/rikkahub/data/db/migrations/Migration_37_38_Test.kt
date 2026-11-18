package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_37_38_Test {
    private val testDb = "migration-37-38-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun loadsNodesInOrderWithoutSortingAndAddsStorageMounts() {
        helper.createDatabase(testDb, 37).apply {
            execSQL(
                "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at) " +
                    "VALUES ('conv', 'assistant', 'Test', '[]', 1000, 1000)"
            )
            listOf(2, 0, 3, 1).forEach { index ->
                execSQL(
                    "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                        "VALUES (?, 'conv', ?, '[]', 0)",
                    arrayOf<Any?>("node-$index", index),
                )
            }
            execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at) " +
                    "VALUES ('ws1', 'Workspace', '/workspace', 'READY', 1000, 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 38, true, Migration_37_38)

        val query = "SELECT * FROM message_node WHERE conversation_id = 'conv' ORDER BY node_index ASC LIMIT 2 OFFSET 1"
        val ids = db.query(query).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("id"))) }
        }
        assertEquals(listOf("node-1", "node-2"), ids)

        val plan = db.query("EXPLAIN QUERY PLAN $query").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }
        }
        assertTrue(plan.toString(), plan.any { "index_message_node_conversation_id_node_index" in it })
        assertFalse(plan.toString(), plan.any { "TEMP B-TREE" in it })

        val storageMounts = db.query("SELECT storage_mounts FROM workspaces WHERE id = 'ws1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertEquals("[]", storageMounts)
        db.close()
    }

    @Test
    fun keepsNodesWithDuplicateIndex() {
        helper.createDatabase(testDb, 37).apply {
            execSQL(
                "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at) " +
                    "VALUES ('conv', 'assistant', 'Test', '[]', 1000, 1000)"
            )
            listOf("a" to 0, "b" to 1, "c" to 1, "d" to 1, "e" to 2).forEach { (id, index) ->
                execSQL(
                    "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                        "VALUES (?, 'conv', ?, '[]', 0)",
                    arrayOf<Any?>(id, index),
                )
            }
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 38, true, Migration_37_38)

        val ids = (0 until 6 step 2).flatMap { offset ->
            db.query(
                "SELECT id FROM message_node WHERE conversation_id = 'conv' " +
                    "ORDER BY node_index ASC LIMIT 2 OFFSET $offset"
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        }
        assertEquals(listOf("a", "b", "c", "d", "e"), ids)
        db.close()
    }
}
