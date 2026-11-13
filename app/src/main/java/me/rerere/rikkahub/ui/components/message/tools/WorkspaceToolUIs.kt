package me.rerere.rikkahub.ui.components.message.tools

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.metadataAs
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.rikkahub.ui.components.richtext.CodeHighlightText
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.FileView
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellManager
import me.rerere.rikkahub.service.BackgroundShellStatus
import me.rerere.rikkahub.ui.components.ai.BackgroundTaskLog
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.ui.richtext.DiffAddedColor
import me.rerere.ui.richtext.DiffRemovedColor
import me.rerere.ui.richtext.DiffView
import me.rerere.ui.richtext.parseDiffStats
import me.rerere.ui.toast.ToastType
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

/**
 * 工作空间编辑文件: 摘要显示增删统计与精简 diff, 详情为完整 diff view
 */
object EditFileToolUI : ToolUIRenderer {
    private const val SUMMARY_MAX_LINES = 10

    override val toolName: String = "workspace_edit_file"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileEdit

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_edit_file, path) else stringResource(R.string.tool_ui_edit_file_default)
    }

    /**
     * 执行后读取输出部件 metadata 中的全文件 diff;
     * 未执行 (如等待审批) 时基于入参的 old_text/new_text 片段生成预览 diff
     */
    private fun diffOf(context: ToolUIContext): String? {
        if (context.tool.isExecuted) {
            return context.tool.output.firstOrNull()?.metadataAs<DiffMetadata>()?.diff
        }
        val path = context.arguments.getStringContent("path") ?: return null
        val oldText = context.arguments.getStringContent("old_text") ?: return null
        val newText = context.arguments.getStringContent("new_text") ?: return null
        return generateUnifiedDiff(oldText, newText, path)
    }

    override fun hasSummary(context: ToolUIContext): Boolean = diffOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val diff = remember(context) { diffOf(context) } ?: return
        val stats = remember(diff) { parseDiffStats(diff) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "+${stats.additions}",
                style = MaterialTheme.typography.labelSmall,
                color = DiffAddedColor,
            )
            Text(
                text = "-${stats.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = DiffRemovedColor,
            )
        }
        DiffView(
            diff = diff,
            modifier = Modifier.fillMaxWidth(),
            maxLines = SUMMARY_MAX_LINES,
            showFileHeader = false,
        )
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val diff = remember(context) { diffOf(context) }
        if (diff == null) {
            DefaultToolPreview(context = context)
            return
        }
        val stats = remember(diff) { parseDiffStats(diff) }
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = context.arguments.getStringContent("path") ?: toolName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+${stats.additions}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiffAddedColor,
                )
                Text(
                    text = "-${stats.deletions}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiffRemovedColor,
                )
            }
            DiffView(
                diff = diff,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 工作空间读取文件: 摘要显示内容首部预览, 详情为带语法高亮的完整内容
 */
object ReadFileToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_read_file"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileView

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_read_file, path) else stringResource(R.string.tool_ui_read_file_default)
    }

    /** 已执行时从输出 JSON 读取文件内容 */
    private fun textOf(context: ToolUIContext): String? =
        context.content.getStringContent("text")

    override fun hasSummary(context: ToolUIContext): Boolean = textOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val text = remember(context) { textOf(context) } ?: return
        FileContentSummary(
            text = text,
            path = context.arguments.getStringContent("path"),
            loading = context.loading,
        )
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val text = remember(context) { textOf(context) }
        if (text == null) {
            DefaultToolPreview(context = context)
            return
        }
        FileContentPreview(
            path = context.arguments.getStringContent("path"),
            code = text,
            workspaceId = context.assistant?.workspaceId?.toString(),
            onSaveCode = { newText ->
                val currentObj = context.content?.jsonObjectOrNull
                if (currentObj != null && context.onUpdateTool != null) {
                    val updatedJson = JsonObject(currentObj + ("text" to JsonPrimitive(newText)))
                    val updatedOutput = listOf(
                        UIMessagePart.Text(JsonInstant.encodeToString(JsonObject.serializer(), updatedJson))
                    )
                    context.onUpdateTool.invoke(context.tool.copy(output = updatedOutput))
                }
            },
        )
    }
}

/**
 * 工作空间写入文件: 内容取自入参 (未执行也可预览), 摘要为内容首部, 详情为完整内容
 */
object WriteFileToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_write_file"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileAdd

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_write_file, path) else stringResource(R.string.tool_ui_write_file_default)
    }

    private fun textOf(context: ToolUIContext): String? =
        context.arguments.getStringContent("text")

    override fun hasSummary(context: ToolUIContext): Boolean = textOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val text = remember(context) { textOf(context) } ?: return
        FileContentSummary(
            text = text,
            path = context.arguments.getStringContent("path"),
            loading = context.loading,
        )
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val text = remember(context) { textOf(context) }
        if (text == null) {
            DefaultToolPreview(context = context)
            return
        }
        FileContentPreview(
            path = context.arguments.getStringContent("path"),
            code = text,
            workspaceId = context.assistant?.workspaceId?.toString(),
            onSaveCode = { newText ->
                val currentObj = context.arguments.jsonObjectOrNull
                if (currentObj != null && context.onUpdateTool != null) {
                    val updatedJson = JsonObject(currentObj + ("text" to JsonPrimitive(newText)))
                    context.onUpdateTool.invoke(
                        context.tool.copy(input = JsonInstant.encodeToString(JsonObject.serializer(), updatedJson))
                    )
                }
            },
        )
    }
}

/** 内联摘要: 按扩展名语法高亮展示文件内容首部若干行 */
@Composable
private fun FileContentSummary(text: String, path: String?, loading: Boolean) {
    val preview = remember(text) {
        text.lineSequence().take(FILE_SUMMARY_MAX_LINES).joinToString("\n")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .shimmer(isLoading = loading),
    ) {
        CodeHighlightText(
            code = preview,
            language = languageOf(path),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = FILE_SUMMARY_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** BottomSheet 详情: 文件路径 + 按扩展名语法高亮的完整内容（支持复制、编辑保存、Markdown 渲染预览与自动换行开关） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileContentPreview(
    path: String?,
    code: String,
    workspaceId: String? = null,
    onSaveCode: ((String) -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val workspaceRepository = koinInject<WorkspaceRepository>()

    var currentCode by remember(code) { mutableStateOf(code) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(code) { mutableStateOf(code) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(workspaceId, path) {
        if (!workspaceId.isNullOrBlank() && !path.isNullOrBlank()) {
            runCatching {
                val (area, targetPath) = workspaceRepository.resolveEditorTarget(workspaceId, path)
                workspaceRepository.readTextForPreview(workspaceId, area, targetPath)
            }.onSuccess { diskContent ->
                currentCode = diskContent
                if (!isEditing) {
                    editText = diskContent
                }
            }
        }
    }

    val language = remember(path) { languageOf(path) }
    val isMarkdown = language == "markdown"
    var renderMarkdown by remember(isMarkdown, settings.displaySetting.enableMarkdownRendering) {
        mutableStateOf(isMarkdown && settings.displaySetting.enableMarkdownRendering)
    }
    var autoWrap by remember { mutableStateOf(true) }
    val effectiveSettings = remember(settings, autoWrap) {
        settings.copy(
            displaySetting = settings.displaySetting.copy(codeBlockAutoWrap = autoWrap)
        )
    }

    CompositionLocalProvider(LocalSettings provides effectiveSettings) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = path ?: stringResource(R.string.tool_ui_file),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val textToCopy = if (isEditing) editText else currentCode
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("file", textToCopy)))
                            toaster.show(context.getString(R.string.copied_to_clipboard), type = ToastType.Success)
                        }
                    },
                    label = { Text(stringResource(R.string.common_copy)) },
                )
                if (!isEditing) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            editText = currentCode
                            isEditing = true
                        },
                        label = { Text(stringResource(R.string.common_edit)) },
                    )
                    FilterChip(
                        selected = autoWrap,
                        onClick = { autoWrap = !autoWrap },
                        label = { Text(stringResource(R.string.workspace_editor_auto_wrap)) },
                    )
                    if (isMarkdown) {
                        FilterChip(
                            selected = renderMarkdown,
                            onClick = { renderMarkdown = !renderMarkdown },
                            label = {
                                Text(
                                    if (renderMarkdown) stringResource(R.string.workspace_editor_preview)
                                    else stringResource(R.string.workspace_editor_source)
                                )
                            },
                        )
                    }
                } else {
                    FilterChip(
                        selected = true,
                        enabled = !saving,
                        onClick = {
                            if (saving) return@FilterChip
                            saving = true
                            scope.launch {
                                val newText = editText
                                val writeResult = if (!workspaceId.isNullOrBlank() && !path.isNullOrBlank()) {
                                    runCatching {
                                        val (area, targetPath) = workspaceRepository.resolveEditorTarget(workspaceId, path)
                                        when (area) {
                                            WorkspaceStorageArea.FILES -> workspaceRepository.writeText(
                                                id = workspaceId,
                                                path = targetPath,
                                                text = newText,
                                                overwrite = true,
                                            )
                                            WorkspaceStorageArea.LINUX -> workspaceRepository.writeAbsoluteText(
                                                id = workspaceId,
                                                path = if (path.startsWith("/")) path else "/$targetPath",
                                                text = newText,
                                                overwrite = true,
                                            )
                                        }
                                    }
                                } else {
                                    Result.success(Unit)
                                }
                                writeResult.onSuccess {
                                    currentCode = newText
                                    onSaveCode?.invoke(newText)
                                    isEditing = false
                                    toaster.show(context.getString(R.string.common_save), type = ToastType.Success)
                                }.onFailure { err ->
                                    // 即使工作区未就绪或文件不存在，也允许更新消息内嵌文档内容
                                    if (onSaveCode != null) {
                                        currentCode = newText
                                        onSaveCode.invoke(newText)
                                        isEditing = false
                                        toaster.show(context.getString(R.string.common_save), type = ToastType.Success)
                                    } else {
                                        toaster.show(err.message ?: "保存失败", type = ToastType.Error)
                                    }
                                }
                                saving = false
                            }
                        },
                        label = { Text(stringResource(R.string.common_save)) },
                    )
                    FilterChip(
                        selected = false,
                        enabled = !saving,
                        onClick = {
                            editText = currentCode
                            isEditing = false
                        },
                        label = { Text(stringResource(R.string.common_cancel)) },
                    )
                }
            }
            when {
                isEditing -> {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 10,
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = JetbrainsMono,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }
                isMarkdown && renderMarkdown -> {
                    SelectableContent {
                        MarkdownBlock(
                            content = currentCode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> {
                    HighlightCodeBlock(
                        code = currentCode,
                        language = language,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * 工作空间执行 Shell: 摘要显示退出状态与输出首部, 详情为命令 + stdout/stderr
 */
object ShellToolUI : ToolUIRenderer {
    private const val TITLE_MAX_CHARS = 40
    private const val SUMMARY_MAX_LINES = 8

    override val toolName: String = "workspace_shell"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.ComputerTerminal01

    @Composable
    override fun title(context: ToolUIContext): String {
        val command = context.arguments.getStringContent("command") ?: return stringResource(R.string.tool_ui_shell_default)
        val preview = command.replace("\n", " ").trim()
        val truncated = if (preview.length > TITLE_MAX_CHARS) preview.take(TITLE_MAX_CHARS) + "…" else preview
        return stringResource(R.string.tool_ui_shell, truncated)
    }

    override fun hasSummary(context: ToolUIContext): Boolean = context.content != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val content = context.content ?: return
        val combined = remember(content) {
            listOf(content.getStringContent("stdout"), content.getStringContent("stderr"))
                .filterNot { it.isNullOrBlank() }
                .joinToString("\n")
                .trim()
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShellExitStatus(content, MaterialTheme.typography.labelSmall)
            if (combined.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .shimmer(isLoading = context.loading),
                ) {
                    Text(
                        text = combined.lineSequence().take(SUMMARY_MAX_LINES).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = SUMMARY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val content = context.content
        if (content == null) {
            DefaultToolPreview(context = context)
            return
        }
        val command = context.arguments.getStringContent("command").orEmpty()
        val cwd = context.arguments.getStringContent("cwd")
        val stdout = content.getStringContent("stdout").orEmpty()
        val stderr = content.getStringContent("stderr").orEmpty()
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.tool_ui_shell_default),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ShellExitStatus(content, MaterialTheme.typography.labelMedium)
            }
            HighlightCodeBlock(
                code = if (cwd.isNullOrBlank()) command else "# cwd: $cwd\n$command",
                language = "bash",
                modifier = Modifier.fillMaxWidth(),
            )
            content.getStringContent("taskId")?.let { BackgroundTaskPreviewLog(it) }
            if (stdout.isNotEmpty()) {
                Text(text = "stdout", style = MaterialTheme.typography.labelMedium)
                HighlightCodeBlock(
                    code = stdout,
                    language = "plaintext",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (stderr.isNotEmpty()) {
                Text(
                    text = "stderr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                HighlightCodeBlock(
                    code = stderr,
                    language = "plaintext",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 后台命令的实时状态; 任务只存在内存里, 应用重启后查不到时只显示任务 ID */
@Composable
private fun BackgroundTaskStatus(taskId: String, style: androidx.compose.ui.text.TextStyle) {
    val manager = koinInject<BackgroundShellManager>()
    val tasks by manager.tasks.collectAsStateWithLifecycle()
    val task = tasks[taskId]
    val colors = MaterialTheme.colorScheme
    Text(
        text = when (task?.status) {
            BackgroundShellStatus.RUNNING -> stringResource(R.string.tool_ui_shell_background_running, taskId)
            BackgroundShellStatus.EXITED -> stringResource(R.string.tool_ui_shell_exit, "${task.exitCode}") + " · $taskId"
            BackgroundShellStatus.KILLED -> stringResource(R.string.tool_ui_shell_background_stopped, taskId)
            null -> stringResource(R.string.tool_ui_shell_background, taskId)
        },
        style = style,
        color = when {
            task?.status == BackgroundShellStatus.RUNNING -> colors.primary
            task?.status == BackgroundShellStatus.EXITED -> if (task.exitCode == 0) DiffAddedColor else colors.error
            else -> colors.onSurfaceVariant
        },
    )
}

/** 工具卡片详情里的日志, 任务已经不存在时不显示 */
@Composable
private fun BackgroundTaskPreviewLog(taskId: String) {
    val manager = koinInject<BackgroundShellManager>()
    val tasks by manager.tasks.collectAsStateWithLifecycle()
    val task = tasks[taskId] ?: return
    BackgroundTaskLog(
        task = task,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
    )
}

/** Shell 退出状态文本: exit code 为 0 显示绿色, 超时或非零显示错误色 */
@Composable
private fun ShellExitStatus(content: JsonElement, style: androidx.compose.ui.text.TextStyle) {
    val taskId = content.getStringContent("taskId")
    if (taskId != null) {
        BackgroundTaskStatus(taskId, style)
        return
    }
    val exitCode = content.int("exitCode")
    val timedOut = content.boolean("timedOut") ?: false
    val ok = !timedOut && exitCode == 0
    Text(
        text = when {
            timedOut -> stringResource(R.string.tool_ui_shell_timeout)
            else -> stringResource(R.string.tool_ui_shell_exit, exitCode?.toString() ?: "?")
        },
        style = style,
        color = if (ok) DiffAddedColor else MaterialTheme.colorScheme.error,
    )
}

/** 从工具输出 JSON 读取布尔字段 */
private fun JsonElement?.boolean(key: String): Boolean? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.booleanOrNull

/** 从工具输出 JSON 读取整型字段 */
private fun JsonElement?.int(key: String): Int? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.intOrNull

/** 从工具输出 JSON 读取长整型字段 */
private fun JsonElement?.long(key: String): Long? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.longOrNull

private const val FILE_SUMMARY_MAX_LINES = 10

/** 由文件扩展名推断语法高亮语言 */
private fun languageOf(path: String?): String = when (
    path?.substringAfterLast('.', "")?.lowercase().orEmpty()
) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "js", "mjs", "cjs" -> "javascript"
    "ts" -> "typescript"
    "tsx" -> "tsx"
    "jsx" -> "jsx"
    "py" -> "python"
    "rb" -> "ruby"
    "go" -> "go"
    "rs" -> "rust"
    "c", "h" -> "c"
    "cpp", "cc", "cxx", "hpp", "hxx" -> "cpp"
    "cs" -> "csharp"
    "swift" -> "swift"
    "php" -> "php"
    "sh", "bash", "zsh" -> "bash"
    "json" -> "json"
    "xml" -> "xml"
    "html", "htm" -> "html"
    "css" -> "css"
    "scss" -> "scss"
    "yaml", "yml" -> "yaml"
    "toml" -> "toml"
    "md", "markdown" -> "markdown"
    "sql" -> "sql"
    "gradle" -> "groovy"
    else -> "plaintext"
}
