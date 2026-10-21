package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import kotlin.random.Random

data class StWorldInfoDepthEntry(
    val depth: Int,
    val role: MessageRole,
    val content: String,
    val order: Int = 100,
)

data class StWorldInfoResult(
    val before: String = "",
    val after: String = "",
    val depthEntries: List<StWorldInfoDepthEntry> = emptyList(),
    val activatedNames: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/**
 * 世界书 marker 的最小实现：常驻 + 关键词、secondary keys、按 order 排序、字符预算。
 *
 * 支持角色卡内嵌 `character_book` 和助手绑定的 RikkaHub Lorebook。
 * 暂不实现递归、sticky/cooldown/delay、向量检索和概率以外的分组竞争。
 */
object StWorldInfoEngine {
    private const val DEFAULT_SCAN_DEPTH = 2
    private const val MAX_CHARS = 120_000

    fun collect(
        characterBook: StCharacterBook?,
        lorebooks: List<Lorebook>,
        messages: List<UIMessage>,
        random: Random = Random.Default,
    ): StWorldInfoResult {
        val warnings = mutableListOf<String>()
        val activated = mutableListOf<ActivatedEntry>()

        characterBook?.entries?.forEach { entry ->
            if (!entry.enabled) return@forEach
            if (!isActive(entry, messages, random)) return@forEach
            val position = when (entry.position.lowercase()) {
                "after_char" -> WorldPosition.AFTER
                "at_depth" -> WorldPosition.DEPTH
                else -> WorldPosition.BEFORE
            }
            activated += ActivatedEntry(
                name = "character_book#${entry.id}",
                content = entry.content,
                position = position,
                depth = entry.depth,
                role = entry.role,
                order = entry.order,
            )
        }

        lorebooks.filter { it.enabled }.forEach { book ->
            book.entries.filter { it.enabled }.forEach { entry ->
                val context = messages
                    .filter { it.role != MessageRole.SYSTEM }
                    .takeLast(entry.scanDepth.coerceAtLeast(1))
                    .joinToString("\n") { it.toText() }
                val hit = entry.constantActive || entry.keywords.any { keyword ->
                    if (entry.useRegex) {
                        runCatching {
                            Regex(keyword, if (entry.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                                .containsMatchIn(context)
                        }.getOrDefault(false)
                    } else if (entry.caseSensitive) {
                        context.contains(keyword)
                    } else {
                        context.contains(keyword, ignoreCase = true)
                    }
                }
                if (!hit) return@forEach
                val (position, depth) = when (entry.position) {
                    InjectionPosition.BEFORE_SYSTEM_PROMPT -> WorldPosition.BEFORE to 0
                    InjectionPosition.AFTER_SYSTEM_PROMPT -> WorldPosition.AFTER to 0
                    InjectionPosition.TOP_OF_CHAT -> WorldPosition.AFTER to 0
                    InjectionPosition.BOTTOM_OF_CHAT -> WorldPosition.DEPTH to 1
                    InjectionPosition.AT_DEPTH -> WorldPosition.DEPTH to entry.injectDepth
                }
                activated += ActivatedEntry(
                    name = "${book.name}/${entry.name}",
                    content = entry.content,
                    position = position,
                    depth = depth,
                    role = entry.role,
                    order = entry.priority,
                )
            }
        }

        val sorted = activated.sortedWith(compareBy<ActivatedEntry> { it.order }.thenBy { it.name })
        val before = StringBuilder()
        val after = StringBuilder()
        val depth = mutableListOf<StWorldInfoDepthEntry>()
        var used = 0
        val names = mutableListOf<String>()
        sorted.forEach { entry ->
            val remaining = MAX_CHARS - used
            if (remaining <= 0) {
                warnings += "世界书内容超过 ${MAX_CHARS} 字符预算，后续条目被截断"
                return@forEach
            }
            val text = if (entry.content.length > remaining) entry.content.take(remaining) else entry.content
            used += text.length
            names += entry.name
            when (entry.position) {
                WorldPosition.BEFORE -> if (text.isNotBlank()) before.append(text).append('\n')
                WorldPosition.AFTER -> if (text.isNotBlank()) after.append(text).append('\n')
                WorldPosition.DEPTH -> if (text.isNotBlank()) depth += StWorldInfoDepthEntry(
                    depth = entry.depth.coerceAtLeast(0),
                    role = entry.role,
                    content = text,
                    order = entry.order,
                )
            }
        }
        return StWorldInfoResult(
            before = before.toString().trimEnd(),
            after = after.toString().trimEnd(),
            depthEntries = depth,
            activatedNames = names,
            warnings = warnings,
        )
    }

    private fun isActive(entry: StCharacterBookEntry, messages: List<UIMessage>, random: Random): Boolean {
        if (entry.constant) return probabilityPasses(entry.probability, random)
        if (entry.keys.isEmpty()) return false
        val scanDepth = entry.scanDepth ?: DEFAULT_SCAN_DEPTH
        val context = messages
            .filter { it.role != MessageRole.SYSTEM }
            .takeLast(scanDepth.coerceAtLeast(1))
            .joinToString("\n") { it.toText() }
        val primary = entry.keys.any { key -> matches(key, context, entry) }
        if (!primary) return false
        if (entry.secondaryKeys.isNotEmpty()) {
            val hits = entry.secondaryKeys.count { key -> matches(key, context, entry) }
            val secondaryOk = when (entry.selectiveLogic) {
                1 -> hits != entry.secondaryKeys.size // NOT_ALL
                2 -> hits == 0 // NOT_ANY
                3 -> hits == entry.secondaryKeys.size // AND_ALL
                else -> hits > 0 // AND_ANY
            }
            if (!secondaryOk) return false
        }
        return probabilityPasses(entry.probability, random)
    }

    private fun matches(key: String, context: String, entry: StCharacterBookEntry): Boolean {
        if (key.isBlank()) return false
        if (key.startsWith("/") && key.lastIndexOf('/') > 0) {
            val last = key.lastIndexOf('/')
            val pattern = key.substring(1, last)
            val flags = key.substring(last + 1)
            val options = buildSet {
                if ('i' in flags) add(RegexOption.IGNORE_CASE)
                if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
                if ('m' in flags) add(RegexOption.MULTILINE)
            }
            return runCatching { Regex(pattern, options).containsMatchIn(context) }.getOrDefault(false)
        }
        val caseSensitive = entry.caseSensitive == true
        val wholeWords = entry.matchWholeWords == true
        if (wholeWords) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val pattern = Regex.escape(key).let { "(?<![\\p{L}\\p{N}])$it(?![\\p{L}\\p{N}])" }
            return runCatching { Regex(pattern, options).containsMatchIn(context) }.getOrDefault(false)
        }
        return if (caseSensitive) context.contains(key) else context.contains(key, ignoreCase = true)
    }

    private fun probabilityPasses(probability: Int, random: Random): Boolean {
        if (probability >= 100) return true
        if (probability <= 0) return false
        return random.nextInt(100) < probability
    }

    private enum class WorldPosition { BEFORE, AFTER, DEPTH }

    private data class ActivatedEntry(
        val name: String,
        val content: String,
        val position: WorldPosition,
        val depth: Int,
        val role: MessageRole,
        val order: Int,
    )
}
