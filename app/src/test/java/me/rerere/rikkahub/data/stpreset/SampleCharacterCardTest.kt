package me.rerere.rikkahub.data.stpreset

import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class SampleCharacterCardTest {

    @Test
    fun `extracts and parses worldbook from sample character card png`() {
        // The user provided the sample card at F:\Project\示例角色卡.png
        // (which is ..\示例角色卡.png relative to rikkahub root)
        val fileCandidates = listOf(
            File("F:/Project/示例角色卡.png"),
            File("../示例角色卡.png"),
            File("../../示例角色卡.png"),
        )
        val file = fileCandidates.firstOrNull { it.exists() }
        if (file == null) {
            println("Sample file not found, skipping test")
            return
        }

        val metadata = ImageMetadataReader.readMetadata(file)
        val pngDirectory = metadata.getDirectoriesOfType(PngDirectory::class.java)
            .firstOrNull { directory ->
                if (directory.pngChunkType != PngChunkType.tEXt) return@firstOrNull false
                val text = directory.getString(PngDirectory.TAG_TEXTUAL_DATA) ?: return@firstOrNull false
                text.startsWith("[chara:") || text.startsWith("[ccv3:")
            }
        assertNotNull("Should find chara or ccv3 chunk in sample character card", pngDirectory)

        val rawText = pngDirectory!!.getString(PngDirectory.TAG_TEXTUAL_DATA)
        val regex = Regex("""\[(?:chara|ccv3):\s*(.+?)]""")
        val base64Data = regex.find(rawText)?.groupValues?.get(1)
        assertNotNull("Should extract base64 metadata", base64Data)

        val jsonString = String(Base64.getDecoder().decode(base64Data))
        val lorebook = TavernWorldBookParser.parseFromJsonString(jsonString)

        assertNotNull("Worldbook should be parsed from sample character card", lorebook)
        assertEquals("It's My Go!!!!!", lorebook!!.name)
        assertEquals(26, lorebook.entries.size)

        // Check entry 0
        val entry0 = lorebook.entries[0]
        assertEquals("日峰（羽丘）", entry0.name)
        assertTrue(entry0.constantActive)
        assertEquals(InjectionPosition.BEFORE_SYSTEM_PROMPT, entry0.position)
        assertEquals(listOf("青森峰", "青森峰男子学校", "男校"), entry0.keywords)

        // Check entry 4 (Mutsumi)
        val entry4 = lorebook.entries[4]
        assertEquals("若叶睦", entry4.name)
        assertEquals(false, entry4.constantActive)
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, entry4.position)
        assertEquals(listOf("睦", "若叶", "默"), entry4.keywords)
        assertTrue(entry4.content.contains("若叶睦"))
    }
}
