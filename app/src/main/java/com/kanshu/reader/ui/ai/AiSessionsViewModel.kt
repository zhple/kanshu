package com.kanshu.reader.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.ai.AiChatRepository
import com.kanshu.reader.data.db.AiSessionEntity
import com.kanshu.reader.data.prefs.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiSessionsViewModel(
    private val aiChatRepository: AiChatRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {
    val sessions: StateFlow<List<AiSessionEntity>> = aiChatRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasApiKey: StateFlow<Boolean> = themePreferences.deepseekApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun saveApiKey(key: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    require(key.trim().isNotEmpty()) { "API Key 不能为空" }
                    themePreferences.setDeepseekApiKey(key)
                }
            )
        }
    }

    fun clearApiKey() {
        viewModelScope.launch { themePreferences.clearDeepseekApiKey() }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { aiChatRepository.deleteSession(id) }
    }

    companion object {
        fun factory(
            aiChatRepository: AiChatRepository,
            themePreferences: ThemePreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiSessionsViewModel(aiChatRepository, themePreferences) as T
            }
        }
    }
}
