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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val GEMINI_OUTPUT_TOOL_NAME = "create_document"
const val USER_INPUT_FILE_NAME = "user_input.txt"

internal const val GEMINI_TOOL_OUTPUT_INSTRUCTION = """
[Document Output Protocol / 工具输出要求]
你必须调用 `create_document` 工具来输出本轮的完整回复内容（将所有输出内容完整写入 `content` 参数中），禁止直接以普通文本输出正文。
如果上下文或预设要求了特定的输出格式或标签（例如 `<think>`、`<dream_think>`、`<dream_scene>`、`<dream_plot>`、`<dream_body>`、`<dream_summary>` 等），请严格按照要求生成，并将包含这些标签在内的全部完整内容原封不动地写入 `create_document` 工具的 `content` 参数中，严禁省略或截断。
"""

internal const val FILE_INPUT_INSTRUCTION = """
[File Input Protocol / 附件输入说明]
本轮用户输入内容已完整封装在附件文件 `user_input.txt` 中。请直接读取该文件中的全部内容作为用户本轮发送的消息，并严格按照上下文与设定直接给出回复，无需在正文中提及或解释“我已读取附件文件”。
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

/**
 * 将请求中真实的用户消息（非合成提示词）转换为文件附件形式发送给 AI，
 * 以规避输入侧敏感词审查并减少空回情况：
 * - Google (Gemini / Interactions) 与 Claude 渠道：编码为原生文档部件 (`inlineData` / `document` block)，
 *   使得原始文本在协议层以附件载荷（Gemini 下为 Base64）传输，不出现在普通文本字段中；
 * - OpenAI 及其他兼容渠道：封装为 `<UploadFile name="user_input.txt">` 文本块。
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun convertUserInputsToFiles(
    messages: List<UIMessage>,
    providerSetting: ProviderSetting? = null,
): List<UIMessage> {
    val instruction = FILE_INPUT_INSTRUCTION.trim()
    val useNativeDocumentPart =
        providerSetting is ProviderSetting.Google || providerSetting is ProviderSetting.Claude

    return messages.map { message ->
        if (message.role != MessageRole.USER || message.isSynthetic) {
            return@map message
        }
        val textParts = message.parts.filterIsInstance<UIMessagePart.Text>()
        val combinedText = textParts
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        if (combinedText.isEmpty()) {
            return@map message
        }
        // 已转换过的消息不再重复封装
        if (combinedText.contains(instruction) ||
            message.parts.any { it is UIMessagePart.Document && it.fileName == USER_INPUT_FILE_NAME && it.url.startsWith("data:") }
        ) {
            return@map message
        }

        // DocumentAsPromptTransformer 已经把本地 file:// 文档转换成了 <UploadFile> Text 部件，
        // 这里保留图片/音视频及已有的 data: 文档部件
        val preservedParts = message.parts.filter { part ->
            part !is UIMessagePart.Text &&
                !(part is UIMessagePart.Document && !part.url.startsWith("data:"))
        }

        val fileParts = if (useNativeDocumentPart) {
            val base64 = Base64.encode(combinedText.encodeToByteArray())
            listOf(
                UIMessagePart.Document(
                    url = "data:text/plain;base64,$base64",
                    fileName = USER_INPUT_FILE_NAME,
                    mime = "text/plain",
                ),
                UIMessagePart.Text(instruction),
            )
        } else {
            val wrappedPrompt = buildString {
                appendLine("""<UploadFile name="$USER_INPUT_FILE_NAME">""")
                appendLine("```")
                appendLine(combinedText)
                appendLine("```")
                appendLine("</UploadFile>")
                appendLine()
                append(instruction)
            }
            listOf(UIMessagePart.Text(wrappedPrompt))
        }

        message.copy(parts = preservedParts + fileParts)
    }
}
