package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StWorldInfoEngineTest {
    @Test
    fun `constant and keyword entries are split by position`() {
        val book = StCharacterBook(
            entries = listOf(
                StCharacterBookEntry(id = 1, content = "WORLD", constant = true, position = "before_char"),
                StCharacterBookEntry(id = 2, content = "KEY", keys = listOf("magic"), position = "after_char"),
            )
        )
        val result = StWorldInfoEngine.collect(
            characterBook = book,
            lorebooks = emptyList(),
            messages = listOf(UIMessage.user("tell me about magic")),
            random = Random(1),
        )
        assertEquals("WORLD", result.before)
        assertEquals("KEY", result.after)
        assertEquals(2, result.activatedNames.size)
    }

    @Test
    fun `at depth entries become depth injection`() {
        val book = StCharacterBook(
            entries = listOf(
                StCharacterBookEntry(
                    id = 1,
                    content = "DEEP",
                    constant = true,
                    position = "at_depth",
                    depth = 3,
                    role = MessageRole.ASSISTANT,
                )
            )
        )
        val result = StWorldInfoEngine.collect(book, emptyList(), listOf(UIMessage.user("hi")))
        assertEquals(1, result.depthEntries.size)
        assertEquals(3, result.depthEntries.single().depth)
        assertEquals(MessageRole.ASSISTANT, result.depthEntries.single().role)
    }

    @Test
    fun `secondary keys respect selective logic`() {
        val book = StCharacterBook(
            entries = listOf(
                StCharacterBookEntry(
                    id = 1,
                    content = "BOTH",
                    keys = listOf("a"),
                    secondaryKeys = listOf("b", "c"),
                    selectiveLogic = 3,
                    position = "before_char",
                )
            )
        )
        val miss = StWorldInfoEngine.collect(book, emptyList(), listOf(UIMessage.user("a b")))
        assertTrue(miss.before.isEmpty())
        val hit = StWorldInfoEngine.collect(book, emptyList(), listOf(UIMessage.user("a b c")))
        assertEquals("BOTH", hit.before)
    }
}
