package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.entity.ConversationCheckpointEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.withConversation
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.ErrorCardsDisplay
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ImeLazyListAutoScroller
import me.rerere.rikkahub.ui.theme.ChatFontProvider
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.ui.components.ListSelectableItem
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType

private const val TAG = "ChatList"
private const val LoadingIndicatorKey = "LoadingIndicator"
private const val ScrollBottomKey = "ScrollBottomKey"

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    previewListState: LazyListState,
    selectionState: ChatSelectionState,
    loading: Boolean,
    processingStatus: String? = null,
    previewMode: Boolean,
    onPreviewModeChange: (Boolean) -> Unit,
    settings: Settings,
    hazeState: HazeState,
    checkpoint: ConversationCheckpointEntity? = null,
    errors: List<ChatError> = emptyList(),
    onDismissError: (Uuid) -> Unit = {},
    onClearAllErrors: () -> Unit = {},
    onRegenerate: (UIMessage) -> Unit = {},
    onEdit: (UIMessage) -> Unit = {},
    onForkMessage: (UIMessage) -> Unit = {},
    onDelete: (UIMessage) -> Unit = {},
    onBatchDelete: (Set<Uuid>) -> Unit = {},
    onUpdateCheckpointSummary: ((checkpointId: String, newSummary: String) -> Unit)? = null,
    onUpdateMessage: (MessageNode) -> Unit = {},
    onClickSuggestion: (String) -> Unit = {},
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onJumpToMessage: (Int) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onToggleHide: ((UIMessage) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
) {
    val previewProgress = rememberPreviewSwipeProgress(previewMode)
    val scope = rememberCoroutineScope()
    val currentPreviewMode by rememberUpdatedState(previewMode)
    val currentOnPreviewModeChange by rememberUpdatedState(onPreviewModeChange)
    val currentConversation by rememberUpdatedState(conversation)
    val layoutDirection = LocalLayoutDirection.current
    val layoutDirectionSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    // 概览页只在露出来的时候组合，平时不占资源
    val showPreview by remember { derivedStateOf { previewProgress.value > 0f } }
    val showNormal by remember { derivedStateOf { previewProgress.value < 1f } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .previewSwipeGesture(
                progress = previewProgress,
                scope = scope,
                layoutDirection = layoutDirection,
                canEnterPreview = { currentConversation.messageNodes.isNotEmpty() },
                isPreviewMode = { currentPreviewMode },
                onPreviewModeChange = { currentOnPreviewModeChange(it) },
            ),
    ) {
        // 两页像翻页一样整体平移：聊天页向左滑出，概览页从右侧跟进
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = previewProgress.value
                    translationX = -p * size.width * layoutDirectionSign
                    alpha = if (showNormal) 1f - p * 0.4f else 0f
                },
        ) {
            ChatListNormal(
                innerPadding = innerPadding,
                conversation = conversation,
                state = state,
                loading = loading,
                processingStatus = processingStatus,
                settings = settings,
                hazeState = hazeState,
                checkpoint = checkpoint,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onForkMessage = onForkMessage,
                onDelete = onDelete,
                onBatchDelete = onBatchDelete,
                onUpdateCheckpointSummary = onUpdateCheckpointSummary,
                onUpdateMessage = onUpdateMessage,
                onClickSuggestion = onClickSuggestion,
                onTranslate = onTranslate,
                onClearTranslation = onClearTranslation,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onToggleFavorite = onToggleFavorite,
                onToggleHide = onToggleHide,
                onConversationSystemPromptChange = onConversationSystemPromptChange,
                selectionState = selectionState,
            )
        }

        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = previewProgress.value
                        translationX = (1f - p) * size.width * layoutDirectionSign
                        alpha = 0.4f + p * 0.6f
                    },
            ) {
                ChatListPreview(
                    innerPadding = innerPadding,
                    conversation = conversation,
                    chatListState = state,
                    listState = previewListState,
                    settings = settings,
                    hazeState = hazeState,
                    onJumpToMessage = onJumpToMessage,
                )
            }
        }
    }
}

// 顶部渐隐：消息滚到标题栏下面时逐渐透明，从状态栏一直淡出到标题栏下沿之外，保证标题可读。
// 让内容自己变透明而不是盖一层背景色，背景图和动态渐变背景才不会被盖住。
internal fun Modifier.topFadingEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fadeHeight = height.toPx()
        val brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color.Black.copy(alpha = 0.15f),
            1f to Color.Black,
            startY = 0f,
            endY = fadeHeight,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush, size = Size(size.width, fadeHeight), blendMode = BlendMode.DstIn)
        }
    }

@Composable
private fun ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    processingStatus: String? = null,
    settings: Settings,
    hazeState: HazeState,
    checkpoint: ConversationCheckpointEntity? = null,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onBatchDelete: (Set<Uuid>) -> Unit = {},
    onUpdateCheckpointSummary: ((checkpointId: String, newSummary: String) -> Unit)? = null,
    onUpdateMessage: (MessageNode) -> Unit,
    onClickSuggestion: (String) -> Unit,
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)?,
    onClearTranslation: (UIMessage) -> Unit,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onToggleHide: ((UIMessage) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
    selectionState: ChatSelectionState,
) {
    val scope = rememberCoroutineScope()
    val loadingState by rememberUpdatedState(loading)
    var isRecentScroll by remember { mutableStateOf(false) }
    val conversationUpdated by rememberUpdatedState(conversation)
    val density = LocalDensity.current
    val activity = LocalContext.current as? me.rerere.rikkahub.RouteActivity

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Boolean = { isVolumeUp ->
            if (settings.displaySetting.enableVolumeKeyScroll) {
                // 列表视口包含标题栏和输入区遮住的部分，翻页时只按可见区域计算
                val coveredPx = with(density) {
                    (innerPadding.calculateTopPadding() + 32.dp + innerPadding.calculateBottomPadding()).toPx()
                }
                val scrollAmount = (state.layoutInfo.viewportSize.height - coveredPx) *
                    settings.displaySetting.volumeKeyScrollRatio
                scope.launch { state.scrollBy(if (isVolumeUp) -scrollAmount else scrollAmount) }
                true
            } else false
        }
        activity?.volumeKeyListeners?.add(listener)
        onDispose {
            activity?.volumeKeyListeners?.remove(listener)
        }
    }

    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        val lastItem = lastOrNull() ?: return false
        val inputBarHeight = with(density) { innerPadding.calculateBottomPadding().toPx() }
        val lastPos = lastItem.offset + lastItem.size
        val inputPos = (state.layoutInfo.viewportEndOffset - inputBarHeight.roundToInt())
        // println("lastPos = $lastPos, inputPos = $inputPos  | ${lastPos <= inputPos - 8}")
        return lastPos <= inputPos - 8
    }

    // 聊天选择
    val selectedItems = selectionState.selectedItems
    var showExportSheet by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // 自动跟随键盘滚动
    ImeLazyListAutoScroller(lazyListState = state)

    val assistant = remember(
        settings.assistants,
        conversation.assistantId,
        conversation.config,
        conversation.modeInjectionIds,
        conversation.lorebookIds,
    ) {
        // 工作区等配置在会话开始后以会话上固定的为准
        settings.getAssistantById(conversation.assistantId)?.withConversation(conversation)
    }
    val modelById = remember(settings.providers) {
        settings.providers
            .flatMap { it.models }
            .associateBy { it.id }
    }
    val lastMessageIndex = conversation.messageNodes.lastIndex
    var isCheckpointExpanded by rememberSaveable(checkpoint?.id) { mutableStateOf(false) }

    val startIdx = remember(checkpoint) {
        checkpoint?.let { (it.startFloor - 1).coerceAtLeast(0) } ?: 0
    }
    val endIdx = remember(checkpoint, conversation.messageNodes.size) {
        checkpoint?.let { (it.boundaryFloor - 1).coerceAtMost(conversation.messageNodes.lastIndex) } ?: 0
    }

    val nodesBefore = remember(conversation.messageNodes, checkpoint, startIdx) {
        if (checkpoint != null) {
            conversation.messageNodes.mapIndexed { idx, node -> idx to node }.filter { it.first < startIdx }
        } else emptyList()
    }
    val nodesCompacted = remember(conversation.messageNodes, checkpoint, startIdx, endIdx) {
        if (checkpoint != null) {
            conversation.messageNodes.mapIndexed { idx, node -> idx to node }.filter { it.first in startIdx..endIdx }
        } else emptyList()
    }
    val nodesAfter = remember(conversation.messageNodes, checkpoint, endIdx) {
        if (checkpoint != null) {
            conversation.messageNodes.mapIndexed { idx, node -> idx to node }.filter { it.first > endIdx }
        } else emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // 自动滚动到底部
        if (settings.displaySetting.enableAutoScroll) {
            LaunchedEffect(state) {
                snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { visibleItemsInfo ->
                    // println("is bottom = ${visibleItemsInfo.isAtBottom()}, scroll = ${state.isScrollInProgress}, can_scroll = ${state.canScrollForward}, loading = $loading")
                    if (!state.isScrollInProgress && loadingState) {
                        if (visibleItemsInfo.isAtBottom()) {
                            state.requestScrollToItem(conversationUpdated.messageNodes.lastIndex + 10)
                            // Log.i(TAG, "ChatList: scroll to ${conversationUpdated.messageNodes.lastIndex}")
                        }
                    }
                }
            }
        }

        // 判断最近是否滚动
        LaunchedEffect(state.isScrollInProgress) {
            if (state.isScrollInProgress) {
                isRecentScroll = true
                delay(1500)
                isRecentScroll = false
            } else {
                delay(1500)
                isRecentScroll = false
            }
        }

        ChatFontProvider(displaySetting = settings.displaySetting) {
            LazyColumn(
                state = state,
                // 顶部留白放进 contentPadding，让消息可以滚动到透明标题栏下面
                contentPadding = PaddingValues(16.dp) + PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 32.dp + innerPadding.calculateBottomPadding(),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .topFadingEdge(innerPadding.calculateTopPadding() + 24.dp)
                    .hazeSource(state = hazeState),
            ) {
            val renderMessageNodeItem: @Composable (Int, MessageNode) -> Unit = { index, node ->
                Column {
                    ListSelectableItem(
                        key = node.id,
                        onSelectChange = {
                            if (!selectedItems.contains(node.id)) {
                                selectedItems.add(node.id)
                            } else {
                                selectedItems.remove(node.id)
                            }
                        },
                        selectedKeys = selectedItems,
                        enabled = selectionState.selecting,
                    ) {
                        ChatMessage(
                            node = node,
                            model = node.currentMessage.modelId?.let(modelById::get)
                                ?: node.currentMessage.snapshotModel(),
                            assistant = assistant,
                            loading = loading && index == lastMessageIndex,
                            nodeIndex = index,
                            onRegenerate = {
                                onRegenerate(node.currentMessage)
                            },
                            onEdit = {
                                onEdit(node.currentMessage)
                            },
                            onFork = {
                                onForkMessage(node.currentMessage)
                            },
                            onDelete = {
                                onDelete(node.currentMessage)
                            },
                            onShare = {
                                // 默认选中从第一条到这一条
                                selectionState.start(
                                    conversation.messageNodes.map { it.id }
                                        .subList(0, conversation.messageNodes.indexOf(node) + 1)
                                )
                            },
                            onUpdate = {
                                onUpdateMessage(it)
                            },
                            isFavorite = node.isFavorite,
                            onToggleFavorite = {
                                onToggleFavorite?.invoke(node)
                            },
                            onToggleHide = onToggleHide?.let { action -> { action(node.currentMessage) } },
                            onBatchSelect = {
                                selectionState.start(listOf(node.id))
                            },
                            onTranslate = onTranslate,
                            onClearTranslation = onClearTranslation,
                            onToolApproval = onToolApproval,
                            onToolAnswer = onToolAnswer,
                            lastMessage = index == lastMessageIndex,
                        )
                    }
                }
            }

            if (checkpoint == null) {
                items(
                    items = conversation.messageNodes.mapIndexed { index, node -> index to node },
                    key = { (_, item) -> item.id },
                ) { (index, node) ->
                    renderMessageNodeItem(index, node)
                }
            } else {
                items(
                    items = nodesBefore,
                    key = { (_, item) -> item.id },
                ) { (index, node) ->
                    renderMessageNodeItem(index, node)
                }

                item(key = "checkpoint_${checkpoint.id}") {
                    CompactedSummaryCard(
                        checkpoint = checkpoint,
                        isExpanded = isCheckpointExpanded,
                        onToggleExpand = { isCheckpointExpanded = !isCheckpointExpanded },
                        onUpdateSummary = onUpdateCheckpointSummary?.let { action ->
                            { newSummary -> action(checkpoint.id, newSummary) }
                        },
                    )
                }

                if (isCheckpointExpanded) {
                    items(
                        items = nodesCompacted,
                        key = { (_, item) -> item.id },
                    ) { (index, node) ->
                        renderMessageNodeItem(index, node)
                    }
                }

                items(
                    items = nodesAfter,
                    key = { (_, item) -> item.id },
                ) { (index, node) ->
                    renderMessageNodeItem(index, node)
                }
            }

            if (!loading && assistant?.allowConversationSystemPrompt == true && onConversationSystemPromptChange != null) {
                item(key = "ConversationSystemPrompt") {
                    ConversationSystemPromptButton(
                        customSystemPrompt = conversation.customSystemPrompt,
                        onSystemPromptChange = onConversationSystemPromptChange,
                    )
                }
            }

            if (loading) {
                item(LoadingIndicatorKey) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(28.dp)
                        )
                        AnimatedVisibility(
                            visible = processingStatus != null,
                        ) {
                            Text(
                                text = processingStatus ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 为了能正确滚动到这
            item(ScrollBottomKey) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 错误消息卡片
            ErrorCardsDisplay(
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(5f)
            )

            // 完成选择
            ChatSelectionToolbar(
                state = selectionState,
                allIds = conversation.messageNodes.map { it.id },
                onConfirm = {
                    selectionState.selecting = false
                    if (conversation.messageNodes.any { it.id in selectedItems }) {
                        showExportSheet = true
                    }
                },
                onDelete = {
                    if (selectedItems.isNotEmpty()) {
                        showBatchDeleteConfirm = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(48).dp),
            )

            RikkaConfirmDialog(
                show = showBatchDeleteConfirm,
                title = stringResource(R.string.chat_message_batch_delete_title),
                confirmText = stringResource(R.string.delete),
                dismissText = stringResource(R.string.cancel),
                onConfirm = {
                    showBatchDeleteConfirm = false
                    val idsToDelete = selectedItems.toSet()
                    selectionState.cancel()
                    if (idsToDelete.isNotEmpty()) {
                        onBatchDelete(idsToDelete)
                    }
                },
                onDismiss = { showBatchDeleteConfirm = false },
            ) {
                Text(stringResource(R.string.chat_message_batch_delete_confirm, selectedItems.size))
            }

            // 导出对话框
            ChatExportSheet(
                visible = showExportSheet,
                onDismissRequest = {
                    showExportSheet = false
                    selectedItems.clear()
                },
                conversation = conversation,
                selectedMessages = conversation.messageNodes.filter { it.id in selectedItems }
                    .map { it.currentMessage }
            )

            val captureProgress = LocalScrollCaptureInProgress.current

            // 消息快速跳转
            MessageJumper(
                show = isRecentScroll && !state.isScrollInProgress && settings.displaySetting.showMessageJumper && !captureProgress,
                onLeft = settings.displaySetting.messageJumperOnLeft,
                scope = scope,
                state = state
            )

            // Suggestion
            if (conversation.chatSuggestions.isNotEmpty() && !captureProgress) {
                ChatSuggestionsRow(
                    conversation = conversation,
                    onClickSuggestion = onClickSuggestion,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun CompactedSummaryCard(
    checkpoint: ConversationCheckpointEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdateSummary: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var isSummaryExpanded by rememberSaveable(checkpoint.id) { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val collapsedPreview = remember(checkpoint.summaryContent) {
        buildCollapsedSummaryPreview(checkpoint.summaryContent)
    }
    val isCollapsible = collapsedPreview != null
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val copiedToastText = stringResource(R.string.copied_to_clipboard)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Package01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "已压缩第 ${checkpoint.startFloor}-${checkpoint.boundaryFloor} 轮对话",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (isExpanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (isExpanded) "收起原文" else "展开原文",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 居中展示压缩得到的摘要正文（默认仅显示前几行，点击可展开全部内容）
            if (!isSummaryExpanded && collapsedPreview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 108.dp)
                        .clipToBounds()
                        .clickable { isSummaryExpanded = true },
                ) {
                    MarkdownBlock(
                        content = collapsedPreview,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, containerColor)
                                )
                            )
                    )
                }
            } else {
                SelectionContainer {
                    MarkdownBlock(
                        content = checkpoint.summaryContent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (isCollapsible) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isSummaryExpanded = !isSummaryExpanded }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isSummaryExpanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (isSummaryExpanded) "收起压缩内容" else "展开压缩内容",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            context.writeClipboardText(checkpoint.summaryContent)
                            toaster.show(copiedToastText, type = ToastType.Success)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Copy01,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.copy),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (onUpdateSummary != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEditDialog = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Edit01,
                            contentDescription = stringResource(R.string.edit),
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.edit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog && onUpdateSummary != null) {
        var editedSummary by remember(checkpoint.summaryContent) { mutableStateOf(checkpoint.summaryContent) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.compact_summary_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editedSummary,
                    onValueChange = { editedSummary = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 420.dp),
                    minLines = 6,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSummary(editedSummary.trim())
                        showEditDialog = false
                    },
                    enabled = editedSummary.isNotBlank(),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun buildCollapsedSummaryPreview(
    summary: String,
    maxNonBlankLines: Int = 4,
    maxChars: Int = 200,
): String? {
    val trimmed = summary.trim()
    if (trimmed.isEmpty()) return null
    val lines = trimmed.lines()
    val nonBlankCount = lines.count { it.isNotBlank() }
    if (nonBlankCount <= maxNonBlankLines && trimmed.length <= maxChars) {
        return null
    }
    val previewLines = mutableListOf<String>()
    var seenNonBlank = 0
    var charCount = 0
    for (line in lines) {
        if (line.isNotBlank()) {
            seenNonBlank++
        }
        val remaining = maxChars - charCount
        if (remaining <= 0) break
        if (line.length > remaining) {
            previewLines += line.take(remaining).trimEnd() + "…"
            break
        } else {
            previewLines += line
            charCount += line.length + 1
        }
        if (seenNonBlank >= maxNonBlankLines) break
    }
    return previewLines.joinToString("\n").trim()
}
