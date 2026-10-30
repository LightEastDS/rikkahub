package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.RetryPolicy
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.withRetry
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transformThinkTags
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.db.entity.ConversationCheckpointEntity
import me.rerere.rikkahub.data.repository.ConversationCheckpointRepository
import me.rerere.rikkahub.data.repository.StPresetRepository
import me.rerere.rikkahub.data.repository.StVariableRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.stpreset.StAssemblyReport
import me.rerere.rikkahub.data.stpreset.StGenerationType
import me.rerere.rikkahub.data.stpreset.StMacroContext
import me.rerere.rikkahub.data.stpreset.StMacroResolver
import me.rerere.rikkahub.data.stpreset.StPreset
import me.rerere.rikkahub.data.stpreset.StPromptAssembler
import me.rerere.rikkahub.data.stpreset.StRegexPipeline
import me.rerere.rikkahub.data.stpreset.StWorldInfoEngine
import me.rerere.rikkahub.data.repository.memoryId
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ChatRequestFactory"

/**
 * 一轮对话的请求上下文，按会话固定的配置解析一次。
 *
 * 正常对话和压缩用同一份上下文组装请求，系统提示词、工具定义、参数和 sessionId 都相同，
 * 压缩请求的前缀才能和上一轮对话一致，命中提示词缓存。
 */
class ChatTurnContext(
    val settings: Settings,
    val assistant: Assistant,
    val model: Model,
    val providerSetting: ProviderSetting,
    val conversationId: Uuid,
    val conversationSystemPrompt: String?,
    val workspaceCwd: String?,
    val memories: List<MemoryFile>,
    val tools: List<Tool>,
    /** 非空时聊天请求走 ST 预设组装器；为 null 时完全沿用旧路径。 */
    val stPreset: StPreset? = null,
    val checkpoint: ConversationCheckpointEntity? = null,
    val milestoneMessageIds: Set<String> = emptySet(),
)

/** 把会话变成发给模型的请求：系统提示词、上下文裁剪、输入输出转换都在这里 */
class ChatRequestFactory(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val chatToolFactory: ChatToolFactory,
    private val memoryRepository: MemoryRepository,
    private val templateTransformer: TemplateTransformer,
    private val workspaceRepository: WorkspaceRepository,
    private val stPresetRepository: StPresetRepository,
    private val stVariableRepository: StVariableRepository,
    private val checkpointRepository: ConversationCheckpointRepository,
) {
    private val inputTransformers: List<InputMessageTransformer> = listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
        templateTransformer,
        WorkspaceReminderTransformer(workspaceRepository),
    )

    // ST 预设模式下只保留内容级转换：PromptInjection/Placeholder 由组装器接管
    private val presetHistoryTransformers: List<InputMessageTransformer> = listOf(
        TimeReminderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
        templateTransformer,
        WorkspaceReminderTransformer(workspaceRepository),
    )

    private val outputTransformers: List<OutputMessageTransformer> = listOf(        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )

    /** 解析本轮的助手、模型、记忆和工具；MCP 服务名不合法时抛出 InvalidMcpServerNamesException */
    suspend fun createContext(settings: Settings, conversation: Conversation): ChatTurnContext {
        // 模型、思考级别、搜索、工具等以会话上固定的配置为准
        val assistant = settings.getAssistantOf(conversation)
        val model = settings.getChatModelOf(conversation)
            ?: throw IllegalStateException("No chat model selected")
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val stPreset = assistant.stPresetId?.let { presetId ->
            runCatching { stPresetRepository.getRecord(presetId) }.getOrNull()
                ?.takeIf { it.enabled }
                ?.preset
        }
        if (assistant.stPresetId != null && stPreset == null) {
            Log.w(TAG, "ST preset ${assistant.stPresetId} is missing or disabled; falling back to legacy prompts")
        }
        val checkpoint = checkpointRepository.getLatest(conversation.id)
        val milestoneMessageIds = conversation.messageNodes
            .filter { it.isFavorite }
            .map { it.currentMessage.id.toString() }
            .toSet()
        return ChatTurnContext(
            settings = settings,
            assistant = assistant,
            model = model,
            providerSetting = provider,
            conversationId = conversation.id,
            conversationSystemPrompt = conversation.customSystemPrompt,
            workspaceCwd = conversation.workspaceCwd,
            memories = if (assistant.enableMemory) memoryRepository.getFiles(assistant.memoryId) else emptyList(),
            tools = chatToolFactory.createTools(
                settings = settings,
                assistant = assistant,
                model = model,
                workspaceCwd = conversation.workspaceCwd,
            ),
            stPreset = stPreset,
            checkpoint = checkpoint,
            milestoneMessageIds = milestoneMessageIds,
        )
    }

    /**
     * 本轮交给 runAgent 的请求和钩子。[messages] 的最后一条是助手消息时从它继续，否则新建一条助手消息。
     * [processingStatus] 用来显示 OCR 这类输入转换的进度。
     */
    fun createAgent(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?>,
        generationType: StGenerationType = StGenerationType.NORMAL,
    ): Pair<AgentRequest, AgentHooks> {
        val request = AgentRequest(
            provider = usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.CHAT, turn.assistant.id, turn.conversationId),
            ),
            providerSetting = turn.providerSetting,
            messages = messages,
            tools = turn.tools,
            stream = turn.assistant.streamOutput,
            retry = turn.settings.retryPolicy(),
        )
        val hooks = object : AgentHooks {
            override suspend fun prepareStep(messages: List<UIMessage>): StepRequest {
                Log.i(TAG, "prepareStep: ${turn.model.id}")
                return buildStep(turn, messages, processingStatus, generationType)
            }

            override suspend fun previewStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }

            override suspend fun finishStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.transforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .onGenerationFinish(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }
        }
        return request to hooks
    }

    /**
     * 生成压缩摘要。
     *
     * 请求按正常对话组装（系统提示词、工具定义、参数和 sessionId 都相同），只在末尾追加压缩指令，
     * 前缀与上一轮对话一致，可以命中提示词缓存，模型也能看到工具调用和结果。
     * 工具定义必须保留：它属于请求前缀，去掉或改动 tool_choice 都会让缓存失效。
     */
    suspend fun generateIsolatedCompressionSummary(
        model: Model,
        provider: ProviderSetting,
        messages: List<UIMessage>,
        compressPrompt: String,
        targetTokensK: Int,
        assistantId: Uuid,
        conversationId: Uuid,
        settings: Settings,
    ): String {
        val effectivePrompt = compressPrompt
            .replace("{targetTokens}", targetTokensK.toString())
            .replace("{targetK}", targetTokensK.toString())

        val requestMessages = buildList {
            add(UIMessage.system(prompt = effectivePrompt))
            messages.forEach { msg ->
                add(UIMessage(role = msg.role, parts = msg.parts))
            }
            add(UIMessage.user("请基于上述历史记录生成压缩总结，严格控制在约 ${targetTokensK}K tokens 以内。"))
        }

        val result = withRetry(
            policy = settings.retryPolicy(),
            onRetry = { Log.w(TAG, "Compression request failed, retrying in ${it.delay}", it.cause) },
        ) {
            usageRecorder.wrap(
                providerManager.getProviderByType(provider),
                UsageTag(UsageSource.COMPRESSION, assistantId, conversationId),
            ).generateText(
                providerSetting = provider,
                messages = requestMessages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    sessionId = conversationId.toString(),
                ),
            )
        }
        val summary = result.message.compressionSummaryOrNull()
            ?: result.message.toText().trim()
        return summary.ifBlank { error("压缩模型返回内容为空") }
    }

    suspend fun generateCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        compressPrompt: String,
    ): String {
        val (requestMessages, params) = buildStep(turn, messages)
        // 压缩指令不经过输入转换器，模板转换器会把其中的花括号当成模板语法
        val result = withRetry(
            policy = turn.settings.retryPolicy(),
            onRetry = { Log.w(TAG, "Compression request failed, retrying in ${it.delay}", it.cause) },
        ) {
            usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.COMPRESSION, turn.assistant.id, turn.conversationId),
            ).generateText(
                providerSetting = turn.providerSetting,
                messages = requestMessages + UIMessage.user(compressPrompt),
                // 助手的输出上限是给聊天回复定的，结构化摘要写不完会被截断；max_tokens 不属于请求前缀，改了不影响缓存
                params = params.copy(maxTokens = null),
            )
        }
        val summary = result.message.compressionSummaryOrNull()
            ?: error(context.getString(R.string.chat_page_compress_tool_call_response))
        return summary.ifBlank { error("Failed to generate compressed summary") }
    }

    /** 一步请求实际发出的消息和参数；正常对话和压缩共用，二者的请求前缀必须一致才能命中提示词缓存 */
    private suspend fun buildStep(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        generationType: StGenerationType = StGenerationType.NORMAL,
    ): StepRequest {
        val assistant = turn.assistant
        val preset = turn.stPreset
        if (preset != null) {
            return buildPresetStep(turn, preset, messages, processingStatus, generationType)
        }
        val requestMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !turn.conversationSystemPrompt.isNullOrBlank()) {
                        turn.conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }

                // 记忆
                if (assistant.enableMemory) {
                    appendLine()
                    append(buildMemoryPrompt(turn.memories))
                }
                // 工具prompt
                turn.tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(turn.model, messages.filter { !it.isHidden }))
                }
            }
            if (system.isNotBlank()) {
                add(UIMessage.system(prompt = system).copy(isSynthetic = true))
            }
            if (turn.checkpoint != null) {
                val cpPrompt = """
                    <conversation_summary>
                    [以下是历史会话（第 ${turn.checkpoint.startFloor} 轮至第 ${turn.checkpoint.boundaryFloor} 轮）的核心大纲与背景记忆，请将其视为既定事实并在此基础上继续对话]
                    ${turn.checkpoint.summaryContent}
                    </conversation_summary>
                """.trimIndent()
                add(UIMessage.system(prompt = cpPrompt).copy(isSynthetic = true))
            }
            val effectiveMessages = if (turn.checkpoint != null) {
                val cp = turn.checkpoint
                val startIdx = (cp.startFloor - 1).coerceAtLeast(0)
                val endIdx = cp.boundaryFloor.coerceAtMost(messages.size)
                val before = if (startIdx > 0 && startIdx <= messages.size) messages.take(startIdx) else emptyList()
                val middleMilestones = if (startIdx < endIdx) {
                    messages.subList(startIdx, endIdx).filter { msg ->
                        turn.milestoneMessageIds.contains(msg.id.toString())
                    }
                } else emptyList()
                val after = if (endIdx < messages.size) messages.drop(endIdx) else emptyList()
                before + middleMilestones + after
            } else {
                messages
            }
            addAll(effectiveMessages.limitContext(assistant.contextMessageLimit))
        }.transforms(
            transformers = inputTransformers,
            context = context,
            model = turn.model,
            assistant = assistant,
            settings = turn.settings,
            processingStatus = processingStatus,
            workspaceCwd = turn.workspaceCwd,
        )
        val params = TextGenerationParams(
            model = turn.model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = turn.tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(turn.model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(turn.model.customBodies)
            },
            sessionId = turn.conversationId.toString(),
        )
        return StepRequest(requestMessages, params)
    }

    /**
     * ST 预设路径。
     *
     * 历史先走内容级转换，再用预设 Regex 做历史压缩，最后交给组装器插入 marker / relative / in-chat 提示。
     * ModeInjection 与 Lorebook 不再由 PromptInjectionTransformer 注入，避免重复。
     */
    private suspend fun buildPresetStep(
        turn: ChatTurnContext,
        preset: StPreset,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?>,
        generationType: StGenerationType,
    ): StepRequest {
        val assistant = turn.assistant
        val history = messages.limitContext(assistant.contextMessageLimit)
            .transforms(
                transformers = presetHistoryTransformers,
                context = context,
                model = turn.model,
                assistant = assistant,
                settings = turn.settings,
                processingStatus = processingStatus,
                workspaceCwd = turn.workspaceCwd,
            )

        val scopeId = turn.conversationId.toString()
        val presetScopeId = preset.id.toString()
        val presetVariables = stVariableRepository.load(presetScopeId)
        val conversationVariables = stVariableRepository.load(scopeId)
        val variables = (presetVariables + conversationVariables).toMutableMap()
        val globalVariables = stVariableRepository.loadGlobal()
        val character = assistant.characterCard
        val personaName = turn.settings.displaySetting.userNickname
        val personaDescription = turn.settings.displaySetting.userPersona
        val macroContext = StMacroContext(
            characterName = character?.name.orEmpty(),
            userName = personaName,
            personaDescription = personaDescription,
            description = character?.description.orEmpty(),
            personality = character?.personality.orEmpty(),
            scenario = character?.scenario.orEmpty(),
            mesExample = character?.mesExample.orEmpty(),
            modelName = turn.model.displayName,
            input = history.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty(),
            lastMessage = history.lastOrNull()?.toText().orEmpty(),
        )
        val regexMacroResolver = StMacroResolver(variables, globalVariables)
        val regexResult = StRegexPipeline.applyToMessages(
            messages = history,
            scripts = preset.regexScripts,
            substitute = { regexMacroResolver.render(it, macroContext) },
        )

        val worldInfo = StWorldInfoEngine.collect(
            characterBook = preset.characterBook ?: character?.characterBook,
            lorebooks = turn.settings.lorebooks.filter { it.id in assistant.lorebookIds },
            messages = regexResult.messages,
        )
        // Claude / Gemini / OpenAI Responses 只读取第一条 system，其余 system message 会被 provider 丢掉
        val flattenSystemMessages = when (val provider = turn.providerSetting) {
            is ProviderSetting.Claude -> true
            is ProviderSetting.Google -> true
            is ProviderSetting.OpenAI -> provider.useResponseApi
            else -> false
        }

        val extraSystem = buildString {
            if (assistant.enableMemory) {
                append(buildMemoryPrompt(turn.memories))
            }
            turn.tools.forEach { tool ->
                if (isNotEmpty()) appendLine()
                append(tool.systemPrompt(turn.model, regexResult.messages.filter { !it.isHidden }))
            }
            // 预设没有启用 main marker 时不能把助手的 system prompt 弄丢
            if (!preset.hasEnabledMarker("main") && assistant.systemPrompt.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(assistant.systemPrompt)
            }
            // 没有 worldInfo marker 时把世界书放在 system 里，避免绑定了 Lorebook 却完全不生效
            if (!preset.hasEnabledMarker("worldInfoBefore") && worldInfo.before.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(regexMacroResolver.render(worldInfo.before, macroContext))
            }
            if (!preset.hasEnabledMarker("worldInfoAfter") && worldInfo.after.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(regexMacroResolver.render(worldInfo.after, macroContext))
            }
        }

        val assembly = StPromptAssembler.assemble(
            preset = preset,
            history = regexResult.messages,
            character = character,
            assistantSystemPrompt = assistant.systemPrompt,
            personaName = personaName,
            personaDescription = personaDescription,
            modelName = turn.model.displayName,
            generationType = generationType,
            extraSystemPrompt = extraSystem,
            worldInfo = worldInfo,
            checkpoint = turn.checkpoint,
            milestoneMessageIds = turn.milestoneMessageIds,
            variables = variables,
            globalVariables = globalVariables,
            flattenSystemMessages = flattenSystemMessages,
        )
        logAssemblyReport(preset, assembly.report)

        // setvar/getvar 在组装过程中可能修改变量，组装完统一持久化
        stVariableRepository.save(scopeId, variables)
        stVariableRepository.saveGlobal(globalVariables)

        val params = TextGenerationParams(
            model = turn.model,
            temperature = assistant.temperature ?: preset.settings.temperature,
            topP = assistant.topP ?: preset.settings.topP,
            maxTokens = assistant.maxTokens ?: preset.settings.maxTokens,
            tools = turn.tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(turn.model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(turn.model.customBodies)
            },
            sessionId = turn.conversationId.toString(),
        )
        return StepRequest(assembly.messages, params)
    }

    private fun logAssemblyReport(preset: StPreset, report: StAssemblyReport) {
        Log.i(
            TAG,
            "ST preset ${preset.name}: ${report.renderedPrompts} prompts, " +
                "${report.inChatCount} in-chat, ${report.finalMessageCount} messages, " +
                "warnings=${report.warnings.size}"
        )
        report.warnings.take(10).forEach { Log.w(TAG, "ST preset: $it") }
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

/**
 * 取出压缩请求回复里的摘要正文。
 *
 * 指令要求不调用工具，模型仍可能发起调用。这时附带的文本只是"我先看看文件"之类的说明，
 * 当成摘要保存会让之后的请求丢掉全部历史，所以返回 null 交给调用方报错。
 * 压缩请求不经过输出转换器，正文开头的 <think> 思考内容也要在这里剥掉，否则会存进摘要。
 */
internal fun UIMessage.compressionSummaryOrNull(): String? {
    if (getTools().isNotEmpty()) return null
    return listOf(this)
        .transformThinkTags(now = Clock.System.now(), generationFinished = true)
        .single()
        .toText()
        .trim()
}

/**
 * 输出转换只处理这一步新生成的 part：之前步骤的内容已经处理过，再处理一遍会让正则这类改写叠加，
 * 也能让每一步开头的 <think> 都被识别出来。
 */
internal suspend fun UIMessage.transformNewParts(
    newPartsFrom: Int,
    transform: suspend (List<UIMessage>) -> List<UIMessage>,
): UIMessage {
    val transformed = transform(listOf(copy(parts = parts.drop(newPartsFrom)))).single()
    return transformed.copy(parts = parts.take(newPartsFrom) + transformed.parts)
}

/** 标题、建议、记忆整理这类后台请求的参数：只带模型自己的自定义请求头和请求体 */
internal fun backgroundTextGenerationParams(
    model: Model,
    sessionId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = sessionId.toString(),
)

internal fun Settings.retryPolicy() =
    if (networkSetting.enableAutoRetry) RetryPolicy.NETWORK else RetryPolicy.NONE
