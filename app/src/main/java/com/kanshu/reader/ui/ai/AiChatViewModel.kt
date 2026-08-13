package com.kanshu.reader.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.ai.AiChatRepository
import com.kanshu.reader.data.ai.ChatMessage
import com.kanshu.reader.data.db.AiMessageEntity
import com.kanshu.reader.data.db.AiSessionEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatUiState(
    val session: AiSessionEntity? = null,
    val input: String = "",
    val streaming: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val openingDone: Boolean = false,
    /** 正在为哪条消息生图；null 表示空闲 */
    val generatingImageFor: Long? = null
)

class AiChatViewModel(
    private val sessionId: Long,
    private val aiChatRepository: AiChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    val messages: StateFlow<List<AiMessageEntity>> =
        aiChatRepository.observeMessages(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val session = aiChatRepository.getSession(sessionId)
            if (session == null) {
                _uiState.update { it.copy(error = "会话不存在") }
                return@launch
            }
            _uiState.update { it.copy(session = session) }
            if (aiChatRepository.needsOpening(sessionId)) {
                startOpening(session)
            } else {
                _uiState.update { it.copy(openingDone = true) }
            }
        }
    }

    fun setInput(value: String) = _uiState.update { it.copy(input = value) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.streaming) return
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(input = "", error = null) }
            aiChatRepository.appendUserMessage(sessionId, text)
            streamAssistant(session)
        }
    }

    fun generateSceneImage(messageId: Long) {
        if (_uiState.value.generatingImageFor != null || _uiState.value.streaming) return
        if (messageId <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(generatingImageFor = messageId, error = null) }
            runCatching {
                aiChatRepository.generateSceneImage(sessionId, messageId)
                // 刷新 session（可能刚写入 Visual DNA）
                aiChatRepository.getSession(sessionId)?.let { s ->
                    _uiState.update { it.copy(session = s) }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "生图失败") }
            }
            _uiState.update { it.copy(generatingImageFor = null) }
        }
    }

    private fun startOpening(session: AiSessionEntity) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            runCatching {
                val hint = session.openingHint.ifBlank {
                    "请根据设定，用中文写出富有代入感的第一幕开场（含旁白与情境），不要替用户行动或说话。"
                }
                streamAssistant(
                    session = session,
                    extraHistory = listOf(ChatMessage("user", hint)),
                    persistTriggerAsUser = false
                )
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        streaming = false,
                        streamingText = "",
                        error = e.message ?: "开场失败",
                        openingDone = true
                    )
                }
            }
        }
    }

    private suspend fun streamAssistant(
        session: AiSessionEntity,
        extraHistory: List<ChatMessage> = emptyList(),
        persistTriggerAsUser: Boolean = true
    ) {
        _uiState.update { it.copy(streaming = true, streamingText = "", error = null) }
        val history = buildList {
            addAll(aiChatRepository.listChatMessages(sessionId))
            addAll(extraHistory)
        }
        @Suppress("UNUSED_VARIABLE")
        val ignored = persistTriggerAsUser

        val assistantId = aiChatRepository.beginAssistantMessage(sessionId)
        val buffer = StringBuilder()
        try {
            aiChatRepository.streamReply(
                sessionId = sessionId,
                systemPrompt = session.systemPrompt,
                history = history
            ).collect { delta ->
                buffer.append(delta)
                val text = buffer.toString()
                _uiState.update { it.copy(streamingText = text) }
                aiChatRepository.updateAssistantMessage(assistantId, text)
            }
            aiChatRepository.finishAssistantTurn(sessionId)
            _uiState.update {
                it.copy(
                    streaming = false,
                    streamingText = "",
                    openingDone = true
                )
            }
        } catch (e: Exception) {
            if (buffer.isNotEmpty()) {
                aiChatRepository.updateAssistantMessage(assistantId, buffer.toString())
                aiChatRepository.finishAssistantTurn(sessionId)
            }
            _uiState.update {
                it.copy(
                    streaming = false,
                    streamingText = "",
                    openingDone = true,
                    error = e.message ?: "生成失败"
                )
            }
        }
    }

    companion object {
        fun factory(
            sessionId: Long,
            aiChatRepository: AiChatRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiChatViewModel(sessionId, aiChatRepository) as T
            }
        }
    }
}
