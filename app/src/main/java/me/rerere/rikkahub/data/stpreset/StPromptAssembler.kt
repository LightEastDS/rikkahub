package me.rerere.rikkahub.data.stpreset

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.findSafeInsertIndex
import me.rerere.rikkahub.data.db.entity.ConversationCheckpointEntity
import me.rerere.rikkahub.data.model.CharacterCard
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlin.time.Clock

data class StAssemblyResult(
    val messages: List<UIMessage>,
    val report: StAssemblyReport,
)

data class StAssemblyRequest(
    val preset: StPreset,
    val liveMessages: List<UIMessage>,
    val character: CharacterCard? = null,
    val assistantSystemPrompt: String = "",
    val personaName: String = "",
    val personaDescription: String = "",
    val modelName: String = "",
    val generationType: StGenerationType = StGenerationType.NORMAL,
    val extraSystemPrompt: String = "",
    val worldInfo: StWorldInfoResult = StWorldInfoResult(),
    val checkpoint: ConversationCheckpointEntity? = null,
    val milestoneMessageIds: Set<String> = emptySet(),
    val variables: MutableMap<String, String> = mutableMapOf(),
    val globalVariables: MutableMap<String, String> = mutableMapOf(),
    val random: Random = Random.Default,
    val now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val flattenSystemMessages: Boolean = false,
)

/**
 * SillyTavern Chat Completion 预设的提示词组装器。
 *
 * 实现了 DeepSeek 极速缓存友好的三区动静隔离时序架构（Zone A / Zone B / Zone C）：
 * - Zone A（静态前缀区）：系统基础设定、角色卡、常驻世界书，绝对冻结保证 100% 缓存命中；
 * - Zone B（稳定历史区）：检查点结构化摘要与单调递增对话历史，联动 isHidden 过滤；
 * - Zone C（动态后缀区）：关键词动态触发的世界书、环境感知时间宏下沉、In-Chat 动态提示与 Assistant Prefill。
 */
object StPromptAssembler {
    val STATIC_MARKERS = setOf(
        "main",
        "worldInfoBefore",
        "charDescription",
        "charPersonality",
        "personaDescription",
        "scenario",
        "dialogueExamples",
    )

    private val DYNAMIC_MACRO_REGEX = Regex(
        """\{\{\s*(time|cur_time|datetime|cur_date|date|isotime|isodate|weekday|input|lastmessage|last_message|random|pick|roll)\b""",
        RegexOption.IGNORE_CASE
    )

    fun isDynamicContent(content: String): Boolean {
        return content.contains(DYNAMIC_MACRO_REGEX)
    }

    /**
     * 对 prompt_order 进行 DeepSeek 极速前缀缓存友好的自动拓扑排序：
     * 1. Zone A（静态前缀区）：静态 marker（main, worldInfoBefore, charDescription, charPersonality, personaDescription, scenario, dialogueExamples）以及静态系统提示词（SYSTEM 角色且不含动态宏）置顶；
     * 2. Zone B（稳定历史区）：chatHistory 提示词放置在静态前缀之后；
     * 3. Zone C（动态后缀区）：worldInfoAfter、含有时间/日期/输入/随机宏的提示词、以及置于历史之后的非 SYSTEM 提示词沉底。
     *
     * 保持各分区内部条目原本的相对顺序。
     */
    fun optimizeOrder(prompts: List<StPrompt>, order: List<StPromptOrderEntry>): List<StPromptOrderEntry> {
        val promptMap = prompts.associateBy { it.identifier }
        val zoneA = mutableListOf<StPromptOrderEntry>()
        val zoneB = mutableListOf<StPromptOrderEntry>()
        val zoneC = mutableListOf<StPromptOrderEntry>()

        var passedHistory = false
        order.forEach { entry ->
            val prompt = promptMap[entry.identifier]
            if (prompt == null) {
                zoneC += entry
                return@forEach
            }
            if (prompt.marker) {
                when (prompt.identifier) {
                    "chatHistory" -> {
                        zoneB += entry
                        passedHistory = true
                    }
                    "worldInfoAfter" -> zoneC += entry
                    in STATIC_MARKERS -> zoneA += entry
                    else -> if (passedHistory) zoneC += entry else zoneA += entry
                }
            } else if (prompt.isInChat) {
                zoneC += entry
            } else if (isDynamicContent(prompt.content)) {
                zoneC += entry
            } else if (prompt.role == MessageRole.SYSTEM) {
                zoneA += entry
            } else {
                if (passedHistory) zoneC += entry else zoneA += entry
            }
        }

        if (zoneB.isEmpty()) {
            val historyPrompt = prompts.firstOrNull { it.marker && it.identifier == "chatHistory" }
            if (historyPrompt != null) {
                zoneB += StPromptOrderEntry(identifier = "chatHistory", enabled = true)
            }
        }

        return zoneA + zoneB + zoneC
    }

    fun assemble(req: StAssemblyRequest): StAssemblyResult {
        val preset = req.preset
        val character = req.character
        val warnings = mutableListOf<String>()
        val bucket = chooseBucket(preset, warnings)
        val promptMap = preset.prompts.associateBy { it.identifier }
        val entries = bucket.order.ifEmpty {
            preset.prompts.map { StPromptOrderEntry(identifier = it.identifier, enabled = true) }
        }

        val macroContext = buildMacroContext(
            character = character,
            personaName = req.personaName,
            personaDescription = req.personaDescription,
            modelName = req.modelName,
            history = req.liveMessages,
        )

        // Pass 1 & Pass 2: 变量预扫描与拓扑依赖展开
        val preScannedVars = StMacroResolver.preScanVariables(
            prompts = preset.prompts,
            persistedVariables = req.variables,
            context = macroContext,
            random = req.random,
            now = req.now,
        )
        req.variables.putAll(preScannedVars)

        val macroResolver = StMacroResolver(
            variables = req.variables,
            globalVariables = req.globalVariables,
            random = req.random,
            now = req.now,
        )

        val timeMacroRegex = Regex("""\{\{\s*(time|cur_time|datetime|cur_date|date|isotime|isodate|weekday)\s*\}\}""", RegexOption.IGNORE_CASE)
        var presetRequiresEnvironmentTime = false

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
            if (!triggerMatches(prompt, req.generationType)) {
                skippedTrigger++
                return@forEach
            }
            if (prompt.content.contains(timeMacroRegex)) {
                presetRequiresEnvironmentTime = true
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
            val isDynamic = isDynamicContent(prompt.content)
            nodes += Node.Text(
                message = textMessage(prompt.role, content),
                role = prompt.role,
                isDynamic = isDynamic,
            )
        }

        // =========================================================================
        // 【Zone A: 静态前缀区 (Static Prefix)】-> 绝对冻结，保证第 0 个 token 开始全中
        // =========================================================================
        val zoneA = mutableListOf<UIMessage>()
        if (req.extraSystemPrompt.isNotBlank()) {
            zoneA += systemMessage(req.extraSystemPrompt)
        }

        // =========================================================================
        // 【Zone B: 稳定历史区 (Append-only Stream)】-> 严格单调递增流
        // =========================================================================
        val zoneB = mutableListOf<UIMessage>()

        // =========================================================================
        // 【Zone C: 动态后缀区 (Turn-Tail Dynamic)】-> 所有易变内容沉底
        // =========================================================================
        val zoneC = mutableListOf<UIMessage>()

        val unknownMarkers = linkedSetOf<String>()
        var historyFound = false

        fun resolveMarker(identifier: String, prompt: StPrompt, targetList: MutableList<UIMessage>) {
            when (identifier) {
                "main" -> {
                    val text = resolveMainPrompt(prompt, character, req.assistantSystemPrompt)
                    if (text.isNotBlank()) targetList += systemMessage(text)
                }
                "worldInfoBefore" -> {
                    if (req.worldInfo.constantEntries.isNotEmpty()) {
                        targetList += req.worldInfo.constantEntries.map { it.toSystemMessage() }
                    } else if (req.worldInfo.before.isNotBlank()) {
                        val text = macroResolver.render(req.worldInfo.before, macroContext)
                        if (text.isNotBlank()) targetList += systemMessage(text)
                    }
                }
                "worldInfoAfter" -> {
                    if (req.worldInfo.dynamicTriggeredEntries.isNotEmpty()) {
                        zoneC += req.worldInfo.dynamicTriggeredEntries.map { it.toSystemMessage() }
                    } else if (req.worldInfo.after.isNotBlank()) {
                        val text = macroResolver.render(req.worldInfo.after, macroContext)
                        if (text.isNotBlank()) zoneC += systemMessage(text)
                    }
                }
                "charDescription" -> character?.description?.takeIf { it.isNotBlank() }?.let {
                    targetList += systemMessage(it)
                }
                "charPersonality" -> character?.personality?.takeIf { it.isNotBlank() }?.let {
                    targetList += systemMessage(it)
                }
                "personaDescription" -> if (req.personaDescription.isNotBlank()) {
                    targetList += systemMessage(req.personaDescription)
                }
                "scenario" -> character?.scenario?.takeIf { it.isNotBlank() }?.let {
                    targetList += systemMessage(it)
                }
                "dialogueExamples" -> character?.mesExample?.takeIf { it.isNotBlank() }?.let {
                    targetList += parseDialogueExamples(
                        raw = it,
                        characterName = character.name,
                        userName = req.personaName,
                    )
                }
                else -> unknownMarkers += identifier
            }
        }

        fun populateZoneB() {
            if (req.checkpoint != null) {
                zoneB += systemMessage(
                    """
                    <conversation_summary>
                    [以下是历史会话（第 1 轮至第 ${req.checkpoint.boundaryFloor} 轮）的核心大纲与背景记忆，请将其视为既定事实并在此基础上继续对话]
                    ${req.checkpoint.summaryContent}
                    </conversation_summary>
                    """.trimIndent()
                )
                // 高光时刻穿透 (Milestone Whitelist)：压缩区间内被收藏/固定的非隐藏消息
                val boundaryIndex = req.liveMessages.indexOfLast { it.id.toString() == req.checkpoint.boundaryNodeId }
                val beforeCheckpoint = if (boundaryIndex >= 0) {
                    req.liveMessages.take(boundaryIndex + 1)
                } else if (req.checkpoint.boundaryFloor in 1..req.liveMessages.size) {
                    req.liveMessages.take(req.checkpoint.boundaryFloor)
                } else {
                    emptyList()
                }
                val milestones = beforeCheckpoint.filter { msg ->
                    !msg.isHidden && req.milestoneMessageIds.contains(msg.id.toString())
                }
                zoneB += milestones

                val liveAfter = filterAfterCheckpoint(req.liveMessages, req.checkpoint)
                zoneB += liveAfter.filterNot { it.isHidden }
            } else {
                zoneB += req.liveMessages.filterNot { it.isHidden }
            }
            historyFound = true
        }

        var inPostHistory = false
        nodes.forEach { node ->
            when (node) {
                is Node.Text -> {
                    if (node.isDynamic) {
                        zoneC += node.message
                    } else if (node.role == MessageRole.SYSTEM) {
                        // 静态系统提示词始终置于 Zone A 静态前缀区，保证 100% 缓存命中
                        zoneA += node.message
                    } else {
                        if (inPostHistory) {
                            zoneC += node.message
                        } else {
                            zoneA += node.message
                        }
                    }
                }
                is Node.Marker -> {
                    if (node.identifier == "chatHistory") {
                        inPostHistory = true
                    } else if (node.identifier in STATIC_MARKERS) {
                        // 静态设定 marker（main/charDesc/scenario/examples等）始终进入 Zone A
                        resolveMarker(node.identifier, node.prompt, zoneA)
                    } else if (node.identifier == "worldInfoAfter") {
                        // 动态触发世界书始终进入 Zone C
                        resolveMarker(node.identifier, node.prompt, zoneC)
                    } else {
                        if (inPostHistory) {
                            resolveMarker(node.identifier, node.prompt, zoneC)
                        } else {
                            resolveMarker(node.identifier, node.prompt, zoneA)
                        }
                    }
                }
            }
        }

        // Zone B: 填充历史会话流
        populateZoneB()

        if (!inPostHistory && req.liveMessages.isNotEmpty()) {
            warnings += "预设没有启用 chatHistory marker，聊天记录已追加到提示词末尾"
        }

        // 常驻世界书保底：如果没有显式启用 worldInfoBefore marker，常驻条目注入到 Zone A
        if (!preset.hasEnabledMarker("worldInfoBefore") && req.worldInfo.constantEntries.isNotEmpty() && zoneA.none { msg -> req.worldInfo.constantEntries.any { it.content == msg.toText() } }) {
            zoneA += req.worldInfo.constantEntries.map { it.toSystemMessage() }
        }

        // 动态世界书：动静分离，始终注入到 Zone C 头部（历史之后）
        if (req.worldInfo.dynamicTriggeredEntries.isNotEmpty() && zoneC.none { msg -> req.worldInfo.dynamicTriggeredEntries.any { it.content == msg.toText() } }) {
            zoneC.addAll(0, req.worldInfo.dynamicTriggeredEntries.map { it.toSystemMessage() })
        }

        // 动态环境感知（时间宏下沉）
        if (presetRequiresEnvironmentTime) {
            val formattedNow = req.now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            zoneC += systemMessage("[Environment Context: Time=$formattedNow]")
        }

        // 世界书的 at_depth 条目和预设的 in-chat prompt 走同一条插入路径。
        req.worldInfo.depthEntries.forEach { entry ->
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

        val messages = (zoneA + zoneB + zoneC).toMutableList()
        val historyStart = zoneA.size
        val historyEnd = zoneA.size + zoneB.size

        insertInChat(
            messages = messages,
            prompts = inChat,
            warnings = warnings,
            historyStart = historyStart,
            historyEnd = historyEnd,
        )

        var finalMessages = if (req.flattenSystemMessages) {
            flattenSystemMessages(messages, historyStart.coerceIn(0, messages.size))
        } else {
            messages.filter { it.parts.isNotEmpty() }
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
            warnings = (warnings + preset.warnings + req.worldInfo.warnings).distinct(),
            finalMessageCount = finalMessages.size,
        )
        return StAssemblyResult(finalMessages, report)
    }

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
        checkpoint: ConversationCheckpointEntity? = null,
        milestoneMessageIds: Set<String> = emptySet(),
        variables: MutableMap<String, String> = mutableMapOf(),
        globalVariables: MutableMap<String, String> = mutableMapOf(),
        random: Random = Random.Default,
        now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        flattenSystemMessages: Boolean = false,
    ): StAssemblyResult = assemble(
        StAssemblyRequest(
            preset = preset,
            liveMessages = history,
            character = character,
            assistantSystemPrompt = assistantSystemPrompt,
            personaName = personaName,
            personaDescription = personaDescription,
            modelName = modelName,
            generationType = generationType,
            extraSystemPrompt = extraSystemPrompt,
            worldInfo = worldInfo,
            checkpoint = checkpoint,
            milestoneMessageIds = milestoneMessageIds,
            variables = variables,
            globalVariables = globalVariables,
            random = random,
            now = now,
            flattenSystemMessages = flattenSystemMessages,
        )
    )

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
        if (character != null && !marker.forbidOverrides) {
            return character.systemPrompt
        }
        return assistantSystemPrompt
    }

    private fun filterAfterCheckpoint(
        messages: List<UIMessage>,
        checkpoint: ConversationCheckpointEntity,
    ): List<UIMessage> {
        val boundaryIndex = messages.indexOfLast { it.id.toString() == checkpoint.boundaryNodeId }
        return if (boundaryIndex >= 0 && boundaryIndex < messages.size - 1) {
            messages.subList(boundaryIndex + 1, messages.size)
        } else if (checkpoint.boundaryFloor in 1 until messages.size) {
            messages.drop(checkpoint.boundaryFloor)
        } else {
            messages
        }
    }

    private fun StPreset.hasEnabledMarker(identifier: String): Boolean {
        val marker = prompts.firstOrNull { it.identifier == identifier } ?: return false
        val hasOrder = promptOrder.any { it.order.isNotEmpty() }
        if (!hasOrder) return true
        return promptOrder.any { bucket ->
            bucket.order.any { entry -> entry.enabled && entry.identifier == marker.identifier }
        }
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
                var inserted = 0
                group.sortedByDescending { it.order }.forEach { prompt ->
                    val target = (end - depth).coerceIn(start, end)
                    val index = safeInsertIndex(messages, target).coerceAtLeast(start)
                    if (index < target) {
                        warnings += "In-Chat「${prompt.name}」原定插入位置会拆开工具调用，已前移到安全位置"
                    }
                    messages.add(index, textMessage(prompt.role, prompt.content))
                    inserted++
                }
                end += inserted
                if (end - inserted <= start) start += inserted
            }
    }

    private fun textMessage(role: MessageRole, text: String): UIMessage = when (role) {
        MessageRole.ASSISTANT -> UIMessage.assistant(text)
        MessageRole.SYSTEM -> UIMessage.system(text)
        else -> UIMessage.user(text)
    }.copy(isSynthetic = true)

    private fun systemMessage(text: String): UIMessage = UIMessage.system(text).copy(isSynthetic = true)

    private fun safeInsertIndex(messages: List<UIMessage>, target: Int): Int {
        var index = findSafeInsertIndex(messages, target)
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
        data class Text(
            val message: UIMessage,
            val role: MessageRole,
            val isDynamic: Boolean,
        ) : Node
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
