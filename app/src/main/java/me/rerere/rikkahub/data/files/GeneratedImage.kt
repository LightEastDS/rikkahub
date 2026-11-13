package me.rerere.rikkahub.data.files

import kotlin.io.encoding.Base64

/** Returns null for a streaming placeholder, without trying to decode it as a bitmap. */
internal fun decodeGeneratedImage(dataUrl: String): ByteArray? {
    val separator = dataUrl.indexOf(',')
    require(
        separator >= 0 && dataUrl.startsWith("data:image/") &&
            dataUrl.substring(0, separator).endsWith(";base64")
    ) {
        "Generated image must be a Base64 image data URL"
    }
    val data = dataUrl.substring(separator + 1)
    if (data.isBlank()) return null
    return try {
        Base64.decode(data)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Generated image contains invalid Base64 data", e)
    }
}
