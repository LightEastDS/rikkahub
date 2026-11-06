package me.rerere.rikkahub.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.db.dao.StPresetDao
import me.rerere.rikkahub.data.db.dao.StVariableDao
import me.rerere.rikkahub.data.db.entity.StPresetEntity
import me.rerere.rikkahub.data.db.entity.StVariableEntity
import me.rerere.rikkahub.data.stpreset.StPreset
import me.rerere.rikkahub.data.stpreset.StPromptAssembler
import me.rerere.rikkahub.data.stpreset.StPresetDocumentEditor
import me.rerere.rikkahub.data.stpreset.StPresetMarkers
import me.rerere.rikkahub.data.stpreset.StPresetParseResult
import me.rerere.rikkahub.data.stpreset.StPresetParser
import me.rerere.rikkahub.data.stpreset.StPromptOrderBucket
import me.rerere.rikkahub.data.stpreset.StPromptOrderEntry
import me.rerere.rikkahub.data.stpreset.StRegexScript
import me.rerere.rikkahub.utils.JsonInstant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "StPresetRepository"

data class StPresetRecord(
    val preset: StPreset,
    val enabled: Boolean,
    val importedAt: Long,
    val sortIndex: Int,
    /** 是否启用自动缓存优化排序；为 false 时使用手动/预设原始排序。 */
    val autoOptimizeOrder: Boolean = preset.autoOptimizeOrder,
    /** 用户改过 prompt_order；为 true 时详情页显示“自定义排序”。 */
    val hasCustomOrder: Boolean = false,
    /** 副本内容或排序与原始 JSON 不同；用于显示“恢复默认”。 */
    val isModified: Boolean = false,
)

@Serializable
data class StCustomOrderConfig(
    val autoOptimizeOrder: Boolean = true,
    val order: List<StPromptOrderEntry> = emptyList(),
)

class StPresetRepository(
    private val dao: StPresetDao,
) {
    private val cache = ConcurrentHashMap<String, StPresetRecord>()

    val records: Flow<List<StPresetRecord>> = dao.observeAll().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { entity.toRecord() }
                .onFailure { Log.e(TAG, "Failed to parse preset ${entity.id}", it) }
                .getOrNull()
        }
    }.flowOn(Dispatchers.Default)

    suspend fun getRecord(id: Uuid): StPresetRecord? {
        val key = id.toString()
        cache[key]?.let { return it }
        val entity = dao.getById(key) ?: return null
        val record = runCatching { entity.toRecord() }
            .onFailure { Log.e(TAG, "Failed to parse preset $key", it) }
            .getOrNull() ?: return null
        cache[key] = record
        return record
    }

    suspend fun get(id: Uuid): StPreset? = getRecord(id)?.preset

    /** 导入并保存；解析失败抛 [me.rerere.rikkahub.data.stpreset.StPresetParseException]。 */
    suspend fun import(rawJson: String, fallbackName: String): StPresetParseResult {
        val id = Uuid.random()
        val result = StPresetParser.parse(rawJson = rawJson, fallbackName = fallbackName, id = id)
        val entity = StPresetEntity(
            id = id.toString(),
            name = result.preset.name,
            rawJson = rawJson,
            originalJson = rawJson,
            promptCount = result.preset.prompts.size,
            importedAt = System.currentTimeMillis(),
            enabled = true,
            sortIndex = dao.maxSortIndex() + 1,
        )
        dao.upsert(entity)
        cache[id.toString()] = entity.toRecord(result.preset)
        return result
    }

    suspend fun setEnabled(id: Uuid, enabled: Boolean) {
        dao.setEnabled(id.toString(), enabled)
        cache.remove(id.toString())
    }

    suspend fun delete(id: Uuid) {
        dao.deleteById(id.toString())
        cache.remove(id.toString())
    }

    /** 启停 prompt_order 里的一条；结构性 marker 不允许停用。 */
    suspend fun setPromptEnabled(id: Uuid, orderIndex: Int, enabled: Boolean) {
        val order = currentOrder(id) ?: return
        if (orderIndex !in order.indices) return
        if (StPresetMarkers.isProtected(order[orderIndex].identifier)) return
        val updated = order.toMutableList().also {
            it[orderIndex] = it[orderIndex].copy(enabled = enabled)
        }
        val record = getRecord(id)
        saveOrder(id, updated, autoOptimizeOrder = record?.autoOptimizeOrder ?: true)
    }

    /** 上移/下移一条 prompt；[delta] 为 -1 或 +1。手动调整顺序会自动关闭自动优化排序。 */
    suspend fun movePrompt(id: Uuid, orderIndex: Int, delta: Int) {
        reorderPrompt(id, orderIndex, orderIndex + delta)
    }

    /** 拖拽移动可见列表中的条目，并切换为手动排序模式。 */
    suspend fun reorderPrompt(id: Uuid, fromIndex: Int, toIndex: Int) {
        val order = currentOrder(id) ?: return
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) return
        val updated = order.toMutableList().also {
            val item = it.removeAt(fromIndex)
            it.add(toIndex, item)
        }
        saveOrder(id, updated, autoOptimizeOrder = false)
    }

    /** 切换是否启用自动缓存优化排序。 */
    suspend fun setAutoOptimizeOrder(id: Uuid, autoOptimize: Boolean) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val record = getRecord(id) ?: return
        val current = currentOrder(id) ?: return
        if (autoOptimize) {
            val optimized = StPromptAssembler.optimizeOrder(record.preset.prompts, current)
            saveOrder(id, optimized, autoOptimizeOrder = true)
        } else {
            val enabledById = current.associate { it.identifier to it.enabled }
            val rawPreset = runCatching {
                StPresetParser.parse(
                    rawJson = entity.rawJson,
                    fallbackName = entity.name,
                    id = id,
                    autoOptimizeOrder = false,
                ).preset
            }.getOrNull()
            val rawOrder = rawPreset?.promptOrder
                ?.firstOrNull { it.characterId == StPromptOrderBucket.GLOBAL_CHARACTER_ID }
                ?.order
                ?: rawPreset?.promptOrder?.firstOrNull()?.order
                ?: current
            val restoredOrder = rawOrder.map { entry ->
                val enabled = enabledById[entry.identifier] ?: entry.enabled
                entry.copy(enabled = if (StPresetMarkers.isProtected(entry.identifier)) true else enabled)
            }
            saveOrder(id, restoredOrder, autoOptimizeOrder = false)
        }
    }

    /** 对指定预设的生效 prompt_order 执行 DeepSeek 极速前缀缓存友好的自动拓扑排序 */
    suspend fun optimizeOrder(id: Uuid) {
        val record = getRecord(id) ?: return
        val order = currentOrder(id) ?: return
        val optimized = StPromptAssembler.optimizeOrder(record.preset.prompts, order)
        saveOrder(id, optimized, autoOptimizeOrder = true)
    }

    /** 当前生效的 prompt_order；没有自定义时取预设原始桶。 */
    private suspend fun currentOrder(id: Uuid): List<StPromptOrderEntry>? {
        val record = getRecord(id) ?: return null
        return record.preset.promptOrder
            .firstOrNull { it.characterId == StPromptOrderBucket.GLOBAL_CHARACTER_ID }
            ?.order
            ?: record.preset.promptOrder.firstOrNull()?.order
            ?: record.preset.prompts.map { StPromptOrderEntry(it.identifier, true) }
    }

    private suspend fun saveOrder(
        id: Uuid,
        order: List<StPromptOrderEntry>,
        autoOptimizeOrder: Boolean = true,
    ) {
        val config = StCustomOrderConfig(
            autoOptimizeOrder = autoOptimizeOrder,
            order = order,
        )
        dao.setOrderJson(
            id = id.toString(),
            orderJson = JsonInstant.encodeToString(config),
        )
        cache.remove(id.toString())
    }

    /** 修改预设副本的名称与描述。 */
    suspend fun updateMetadata(
        id: Uuid,
        name: String,
        description: String,
    ) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val updatedRaw = StPresetDocumentEditor.updateMetadata(
            rawJson = entity.rawJson,
            name = name,
            description = description,
        )
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 修改预设副本里的单条提示正文/名称/角色。 */
    suspend fun updatePrompt(
        id: Uuid,
        identifier: String,
        name: String,
        content: String,
        role: MessageRole,
    ) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val updatedRaw = StPresetDocumentEditor.updatePrompt(
            rawJson = entity.rawJson,
            identifier = identifier,
            name = name,
            content = content,
            role = role,
        )
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 新增一条提示条目到预设副本中。 */
    suspend fun addPrompt(
        id: Uuid,
        name: String,
        content: String,
        role: MessageRole,
    ) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val newIdentifier = "custom_${java.util.UUID.randomUUID().toString().replace("-", "").take(8)}"
        val updatedRaw = StPresetDocumentEditor.addPrompt(
            rawJson = entity.rawJson,
            name = name,
            content = content,
            role = role,
            identifier = newIdentifier,
        )
        if (entity.orderJson.isNotBlank()) {
            val config = decodeOrderConfig(entity.orderJson)
            if (config.order.isNotEmpty()) {
                val updatedOrder = config.order + StPromptOrderEntry(newIdentifier, enabled = true)
                saveOrder(id, updatedOrder, autoOptimizeOrder = config.autoOptimizeOrder)
            }
        }
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 删除预设副本里的指定条目（非受保护核心 marker）。 */
    suspend fun deletePrompt(
        id: Uuid,
        identifier: String,
    ) {
        if (StPresetMarkers.isProtected(identifier)) return
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val updatedRaw = StPresetDocumentEditor.deletePrompt(
            rawJson = entity.rawJson,
            identifier = identifier,
        )
        if (entity.orderJson.isNotBlank()) {
            val config = decodeOrderConfig(entity.orderJson)
            if (config.order.isNotEmpty()) {
                val updatedOrder = config.order.filter { it.identifier != identifier }
                saveOrder(id, updatedOrder, autoOptimizeOrder = config.autoOptimizeOrder)
            }
        }
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 新增正则脚本。 */
    suspend fun addRegexScript(
        id: Uuid,
        script: StRegexScript,
    ) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val updatedRaw = StPresetDocumentEditor.addRegexScript(
            rawJson = entity.rawJson,
            script = script,
        )
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 修改正则脚本。 */
    suspend fun updateRegexScript(
        id: Uuid,
        script: StRegexScript,
    ) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val updatedRaw = StPresetDocumentEditor.updateRegexScript(
            rawJson = entity.rawJson,
            script = script,
        )
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 删除正则脚本。 */
    suspend fun deleteRegexScript(
        id: Uuid,
        scriptId: String,
    ) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val updatedRaw = StPresetDocumentEditor.deleteRegexScript(
            rawJson = entity.rawJson,
            scriptId = scriptId,
        )
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 启停正则脚本。 */
    suspend fun toggleRegexScript(
        id: Uuid,
        scriptId: String,
        disabled: Boolean,
    ) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        val updatedRaw = StPresetDocumentEditor.toggleRegexScript(
            rawJson = entity.rawJson,
            scriptId = scriptId,
            disabled = disabled,
        )
        if (updatedRaw != entity.rawJson) {
            dao.updateRawJson(key, updatedRaw)
            cache.remove(key)
        }
    }

    /** 恢复默认：内容回到导入时的原始 JSON，排序回到原始 prompt_order。 */
    suspend fun resetToDefault(id: Uuid) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        dao.resetToOriginal(key, entity.originalJson.ifBlank { entity.rawJson })
        cache.remove(key)
    }

    suspend fun rawJson(id: Uuid): String? = dao.getById(id.toString())?.rawJson

    private fun StPresetEntity.toRecord(): StPresetRecord {
        val config = decodeOrderConfig(orderJson)
        val parsed = StPresetParser.parse(
            rawJson = rawJson,
            fallbackName = name,
            id = Uuid.parse(id),
            autoOptimizeOrder = config.autoOptimizeOrder,
        ).preset
        return toRecord(parsed.withCustomOrder(config).withProtectedMarkersEnabled(), config)
    }

    private fun StPresetEntity.toRecord(
        preset: StPreset,
        config: StCustomOrderConfig = decodeOrderConfig(orderJson),
    ): StPresetRecord {
        val normalizedPreset = preset.copy(autoOptimizeOrder = config.autoOptimizeOrder).withProtectedMarkersEnabled()
        return StPresetRecord(
            preset = normalizedPreset,
            enabled = enabled,
            importedAt = importedAt,
            sortIndex = sortIndex,
            autoOptimizeOrder = config.autoOptimizeOrder,
            hasCustomOrder = config.order.isNotEmpty() || !config.autoOptimizeOrder,
            isModified = orderJson.isNotBlank() || rawJson != originalJson.ifBlank { rawJson },
        )
    }

    private fun StPreset.withCustomOrder(config: StCustomOrderConfig): StPreset {
        val updated = if (config.order.isNotEmpty()) {
            copy(
                promptOrder = listOf(
                    StPromptOrderBucket(
                        characterId = StPromptOrderBucket.GLOBAL_CHARACTER_ID,
                        order = config.order,
                    )
                )
            )
        } else {
            this
        }
        return updated.copy(autoOptimizeOrder = config.autoOptimizeOrder)
    }

    private fun decodeOrderConfig(orderJson: String): StCustomOrderConfig {
        val trimmed = orderJson.trim()
        if (trimmed.isEmpty()) return StCustomOrderConfig()
        if (trimmed.startsWith("{")) {
            runCatching {
                return JsonInstant.decodeFromString<StCustomOrderConfig>(trimmed)
            }
        }
        if (trimmed.startsWith("[")) {
            val legacyOrder = runCatching {
                JsonInstant.decodeFromString<List<StPromptOrderEntry>>(trimmed)
            }.getOrNull()
            if (legacyOrder != null) {
                return StCustomOrderConfig(
                    autoOptimizeOrder = false,
                    order = legacyOrder,
                )
            }
        }
        return StCustomOrderConfig()
    }

    /** 结构性 marker 即使原预设里被停用，也强制启用；兜底逻辑仍然保留在组装器里。 */
    private fun StPreset.withProtectedMarkersEnabled(): StPreset = copy(
        promptOrder = promptOrder.map { bucket ->
            bucket.copy(
                order = bucket.order.map { entry ->
                    if (StPresetMarkers.isProtected(entry.identifier)) entry.copy(enabled = true) else entry
                }
            )
        }
    )
}

class StVariableRepository(
    private val dao: StVariableDao,
) {
    suspend fun load(scopeId: String): MutableMap<String, String> {
        val map = linkedMapOf<String, String>()
        dao.getScope(scopeId).forEach { map[it.name] = it.value }
        return map
    }

    suspend fun save(scopeId: String, variables: Map<String, String>) {
        dao.deleteScope(scopeId)
        if (variables.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertAll(
            variables.map { (name, value) ->
                StVariableEntity(scopeId = scopeId, name = name, value = value, updatedAt = now)
            }
        )
    }

    suspend fun loadGlobal(): MutableMap<String, String> = load(StVariableEntity.GLOBAL_SCOPE)

    suspend fun saveGlobal(variables: Map<String, String>) = save(StVariableEntity.GLOBAL_SCOPE, variables)
}
