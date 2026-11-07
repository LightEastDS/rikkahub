package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

const val GEMINI_OUTPUT_TOOL_NAME = "create_document"

internal const val GEMINI_TOOL_OUTPUT_INSTRUCTION = """
[Document Output Protocol / 工具输出要求]
你必须调用 `create_document` 工具来输出本轮的完整回复内容（将所有输出内容完整写入 `content` 参数中），禁止直接以普通文本输出正文。
如果上下文或预设要求了特定的输出格式或标签（例如 `<think>`、`<dream_think>`、`<dream_scene>`、`<dream_plot>`、`<dream_body>`、`<dream_summary>` 等），请严格按照要求生成，并将包含这些标签在内的全部完整内容原封不动地写入 `create_document` 工具的 `content` 参数中，严禁省略或截断。
"""

/**
 * 判断当前模型是否为 Gemini 模型（覆盖官方 Google 提供商及 OpenAI 兼容反代渠道，排除纯生图模型）。
 */
fun isGeminiModel(model: Model, providerSetting: ProviderSetting? = null): Boolean {
    if (model.outputModalities.contains(Modality.IMAGE)) return false
    return providerSetting is ProviderSetting.Google ||
        ModelRegistry.GEMINI_SERIES.match(model.modelId) ||
        model.modelId.contains("gemini", ignoreCase = true) ||
        model.displayName.contains("gemini", ignoreCase = true)
}

/**
 * 创建用于 Gemini 抗截断的文档输出工具。
 * 模型将完整回复写入 `content` 参数后，前端与输出转换器会自动将其提取并还原为正常对话正文。
 */
fun createGeminiOutputTool(): Tool = Tool(
    name = GEMINI_OUTPUT_TOOL_NAME,
    description = "Create a markdown document to write and deliver the full response content. Always write your complete response into the 'content' parameter of this tool instead of direct text output.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", "Document file path or title, e.g. response.md")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "Full UTF-8 markdown content of the document. Write the complete reply/story content here without any omission or truncation.")
                })
            },
            required = listOf("content"),
        )
    },
    systemPrompt = { _, _ ->
        GEMINI_TOOL_OUTPUT_INSTRUCTION.trim()
    },
    needsApproval = { false },
    execute = {
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("status", "ok")
                }.toString()
            )
        )
    },
)

/**
 * 在请求消息列表尾部注入 Gemini 工具输出指令，并确保当处于强制函数调用模式时，
 * 列表末尾不会停留在纯文本的 ASSISTANT 预填充消息上（避免 Gemini API 在 ANY 模式下报错）。
 */
internal fun appendGeminiToolOutputInstruction(messages: List<UIMessage>): List<UIMessage> {
    val instruction = GEMINI_TOOL_OUTPUT_INSTRUCTION.trim()
    if (messages.isEmpty()) {
        return listOf(UIMessage.user(instruction).copy(isSynthetic = true))
    }
    val result = messages.toMutableList()
    val lastIndex = result.lastIndex
    val last = result[lastIndex]

    when {
        last.role == MessageRole.USER -> {
            result[lastIndex] = appendInstructionToUserMessage(last, instruction)
        }

        last.role == MessageRole.ASSISTANT && last.getTools().isNotEmpty() -> {
            // 多步工具调用续写：在最后一个 USER 消息上追加提示，保持末尾的 activeToolTurn 位置不变
            val lastUserIdx = result.indexOfLast { it.role == MessageRole.USER }
            if (lastUserIdx >= 0) {
                result[lastUserIdx] = appendInstructionToUserMessage(result[lastUserIdx], instruction)
            }
        }

        else -> {
            // 末尾是无工具调用的 ASSISTANT 预填充消息或 SYSTEM 消息，追加一条 USER 指令消息以触发工具调用
            result += UIMessage.user(instruction).copy(isSynthetic = true)
        }
    }
    return result
}

private fun appendInstructionToUserMessage(message: UIMessage, instruction: String): UIMessage {
    if (message.toText().contains(GEMINI_OUTPUT_TOOL_NAME)) return message
    val lastTextIdx = message.parts.indexOfLast { it is UIMessagePart.Text }
    return if (lastTextIdx >= 0) {
        val textPart = message.parts[lastTextIdx] as UIMessagePart.Text
        val updatedText = if (textPart.text.isBlank()) {
            instruction
        } else {
            "${textPart.text.trimEnd()}\n\n$instruction"
        }
        message.copy(
            parts = message.parts.toMutableList().also {
                it[lastTextIdx] = textPart.copy(text = updatedText)
            }
        )
    } else {
        message.copy(parts = message.parts + UIMessagePart.Text(instruction))
    }
}
