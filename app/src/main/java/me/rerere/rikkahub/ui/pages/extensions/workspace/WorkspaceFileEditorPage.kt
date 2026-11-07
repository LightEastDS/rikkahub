package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.ClipData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.LocalWorkspaceId
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.ui.webview.WebView
import me.rerere.ui.webview.rememberWebViewState
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")
private val WEB_PREVIEW_EXTENSIONS = setOf("html", "htm", "svg")

/**
 * 工作区文本文件编辑/预览页.
 *
 * 支持查看、复制、编辑并保存工作区文件 (包括 FILES 区与 LINUX 区).
 */
@Composable
fun WorkspaceFileEditorPage(
    id: String,
    area: WorkspaceStorageArea,
    path: String,
) {
    val repository = koinInject<WorkspaceRepository>()
    val toaster = LocalToaster.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val editable = true
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val isMarkdown = extension in MARKDOWN_EXTENSIONS
    val isWebPreview = extension in WEB_PREVIEW_EXTENSIONS
    val supportsPreview = isMarkdown || isWebPreview
    var showPreview by rememberSaveable(id, area, path) { mutableStateOf(supportsPreview) }
    var autoWrap by rememberSaveable(id, area, path) { mutableStateOf(true) }

    // 不能用 rememberTextFieldState: 它会把全文存进 saved state Bundle, 大文件切后台时触发 TransactionTooLargeException (#1953).
    // 内容本就由下方 LaunchedEffect 从磁盘加载, 无需 saveable.
    val textState = remember(id, area, path) { TextFieldState() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id, area, path) {
        loading = true
        loadError = null
        runCatching {
            repository.readTextForPreview(id, area, path)
        }.onSuccess { content ->
            textState.setTextAndPlaceCursorAtEnd(content)
            loading = false
        }.onFailure {
            loadError = it.message ?: "读取文件失败"
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (!loading && loadError == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = autoWrap,
                                onClick = { autoWrap = !autoWrap },
                                label = { Text(stringResource(R.string.workspace_editor_auto_wrap)) },
                            )
                            TextButton(
                                onClick = {
                                    val contentToCopy = textState.text.toString()
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText(fileName, contentToCopy))
                                        )
                                        toaster.show(
                                            context.getString(R.string.copied_to_clipboard),
                                            type = ToastType.Success,
                                        )
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.common_copy))
                            }
                            if (supportsPreview) {
                                TextButton(onClick = { showPreview = !showPreview }) {
                                    Text(
                                        if (showPreview) stringResource(R.string.common_edit)
                                        else stringResource(R.string.workspace_editor_preview)
                                    )
                                }
                            }
                            if (editable && !showPreview) {
                                TextButton(
                                    onClick = {
                                        if (saving) return@TextButton
                                        saving = true
                                        scope.launch {
                                            runCatching {
                                                val newText = textState.text.toString()
                                                when (area) {
                                                    WorkspaceStorageArea.FILES -> repository.writeText(
                                                        id = id,
                                                        path = path,
                                                        text = newText,
                                                        overwrite = true,
                                                    )
                                                    WorkspaceStorageArea.LINUX -> repository.writeAbsoluteText(
                                                        id = id,
                                                        path = "/${path.trimStart('/')}",
                                                        text = newText,
                                                        overwrite = true,
                                                    )
                                                }
                                            }.onSuccess {
                                                toaster.show("已保存", type = ToastType.Success)
                                            }.onFailure {
                                                toaster.show(it.message ?: "保存失败", type = ToastType.Error)
                                            }
                                            saving = false
                                        }
                                    },
                                    enabled = !saving,
                                ) {
                                    Text(stringResource(R.string.common_save))
                                }
                            }
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text(
                    text = loadError ?: "",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            showPreview && isMarkdown -> {
                val effectiveSettings = remember(settings, autoWrap) {
                    settings.copy(
                        displaySetting = settings.displaySetting.copy(codeBlockAutoWrap = autoWrap)
                    )
                }
                val verticalScroll = rememberScrollState()
                val horizontalScroll = rememberScrollState()
                CompositionLocalProvider(
                    LocalWorkspaceId provides id,
                    LocalSettings provides effectiveSettings,
                ) {
                    SelectableContent {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(verticalScroll)
                                .then(
                                    if (autoWrap) {
                                        Modifier.fillMaxWidth()
                                    } else {
                                        Modifier
                                            .horizontalScroll(horizontalScroll)
                                            .width(IntrinsicSize.Max)
                                    }
                                )
                                .padding(16.dp),
                        ) {
                            MarkdownBlock(
                                content = textState.text.toString(),
                                modifier = if (autoWrap) Modifier.fillMaxWidth() else Modifier,
                            )
                        }
                    }
                }
            }

            showPreview && isWebPreview -> WorkspaceWebPreview(
                content = textState.text.toString(),
                isSvg = extension == "svg",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> {
                val horizontalScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .then(
                            if (autoWrap) {
                                Modifier
                            } else {
                                Modifier.horizontalScroll(horizontalScroll)
                            }
                        ),
                ) {
                    TextField(
                        state = textState,
                        modifier = if (autoWrap) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .width(IntrinsicSize.Max)
                                .fillMaxSize()
                        },
                        readOnly = !editable,
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = JetbrainsMono,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceWebPreview(
    content: String,
    isSvg: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(
        data = content,
        baseUrl = "https://workspace-preview.invalid/",
        mimeType = if (isSvg) "image/svg+xml" else "text/html",
        settings = {
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    )
    WebView(state = state, modifier = modifier)
}
