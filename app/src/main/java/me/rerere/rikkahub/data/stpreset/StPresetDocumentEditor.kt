package me.rerere.rikkahub.data.stpreset

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.utils.JsonInstant
import java.util.UUID

/**
 * 编辑“预设副本”的 JSON 文档。
 *
 * 保留原预设的所有未知字段；原始 JSON 仍单独保存在 original_json，用于恢复默认。
 */
object StPresetDocumentEditor {
    fun updatePrompt(
        rawJson: String,
        identifier: String,
        name: String,
        content: String,
        role: MessageRole,
    ): String {
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val prompts = root["prompts"]?.jsonArray ?: return rawJson
        var changed = false
        val newPrompts = prompts.map { element ->
            val obj = element as? JsonObject ?: return@map element
            val currentIdentifier = obj["identifier"]?.jsonPrimitive?.contentOrNull
            if (currentIdentifier != identifier) return@map element
            changed = true
            JsonObject(
                obj.toMutableMap().apply {
                    put("name", JsonPrimitive(name))
                    put("content", JsonPrimitive(content))
                    put("role", JsonPrimitive(role.name.lowercase()))
                }
            )
        }
        if (!changed) return rawJson
        val newRoot = JsonObject(
            root.toMutableMap().apply {
                put("prompts", JsonArray(newPrompts))
            }
        )
        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), newRoot)
        }.getOrDefault(rawJson)
    }

    fun addPrompt(
        rawJson: String,
        name: String,
        content: String,
        role: MessageRole,
        identifier: String = "custom_${UUID.randomUUID().toString().replace("-", "").take(8)}",
    ): String {
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val prompts = (root["prompts"]?.jsonArray ?: JsonArray(emptyList())).toMutableList()
        val newPromptObj = JsonObject(
            mapOf(
                "identifier" to JsonPrimitive(identifier),
                "name" to JsonPrimitive(name),
                "role" to JsonPrimitive(role.name.lowercase()),
                "content" to JsonPrimitive(content),
                "system_prompt" to JsonPrimitive(false),
                "marker" to JsonPrimitive(false),
            )
        )
        prompts.add(newPromptObj)

        val newRootMap = root.toMutableMap()
        newRootMap["prompts"] = JsonArray(prompts)

        val promptOrderElement = root["prompt_order"]
        if (promptOrderElement is JsonArray) {
            val orderArray = promptOrderElement.jsonArray
            val isBuckets = orderArray.any { (it as? JsonObject)?.get("order") is JsonArray }
            if (isBuckets) {
                var added = false
                val newBuckets = orderArray.map { bucketElement ->
                    val bucketObj = bucketElement as? JsonObject ?: return@map bucketElement
                    val charId = bucketObj["character_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    if (charId == StPromptOrderBucket.GLOBAL_CHARACTER_ID || (!added && bucketElement == orderArray.first())) {
                        added = true
                        val existingOrder = (bucketObj["order"]?.jsonArray ?: JsonArray(emptyList())).toMutableList()
                        existingOrder.add(
                            JsonObject(
                                mapOf(
                                    "identifier" to JsonPrimitive(identifier),
                                    "enabled" to JsonPrimitive(true),
                                )
                            )
                        )
                        JsonObject(bucketObj.toMutableMap().apply { put("order", JsonArray(existingOrder)) })
                    } else {
                        bucketElement
                    }
                }
                newRootMap["prompt_order"] = JsonArray(newBuckets)
            } else {
                val newOrder = orderArray.toMutableList()
                newOrder.add(
                    JsonObject(
                        mapOf(
                            "identifier" to JsonPrimitive(identifier),
                            "enabled" to JsonPrimitive(true),
                        )
                    )
                )
                newRootMap["prompt_order"] = JsonArray(newOrder)
            }
        } else {
            newRootMap["prompt_order"] = JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "character_id" to JsonPrimitive(StPromptOrderBucket.GLOBAL_CHARACTER_ID),
                            "order" to JsonArray(
                                listOf(
                                    JsonObject(
                                        mapOf(
                                            "identifier" to JsonPrimitive(identifier),
                                            "enabled" to JsonPrimitive(true),
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }

        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), JsonObject(newRootMap))
        }.getOrDefault(rawJson)
    }

    fun deletePrompt(
        rawJson: String,
        identifier: String,
    ): String {
        if (StPresetMarkers.isProtected(identifier)) return rawJson
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val prompts = root["prompts"]?.jsonArray ?: return rawJson
        val newPrompts = prompts.filter { element ->
            val obj = element as? JsonObject ?: return@filter true
            obj["identifier"]?.jsonPrimitive?.contentOrNull != identifier
        }

        val newRootMap = root.toMutableMap()
        newRootMap["prompts"] = JsonArray(newPrompts)

        val promptOrderElement = root["prompt_order"]
        if (promptOrderElement is JsonArray) {
            val orderArray = promptOrderElement.jsonArray
            val isBuckets = orderArray.any { (it as? JsonObject)?.get("order") is JsonArray }
            if (isBuckets) {
                val newBuckets = orderArray.map { bucketElement ->
                    val bucketObj = bucketElement as? JsonObject ?: return@map bucketElement
                    val existingOrder = bucketObj["order"]?.jsonArray ?: return@map bucketElement
                    val filteredOrder = existingOrder.filter { entryElement ->
                        val entryObj = entryElement as? JsonObject ?: return@filter true
                        entryObj["identifier"]?.jsonPrimitive?.contentOrNull != identifier
                    }
                    JsonObject(bucketObj.toMutableMap().apply { put("order", JsonArray(filteredOrder)) })
                }
                newRootMap["prompt_order"] = JsonArray(newBuckets)
            } else {
                val filteredOrder = orderArray.filter { entryElement ->
                    val entryObj = entryElement as? JsonObject ?: return@filter true
                    entryObj["identifier"]?.jsonPrimitive?.contentOrNull != identifier
                }
                newRootMap["prompt_order"] = JsonArray(filteredOrder)
            }
        }

        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), JsonObject(newRootMap))
        }.getOrDefault(rawJson)
    }

    private fun StRegexScript.toJsonObject(): JsonObject {
        val map = mutableMapOf<String, JsonElement>(
            "id" to JsonPrimitive(id.ifBlank { UUID.randomUUID().toString() }),
            "scriptName" to JsonPrimitive(name),
            "findRegex" to JsonPrimitive(findRegex),
            "replaceString" to JsonPrimitive(replaceString),
            "trimStrings" to JsonArray(trimStrings.map { JsonPrimitive(it) }),
            "placement" to JsonArray(placement.map { JsonPrimitive(it) }),
            "disabled" to JsonPrimitive(disabled),
            "markdownOnly" to JsonPrimitive(markdownOnly),
            "promptOnly" to JsonPrimitive(promptOnly),
            "runOnEdit" to JsonPrimitive(runOnEdit),
            "substituteRegex" to JsonPrimitive(if (substituteRegex) 1 else 0),
        )
        minDepth?.let { map["minDepth"] = JsonPrimitive(it) }
        maxDepth?.let { map["maxDepth"] = JsonPrimitive(it) }
        return JsonObject(map)
    }

    private fun getRegexScriptsArray(root: JsonObject): Pair<Boolean, JsonArray> {
        val extensions = root["extensions"] as? JsonObject
        if (extensions != null && extensions["regex_scripts"] is JsonArray) {
            return true to extensions["regex_scripts"]!!.jsonArray
        }
        if (root["regex_scripts"] is JsonArray) {
            return false to root["regex_scripts"]!!.jsonArray
        }
        return true to JsonArray(emptyList())
    }

    private fun withNewRegexScripts(root: JsonObject, inExtensions: Boolean, newScripts: List<JsonElement>): JsonObject {
        val rootMap = root.toMutableMap()
        if (inExtensions) {
            val extensions = (root["extensions"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            extensions["regex_scripts"] = JsonArray(newScripts)
            rootMap["extensions"] = JsonObject(extensions)
        } else {
            rootMap["regex_scripts"] = JsonArray(newScripts)
        }
        return JsonObject(rootMap)
    }

    fun addRegexScript(rawJson: String, script: StRegexScript): String {
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val (inExtensions, scripts) = getRegexScriptsArray(root)
        val existing = scripts.toMutableList()
        existing.add(script.toJsonObject())
        val newRoot = withNewRegexScripts(root, inExtensions, existing)
        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), newRoot)
        }.getOrDefault(rawJson)
    }

    fun updateRegexScript(rawJson: String, script: StRegexScript): String {
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val (inExtensions, scripts) = getRegexScriptsArray(root)
        var changed = false
        val newScripts = scripts.map { element ->
            val obj = element as? JsonObject ?: return@map element
            val currentId = obj["id"]?.jsonPrimitive?.contentOrNull
            if (currentId == script.id || (currentId == null && obj["scriptName"]?.jsonPrimitive?.contentOrNull == script.name)) {
                changed = true
                script.toJsonObject()
            } else {
                element
            }
        }
        if (!changed) return rawJson
        val newRoot = withNewRegexScripts(root, inExtensions, newScripts)
        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), newRoot)
        }.getOrDefault(rawJson)
    }

    fun deleteRegexScript(rawJson: String, scriptId: String): String {
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val (inExtensions, scripts) = getRegexScriptsArray(root)
        val newScripts = scripts.filter { element ->
            val obj = element as? JsonObject ?: return@filter true
            obj["id"]?.jsonPrimitive?.contentOrNull != scriptId
        }
        val newRoot = withNewRegexScripts(root, inExtensions, newScripts)
        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), newRoot)
        }.getOrDefault(rawJson)
    }

    fun toggleRegexScript(rawJson: String, scriptId: String, disabled: Boolean): String {
        val root = runCatching { JsonInstant.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return rawJson
        val (inExtensions, scripts) = getRegexScriptsArray(root)
        var changed = false
        val newScripts = scripts.map { element ->
            val obj = element as? JsonObject ?: return@map element
            val currentId = obj["id"]?.jsonPrimitive?.contentOrNull
            if (currentId == scriptId) {
                changed = true
                JsonObject(obj.toMutableMap().apply { put("disabled", JsonPrimitive(disabled)) })
            } else {
                element
            }
        }
        if (!changed) return rawJson
        val newRoot = withNewRegexScripts(root, inExtensions, newScripts)
        return runCatching {
            JsonInstant.encodeToString(JsonObject.serializer(), newRoot)
        }.getOrDefault(rawJson)
    }
}
