package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation

enum class FloorHideViewMode {
    OVERVIEW,
    HIDDEN_LIST,
    ALL_MESSAGES_LIST,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorHideSheet(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onHideRange: (rangeText: String, isHide: Boolean) -> Unit,
    onToggleHideFloor: (floorIndex: Int) -> Unit,
    onBatchSetHidden: (indices: Set<Int>, isHide: Boolean) -> Unit,
    onJumpToMessage: (floorIndex: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewMode by remember { mutableStateOf(FloorHideViewMode.OVERVIEW) }

    val allNodesWithIndex = remember(conversation.messageNodes) {
        conversation.messageNodes.mapIndexed { index, node -> index to node }
    }
    val hiddenNodes = remember(allNodesWithIndex) {
        allNodesWithIndex.filter { it.second.currentMessage.isHidden }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = viewMode,
            label = "FloorHideModeTransition",
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { targetMode ->
            when (targetMode) {
                FloorHideViewMode.OVERVIEW -> {
                    FloorHideOverview(
                        totalFloors = conversation.messageNodes.size,
                        hiddenCount = hiddenNodes.size,
                        onHideRange = onHideRange,
                        onViewHiddenList = { viewMode = FloorHideViewMode.HIDDEN_LIST },
                        onViewAllMessages = { viewMode = FloorHideViewMode.ALL_MESSAGES_LIST },
                        onDismiss = onDismiss,
                    )
                }

                FloorHideViewMode.HIDDEN_LIST -> {
                    HiddenFloorsListView(
                        hiddenNodes = hiddenNodes,
                        onBack = { viewMode = FloorHideViewMode.OVERVIEW },
                        onUnhide = { onToggleHideFloor(it) },
                        onJump = { index ->
                            onDismiss()
                            onJumpToMessage(index)
                        },
                    )
                }

                FloorHideViewMode.ALL_MESSAGES_LIST -> {
                    AllMessagesListView(
                        allNodes = allNodesWithIndex,
                        onBack = { viewMode = FloorHideViewMode.OVERVIEW },
                        onBatchSetHidden = onBatchSetHidden,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloorHideOverview(
    totalFloors: Int,
    hiddenCount: Int,
    onHideRange: (rangeText: String, isHide: Boolean) -> Unit,
    onViewHiddenList: () -> Unit,
    onViewAllMessages: () -> Unit,
    onDismiss: () -> Unit,
) {
    var rangeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.ViewOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "楼层隐藏与显示管理",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(HugeIcons.Cancel01, contentDescription = "Close")
            }
        }

        Text(
            text = "当前会话共有 $totalFloors 层对话，被隐藏的楼层将被彻底排除在 AI 模型上下文之外。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 范围输入模块
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "输入指定楼层范围",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = rangeInput,
                    onValueChange = { rangeInput = it },
                    placeholder = { Text("示例: 5 或 3-18") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            if (rangeInput.isNotBlank()) {
                                onHideRange(rangeInput.trim(), true)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = rangeInput.isNotBlank(),
                    ) {
                        Icon(HugeIcons.ViewOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("隐藏该范围")
                    }

                    OutlinedButton(
                        onClick = {
                            if (rangeInput.isNotBlank()) {
                                onHideRange(rangeInput.trim(), false)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = rangeInput.isNotBlank(),
                    ) {
                        Icon(HugeIcons.View, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("取消隐藏")
                    }
                }
            }
        }

        // 查看已隐藏楼层入口
        OutlinedCard(
            onClick = onViewHiddenList,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.ViewOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "查看目前被隐藏的楼层",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "当前已隐藏 $hiddenCount 层消息，点击查看具体内容与恢复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 进入消息列表多选管理入口
        OutlinedCard(
            onClick = onViewAllMessages,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.LeftToRightListBullet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "进入消息列表 (多选管理)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "浏览每层内容、展开详情并批量多选隐藏或取消",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HiddenFloorsListView(
    hiddenNodes: List<Pair<Int, me.rerere.rikkahub.data.model.MessageNode>>,
    onBack: () -> Unit,
    onUnhide: (floorIndex: Int) -> Unit,
    onJump: (floorIndex: Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 520.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = "Back")
            }
            Text(
                text = "已隐藏楼层列表 (${hiddenNodes.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        if (hiddenNodes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.View,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = "当前没有被隐藏的楼层",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(hiddenNodes, key = { it.second.id }) { (index, node) ->
                    val message = node.currentMessage
                    val isUser = message.role == MessageRole.USER
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "第 ${index + 1} 轮 · ${if (isUser) "用户" else "助手"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { onUnhide(index) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text("取消隐藏", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { onJump(index) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text("跳转", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text(
                                text = message.toText().trim().ifBlank { "[无文本内容]" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllMessagesListView(
    allNodes: List<Pair<Int, me.rerere.rikkahub.data.model.MessageNode>>,
    onBack: () -> Unit,
    onBatchSetHidden: (indices: Set<Int>, isHide: Boolean) -> Unit,
) {
    val selectedIndices = remember { mutableStateListOf<Int>() }
    val expandedIndices = remember { mutableStateListOf<Int>() }

    val allSelected by remember(allNodes.size) {
        derivedStateOf { selectedIndices.size == allNodes.size && allNodes.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp, max = 560.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = "Back")
            }
            Text(
                text = "消息列表管理 (${allNodes.size} 层)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    if (allSelected) {
                        selectedIndices.clear()
                    } else {
                        selectedIndices.clear()
                        selectedIndices.addAll(allNodes.map { it.first })
                    }
                }
            ) {
                Text(if (allSelected) "取消全选" else "全选")
            }
        }

        // 批量操作按钮条
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onBatchSetHidden(selectedIndices.toSet(), true)
                },
                modifier = Modifier.weight(1f),
                enabled = selectedIndices.isNotEmpty(),
            ) {
                Text("隐藏选中 (${selectedIndices.size})")
            }
            OutlinedButton(
                onClick = {
                    onBatchSetHidden(selectedIndices.toSet(), false)
                },
                modifier = Modifier.weight(1f),
                enabled = selectedIndices.isNotEmpty(),
            ) {
                Text("取消隐藏选中 (${selectedIndices.size})")
            }
        }

        // 消息列表
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(allNodes, key = { it.second.id }) { (index, node) ->
                val message = node.currentMessage
                val isUser = message.role == MessageRole.USER
                val isSelected = selectedIndices.contains(index)
                val isExpanded = expandedIndices.contains(index)
                val isHidden = message.isHidden

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selectedIndices.remove(index) else selectedIndices.add(index)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked == true) selectedIndices.add(index) else selectedIndices.remove(index)
                            },
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "第 ${index + 1} 轮 · ${if (isUser) "用户" else "助手"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    )
                                    if (isHidden) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ) {
                                            Text(
                                                text = "已隐藏",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        if (isExpanded) expandedIndices.remove(index) else expandedIndices.add(index)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp),
                                ) {
                                    Text(
                                        text = if (isExpanded) "收起" else "展开",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }

                            Text(
                                text = message.toText().trim().ifBlank { "[无文本内容]" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
