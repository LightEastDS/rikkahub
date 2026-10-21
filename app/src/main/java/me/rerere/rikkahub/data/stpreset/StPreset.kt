package me.rerere.rikkahub.data.stpreset

import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

/**
 * SillyTavern Chat Completion 预设的运行时模型。
 *
 * 只保存解析后的结构，原始 JSON 由 [me.rerere.rikkahub.data.db.entity.StPresetEntity.rawJson] 保存，
 * 这样解析器升级后可以重新解析，也可以原样导回 SillyTavern。
 */
@Serializable
data class StPreset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val prompts: List<StPrompt> = emptyList(),
    val promptOrder: List<StPromptOrderBucket> = emptyList(),
    val settings: StGenerationSettings = StGenerationSettings(),
    val regexScripts: List<StRegexScript> = emptyList(),
    val characterBook: StCharacterBook? = null,
    /** 导入时发现的问题，展示在预设详情里。 */
    val warnings: List<String> = emptyList(),
)

@Serializable
data class StPrompt(
    val identifier: String,
    val name: String = "",
    val role: MessageRole = MessageRole.SYSTEM,
    val content: String = "",
    val marker: Boolean = false,
    /** ST 旧版内置槽位标记；不等于 [role]。 */
    val systemPrompt: Boolean = false,
    /** 0 = relative，1 = in-chat。 */
    val injectionPosition: Int = 0,
    val injectionDepth: Int = 4,
    val injectionOrder: Int = 100,
    /** 空集合表示所有生成类型。值对应 [StGenerationType.stName]。 */
    val injectionTrigger: Set<String> = emptySet(),
    val forbidOverrides: Boolean = false,
    /** 仅用于诊断：原始 prompts[].enabled，不可作为启用状态。 */
    val sourceEnabled: Boolean = false,
) {
    val isInChat: Boolean get() = injectionPosition == 1
}

@Serializable
data class StPromptOrderBucket(
    val characterId: Long = GLOBAL_CHARACTER_ID,
    val order: List<StPromptOrderEntry> = emptyList(),
) {
    companion object {
        /** SillyTavern 的 Chat Completion 全局排序桶。 */
        const val GLOBAL_CHARACTER_ID = 100001L
    }
}

@Serializable
data class StPromptOrderEntry(
    val identifier: String,
    val enabled: Boolean = true,
)

/** 预设详情页的一行：在 prompt_order 里的条目带 [orderIndex]，未挂载的为 null。 */
data class StPromptRow(
    val prompt: StPrompt,
    val orderIndex: Int?,
    val enabled: Boolean,
)

/** 结构性 marker：不允许在 UI 里停用，保证上下文/世界书永远有插入点。 */
object StPresetMarkers {
    val PROTECTED = setOf(
        "main",
        "worldInfoBefore",
        "worldInfoAfter",
        "chatHistory",
    )

    fun isProtected(identifier: String): Boolean = identifier in PROTECTED
}

@Serializable
data class StGenerationSettings(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val topA: Float? = null,
    val minP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val repetitionPenalty: Float? = null,
    val maxTokens: Int? = null,
    val maxContext: Int? = null,
    val squashSystemMessages: Boolean = false,
    val useSysPrompt: Boolean = false,
    val assistantPrefill: String = "",
    val continuePrefill: Boolean = false,
    val continuePostfix: String = "",
    val namesBehavior: Int? = null,
    val streamOpenAi: Boolean = true,
)

/**
 * 预设作用域的 Regex 脚本。
 *
 * [placement]：1 = 用户输入，2 = AI 输出。两者可以同时存在。
 */
@Serializable
data class StRegexScript(
    val id: String = "",
    val name: String = "",
    val findRegex: String = "",
    val replaceString: String = "",
    val trimStrings: List<String> = emptyList(),
    val placement: Set<Int> = emptySet(),
    val disabled: Boolean = false,
    val markdownOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = true,
    val substituteRegex: Boolean = false,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
) {
    val isPromptScript: Boolean get() = promptOnly || !markdownOnly
    val isDisplayScript: Boolean get() = markdownOnly
}

/** Character Book / 角色卡内嵌世界书的精简模型。 */
@Serializable
data class StCharacterBook(
    val name: String = "",
    val entries: List<StCharacterBookEntry> = emptyList(),
)

@Serializable
data class StCharacterBookEntry(
    val id: Int = 0,
    val keys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val content: String = "",
    val enabled: Boolean = true,
    val constant: Boolean = false,
    /** before_char / after_char / at_depth */
    val position: String = "before_char",
    val depth: Int = 4,
    val role: MessageRole = MessageRole.SYSTEM,
    val order: Int = 100,
    val probability: Int = 100,
    val scanDepth: Int? = null,
    val caseSensitive: Boolean? = null,
    val matchWholeWords: Boolean? = null,
    val selectiveLogic: Int = 0,
)

/** 当前的生成类型，用于过滤 [StPrompt.injectionTrigger]。 */
enum class StGenerationType(val stName: String) {
    NORMAL("normal"),
    CONTINUE("continue"),
    REGENERATE("regenerate"),
    IMPERSONATE("impersonate"),
    SWIPE("swipe"),
    QUIET("quiet"),
}

/** 组装时记录的可解释信息，用于 Inspect / 问题排查。 */
data class StAssemblyReport(
    val promptOrderBucket: Long = StPromptOrderBucket.GLOBAL_CHARACTER_ID,
    val orderedEntries: Int = 0,
    val renderedPrompts: Int = 0,
    val skippedDisabled: Int = 0,
    val skippedByTrigger: Int = 0,
    val skippedEmpty: Int = 0,
    val markerCount: Int = 0,
    val inChatCount: Int = 0,
    val unknownMarkers: List<String> = emptyList(),
    val unresolvedMacros: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val finalMessageCount: Int = 0,
)
