package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StPresetDocumentEditorTest {
    private val raw = """
        {
          "name": "copy",
          "prompts": [
            {"identifier": "a", "name": "A", "role": "system", "content": "old"},
            {"identifier": "b", "name": "B", "role": "user", "content": "keep"}
          ],
          "prompt_order": [{"character_id": 100001, "order": [{"identifier": "a", "enabled": true}]}]
        }
    """.trimIndent()

    @Test
    fun `edits one prompt and keeps other fields`() {
        val updated = StPresetDocumentEditor.updatePrompt(
            rawJson = raw,
            identifier = "a",
            name = "New A",
            content = "new content",
            role = MessageRole.ASSISTANT,
        )
        val preset = StPresetParser.parse(updated).preset
        assertEquals("New A", preset.prompts[0].name)
        assertEquals("new content", preset.prompts[0].content)
        assertEquals(MessageRole.ASSISTANT, preset.prompts[0].role)
        assertEquals("keep", preset.prompts[1].content)
        assertTrue(updated.contains("\"prompt_order\""))
    }
}
