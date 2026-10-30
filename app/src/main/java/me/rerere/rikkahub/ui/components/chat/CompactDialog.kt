package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.DEFAULT_COMPACT_PROMPT

@Composable
fun CompactDialog(
    conversation: Conversation,
    settings: Settings,
    currentModel: Model?,
    onDismiss: () -> Unit,
    onConfirm: (
        startFloor: Int,
        endFloor: Int,
        targetTokensK: Int,
        model: Model,
        compressPrompt: String,
        autoHide: Boolean,
    ) -> Unit,
) {
    val totalFloors = conversation.messageNodes.size
    var startFloorText by remember { mutableStateOf("1") }
    var endFloorText by remember { mutableStateOf(totalFloors.coerceAtLeast(1).toString()) }
    var targetTokensText by remember { mutableStateOf("2") }

    val availableModels = remember(settings.providers) {
        settings.providers.filter { it.enabled }.flatMap { provider ->
            provider.models.filter { it.type == ModelType.CHAT }.map { it to provider.name }
        }
    }

    var selectedModel by remember {
        mutableStateOf(currentModel ?: availableModels.firstOrNull()?.first)
    }
    var showModelMenu by remember { mutableStateOf(false) }

    var promptText by remember { mutableStateOf(DEFAULT_COMPACT_PROMPT) }
    var autoHide by remember { mutableStateOf(true) }
    var isCompressing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isCompressing) onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Package01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "压缩对话上下文",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "当前会话共 $totalFloors 层对话，压缩后将生成结构化背景记忆。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 楼层范围
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = startFloorText,
                        onValueChange = {
                            startFloorText = it.filter { char -> char.isDigit() }
                            errorMessage = null
                        },
                        label = { Text("起始楼层") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isCompressing,
                    )
                    Text(
                        text = "至",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = endFloorText,
                        onValueChange = {
                            endFloorText = it.filter { char -> char.isDigit() }
                            errorMessage = null
                        },
                        label = { Text("结尾楼层") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isCompressing,
                    )
                }

                // 目标大小
                OutlinedTextField(
                    value = targetTokensText,
                    onValueChange = {
                        targetTokensText = it.filter { char -> char.isDigit() }
                        errorMessage = null
                    },
                    label = { Text("目标大小 (单位: K tokens)") },
                    placeholder = { Text("例如 2 代表 2K tokens") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isCompressing,
                )

                // 使用的模型选择
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "使用的压缩模型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { if (!isCompressing) showModelMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val currentProviderName = availableModels.find { it.first.id == selectedModel?.id }?.second
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedModel?.displayName ?: "未选择模型",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (currentProviderName != null) {
                                        Text(
                                            text = currentProviderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = HugeIcons.ArrowDown01,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                            modifier = Modifier.heightIn(max = 280.dp),
                        ) {
                            if (availableModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无启用的模型") },
                                    onClick = { showModelMenu = false },
                                )
                            } else {
                                availableModels.forEach { (model, providerName) ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Text(
                                                    text = providerName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedModel = model
                                            showModelMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 提示词输入
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("发给压缩模型的提示词") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isCompressing,
                )

                // 压缩后是否自动 hide
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCompressing) { autoHide = !autoHide }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "压缩后自动隐藏已压缩楼层",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "隐藏后上述楼层将不再发给后续模型请求",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoHide,
                        onCheckedChange = { autoHide = it },
                        enabled = !isCompressing,
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = startFloorText.trim().toIntOrNull()
                    val end = endFloorText.trim().toIntOrNull()
                    val targetK = targetTokensText.trim().toIntOrNull()
                    val model = selectedModel

                    if (start == null || start < 1 || start > totalFloors) {
                        errorMessage = "起始楼层必须在 1 到 $totalFloors 之间"
                        return@Button
                    }
                    if (end == null || end < start || end > totalFloors) {
                        errorMessage = "结尾楼层必须在 $start 到 $totalFloors 之间"
                        return@Button
                    }
                    if (targetK == null || targetK <= 0) {
                        errorMessage = "目标大小必须为大于 0 的数字"
                        return@Button
                    }
                    if (model == null) {
                        errorMessage = "请选择一个启用的压缩模型"
                        return@Button
                    }

                    isCompressing = true
                    onConfirm(start, end, targetK, model, promptText, autoHide)
                },
                enabled = !isCompressing && selectedModel != null,
            ) {
                if (isCompressing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("压缩中...")
                } else {
                    Text("开始压缩")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCompressing,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
