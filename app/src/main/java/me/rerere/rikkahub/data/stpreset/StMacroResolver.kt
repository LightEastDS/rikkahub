package me.rerere.rikkahub.data.stpreset

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Clock

/**
 * ST 宏展开上下文。所有字段都是当前这一轮请求的快照。
 */
data class StMacroContext(
    val characterName: String = "",
    val userName: String = "",
    val personaDescription: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val mesExample: String = "",
    val modelName: String = "",
    val input: String = "",
    val lastMessage: String = "",
    val original: String = "",
)

/**
 * SillyTavern 宏引擎的最小可用实现。
 *
 * 支持：角色/用户字段、时间日期、random/pick/roll、会话变量与全局变量、注释。
 * 未识别的宏会被记录并替换为空串，避免把 `{{...}}` 原样发给模型。
 *
 * [variables] 是当前聊天的变量，[globalVariables] 是跨聊天共享的变量；setvar 按 prompt 顺序执行。
 */
class StMacroResolver(
    private val variables: MutableMap<String, String> = mutableMapOf(),
    private val globalVariables: MutableMap<String, String> = mutableMapOf(),
    private val random: Random = Random.Default,
    private val now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
) {
    private val _unresolved = linkedSetOf<String>()
    val unresolvedMacros: Set<String> get() = _unresolved

    private val setVarRegex = Regex("""^\s*setvar::([^:]+)::([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val getVarRegex = Regex("""^\s*getvar::([^}]+)$""", RegexOption.IGNORE_CASE)
    private val setGlobalVarRegex = Regex("""^\s*setglobalvar::([^:]+)::([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val getGlobalVarRegex = Regex("""^\s*getglobalvar::([^}]+)$""", RegexOption.IGNORE_CASE)
    private val hasVarRegex = Regex("""^\s*hasvar::([^}]+)$""", RegexOption.IGNORE_CASE)
    private val deleteVarRegex = Regex("""^\s*deletevar::([^}]+)$""", RegexOption.IGNORE_CASE)
    private val randomRegex = Regex("""^\s*(random|pick)(?:::|:)([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val rollRegex = Regex("""^\s*roll(?:::|:)\s*([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val diceRegex = Regex("""^\s*(\d*)d(\d+)\s*([+-]\s*\d+)?\s*$""", RegexOption.IGNORE_CASE)

    fun render(text: String, context: StMacroContext): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        var result = text
        // 变量值里可能继续引用宏；多轮展开，直到稳定。上限防止自引用死循环。
        repeat(8) {
            val next = replaceOnce(result, context)
            if (next == result) return result
            result = next
        }
        return result
    }

    private fun replaceOnce(text: String, context: StMacroContext): String {
        val output = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf("{{", cursor)
            if (start < 0) {
                output.append(text, cursor, text.length)
                break
            }
            output.append(text, cursor, start)
            val end = findMacroEnd(text, start)
            if (end < 0) {
                // 没有闭合的 {{，原样保留剩余文本
                output.append(text, start, text.length)
                break
            }
            val expression = text.substring(start + 2, end)
            val value = resolveExpression(expression, context)
            if (value == null) {
                _unresolved += expression.take(80)
            } else {
                output.append(value)
            }
            cursor = end + 2
        }
        return output.toString()
    }

    /** 找到与 start 处 `{{` 匹配的 `}}`，支持嵌套宏（例如 setvar 的值里再写 {{char}}）。 */
    private fun findMacroEnd(text: String, start: Int): Int {
        var depth = 1
        var index = start + 2
        while (index < text.length - 1) {
            if (text[index] == '{' && text[index + 1] == '{') {
                depth++
                index += 2
                continue
            }
            if (text[index] == '}' && text[index + 1] == '}') {
                depth--
                if (depth == 0) return index
                index += 2
                continue
            }
            index++
        }
        return -1
    }

    private fun resolveExpression(expression: String, context: StMacroContext): String? {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("//")) return ""

        setVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            val value = render(match.groupValues[2], context)
            variables[name] = value
            return ""
        }
        setGlobalVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            val value = render(match.groupValues[2], context)
            globalVariables[name] = value
            return ""
        }
        getVarRegex.find(trimmed)?.let { match ->
            return variables[match.groupValues[1].trim()].orEmpty()
        }
        getGlobalVarRegex.find(trimmed)?.let { match ->
            return globalVariables[match.groupValues[1].trim()].orEmpty()
        }
        hasVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            return (variables.containsKey(name) || globalVariables.containsKey(name)).toString()
        }
        deleteVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            variables.remove(name)
            return ""
        }
        randomRegex.find(trimmed)?.let { match ->
            val options = splitRandomOptions(match.groupValues[2])
            if (options.isEmpty()) return ""
            return options[random.nextInt(options.size)]
        }
        rollRegex.find(trimmed)?.let { match ->
            return roll(match.groupValues[1])
        }

        return when (trimmed.lowercase(Locale.ROOT)) {
            "char" -> context.characterName
            "user" -> context.userName
            "persona" -> context.personaDescription
            "description" -> context.description
            "personality" -> context.personality
            "scenario" -> context.scenario
            "mesexample", "mes_example" -> context.mesExample
            "model" -> context.modelName
            "input" -> context.input
            "lastmessage", "last_message" -> context.lastMessage
            "original" -> context.original
            "time", "isotime" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            "date", "isodate" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            "datetime" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            "weekday" -> now.toJavaLocalDateTime().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "newline" -> "\n"
            "space" -> " "
            "trim" -> ""
            "cur_date" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            "cur_time" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            else -> null
        }
    }

    private fun splitRandomOptions(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val separator = if (trimmed.contains("::")) "::" else ","
        return trimmed.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun roll(raw: String): String {
        val match = diceRegex.find(raw.trim()) ?: return "0"
        val count = match.groupValues[1].ifBlank { "1" }.toIntOrNull()?.coerceIn(1, 100) ?: 1
        val sides = match.groupValues[2].toIntOrNull()?.coerceIn(1, 1000) ?: 6
        val modifier = match.groupValues[3].replace(" ", "").toIntOrNull() ?: 0
        var total = modifier
        repeat(count) { total += random.nextInt(1, sides + 1) }
        return total.toString()
    }
}
