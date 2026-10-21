package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.CharacterCard
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
                p("post", "POST"),
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
                p("post", "POST"),
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
        assertEquals(listOf("MAIN", "PRE", "u1", "a1", "POST"), result.messages.map { it.toText() })
        assertEquals(1, result.messages.count { it.role == MessageRole.SYSTEM })
        assertEquals(MessageRole.USER, result.messages.last().role)
    }
}
