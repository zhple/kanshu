package com.kanshu.reader.ui.write

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.remote.GithubBooksUploader
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WriteUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val title: String = "",
    val content: String = "",
    val uploadToRemote: Boolean = false,
    val bookId: Long? = null,
    val error: String? = null,
    val savedMessage: String? = null,
    val savedBookId: Long? = null
)

class WriteViewModel(
    private val bookId: Long?,
    private val folderId: Long?,
    private val bookRepository: BookRepository,
    private val themePreferences: ThemePreferences,
    private val githubBooksUploader: GithubBooksUploader
) : ViewModel() {
    private val _uiState = MutableStateFlow(WriteUiState(bookId = bookId, loading = bookId != null))
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()

    val hasGithubToken: StateFlow<Boolean> = themePreferences.githubToken
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (bookId != null) load(bookId)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                val book = bookRepository.getBook(id) ?: error("文稿不存在")
                require(book.format.equals("TXT", ignoreCase = true)) { "只能编辑 TXT 文稿" }
                val content = bookRepository.readTextContent(id)
                _uiState.update {
                    it.copy(
                        loading = false,
                        title = book.title,
                        content = content,
                        bookId = id
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    fun setTitle(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun setContent(value: String) {
        _uiState.update { it.copy(content = value) }
    }

    fun setUploadToRemote(value: Boolean) {
        _uiState.update { it.copy(uploadToRemote = value) }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(savedMessage = null, savedBookId = null) }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching {
                require(state.content.isNotBlank()) { "先写点内容再保存" }
                val id = if (state.bookId != null) {
                    bookRepository.updateTextBook(state.bookId, state.title, state.content)
                    state.bookId
                } else {
                    bookRepository.createTextBook(
                        title = state.title,
                        content = state.content,
                        folderId = folderId
                    )
                }

                var message = "已保存到本地"
                if (state.uploadToRemote) {
                    val token = themePreferences.githubToken.first().trim()
                    require(token.isNotEmpty()) {
                        "要保存到仓库，请先在书架设置里填写 Token"
                    }
                    val book = bookRepository.getBook(id) ?: error("保存后找不到文稿")
                    val upload = githubBooksUploader.uploadBook(book).getOrThrow()
                    message = "已保存到本地，并上传到仓库：${upload.remoteId}"
                }
                id to message
            }.onSuccess { (id, message) ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        bookId = id,
                        savedBookId = id,
                        savedMessage = message
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(saving = false, error = e.message ?: "保存失败")
                }
            }
        }
    }

    companion object {
        fun factory(
            bookId: Long?,
            folderId: Long?,
            bookRepository: BookRepository,
            themePreferences: ThemePreferences,
            githubBooksUploader: GithubBooksUploader
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WriteViewModel(
                    bookId,
                    folderId,
                    bookRepository,
                    themePreferences,
                    githubBooksUploader
                ) as T
            }
        }
    }
}
