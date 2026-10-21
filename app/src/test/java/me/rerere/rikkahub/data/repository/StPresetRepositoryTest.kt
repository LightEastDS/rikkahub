package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.db.dao.StPresetDao
import me.rerere.rikkahub.data.db.entity.StPresetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class StPresetRepositoryTest {
    private class FakeDao : StPresetDao {
        var entity: StPresetEntity? = null
        private val flow = MutableStateFlow<List<StPresetEntity>>(emptyList())

        override fun observeAll(): Flow<List<StPresetEntity>> = flow
        override suspend fun getById(id: String): StPresetEntity? = entity?.takeIf { it.id == id }
        override suspend fun maxSortIndex(): Int = 0
        override suspend fun upsert(preset: StPresetEntity) {
            entity = preset
            flow.value = listOf(preset)
        }

        override suspend fun setEnabled(id: String, enabled: Boolean) {
            entity = entity?.copy(enabled = enabled)
            flow.value = listOfNotNull(entity)
        }

        override suspend fun setOrderJson(id: String, orderJson: String) {
            entity = entity?.copy(orderJson = orderJson)
            flow.value = listOfNotNull(entity)
        }

        override suspend fun updateRawJson(id: String, rawJson: String) {
            entity = entity?.copy(rawJson = rawJson)
            flow.value = listOfNotNull(entity)
        }

        override suspend fun resetToOriginal(id: String, rawJson: String) {
            entity = entity?.copy(rawJson = rawJson, orderJson = "")
            flow.value = listOfNotNull(entity)
        }

        override suspend fun deleteById(id: String) {
            entity = null
            flow.value = emptyList()
        }
    }

    private val json = """
        {
          "name": "Edit test",
          "prompts": [
            {"identifier": "a", "name": "A", "role": "system", "content": "A"},
            {"identifier": "b", "name": "B", "role": "system", "content": "B"},
            {"identifier": "c", "name": "C", "role": "system", "content": "C"}
          ],
          "prompt_order": [{"character_id": 100001, "order": [
            {"identifier": "a", "enabled": true},
            {"identifier": "b", "enabled": true},
            {"identifier": "c", "enabled": true}
          ]}]
        }
    """.trimIndent()

    @Test
    fun `toggle move and reset prompt order`() = runTest {
        val dao = FakeDao()
        val repository = StPresetRepository(dao)
        val imported = repository.import(json, "test")
        val id: Uuid = imported.preset.id

        repository.setPromptEnabled(id, 1, false)
        var record = repository.getRecord(id)!!
        assertTrue(record.hasCustomOrder)
        assertFalse(record.preset.promptOrder.single().order[1].enabled)

        repository.movePrompt(id, 2, -1)
        record = repository.getRecord(id)!!
        assertEquals(listOf("a", "c", "b"), record.preset.promptOrder.single().order.map { it.identifier })

        repository.resetToDefault(id)
        record = repository.getRecord(id)!!
        assertFalse(record.hasCustomOrder)
        assertTrue(record.preset.promptOrder.single().order.all { it.enabled })
        assertEquals(listOf("a", "b", "c"), record.preset.promptOrder.single().order.map { it.identifier })

        repository.updatePrompt(id, "a", "New A", "changed", me.rerere.ai.core.MessageRole.USER)
        record = repository.getRecord(id)!!
        assertEquals("New A", record.preset.prompts.first { it.identifier == "a" }.name)
        assertEquals("changed", record.preset.prompts.first { it.identifier == "a" }.content)
        assertTrue(record.isModified)

        repository.resetToDefault(id)
        record = repository.getRecord(id)!!
        assertEquals("A", record.preset.prompts.first { it.identifier == "a" }.content)
        assertFalse(record.isModified)
    }
}
