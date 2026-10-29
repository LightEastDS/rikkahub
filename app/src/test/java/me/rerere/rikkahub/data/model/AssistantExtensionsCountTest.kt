package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantExtensionsCountTest {

    @Test
    fun `default assistant has zero active extensions`() {
        val assistant = Assistant()
        assertEquals(0, assistant.activeExtensionsCount)
    }

    @Test
    fun `st preset increments active extensions count when present`() {
        val withoutPreset = Assistant(stPresetId = null)
        assertEquals(0, withoutPreset.activeExtensionsCount)

        val withPreset = Assistant(stPresetId = Uuid.random())
        assertEquals(1, withPreset.activeExtensionsCount)
    }

    @Test
    fun `combined active extensions include st preset correctly`() {
        val assistantWithFourExtensions = Assistant(
            quickMessageIds = setOf(Uuid.random()),
            modeInjectionIds = setOf(Uuid.random()),
            lorebookIds = setOf(Uuid.random()),
            enabledSkills = setOf("translator"),
            stPresetId = null,
        )
        // 之前只统计了前四项，此处为 4
        assertEquals(4, assistantWithFourExtensions.activeExtensionsCount)

        // 开启 ST 预设后应正确统计为 5
        val assistantWithFiveExtensions = assistantWithFourExtensions.copy(
            stPresetId = Uuid.random(),
        )
        assertEquals(5, assistantWithFiveExtensions.activeExtensionsCount)

        // 关闭 ST 预设后恢复为 4
        val disabledPreset = assistantWithFiveExtensions.copy(stPresetId = null)
        assertEquals(4, disabledPreset.activeExtensionsCount)
    }

    @Test
    fun `multiple entries in set-based extensions and st preset are counted`() {
        val assistant = Assistant(
            quickMessageIds = setOf(Uuid.random(), Uuid.random()), // 2
            modeInjectionIds = setOf(Uuid.random()), // 1
            lorebookIds = setOf(Uuid.random(), Uuid.random(), Uuid.random()), // 3
            enabledSkills = setOf("skillA", "skillB"), // 2
            stPresetId = Uuid.random(), // 1
        )
        // 2 + 1 + 3 + 2 + 1 = 9
        assertEquals(9, assistant.activeExtensionsCount)
    }
}
