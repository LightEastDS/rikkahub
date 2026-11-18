package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_38_39"

/** 为 workspaces 增加工作区提示词模式（prompt_mode）与自定义提示词（custom_prompt）字段。 */
val Migration_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 38 to 39 (workspace custom prompt)")
        DatabaseMigrationTracker.onMigrationStart(38, 39)
        try {
            db.execSQL(
                "ALTER TABLE `workspaces` ADD COLUMN `prompt_mode` TEXT NOT NULL DEFAULT 'DEFAULT'"
            )
            db.execSQL(
                "ALTER TABLE `workspaces` ADD COLUMN `custom_prompt` TEXT"
            )
            Log.i(TAG, "migrate: migrate from 38 to 39 success")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
