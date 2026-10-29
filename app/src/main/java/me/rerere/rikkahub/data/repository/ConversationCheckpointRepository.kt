package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.ConversationCheckpointDao
import me.rerere.rikkahub.data.db.entity.ConversationCheckpointEntity
import kotlin.uuid.Uuid

class ConversationCheckpointRepository(
    private val dao: ConversationCheckpointDao,
) {
    suspend fun getLatest(conversationId: Uuid): ConversationCheckpointEntity? =
        dao.getLatestCheckpoint(conversationId.toString())

    fun observeLatest(conversationId: Uuid): Flow<ConversationCheckpointEntity?> =
        dao.observeLatestCheckpoint(conversationId.toString())

    suspend fun getAll(conversationId: Uuid): List<ConversationCheckpointEntity> =
        dao.getAllCheckpoints(conversationId.toString())

    suspend fun saveCheckpoint(checkpoint: ConversationCheckpointEntity) =
        dao.insert(checkpoint)

    suspend fun clear(conversationId: Uuid) =
        dao.deleteByConversationId(conversationId.toString())

    suspend fun delete(checkpointId: String) =
        dao.deleteById(checkpointId)
}
