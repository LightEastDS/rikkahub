package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class ThinkTagTransformerTest {
    private val now = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun `think tag at start should be converted to reasoning`() {
        val result = transform("<think>reason</think>answer")

        assertEquals("reason", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertEquals("answer", result.parts.filterIsInstance<UIMessagePart.Text>().single().text)
        assertEquals(now, result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().finishedAt)
    }

    @Test
    fun `think tag in visible answer should be preserved`() {
        val text = "The literal `<think>` tag should stay visible."

        assertEquals(listOf(UIMessagePart.Text(text)), transform(text).parts)
    }

    @Test
    fun `native reasoning should prevent fallback tag parsing`() {
        val nativeReasoning = UIMessagePart.Reasoning("native reasoning")
        val text = UIMessagePart.Text("answer mentions <think> without a closing tag")
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(nativeReasoning, text),
        )

        val result = listOf(message).transformThinkTags(now, generationFinished = true).single()

        assertEquals(listOf(nativeReasoning, text), result.parts)
    }

    @Test
    fun `later think tags should remain visible`() {
        val result = transform("<think>reason</think>answer <think>literal")

        assertEquals("reason", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertEquals("answer <think>literal", result.parts.filterIsInstance<UIMessagePart.Text>().single().text)
    }

    @Test
    fun `think tag in a later text part should be preserved`() {
        val parts = listOf(
            UIMessagePart.Text("visible answer"),
            UIMessagePart.Text("<think>literal"),
        )
        val message = UIMessage(role = MessageRole.ASSISTANT, parts = parts)

        val result = listOf(message).transformThinkTags(now, generationFinished = true).single()

        assertEquals(parts, result.parts)
    }

    @Test
    fun `unclosed prefix tag should remain unfinished while streaming`() {
        val message = UIMessage.assistant("<think>reason in progress")

        val result = listOf(message).transformThinkTags(now, generationFinished = false).single()

        assertEquals("reason in progress", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertNull(result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().finishedAt)
    }

    @Test
    fun `generation finish should close reasoning created by visual transform`() {
        val message = UIMessage.assistant("<think>reason in progress")
        val visualResult = listOf(message).transformThinkTags(now, generationFinished = false)

        val finishedResult = visualResult.transformThinkTags(now, generationFinished = true).single()

        assertEquals(now, finishedResult.parts.filterIsInstance<UIMessagePart.Reasoning>().single().finishedAt)
    }

    @Test
    fun `dream_think closed by mismatched think tag should not swallow body text`() {
        val result = transform("<dream_think>剧情规划</think>\n<dream_plot><dream_body>正文内容</dream_body></dream_plot>")

        assertEquals("剧情规划", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertEquals(
            "<dream_plot><dream_body>正文内容</dream_body></dream_plot>",
            result.parts.filterIsInstance<UIMessagePart.Text>().single().text,
        )
    }

    @Test
    fun `unclosed dream_think before dream_plot should stop at dream_plot boundary`() {
        val result = transform("<dream_think>剧情规划\n<dream_plot><dream_body>正文内容</dream_body></dream_plot>")

        assertEquals("剧情规划", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertEquals(
            "<dream_plot><dream_body>正文内容</dream_body></dream_plot>",
            result.parts.filterIsInstance<UIMessagePart.Text>().single().text,
        )
    }

    @Test
    fun `native reasoning still extracts dream_think tag from text`() {
        val nativeReasoning = UIMessagePart.Reasoning("native thought", finishedAt = now)
        val text = UIMessagePart.Text("<dream_think>预设思考</dream_think>\n正文内容")
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(nativeReasoning, text),
        )

        val result = listOf(message).transformThinkTags(now, generationFinished = true).single()

        val reasonings = result.parts.filterIsInstance<UIMessagePart.Reasoning>()
        assertEquals(listOf("native thought", "预设思考"), reasonings.map { it.reasoning })
        assertEquals("正文内容", result.parts.filterIsInstance<UIMessagePart.Text>().single().text)
    }

    private fun transform(text: String): UIMessage {
        val message = UIMessage.assistant(text)
        return listOf(message).transformThinkTags(now, generationFinished = true).single()
    }
}
