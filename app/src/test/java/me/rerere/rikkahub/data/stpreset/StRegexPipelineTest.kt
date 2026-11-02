package me.rerere.rikkahub.data.stpreset

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StRegexPipelineTest {
    @Test
    fun `prompt-only script rewrites old user messages with minDepth`() {
        val script = StRegexScript(
            name = "clear old user",
            findRegex = "/[\\s\\S]+/g",
            replaceString = "",
            promptOnly = true,
            placement = setOf(1),
            minDepth = 1,
        )
        val messages = listOf(
            UIMessage.user("old-1"),
            UIMessage.assistant("old-a"),
            UIMessage.user("latest"),
        )
        val result = StRegexPipeline.applyToMessages(messages, listOf(script))
        assertEquals(listOf("", "old-a", "latest"), result.messages.map { it.toText() })
    }

    @Test
    fun `maxDepth limits rewrite to latest messages`() {
        val script = StRegexScript(
            name = "wrap latest",
            findRegex = "/^(.+)$/g",
            replaceString = "<inputs>$1</inputs>",
            promptOnly = true,
            placement = setOf(1),
            maxDepth = 0,
        )
        val messages = listOf(
            UIMessage.user("old"),
            UIMessage.assistant("a"),
            UIMessage.user("latest"),
        )
        val result = StRegexPipeline.applyToMessages(messages, listOf(script))
        assertEquals(listOf("old", "a", "<inputs>latest</inputs>"), result.messages.map { it.toText() })
    }

    @Test
    fun `trimStrings removes helper text after replacement`() {
        val script = StRegexScript(
            name = "trim",
            findRegex = "/x/",
            replaceString = "y<KEEP>",
            promptOnly = true,
            placement = setOf(1),
            trimStrings = listOf("<KEEP>"),
        )
        val result = StRegexPipeline.applyToMessages(listOf(UIMessage.user("x")), listOf(script))
        assertEquals("y", result.messages.single().toText())
    }

    @Test
    fun `invalid regex does not crash`() {
        val script = StRegexScript(
            name = "bad",
            findRegex = "/([/",
            replaceString = "",
            promptOnly = true,
            placement = setOf(1),
        )
        val result = StRegexPipeline.applyToMessages(listOf(UIMessage.user("x")), listOf(script))
        assertEquals("x", result.messages.single().toText())
        assertTrue(result.warnings.any { it.contains("bad") })
    }

    @Test
    fun `js dollar ampersand replacement works`() {
        val script = StRegexScript(
            name = "wrapped",
            findRegex = "/yes/",
            replaceString = "[$&]",
            promptOnly = true,
            placement = setOf(2),
        )
        val result = StRegexPipeline.applyToMessages(listOf(UIMessage.assistant("yes")), listOf(script))
        assertEquals("[yes]", result.messages.single().toText())
        assertEquals(MessageRole.ASSISTANT, result.messages.single().role)
    }

    @Test
    fun `applyToDisplayText converts dream XML blocks and normalizes details for Markdown`() {
        val raw = """
            <dream_scene>
            <date>2026年5月1日</date>
            <time>下午 3:00</time>
            <location>星海图书馆</location>
            </dream_scene>
            <dream_body>
            他推开了沉重的木门，看见旧错字在桌上闪烁。
            </dream_body>
            <dream_summary>
            本轮摘要：主角进入星海图书馆。
            </dream_summary>
            <dream_option>
            查阅古籍|询问管理员|离开图书馆
            </dream_option>
            <dream_parallel_event>
            <simple_thinking>思考平行事件</simple_thinking>
            钟楼顶层|守夜人敲响了下午三点的钟声。<br>飞鸟掠过广场。
            </dream_parallel_event>
            <dream_self_check>
            <review>发现一处错字并修正。</review>
            <patch>
            FIND: 旧错字
            REPLACE: 古老的星盘
            </patch>
            </dream_self_check>
        """.trimIndent()

        val rendered = StRegexPipeline.applyToDisplayText(
            text = raw,
            scripts = emptyList(),
            placement = 2,
        )

        assertTrue("Scene bar should be formatted", rendered.contains("📅 **2026年5月1日** ｜ 🕰️ **下午 3:00** ｜ 📍 **星海图书馆**"))
        assertTrue("Self-check patch should fix story body text", rendered.contains("看见古老的星盘在桌上闪烁"))
        assertTrue("Summary should be wrapped in details", rendered.contains("<summary>🐋梦鲸摘要</summary>"))
        assertTrue("Options should be wrapped in details", rendered.contains("<summary>🎯 剧情推进选项（3 项）</summary>"))
        assertTrue("Parallel event should strip simple_thinking and format location", rendered.contains("**【钟楼顶层】**") && !rendered.contains("思考平行事件"))
        assertTrue("Self-check card should show applied count", rendered.contains("已应用 1/1 项修正"))
        assertTrue("Wrapper tag <dream_body> should be stripped", !rendered.contains("<dream_body>"))
    }
}
