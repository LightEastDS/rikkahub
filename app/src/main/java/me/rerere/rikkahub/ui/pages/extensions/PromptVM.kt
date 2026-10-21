package me.rerere.rikkahub.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.StPresetRepository
import me.rerere.rikkahub.data.repository.StPresetRecord
import me.rerere.rikkahub.data.stpreset.StPresetImportReport
import kotlin.uuid.Uuid

class PromptVM(
    private val settingsStore: SettingsStore,
    private val stPresetRepository: StPresetRepository,
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    val stPresetRecords: kotlinx.coroutines.flow.StateFlow<List<StPresetRecord>> =
        stPresetRepository.records.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList(),
        )

    fun updateSettings(fn: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(fn)
        }
    }

    suspend fun importStPreset(rawJson: String, fallbackName: String): StPresetImportReport {
        return withContext(Dispatchers.Default) {
            stPresetRepository.import(rawJson, fallbackName).report
        }
    }

    fun deleteStPreset(id: Uuid) {
        viewModelScope.launch {
            stPresetRepository.delete(id)
        }
    }

    fun setStPresetEnabled(id: Uuid, enabled: Boolean) {
        viewModelScope.launch {
            stPresetRepository.setEnabled(id, enabled)
        }
    }

    fun setStPromptEnabled(id: Uuid, orderIndex: Int, enabled: Boolean) {
        viewModelScope.launch {
            stPresetRepository.setPromptEnabled(id, orderIndex, enabled)
        }
    }

    fun moveStPrompt(id: Uuid, orderIndex: Int, delta: Int) {
        viewModelScope.launch {
            stPresetRepository.movePrompt(id, orderIndex, delta)
        }
    }

    fun resetStPreset(id: Uuid) {
        viewModelScope.launch {
            stPresetRepository.resetToDefault(id)
        }
    }

    fun updateStPrompt(
        id: Uuid,
        identifier: String,
        name: String,
        content: String,
        role: MessageRole,
    ) {
        viewModelScope.launch {
            stPresetRepository.updatePrompt(id, identifier, name, content, role)
        }
    }

    suspend fun stPresetRawJson(id: Uuid): String? = stPresetRepository.rawJson(id)
}
