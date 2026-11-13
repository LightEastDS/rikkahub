package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.stream.SseEvent
import me.rerere.ai.ui.GoogleThoughtMetadata
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.metadataAs
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleThoughtSignatureParsingTest {
    @Test
    fun `streaming decoder handles standalone thoughtSignature after text`() {
        val decoder = GoogleStreamDecoder(responseId = "resp-1", model = "gemini-2.5-pro")
        val handler = StreamChunkHandler(Model(modelId = "gemini-2.5-pro", displayName = "Gemini"))
        var messages = listOf(UIMessage.user("hi"))

        val chunk1 = """{"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}"""
        val chunk2 = """{"candidates":[{"content":{"parts":[{"thoughtSignature":"sig-text-1"}]},"finishReason":"STOP"}]}"""

        for (raw in listOf(chunk1, chunk2)) {
            decoder.accept(SseEvent(data = raw)).chunks.forEach { messages = handler.handle(messages, it) }
        }
        decoder.onClosed().forEach { messages = handler.handle(messages, it) }

        val parts = messages.last().parts
        assertEquals(1, parts.size)
        val text = parts.single() as UIMessagePart.Text
        assertEquals("Hello", text.text)
        assertEquals("sig-text-1", text.metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)
    }

    @Test
    fun `streaming decoder handles standalone thoughtSignature after functionCall and standalone thought part`() {
        val decoder = GoogleStreamDecoder(responseId = "resp-2", model = "gemini-2.5-pro")
        val handler = StreamChunkHandler(Model(modelId = "gemini-2.5-pro", displayName = "Gemini"))
        var messages = listOf(UIMessage.user("search"))

        val chunk1 = """{"candidates":[{"content":{"parts":[{"thought":true,"text":"plan"},{"thought":true,"thoughtSignature":"sig-reason"}]}}]}"""
        val chunk2 = """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"lookup","args":{"q":"x"}}},{"thoughtSignature":"sig-tool-1"}]},"finishReason":"STOP"}]}"""

        for (raw in listOf(chunk1, chunk2)) {
            decoder.accept(SseEvent(data = raw)).chunks.forEach { messages = handler.handle(messages, it) }
        }
        decoder.onClosed().forEach { messages = handler.handle(messages, it) }

        val parts = messages.last().parts
        assertEquals(2, parts.size)
        val reasoning = parts[0] as UIMessagePart.Reasoning
        assertEquals("plan", reasoning.reasoning)
        assertEquals("sig-reason", reasoning.metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)

        val tool = parts[1] as UIMessagePart.Tool
        assertEquals("lookup", tool.toolName)
        assertEquals("sig-tool-1", tool.metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `non-streaming parseMessageParts merges or preserves standalone thoughtSignature parts without throwing`() {
        val provider = GoogleProvider(OkHttpClient())
        val parseMessageParts = GoogleProvider::class.java.getDeclaredMethod("parseMessageParts", JsonArray::class.java)
        parseMessageParts.isAccessible = true

        val partsWithTrailingSignature = buildJsonArray {
            add(buildJsonObject { put("text", "Answer") })
            add(buildJsonObject { put("thoughtSignature", "sig-nonstream") })
        }
        val parsed = parseMessageParts.invoke(provider, partsWithTrailingSignature) as List<UIMessagePart>
        assertEquals(1, parsed.size)
        val text = parsed.single() as UIMessagePart.Text
        assertEquals("Answer", text.text)
        assertEquals("sig-nonstream", text.metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)

        val partsSignatureOnly = buildJsonArray {
            add(buildJsonObject { put("thoughtSignature", "sig-only") })
        }
        val parsedOnly = parseMessageParts.invoke(provider, partsSignatureOnly) as List<UIMessagePart>
        assertTrue(parsedOnly.single() is UIMessagePart.Text)
        assertEquals("sig-only", parsedOnly.single().metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)
    }
}
