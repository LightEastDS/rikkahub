package me.rerere.rikkahub.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.db.dao.StPresetDao
import me.rerere.rikkahub.data.db.dao.StVariableDao
import me.rerere.rikkahub.data.db.entity.StPresetEntity
import me.rerere.rikkahub.data.db.entity.StVariableEntity
import me.rerere.rikkahub.data.stpreset.StPreset
import me.rerere.rikkahub.data.stpreset.StPresetDocumentEditor
import me.rerere.rikkahub.data.stpreset.StPresetMarkers
import me.rerere.rikkahub.data.stpreset.StPresetParseResult
import me.rerere.rikkahub.data.stpreset.StPresetParser
import me.rerere.rikkahub.data.stpreset.StPromptOrderBucket
import me.rerere.rikkahub.data.stpreset.StPromptOrderEntry
import me.rerere.rikkahub.utils.JsonInstant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "StPresetRepository"

data class StPresetRecord(
    val preset: StPreset,
    val enabled: Boolean,
    val importedAt: Long,
    val sortIndex: Int,
    /** 用户改过 prompt_order；为 true 时详情页显示“自定义排序”。 */
    val hasCustomOrder: Boolean = false,
    /** 副本内容或排序与原始 JSON 不同；用于显示“恢复默认”。 */
    val isModified: Boolean = false,
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
        saveOrder(id, updated)
    }

    /** 上移/下移一条 prompt；[delta] 为 -1 或 +1。 */
    suspend fun movePrompt(id: Uuid, orderIndex: Int, delta: Int) {
        val order = currentOrder(id) ?: return
        val target = orderIndex + delta
        if (orderIndex !in order.indices || target !in order.indices) return
        val updated = order.toMutableList().also {
            val item = it.removeAt(orderIndex)
            it.add(target, item)
        }
        saveOrder(id, updated)
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

    private suspend fun saveOrder(id: Uuid, order: List<StPromptOrderEntry>) {
        dao.setOrderJson(
            id = id.toString(),
            orderJson = JsonInstant.encodeToString(order),
        )
        cache.remove(id.toString())
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

    /** 恢复默认：内容回到导入时的原始 JSON，排序回到原始 prompt_order。 */
    suspend fun resetToDefault(id: Uuid) {
        val key = id.toString()
        val entity = dao.getById(key) ?: return
        dao.resetToOriginal(key, entity.originalJson.ifBlank { entity.rawJson })
        cache.remove(key)
    }

    suspend fun rawJson(id: Uuid): String? = dao.getById(id.toString())?.rawJson

    private fun StPresetEntity.toRecord(): StPresetRecord {
        val parsed = (cache[id]?.preset) ?: StPresetParser.parse(
            rawJson = rawJson,
            fallbackName = name,
            id = Uuid.parse(id),
        ).preset
        return toRecord(parsed.withCustomOrder(orderJson).withProtectedMarkersEnabled())
    }

    private fun StPresetEntity.toRecord(preset: StPreset): StPresetRecord = StPresetRecord(
        preset = preset.withProtectedMarkersEnabled(),
        enabled = enabled,
        importedAt = importedAt,
        sortIndex = sortIndex,
        hasCustomOrder = orderJson.isNotBlank(),
        isModified = orderJson.isNotBlank() || rawJson != originalJson.ifBlank { rawJson },
    )

    private fun StPreset.withCustomOrder(orderJson: String): StPreset {
        if (orderJson.isBlank()) return this
        val order = runCatching {
            JsonInstant.decodeFromString<List<StPromptOrderEntry>>(orderJson)
        }.getOrNull() ?: return this
        return copy(
            promptOrder = listOf(
                StPromptOrderBucket(
                    characterId = StPromptOrderBucket.GLOBAL_CHARACTER_ID,
                    order = order,
                )
            )
        )
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
