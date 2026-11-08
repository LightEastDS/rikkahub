package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.db.entity.ConversationCheckpointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListIndexMappingTest {
    private val checkpoint = ConversationCheckpointEntity(
        id = "cp-1",
        conversationId = "conv-1",
        startFloor = 1,
        boundaryNodeId = "msg-6",
        boundaryFloor = 6,
        summaryContent = "压缩摘要",
        originalTokens = 2000,
        compressedTokens = 300,
        createdAt = 1000L,
    )

    @Test
    fun `uncompacted conversation maps indices 1 to 1`() {
        for (i in 0 until 10) {
            assertFalse(isMessageInCompactedRange(i, 10, null))
            assertEquals(i, messageIndexToLazyIndex(i, 10, null, isCheckpointExpanded = false))
            assertEquals(i, lazyIndexToMessageIndex(i, 10, null, isCheckpointExpanded = false))
        }
    }

    @Test
    fun `collapsed checkpoint maps post-checkpoint messages with offset and compacted range to summary card`() {
        // Total 10 messages (indices 0..9), compacted floors 1..6 (indices 0..5)
        // LazyColumn items when collapsed:
        // [0]: CompactedSummaryCard (for messages 0..5)
        // [1]: message 6
        // [2]: message 7
        // [3]: message 8
        // [4]: message 9
        for (i in 0..5) {
            assertTrue(isMessageInCompactedRange(i, 10, checkpoint))
            assertEquals(0, messageIndexToLazyIndex(i, 10, checkpoint, isCheckpointExpanded = false))
        }
        for (i in 6..9) {
            assertFalse(isMessageInCompactedRange(i, 10, checkpoint))
            assertEquals(i - 5, messageIndexToLazyIndex(i, 10, checkpoint, isCheckpointExpanded = false))
            assertEquals(i, lazyIndexToMessageIndex(i - 5, 10, checkpoint, isCheckpointExpanded = false))
        }
        assertEquals(0, lazyIndexToMessageIndex(0, 10, checkpoint, isCheckpointExpanded = false))
    }

    @Test
    fun `expanded checkpoint shifts compacted and subsequent messages by 1 after summary card`() {
        // Mid-conversation checkpoint: floors 3..6 (indices 2..5) out of 10 messages (0..9)
        val midCheckpoint = checkpoint.copy(startFloor = 3, boundaryFloor = 6)
        // LazyColumn items when expanded:
        // [0]: message 0
        // [1]: message 1
        // [2]: CompactedSummaryCard
        // [3..6]: messages 2..5
        // [7..10]: messages 6..9
        assertEquals(0, messageIndexToLazyIndex(0, 10, midCheckpoint, isCheckpointExpanded = true))
        assertEquals(1, messageIndexToLazyIndex(1, 10, midCheckpoint, isCheckpointExpanded = true))
        for (i in 2..9) {
            assertEquals(i + 1, messageIndexToLazyIndex(i, 10, midCheckpoint, isCheckpointExpanded = true))
            assertEquals(i, lazyIndexToMessageIndex(i + 1, 10, midCheckpoint, isCheckpointExpanded = true))
        }
    }
}
