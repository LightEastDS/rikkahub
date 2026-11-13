package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

@Serializable
data class ChatInputDraft(
    val text: String = "",
    val attachments: List<UIMessagePart> = emptyList(),
)

/**
 * 保存每个会话的未发送输入草稿，支持在切换会话和应用重启之间恢复，并在发送或删除会话后清理。
 */
class ChatDraftStore(
    private val prefs: SharedPreferences? = null,
    private val json: Json = JsonInstant,
) {
    constructor(context: Context, json: Json = JsonInstant) : this(
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        json = json,
    )

    private val drafts = ConcurrentHashMap<Uuid, ChatInputDraft>()

    init {
        loadFromPrefs()
    }

    fun get(conversationId: Uuid): ChatInputDraft? = drafts[conversationId]

    fun save(conversationId: Uuid, text: String, attachments: List<UIMessagePart>) {
        if (text.isBlank() && attachments.isEmpty()) {
            remove(conversationId)
            return
        }
        val draft = ChatInputDraft(text = text, attachments = attachments.toList())
        if (drafts[conversationId] == draft) return
        drafts[conversationId] = draft
        prefs?.edit {
            putString(conversationId.toString(), json.encodeToString(ChatInputDraft.serializer(), draft))
        }
    }

    fun remove(conversationId: Uuid) {
        if (drafts.remove(conversationId) != null) {
            prefs?.edit { remove(conversationId.toString()) }
        }
    }

    fun clearAll() {
        drafts.clear()
        prefs?.edit { clear() }
    }

    private fun loadFromPrefs() {
        val entries = prefs?.all ?: return
        entries.forEach { (key, value) ->
            val id = runCatching { Uuid.parse(key) }.getOrNull() ?: return@forEach
            val raw = value as? String ?: return@forEach
            val draft = runCatching { json.decodeFromString(ChatInputDraft.serializer(), raw) }.getOrNull()
                ?: return@forEach
            if (draft.text.isNotBlank() || draft.attachments.isNotEmpty()) {
                drafts[id] = draft
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "chat_input_drafts"
    }
}
