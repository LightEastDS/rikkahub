package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `adds new prompt and mounts in prompt_order`() {
        val updated = StPresetDocumentEditor.addPrompt(
            rawJson = raw,
            name = "New Item",
            content = "Hello world",
            role = MessageRole.USER,
            identifier = "custom_test_1",
        )
        val preset = StPresetParser.parse(updated).preset
        assertEquals(3, preset.prompts.size)
        val addedPrompt = preset.prompts.first { it.identifier == "custom_test_1" }
        assertEquals("New Item", addedPrompt.name)
        assertEquals("Hello world", addedPrompt.content)
        assertEquals(MessageRole.USER, addedPrompt.role)

        val globalOrder = preset.promptOrder.first { it.characterId == StPromptOrderBucket.GLOBAL_CHARACTER_ID }.order
        assertTrue(globalOrder.any { it.identifier == "custom_test_1" && it.enabled })
    }

    @Test
    fun `deletes prompt and removes from prompt_order`() {
        val withAdded = StPresetDocumentEditor.addPrompt(
            rawJson = raw,
            name = "To Delete",
            content = "bye",
            role = MessageRole.SYSTEM,
            identifier = "del_me",
        )
        val deleted = StPresetDocumentEditor.deletePrompt(withAdded, "del_me")
        val preset = StPresetParser.parse(deleted).preset
        assertNull(preset.prompts.firstOrNull { it.identifier == "del_me" })
        val globalOrder = preset.promptOrder.first { it.characterId == StPromptOrderBucket.GLOBAL_CHARACTER_ID }.order
        assertFalse(globalOrder.any { it.identifier == "del_me" })
    }

    @Test
    fun `cannot delete protected marker`() {
        val rawWithMarker = """
            {
              "name": "copy",
              "prompts": [
                {"identifier": "main", "name": "Main", "marker": true, "content": ""}
              ],
              "prompt_order": [{"character_id": 100001, "order": [{"identifier": "main", "enabled": true}]}]
            }
        """.trimIndent()
        val res = StPresetDocumentEditor.deletePrompt(rawWithMarker, "main")
        assertEquals(rawWithMarker, res)
    }

    @Test
    fun `regex script CRUD and toggle`() {
        val script = StRegexScript(
            id = "script_1",
            name = "My Regex",
            findRegex = "<think>.*?</think>",
            replaceString = "",
            placement = setOf(2),
            disabled = false,
            markdownOnly = true,
        )

        // Add
        val withScript = StPresetDocumentEditor.addRegexScript(raw, script)
        var parsed = StPresetParser.parse(withScript).preset
        assertEquals(1, parsed.regexScripts.size)
        assertEquals("My Regex", parsed.regexScripts[0].name)
        assertEquals("<think>.*?</think>", parsed.regexScripts[0].findRegex)
        assertFalse(parsed.regexScripts[0].disabled)

        // Toggle
        val toggled = StPresetDocumentEditor.toggleRegexScript(withScript, "script_1", disabled = true)
        parsed = StPresetParser.parse(toggled).preset
        assertTrue(parsed.regexScripts[0].disabled)

        // Update
        val updatedScript = script.copy(name = "Updated Regex", replaceString = "[thought]")
        val withUpdate = StPresetDocumentEditor.updateRegexScript(toggled, updatedScript)
        parsed = StPresetParser.parse(withUpdate).preset
        assertEquals("Updated Regex", parsed.regexScripts[0].name)
        assertEquals("[thought]", parsed.regexScripts[0].replaceString)

        // Delete
        val deleted = StPresetDocumentEditor.deleteRegexScript(withUpdate, "script_1")
        parsed = StPresetParser.parse(deleted).preset
        assertTrue(parsed.regexScripts.isEmpty())
    }
}
