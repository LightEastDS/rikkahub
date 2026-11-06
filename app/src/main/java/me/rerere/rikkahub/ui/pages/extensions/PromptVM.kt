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
import me.rerere.rikkahub.data.repository.StVariableRepository
import me.rerere.rikkahub.data.stpreset.StPresetImportReport
import me.rerere.rikkahub.data.stpreset.StRegexScript
import kotlin.uuid.Uuid

class PromptVM(
    private val settingsStore: SettingsStore,
    private val stPresetRepository: StPresetRepository,
    private val stVariableRepository: StVariableRepository,
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

    fun reorderStPrompt(id: Uuid, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            stPresetRepository.reorderPrompt(id, fromIndex, toIndex)
        }
    }

    fun setStPresetAutoOptimizeOrder(id: Uuid, autoOptimize: Boolean) {
        viewModelScope.launch {
            stPresetRepository.setAutoOptimizeOrder(id, autoOptimize)
        }
    }

    fun resetStPreset(id: Uuid) {
        viewModelScope.launch {
            stPresetRepository.resetToDefault(id)
        }
    }

    fun optimizeStPresetOrder(id: Uuid) {
        viewModelScope.launch {
            stPresetRepository.optimizeOrder(id)
        }
    }

    fun updateStPresetMetadata(
        id: Uuid,
        name: String,
        description: String,
    ) {
        viewModelScope.launch {
            stPresetRepository.updateMetadata(id, name, description)
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

    fun addStPrompt(
        id: Uuid,
        name: String,
        content: String,
        role: MessageRole,
    ) {
        viewModelScope.launch {
            stPresetRepository.addPrompt(id, name, content, role)
        }
    }

    fun deleteStPrompt(
        id: Uuid,
        identifier: String,
    ) {
        viewModelScope.launch {
            stPresetRepository.deletePrompt(id, identifier)
        }
    }

    fun addStRegexScript(
        id: Uuid,
        script: StRegexScript,
    ) {
        viewModelScope.launch {
            stPresetRepository.addRegexScript(id, script)
        }
    }

    fun updateStRegexScript(
        id: Uuid,
        script: StRegexScript,
    ) {
        viewModelScope.launch {
            stPresetRepository.updateRegexScript(id, script)
        }
    }

    fun deleteStRegexScript(
        id: Uuid,
        scriptId: String,
    ) {
        viewModelScope.launch {
            stPresetRepository.deleteRegexScript(id, scriptId)
        }
    }

    fun toggleStRegexScript(
        id: Uuid,
        scriptId: String,
        disabled: Boolean,
    ) {
        viewModelScope.launch {
            stPresetRepository.toggleRegexScript(id, scriptId, disabled)
        }
    }

    suspend fun stPresetRawJson(id: Uuid): String? = stPresetRepository.rawJson(id)

    suspend fun getVariables(scopeId: String): Map<String, String> {
        return withContext(Dispatchers.IO) {
            stVariableRepository.load(scopeId)
        }
    }

    fun saveVariables(scopeId: String, variables: Map<String, String>) {
        viewModelScope.launch(Dispatchers.IO) {
            stVariableRepository.save(scopeId, variables)
        }
    }
}
