package me.rerere.rikkahub.data.stpreset

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.findSafeInsertIndex
import me.rerere.rikkahub.data.model.CharacterCard
import kotlin.random.Random
import kotlin.time.Clock

data class StAssemblyResult(
    val messages: List<UIMessage>,
    val report: StAssemblyReport,
)

/**
 * SillyTavern Chat Completion 预设的提示词组装器。
 *
 * 顺序的唯一来源是 `prompt_order`；`prompts[]` 只是定义库。marker 是动态内容插入点，
 * in-chat prompt 从 relative 序列中移除后按 depth 插入。最后才做 system 合并和 prefill。
 */
object StPromptAssembler {
    fun assemble(
        preset: StPreset,
        history: List<UIMessage>,
        character: CharacterCard? = null,
        assistantSystemPrompt: String = "",
        personaName: String = "",
        personaDescription: String = "",
        modelName: String = "",
        generationType: StGenerationType = StGenerationType.NORMAL,
        extraSystemPrompt: String = "",
        worldInfo: StWorldInfoResult = StWorldInfoResult(),
        variables: MutableMap<String, String> = mutableMapOf(),
        globalVariables: MutableMap<String, String> = mutableMapOf(),
        random: Random = Random.Default,
        now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        /**
         * 某些 provider（Claude / Gemini / OpenAI Responses）只接受一条顶层 system，
         * 开启后把开头的 system 合并，历史之后的 system 降级成 user。
         */
        flattenSystemMessages: Boolean = false,
    ): StAssemblyResult {
        val warnings = mutableListOf<String>()
        val bucket = chooseBucket(preset, warnings)
        val promptMap = preset.prompts.associateBy { it.identifier }
        val entries = bucket.order.ifEmpty {
            preset.prompts.map { StPromptOrderEntry(identifier = it.identifier, enabled = true) }
        }

        val macroContext = buildMacroContext(
            character = character,
            personaName = personaName,
            personaDescription = personaDescription,
            modelName = modelName,
            history = history,
        )
        val macroResolver = StMacroResolver(
            variables = variables,
            globalVariables = globalVariables,
            random = random,
            now = now,
        )

        val nodes = mutableListOf<Node>()
        val inChat = mutableListOf<InChatPrompt>()
        var skippedDisabled = 0
        var skippedTrigger = 0
        var skippedEmpty = 0
        var markerCount = 0
        var rendered = 0

        entries.forEach { entry ->
            if (!entry.enabled) {
                skippedDisabled++
                return@forEach
            }
            val prompt = promptMap[entry.identifier]
            if (prompt == null) {
                warnings += "prompt_order 引用的「${entry.identifier}」不存在"
                return@forEach
            }
            if (!triggerMatches(prompt, generationType)) {
                skippedTrigger++
                return@forEach
            }
            if (prompt.isInChat) {
                inChat += InChatPrompt(
                    name = prompt.name.ifBlank { prompt.identifier },
                    content = macroResolver.render(prompt.content, macroContext),
                    role = prompt.role,
                    depth = prompt.injectionDepth,
                    order = prompt.injectionOrder,
                )
                return@forEach
            }
            if (prompt.marker) {
                markerCount++
                nodes += Node.Marker(prompt.identifier, prompt)
                return@forEach
            }
            val content = macroResolver.render(prompt.content, macroContext)
            if (content.isBlank()) {
                skippedEmpty++
                return@forEach
            }
            rendered++
            nodes += Node.Text(textMessage(prompt.role, content))
        }

        val messages = mutableListOf<UIMessage>()
        if (extraSystemPrompt.isNotBlank()) {
            messages += systemMessage(extraSystemPrompt)
        }

        val unknownMarkers = linkedSetOf<String>()
        var historyStart = 0
        var historyEnd = 0
        var historyFound = false
        nodes.forEach { node ->
            when (node) {
                is Node.Text -> messages += node.message
                is Node.Marker -> {
                    when (node.identifier) {
                        "chatHistory" -> {
                            historyStart = messages.size
                            if (history.isNotEmpty()) messages += history
                            historyEnd = messages.size
                            historyFound = true
                        }
                        "main" -> {
                            val text = resolveMainPrompt(node.prompt, character, assistantSystemPrompt)
                            if (text.isNotBlank()) messages += systemMessage(text)
                        }

                        "worldInfoBefore" -> if (worldInfo.before.isNotBlank()) {
                            val text = macroResolver.render(worldInfo.before, macroContext)
                            if (text.isNotBlank()) messages += systemMessage(text)
                        }

                        "worldInfoAfter" -> if (worldInfo.after.isNotBlank()) {
                            val text = macroResolver.render(worldInfo.after, macroContext)
                            if (text.isNotBlank()) messages += systemMessage(text)
                        }

                        "charDescription" -> character?.description?.takeIf { it.isNotBlank() }?.let {
                            messages += systemMessage(it)
                        }

                        "charPersonality" -> character?.personality?.takeIf { it.isNotBlank() }?.let {
                            messages += systemMessage(it)
                        }

                        "personaDescription" -> if (personaDescription.isNotBlank()) {
                            messages += systemMessage(personaDescription)
                        }

                        "scenario" -> character?.scenario?.takeIf { it.isNotBlank() }?.let {
                            messages += systemMessage(it)
                        }

                        "dialogueExamples" -> character?.mesExample?.takeIf { it.isNotBlank() }?.let {
                            messages += parseDialogueExamples(
                                raw = it,
                                characterName = character.name,
                                userName = personaName,
                            )
                        }

                        else -> unknownMarkers += node.identifier
                    }
                }
            }
        }

        // 预设没有 chatHistory marker 时也不能把聊天记录丢掉：放在最后，保证模型能看到当前对话。
        if (!historyFound && history.isNotEmpty()) {
            historyStart = messages.size
            messages += history
            historyEnd = messages.size
            historyFound = true
            warnings += "预设没有启用 chatHistory marker，聊天记录已追加到提示词末尾"
        }

        // 世界书的 at_depth 条目和预设的 in-chat prompt 走同一条插入路径。
        worldInfo.depthEntries.forEach { entry ->
            val content = macroResolver.render(entry.content, macroContext)
            if (content.isBlank()) return@forEach
            inChat += InChatPrompt(
                name = "worldInfo:${entry.depth}",
                content = content,
                role = entry.role,
                depth = entry.depth,
                order = entry.order,
            )
        }

        val result = messages.toMutableList()
        insertInChat(
            messages = result,
            prompts = inChat,
            warnings = warnings,
            historyStart = if (historyFound) historyStart else 0,
            historyEnd = if (historyFound) historyEnd else result.size,
        )

        var finalMessages = if (flattenSystemMessages) {
            flattenSystemMessages(result, historyStart.coerceIn(0, result.size))
        } else {
            result.filter { it.parts.isNotEmpty() }
        }
        if (preset.settings.squashSystemMessages) {
            finalMessages = squashConsecutiveSystem(finalMessages)
        }
        if (preset.settings.assistantPrefill.isNotBlank()) {
            finalMessages = finalMessages + UIMessage.assistant(preset.settings.assistantPrefill).copy(isSynthetic = true)
        }
        if (unknownMarkers.isNotEmpty()) {
            warnings += "未知 marker：${unknownMarkers.joinToString("、")}（已跳过）"
        }
        if (macroResolver.unresolvedMacros.isNotEmpty()) {
            warnings += "未识别的宏：${macroResolver.unresolvedMacros.joinToString("、")}（已置空）"
        }

        val report = StAssemblyReport(
            promptOrderBucket = bucket.characterId,
            orderedEntries = entries.size,
            renderedPrompts = rendered,
            skippedDisabled = skippedDisabled,
            skippedByTrigger = skippedTrigger,
            skippedEmpty = skippedEmpty,
            markerCount = markerCount,
            inChatCount = inChat.size,
            unknownMarkers = unknownMarkers.toList(),
            unresolvedMacros = macroResolver.unresolvedMacros.toList(),
            warnings = (warnings + preset.warnings + worldInfo.warnings).distinct(),
            finalMessageCount = finalMessages.size,
        )
        return StAssemblyResult(finalMessages, report)
    }

    /** 只解析 prompt_order，不组装；预设详情页用它展示发送顺序。 */
    fun orderedPrompts(preset: StPreset): List<Pair<StPrompt, StPromptOrderEntry>> {
        val bucket = chooseBucket(preset, mutableListOf())
        val promptMap = preset.prompts.associateBy { it.identifier }
        val entries = bucket.order.ifEmpty {
            preset.prompts.map { StPromptOrderEntry(it.identifier, true) }
        }
        return entries.mapNotNull { entry -> promptMap[entry.identifier]?.let { it to entry } }
    }

    /** 详情页编辑列表：先按当前顺序列出，再把未挂到 prompt_order 的提示追加到末尾。 */
    fun editablePrompts(preset: StPreset): List<StPromptRow> {
        val bucket = chooseBucket(preset, mutableListOf())
        val promptMap = preset.prompts.associateBy { it.identifier }
        val entries = bucket.order.ifEmpty {
            preset.prompts.map { StPromptOrderEntry(it.identifier, true) }
        }
        val rows = mutableListOf<StPromptRow>()
        val inOrder = mutableSetOf<String>()
        entries.forEachIndexed { index, entry ->
            inOrder += entry.identifier
            val prompt = promptMap[entry.identifier] ?: return@forEachIndexed
            rows += StPromptRow(prompt, index, entry.enabled)
        }
        preset.prompts.filter { it.identifier !in inOrder }.forEach { prompt ->
            rows += StPromptRow(prompt, null, false)
        }
        return rows
    }

    private fun chooseBucket(preset: StPreset, warnings: MutableList<String>): StPromptOrderBucket {
        val usable = preset.promptOrder.filter { it.order.isNotEmpty() }
        if (usable.isEmpty()) {
            if (preset.promptOrder.isNotEmpty()) warnings += "prompt_order 的所有桶都是空的"
            return StPromptOrderBucket()
        }
        return usable.firstOrNull { it.characterId == StPromptOrderBucket.GLOBAL_CHARACTER_ID }
            ?: usable.first().also {
                if (usable.size > 1) warnings += "没有全局桶，使用 character_id=${it.characterId} 的排序"
            }
    }

    private fun triggerMatches(prompt: StPrompt, generationType: StGenerationType): Boolean {
        if (prompt.injectionTrigger.isEmpty()) return true
        return prompt.injectionTrigger.any { it.equals(generationType.stName, ignoreCase = true) }
    }

    private fun resolveMainPrompt(
        marker: StPrompt,
        character: CharacterCard?,
        assistantSystemPrompt: String,
    ): String {
        // 有结构化角色卡时，main 只使用卡片的 system_prompt；assistant.systemPrompt 是旧路径的拼接结果，
        // 再拼进来会和 charDescription / personality / scenario marker 重复。
        if (character != null && !marker.forbidOverrides) {
            return character.systemPrompt
        }
        return assistantSystemPrompt
    }

    private fun insertInChat(
        messages: MutableList<UIMessage>,
        prompts: List<InChatPrompt>,
        warnings: MutableList<String>,
        historyStart: Int,
        historyEnd: Int,
    ) {
        if (prompts.isEmpty()) return
        val nonEmpty = prompts.filter { it.content.isNotBlank() }
        var start = historyStart.coerceIn(0, messages.size)
        var end = historyEnd.coerceIn(start, messages.size)
        nonEmpty.groupBy { it.depth.coerceAtLeast(0) }
            .toSortedMap(compareByDescending { it })
            .forEach { (depth, group) ->
                // 从高 injection_order 开始插入同一位置：后插入的低 order 会落在前面，
                // 最终顺序符合 ST 的「数值小的更靠前、数值大的更靠近最新消息」。
                var inserted = 0
                group.sortedByDescending { it.order }.forEach { prompt ->
                    // depth 是相对聊天历史的深度，不是相对整段组装结果；
                    // depth=0 表示历史末尾，1 表示最后一条历史消息之前。
                    val target = (end - depth).coerceIn(start, end)
                    val index = safeInsertIndex(messages, target).coerceAtLeast(start)
                    if (index < target) {
                        warnings += "In-Chat「${prompt.name}」原定插入位置会拆开工具调用，已前移到安全位置"
                    }
                    messages.add(index, textMessage(prompt.role, prompt.content))
                    inserted++
                }
                // 插入在历史区间内，历史末尾和起点要跟着后移
                end += inserted
                if (end - inserted <= start) start += inserted
            }
    }

    private fun textMessage(role: MessageRole, text: String): UIMessage = when (role) {
        MessageRole.ASSISTANT -> UIMessage.assistant(text)
        MessageRole.SYSTEM -> UIMessage.system(text)
        // ST 只有 system/user/assistant；system 之外的未知角色按 user 发送
        else -> UIMessage.user(text)
    }.copy(isSynthetic = true)

    private fun systemMessage(text: String): UIMessage = UIMessage.system(text).copy(isSynthetic = true)

    private fun safeInsertIndex(messages: List<UIMessage>, target: Int): Int {
        var index = findSafeInsertIndex(messages, target)
        // 不要拆开 assistant(tool call) -> tool result，或连续 tool result
        while (index > 0 && index < messages.size) {
            val previous = messages[index - 1]
            val current = messages[index]
            val splitsTools = (previous.role == MessageRole.ASSISTANT && previous.getTools().isNotEmpty() && current.role == MessageRole.TOOL) ||
                (previous.role == MessageRole.TOOL && current.role == MessageRole.TOOL)
            if (!splitsTools) break
            index--
        }
        return index
    }

    private fun squashConsecutiveSystem(messages: List<UIMessage>): List<UIMessage> {
        val result = mutableListOf<UIMessage>()
        messages.forEach { message ->
            val previous = result.lastOrNull()
            if (message.role == MessageRole.SYSTEM && previous?.role == MessageRole.SYSTEM) {
                val merged = buildString {
                    append(previous.toText())
                    val text = message.toText()
                    if (text.isNotBlank()) {
                        if (isNotEmpty() && !endsWith("\n")) appendLine()
                        append(text)
                    }
                }
                result[result.lastIndex] = previous.copy(parts = listOf(UIMessagePart.Text(merged)))
            } else {
                result += message
            }
        }
        return result
    }

    /**
     * 只保留一条顶层 system：历史之前的 system 全部合并到最前面，历史之后的 system 降级为 user。
     * Claude / Gemini / Responses API 的 provider 实现只会读取第一条 system，其余会被丢弃。
     */
    private fun flattenSystemMessages(messages: List<UIMessage>, historyStart: Int): List<UIMessage> {
        if (messages.none { it.role == MessageRole.SYSTEM }) return messages
        val split = historyStart.coerceIn(0, messages.size)
        val beforeHistory = messages.take(split)
        val afterHistory = messages.drop(split)
        val result = mutableListOf<UIMessage>()

        val leadingSystemText = beforeHistory
            .filter { it.role == MessageRole.SYSTEM }
            .map { it.toText() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (leadingSystemText.isNotBlank()) {
            result += systemMessage(leadingSystemText)
        }
        beforeHistory.filter { it.role != MessageRole.SYSTEM }.forEach { result += it }
        afterHistory.forEach { message ->
            if (message.role == MessageRole.SYSTEM) {
                result += UIMessage.user(message.toText()).copy(isSynthetic = true)
            } else {
                result += message
            }
        }
        return result
    }

    private fun buildMacroContext(
        character: CharacterCard?,
        personaName: String,
        personaDescription: String,
        modelName: String,
        history: List<UIMessage>,
    ): StMacroContext {
        val lastUser = history.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        return StMacroContext(
            characterName = character?.name.orEmpty(),
            userName = personaName,
            personaDescription = personaDescription,
            description = character?.description.orEmpty(),
            personality = character?.personality.orEmpty(),
            scenario = character?.scenario.orEmpty(),
            mesExample = character?.mesExample.orEmpty(),
            modelName = modelName,
            input = lastUser,
            lastMessage = history.lastOrNull()?.toText().orEmpty(),
        )
    }

    /**
     * 把 mes_example 拆成真实的消息对。
     *
     * 兼容 `<START>` 分隔和 `{{user}}:` / `{{char}}:` 前缀；识别不了的块退化为一条 system 消息，
     * 保证示例内容不会丢。
     */
    internal fun parseDialogueExamples(
        raw: String,
        characterName: String,
        userName: String,
    ): List<UIMessage> {
        if (raw.isBlank()) return emptyList()
        val charName = characterName.ifBlank { "char" }
        val name = userName.ifBlank { "user" }
        val blocks = raw.split(Regex("""<START>""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val messages = mutableListOf<UIMessage>()
        blocks.forEach { block ->
            var currentRole: MessageRole? = null
            val buffer = StringBuilder()
            fun flush() {
                if (buffer.isBlank()) return
                val role = currentRole ?: MessageRole.SYSTEM
                messages += textMessage(role, buffer.toString().trim())
                buffer.clear()
            }
            block.lines().forEach { line ->
                val match = Regex("""^\s*([^:：]{1,60})[:：]\s?(.*)$""").find(line)
                val speaker = match?.groupValues?.get(1)?.trim()
                val role = when {
                    speaker == null -> null
                    speaker.equals("{{user}}", true) || speaker.equals(name, true) -> MessageRole.USER
                    speaker.equals("{{char}}", true) || speaker.equals(charName, true) -> MessageRole.ASSISTANT
                    else -> null
                }
                if (role != null) {
                    flush()
                    currentRole = role
                    buffer.append(match?.groupValues?.get(2).orEmpty())
                } else {
                    if (buffer.isNotEmpty()) buffer.append('\n')
                    buffer.append(line)
                }
            }
            flush()
        }
        return messages
    }

    private sealed interface Node {
        data class Text(val message: UIMessage) : Node
        data class Marker(val identifier: String, val prompt: StPrompt) : Node
    }

    private data class InChatPrompt(
        val name: String,
        val content: String,
        val role: MessageRole,
        val depth: Int,
        val order: Int,
    )
}
