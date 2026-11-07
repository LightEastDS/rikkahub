package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.GEMINI_OUTPUT_TOOL_NAME
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

private val OUTPUT_TOOL_NAMES = setOf(
    GEMINI_OUTPUT_TOOL_NAME,
    "write_document",
    "respond_with_document",
)

private val PARTIAL_CONTENT_KEY_REGEX = Regex(
    """"(?:content|text|body|markdown)"\s*:\s*""""
)

/**
 * 将 Gemini 抗截断文档输出工具（如 `create_document`）的调用参数还原为正常的对话文本部件 [UIMessagePart.Text]。
 *
 * - 在流式阶段 ([visualTransform]) 实时解析完整或半截 JSON 参数中的 `content` / `text` 字段，作为正文流式展示；
 * - 在步骤完成阶段 ([transform] / [onGenerationFinish]) 将工具调用永久替换为 [UIMessagePart.Text]，
 *   从而使 AgentLoop 直接结束本轮生成，并在下一轮对话时自然作为普通助手正文上下文发送给 AI。
 */
object ToolOutputTransformer : OutputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformToolOutputs(
            allowWorkspaceWriteFallback = ctx.assistant.workspaceId == null,
            generationFinished = true,
        )
    }

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformToolOutputs(
            allowWorkspaceWriteFallback = ctx.assistant.workspaceId == null,
            generationFinished = false,
        )
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformToolOutputs(
            allowWorkspaceWriteFallback = ctx.assistant.workspaceId == null,
            generationFinished = true,
        )
    }
}

internal fun List<UIMessage>.transformToolOutputs(
    allowWorkspaceWriteFallback: Boolean = false,
    generationFinished: Boolean = true,
): List<UIMessage> = map { message ->
    if (message.role != MessageRole.ASSISTANT) return@map message
    val hasOutputTool = message.parts.any { part ->
        part is UIMessagePart.Tool && isDocumentOutputTool(part.toolName, allowWorkspaceWriteFallback)
    }
    if (!hasOutputTool) return@map message

    val convertedParts = mutableListOf<UIMessagePart>()
    for (part in message.parts) {
        if (part is UIMessagePart.Tool && isDocumentOutputTool(part.toolName, allowWorkspaceWriteFallback)) {
            val extracted = extractDocumentContent(part.input)
            if (extracted != null) {
                if (extracted.isNotEmpty() || !generationFinished) {
                    convertedParts += UIMessagePart.Text(text = extracted, metadata = null)
                }
            } else if (!generationFinished) {
                // 流式早期参数尚未生成到 content 字段时，先占位空文本避免闪烁显示工具卡片
                convertedParts += UIMessagePart.Text(text = "", metadata = null)
            } else {
                convertedParts += part
            }
        } else {
            convertedParts += part
        }
    }

    val mergedParts = mergeAdjacentTextParts(convertedParts, generationFinished)
    message.copy(parts = mergedParts)
}

private fun isDocumentOutputTool(
    toolName: String,
    allowWorkspaceWriteFallback: Boolean,
): Boolean {
    if (toolName in OUTPUT_TOOL_NAMES) return true
    if (allowWorkspaceWriteFallback && toolName == "workspace_write_file") return true
    return false
}

internal fun extractDocumentContent(rawInput: String): String? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return ""

    val parsedObj = runCatching { JsonInstant.parseToJsonElement(trimmed).jsonObject }.getOrNull()
    if (parsedObj != null) {
        val value = (parsedObj["content"] ?: parsedObj["text"] ?: parsedObj["body"] ?: parsedObj["markdown"])
            ?.jsonPrimitiveOrNull
            ?.contentOrNull
        if (value != null) return value
    }

    // 处理流式未闭合的 JSON 字符串参数
    val keyMatch = PARTIAL_CONTENT_KEY_REGEX.find(trimmed) ?: return null
    val valueStart = keyMatch.range.last + 1
    if (valueStart > trimmed.length) return ""
    return unescapePartialJsonString(trimmed, valueStart)
}

private fun unescapePartialJsonString(source: String, startIndex: Int): String {
    val sb = StringBuilder(source.length - startIndex)
    var i = startIndex
    while (i < source.length) {
        val c = source[i]
        if (c == '"') {
            break
        }
        if (c == '\\') {
            if (i + 1 >= source.length) break
            when (val next = source[i + 1]) {
                '"' -> {
                    sb.append('"')
                    i += 2
                }
                '\\' -> {
                    sb.append('\\')
                    i += 2
                }
                '/' -> {
                    sb.append('/')
                    i += 2
                }
                'b' -> {
                    sb.append('\b')
                    i += 2
                }
                'f' -> {
                    sb.append('\u000C')
                    i += 2
                }
                'n' -> {
                    sb.append('\n')
                    i += 2
                }
                'r' -> {
                    sb.append('\r')
                    i += 2
                }
                't' -> {
                    sb.append('\t')
                    i += 2
                }
                'u' -> {
                    if (i + 6 <= source.length) {
                        val hex = source.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append("\\u")
                            i += 2
                        }
                    } else {
                        break
                    }
                }
                else -> {
                    sb.append(next)
                    i += 2
                }
            }
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun mergeAdjacentTextParts(
    parts: List<UIMessagePart>,
    generationFinished: Boolean,
): List<UIMessagePart> {
    if (parts.size <= 1) return parts
    val result = mutableListOf<UIMessagePart>()
    for (part in parts) {
        val prev = result.lastOrNull()
        if (part is UIMessagePart.Text && prev is UIMessagePart.Text) {
            val combinedText = when {
                prev.text.isEmpty() -> part.text
                part.text.isEmpty() -> prev.text
                prev.text.endsWith("\n") || part.text.startsWith("\n") -> prev.text + part.text
                else -> "${prev.text}\n${part.text}"
            }
            result[result.lastIndex] = prev.copy(
                text = combinedText,
                metadata = prev.metadata ?: part.metadata,
            )
        } else {
            result += part
        }
    }
    if (generationFinished && result.count { it is UIMessagePart.Text } > 1) {
        return result.filterNot { it is UIMessagePart.Text && it.text.isBlank() }
    }
    return result
}
