package me.rerere.ai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ErrorParserTest {
    @Test
    fun `parseErrorBody handles OpenResty 504 HTML without throwing JsonDecodingException`() {
        // Exact HTML structure where offset 9 is `<head>` after `<html>\r\n`
        val openResty504Html = "<html>\r\n" +
            "<head><title>504 Gateway Time-out</title></head>\r\n" +
            "<body>\r\n" +
            "<center><h1>504 Gateway Time-out</h1></center>\r\n" +
            "<hr><center>openresty</center>\r\n" +
            "</body>\r\n" +
            "</html>\r\n"

        val parsed = parseErrorBody(openResty504Html, statusCode = 504).message.orEmpty()
        assertTrue("Should contain 504 Gateway Time-out: $parsed", parsed.contains("504 Gateway Time-out"))
        assertTrue("Should identify openresty gateway: $parsed", parsed.contains("[openresty]"))
        assertTrue("Should include human-readable timeout hint: $parsed", parsed.contains("网关响应超时"))
    }

    @Test
    fun `parseErrorBody extracts JSON error message`() {
        val jsonBody = """{"error":{"message":"Rate limit reached for model","type":"rate_limit_error","code":429}}"""
        val parsed = parseErrorBody(jsonBody, statusCode = 429).message.orEmpty()
        assertTrue("Should contain rate limit message: $parsed", parsed.contains("Rate limit reached for model"))
    }

    @Test
    fun `parseErrorBody extracts Google JSON array error message`() {
        val jsonArrayBody = """[{"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}]"""
        val parsed = parseErrorBody(jsonArrayBody, statusCode = 503).message.orEmpty()
        assertTrue("Should contain overloaded message: $parsed", parsed.contains("The model is overloaded. Please try again later."))
    }

    @Test
    fun `parseJsonOrThrowHttpError converts HTML gateway page into HttpException instead of JsonDecodingException`() {
        val htmlBody = "<html>\r\n<head><title>504 Gateway Time-out</title></head><body><center><h1>504 Gateway Time-out</h1></center><hr><center>nginx/1.24.0</center></body></html>"
        try {
            parseJsonOrThrowHttpError(htmlBody, statusCode = 504)
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(504, e.statusCode)
            val msg = e.message.orEmpty()
            assertTrue("Message should contain 504 Gateway Time-out: $msg", msg.contains("504 Gateway Time-out"))
            assertTrue("Message should contain [nginx/1.24.0]: $msg", msg.contains("[nginx/1.24.0]"))
        }
    }
}
