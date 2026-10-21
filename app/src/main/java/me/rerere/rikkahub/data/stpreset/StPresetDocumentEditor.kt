package me.rerere.rikkahub.data.stpreset

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 编辑“预设副本”的 JSON 文档。
 *
 * 只改 prompts[] 中目标 identifier 的 name/content/role，保留原预设的所有未知字段；
 * 原始 JSON 仍单独保存在 original_json，用于恢复默认。
 */
object StPresetDocumentEditor {
    fun updatePrompt(
        rawJson: String,
        identifier: String,
        name: String,
        content: String,
        role: MessageRole,
    ): String {
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val prompts = root["prompts"]?.jsonArray ?: return rawJson
        var changed = false
        val newPrompts = prompts.map { element ->
            val obj = element as? JsonObject ?: return@map element
            val currentIdentifier = obj["identifier"]?.jsonPrimitive?.contentOrNull
            if (currentIdentifier != identifier) return@map element
            changed = true
            JsonObject(
                obj.toMutableMap().apply {
                    put("name", JsonPrimitive(name))
                    put("content", JsonPrimitive(content))
                    put("role", JsonPrimitive(role.name.lowercase()))
                }
            )
        }
        if (!changed) return rawJson
        val newRoot = JsonObject(
            root.toMutableMap().apply {
                put("prompts", JsonArray(newPrompts))
            }
        )
        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), newRoot)
        }.getOrDefault(rawJson)
    }
}
