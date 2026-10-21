package me.rerere.rikkahub.ui.pages.extensions

import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FileDownload
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Share03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.export.LorebookSerializer
import me.rerere.rikkahub.data.export.ModeInjectionSerializer
import me.rerere.rikkahub.ui.hooks.rememberExporter
import me.rerere.rikkahub.ui.hooks.rememberImporter
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.repository.StPresetRecord
import me.rerere.rikkahub.data.stpreset.StPromptAssembler
import me.rerere.rikkahub.data.stpreset.StPromptRow
import me.rerere.rikkahub.data.stpreset.StPresetMarkers
import me.rerere.rikkahub.data.stpreset.StPresetParser
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.ExportDialog
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.FormItem
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.components.Select
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

private enum class PromptTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    MODE_INJECTIONS(HugeIcons.MagicWand01, R.string.prompt_page_mode_injection_tab),
    LOREBOOKS(HugeIcons.Book01, R.string.prompt_page_lorebook_tab),
    ST_PRESETS(HugeIcons.Book02, R.string.prompt_page_st_presets_tab);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun PromptPage(vm: PromptVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stPresetRecords by vm.stPresetRecords.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { PromptTab.entries.size }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(stringResource(R.string.prompt_page_title)) },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PagerTabs(
                pagerState = pagerState,
                tabs = PromptTab.entries,
                modifier = Modifier.padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (PromptTab.entries[page]) {
                    PromptTab.MODE_INJECTIONS -> ModeInjectionTab(
                        modeInjections = settings.modeInjections,
                        onUpdate = { vm.updateSettings { latest -> latest.copy(modeInjections = it) } }
                    )

                    PromptTab.LOREBOOKS -> LorebookTab(
                        lorebooks = settings.lorebooks,
                        onUpdate = { vm.updateSettings { latest -> latest.copy(lorebooks = it) } }
                    )

                    PromptTab.ST_PRESETS -> StPresetTab(
                        vm = vm,
                        records = stPresetRecords,
                        assistants = settings.assistants,
                        personaName = settings.displaySetting.userNickname,
                        userPersona = settings.displaySetting.userPersona,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeInjectionTab(
    modeInjections: List<PromptInjection.ModeInjection>,
    onUpdate: (List<PromptInjection.ModeInjection>) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val toaster = LocalToaster.current
    val currentModeInjections by rememberUpdatedState(modeInjections)
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = modeInjections.toMutableList()
        val item = newList.removeAt(from.index)
        newList.add(to.index, item)
        onUpdate(newList)
    }
    val editState = useEditState<PromptInjection.ModeInjection> { edited ->
        val index = modeInjections.indexOfFirst { it.id == edited.id }
        if (index >= 0) {
            onUpdate(modeInjections.toMutableList().apply { set(index, edited) })
        } else {
            onUpdate(modeInjections + edited)
        }
    }
    val importSuccessMsg = stringResource(R.string.export_import_success)
    val importFailedMsg = stringResource(R.string.export_import_failed)
    val importer = rememberImporter(ModeInjectionSerializer) { result ->
        result.onSuccess { imported ->
            onUpdate(currentModeInjections + imported)
            toaster.show(importSuccessMsg)
        }.onFailure { error ->
            toaster.show(importFailedMsg.format(error.message))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (modeInjections.isEmpty()) {
            ExtensionEmptyState(
                message = stringResource(R.string.prompt_page_mode_injection_empty),
                description = stringResource(R.string.prompt_page_empty_hint),
                icon = HugeIcons.MagicWand01,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .floatingToolbarVerticalNestedScroll(
                        expanded = expanded,
                        onExpand = { expanded = true },
                        onCollapse = { expanded = false }
                    ),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState
            ) {
                items(modeInjections, key = { it.id }) { injection ->
                    ReorderableItem(
                        state = reorderableState,
                        key = injection.id
                    ) { isDragging ->
                        ModeInjectionCard(
                            injection = injection,
                            modifier = longPressReorder(isDragging),
                            onEdit = { editState.open(injection) },
                            onDelete = { onUpdate(modeInjections - injection) }
                        )
                    }
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = expanded,
            colors = CustomColors.floatingToolbarColors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            leadingContent = {
                IconButton(onClick = { importer.importFromFile() }) {
                    Icon(HugeIcons.FileImport, null)
                }
            },
        ) {
            Button(onClick = { editState.open(PromptInjection.ModeInjection()) }) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(HugeIcons.Add01, null)
                    AnimatedVisibility(expanded) {
                        Row {
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.prompt_page_add_mode_injection))
                        }
                    }
                }
            }
        }
    }

    if (editState.isEditing) {
        editState.currentState?.let { state ->
            ModeInjectionEditSheet(
                injection = state,
                onDismiss = { editState.dismiss() },
                onConfirm = { editState.confirm() },
                onEdit = { editState.currentState = it }
            )
        }
    }
}

@Composable
private fun ModeInjectionCard(
    injection: PromptInjection.ModeInjection,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val exporter = rememberExporter(injection, ModeInjectionSerializer)

    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 停用的条目图标褪成中性色
            CardGroupIcon(
                icon = HugeIcons.MagicWand01,
                containerColor = if (injection.enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (injection.enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = injection.name.ifEmpty { stringResource(R.string.prompt_page_unnamed) },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(type = TagType.INFO) {
                        Text(getPositionLabel(injection.position))
                    }
                    Tag(type = TagType.DEFAULT) {
                        Text(stringResource(R.string.prompt_page_priority_format, injection.priority))
                    }
                    if (!injection.enabled) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.prompt_page_disabled))
                        }
                    }
                }
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.export_title),
                        icon = HugeIcons.Share03,
                        onClick = { showExportDialog = true },
                    ),
                    ItemAction(
                        text = stringResource(R.string.prompt_page_delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = { showDeleteDialog = true },
                    ),
                )
            )
        }
    }

    RikkaConfirmDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteDialog = false
            onDelete()
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, injection.name))
    }

    if (showExportDialog) {
        ExportDialog(
            exporter = exporter,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun ModeInjectionEditSheet(
    injection: PromptInjection.ModeInjection,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: (PromptInjection.ModeInjection) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }) {
                Icon(HugeIcons.ArrowDown01, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.prompt_page_edit_mode_injection),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = injection.name,
                    onValueChange = { onEdit(injection.copy(name = it)) },
                    label = { Text(stringResource(R.string.prompt_page_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                FormItem(
                    label = { Text(stringResource(R.string.prompt_page_enabled)) },
                    tail = {
                        Switch(
                            checked = injection.enabled,
                            onCheckedChange = { onEdit(injection.copy(enabled = it)) }
                        )
                    }
                )

                OutlinedTextField(
                    value = injection.priority.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { p -> onEdit(injection.copy(priority = p)) }
                    },
                    label = { Text(stringResource(R.string.prompt_page_priority_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Text(
                    stringResource(R.string.prompt_page_injection_position),
                    style = MaterialTheme.typography.titleSmall
                )
                InjectionPositionSelector(
                    position = injection.position,
                    onSelect = { onEdit(injection.copy(position = it)) }
                )

                AnimatedVisibility(visible = injection.position == InjectionPosition.AT_DEPTH) {
                    OutlinedTextField(
                        value = injection.injectDepth.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { d -> onEdit(injection.copy(injectDepth = d)) }
                        },
                        label = { Text(stringResource(R.string.prompt_page_inject_depth)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                AnimatedVisibility(visible = injection.position.usesStandaloneMessage()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(R.string.prompt_page_injection_role),
                            style = MaterialTheme.typography.titleSmall
                        )
                        InjectionRoleSelector(
                            role = injection.role,
                            onSelect = { onEdit(injection.copy(role = it)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = injection.content,
                    onValueChange = { onEdit(injection.copy(content = it)) },
                    label = { Text(stringResource(R.string.prompt_page_injection_content)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    minLines = 5
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.prompt_page_cancel))
                }
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.prompt_page_confirm))
                }
            }
        }
    }
}

@Composable
private fun InjectionPositionSelector(
    position: InjectionPosition,
    onSelect: (InjectionPosition) -> Unit
) {
    Select(
        options = InjectionPosition.entries,
        selectedOption = position,
        onOptionSelected = onSelect,
        optionToString = { getPositionLabel(it) },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun InjectionPosition.usesStandaloneMessage(): Boolean = when (this) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT,
    InjectionPosition.AFTER_SYSTEM_PROMPT -> false

    InjectionPosition.TOP_OF_CHAT,
    InjectionPosition.BOTTOM_OF_CHAT,
    InjectionPosition.AT_DEPTH -> true
}

@Composable
private fun getPositionLabel(position: InjectionPosition): String = when (position) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT -> stringResource(R.string.prompt_page_position_before_system)
    InjectionPosition.AFTER_SYSTEM_PROMPT -> stringResource(R.string.prompt_page_position_after_system)
    InjectionPosition.TOP_OF_CHAT -> stringResource(R.string.prompt_page_position_top_of_chat)
    InjectionPosition.BOTTOM_OF_CHAT -> stringResource(R.string.prompt_page_position_bottom_of_chat)
    InjectionPosition.AT_DEPTH -> stringResource(R.string.prompt_page_position_at_depth)
}

@Composable
private fun InjectionRoleSelector(
    role: MessageRole,
    onSelect: (MessageRole) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    ConnectedChoiceButtons(
        options = listOf(MessageRole.USER, MessageRole.ASSISTANT),
        selected = role,
        onSelect = onSelect,
        containerColor = containerColor,
    ) { option, selected ->
        ConnectedChoiceLabel(text = getRoleLabel(option), selected = selected)
    }
}

@Composable
private fun getRoleLabel(role: MessageRole): String = when (role) {
    MessageRole.USER -> stringResource(R.string.prompt_page_role_user)
    MessageRole.ASSISTANT -> stringResource(R.string.prompt_page_role_assistant)
    else -> role.name
}

// ==================== Lorebook Tab ====================

@Composable
private fun LorebookTab(
    lorebooks: List<Lorebook>,
    onUpdate: (List<Lorebook>) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val toaster = LocalToaster.current
    val currentLorebooks by rememberUpdatedState(lorebooks)
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = lorebooks.toMutableList()
        val item = newList.removeAt(from.index)
        newList.add(to.index, item)
        onUpdate(newList)
    }
    val editState = useEditState<Lorebook> { edited ->
        val index = lorebooks.indexOfFirst { it.id == edited.id }
        if (index >= 0) {
            onUpdate(lorebooks.toMutableList().apply { set(index, edited) })
        } else {
            onUpdate(lorebooks + edited)
        }
    }
    val importSuccessMsg = stringResource(R.string.export_import_success)
    val importFailedMsg = stringResource(R.string.export_import_failed)
    val importer = rememberImporter(LorebookSerializer) { result ->
        result.onSuccess { imported ->
            onUpdate(currentLorebooks + imported)
            toaster.show(importSuccessMsg)
        }.onFailure { error ->
            toaster.show(importFailedMsg.format(error.message))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (lorebooks.isEmpty()) {
            ExtensionEmptyState(
                message = stringResource(R.string.prompt_page_lorebook_empty),
                description = stringResource(R.string.prompt_page_empty_hint),
                icon = HugeIcons.Book01,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .floatingToolbarVerticalNestedScroll(
                        expanded = expanded,
                        onExpand = { expanded = true },
                        onCollapse = { expanded = false }
                    ),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState
            ) {
                items(lorebooks, key = { it.id }) { book ->
                    ReorderableItem(
                        state = reorderableState,
                        key = book.id
                    ) { isDragging ->
                        LorebookCard(
                            book = book,
                            modifier = longPressReorder(isDragging),
                            onEdit = { editState.open(book) },
                            onDelete = { onUpdate(lorebooks - book) }
                        )
                    }
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = expanded,
            colors = CustomColors.floatingToolbarColors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            leadingContent = {
                IconButton(onClick = { importer.importFromFile() }) {
                    Icon(HugeIcons.FileImport, null)
                }
            },
        ) {
            Button(onClick = { editState.open(Lorebook()) }) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(HugeIcons.Add01, null)
                    AnimatedVisibility(expanded) {
                        Row {
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.prompt_page_add_lorebook))
                        }
                    }
                }
            }
        }
    }

    if (editState.isEditing) {
        editState.currentState?.let { state ->
            LorebookEditSheet(
                book = state,
                onDismiss = { editState.dismiss() },
                onConfirm = { editState.confirm() },
                onEdit = { editState.currentState = it }
            )
        }
    }
}

@Composable
private fun LorebookCard(
    book: Lorebook,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val exporter = rememberExporter(book, LorebookSerializer)

    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 停用的条目图标褪成中性色
            CardGroupIcon(
                icon = HugeIcons.Book01,
                containerColor = if (book.enabled) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (book.enabled) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.name.ifEmpty { stringResource(R.string.prompt_page_unnamed_lorebook) },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.description.isNotEmpty()) {
                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(type = TagType.INFO) {
                        Text(
                            stringResource(
                                R.string.prompt_page_entries_count_format,
                                book.entries.size
                            )
                        )
                    }
                    if (!book.enabled) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.prompt_page_disabled))
                        }
                    }
                }
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.export_title),
                        icon = HugeIcons.Share03,
                        onClick = { showExportDialog = true },
                    ),
                    ItemAction(
                        text = stringResource(R.string.prompt_page_delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = { showDeleteDialog = true },
                    ),
                )
            )
        }
    }

    RikkaConfirmDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteDialog = false
            onDelete()
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, book.name))
    }

    if (showExportDialog) {
        ExportDialog(
            exporter = exporter,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun LorebookEditSheet(
    book: Lorebook,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: (Lorebook) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val entryEditState = useEditState<PromptInjection.RegexInjection> { edited ->
        val index = book.entries.indexOfFirst { it.id == edited.id }
        if (index >= 0) {
            onEdit(book.copy(entries = book.entries.toMutableList().apply { set(index, edited) }))
        } else {
            onEdit(book.copy(entries = book.entries + edited))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }) {
                Icon(HugeIcons.ArrowDown01, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.prompt_page_edit_lorebook),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = book.name,
                    onValueChange = { onEdit(book.copy(name = it)) },
                    label = { Text(stringResource(R.string.prompt_page_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = book.description,
                    onValueChange = { onEdit(book.copy(description = it)) },
                    label = { Text(stringResource(R.string.prompt_page_description)) },
                    modifier = Modifier.fillMaxWidth()
                )

                FormItem(
                    label = { Text(stringResource(R.string.prompt_page_enabled)) },
                    tail = {
                        Switch(
                            checked = book.enabled,
                            onCheckedChange = { onEdit(book.copy(enabled = it)) }
                        )
                    }
                )

                // 条目列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.prompt_page_entries_format, book.entries.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    FilledTonalIconButton(onClick = {
                        entryEditState.open(PromptInjection.RegexInjection())
                    }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.prompt_page_add_entry))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    book.entries.fastForEachIndexed { index, entry ->
                        RegexInjectionEntryCard(
                            entry = entry,
                            shape = cardGroupItemShape(index, book.entries.size),
                            onEdit = { entryEditState.open(entry) },
                            onDelete = {
                                onEdit(book.copy(entries = book.entries - entry))
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.prompt_page_cancel))
                }
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.prompt_page_confirm))
                }
            }
        }
    }

    if (entryEditState.isEditing) {
        entryEditState.currentState?.let { state ->
            RegexInjectionEditDialog(
                entry = state,
                onDismiss = { entryEditState.dismiss() },
                onConfirm = { entryEditState.confirm() },
                onEdit = { entryEditState.currentState = it }
            )
        }
    }
}

@Composable
private fun RegexInjectionEntryCard(
    entry: PromptInjection.RegexInjection,
    shape: Shape,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.name.ifEmpty { stringResource(R.string.prompt_page_unnamed_entry) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.keywords.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.prompt_page_keywords_format, entry.keywords.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!entry.enabled) {
                    Tag(type = TagType.WARNING) {
                        Text(stringResource(R.string.prompt_page_disabled))
                    }
                }
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.prompt_page_delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegexInjectionEditDialog(
    entry: PromptInjection.RegexInjection,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: (PromptInjection.RegexInjection) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prompt_page_edit_entry)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = entry.name,
                    onValueChange = { onEdit(entry.copy(name = it)) },
                    label = { Text(stringResource(R.string.prompt_page_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                FormItem(
                    label = { Text(stringResource(R.string.prompt_page_enabled)) },
                    tail = {
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { onEdit(entry.copy(enabled = it)) }
                        )
                    }
                )

                OutlinedTextField(
                    value = entry.priority.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { p -> onEdit(entry.copy(priority = p)) }
                    },
                    label = { Text(stringResource(R.string.prompt_page_priority_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Text(
                    stringResource(R.string.prompt_page_injection_position),
                    style = MaterialTheme.typography.titleSmall
                )
                InjectionPositionSelector(
                    position = entry.position,
                    onSelect = { onEdit(entry.copy(position = it)) }
                )

                AnimatedVisibility(visible = entry.position == InjectionPosition.AT_DEPTH) {
                    OutlinedTextField(
                        value = entry.injectDepth.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { d -> onEdit(entry.copy(injectDepth = d)) }
                        },
                        label = { Text(stringResource(R.string.prompt_page_inject_depth)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // 关键词
                Text(stringResource(R.string.prompt_page_keywords_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entry.keywords.forEach { keyword ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(keyword) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        onEdit(entry.copy(keywords = entry.keywords - keyword))
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(HugeIcons.Cancel01, null, modifier = Modifier.size(12.dp))
                                }
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text(stringResource(R.string.prompt_page_new_keyword)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (newKeyword.isNotBlank()) {
                                onEdit(entry.copy(keywords = entry.keywords + newKeyword.trim()))
                                newKeyword = ""
                            }
                        }
                    ) {
                        Icon(HugeIcons.Add01, stringResource(R.string.prompt_page_add))
                    }
                }

                FormItem(
                    label = { Text(stringResource(R.string.prompt_page_use_regex)) },
                    tail = {
                        Switch(
                            checked = entry.useRegex,
                            onCheckedChange = { onEdit(entry.copy(useRegex = it)) }
                        )
                    }
                )

                FormItem(
                    label = { Text(stringResource(R.string.prompt_page_case_sensitive)) },
                    tail = {
                        Switch(
                            checked = entry.caseSensitive,
                            onCheckedChange = { onEdit(entry.copy(caseSensitive = it)) }
                        )
                    }
                )

                FormItem(
                    label = { Text(stringResource(R.string.prompt_page_constant_active)) },
                    description = { Text(stringResource(R.string.prompt_page_constant_active_desc)) },
                    tail = {
                        Switch(
                            checked = entry.constantActive,
                            onCheckedChange = { onEdit(entry.copy(constantActive = it)) }
                        )
                    }
                )

                OutlinedTextField(
                    value = entry.scanDepth.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { d -> onEdit(entry.copy(scanDepth = d)) }
                    },
                    label = { Text(stringResource(R.string.prompt_page_scan_depth)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                AnimatedVisibility(visible = entry.position.usesStandaloneMessage()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.prompt_page_injection_role),
                            style = MaterialTheme.typography.titleSmall
                        )
                        InjectionRoleSelector(
                            role = entry.role,
                            onSelect = { onEdit(entry.copy(role = it)) },
                            // 对话框底色和默认的按钮底色一样，换一个能分开的
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                        )
                    }
                }

                OutlinedTextField(
                    value = entry.content,
                    onValueChange = { onEdit(entry.copy(content = it)) },
                    label = { Text(stringResource(R.string.prompt_page_injection_content)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            val canSave = entry.keywords.isNotEmpty() || entry.constantActive
            TextButton(
                onClick = onConfirm,
                enabled = canSave
            ) {
                Text(stringResource(R.string.prompt_page_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.prompt_page_cancel))
            }
        }
    )
}

// ==================== ST Preset Tab ====================

@Composable
private fun StPresetTab(
    vm: PromptVM,
    records: List<StPresetRecord>,
    assistants: List<Assistant>,
    personaName: String,
    userPersona: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var detailId by remember { mutableStateOf<Uuid?>(null) }
    var deleting by remember { mutableStateOf<StPresetRecord?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.second.toByteArray())
                    } ?: error("cannot open output stream")
                }
            }
            result.onSuccess {
                toaster.show(context.getString(R.string.prompt_page_st_preset_export_success))
            }.onFailure {
                toaster.show(it.message ?: context.getString(R.string.prompt_page_st_preset_export_failed))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            try {
                val (raw, name) = withContext(Dispatchers.IO) {
                    val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index >= 0) cursor.getString(index) else null
                        } else null
                    }
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("cannot read selected file")
                    text to (displayName?.substringBeforeLast('.') ?: "SillyTavern Preset")
                }
                val report = vm.importStPreset(raw, name)
                toaster.show(
                    context.getString(
                        R.string.prompt_page_st_preset_import_success,
                        report.promptDefinitions,
                        report.enabledEntries,
                    )
                )
            } catch (e: Exception) {
                toaster.show(e.message ?: context.getString(R.string.export_import_failed))
            } finally {
                importing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CustomColors.listItemColors.containerColor
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_page_st_preset_persona),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                        Text(
                            text = stringResource(R.string.prompt_page_st_preset_persona_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = userPersona,
                            onValueChange = { value ->
                                vm.updateSettings { settings ->
                                    settings.copy(displaySetting = settings.displaySetting.copy(userPersona = value))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        ExtensionEmptyState(
                            message = stringResource(R.string.prompt_page_st_presets_empty),
                            description = stringResource(R.string.prompt_page_st_presets_empty_hint),
                            icon = HugeIcons.Book02,
                        )
                    }
                }
            } else {
                items(records, key = { it.preset.id.toString() }) { record ->
                    StPresetCard(
                        record = record,
                        onOpen = { detailId = record.preset.id },
                        onToggle = { vm.setStPresetEnabled(record.preset.id, it) },
                        onExport = {
                            scope.launch {
                                val raw = vm.stPresetRawJson(record.preset.id) ?: return@launch
                                pendingExport = record.preset.name.ifBlank { "st-preset" } to raw
                                exportLauncher.launch("${record.preset.name.ifBlank { "st-preset" }}.json")
                            }
                        },
                        onDelete = { deleting = record },
                    )
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            colors = CustomColors.floatingToolbarColors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            leadingContent = {
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !importing,
                ) {
                    Icon(HugeIcons.FileImport, null)
                }
            },
        ) {
            Button(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                enabled = !importing,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(HugeIcons.Add01, null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        if (importing) stringResource(R.string.assistant_importer_importing)
                        else stringResource(R.string.prompt_page_st_preset_import)
                    )
                }
            }
        }
    }

    deleting?.let { record ->
        RikkaConfirmDialog(
            show = true,
            title = stringResource(R.string.confirm_delete),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                vm.deleteStPreset(record.preset.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        ) {
            Text(stringResource(R.string.common_delete_confirm_message, record.preset.name))
        }
    }

    records.firstOrNull { it.preset.id == detailId }?.let { record ->
        ModalBottomSheet(onDismissRequest = { detailId = null }) {
            StPresetDetailContent(
                record = record,
                assistants = assistants,
                personaName = personaName,
                userPersona = userPersona,
                onTogglePrompt = { orderIndex, enabled ->
                    vm.setStPromptEnabled(record.preset.id, orderIndex, enabled)
                },
                onMovePrompt = { orderIndex, delta ->
                    vm.moveStPrompt(record.preset.id, orderIndex, delta)
                },
                onResetPreset = { vm.resetStPreset(record.preset.id) },
                onUpdatePrompt = { identifier, name, role, content ->
                    vm.updateStPrompt(record.preset.id, identifier, name, content, role)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StPresetCard(
    record: StPresetRecord,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val preset = record.preset
    val enabledEntries = preset.promptOrder.sumOf { bucket -> bucket.order.count { it.enabled } }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardGroupIcon(
                    icon = HugeIcons.Book02,
                    containerColor = if (record.enabled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (record.enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = preset.name.ifEmpty { stringResource(R.string.prompt_page_unnamed) },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = record.enabled, onCheckedChange = onToggle)
                ItemActionMenu(
                    actions = listOf(
                        ItemAction(
                            text = stringResource(R.string.export_title),
                            icon = HugeIcons.Share03,
                            onClick = onExport,
                        ),
                        ItemAction(
                            text = stringResource(R.string.prompt_page_delete),
                            icon = HugeIcons.Delete01,
                            destructive = true,
                            onClick = onDelete,
                        ),
                    )
                )
            }
            FlowRow(
                modifier = Modifier.padding(start = 52.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tag(type = TagType.INFO) {
                    Text(stringResource(R.string.prompt_page_st_preset_prompt_count, preset.prompts.size))
                }
                Tag(type = TagType.DEFAULT) {
                    Text(stringResource(R.string.prompt_page_st_preset_enabled_count, enabledEntries))
                }
                if (record.hasCustomOrder) {
                    Tag(type = TagType.WARNING) {
                        Text(stringResource(R.string.prompt_page_st_preset_custom_order))
                    }
                } else if (record.isModified) {
                    Tag(type = TagType.WARNING) {
                        Text(stringResource(R.string.prompt_page_st_preset_modified))
                    }
                }
                if (!record.enabled) {
                    Tag(type = TagType.WARNING) {
                        Text(stringResource(R.string.prompt_page_st_preset_disabled))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StPresetDetailContent(
    record: StPresetRecord,
    assistants: List<Assistant>,
    personaName: String,
    userPersona: String,
    onTogglePrompt: (orderIndex: Int, enabled: Boolean) -> Unit,
    onMovePrompt: (orderIndex: Int, delta: Int) -> Unit,
    onResetPreset: () -> Unit,
    onUpdatePrompt: (identifier: String, name: String, role: MessageRole, content: String) -> Unit,
) {
    val preset = record.preset
    val rows = remember(preset) { StPromptAssembler.editablePrompts(preset) }
    val mountedRows = rows.filter { it.orderIndex != null }
    val detachedRows = rows.filter { it.orderIndex == null }
    var showInspect by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<StPromptRow?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = preset.name.ifEmpty { stringResource(R.string.prompt_page_unnamed) },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                R.string.prompt_page_st_preset_summary,
                preset.prompts.size,
                preset.promptOrder.sumOf { it.order.size },
                preset.regexScripts.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.prompt_page_st_preset_copy_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { showInspect = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.prompt_page_st_preset_inspect))
            }
            if (record.isModified) {
                TextButton(onClick = onResetPreset) {
                    Text(stringResource(R.string.prompt_page_st_preset_restore_default))
                }
            }
        }
        Text(
            text = stringResource(R.string.prompt_page_st_preset_inspect_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preset.warnings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.prompt_page_st_preset_warnings),
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            preset.warnings.take(6).forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = stringResource(R.string.prompt_page_st_preset_order),
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(mountedRows, key = { "${it.prompt.identifier}:${it.orderIndex}" }) { row ->
                StPromptEditRow(
                    row = row,
                    canMoveUp = (row.orderIndex ?: 0) > 0,
                    canMoveDown = (row.orderIndex ?: 0) < mountedRows.lastIndex,
                    onToggle = { enabled -> row.orderIndex?.let { onTogglePrompt(it, enabled) } },
                    onMoveUp = { row.orderIndex?.let { onMovePrompt(it, -1) } },
                    onMoveDown = { row.orderIndex?.let { onMovePrompt(it, 1) } },
                    onEdit = { editingRow = row },
                )
            }
            if (detachedRows.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.prompt_page_st_preset_detached),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(detachedRows, key = { "detached:${it.prompt.identifier}" }) { row ->
                    StPromptEditRow(
                        row = row,
                        canMoveUp = false,
                        canMoveDown = false,
                        onToggle = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        onEdit = { editingRow = row },
                    )
                }
            }
            if (preset.regexScripts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.prompt_page_st_preset_regex),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(preset.regexScripts) { script ->
                    Text(
                        text = buildString {
                            append(script.name.ifBlank { script.findRegex.take(40) })
                            append(" · ")
                            append(if (script.isDisplayScript) "display" else "prompt")
                            append(" · placement ")
                            append(script.placement.joinToString(",").ifBlank { "?" })
                            if (script.disabled) append(" · disabled")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showInspect) {
        val boundAssistant = remember(assistants, preset) {
            assistants.firstOrNull { it.stPresetId == preset.id }
        }
        val assembly = remember(preset, boundAssistant, personaName, userPersona) {
            StPromptAssembler.assemble(
                preset = preset,
                history = listOf(
                    UIMessage.user("（预览）你好。"),
                    UIMessage.assistant("（预览）你好，我是${boundAssistant?.characterCard?.name ?: "角色"}。"),
                ),
                character = boundAssistant?.characterCard,
                assistantSystemPrompt = boundAssistant?.systemPrompt.orEmpty(),
                personaName = personaName,
                personaDescription = userPersona,
                modelName = "preview",
            )
        }
        AlertDialog(
            onDismissRequest = { showInspect = false },
            title = { Text(stringResource(R.string.prompt_page_st_preset_inspect)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.prompt_page_st_preset_inspect_summary,
                            assembly.report.renderedPrompts,
                            assembly.messages.size,
                            assembly.report.warnings.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (assembly.messages.isEmpty()) {
                        Text(stringResource(R.string.prompt_page_st_preset_inspect_empty))
                    }
                    assembly.messages.forEachIndexed { index, message ->
                        Text(
                            text = "${index + 1}. ${message.role.name}: ${message.toText().take(300)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    assembly.report.warnings.forEach { warning ->
                        Text(
                            text = "⚠ $warning",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInspect = false }) {
                    Text(stringResource(R.string.prompt_page_st_preset_close))
                }
            },
        )
    }

    editingRow?.let { row ->
        StPromptEditDialog(
            row = row,
            onDismiss = { editingRow = null },
            onSave = { name, role, content ->
                onUpdatePrompt(row.prompt.identifier, name, role, content)
                editingRow = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StPromptEditRow(
    row: StPromptRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
) {
    val prompt = row.prompt
    val protected = prompt.marker && StPresetMarkers.isProtected(prompt.identifier)
    val notApplicable = row.orderIndex == null ||
        (prompt.marker && prompt.identifier !in StPresetParser.KNOWN_MARKERS) ||
        (!prompt.marker && prompt.content.isBlank())
    val status = when {
        notApplicable -> stringResource(R.string.prompt_page_st_preset_status_na)
        protected || row.enabled -> stringResource(R.string.prompt_page_st_preset_status_active)
        else -> stringResource(R.string.prompt_page_st_preset_status_disabled)
    }
    val statusType = when {
        notApplicable -> TagType.WARNING
        protected || row.enabled -> TagType.SUCCESS
        else -> TagType.ERROR
    }
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(14.dp),
        color = if ((row.enabled || protected) && !notApplicable) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${row.orderIndex?.plus(1) ?: "—"}. ${prompt.name.ifBlank { prompt.identifier }}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = HugeIcons.Edit01,
                        contentDescription = stringResource(R.string.prompt_page_st_preset_edit),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Tag(type = TagType.DEFAULT) { Text(prompt.role.name.lowercase()) }
                    if (prompt.marker) {
                        Tag(type = TagType.INFO) { Text(stringResource(R.string.prompt_page_st_preset_marker)) }
                    } else if (prompt.isInChat) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.prompt_page_st_preset_in_chat_depth, prompt.injectionDepth))
                        }
                    } else {
                        Tag(type = TagType.DEFAULT) { Text(stringResource(R.string.prompt_page_st_preset_relative)) }
                    }
                    if (protected) {
                        Tag(type = TagType.INFO) { Text(stringResource(R.string.prompt_page_st_preset_protected)) }
                    }
                    Tag(type = statusType) { Text(status) }
                    Text(
                        text = stringResource(R.string.prompt_page_st_preset_chars, prompt.content.length),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!notApplicable) {
                Switch(
                    checked = protected || row.enabled,
                    onCheckedChange = if (protected) null else onToggle,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(HugeIcons.ArrowUp01, stringResource(R.string.prompt_page_st_preset_move_up))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(HugeIcons.ArrowDown01, stringResource(R.string.prompt_page_st_preset_move_down))
                    }
                }
            }
        }
    }
}

@Composable
private fun StPromptEditDialog(
    row: StPromptRow,
    onDismiss: () -> Unit,
    onSave: (name: String, role: MessageRole, content: String) -> Unit,
) {
    val prompt = row.prompt
    var name by remember(prompt.identifier) { mutableStateOf(prompt.name) }
    var role by remember(prompt.identifier) { mutableStateOf(prompt.role) }
    var content by remember(prompt.identifier) { mutableStateOf(prompt.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prompt_page_st_preset_edit)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.prompt_page_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (prompt.marker) {
                    Text(
                        text = stringResource(R.string.prompt_page_st_preset_marker_readonly),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.prompt_page_injection_role),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Select(
                        options = listOf(MessageRole.SYSTEM, MessageRole.USER, MessageRole.ASSISTANT),
                        selectedOption = role,
                        onOptionSelected = { role = it },
                        optionToString = { it.name.lowercase() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.prompt_page_injection_content)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp),
                        minLines = 8,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), role, content) }) {
                Text(stringResource(R.string.prompt_page_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.prompt_page_cancel))
            }
        },
    )
}
