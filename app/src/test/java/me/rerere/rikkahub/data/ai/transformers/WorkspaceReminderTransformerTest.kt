package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.db.entity.WorkspacePromptMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WorkspaceReminderTransformerTest {

    @Test
    fun `os-release should prefer pretty name`() {
        val content = """
            NAME="Alpine Linux"
            ID=alpine
            VERSION_ID=3.24.1
            PRETTY_NAME="Alpine Linux v3.24"
            HOME_URL="https://alpinelinux.org/"
        """.trimIndent()
        assertEquals("Alpine Linux v3.24", parseOsReleaseName(content))
    }

    @Test
    fun `os-release should fall back to name and version`() {
        val content = """
            # comment
            NAME='Ubuntu'
            VERSION_ID="24.04"
        """.trimIndent()
        assertEquals("Ubuntu 24.04", parseOsReleaseName(content))
    }

    @Test
    fun `os-release without name should return null`() {
        assertNull(parseOsReleaseName("ID=unknown\nnot a key value line\n"))
    }

    @Test
    fun `os-release name should be sanitized and truncated`() {
        val name = parseOsReleaseName("PRETTY_NAME=\"Distro\u0007 ${"x".repeat(200)}\"")!!
        assertEquals(80, name.length)
        assertEquals("Distro x", name.take(8))
    }

    @Test
    fun `remote environment should be loaded once per config`() = runTest {
        val cache = RemoteEnvironmentCache()
        var loads = 0
        val load = suspend { RemoteEnvironment("/bin/bash", "Debian ${++loads}") }

        assertEquals("Debian 1", cache.get("w", "config-a", load).getOrThrow().distro)
        assertEquals("Debian 1", cache.get("w", "config-a", load).getOrThrow().distro)
        // 连接配置改了, 可能已经换了一台机器
        assertEquals("Debian 2", cache.get("w", "config-b", load).getOrThrow().distro)
        assertEquals(2, loads)
    }

    @Test
    fun `remote failure should be reused until it expires`() = runTest {
        var now = 0L
        val cache = RemoteEnvironmentCache(failureTtlMillis = 60_000, now = { now })
        var loads = 0
        val failing = suspend {
            loads++
            throw IOException("unreachable")
        }

        assertTrue(cache.get("w", "config", failing).isFailure)
        now = 59_999
        assertTrue(cache.get("w", "config", failing).isFailure)
        assertEquals(1, loads)

        now = 60_000
        val recovered = cache.get("w", "config") { RemoteEnvironment("/bin/sh", null) }
        assertEquals("/bin/sh", recovered.getOrThrow().shell)
    }

    @Test
    fun `disabled prompt mode skips default prompt and agents`() = runTest {
        val workspace = workspaceEntity(promptMode = WorkspacePromptMode.DISABLED, customPrompt = "Custom")
        var defaultCalls = 0
        var agentsCalls = 0
        val result = resolveWorkspacePrompt(
            workspace = workspace,
            defaultPrompt = {
                defaultCalls++
                "<workspace>default</workspace>"
            },
            agentsPrompt = {
                agentsCalls++
                "\n\n<workspace_instructions/>"
            },
        )
        assertNull(result)
        assertEquals(0, defaultCalls)
        assertEquals(0, agentsCalls)
    }

    @Test
    fun `custom prompt mode replaces default prompt and ignores blank custom text`() = runTest {
        var defaultCalls = 0
        val customWorkspace = workspaceEntity(
            promptMode = WorkspacePromptMode.CUSTOM,
            customPrompt = "  You are running on Debian 13 with zsh.  ",
        )
        val customResult = resolveWorkspacePrompt(
            workspace = customWorkspace,
            defaultPrompt = {
                defaultCalls++
                "<workspace>default</workspace>"
            },
            agentsPrompt = { "\n\n<workspace_instructions>AGENTS</workspace_instructions>" },
        )
        assertEquals(
            "You are running on Debian 13 with zsh.\n\n<workspace_instructions>AGENTS</workspace_instructions>",
            customResult,
        )
        assertEquals(0, defaultCalls)

        val blankCustom = workspaceEntity(promptMode = WorkspacePromptMode.CUSTOM, customPrompt = "   ")
        assertNull(
            resolveWorkspacePrompt(
                workspace = blankCustom,
                defaultPrompt = { "<workspace>default</workspace>" },
                agentsPrompt = { "\n\nAGENTS" },
            )
        )
    }

    @Test
    fun `default prompt mode delegates to generated prompt`() = runTest {
        val workspace = workspaceEntity(promptMode = WorkspacePromptMode.DEFAULT, customPrompt = "Ignored")
        val result = resolveWorkspacePrompt(
            workspace = workspace,
            defaultPrompt = { "<workspace>generated</workspace>" },
            agentsPrompt = { "" },
        )
        assertEquals("<workspace>generated</workspace>", result)
    }

    private fun workspaceEntity(
        promptMode: WorkspacePromptMode,
        customPrompt: String? = null,
    ) = WorkspaceEntity(
        id = "ws-1",
        name = "Test",
        root = "ws-1",
        createdAt = 1L,
        updatedAt = 1L,
        promptMode = promptMode.name,
        customPrompt = customPrompt,
    )
}
