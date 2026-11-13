package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.provider.stream.SseEvent
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseApiImageGenerationTest {
    @Test
    fun `final image replaces partial previews and terminal response does not duplicate it`() {
        val decoder = ResponseApiStreamDecoder()
        val final = imageItem(data = "ZmluYWw=", format = "webp")
        val chunks = buildList {
            addAll(decoder.decode(itemEvent("response.output_item.added", imageItem())))
            repeat(2) { index ->
                addAll(decoder.decode(buildJsonObject {
                    put("type", "response.image_generation_call.partial_image")
                    put("item_id", "ig_1")
                    put("partial_image_index", index)
                    put("partial_image_b64", "cHJldmlldw==")
                    put("output_format", "jpeg")
                }))
            }
            // This event announces completion but does not contain the image bytes.
            addAll(decoder.decode(buildJsonObject {
                put("type", "response.image_generation_call.completed")
                put("item_id", "ig_1")
            }))
            addAll(decoder.decode(itemEvent("response.output_item.done", final)))
            addAll(decoder.decode(completedEvent(final)))
        }

        assertEquals(listOf("data:image/webp;base64,ZmluYWw="), imageUrls(chunks))
        assertEquals(1, chunks.count { it is StreamChunk.ImageStart })
        assertEquals(1, chunks.count { it is StreamChunk.ImageEnd })
        assertTrue(decoder.onClosed().isEmpty())
    }

    @Test
    fun `terminal response recovers a final image missing from output item done`() {
        val decoder = ResponseApiStreamDecoder()
        val chunks = decoder.decode(itemEvent("response.output_item.added", imageItem())) +
            decoder.decode(itemEvent("response.output_item.done", imageItem())) +
            decoder.decode(completedEvent(imageItem(data = "ZmluYWw=")))

        assertEquals(listOf("data:image/png;base64,ZmluYWw="), imageUrls(chunks))
        assertEquals(1, chunks.count { it is StreamChunk.ImageStart })
        assertEquals(1, chunks.count { it is StreamChunk.ImageEnd })
    }

    @Test
    fun `terminal only response supports multiple images`() {
        val decoder = ResponseApiStreamDecoder()
        val chunks = decoder.decode(completedEvent(
            imageItem(id = "ig_1", data = "b25l"),
            imageItem(id = "ig_2", data = "dHdv", format = "jpeg"),
        ))

        assertEquals(listOf("data:image/png;base64,b25l", "data:image/jpeg;base64,dHdv"), imageUrls(chunks))
    }

    @Test
    fun `non streaming response returns image parts with the supplied format`() {
        val api = ResponseAPI(OkHttpClient())
        val result = api.parseResponseOutput(buildJsonObject {
            put("output", buildJsonArray {
                add(imageItem(data = "cG5n"))
                add(imageItem(data = "anBlZw==", format = "jpeg"))
                add(imageItem(data = "d2VicA==", format = "webp"))
                add(imageItem())
            })
        })

        assertEquals(
            listOf("data:image/png;base64,cG5n", "data:image/jpeg;base64,anBlZw==", "data:image/webp;base64,d2VicA=="),
            result.message.parts.filterIsInstance<UIMessagePart.Image>().map { it.url },
        )
    }

    private fun imageUrls(chunks: List<StreamChunk>): List<String> {
        val handler = StreamChunkHandler()
        return chunks.fold(listOf(UIMessage.user("Draw an image"))) { messages, chunk ->
            handler.handle(messages, chunk)
        }.last().parts.filterIsInstance<UIMessagePart.Image>().map { it.url }
    }

    private fun imageItem(id: String = "ig_1", data: String? = null, format: String = "png") = buildJsonObject {
        put("id", id)
        put("type", "image_generation_call")
        put("status", if (data == null) "in_progress" else "completed")
        put("result", data?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
        put("output_format", format)
    }

    private fun itemEvent(type: String, item: JsonObject) = buildJsonObject {
        put("type", type)
        put("item", item)
    }

    private fun completedEvent(vararg items: JsonObject) = buildJsonObject {
        put("type", "response.completed")
        put("response", buildJsonObject {
            put("status", "completed")
            put("output", buildJsonArray { items.forEach { add(it) } })
        })
    }

    private fun ResponseApiStreamDecoder.decode(payload: JsonObject): List<StreamChunk> = accept(SseEvent(
        event = payload["type"]?.jsonPrimitive?.content,
        data = json.encodeToString(payload),
    )).chunks
}
