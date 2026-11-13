package me.rerere.rikkahub.data.datastore

import android.content.SharedPreferences
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ChatDraftStoreTest {
    @Test
    fun `saves restores isolates and persists drafts per conversation`() {
        val prefs = InMemorySharedPreferences()
        val store = ChatDraftStore(prefs = prefs)
        val convA = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val convB = Uuid.parse("00000000-0000-0000-0000-000000000002")

        val imagePart = UIMessagePart.Image(url = "file:///tmp/image.png")
        store.save(convA, "draft for A", listOf(imagePart))
        store.save(convB, "draft for B", emptyList())

        assertEquals(ChatInputDraft("draft for A", listOf(imagePart)), store.get(convA))
        assertEquals(ChatInputDraft("draft for B", emptyList()), store.get(convB))

        // 模拟应用重启后重新加载
        val reloaded = ChatDraftStore(prefs = prefs)
        assertEquals(ChatInputDraft("draft for A", listOf(imagePart)), reloaded.get(convA))
        assertEquals(ChatInputDraft("draft for B", emptyList()), reloaded.get(convB))

        // 清空或发送后保存空内容会移除草稿
        reloaded.save(convA, "   ", emptyList())
        assertNull(reloaded.get(convA))
        assertEquals(ChatInputDraft("draft for B", emptyList()), reloaded.get(convB))

        // 关闭草稿保留后清掉全部草稿
        reloaded.clearAll()
        assertNull(reloaded.get(convB))
        assertEquals(emptyMap<String, Any?>(), prefs.all)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
        override fun edit(): SharedPreferences.Editor = EditorImpl()

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
                pending[key] = value
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                pending[key] = values
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
                pending[key] =
                    value
            }
            override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) {
                    data.clear()
                }
                removals.forEach { data.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
