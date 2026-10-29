package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.CharacterCard
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.stpreset.StPresetParser
import me.rerere.rikkahub.data.stpreset.TavernWorldBookParser
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject

@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    onUpdate: (Assistant) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SillyTavernImporter(onImport = onUpdate)
    }
}

@Composable
private fun SillyTavernImporter(
    onImport: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var isLoading by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<TavernCardImportResult?>(null) }

    fun handleImportResult(result: TavernCardImportResult) {
        val lorebook = result.lorebook
        if (lorebook != null && lorebook.entries.isNotEmpty()) {
            pendingImport = result
        } else {
            onImport(result.assistant)
        }
    }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    val result = importAssistantFromUri(
                        context = context,
                        uri = uri,
                        filesManager = filesManager,
                    )
                    handleImportResult(result)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    toaster.show(
                        message = exception.message ?: context.getString(R.string.assistant_importer_import_failed),
                        type = ToastType.Error
                    )
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val pngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    val result = importAssistantFromUri(
                        context = context,
                        uri = uri,
                        filesManager = filesManager,
                    )
                    handleImportResult(result)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    toaster.show(
                        message = exception.message ?: context.getString(R.string.assistant_importer_import_failed),
                        type = ToastType.Error
                    )
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                pngPickerLauncher.launch(arrayOf("image/png"))
            },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_png))
        }

        OutlinedButton(
            onClick = {
                jsonPickerLauncher.launch(arrayOf("application/json"))
            },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_json))
        }
    }

    if (pendingImport != null) {
        val pending = pendingImport!!
        val lorebook = pending.lorebook
        if (lorebook != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingImport = null
                },
                title = {
                    Text(text = stringResource(R.string.assistant_importer_worldbook_dialog_title))
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.assistant_importer_worldbook_dialog_message,
                            lorebook.name,
                            lorebook.entries.size
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val toImport = pending
                            val finalLorebook = lorebook
                            pendingImport = null
                            scope.launch {
                                try {
                                    settingsStore.update { current ->
                                        current.copy(lorebooks = current.lorebooks + finalLorebook)
                                    }
                                    val finalAssistant = toImport.assistant.copy(
                                        lorebookIds = toImport.assistant.lorebookIds + finalLorebook.id,
                                        characterCard = toImport.assistant.characterCard?.copy(characterBook = null)
                                    )
                                    onImport(finalAssistant)
                                    toaster.show(
                                        context.getString(
                                            R.string.assistant_importer_worldbook_imported_toast,
                                            finalLorebook.name,
                                            finalLorebook.entries.size
                                        )
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    toaster.show(
                                        message = e.message ?: context.getString(R.string.assistant_importer_import_failed),
                                        type = ToastType.Error
                                    )
                                }
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.assistant_importer_worldbook_dialog_confirm))
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                pendingImport = null
                            }
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                val toImport = pending
                                pendingImport = null
                                val finalAssistant = toImport.assistant.copy(
                                    lorebookIds = emptySet(),
                                    characterCard = toImport.assistant.characterCard?.copy(characterBook = null)
                                )
                                onImport(finalAssistant)
                            }
                        ) {
                            Text(text = stringResource(R.string.assistant_importer_worldbook_dialog_skip))
                        }
                    }
                }
            )
        }
    }
}

// region Parsing Strategy

data class TavernCardImportResult(
    val assistant: Assistant,
    val lorebook: Lorebook? = null,
)

private interface TavernCardParser {
    val specName: String
    fun parse(context: Context, json: JsonObject, background: String?): TavernCardImportResult
}

private class CharaCardV2Parser : TavernCardParser {
    override val specName: String = "chara_card_v2"

    override fun parse(context: Context, json: JsonObject, background: String?): TavernCardImportResult =
        parseTavernCard(context, json, background)
}

private class CharaCardV3Parser : TavernCardParser {
    override val specName: String = "chara_card_v3"

    override fun parse(context: Context, json: JsonObject, background: String?): TavernCardImportResult =
        parseTavernCard(context, json, background)
}

/**
 * V2/V3 卡片共用解析：结构化字段进 [CharacterCard]，旧 systemPrompt 继续拼接，
 * 保证没有绑定 ST 预设的助手行为不变。
 */
private fun parseTavernCard(
    context: Context,
    json: JsonObject,
    background: String?
): TavernCardImportResult {
    val data = json["data"]?.jsonObject
        ?: error(context.getString(R.string.assistant_importer_missing_data_field))
    val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: error(context.getString(R.string.assistant_importer_missing_name_field))
    val description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val system = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val mesExample = data["mes_example"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val postHistory = data["post_history_instructions"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val creatorNotes = data["creator_notes"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val alternateGreetings = (data["alternate_greetings"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()

    val rawBookObj = (data["character_book"] as? JsonObject) ?: (json["character_book"] as? JsonObject)
    val characterBook = StPresetParser.parseCharacterBook(rawBookObj)
    val defaultWorldBookName = context.getString(R.string.assistant_importer_worldbook_default_name, name)
    val parsedLorebook = rawBookObj?.let { TavernWorldBookParser.parse(it, defaultWorldBookName) }

    val card = CharacterCard(
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMessage = firstMessage,
        alternateGreetings = alternateGreetings,
        mesExample = mesExample,
        systemPrompt = system,
        postHistoryInstructions = postHistory,
        creatorNotes = creatorNotes,
        characterBook = characterBook,
    )

    val prompt = buildString {
        appendLine("You are roleplaying as $name.")
        appendLine()
        if (system.isNotBlank()) {
            appendLine(system)
            appendLine()
        }
        appendLine("## Description of the character")
        appendLine(description.ifBlank { "Empty" })
        appendLine()
        appendLine("## Personality of the character")
        appendLine(personality.ifBlank { "Empty" })
        appendLine()
        appendLine("## Scenario")
        append(scenario.ifBlank { "Empty" })
    }

    val assistant = Assistant(
        name = name,
        presetMessages = if (firstMessage.isNotBlank()) listOf(UIMessage.assistant(firstMessage)) else emptyList(),
        systemPrompt = prompt,
        characterCard = card,
        background = background,
    )

    return TavernCardImportResult(
        assistant = assistant,
        lorebook = parsedLorebook,
    )
}

private val TAVERN_PARSERS: Map<String, TavernCardParser> = listOf(
    CharaCardV2Parser(),
    CharaCardV3Parser()
).associateBy { it.specName }

private fun parseAssistantFromJson(
    context: Context,
    json: JsonObject,
    background: String?,
): TavernCardImportResult {
    val spec = json["spec"]?.jsonPrimitive?.contentOrNull
        ?: error(context.getString(R.string.assistant_importer_missing_spec_field))
    val parser = TAVERN_PARSERS[spec] ?: error(context.getString(R.string.assistant_importer_unsupported_spec, spec))
    return parser.parse(context = context, json = json, background = background)
}

// endregion

private suspend fun importAssistantFromUri(
    context: Context,
    uri: Uri,
    filesManager: FilesManager,
): TavernCardImportResult {
    val mime = withContext(Dispatchers.IO) { filesManager.getFileMimeType(uri) }
    val (jsonString, backgroundStr) = withContext(Dispatchers.IO) {
        when (mime) {
            "image/png" -> {
                val result = ImageUtils.getTavernCharacterMeta(context, uri)
                result.map { base64Data ->
                    val json = String(Base64.decode(base64Data, Base64.DEFAULT))
                    val bg = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
                    json to bg
                }.getOrElse { throw it }
            }

            "application/json" -> {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                    .use { it?.readText() }
                    ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                json to null
            }

            else -> error(context.getString(R.string.assistant_importer_unsupported_file_type, mime ?: "unknown"))
        }
    }
    val json = Json.parseToJsonElement(jsonString).jsonObject
    return parseAssistantFromJson(context = context, json = json, background = backgroundStr)
}
