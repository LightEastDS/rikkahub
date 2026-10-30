package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(
    tableName = "conversation_checkpoint",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversation_id")]
)
data class ConversationCheckpointEntity(
    @PrimaryKey val id: String = Uuid.random().toString(),
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "start_floor", defaultValue = "1") val startFloor: Int = 1,     // 压缩起始楼层数（包含，从1开始）
    @ColumnInfo(name = "boundary_node_id") val boundaryNodeId: String,   // 压缩覆盖到的最后一条消息节点 ID
    @ColumnInfo(name = "boundary_floor") val boundaryFloor: Int,       // 对应楼层数（如第 100 轮）
    @ColumnInfo(name = "summary_content") val summaryContent: String,  // 压缩生成的结构化大纲正文（约 10K~20K tokens）
    @ColumnInfo(name = "original_tokens") val originalTokens: Int,     // 压缩前体积（如 300,000）
    @ColumnInfo(name = "compressed_tokens") val compressedTokens: Int, // 压缩后体积（如 18,500）
    @ColumnInfo(name = "auto_hidden_floors", defaultValue = "''") val autoHiddenFloors: String = "", // 压缩时自动隐藏的楼层（逗号隔开）
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
