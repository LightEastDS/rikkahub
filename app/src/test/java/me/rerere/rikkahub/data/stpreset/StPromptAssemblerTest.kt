package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.entity.ConversationCheckpointEntity
import me.rerere.rikkahub.data.model.CharacterCard
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StPromptAssemblerTest {
    private fun preset(
        prompts: List<StPrompt>,
        order: List<StPromptOrderEntry>,
        squash: Boolean = false,
        prefill: String = "",
    ) = StPreset(
        name = "test",
        prompts = prompts,
        promptOrder = listOf(StPromptOrderBucket(StPromptOrderBucket.GLOBAL_CHARACTER_ID, order)),
        settings = StGenerationSettings(squashSystemMessages = squash, assistantPrefill = prefill),
    )

    private fun p(
        identifier: String,
        content: String = "",
        role: MessageRole = MessageRole.SYSTEM,
        marker: Boolean = false,
        inChat: Boolean = false,
        depth: Int = 4,
        order: Int = 100,
        trigger: Set<String> = emptySet(),
    ) = StPrompt(identifier = identifier, name = identifier, role = role, content = content,
        marker = marker, injectionPosition = if (inChat) 1 else 0, injectionDepth = depth,
        injectionOrder = order, injectionTrigger = trigger)

    @Test
    fun `follows prompt order and inserts in-chat prompt into history by depth`() {
        val preset = preset(
            prompts = listOf(
                p("main", marker = true),
                p("pre", "PRE {{char}}"),
                p("chatHistory", marker = true),
                p("post", "POST", role = MessageRole.USER),
                p("inChat", "IN", inChat = true, depth = 1),
            ),
            order = listOf(
                StPromptOrderEntry("main", true),
                StPromptOrderEntry("pre", true),
                StPromptOrderEntry("chatHistory", true),
                StPromptOrderEntry("post", true),
                StPromptOrderEntry("inChat", true),
            ),
        )
        val history = listOf(UIMessage.user("u1"), UIMessage.assistant("a1"))
        val result = StPromptAssembler.assemble(
            preset = preset,
            history = history,
            character = CharacterCard(name = "Alice", systemPrompt = "MAIN"),
            assistantSystemPrompt = "MAIN",
            personaName = "Bob",
        )
        assertEquals(
            listOf("MAIN", "PRE Alice", "u1", "IN", "a1", "POST"),
            result.messages.map { it.toText() },
        )
        assertEquals(1, result.report.inChatCount)
    }

    @Test
    fun `prompt order enabled is the only source of truth`() {
        val preset = preset(
            prompts = listOf(
                p("a", "A").copy(sourceEnabled = true),
                p("b", "B").copy(sourceEnabled = false),
            ),
            order = listOf(
                StPromptOrderEntry("a", false),
                StPromptOrderEntry("b", true),
            ),
        )
        val result = StPromptAssembler.assemble(preset, emptyList())
        assertEquals(listOf("B"), result.messages.map { it.toText() })
        assertEquals(1, result.report.skippedDisabled)
    }

    @Test
    fun `trigger filters prompts`() {
        val preset = preset(
            prompts = listOf(
                p("normal", "N"),
                p("continueOnly", "C", trigger = setOf("continue")),
            ),
            order = listOf(StPromptOrderEntry("normal", true), StPromptOrderEntry("continueOnly", true)),
        )
        val normal = StPromptAssembler.assemble(preset, emptyList(), generationType = StGenerationType.NORMAL)
        assertEquals(listOf("N"), normal.messages.map { it.toText() })
        val cont = StPromptAssembler.assemble(preset, emptyList(), generationType = StGenerationType.CONTINUE)
        assertEquals(listOf("N", "C"), cont.messages.map { it.toText() })
    }

    @Test
    fun `squashes consecutive system messages`() {
        val preset = preset(
            prompts = listOf(p("a", "A"), p("b", "B"), p("c", "C", role = MessageRole.USER)),
            order = listOf(StPromptOrderEntry("a", true), StPromptOrderEntry("b", true), StPromptOrderEntry("c", true)),
            squash = true,
        )
        val result = StPromptAssembler.assemble(preset, emptyList())
        assertEquals(2, result.messages.size)
        assertEquals("A\nB", result.messages[0].toText())
        assertEquals(MessageRole.USER, result.messages[1].role)
    }

    @Test
    fun `fills char markers and dialogue examples`() {
        val preset = preset(
            prompts = listOf(
                p("charDescription", marker = true),
                p("charPersonality", marker = true),
                p("scenario", marker = true),
                p("dialogueExamples", marker = true),
            ),
            order = listOf(
                StPromptOrderEntry("charDescription", true),
                StPromptOrderEntry("charPersonality", true),
                StPromptOrderEntry("scenario", true),
                StPromptOrderEntry("dialogueExamples", true),
            ),
        )
        val card = CharacterCard(
            name = "Alice",
            description = "DESC",
            personality = "KIND",
            scenario = "SCENE",
            mesExample = "<START>\n{{user}}: hi\n{{char}}: hello",
        )
        val result = StPromptAssembler.assemble(preset, emptyList(), character = card, personaName = "Bob")
        assertEquals(listOf("DESC", "KIND", "SCENE", "hi", "hello"), result.messages.map { it.toText() })
        assertEquals(MessageRole.USER, result.messages[3].role)
        assertEquals(MessageRole.ASSISTANT, result.messages[4].role)
    }

    @Test
    fun `adds assistant prefill at the end`() {
        val preset = preset(
            prompts = listOf(p("a", "A")),
            order = listOf(StPromptOrderEntry("a", true)),
            prefill = "Start:",
        )
        val result = StPromptAssembler.assemble(preset, emptyList())
        assertEquals("Start:", result.messages.last().toText())
        assertEquals(MessageRole.ASSISTANT, result.messages.last().role)
    }

    @Test
    fun `unknown marker is reported not silently dropped`() {
        val preset = preset(
            prompts = listOf(p("weirdMarker", marker = true)),
            order = listOf(StPromptOrderEntry("weirdMarker", true)),
        )
        val result = StPromptAssembler.assemble(preset, emptyList())
        assertTrue(result.report.unknownMarkers.contains("weirdMarker"))
        assertTrue(result.report.warnings.any { it.contains("weirdMarker") })
    }

    @Test
    fun `editable prompt rows include mounted order then detached prompts`() {
        val preset = preset(
            prompts = listOf(p("a", "A"), p("b", "B"), p("detached", "D")),
            order = listOf(StPromptOrderEntry("a", true), StPromptOrderEntry("b", false)),
        )
        val rows = StPromptAssembler.editablePrompts(preset)
        assertEquals(listOf("a", "b", "detached"), rows.map { it.prompt.identifier })
        assertEquals(listOf(0, 1, null), rows.map { it.orderIndex })
        assertEquals(listOf(true, false, false), rows.map { it.enabled })
    }

    @Test
    fun `flatten system keeps one system and downgrades post-history system`() {
        val preset = preset(
            prompts = listOf(
                p("main", marker = true),
                p("pre", "PRE", role = MessageRole.USER),
                p("chatHistory", marker = true),
                p("post", "POST {{time}}"),
            ),
            order = listOf(
                StPromptOrderEntry("main", true),
                StPromptOrderEntry("pre", true),
                StPromptOrderEntry("chatHistory", true),
                StPromptOrderEntry("post", true),
            ),
        )
        val result = StPromptAssembler.assemble(
            preset = preset,
            history = listOf(UIMessage.user("u1"), UIMessage.assistant("a1")),
            assistantSystemPrompt = "MAIN",
            flattenSystemMessages = true,
        )
        val texts = result.messages.map { it.toText() }
        assertEquals("MAIN", texts[0])
        assertEquals("PRE", texts[1])
        assertEquals("u1", texts[2])
        assertEquals("a1", texts[3])
        assertTrue(texts.drop(4).any { it.startsWith("POST") })
        assertEquals(1, result.messages.count { it.role == MessageRole.SYSTEM })
        assertEquals(MessageRole.USER, result.messages.last().role)
    }

    @Test
    fun `three-zone assembly separates static prefix, stable history, and dynamic tail`() {
        val preset = preset(
            prompts = listOf(
                p("main", marker = true),
                p("charDescription", marker = true),
                p("worldInfoBefore", marker = true),
                p("chatHistory", marker = true),
                p("worldInfoAfter", marker = true),
                p("timePrompt", "Current: {{time}}"),
            ),
            order = listOf(
                StPromptOrderEntry("main", true),
                StPromptOrderEntry("charDescription", true),
                StPromptOrderEntry("worldInfoBefore", true),
                StPromptOrderEntry("chatHistory", true),
                StPromptOrderEntry("worldInfoAfter", true),
                StPromptOrderEntry("timePrompt", true),
            ),
        )
        val worldInfo = StWorldInfoResult(
            constantEntries = listOf(StWorldInfoEntry("rules", "Constant World Rules", isConstant = true)),
            dynamicTriggeredEntries = listOf(StWorldInfoEntry("monster", "A wild dragon appears!", isConstant = false)),
        )
        val character = CharacterCard(name = "Hero", description = "A brave knight", systemPrompt = "SYS")
        val history = listOf(UIMessage.user("Let's fight!"), UIMessage.assistant("I am ready."))

        val result = StPromptAssembler.assemble(
            preset = preset,
            history = history,
            character = character,
            worldInfo = worldInfo,
        )

        // Zone A: SYS (0), A brave knight (1), Constant World Rules (2)
        assertEquals("SYS", result.messages[0].toText())
        assertEquals("A brave knight", result.messages[1].toText())
        assertEquals("Constant World Rules", result.messages[2].toText())

        // Zone B: History
        assertEquals("Let's fight!", result.messages[3].toText())
        assertEquals("I am ready.", result.messages[4].toText())

        // Zone C: Dynamic Worldbook and Environment Time Sink at tail
        val tailTexts = result.messages.drop(5).map { it.toText() }
        assertTrue(tailTexts.any { it.contains("A wild dragon appears!") })
        assertTrue(tailTexts.any { it.contains("Environment Context: Time=") || it.contains("Current:") })
    }

    @Test
    fun `compaction checkpoint injects summary, trims history, and filters hidden messages`() {
        val preset = preset(
            prompts = listOf(
                p("main", marker = true),
                p("chatHistory", marker = true),
            ),
            order = listOf(
                StPromptOrderEntry("main", true),
                StPromptOrderEntry("chatHistory", true),
            ),
        )
        val m1 = UIMessage.user("Old msg 1")
        val m2 = UIMessage.assistant("Old reply 1")
        val m3 = UIMessage.user("Old msg 2 (hidden)").copy(isHidden = true)
        val m4 = UIMessage.assistant("Old reply 2")
        val m5 = UIMessage.user("Live message 1")
        val m6 = UIMessage.assistant("Live reply (hidden)").copy(isHidden = true)
        val m7 = UIMessage.user("Live message 2")

        val checkpoint = ConversationCheckpointEntity(
            conversationId = "conv-1",
            boundaryNodeId = m4.id.toString(),
            boundaryFloor = 4,
            summaryContent = "Summary of turns 1-4",
            originalTokens = 10000,
            compressedTokens = 500,
        )

        val result = StPromptAssembler.assemble(
            preset = preset,
            history = listOf(m1, m2, m3, m4, m5, m6, m7),
            assistantSystemPrompt = "SYSTEM",
            checkpoint = checkpoint,
        )

        val texts = result.messages.map { it.toText() }
        // System prompt
        assertEquals("SYSTEM", texts[0])
        // Checkpoint summary in Zone B
        assertTrue(texts[1].contains("<conversation_summary>"))
        assertTrue(texts[1].contains("Summary of turns 1-4"))
        // Messages before boundary (m1, m2, m3, m4) are compacted away
        assertTrue(!texts.contains("Old msg 1"))
        assertTrue(!texts.contains("Old reply 1"))
        assertTrue(!texts.contains("Old msg 2 (hidden)"))
        assertTrue(!texts.contains("Old reply 2"))
        // Hidden live message m6 is filtered out
        assertTrue(!texts.contains("Live reply (hidden)"))
        // Live non-hidden messages are preserved
        assertTrue(texts.contains("Live message 1"))
        assertTrue(texts.contains("Live message 2"))
    }

    @Test
    fun milestoneWhitelistPassesThroughAfterCheckpointSummary() {
        val preset = StPreset(
            id = Uuid.random(),
            name = "Milestone Test",
            prompts = listOf(
                StPrompt(identifier = "main", content = "SYSTEM", marker = true, role = MessageRole.SYSTEM),
                StPrompt(identifier = "chatHistory", content = "", marker = true, role = MessageRole.SYSTEM),
            ),
            promptOrder = listOf(
                StPromptOrderBucket(
                    characterId = StPromptOrderBucket.GLOBAL_CHARACTER_ID,
                    order = listOf(
                        StPromptOrderEntry("main", true),
                        StPromptOrderEntry("chatHistory", true),
                    ),
                )
            ),
        )

        val m1 = UIMessage.user("Normal old msg")
        val m2 = UIMessage.assistant("Key milestone moment!")
        val m3 = UIMessage.user("Another old msg")
        val m4 = UIMessage.assistant("Current live msg")

        val checkpoint = ConversationCheckpointEntity(
            conversationId = "conv-1",
            boundaryNodeId = m3.id.toString(),
            boundaryFloor = 3,
            summaryContent = "Summary of past events",
            originalTokens = 5000,
            compressedTokens = 300,
        )

        val result = StPromptAssembler.assemble(
            preset = preset,
            history = listOf(m1, m2, m3, m4),
            checkpoint = checkpoint,
            milestoneMessageIds = setOf(m2.id.toString()),
        )

        val texts = result.messages.map { it.toText() }
        assertTrue(!texts.contains("Normal old msg"))
        assertTrue(!texts.contains("Another old msg"))
        assertTrue(texts.any { it.contains("<conversation_summary>") })
        assertTrue(texts.contains("Key milestone moment!"))
        assertTrue(texts.contains("Current live msg"))
    }

    @Test
    fun `static markers and static system prompts placed after chatHistory are hoisted to Zone A before history`() {
        val preset = preset(
            prompts = listOf(
                p("main", marker = true),
                p("chatHistory", marker = true),
                p("charDescription", marker = true),
                p("charPersonality", marker = true),
                p("scenario", marker = true),
                p("dialogueExamples", marker = true),
                p("jailbreak", "Follow formatting rules", role = MessageRole.SYSTEM),
                p("timePrompt", "Time is {{time}}", role = MessageRole.SYSTEM),
                p("userNudge", "Please continue", role = MessageRole.USER),
            ),
            order = listOf(
                StPromptOrderEntry("main", true),
                StPromptOrderEntry("chatHistory", true),
                StPromptOrderEntry("charDescription", true),
                StPromptOrderEntry("charPersonality", true),
                StPromptOrderEntry("scenario", true),
                StPromptOrderEntry("dialogueExamples", true),
                StPromptOrderEntry("jailbreak", true),
                StPromptOrderEntry("timePrompt", true),
                StPromptOrderEntry("userNudge", true),
            ),
        )
        val character = CharacterCard(
            name = "Alice",
            systemPrompt = "MAIN_PROMPT",
            description = "DESC",
            personality = "PERS",
            scenario = "SCENE",
            mesExample = "<START>\n{{user}}: hi\n{{char}}: hello",
        )
        val history = listOf(UIMessage.user("u1"), UIMessage.assistant("a1"))
        val result = StPromptAssembler.assemble(
            preset = preset,
            history = history,
            character = character,
            personaName = "Bob",
        )
        val texts = result.messages.map { it.toText() }

        // Zone A: All static definitions hoisted BEFORE history
        assertEquals("MAIN_PROMPT", texts[0])
        assertEquals("DESC", texts[1])
        assertEquals("PERS", texts[2])
        assertEquals("SCENE", texts[3])
        assertEquals("hi", texts[4])
        assertEquals("hello", texts[5])
        assertEquals("Follow formatting rules", texts[6])

        // Zone B: Chat History
        assertEquals("u1", texts[7])
        assertEquals("a1", texts[8])

        // Zone C: Dynamic items & post-history user prompt
        assertTrue(texts.drop(9).any { it.contains("Time is") })
        assertTrue(texts.drop(9).contains("Please continue"))
    }

    @Test
    fun `optimizeOrder sorts static items before chatHistory and dynamic items after`() {
        val prompts = listOf(
            p("main", marker = true),
            p("chatHistory", marker = true),
            p("worldInfoAfter", marker = true),
            p("charDescription", marker = true),
            p("jailbreak", "Static Jailbreak", role = MessageRole.SYSTEM),
            p("timePrompt", "Current: {{time}}"),
            p("userReminder", "Remember this", role = MessageRole.USER),
        )
        val order = listOf(
            StPromptOrderEntry("main", true),
            StPromptOrderEntry("chatHistory", true),
            StPromptOrderEntry("worldInfoAfter", true),
            StPromptOrderEntry("charDescription", true),
            StPromptOrderEntry("jailbreak", true),
            StPromptOrderEntry("timePrompt", true),
            StPromptOrderEntry("userReminder", true),
        )
        val optimized = StPromptAssembler.optimizeOrder(prompts, order)
        val identifiers = optimized.map { it.identifier }
        assertEquals(
            listOf("main", "charDescription", "jailbreak", "chatHistory", "worldInfoAfter", "timePrompt", "userReminder"),
            identifiers,
        )
    }
}
