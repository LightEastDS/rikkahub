package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `conversation_checkpoint` ADD COLUMN `start_floor` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `conversation_checkpoint` ADD COLUMN `auto_hidden_floors` TEXT NOT NULL DEFAULT ''")
    }
}
