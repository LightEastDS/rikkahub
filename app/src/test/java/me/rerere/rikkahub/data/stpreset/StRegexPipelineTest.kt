package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StRegexPipelineTest {
    @Test
    fun `prompt-only script rewrites old user messages with minDepth`() {
        val script = StRegexScript(
            name = "clear old user",
            findRegex = "/[\\s\\S]+/g",
            replaceString = "",
            promptOnly = true,
            placement = setOf(1),
            minDepth = 1,
        )
        val messages = listOf(
            UIMessage.user("old-1"),
            UIMessage.assistant("old-a"),
            UIMessage.user("latest"),
        )
        val result = StRegexPipeline.applyToMessages(messages, listOf(script))
        assertEquals(listOf("", "old-a", "latest"), result.messages.map { it.toText() })
    }

    @Test
    fun `maxDepth limits rewrite to latest messages`() {
        val script = StRegexScript(
            name = "wrap latest",
            findRegex = "/^(.+)$/g",
            replaceString = "<inputs>$1</inputs>",
            promptOnly = true,
            placement = setOf(1),
            maxDepth = 0,
        )
        val messages = listOf(
            UIMessage.user("old"),
            UIMessage.assistant("a"),
            UIMessage.user("latest"),
        )
        val result = StRegexPipeline.applyToMessages(messages, listOf(script))
        assertEquals(listOf("old", "a", "<inputs>latest</inputs>"), result.messages.map { it.toText() })
    }

    @Test
    fun `trimStrings removes helper text after replacement`() {
        val script = StRegexScript(
            name = "trim",
            findRegex = "/x/",
            replaceString = "y<KEEP>",
            promptOnly = true,
            placement = setOf(1),
            trimStrings = listOf("<KEEP>"),
        )
        val result = StRegexPipeline.applyToMessages(listOf(UIMessage.user("x")), listOf(script))
        assertEquals("y", result.messages.single().toText())
    }

    @Test
    fun `invalid regex does not crash`() {
        val script = StRegexScript(
            name = "bad",
            findRegex = "/([/",
            replaceString = "",
            promptOnly = true,
            placement = setOf(1),
        )
        val result = StRegexPipeline.applyToMessages(listOf(UIMessage.user("x")), listOf(script))
        assertEquals("x", result.messages.single().toText())
        assertTrue(result.warnings.any { it.contains("bad") })
    }

    @Test
    fun `js dollar ampersand replacement works`() {
        val script = StRegexScript(
            name = "wrapped",
            findRegex = "/yes/",
            replaceString = "[$&]",
            promptOnly = true,
            placement = setOf(2),
        )
        val result = StRegexPipeline.applyToMessages(listOf(UIMessage.assistant("yes")), listOf(script))
        assertEquals("[yes]", result.messages.single().toText())
        assertEquals(MessageRole.ASSISTANT, result.messages.single().role)
    }
}
