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
    val lastUserMessage: String = input,
    val lastCharMessage: String = "",
    val original: String = "",
)

/**
 * SillyTavern 宏引擎的最小可用实现。
 *
 * 支持：角色/用户字段、时间日期、random/pick/roll、会话变量与全局变量（含 setvar/addvar/incvar/decvar）、注释。
 * 未识别的宏会被记录并替换为空串，避免把 `{{...}}` 原样发给模型。
 *
 * [variables] 是当前聊天的变量，[globalVariables] 是跨聊天共享的变量；setvar/addvar 按 prompt 顺序执行。
 * [lockedVariables] 是用户在 UI 变量面板中手动覆盖锁定的变量，不会被预设内置的默认 setvar/addvar 覆盖。
 */
class StMacroResolver(
    private val variables: MutableMap<String, String> = mutableMapOf(),
    private val globalVariables: MutableMap<String, String> = mutableMapOf(),
    private val random: Random = Random.Default,
    private val now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    private val lockedVariables: Map<String, String> = emptyMap(),
) {
    init {
        if (lockedVariables.isNotEmpty()) {
            variables.putAll(lockedVariables)
        }
    }

    private val _unresolved = linkedSetOf<String>()
    val unresolvedMacros: Set<String> get() = _unresolved

    private val setVarRegex = Regex("""^\s*setvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val addVarRegex = Regex("""^\s*addvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val incVarRegex = Regex("""^\s*incvar(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val decVarRegex = Regex("""^\s*decvar(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val getVarRegex = Regex("""^\s*(?:getvar|var)(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val setGlobalVarRegex = Regex("""^\s*setglobalvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val addGlobalVarRegex = Regex("""^\s*addglobalvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val incGlobalVarRegex = Regex("""^\s*incglobalvar(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val decGlobalVarRegex = Regex("""^\s*decglobalvar(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val getGlobalVarRegex = Regex("""^\s*(?:getglobalvar|globalvar)(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val hasVarRegex = Regex("""^\s*hasvar(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val deleteVarRegex = Regex("""^\s*deletevar(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
    private val randomRegex = Regex("""^\s*(random|pick)(?:::|:)([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val rollRegex = Regex("""^\s*roll(?:::|:)\s*([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val diceRegex = Regex("""^\s*(\d*)d(\d+)\s*([+-]\s*\d+)?\s*$""", RegexOption.IGNORE_CASE)
    private val trimSurroundingRegex = Regex("""(?:\r?\n)*\{\{\s*trim\s*\}\}(?:\r?\n)*""", RegexOption.IGNORE_CASE)

    fun render(text: String, context: StMacroContext): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        var result = processConditionals(text, context)
        if (result.contains("trim", ignoreCase = true)) {
            result = result.replace(trimSurroundingRegex, "")
        }
        // 变量值里可能继续引用宏；多轮展开，直到稳定。上限防止自引用死循环。
        repeat(8) {
            val next = replaceOnce(result, context)
            if (next == result) return result
            result = next
        }
        return result
    }

    private fun processConditionals(text: String, context: StMacroContext): String {
        var output = text
        repeat(6) {
            var changed = false
            val ifIdx = output.indexOf("{{#if", ignoreCase = true)
            if (ifIdx >= 0) {
                val headerEnd = findMacroEnd(output, ifIdx)
                if (headerEnd >= 0) {
                    val conditionExpr = output.substring(ifIdx + 5, headerEnd).trim()
                    val closeTag = "{{/if}}"
                    val closeIdx = findMatchingCloseTag(output, headerEnd + 2, "{{#if", closeTag)
                    if (closeIdx >= 0) {
                        val innerBody = output.substring(headerEnd + 2, closeIdx)
                        val eval = render(conditionExpr, context).trim()
                        val replacement = if (eval.isNotEmpty() && eval != "0" && !eval.equals("false", ignoreCase = true)) {
                            innerBody
                        } else {
                            ""
                        }
                        output = output.substring(0, ifIdx) + replacement + output.substring(closeIdx + closeTag.length)
                        changed = true
                    }
                }
            }

            val unlessIdx = output.indexOf("{{#unless", ignoreCase = true)
            if (unlessIdx >= 0) {
                val headerEnd = findMacroEnd(output, unlessIdx)
                if (headerEnd >= 0) {
                    val conditionExpr = output.substring(unlessIdx + 9, headerEnd).trim()
                    val closeTag = "{{/unless}}"
                    val closeIdx = findMatchingCloseTag(output, headerEnd + 2, "{{#unless", closeTag)
                    if (closeIdx >= 0) {
                        val innerBody = output.substring(headerEnd + 2, closeIdx)
                        val eval = render(conditionExpr, context).trim()
                        val replacement = if (eval.isEmpty() || eval == "0" || eval.equals("false", ignoreCase = true)) {
                            innerBody
                        } else {
                            ""
                        }
                        output = output.substring(0, unlessIdx) + replacement + output.substring(closeIdx + closeTag.length)
                        changed = true
                    }
                }
            }

            if (!changed) return output
        }
        return output
    }

    private fun findMatchingCloseTag(text: String, startFrom: Int, openTag: String, closeTag: String): Int {
        var depth = 1
        var cursor = startFrom
        while (cursor < text.length) {
            val nextOpen = text.indexOf(openTag, cursor, ignoreCase = true)
            val nextClose = text.indexOf(closeTag, cursor, ignoreCase = true)
            if (nextClose < 0) return -1
            if (nextOpen in 0 until nextClose) {
                depth++
                cursor = nextOpen + openTag.length
            } else {
                depth--
                if (depth == 0) return nextClose
                cursor = nextClose + closeTag.length
            }
        }
        return -1
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

        setVarRegex.find(expression)?.let { match ->
            val name = match.groupValues[1].trim()
            val value = render(match.groupValues[2], context)
            if (!lockedVariables.containsKey(name)) {
                variables[name] = value
            }
            return ""
        }
        addVarRegex.find(expression)?.let { match ->
            val name = match.groupValues[1].trim()
            val value = render(match.groupValues[2], context)
            if (!lockedVariables.containsKey(name)) {
                variables[name] = combineVarValue(variables[name].orEmpty(), value)
            }
            return ""
        }
        incVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            if (lockedVariables.containsKey(name)) return variables[name].orEmpty()
            val next = stepNumericVar(variables[name], 1)
            variables[name] = next
            return next
        }
        decVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            if (lockedVariables.containsKey(name)) return variables[name].orEmpty()
            val next = stepNumericVar(variables[name], -1)
            variables[name] = next
            return next
        }
        setGlobalVarRegex.find(expression)?.let { match ->
            val name = match.groupValues[1].trim()
            val value = render(match.groupValues[2], context)
            globalVariables[name] = value
            return ""
        }
        addGlobalVarRegex.find(expression)?.let { match ->
            val name = match.groupValues[1].trim()
            val value = render(match.groupValues[2], context)
            globalVariables[name] = combineVarValue(globalVariables[name].orEmpty(), value)
            return ""
        }
        incGlobalVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            val next = stepNumericVar(globalVariables[name], 1)
            globalVariables[name] = next
            return next
        }
        decGlobalVarRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1].trim()
            val next = stepNumericVar(globalVariables[name], -1)
            globalVariables[name] = next
            return next
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
            if (!lockedVariables.containsKey(name)) {
                variables.remove(name)
            }
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
            "char", "charifnotgroup", "char_if_not_group", "group", "groupnotmuted" -> context.characterName
            "user" -> context.userName
            "persona" -> context.personaDescription
            "description" -> context.description
            "personality" -> context.personality
            "scenario" -> context.scenario
            "mesexample", "mes_example", "mesexamples", "mes_examples" -> context.mesExample
            "model" -> context.modelName
            "input", "lastusermessage", "last_user_message" -> context.lastUserMessage.ifEmpty { context.input }
            "lastcharmessage", "last_char_message" -> context.lastCharMessage
            "lastmessage", "last_message" -> context.lastMessage
            "original" -> context.original
            "time", "isotime", "cur_time" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            "date", "isodate", "cur_date" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            "datetime" -> now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            "weekday" -> now.toJavaLocalDateTime().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "newline" -> "\n"
            "space" -> " "
            "noop", "trim" -> ""
            else -> null
        }
    }

    private fun combineVarValue(current: String, addition: String): String {
        val cTrim = current.trim()
        val aTrim = addition.trim()
        if (cTrim.isNotEmpty() && aTrim.isNotEmpty()) {
            val cNum = cTrim.toBigDecimalOrNull()
            val aNum = aTrim.toBigDecimalOrNull()
            if (cNum != null && aNum != null) {
                return (cNum + aNum).stripTrailingZeros().toPlainString()
            }
        }
        return current + addition
    }

    private fun stepNumericVar(current: String?, delta: Long): String {
        val num = current?.trim()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        return (num + java.math.BigDecimal.valueOf(delta)).stripTrailingZeros().toPlainString()
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

    companion object {
        private val setVarDeclarationRegex = Regex("""^\s*setvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)
        private val addVarDeclarationRegex = Regex("""^\s*addvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)

        private enum class VarOpType { SET, ADD }
        private data class VarMutation(val type: VarOpType, val key: String, val rawValue: String)

        /**
         * 基于完整预设的启用顺序（prompt_order）扫描变量默认值并展开依赖。
         */
        fun preScanVariables(
            preset: StPreset,
            persistedVariables: Map<String, String> = emptyMap(),
            context: StMacroContext = StMacroContext(),
            random: Random = Random.Default,
            now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        ): MutableMap<String, String> {
            val enabledOrderedPrompts = StPromptAssembler.orderedPrompts(preset)
                .filter { (_, entry) -> entry.enabled }
                .map { (prompt, _) -> prompt }
            return preScanVariables(
                prompts = enabledOrderedPrompts,
                persistedVariables = persistedVariables,
                context = context,
                random = random,
                now = now,
            )
        }

        /**
         * Pass 1: 按条目顺序扫描所有 setvar / addvar 声明并展开拓扑依赖。
         * - 预设内后续启用的 setvar 会覆盖前面的初始化空值（如 #2 变量初始化 -> #27 梦白话文风）；
         * - addvar 会在当前变量值基础上追加内容；
         * - [persistedVariables] 中的用户显式自定义值具有最高优先级，不会被预设默认声明覆盖。
         */
        fun preScanVariables(
            prompts: List<StPrompt>,
            persistedVariables: Map<String, String> = emptyMap(),
            context: StMacroContext = StMacroContext(),
            random: Random = Random.Default,
            now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        ): MutableMap<String, String> {
            val vars = linkedMapOf<String, String>()

            // 1. 按条目顺序执行 setvar / addvar 静态声明
            prompts.forEach { prompt ->
                if (prompt.content.contains("setvar", ignoreCase = true) ||
                    prompt.content.contains("addvar", ignoreCase = true)
                ) {
                    extractVarMutations(prompt.content).forEach { mutation ->
                        when (mutation.type) {
                            VarOpType.SET -> vars[mutation.key] = mutation.rawValue
                            VarOpType.ADD -> vars[mutation.key] = vars[mutation.key].orEmpty() + mutation.rawValue
                        }
                    }
                }
            }

            // 2. 用户自定义变量覆盖默认值
            vars.putAll(persistedVariables)

            // 3. 拓扑迭代展开（处理 {{setvar::A::{{getvar::B}}}} 这种嵌套依赖，最多 6 轮防死循环）
            val resolver = StMacroResolver(
                variables = vars,
                random = random,
                now = now,
                lockedVariables = persistedVariables,
            )
            repeat(6) {
                var changed = false
                vars.keys.toList().forEach { k ->
                    val oldVal = vars[k] ?: ""
                    val newVal = resolver.render(oldVal, context)
                    if (newVal != oldVal) {
                        vars[k] = newVal
                        changed = true
                    }
                }
                if (!changed) return@repeat
            }
            return vars
        }

        /**
         * 判断一段提示词是否仅包含变量赋值（setvar/addvar/incvar/decvar/deletevar）、注释（//）或 trim，
         * 即自身不会产生任何发给模型的可见文本，仅用于修改宏变量状态。
         */
        fun isVariableMutationOnly(content: String): Boolean {
            if (content.isBlank()) return false
            val resolver = StMacroResolver()
            val stripped = resolver.render(content, StMacroContext())
            if (stripped.isNotBlank()) return false
            return content.contains("setvar", ignoreCase = true) ||
                content.contains("addvar", ignoreCase = true) ||
                content.contains("incvar", ignoreCase = true) ||
                content.contains("decvar", ignoreCase = true) ||
                content.contains("setglobalvar", ignoreCase = true) ||
                content.contains("addglobalvar", ignoreCase = true)
        }

        fun findAdditiveVariableNames(prompts: List<StPrompt>): Set<String> {
            val names = mutableSetOf<String>()
            prompts.forEach { prompt ->
                if (!prompt.content.contains("{{")) return@forEach
                extractVarMutations(prompt.content).forEach { mutation ->
                    if (mutation.type == VarOpType.ADD && mutation.key.isNotEmpty()) {
                        names += mutation.key
                    }
                }
            }
            return names
        }

        fun findMutatedVariableNames(prompts: List<StPrompt>): Set<String> {
            val names = mutableSetOf<String>()
            prompts.forEach { prompt ->
                if (!prompt.content.contains("{{")) return@forEach
                extractVarMutations(prompt.content).forEach { mutation ->
                    if (mutation.key.isNotEmpty()) {
                        names += mutation.key
                    }
                }
            }
            return names
        }

        private fun extractVarMutations(text: String): List<VarMutation> {
            val result = mutableListOf<VarMutation>()
            var cursor = 0
            while (cursor < text.length) {
                val start = text.indexOf("{{", cursor)
                if (start < 0) break
                val end = findMacroEndStatic(text, start)
                if (end < 0) break
                val expression = text.substring(start + 2, end)
                setVarDeclarationRegex.find(expression)?.let { match ->
                    val key = match.groupValues[1].trim()
                    val rawVal = match.groupValues[2]
                    result.add(VarMutation(VarOpType.SET, key, rawVal))
                } ?: addVarDeclarationRegex.find(expression)?.let { match ->
                    val key = match.groupValues[1].trim()
                    val rawVal = match.groupValues[2]
                    result.add(VarMutation(VarOpType.ADD, key, rawVal))
                }
                cursor = end + 2
            }
            return result
        }

        private fun findMacroEndStatic(text: String, start: Int): Int {
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
    }
}
