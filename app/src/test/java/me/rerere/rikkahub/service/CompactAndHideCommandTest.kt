package me.rerere.rikkahub.service

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.CommandCompletionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactAndHideCommandTest {
    @Test
    fun testCompactArgsRegex() {
        val regex = ChatService.COMPACT_ARGS_REGEX
        val match1 = regex.matchEntire("/compact 1 10 2")
        assertNotNull(match1)
        assertEquals("1", match1!!.groupValues[1])
        assertEquals("10", match1.groupValues[2])
        assertEquals("2", match1.groupValues[3])
        assertEquals("", match1.groupValues[4])

        val match2 = regex.matchEntire("/compact 3 20 4 hide")
        assertNotNull(match2)
        assertEquals("3", match2!!.groupValues[1])
        assertEquals("20", match2.groupValues[2])
        assertEquals("4", match2.groupValues[3])
        assertEquals("hide", match2.groupValues[4])
    }

    @Test
    fun testCompactWithdrawRegex() {
        assertTrue(ChatService.COMPACT_WITHDRAW_REGEX.matches("/compact withdraw"))
        assertTrue(ChatService.COMPACT_WITHDRAW_REGEX.matches("/compact  withdraw "))
        assertFalse(ChatService.COMPACT_WITHDRAW_REGEX.matches("/compact 1 2 3"))
    }

    @Test
    fun testHideCommandRegex() {
        assertTrue(ChatService.isHideCommand("/hide"))
        assertTrue(ChatService.isHideCommand("/unhide"))
        assertTrue(ChatService.isHideCommand("/hide 5"))
        assertTrue(ChatService.isHideCommand("/unhide 3-18"))

        val matchEmpty = ChatService.HIDE_CMD_REGEX.matchEntire("/hide")
        assertNotNull(matchEmpty)
        assertEquals("", matchEmpty!!.groupValues[2].trim())

        val matchRange = ChatService.HIDE_CMD_REGEX.matchEntire("/hide 3-18")
        assertNotNull(matchRange)
        assertEquals("3-18", matchRange!!.groupValues[2].trim())
    }

    @Test
    fun testCommandCompletionProvider() = runBlocking {
        val provider = CommandCompletionProvider()

        // Slash alone
        val resSlash = provider.complete(ChatCompletionContext(text = "/", selection = androidx.compose.ui.text.TextRange(1)))
        assertNotNull(resSlash)
        assertEquals(4, resSlash!!.items.size)

        // /co
        val resCo = provider.complete(ChatCompletionContext(text = "/co", selection = androidx.compose.ui.text.TextRange(3)))
        assertNotNull(resCo)
        assertTrue(resCo!!.items.any { it.label == "/compact" })
        assertTrue(resCo.items.any { it.label == "/compact withdraw" })

        // /hide
        val resHide = provider.complete(ChatCompletionContext(text = "/hid", selection = androidx.compose.ui.text.TextRange(4)))
        assertNotNull(resHide)
        assertTrue(resHide!!.items.any { it.label == "/hide" })
    }
}
