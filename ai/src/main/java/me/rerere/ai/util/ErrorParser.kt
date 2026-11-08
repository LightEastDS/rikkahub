package me.rerere.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

class HttpException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

private val HTML_TITLE_REGEX = Regex(
    """<title[^>]*>(.*?)</title>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val HTML_H1_REGEX = Regex(
    """<h1[^>]*>(.*?)</h1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val HTML_CENTER_SERVER_REGEX = Regex(
    """<hr[^>]*>\s*<center[^>]*>(.*?)</center>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
private val WHITESPACE_REGEX = Regex("""\s+""")
private val HTTP_STATUS_IN_TEXT_REGEX = Regex("""\b([45]\d{2})\b""")

fun JsonElement.parseErrorDetail(statusCode: Int? = null): HttpException {
    return when (this) {
        is JsonObject -> {
            // 尝试获取常见的错误字段
            val errorFields = listOf("error", "detail", "message", "description")

            // 查找第一个存在的错误字段
            val foundField = errorFields.firstOrNull { this[it] != null }

            if (foundField != null) {
                // 递归解析找到的字段值
                this[foundField]!!.parseErrorDetail(statusCode)
            } else {
                // 如果没有找到任何错误字段，序列化整个对象
                val raw = Json.encodeToString(JsonElement.serializer(), this)
                HttpException(prefixWithStatus(raw, statusCode), statusCode = statusCode)
            }
        }

        is JsonArray -> {
            if (this.isEmpty()) {
                HttpException(prefixWithStatus("Unknown error: Empty JSON array", statusCode), statusCode = statusCode)
            } else {
                // 递归解析数组的第一个元素
                this.first().parseErrorDetail(statusCode)
            }
        }

        is JsonPrimitive -> {
            val content = this.jsonPrimitive.content
            if (looksLikeHtml(content)) {
                parseHtmlError(content, statusCode)
            } else {
                HttpException(prefixWithStatus(content, statusCode), statusCode = statusCode)
            }
        }

        else -> {
            val raw = Json.encodeToString(JsonElement.serializer(), this)
            HttpException(prefixWithStatus(raw, statusCode), statusCode = statusCode)
        }
    }
}

/**
 * 容错解析 HTTP 错误响应（支持 JSON、网关 HTML 报错页如 502/503/504、纯文本等），
 * 避免网关返回 HTML 时因 `Json.parseToJsonElement` 抛出 `JsonDecodingException` 而吞掉真实状态码与错误原因。
 */
fun parseHttpError(
    response: Response?,
    bodyRaw: String? = response?.body?.stringSafe(),
    fallback: Throwable? = null,
): Throwable {
    val statusCode = response?.code?.takeIf { it >= 400 }
    val statusMessage = response?.message?.takeIf { it.isNotBlank() }
    return parseErrorBody(
        bodyRaw = bodyRaw,
        statusCode = statusCode,
        statusMessage = statusMessage,
        fallback = fallback,
    )
}

fun parseErrorBody(
    bodyRaw: String?,
    statusCode: Int? = null,
    statusMessage: String? = null,
    fallback: Throwable? = null,
): Throwable {
    val trimmed = bodyRaw?.trim().orEmpty()
    if (trimmed.isNotEmpty()) {
        if (looksLikeHtml(trimmed)) {
            return parseHtmlError(trimmed, statusCode, statusMessage, fallback)
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val parsed = runCatching { json.parseToJsonElement(trimmed).parseErrorDetail(statusCode) }.getOrNull()
            if (parsed != null) return parsed
        }
        val cleanSnippet = trimmed
            .replace(WHITESPACE_REGEX, " ")
            .take(300)
        val effectiveCode = statusCode ?: extractStatusCodeFromText(cleanSnippet)
        val hint = gatewayStatusHint(effectiveCode)
        val baseMsg = prefixWithStatus(cleanSnippet, effectiveCode, statusMessage)
        val finalMsg = if (hint != null && !baseMsg.contains(hint)) "$baseMsg ($hint)" else baseMsg
        return HttpException(finalMsg, statusCode = effectiveCode, cause = fallback)
    }

    if (statusCode != null) {
        val statusHeadline = buildString {
            append("HTTP ").append(statusCode)
            if (!statusMessage.isNullOrBlank()) {
                append(" ").append(statusMessage)
            } else {
                defaultReasonPhrase(statusCode)?.let { append(" ").append(it) }
            }
        }
        val hint = gatewayStatusHint(statusCode)
        val finalMsg = if (hint != null) "$statusHeadline ($hint)" else statusHeadline
        return HttpException(finalMsg, statusCode = statusCode, cause = fallback)
    }

    return fallback ?: HttpException("Stream request failed")
}

/**
 * 解析预期为 JSON 的响应体；若上游网关返回了 HTML 错误页（有时甚至伴随 HTTP 200）或非 JSON 文本，
 * 则抛出可读的 [HttpException] 而不是底层 `JsonDecodingException`。
 */
fun parseJsonOrThrowHttpError(
    bodyRaw: String,
    statusCode: Int? = null,
): JsonElement {
    val trimmed = bodyRaw.trim()
    if (trimmed.isEmpty()) {
        throw parseErrorBody(bodyRaw, statusCode)
    }
    if (looksLikeHtml(trimmed)) {
        throw parseHtmlError(trimmed, statusCode)
    }
    return try {
        json.parseToJsonElement(trimmed)
    } catch (e: Throwable) {
        throw parseErrorBody(trimmed, statusCode, fallback = e)
    }
}

internal fun looksLikeHtml(text: String): Boolean {
    val trimmed = text.trimStart()
    if (trimmed.startsWith("<")) {
        val head = trimmed.take(256).lowercase()
        if (head.startsWith("<!doctype") ||
            head.startsWith("<html") ||
            head.startsWith("<head") ||
            head.startsWith("<title") ||
            head.startsWith("<body") ||
            head.startsWith("<center") ||
            head.startsWith("<h1") ||
            head.contains("<html") ||
            head.contains("<title>") ||
            head.contains("<h1>")
        ) {
            return true
        }
    }
    return false
}

private fun parseHtmlError(
    html: String,
    statusCode: Int? = null,
    statusMessage: String? = null,
    fallback: Throwable? = null,
): HttpException {
    val title = HTML_TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
    val h1 = HTML_H1_REGEX.find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
    val server = HTML_CENTER_SERVER_REGEX.find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
        ?.takeIf { it.isNotBlank() && it.length <= 64 }

    val primary = when {
        !title.isNullOrBlank() && !h1.isNullOrBlank() && !title.equals(h1, ignoreCase = true) ->
            if (title.contains(h1, ignoreCase = true)) title else "$title - $h1"
        !title.isNullOrBlank() -> title
        !h1.isNullOrBlank() -> h1
        else -> html.cleanHtmlText().take(200).ifBlank { statusMessage ?: "Gateway HTML Error" }
    }

    val effectiveCode = statusCode ?: extractStatusCodeFromText(primary)
    val baseHeadline = prefixWithStatus(primary, effectiveCode, statusMessage)
    val withServer = if (!server.isNullOrBlank() && !baseHeadline.contains(server, ignoreCase = true)) {
        "$baseHeadline [$server]"
    } else {
        baseHeadline
    }
    val hint = gatewayStatusHint(effectiveCode)
    val finalMessage = if (hint != null && !withServer.contains(hint)) {
        "$withServer ($hint)"
    } else {
        withServer
    }
    return HttpException(finalMessage, statusCode = effectiveCode, cause = fallback)
}

private fun String.cleanHtmlText(): String = this
    .replace(HTML_TAG_REGEX, " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(WHITESPACE_REGEX, " ")
    .trim()

private fun extractStatusCodeFromText(text: String): Int? =
    HTTP_STATUS_IN_TEXT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun prefixWithStatus(
    message: String,
    statusCode: Int?,
    statusMessage: String? = null,
): String {
    val trimmed = message.trim()
    if (statusCode == null) return trimmed
    val codeStr = statusCode.toString()
    return if (trimmed.startsWith("HTTP $codeStr", ignoreCase = true)) {
        trimmed
    } else if (trimmed.startsWith(codeStr)) {
        "HTTP $trimmed"
    } else if (!statusMessage.isNullOrBlank() && !trimmed.contains(statusMessage, ignoreCase = true)) {
        "HTTP $statusCode $statusMessage: $trimmed"
    } else {
        "HTTP $statusCode: $trimmed"
    }
}

private fun defaultReasonPhrase(statusCode: Int): String? = when (statusCode) {
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    408 -> "Request Timeout"
    429 -> "Too Many Requests"
    500 -> "Internal Server Error"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    504 -> "Gateway Time-out"
    522 -> "Connection Timed Out"
    524 -> "A Timeout Occurred"
    else -> null
}

private fun gatewayStatusHint(statusCode: Int?): String? = when (statusCode) {
    408, 504, 522, 524 -> "网关响应超时，上游模型响应过慢或中转站连接超时"
    502 -> "网关错误，上游服务无响应或中转站节点异常"
    503 -> "服务暂时不可用，上游节点过载或维护中"
    429 -> "请求频率超限或额度不足"
    else -> null
}

