package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.time.Instant

private val THINKING_REGEX = Regex(
    "\\A\\s*<(think|dream_think)>([\\s\\S]*?)(</(?:think|dream_think)>|(?=<dream_plot\\b|<dream_body\\b)|$)",
    RegexOption.IGNORE_CASE,
)
private val DREAM_THINK_REGEX = Regex(
    "\\A\\s*<dream_think>([\\s\\S]*?)(</(?:think|dream_think)>|(?=<dream_plot\\b|<dream_body\\b)|$)",
    RegexOption.IGNORE_CASE,
)
private val PREFILLED_DREAM_THINK_REGEX = Regex(
    "\\A\\s*([\\s\\S]*?)(</dream_think>)\\s*",
    RegexOption.IGNORE_CASE,
)

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = false,
        )
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = true,
        )
    }
}

internal fun List<UIMessage>.transformThinkTags(
    now: Instant,
    generationFinished: Boolean,
): List<UIMessage> = map { message ->
    if (message.role != MessageRole.ASSISTANT) {
        return@map message
    }
    val hasExistingReasoning = message.hasPart<UIMessagePart.Reasoning>()
    val closedMessage = if (hasExistingReasoning && generationFinished) {
        message.copy(
            parts = message.parts.map { part ->
                if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                    part.copy(finishedAt = now)
                } else {
                    part
                }
            }
        )
    } else {
        message
    }

    val textPartIndex = closedMessage.parts.indexOfFirst { part ->
        part is UIMessagePart.Text && part.text.isNotBlank()
    }
    val textPart = closedMessage.parts.getOrNull(textPartIndex) as? UIMessagePart.Text
        ?: return@map closedMessage
    val openMatch = if (hasExistingReasoning) {
        DREAM_THINK_REGEX.find(textPart.text)
    } else {
        THINKING_REGEX.find(textPart.text)
    }
    val prefillMatch = if (openMatch == null) PREFILLED_DREAM_THINK_REGEX.find(textPart.text) else null
    val match = openMatch ?: prefillMatch ?: return@map closedMessage
    val reasoningText = when {
        openMatch != null && hasExistingReasoning -> openMatch.groupValues[1].trim()
        openMatch != null -> openMatch.groupValues[2].trim()
        else -> prefillMatch!!.groupValues[1].trim()
    }
    val hasClosingTag = when {
        openMatch != null && hasExistingReasoning -> openMatch.groupValues[2].isNotEmpty() || openMatch.range.last < textPart.text.lastIndex
        openMatch != null -> openMatch.groupValues[3].isNotEmpty() || openMatch.range.last < textPart.text.lastIndex
        else -> true
    }
    val reasoning = UIMessagePart.Reasoning(
        reasoning = reasoningText,
        createdAt = closedMessage.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
        finishedAt = if (generationFinished || hasClosingTag) now else null,
    )
    val strippedText = textPart.copy(text = textPart.text.removeRange(match.range).trimStart('\n', '\r'))

    closedMessage.copy(
        parts = buildList {
            addAll(closedMessage.parts.subList(0, textPartIndex))
            add(reasoning)
            add(strippedText)
            addAll(closedMessage.parts.subList(textPartIndex + 1, closedMessage.parts.size))
        }
    )
}
