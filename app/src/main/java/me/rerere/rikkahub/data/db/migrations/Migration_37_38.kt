package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_37_38"

/**
 * 3.2.0.2 -> 3.3.0.1：合并上游 DB 32 -> 34 变更到 Fork DB 37 -> 38：
 * 1. message_node 的索引从 (conversation_id) 换成 (conversation_id, node_index)
 * 2. workspaces 新增 storage_mounts 列
 */
val Migration_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 37 to 38 (message_node index & workspace storage_mounts)")
        DatabaseMigrationTracker.onMigrationStart(37, 38)
        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_message_node_conversation_id_node_index` " +
                    "ON `message_node` (`conversation_id`, `node_index`)"
            )
            db.execSQL("DROP INDEX IF EXISTS `index_message_node_conversation_id`")
            db.execSQL(
                "ALTER TABLE `workspaces` ADD COLUMN `storage_mounts` TEXT NOT NULL DEFAULT '[]'"
            )
            Log.i(TAG, "migrate: migrate from 37 to 38 success")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
