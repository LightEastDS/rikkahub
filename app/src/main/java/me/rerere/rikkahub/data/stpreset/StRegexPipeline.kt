package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

data class StRegexApplyResult(
    val messages: List<UIMessage>,
    val appliedScripts: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/**
 * 预设作用域的 Regex 管线。
 *
 * ST 的 findRegex 通常是 `/pattern/flags` 形式；Kotlin 的 Regex 不支持 flag 后缀，
 * 这里解析后映射成 [RegexOption]。placement：1 = 用户输入，2 = AI 输出。
 */
object StRegexPipeline {
    private const val MAX_PATTERN_LENGTH = 20_000
    private const val MAX_INPUT_LENGTH = 400_000

    /**
     * 对完整消息列表应用 prompt-only 脚本。`depth = 0` 表示最后一条消息，与 ST 一致。
     *
     * [substitute] 用于 substituteRegex：在编译 find/replace 前展开宏。
     */
    fun applyToMessages(
        messages: List<UIMessage>,
        scripts: List<StRegexScript>,
        substitute: (String) -> String = { it },
    ): StRegexApplyResult {
        if (messages.isEmpty() || scripts.isEmpty()) return StRegexApplyResult(messages)
        val applied = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val compiled = scripts.mapNotNull { script ->
            if (script.disabled || !script.isPromptScript) return@mapNotNull null
            compile(script, substitute, warnings)?.let { it to script }
        }
        if (compiled.isEmpty()) return StRegexApplyResult(messages, warnings = warnings)

        val result = messages.mapIndexed { index, message ->
            val depth = messages.lastIndex - index
            val placement = when (message.role) {
                MessageRole.USER -> 1
                MessageRole.ASSISTANT -> 2
                else -> return@mapIndexed message
            }
            var current = message
            compiled.forEach { (regex, script) ->
                if (placement !in script.placement && script.placement.isNotEmpty()) return@forEach
                if (!inDepth(script, depth)) return@forEach
                val text = current.toText()
                if (text.isEmpty() || text.length > MAX_INPUT_LENGTH) return@forEach
                val replacement = if (script.substituteRegex) substitute(script.replaceString) else script.replaceString
                val replaced = runCatching {
                    regex.replace(text) { match -> expandReplacement(match, replacement) }
                        .let { value -> script.trimStrings.fold(value) { acc, trim -> acc.replace(trim, "") } }
                }.getOrElse {
                    warnings += "正则「${script.name}」执行失败：${it.message}"
                    return@forEach
                }
                if (replaced == text) return@forEach
                current = current.replaceText(replaced)
                applied += script.name.ifBlank { script.findRegex.take(40) }
            }
            current
        }
        return StRegexApplyResult(
            messages = result,
            appliedScripts = applied.distinct(),
            warnings = warnings.distinct(),
        )
    }

    /** 显示层脚本：只改一段文本。可在 UI 渲染时调用，不影响发送给模型的消息。 */
    fun applyToDisplayText(
        text: String,
        scripts: List<StRegexScript>,
        placement: Int,
        depth: Int = 0,
        substitute: (String) -> String = { it },
    ): String {
        if (text.isEmpty()) return text
        val warnings = mutableListOf<String>()
        return scripts.fold(text) { acc, script ->
            if (script.disabled || !script.isDisplayScript) return@fold acc
            if (placement !in script.placement && script.placement.isNotEmpty()) return@fold acc
            if (!inDepth(script, depth)) return@fold acc
            val regex = compile(script, substitute, warnings) ?: return@fold acc
            val replacement = if (script.substituteRegex) substitute(script.replaceString) else script.replaceString
            runCatching {
                regex.replace(acc) { match -> expandReplacement(match, replacement) }
                    .let { value -> script.trimStrings.fold(value) { current, trim -> current.replace(trim, "") } }
            }.getOrDefault(acc)
        }
    }

    internal fun compile(script: StRegexScript, substitute: (String) -> String = { it }): Regex? =
        compile(script, substitute, mutableListOf())

    private fun compile(
        script: StRegexScript,
        substitute: (String) -> String,
        warnings: MutableList<String>,
    ): Regex? {
        val raw = if (script.substituteRegex) substitute(script.findRegex) else script.findRegex
        if (raw.length > MAX_PATTERN_LENGTH) {
            warnings += "正则「${script.name}」过长（${raw.length} 字符），已跳过"
            return null
        }
        val (pattern, options) = parsePattern(raw)
        return runCatching { Regex(pattern, options) }.getOrElse {
            warnings += "正则「${script.name}」无法编译：${it.message}"
            null
        }
    }

    private fun parsePattern(raw: String): Pair<String, Set<RegexOption>> {
        if (!raw.startsWith("/")) return raw to emptySet()
        val lastSlash = raw.lastIndexOf('/')
        if (lastSlash <= 0) return raw to emptySet()
        val body = raw.substring(1, lastSlash)
        val flags = raw.substring(lastSlash + 1)
        val options = mutableSetOf<RegexOption>()
        flags.forEach { flag ->
            when (flag) {
                'i' -> options += RegexOption.IGNORE_CASE
                'm' -> options += RegexOption.MULTILINE
                's' -> options += RegexOption.DOT_MATCHES_ALL
                'x' -> options += RegexOption.COMMENTS
                // g/u/y 对 Kotlin 没有对应语义：g 由 replace 自动全局处理，u 默认启用，y 忽略
            }
        }
        return body to options
    }

    private fun inDepth(script: StRegexScript, depth: Int): Boolean {
        val min = script.minDepth
        val max = script.maxDepth
        if (min != null && depth < min) return false
        if (max != null && depth > max) return false
        return true
    }

    /** 支持 JS 风格的 `$&`、`$1..$9`、`$$`，其余 `$` 原样保留。 */
    internal fun expandReplacement(match: MatchResult, replacement: String): String {
        if (!replacement.contains('$')) return replacement
        val builder = StringBuilder()
        var index = 0
        while (index < replacement.length) {
            val char = replacement[index]
            if (char != '$' || index == replacement.lastIndex) {
                builder.append(char)
                index++
                continue
            }
            val next = replacement[index + 1]
            when {
                next == '$' -> {
                    builder.append('$')
                    index += 2
                }

                next == '&' -> {
                    builder.append(match.value)
                    index += 2
                }

                next.isDigit() -> {
                    val group = next.digitToInt()
                    builder.append(match.groups[group]?.value.orEmpty())
                    index += 2
                }

                else -> {
                    builder.append(char)
                    index++
                }
            }
        }
        return builder.toString()
    }
}

/** 只替换消息里的文本 part，保留图片/文档/工具等其它 part。 */
internal fun UIMessage.replaceText(text: String): UIMessage {
    var replaced = false
    val parts = parts.map { part ->
        if (!replaced && part is UIMessagePart.Text) {
            replaced = true
            part.copy(text = text)
        } else {
            part
        }
    }
    return copy(parts = parts)
}
