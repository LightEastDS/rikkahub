package me.rerere.rikkahub.data.stpreset

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

/**
 * SillyTavern 角色卡内嵌世界书 (character_book) 与独立世界书解析器。
 * 将 ST 的世界书转化为 RikkaHub 的原生 [Lorebook] 模型。
 */
object TavernWorldBookParser {

    /**
     * 解析 Character Book [JsonObject] 为 RikkaHub 的 [Lorebook]。
     *
     * @param bookJson 包含 `entries` 的 JSON 对象
     * @param defaultName 当世界书没有名称时使用的默认名称
     * @return 解析得到的 [Lorebook]，无有效条目时返回 null
     */
    fun parse(bookJson: JsonObject, defaultName: String = "SillyTavern Worldbook"): Lorebook? {
        val entriesJson: List<JsonObject> = when (val raw = bookJson["entries"]) {
            is JsonArray -> raw.mapNotNull { it as? JsonObject }
            is JsonObject -> raw.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        if (entriesJson.isEmpty()) return null

        val bookName = bookJson.string("name")?.takeIf { it.isNotBlank() } ?: defaultName
        val bookDescription = bookJson.string("description").orEmpty()

        val entries = entriesJson.mapIndexedNotNull { index, entryObj ->
            val content = entryObj.string("content").orEmpty()
            if (content.isBlank()) return@mapIndexedNotNull null

            val comment = entryObj.string("comment")
            val entryName = entryObj.string("name")
            val keys = parseStringList(entryObj["keys"]).ifEmpty { parseStringList(entryObj["key"]) }

            val title = comment?.takeIf { it.isNotBlank() }
                ?: entryName?.takeIf { it.isNotBlank() }
                ?: keys.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: "Entry ${index + 1}"

            val enabled = entryObj.boolean("enabled")
                ?: (entryObj.int("enabled")?.let { it == 1 })
                ?: (entryObj.boolean("disable")?.not())
                ?: (entryObj.int("disable")?.let { it == 0 })
                ?: true

            val constant = entryObj.boolean("constant") == true || entryObj.int("constant") == 1

            val order = entryObj.int("insertion_order") ?: entryObj.int("order") ?: 100

            val extensions = entryObj["extensions"] as? JsonObject
            val posString = entryObj.string("position")
            val posInt = extensions?.int("position") ?: entryObj.int("position")
            val position = mapPosition(posString, posInt)

            val depth = entryObj.int("depth") ?: extensions?.int("depth") ?: 4

            val useRegex = entryObj.boolean("use_regex")
                ?: entryObj.boolean("useRegex")
                ?: extensions?.boolean("use_regex")
                ?: extensions?.boolean("useRegex")
                ?: false

            val caseSensitive = entryObj.boolean("case_sensitive")
                ?: entryObj.boolean("caseSensitive")
                ?: extensions?.boolean("case_sensitive")
                ?: extensions?.boolean("caseSensitive")
                ?: false

            val scanDepth = entryObj.int("scan_depth")
                ?: entryObj.int("scanDepth")
                ?: extensions?.int("scan_depth")
                ?: extensions?.int("scanDepth")
                ?: 4

            val role = mapRole(
                roleString = extensions?.string("role") ?: entryObj.string("role"),
                roleInt = extensions?.int("role") ?: entryObj.int("role")
            )

            PromptInjection.RegexInjection(
                id = Uuid.random(),
                name = title,
                enabled = enabled,
                priority = order,
                position = position,
                injectDepth = depth,
                content = content,
                keywords = keys,
                useRegex = useRegex,
                caseSensitive = caseSensitive,
                scanDepth = scanDepth,
                constantActive = constant,
                role = role,
            )
        }

        if (entries.isEmpty()) return null

        return Lorebook(
            id = Uuid.random(),
            name = bookName,
            description = bookDescription,
            enabled = true,
            entries = entries,
        )
    }

    /**
     * 尝试从任意 JSON 字符串解析世界书（自动识别 data.character_book / character_book / 顶层 entries）。
     */
    fun parseFromJsonString(jsonString: String, defaultName: String = "SillyTavern Worldbook"): Lorebook? {
        return runCatching {
            val root = Json.parseToJsonElement(jsonString) as? JsonObject ?: return null
            // 角色卡 V2 / V3 嵌套
            val dataBook = (root["data"] as? JsonObject)?.get("character_book") as? JsonObject
            if (dataBook != null) return parse(dataBook, defaultName)

            // 根级别 character_book
            val rootBook = root["character_book"] as? JsonObject
            if (rootBook != null) return parse(rootBook, defaultName)

            // 顶层包含 entries 的独立世界书
            if (root.containsKey("entries")) return parse(root, defaultName)

            null
        }.getOrNull()
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> element.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            else -> emptyList()
        }
    }

    private fun mapPosition(posString: String?, posInt: Int?): InjectionPosition {
        if (!posString.isNullOrBlank()) {
            when (posString.lowercase().trim()) {
                "before_char", "before_system_prompt", "0" -> return InjectionPosition.BEFORE_SYSTEM_PROMPT
                "after_char", "after_system_prompt", "1" -> return InjectionPosition.AFTER_SYSTEM_PROMPT
                "top_of_chat", "2", "3" -> return InjectionPosition.TOP_OF_CHAT
                "bottom_of_chat" -> return InjectionPosition.BOTTOM_OF_CHAT
                "at_depth", "4" -> return InjectionPosition.AT_DEPTH
            }
        }
        return when (posInt) {
            0 -> InjectionPosition.BEFORE_SYSTEM_PROMPT
            1 -> InjectionPosition.AFTER_SYSTEM_PROMPT
            2, 3 -> InjectionPosition.TOP_OF_CHAT
            4 -> InjectionPosition.AT_DEPTH
            else -> InjectionPosition.AFTER_SYSTEM_PROMPT
        }
    }

    private fun mapRole(roleString: String?, roleInt: Int?): MessageRole {
        return when (roleString?.lowercase()?.trim()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            else -> when (roleInt) {
                1 -> MessageRole.USER
                2 -> MessageRole.ASSISTANT
                else -> MessageRole.USER
            }
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let {
        it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt()
    }

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
        it.booleanOrNull ?: when (it.contentOrNull?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }
}
