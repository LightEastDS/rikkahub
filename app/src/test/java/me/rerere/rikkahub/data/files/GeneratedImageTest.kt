package me.rerere.rikkahub.data.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.io.encoding.Base64

class GeneratedImageTest {
    @Test
    fun `empty streaming placeholder is not decoded as an image`() {
        assertNull(decodeGeneratedImage("data:image/png;base64,"))
        assertNull(decodeGeneratedImage("data:image/jpeg;base64, \n"))
    }

    @Test
    fun `complete image bytes survive Base64 decoding`() {
        val bytes = ByteArray(1024 * 1024) { (it % 256).toByte() }
        assertArrayEquals(bytes, decodeGeneratedImage("data:image/png;base64,${Base64.encode(bytes)}"))
    }

    @Test
    fun `invalid data has an actionable error without including the payload`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            decodeGeneratedImage("data:image/png;base64,invalid!payload")
        }
        assertEquals("Generated image contains invalid Base64 data", error.message)
        assertThrows(IllegalArgumentException::class.java) {
            decodeGeneratedImage("data:image/png,not-base64")
        }
    }
}
