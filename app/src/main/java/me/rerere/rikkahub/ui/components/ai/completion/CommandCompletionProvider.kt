package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff

class CommandCompletionProvider : ChatCompletionProvider {
    override val id: String = "slash_commands"

    private data class CommandDefinition(
        val name: String,
        val insertText: String,
        val detail: String,
        val icon: ImageVector,
    )

    private val commands = listOf(
        CommandDefinition(
            name = "/compact",
            insertText = "/compact ",
            detail = "/compact [起始] [结尾] [目标大小(K)] [hide]：压缩指定楼层为背景记忆；无参数弹出窗口",
            icon = HugeIcons.Package01,
        ),
        CommandDefinition(
            name = "/compact withdraw",
            insertText = "/compact withdraw",
            detail = "/compact withdraw：撤销压缩并恢复被自动隐藏的楼层",
            icon = HugeIcons.ArrowTurnBackward,
        ),
        CommandDefinition(
            name = "/hide",
            insertText = "/hide ",
            detail = "/hide [楼层范围]：隐藏指定楼层上下文（如 5 或 3-18）；无参数弹出管理窗口",
            icon = HugeIcons.ViewOff,
        ),
        CommandDefinition(
            name = "/unhide",
            insertText = "/unhide ",
            detail = "/unhide [楼层范围]：恢复指定楼层上下文（如 5 或 3-18）；无参数弹出管理窗口",
            icon = HugeIcons.View,
        ),
    )

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection) return null
        val mention = findCommandMention(context.text, context.cursor) ?: return null
        val query = mention.query.lowercase()

        val matched = if (query.isEmpty()) {
            commands
        } else {
            commands.filter { cmd ->
                val pureName = cmd.name.removePrefix("/").lowercase()
                pureName.contains(query) || cmd.name.lowercase().contains(query) ||
                    cmd.detail.lowercase().contains(query)
            }
        }

        if (matched.isEmpty()) return null

        val items = matched.map { cmd ->
            val isPrefix = cmd.name.lowercase().startsWith("/$query") ||
                cmd.name.removePrefix("/").lowercase().startsWith(query)
            ChatCompletionItem(
                label = cmd.name,
                insertText = cmd.insertText,
                detail = cmd.detail,
                icon = cmd.icon,
                sortScore = if (isPrefix) 200 else 100,
            )
        }

        return ChatCompletionList(
            providerId = id,
            replacementRange = mention.range,
            items = items,
        )
    }

    private data class CommandMention(
        val query: String,
        val range: TextRange,
    )

    private fun findCommandMention(text: String, cursor: Int): CommandMention? {
        if (cursor < 0 || cursor > text.length) return null
        val prefix = text.substring(0, cursor)
        val start = prefix.lastIndexOf('/')
        if (start < 0) return null
        // 确保 '/' 出现在行首或前面是空格/空白
        if (start > 0 && !prefix[start - 1].isWhitespace()) return null

        val query = prefix.substring(start + 1)
        // 允许以 / 开头的一整段指令查询（如 "/compact with"）
        if (query.count { it.isWhitespace() } > 1) return null

        return CommandMention(
            query = query,
            range = TextRange(start, cursor),
        )
    }
}
