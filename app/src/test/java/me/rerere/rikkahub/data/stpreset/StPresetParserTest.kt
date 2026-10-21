package me.rerere.rikkahub.data.stpreset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StPresetParserTest {
    @Test
    fun `parses chat completion preset and uses prompt_order enabled state`() {
        val json = """
            {
              "name": "Test Preset",
              "temperature": 0.7,
              "top_p": 0.9,
              "squash_system_messages": true,
              "prompts": [
                {"identifier": "main", "name": "Main", "role": "system", "content": "", "marker": true, "system_prompt": true, "enabled": false},
                {"identifier": "aa", "name": "A", "role": "system", "content": "Hello {{char}}", "enabled": false},
                {"identifier": "chatHistory", "name": "History", "marker": true, "content": ""}
              ],
              "prompt_order": [
                {"character_id": 100001, "order": [
                  {"identifier": "main", "enabled": true},
                  {"identifier": "aa", "enabled": true},
                  {"identifier": "chatHistory", "enabled": true}
                ]}
              ],
              "extensions": {"regex_scripts": [
                {"scriptName": "wrap", "findRegex": "/x/g", "replaceString": "y", "placement": [1], "promptOnly": true}
              ]}
            }
        """.trimIndent()

        val result = StPresetParser.parse(json, "fallback")
        assertEquals("Test Preset", result.preset.name)
        assertEquals(3, result.preset.prompts.size)
        assertEquals(3, result.report.enabledEntries)
        assertEquals(2, result.report.enabledMarkerPrompts)
        assertEquals(1, result.report.enabledRegexScripts)
        assertEquals(0.7f, result.preset.settings.temperature)
        assertTrue(result.preset.settings.squashSystemMessages)
        assertTrue(result.preset.prompts[1].content.contains("{{char}}"))
    }

    @Test
    fun `warns about prompt_order entries without definitions`() {
        val json = """
            {
              "prompts": [{"identifier": "a", "content": "x"}],
              "prompt_order": [{"character_id": 100001, "order": [
                {"identifier": "a", "enabled": true},
                {"identifier": "missing", "enabled": true}
              ]}]
            }
        """.trimIndent()
        val result = StPresetParser.parse(json)
        assertTrue(result.preset.warnings.any { it.contains("missing") })
    }

    @Test(expected = StPresetParseException::class)
    fun `rejects world info files`() {
        StPresetParser.parse("""{"entries": {}}""")
    }

    @Test(expected = StPresetParseException::class)
    fun `rejects text completion presets`() {
        StPresetParser.parse("""{"temp": 0.8, "rep_pen": 1.1, "prompts": ["a", "b"]}""")
    }
}
