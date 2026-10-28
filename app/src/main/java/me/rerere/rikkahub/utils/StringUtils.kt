package me.rerere.rikkahub.utils

import org.apache.commons.text.StringEscapeUtils
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "UTF-8")
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Encode(): String {
    return Base64.encode(this.toByteArray())
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Decode(): String {
    return String(Base64.decode(this))
}

fun String.escapeHtml(): String {
    return StringEscapeUtils.escapeHtml4(this)
}

fun String.unescapeHtml(): String {
    return StringEscapeUtils.unescapeHtml4(this)
}

fun Number.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

fun String.applyPlaceholders(
    vararg placeholders: Pair<String, String>,
): String {
    var result = this
    for ((placeholder, replacement) in placeholders) {
        result = result.replace("{$placeholder}", replacement)
    }
    return result
}

fun Long.fileSizeToString(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val precision = when {
        value >= 100 -> 0
        value >= 10 -> 1
        else -> 2
    }
    return "%.${precision}f %s".format(value, units[unitIndex])
}

fun Int.formatNumber(): String {
    val absValue = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""

    return when {
        absValue < 1000 -> this.toString()
        absValue < 1000000 -> {
            val value = absValue / 1000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}K"
            } else {
                "$sign${value.toFixed(1)}K"
            }
        }

        absValue < 1000000000 -> {
            val value = absValue / 1000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}M"
            } else {
                "$sign${value.toFixed(1)}M"
            }
        }

        else -> {
            val value = absValue / 1000000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}B"
            } else {
                "$sign${value.toFixed(1)}B"
            }
        }
    }
}

fun Float.toFixed(digits: Int = 0) = "%.${digits}f".format(this)
fun Double.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

/**
 * 提取字符串中所有引号内的内容
 * 支持多种引号类型：英文双引号 "..."、英文单引号 '...'、中文双引号 "..."、中文单引号 '...'、直角引号「…」、白直角引号『…』
 * @return 所有引号内内容的列表
 */
fun String.extractQuotedContent(): List<String> {
    val result = mutableListOf<String>()
    // 匹配多种引号类型
    val patterns = listOf(
        "\u201C([^\u201D]*?)\u201D",  // 中文双引号
        "\u2018([^\u2019]*?)\u2019",  // 中文单引号
        """"([^"]*?)"""",  // 英文双引号
        """'([^']*?)'""",  // 英文单引号
        """「([^」]*?)」""",           // 直角引号
        """『([^』]*?)』""",           // 白直角引号
    )
    for (pattern in patterns) {
        val regex = Regex(pattern)
        regex.findAll(this).forEach { matchResult ->
            val content = matchResult.groupValues[1]
            if (content.isNotBlank()) {
                result.add(content)
            }
        }
    }
    return result
}

/**
 * 提取字符串中所有引号内的内容并合并为一个字符串
 * @param separator 分隔符，默认为换行
 * @return 合并后的字符串，如果没有引号内容则返回 null
 */
fun String.extractQuotedContentAsText(separator: String = "\n"): String? {
    val contents = extractQuotedContent()
    return if (contents.isNotEmpty()) {
        contents.joinToString(separator)
    } else {
        null
    }
}

/**
 * 移除字符串中所有括号内的内容
 * 支持英文括号 (...) 和中文括号（...）
 * @return 移除括号内容后的字符串，如果全被移除则返回 null
 */
fun String.removeBracketedContent(): String? {
    val pattern = """\([^)]*?\)|（[^）]*?）""".toRegex()
    val result = pattern.replace(this, "").trim()
    return result.ifBlank { null }
}

/**
 * 统计文本字数。
 *
 * 智能混合统计规则：
 * 1. 汉字（中文汉字、日文汉字等）：按单字计入（每个汉字计 1 字）。
 * 2. 日语假名（平假名、片假名、长音符等）：按单字计入（每个假名计 1 字）。
 * 3. 英语/西方单词与数字：按词计入（连续字母/数字序列计 1 词；缩写内部连字符/撇号如 don't、it's 作为同一个词整体计入）。
 * 4. 标点符号与特殊符号（中文标点、日文标点、英文标点及常用符号）：按个计入（每个符号计 1）。
 * 5. 空格与空白字符（英文空格、制表符、换行符、全角空格 \u3000、零宽空格等）：完全不计入字数。
 * 6. 组合字符/变音符号与格式控制符：不重复计入。
 */
fun String.countWords(): Int {
    if (isEmpty()) return 0
    var count = 0
    var i = 0
    val length = this.length

    while (i < length) {
        val cp = codePointAt(i)
        val charCount = Character.charCount(cp)

        when {
            // 1. 空白字符：完全忽略，不计入字数
            isWordCountWhitespace(cp) -> {
                i += charCount
            }

            // 2. 忽略的非间距修饰符/格式控制符（如变音符号、ZWJ、零宽连接符等）
            isWordCountIgnoredMark(cp) -> {
                i += charCount
            }

            // 3. 汉字（CJK 统一表意文字及扩展）：每个字符计 1
            isWordCountHan(cp) -> {
                count++
                i += charCount
            }

            // 4. 日文假名（平假名、片假名、假名扩展）：每个字符计 1
            isWordCountKana(cp) -> {
                count++
                i += charCount
            }

            // 5. 西方/字母数字单词起始：完整读取连续单词（包括 don't、it's 这类单词内撇号）
            isWordCountWordChar(cp) -> {
                i = consumeWordToken(this, i)
                count++
            }

            // 6. 标点符号与通用符号（包含全角/半角标点、数学/货币/修饰符号等）：计 1
            isWordCountPunctuationOrSymbol(cp) -> {
                count++
                i += charCount
            }

            // 7. 其他非空白有义字符（如韩文音节等）：计 1
            else -> {
                count++
                i += charCount
            }
        }
    }

    return count
}

private fun isWordCountWhitespace(cp: Int): Boolean {
    if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return true
    return when (cp) {
        0x0009, // \t
        0x000A, // \n
        0x000B, // \v
        0x000C, // \f
        0x000D, // \r
        0x0020, // space
        0x0085, // NEL
        0x00A0, // NBSP
        0x1680,
        0x180E,
        in 0x2000..0x200A, // En quad .. Hair space
        0x200B, // Zero width space
        0x2028, // Line separator
        0x2029, // Paragraph separator
        0x202F, // Narrow NBSP
        0x205F, // Medium math space
        0x3000, // 全角空格 (Ideographic space)
        0xFEFF  // BOM / ZWNBSP
        -> true
        else -> false
    }
}

private fun isWordCountIgnoredMark(cp: Int): Boolean {
    val type = Character.getType(cp)
    return type == Character.NON_SPACING_MARK.toInt() ||
           type == Character.COMBINING_SPACING_MARK.toInt() ||
           type == Character.ENCLOSING_MARK.toInt() ||
           type == Character.FORMAT.toInt() ||
           (type == Character.CONTROL.toInt() && !isWordCountWhitespace(cp))
}

private fun isWordCountHan(cp: Int): Boolean {
    val script = try {
        Character.UnicodeScript.of(cp)
    } catch (_: Exception) {
        null
    }
    if (script == Character.UnicodeScript.HAN) return true

    return cp in 0x4E00..0x9FFF ||      // CJK Unified Ideographs
           cp in 0x3400..0x4DBF ||      // Extension A
           cp in 0x20000..0x2A6DF ||    // Extension B
           cp in 0x2A700..0x2B739 ||    // Extension C
           cp in 0x2B740..0x2B81D ||    // Extension D
           cp in 0x2B820..0x2CEAD ||    // Extension E
           cp in 0x2CEB0..0x2EBE0 ||    // Extension F
           cp in 0x30000..0x3134A ||    // Extension G
           cp in 0x31350..0x323AF ||    // Extension H
           cp in 0x2EBF0..0x2EE5D ||    // Extension I
           cp in 0xF900..0xFAFF ||      // CJK Compatibility Ideographs
           cp in 0x2F800..0x2FA1F ||    // CJK Compatibility Ideographs Supplement
           cp in 0x2E80..0x2FD5 ||      // CJK Radicals / Kangxi Radicals
           cp == 0x3005 ||              // 々 (Ideographic iteration mark)
           cp == 0x3007                 // 〇 (Ideographic number zero)
}

private fun isWordCountKana(cp: Int): Boolean {
    val script = try {
        Character.UnicodeScript.of(cp)
    } catch (_: Exception) {
        null
    }
    if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA) return true

    return cp in 0x3040..0x309F ||      // Hiragana
           cp in 0x30A0..0x30FF ||      // Katakana
           cp in 0x31F0..0x31FF ||      // Katakana Phonetic Extensions
           cp in 0xFF65..0xFF9F ||      // Halfwidth Katakana
           cp in 0x1B000..0x1B16F ||    // Kana Supplement & Kana Extended
           cp == 0x30FC                 // ー (Japanese prolonged sound mark)
}

private fun isWordCountWordChar(cp: Int): Boolean {
    if (cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code || cp in '0'.code..'9'.code) {
        return true
    }
    return Character.isLetterOrDigit(cp) && !isWordCountHan(cp) && !isWordCountKana(cp)
}

private fun consumeWordToken(text: String, startIndex: Int): Int {
    var i = startIndex
    val length = text.length

    while (i < length) {
        val cp = text.codePointAt(i)
        val charCount = Character.charCount(cp)

        if (isWordCountWordChar(cp)) {
            i += charCount
        } else if ((cp == '\''.code || cp == '’'.code) && i + charCount < length) {
            val nextCp = text.codePointAt(i + charCount)
            if (isWordCountWordChar(nextCp)) {
                i += charCount
            } else {
                break
            }
        } else {
            break
        }
    }
    return i
}

private fun isWordCountPunctuationOrSymbol(cp: Int): Boolean {
    val type = Character.getType(cp)
    return when (type) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt()
        -> true
        else -> cp in 0x3001..0x303F && cp != 0x3005 && cp != 0x3007
    }
}
