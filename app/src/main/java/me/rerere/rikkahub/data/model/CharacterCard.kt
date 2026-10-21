package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.stpreset.StCharacterBook

/**
 * 结构化的角色卡字段。
 *
 * SillyTavern 预设的 marker 需要 description / personality / scenario / mesExample 分开使用，
 * 不能像旧版导入器那样全部拼进 systemPrompt。旧字段仍保留，保证没有绑定预设的助手行为不变。
 */
@Serializable
data class CharacterCard(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val characterBook: StCharacterBook? = null,
)
