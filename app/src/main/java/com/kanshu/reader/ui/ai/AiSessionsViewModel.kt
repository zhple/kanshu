package com.kanshu.reader.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.ai.AiChatRepository
import com.kanshu.reader.data.db.AiSessionEntity
import com.kanshu.reader.data.prefs.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiKeysStatus(
    val hasDeepseek: Boolean = false,
    val hasSiliconflow: Boolean = false,
    val hasMinimax: Boolean = false
)

class AiSessionsViewModel(
    private val aiChatRepository: AiChatRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {
    val sessions: StateFlow<List<AiSessionEntity>> = aiChatRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val keysStatus: StateFlow<AiKeysStatus> = combine(
        themePreferences.deepseekApiKey,
        themePreferences.siliconflowApiKey,
        themePreferences.minimaxApiKey
    ) { deepseek, silicon, minimax ->
        AiKeysStatus(
            hasDeepseek = deepseek.isNotBlank(),
            hasSiliconflow = silicon.isNotBlank(),
            hasMinimax = minimax.isNotBlank()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiKeysStatus())

    fun saveKeys(
        deepseekKey: String?,
        siliconflowKey: String?,
        minimaxKey: String?,
        onDone: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    if (deepseekKey != null) {
                        require(deepseekKey.trim().isNotEmpty()) { "DeepSeek API Key 不能为空" }
                        themePreferences.setDeepseekApiKey(deepseekKey)
                    }
                    if (siliconflowKey != null) {
                        require(siliconflowKey.trim().isNotEmpty()) { "硅基流动 API Key 不能为空" }
                        themePreferences.setSiliconflowApiKey(siliconflowKey)
                    }
                    if (minimaxKey != null) {
                        require(minimaxKey.trim().isNotEmpty()) { "MiniMax API Key 不能为空" }
                        themePreferences.setMinimaxApiKey(minimaxKey)
                    }
                }
            )
        }
    }

    fun clearDeepseekApiKey() {
        viewModelScope.launch { themePreferences.clearDeepseekApiKey() }
    }

    fun clearSiliconflowApiKey() {
        viewModelScope.launch { themePreferences.clearSiliconflowApiKey() }
    }

    fun clearMinimaxApiKey() {
        viewModelScope.launch { themePreferences.clearMinimaxApiKey() }
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
