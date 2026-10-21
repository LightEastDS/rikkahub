package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 3.1.0.1.2 -> 3.1.0.1.3：新增预设副本的原始 JSON。
 *
 * 老数据没有 original_json，直接把当前 raw_json 当作原始副本；之后修改内容才能恢复默认。
 */
val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `st_presets` ADD COLUMN `original_json` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `st_presets` SET `original_json` = `raw_json` WHERE `original_json` = ''")
    }
}
