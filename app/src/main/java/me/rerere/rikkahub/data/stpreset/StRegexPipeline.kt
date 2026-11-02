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
        val preFormatted = formatDreamBlocksForDisplay(text)
        val afterScripts = scripts.fold(preFormatted) { acc, script ->
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
        return normalizeHtmlDetailsForMarkdown(afterScripts)
    }

    private val DREAM_SELF_CHECK_REGEX = Regex(
        """<dream_self_check\b[^>]*>\s*([\s\S]*?)\s*</dream_self_check>""",
        RegexOption.IGNORE_CASE,
    )
    private val REVIEW_TAG_REGEX = Regex("""<review\b[^>]*>([\s\S]*?)</review>""", RegexOption.IGNORE_CASE)
    private val PATCH_TAG_REGEX = Regex("""<patch\b[^>]*>([\s\S]*?)</patch>""", RegexOption.IGNORE_CASE)
    private val PATCH_PAIR_REGEX = Regex("""^FIND:[ \t]*([\s\S]*?)\nREPLACE:[ \t]?([\s\S]*)$""")
    private val PATCH_SPLIT_REGEX = Regex("""\n[ \t]*\n(?=FIND:[ \t]*)""")

    private val DREAM_SCENE_XML_REGEX = Regex(
        """<dream_scene>\s*<date>\s*([\s\S]*?)\s*</date>\s*<time>\s*([\s\S]*?)\s*</time>\s*<location>\s*([\s\S]*?)\s*</location>\s*</dream_scene>""",
        RegexOption.IGNORE_CASE,
    )
    private val DREAM_SCENE_GENERIC_REGEX = Regex(
        """<dream_scene>\s*([\s\S]*?)\s*</dream_scene>""",
        RegexOption.IGNORE_CASE,
    )
    private val DREAM_OPTION_REGEX = Regex(
        """<dream_option\b[^>]*>\s*([\s\S]*?)\s*</dream_option>""",
        RegexOption.IGNORE_CASE,
    )
    private val DREAM_PARALLEL_EVENT_REGEX = Regex(
        """<dream_parallel_event>\s*([\s\S]*?)\s*</dream_parallel_event>""",
        RegexOption.IGNORE_CASE,
    )
    private val SIMPLE_THINKING_REGEX = Regex(
        """<simple_thinking>\s*([\s\S]*?)\s*</simple_thinking>\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val DREAM_BIG_DISCUSS_REGEX = Regex(
        """<dream_big_discuss>\s*([\s\S]*?)\s*</dream_big_discuss>""",
        RegexOption.IGNORE_CASE,
    )
    private val QUESTION_NODE_REGEX = Regex(
        """<q\b[^>]*content\s*=\s*["']([^"']*)["'][^>]*>([\s\S]*?)</q>""",
        RegexOption.IGNORE_CASE,
    )
    private val ANSWER_NODE_REGEX = Regex(
        """<([a-z])\b[^>]*>([\s\S]*?)</\1>""",
        RegexOption.IGNORE_CASE,
    )
    private val STYLED_DETAILS_REGEX = Regex(
        """<details\b[^>]*>\s*<summary\b[^>]*>([\s\S]*?)</summary>\s*(?:<div\b[^>]*>([\s\S]*?)</div>|<pre\b[^>]*>([\s\S]*?)</pre>|([\s\S]*?))\s*</details>""",
        RegexOption.IGNORE_CASE,
    )
    private val FALLBACK_SUMMARY_REGEX = Regex(
        """<dream_summary>\s*([\s\S]*?)\s*</dream_summary>""",
        RegexOption.IGNORE_CASE,
    )
    private val FALLBACK_DISCUSS_REGEX = Regex(
        """<dream_discuss>\s*([\s\S]*?)\s*</dream_discuss>""",
        RegexOption.IGNORE_CASE,
    )
    private val FALLBACK_WRAPPER_TAGS_REGEX = Regex(
        """(?:</?dream_plot>|</?dream_body>|</?dream_after_format>|<dream_done\s*/>|<dream_check_done\s*/>)\r?\n?""",
        RegexOption.IGNORE_CASE,
    )
    private val INLINE_HTML_TAG_REGEX = Regex("""</?(?:span|strong|em|b|i)\b[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * 将 SillyTavern 预设（如梦鲸思客 V5）中依赖浏览器 iframe / DOM 脚本的 XML 块
     * 转换为 RikkaHub 原生 BetterMarkdown 可渲染的引用卡片与 `<details>` 折叠面板。
     */
    internal fun formatDreamBlocksForDisplay(input: String): String {
        var result = input

        // 1. <dream_self_check>：自动将 <patch> 中的 FIND/REPLACE 应用于前文，并渲染折叠自检报告
        if (result.contains("<dream_self_check", ignoreCase = true)) {
            val checkMatches = DREAM_SELF_CHECK_REGEX.findAll(result).toList()
            for (match in checkMatches.asReversed()) {
                val inner = match.groupValues[1]
                val review = REVIEW_TAG_REGEX.find(inner)?.groupValues?.get(1)?.trim().orEmpty()
                val patchRaw = PATCH_TAG_REGEX.find(inner)?.groupValues?.get(1)?.trim().orEmpty()
                val patches = if (patchRaw.isNotEmpty()) {
                    patchRaw.replace("\r\n", "\n")
                        .split(PATCH_SPLIT_REGEX)
                        .mapNotNull { block ->
                            val m = PATCH_PAIR_REGEX.find(block.trim()) ?: return@mapNotNull null
                            m.groupValues[1] to m.groupValues[2]
                        }
                } else {
                    emptyList()
                }
                var beforeText = result.substring(0, match.range.first)
                val afterText = result.substring(match.range.last + 1)
                var appliedCount = 0
                for ((findStr, replaceStr) in patches) {
                    if (findStr.isNotEmpty() && beforeText.contains(findStr)) {
                        beforeText = beforeText.replaceFirst(findStr, replaceStr)
                        appliedCount++
                    }
                }
                val title = if (patches.isNotEmpty()) {
                    "🔍 梦境自检与修复（已应用 $appliedCount/${patches.size} 项修正）"
                } else {
                    "🔍 梦境自检（本轮无纰漏）"
                }
                val cardBody = buildString {
                    if (review.isNotEmpty()) {
                        append(review)
                    }
                    if (patches.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        patches.forEachIndexed { idx, (f, r) ->
                            val rDisplay = r.ifEmpty { "∅（删除）" }
                            append("${idx + 1}. `$f` → `$rDisplay`")
                            if (idx < patches.lastIndex) append('\n')
                        }
                    }
                }.ifBlank { "已完成正文自检。" }
                val card = "\n<details>\n<summary>$title</summary>\n\n$cardBody\n\n</details>\n"
                result = beforeText + card + afterText
            }
        }

        // 2. <dream_scene>：转换为紧凑引用状态栏
        result = DREAM_SCENE_XML_REGEX.replace(result) { match ->
            val date = match.groupValues[1].trim()
            val time = match.groupValues[2].trim()
            val location = match.groupValues[3].trim()
            "> 📅 **$date** ｜ 🕰️ **$time** ｜ 📍 **$location**\n"
        }
        result = DREAM_SCENE_GENERIC_REGEX.replace(result) { match ->
            val lines = match.groupValues[1].lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) "" else "> ${lines.joinToString(" ｜ ")}\n"
        }

        // 3. <dream_parallel_event>：清理 <simple_thinking> 并渲染平行事件折叠卡片
        result = DREAM_PARALLEL_EVENT_REGEX.replace(result) { match ->
            val cleaned = SIMPLE_THINKING_REGEX.replace(match.groupValues[1], "").trim()
            val events = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val formattedEvents = events.joinToString("\n\n") { line ->
                val sep = line.indexOf('|')
                if (sep > 0) {
                    val loc = line.substring(0, sep).trim()
                    val desc = line.substring(sep + 1).trim().replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n  ")
                    "- **【$loc】**\n  $desc"
                } else {
                    "- ${line.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n  ")}"
                }
            }.ifBlank { "暂无平行事件" }
            "\n<details>\n<summary>🪐 梦境平行事件（${events.size} 处）</summary>\n\n$formattedEvents\n\n</details>\n"
        }

        // 4. <dream_big_discuss>：解析共创讨论与 <q content="..."><a>...</a></q>
        result = DREAM_BIG_DISCUSS_REGEX.replace(result) { match ->
            val raw = match.groupValues[1]
            val questions = QUESTION_NODE_REGEX.findAll(raw).toList()
            val discussNote = QUESTION_NODE_REGEX.replace(raw, "").trim()
            val body = buildString {
                if (discussNote.isNotEmpty()) {
                    append(discussNote)
                }
                questions.forEachIndexed { qIdx, qMatch ->
                    if (isNotEmpty()) append("\n\n")
                    val qTitle = qMatch.groupValues[1].trim()
                    append("**Q${qIdx + 1}：$qTitle**")
                    val answers = ANSWER_NODE_REGEX.findAll(qMatch.groupValues[2]).toList()
                    answers.forEachIndexed { aIdx, aMatch ->
                        append("\n${aIdx + 1}. ${aMatch.groupValues[2].trim()}")
                    }
                }
            }.ifBlank { raw.trim() }
            val meta = if (questions.isNotEmpty()) "（${questions.size} 问）" else ""
            "\n<details open>\n<summary>💬 思客大调查$meta</summary>\n\n$body\n\n</details>\n"
        }

        // 5. <dream_option>：支持 `|` 分隔或换行分隔的行动选项
        result = DREAM_OPTION_REGEX.replace(result) { match ->
            val raw = match.groupValues[1].trim()
            val options = if (raw.contains('|')) {
                raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                raw.lines().map { it.trim().removePrefix("- ").replace(Regex("""^\d+[.、]\s*"""), "") }.filter { it.isNotEmpty() }
            }
            if (options.isEmpty()) return@replace ""
            val listText = options.mapIndexed { idx, opt -> "${idx + 1}. $opt" }.joinToString("\n")
            "\n<details open>\n<summary>🎯 剧情推进选项（${options.size} 项）</summary>\n\n$listText\n\n</details>\n"
        }

        return result
    }

    /**
     * 将带 inline style / `<div>` 包裹的 HTML `<details>` 规范化为 BetterMarkdown 原生支持的格式。
     */
    internal fun normalizeHtmlDetailsForMarkdown(input: String): String {
        var result = input
        if (result.contains("<dream_summary>", ignoreCase = true)) {
            result = FALLBACK_SUMMARY_REGEX.replace(result) { match ->
                "\n<details>\n<summary>🐋梦鲸摘要</summary>\n\n${match.groupValues[1].trim()}\n\n</details>\n"
            }
        }
        if (result.contains("<dream_discuss>", ignoreCase = true)) {
            result = FALLBACK_DISCUSS_REGEX.replace(result) { match ->
                "\n<details>\n<summary>📖思客说书</summary>\n\n${match.groupValues[1].trim()}\n\n</details>\n"
            }
        }
        result = FALLBACK_WRAPPER_TAGS_REGEX.replace(result, "")
        if (!result.contains("<details", ignoreCase = true)) return result
        return STYLED_DETAILS_REGEX.replace(result) { match ->
            val rawTag = match.value
            val isOpen = Regex("""^<details\b[^>]*\bopen\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawTag)
            val summaryTitle = INLINE_HTML_TAG_REGEX.replace(match.groupValues[1], "").trim().lines().firstOrNull()?.trim().orEmpty()
                .ifBlank { "详情" }
            val body = (match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }).trim()
            val openAttr = if (isOpen) " open" else ""
            "\n<details$openAttr>\n<summary>$summaryTitle</summary>\n\n$body\n\n</details>\n"
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
