package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SillyTavern 预设。原始 JSON 必须原样保存，解析结果按需重建。
 */
@Entity(
    tableName = "st_presets",
    indices = [Index(value = ["sort_index"])]
)
data class StPresetEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("raw_json")
    val rawJson: String,
    /** 导入时的原始 JSON，用于恢复默认副本。 */
    @ColumnInfo("original_json", defaultValue = "")
    val originalJson: String = "",
    @ColumnInfo("source_version")
    val sourceVersion: String? = null,
    @ColumnInfo("prompt_count", defaultValue = "0")
    val promptCount: Int = 0,
    @ColumnInfo("imported_at", defaultValue = "0")
    val importedAt: Long = 0,
    @ColumnInfo("enabled", defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo("sort_index", defaultValue = "0")
    val sortIndex: Int = 0,
    /** 用户自定义的 prompt_order；空串表示使用预设原始排序。 */
    @ColumnInfo("order_json", defaultValue = "")
    val orderJson: String = "",
)

/**
 * 预设宏变量。scope_id 为会话 id 时是会话变量，为 [GLOBAL_SCOPE] 时是全局变量。
 */
@Entity(
    tableName = "st_variables",
    primaryKeys = ["scope_id", "name"],
    indices = [Index(value = ["scope_id"])]
)
data class StVariableEntity(
    @ColumnInfo("scope_id")
    val scopeId: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("value")
    val value: String,
    @ColumnInfo("updated_at", defaultValue = "0")
    val updatedAt: Long = 0,
) {
    companion object {
        const val GLOBAL_SCOPE = "__global__"
    }
}
