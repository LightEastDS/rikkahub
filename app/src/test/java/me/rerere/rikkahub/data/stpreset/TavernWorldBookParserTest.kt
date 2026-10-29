package me.rerere.rikkahub.data.stpreset

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernWorldBookParserTest {

    @Test
    fun `parses character card embedded character_book with array entries`() {
        val json = """
            {
              "name": "It's My Go!!!!!",
              "description": "Band worldbook",
              "entries": [
                {
                  "id": 0,
                  "keys": ["青森峰", "青森峰男子学校", "男校"],
                  "comment": "日峰（羽丘）",
                  "content": "日峰男子学校位于新宿区",
                  "constant": true,
                  "insertion_order": 4,
                  "enabled": true,
                  "position": "before_char",
                  "use_regex": true,
                  "extensions": {
                    "position": 0,
                    "depth": 4,
                    "role": 0
                  }
                },
                {
                  "id": 4,
                  "keys": ["睦", "若叶", "默"],
                  "comment": "若叶睦",
                  "content": "若叶睦，十六岁，吉他手",
                  "constant": false,
                  "insertion_order": 14,
                  "enabled": true,
                  "position": "after_char",
                  "use_regex": true,
                  "extensions": {
                    "position": 1,
                    "depth": 4
                  }
                },
                {
                  "id": 10,
                  "key": ["深度"],
                  "name": "Depth Entry",
                  "content": "深层设定",
                  "constant": false,
                  "order": 50,
                  "enabled": true,
                  "position": "at_depth",
                  "depth": 3
                },
                {
                  "id": 99,
                  "keys": ["空"],
                  "comment": "空白内容",
                  "content": "   ",
                  "enabled": true
                }
              ]
            }
        """.trimIndent()

        val jsonObject = Json.parseToJsonElement(json).jsonObject
        val lorebook = TavernWorldBookParser.parse(jsonObject, "Fallback Worldbook")

        assertNotNull(lorebook)
        assertEquals("It's My Go!!!!!", lorebook!!.name)
        assertEquals("Band worldbook", lorebook.description)
        assertTrue(lorebook.enabled)
        // 4 entries, but one has blank content -> 3 valid entries
        assertEquals(3, lorebook.entries.size)

        // Entry 0
        val entry0 = lorebook.entries[0]
        assertEquals("日峰（羽丘）", entry0.name)
        assertEquals("日峰男子学校位于新宿区", entry0.content)
        assertTrue(entry0.constantActive)
        assertTrue(entry0.useRegex)
        assertEquals(4, entry0.priority)
        assertEquals(InjectionPosition.BEFORE_SYSTEM_PROMPT, entry0.position)
        assertEquals(listOf("青森峰", "青森峰男子学校", "男校"), entry0.keywords)

        // Entry 1 (id 4)
        val entry1 = lorebook.entries[1]
        assertEquals("若叶睦", entry1.name)
        assertEquals(false, entry1.constantActive)
        assertTrue(entry1.useRegex)
        assertEquals(14, entry1.priority)
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, entry1.position)
        assertEquals(listOf("睦", "若叶", "默"), entry1.keywords)

        // Entry 2 (id 10)
        val entry2 = lorebook.entries[2]
        assertEquals("Depth Entry", entry2.name)
        assertEquals(InjectionPosition.AT_DEPTH, entry2.position)
        assertEquals(3, entry2.injectDepth)
        assertEquals(50, entry2.priority)
    }

    @Test
    fun `parses standalone SillyTavern worldbook with map entries`() {
        val json = """
            {
              "name": "Standalone Lorebook",
              "entries": {
                "0": {
                  "key": ["magic", "spell"],
                  "comment": "Magic System",
                  "content": "Rules of magic",
                  "constant": false,
                  "position": 0,
                  "order": 100,
                  "disable": false,
                  "depth": 4
                },
                "1": {
                  "keys": "lore, history",
                  "comment": "History",
                  "content": "Ancient war",
                  "constant": true,
                  "position": 1,
                  "order": 200,
                  "enabled": true
                }
              }
            }
        """.trimIndent()

        val lorebook = TavernWorldBookParser.parseFromJsonString(json)
        assertNotNull(lorebook)
        assertEquals("Standalone Lorebook", lorebook!!.name)
        assertEquals(2, lorebook.entries.size)

        val entry0 = lorebook.entries[0]
        assertEquals("Magic System", entry0.name)
        assertEquals(listOf("magic", "spell"), entry0.keywords)
        assertEquals(InjectionPosition.BEFORE_SYSTEM_PROMPT, entry0.position)
        assertTrue(entry0.enabled)

        val entry1 = lorebook.entries[1]
        assertEquals("History", entry1.name)
        assertEquals(listOf("lore", "history"), entry1.keywords)
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, entry1.position)
        assertTrue(entry1.constantActive)
    }

    @Test
    fun `parseFromJsonString extracts character_book from character card json`() {
        val cardJson = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "Tomori",
                "character_book": {
                  "name": "CRYCHIC",
                  "entries": [
                    {
                      "keys": ["春日影"],
                      "comment": "Haruhikage",
                      "content": "Why did you play Haruhikage?"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val lorebook = TavernWorldBookParser.parseFromJsonString(cardJson)
        assertNotNull(lorebook)
        assertEquals("CRYCHIC", lorebook!!.name)
        assertEquals(1, lorebook.entries.size)
        assertEquals("Haruhikage", lorebook.entries[0].name)
    }

    @Test
    fun `returns null when character_book has no valid entries`() {
        val emptyBook = """
            {
              "name": "Empty Book",
              "entries": []
            }
        """.trimIndent()
        assertNull(TavernWorldBookParser.parseFromJsonString(emptyBook))

        val allBlank = """
            {
              "entries": [
                { "keys": ["a"], "content": "   " }
              ]
            }
        """.trimIndent()
        assertNull(TavernWorldBookParser.parseFromJsonString(allBlank))
    }
}
