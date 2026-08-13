package com.kanshu.reader.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.ai.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiCreateStep {
    DRAFT,
    REVIEW
}

data class AiCreateUiState(
    val step: AiCreateStep = AiCreateStep.DRAFT,
    val scenario: String = "",
    val character: String = "",
    val userPersona: String = "",
    val title: String = "",
    val systemPrompt: String = "",
    val openingHint: String = "",
    val optimizing: Boolean = false,
    val creating: Boolean = false,
    val error: String? = null,
    val createdSessionId: Long? = null
)

class AiCreateViewModel(
    private val aiChatRepository: AiChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiCreateUiState())
    val uiState: StateFlow<AiCreateUiState> = _uiState.asStateFlow()

    fun setScenario(value: String) = _uiState.update { it.copy(scenario = value) }
    fun setCharacter(value: String) = _uiState.update { it.copy(character = value) }
    fun setUserPersona(value: String) = _uiState.update { it.copy(userPersona = value) }
    fun setTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun setSystemPrompt(value: String) = _uiState.update { it.copy(systemPrompt = value) }
    fun setOpeningHint(value: String) = _uiState.update { it.copy(openingHint = value) }

    fun backToDraft() = _uiState.update { it.copy(step = AiCreateStep.DRAFT, error = null) }

    fun consumeCreated() = _uiState.update { it.copy(createdSessionId = null) }

    fun optimize() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update { it.copy(optimizing = true, error = null) }
            runCatching {
                require(s.scenario.isNotBlank()) { "请填写场景想法" }
                require(s.character.isNotBlank()) { "请填写对方角色人设" }
                require(s.userPersona.isNotBlank()) { "请填写我的人设" }
                aiChatRepository.optimizeDraft(
                    scenario = s.scenario,
                    character = s.character,
                    userPersona = s.userPersona
                )
            }.onSuccess { optimized ->
                _uiState.update {
                    it.copy(
                        optimizing = false,
                        step = AiCreateStep.REVIEW,
                        title = optimized.title,
                        systemPrompt = optimized.systemPrompt,
                        openingHint = optimized.openingHint
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(optimizing = false, error = e.message ?: "优化失败")
                }
            }
        }
    }

    fun startChat() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update { it.copy(creating = true, error = null) }
            runCatching {
                require(s.systemPrompt.isNotBlank()) { "提示词不能为空" }
                aiChatRepository.createSession(
                    title = s.title,
                    systemPrompt = s.systemPrompt,
                    openingHint = s.openingHint
                )
            }.onSuccess { id ->
                _uiState.update {
                    it.copy(creating = false, createdSessionId = id)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(creating = false, error = e.message ?: "创建失败")
                }
            }
        }
    }

    companion object {
        fun factory(aiChatRepository: AiChatRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AiCreateViewModel(aiChatRepository) as T
                }
            }
    }
}
