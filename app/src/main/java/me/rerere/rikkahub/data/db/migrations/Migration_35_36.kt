package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conversation_checkpoint` (
                `id` TEXT NOT NULL,
                `conversation_id` TEXT NOT NULL,
                `boundary_node_id` TEXT NOT NULL,
                `boundary_floor` INTEGER NOT NULL,
                `summary_content` TEXT NOT NULL,
                `original_tokens` INTEGER NOT NULL,
                `compressed_tokens` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_conversation_checkpoint_conversation_id` ON `conversation_checkpoint` (`conversation_id`)"
        )
    }
}
