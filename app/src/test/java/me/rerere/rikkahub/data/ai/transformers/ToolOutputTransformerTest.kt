package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.GEMINI_OUTPUT_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.appendGeminiToolOutputInstruction
import me.rerere.rikkahub.data.ai.tools.isGeminiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class ToolOutputTransformerTest {
    private val now = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun `create_document tool call is converted into plain assistant text`() {
        val toolPart = UIMessagePart.Tool(
            toolCallId = "call-1",
            toolName = GEMINI_OUTPUT_TOOL_NAME,
            input = """{"title":"response.md","content":"这是通过文档工具输出的完整正文内容。\n第二行内容。"}""",
            output = emptyList(),
        )
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(toolPart),
        )

        val transformed = listOf(message).transformToolOutputs().single()

        assertEquals(
            listOf(UIMessagePart.Text("这是通过文档工具输出的完整正文内容。\n第二行内容。")),
            transformed.parts,
        )
    }

    @Test
    fun `partial streaming JSON in create_document tool call is extracted progressively`() {
        val streamingToolPart = UIMessagePart.Tool(
            toolCallId = "call-stream",
            toolName = GEMINI_OUTPUT_TOOL_NAME,
            input = """{"title":"response.md","content":"流式输出中的第一段内容\n还在继续生成""",
            output = emptyList(),
        )
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(streamingToolPart),
        )

        val transformed = listOf(message).transformToolOutputs(generationFinished = false).single()

        assertEquals(
            listOf(UIMessagePart.Text("流式输出中的第一段内容\n还在继续生成")),
            transformed.parts,
        )
    }

    @Test
    fun `tool output containing dream_think and dream_body integrates with ThinkTagTransformer`() {
        val toolPart = UIMessagePart.Tool(
            toolCallId = "call-dream",
            toolName = GEMINI_OUTPUT_TOOL_NAME,
            input = """{"title":"story.md","content":"<dream_think>思考剧情走向</dream_think>\n<dream_plot><dream_body>正文故事内容</dream_body></dream_plot>"}""",
            output = emptyList(),
        )
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(toolPart),
        )

        val afterToolTransform = listOf(message).transformToolOutputs()
        val afterThinkTransform = afterToolTransform.transformThinkTags(now, generationFinished = true).single()

        assertEquals(
            "思考剧情走向",
            afterThinkTransform.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning,
        )
        assertEquals(
            "<dream_plot><dream_body>正文故事内容</dream_body></dream_plot>",
            afterThinkTransform.parts.filterIsInstance<UIMessagePart.Text>().single().text,
        )
    }

    @Test
    fun `isGeminiModel detects Google provider and gemini model IDs`() {
        val googleProvider = ProviderSetting.Google(name = "Google")
        val openAiProvider = ProviderSetting.OpenAI(name = "OpenAIProxy")

        assertTrue(isGeminiModel(Model(modelId = "gemini-2.5-pro", displayName = "Gemini 2.5 Pro"), openAiProvider))
        assertTrue(isGeminiModel(Model(modelId = "custom-model", displayName = "Custom"), googleProvider))
        assertFalse(isGeminiModel(Model(modelId = "claude-sonnet-4-5", displayName = "Claude"), openAiProvider))
    }

    @Test
    fun `appendGeminiToolOutputInstruction ensures trailing message is user role`() {
        val messagesWithPrefill = listOf(
            UIMessage.user("你好"),
            UIMessage.assistant("<dream_think>"),
        )
        val updated = appendGeminiToolOutputInstruction(messagesWithPrefill)

        assertEquals(MessageRole.USER, updated.last().role)
        assertTrue(updated.last().toText().contains(GEMINI_OUTPUT_TOOL_NAME))
    }
}
