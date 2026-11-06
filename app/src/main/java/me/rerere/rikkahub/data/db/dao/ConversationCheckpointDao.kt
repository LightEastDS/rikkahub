package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ConversationCheckpointEntity

@Dao
interface ConversationCheckpointDao {
    @Query("SELECT * FROM conversation_checkpoint WHERE conversation_id = :conversationId ORDER BY boundary_floor DESC, created_at DESC LIMIT 1")
    suspend fun getLatestCheckpoint(conversationId: String): ConversationCheckpointEntity?

    @Query("SELECT * FROM conversation_checkpoint WHERE conversation_id = :conversationId ORDER BY boundary_floor DESC, created_at DESC LIMIT 1")
    fun observeLatestCheckpoint(conversationId: String): Flow<ConversationCheckpointEntity?>

    @Query("SELECT * FROM conversation_checkpoint WHERE conversation_id = :conversationId ORDER BY boundary_floor DESC, created_at DESC")
    suspend fun getAllCheckpoints(conversationId: String): List<ConversationCheckpointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkpoint: ConversationCheckpointEntity)

    @Query("UPDATE conversation_checkpoint SET summary_content = :summaryContent, compressed_tokens = :compressedTokens WHERE id = :id")
    suspend fun updateSummary(id: String, summaryContent: String, compressedTokens: Int)

    @Query("DELETE FROM conversation_checkpoint WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query("DELETE FROM conversation_checkpoint WHERE id = :id")
    suspend fun deleteById(id: String)
}
