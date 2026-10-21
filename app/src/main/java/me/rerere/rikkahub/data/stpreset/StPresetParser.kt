package me.rerere.rikkahub.data.stpreset

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

class StPresetParseException(message: String) : IllegalArgumentException(message)

data class StPresetImportReport(
    val promptDefinitions: Int = 0,
    val orderEntries: Int = 0,
    val enabledEntries: Int = 0,
    val enabledTextPrompts: Int = 0,
    val enabledMarkerPrompts: Int = 0,
    val detachedPrompts: Int = 0,
    val regexScripts: Int = 0,
    val enabledRegexScripts: Int = 0,
    val enabledChars: Int = 0,
    val warnings: List<String> = emptyList(),
) {
    val summary: String
        get() = "$enabledEntries 条启用 / $promptDefinitions 条定义 · $enabledChars 字符 · " +
            "$enabledMarkerPrompts 个 marker · $enabledRegexScripts/$regexScripts 个 regex"
}

data class StPresetParseResult(
    val preset: StPreset,
    val report: StPresetImportReport,
)

/**
 * SillyTavern Chat Completion 预设解析器。
 *
 * 刻意做成宽松解析：ST 预设没有 schema_version，字段会随版本增删。未知字段忽略，
 * 缺失字段使用默认值；结构性问题收集到 warnings，不直接抛异常。
 */
object StPresetParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 所有 ST 内置 marker。不在这里的 marker 会记录警告，但不会丢弃。 */
    val KNOWN_MARKERS = setOf(
        "main",
        "worldInfoBefore",
        "worldInfoAfter",
        "charDescription",
        "charPersonality",
        "scenario",
        "personaDescription",
        "dialogueExamples",
        "chatHistory",
    )

    fun parse(rawJson: String, fallbackName: String = "SillyTavern Preset", id: Uuid = Uuid.random()): StPresetParseResult {
        val root = runCatching { json.parseToJsonElement(rawJson.trim().removePrefix("\uFEFF")) }
            .getOrElse { throw StPresetParseException("不是合法的 JSON：${it.message}") }
            .jsonObjectOrNull()
            ?: throw StPresetParseException("预设顶层必须是 JSON 对象")

        return parse(root, fallbackName, id)
    }

    fun parse(root: JsonObject, fallbackName: String = "SillyTavern Preset", id: Uuid = Uuid.random()): StPresetParseResult {
        rejectUnsupported(root)

        val promptsArray = root["prompts"]?.jsonArrayOrNull()
            ?: throw StPresetParseException("缺少 prompts 数组，不是 Chat Completion 预设")
        if (promptsArray.isEmpty()) {
            throw StPresetParseException("prompts 数组为空，预设里没有可导入的提示")
        }
        if (promptsArray.any { it !is JsonObject }) {
            throw StPresetParseException("prompts 不是对象数组，可能是 Text Completion 预设；当前只支持 Chat Completion 预设")
        }

        val warnings = mutableListOf<String>()
        val promptObjects = promptsArray.mapNotNull { it as? JsonObject }
        val name = root.string("name")?.takeIf { it.isNotBlank() }
            ?: root.string("preset_name")?.takeIf { it.isNotBlank() }
            ?: fallbackName

        val prompts = mutableListOf<StPrompt>()
        val seenIdentifiers = mutableSetOf<String>()
        promptObjects.forEachIndexed { index, promptObject ->
            val prompt = parsePrompt(promptObject, index, warnings) ?: return@forEachIndexed
            if (!seenIdentifiers.add(prompt.identifier)) {
                warnings += "重复的 prompt identifier「${prompt.identifier}」，只保留第一条"
                return@forEachIndexed
            }
            prompts += prompt
        }
        if (prompts.isEmpty()) {
            throw StPresetParseException("没有解析出任何可用提示")
        }

        val promptOrder = parsePromptOrder(root, warnings)
        if (promptOrder.isEmpty()) {
            warnings += "prompt_order 为空，将按 prompts 数组顺序发送"
        }

        val promptMap = prompts.associateBy { it.identifier }
        val orderIdentifiers = promptOrder.flatMapTo(mutableSetOf()) { bucket ->
            bucket.order.map { it.identifier }
        }
        promptOrder.forEach { bucket ->
            bucket.order.forEach { entry ->
                if (promptMap[entry.identifier] == null) {
                    warnings += "prompt_order 引用了不存在的 identifier「${entry.identifier}」，已跳过"
                }
            }
        }
        val detached = prompts.count { it.identifier !in orderIdentifiers }
        if (detached > 0) {
            warnings += "有 $detached 条提示保存在 prompts[] 但没有挂到 prompt_order，不会发送"
        }

        val markers = prompts.filter { it.marker }
        markers.forEach { marker ->
            if (marker.identifier !in KNOWN_MARKERS) {
                warnings += "未知 marker「${marker.identifier}」，组装时会跳过并记录"
            }
        }

        val settings = parseSettings(root)
        val regexScripts = parseRegexScripts(root, warnings)
        val characterBook = parseCharacterBook(root["character_book"] as? JsonObject)

        val enabledEntries = promptOrder.sumOf { bucket -> bucket.order.count { it.enabled } }
        val enabledText = promptOrder.sumOf { bucket ->
            bucket.order.count { entry ->
                entry.enabled && promptMap[entry.identifier]?.let { !it.marker && it.content.isNotBlank() } == true
            }
        }
        val enabledMarkers = promptOrder.sumOf { bucket ->
            bucket.order.count { entry -> entry.enabled && promptMap[entry.identifier]?.marker == true }
        }
        val enabledChars = promptOrder.sumOf { bucket ->
            bucket.order.sumOf { entry ->
                if (!entry.enabled) return@sumOf 0
                val prompt = promptMap[entry.identifier] ?: return@sumOf 0
                if (prompt.marker) 0 else prompt.content.length
            }
        }

        val preset = StPreset(
            id = id,
            name = name,
            prompts = prompts,
            promptOrder = promptOrder,
            settings = settings,
            regexScripts = regexScripts,
            characterBook = characterBook,
            warnings = warnings.toList(),
        )
        val report = StPresetImportReport(
            promptDefinitions = prompts.size,
            orderEntries = promptOrder.sumOf { it.order.size },
            enabledEntries = enabledEntries,
            enabledTextPrompts = enabledText,
            enabledMarkerPrompts = enabledMarkers,
            detachedPrompts = detached,
            regexScripts = regexScripts.size,
            enabledRegexScripts = regexScripts.count { !it.disabled },
            enabledChars = enabledChars,
            warnings = warnings.toList(),
        )
        return StPresetParseResult(preset, report)
    }

    private fun rejectUnsupported(root: JsonObject) {
        if (root["entries"] is JsonObject && root["prompts"] == null) {
            throw StPresetParseException("这是世界书（World Info）文件，不是 Chat Completion 预设")
        }
        if (root["spec"] != null && root["data"] is JsonObject) {
            throw StPresetParseException("这是角色卡文件，请在助手的导入入口导入角色卡，而不是预设")
        }
        val prompts = root["prompts"]
        if (prompts == null && (root["temp"] != null || root["rep_pen"] != null)) {
            throw StPresetParseException("这是 Text Completion 预设，当前只支持 Chat Completion 预设")
        }
    }

    private fun parsePrompt(obj: JsonObject, index: Int, warnings: MutableList<String>): StPrompt? {
        val identifier = obj.string("identifier")?.trim().orEmpty()
            .ifEmpty { "prompt_$index" }
        val content = obj.string("content").orEmpty()
        val isMarker = obj.boolean("marker") == true
        val role = obj.messageRole("role")
        val trigger = obj["injection_trigger"].stringSet()
        val position = obj.int("injection_position") ?: 0
        val depth = obj.int("injection_depth") ?: 4
        val order = obj.int("injection_order") ?: 100
        if (position == 1 && depth < 0) {
            warnings += "「${obj.string("name") ?: identifier}」的 injection_depth 为负数，已按 0 处理"
        }
        return StPrompt(
            identifier = identifier,
            name = obj.string("name").orEmpty(),
            role = role,
            content = content,
            marker = isMarker,
            systemPrompt = obj.boolean("system_prompt") == true,
            injectionPosition = if (position == 1) 1 else 0,
            injectionDepth = depth.coerceAtLeast(0),
            injectionOrder = order,
            injectionTrigger = trigger,
            forbidOverrides = obj.boolean("forbid_overrides") == true,
            sourceEnabled = obj.boolean("enabled") == true,
        )
    }

    private fun parsePromptOrder(root: JsonObject, warnings: MutableList<String>): List<StPromptOrderBucket> {
        val orderElement = root["prompt_order"] ?: return emptyList()
        val array = orderElement.jsonArrayOrNull() ?: return emptyList()
        // 少数旧版本把 order 数组直接放在 prompt_order 顶层
        if (array.isNotEmpty() && array.all { element ->
                val obj = element as? JsonObject
                obj != null && obj["order"] == null && obj.string("identifier") != null
            }
        ) {
            return listOf(
                StPromptOrderBucket(
                    order = array.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        StPromptOrderEntry(
                            identifier = obj.string("identifier") ?: return@mapNotNull null,
                            enabled = obj.boolean("enabled") ?: true,
                        )
                    }
                )
            )
        }
        val buckets = mutableListOf<StPromptOrderBucket>()
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val orderArray = obj["order"].jsonArrayOrNull()
            if (orderArray != null) {
                val entries = orderArray.mapNotNull { entryElement ->
                    val entry = entryElement as? JsonObject ?: return@mapNotNull null
                    val identifier = entry.string("identifier") ?: return@mapNotNull null
                    StPromptOrderEntry(
                        identifier = identifier,
                        enabled = entry.boolean("enabled") ?: true,
                    )
                }
                buckets += StPromptOrderBucket(
                    characterId = obj.long("character_id") ?: StPromptOrderBucket.GLOBAL_CHARACTER_ID,
                    order = entries,
                )
            } else if (obj.string("identifier") != null) {
                // 少数旧版本直接把 order 数组放在 prompt_order 顶层
                buckets += StPromptOrderBucket(
                    order = listOf(
                        StPromptOrderEntry(
                            identifier = obj.string("identifier")!!,
                            enabled = obj.boolean("enabled") ?: true,
                        )
                    )
                )
            }
        }
        if (buckets.size > 1) {
            warnings += "预设包含 ${buckets.size} 个 prompt_order 桶，聊天时优先使用 character_id=${StPromptOrderBucket.GLOBAL_CHARACTER_ID} 的全局桶"
        }
        return buckets
    }

    private fun parseSettings(root: JsonObject): StGenerationSettings = StGenerationSettings(
        temperature = root.float("temperature"),
        topP = root.float("top_p"),
        topK = root.int("top_k"),
        topA = root.float("top_a"),
        minP = root.float("min_p"),
        frequencyPenalty = root.float("frequency_penalty"),
        presencePenalty = root.float("presence_penalty"),
        repetitionPenalty = root.float("repetition_penalty"),
        maxTokens = root.int("openai_max_tokens"),
        maxContext = root.int("openai_max_context"),
        squashSystemMessages = root.boolean("squash_system_messages") == true,
        useSysPrompt = root.boolean("use_sysprompt") == true,
        assistantPrefill = root.string("assistant_prefill").orEmpty(),
        continuePrefill = root.boolean("continue_prefill") == true,
        continuePostfix = root.string("continue_postfix").orEmpty(),
        namesBehavior = root.int("names_behavior"),
        streamOpenAi = root.boolean("stream_openai") ?: true,
    )

    private fun parseRegexScripts(root: JsonObject, warnings: MutableList<String>): List<StRegexScript> {
        val extensions = root["extensions"] as? JsonObject ?: return emptyList()
        val scripts = extensions["regex_scripts"].jsonArrayOrNull() ?: return emptyList()
        return scripts.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val findRegex = obj.string("findRegex").orEmpty()
            if (findRegex.isBlank()) {
                warnings += "忽略了一个没有 findRegex 的正则脚本「${obj.string("scriptName").orEmpty()}」"
                return@mapNotNull null
            }
            StRegexScript(
                id = obj.string("id").orEmpty(),
                name = obj.string("scriptName").orEmpty(),
                findRegex = findRegex,
                replaceString = obj.string("replaceString").orEmpty(),
                trimStrings = obj["trimStrings"].stringList(),
                placement = obj["placement"].intSet(),
                disabled = obj.boolean("disabled") == true,
                markdownOnly = obj.boolean("markdownOnly") == true,
                promptOnly = obj.boolean("promptOnly") == true,
                runOnEdit = obj.boolean("runOnEdit") ?: true,
                substituteRegex = obj.boolOrInt("substituteRegex") > 0,
                minDepth = obj.int("minDepth"),
                maxDepth = obj.int("maxDepth"),
            )
        }
    }

    /** 解析 Character Book（角色卡 data.character_book）；没有条目时返回 null。 */
    fun parseCharacterBook(book: JsonObject?): StCharacterBook? {
        book ?: return null
        val entries = book["entries"].jsonArrayOrNull() ?: return null
        return StCharacterBook(
            name = book.string("name").orEmpty(),
            entries = entries.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                parseCharacterBookEntry(obj)
            }
        )
    }

    private fun parseCharacterBookEntry(obj: JsonObject): StCharacterBookEntry? {
        val extensions = obj["extensions"] as? JsonObject
        val content = obj.string("content").orEmpty()
        if (content.isBlank()) return null
        return StCharacterBookEntry(
            id = obj.int("id") ?: obj.int("uid") ?: 0,
            keys = obj["keys"].stringList().ifEmpty { obj["key"].stringList() },
            secondaryKeys = obj["secondary_keys"].stringList().ifEmpty { obj["keysecondary"].stringList() },
            content = content,
            enabled = obj.boolean("enabled") ?: (obj.boolean("disable") != true),
            constant = obj.boolean("constant") == true,
            position = obj.string("position") ?: "before_char",
            depth = obj.int("depth") ?: extensions?.int("depth") ?: 4,
            role = extensions?.messageRole("role") ?: MessageRole.SYSTEM,
            order = obj.int("insertion_order") ?: obj.int("order") ?: 100,
            probability = extensions?.int("probability") ?: obj.int("probability") ?: 100,
            scanDepth = obj.int("scan_depth") ?: extensions?.int("scan_depth"),
            caseSensitive = obj.boolean("case_sensitive") ?: extensions?.boolean("case_sensitive"),
            matchWholeWords = obj.boolean("match_whole_words") ?: extensions?.boolean("match_whole_words"),
            selectiveLogic = obj.int("selectiveLogic") ?: extensions?.int("selectiveLogic") ?: 0,
        )
    }
}

private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.jsonArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let {
    it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt()
}

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let {
    it.contentOrNull?.toLongOrNull() ?: it.contentOrNull?.toDoubleOrNull()?.toLong()
}

private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
    it.booleanOrNull ?: when (it.contentOrNull?.lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
}

private fun JsonObject.boolOrInt(key: String): Int = (this[key] as? JsonPrimitive)?.let {
    it.intOrNull ?: if (it.booleanOrNull == true) 1 else 0
} ?: 0

private fun JsonObject.messageRole(key: String): MessageRole {
    val raw = string(key)
    return when (raw?.lowercase()) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "system" -> MessageRole.SYSTEM
        else -> when (int(key)) {
            1 -> MessageRole.USER
            2 -> MessageRole.ASSISTANT
            else -> MessageRole.SYSTEM
        }
    }
}

private fun JsonElement?.stringList(): List<String> {
    val array = this as? JsonArray ?: return emptyList()
    return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
}

private fun JsonElement?.stringSet(): Set<String> = stringList().toSet()

private fun JsonElement?.intSet(): Set<Int> {
    val array = this as? JsonArray ?: return emptySet()
    return array.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.toSet()
}
