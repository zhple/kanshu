package com.kanshu.reader.ui.write

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.remote.GithubBooksUploader
import com.kanshu.reader.data.repo.BookRepository
import com.kanshu.reader.reader.WriteMarkers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class WriteUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val title: String = "",
    val content: String = "",
    val saveFormat: BookRepository.WriteSaveFormat = BookRepository.WriteSaveFormat.TXT,
    val uploadToRemote: Boolean = false,
    val bookId: Long? = null,
    val chapterCount: Int = 0,
    val insertedImages: List<String> = emptyList(),
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
                require(bookRepository.canEditBook(id)) { "这篇文稿无法编辑" }
                val content = bookRepository.readTextContent(id)
                val format = when {
                    book.format.equals("PDF", ignoreCase = true) ->
                        BookRepository.WriteSaveFormat.PDF
                    else -> BookRepository.WriteSaveFormat.TXT
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        title = book.title,
                        content = content,
                        bookId = id,
                        saveFormat = format,
                        chapterCount = countChapters(content),
                        insertedImages = extractImages(content)
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
        _uiState.update {
            it.copy(
                content = value,
                chapterCount = countChapters(value),
                insertedImages = extractImages(value)
            )
        }
    }

    fun setSaveFormat(format: BookRepository.WriteSaveFormat) {
        _uiState.update { it.copy(saveFormat = format) }
    }

    fun setUploadToRemote(value: Boolean) {
        _uiState.update { it.copy(uploadToRemote = value) }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(savedMessage = null, savedBookId = null) }
    }

    /** 在文末插入下一章标题，方便连续写作。 */
    fun startNextChapter(subtitle: String = "") {
        val state = _uiState.value
        val next = countChapters(state.content) + 1
        val heading = buildString {
            append("第")
            append(next)
            append("章")
            val sub = subtitle.trim()
            if (sub.isNotEmpty()) {
                append(' ')
                append(sub)
            }
        }
        val base = state.content.trimEnd()
        val nextContent = when {
            base.isBlank() -> "$heading\n\n"
            else -> "$base\n\n$heading\n\n"
        }
        setContent(nextContent)
    }

    fun insertImage(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                val relative = bookRepository.importWriteImage(contentResolver, uri)
                val marker = WriteMarkers.imageMarker(relative)
                val base = _uiState.value.content.trimEnd()
                val next = if (base.isBlank()) "$marker\n\n" else "$base\n\n$marker\n\n"
                setContent(next)
                // 选 PDF 更利于看图
                if (_uiState.value.saveFormat == BookRepository.WriteSaveFormat.TXT &&
                    extractImages(next).isNotEmpty()
                ) {
                    _uiState.update { it.copy(saveFormat = BookRepository.WriteSaveFormat.PDF) }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "插入图片失败") }
            }
        }
    }

    fun resolveImageFile(path: String): File? = bookRepository.resolveWriteImage(path)

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching {
                require(state.content.isNotBlank()) { "先写点内容再保存" }
                val id = if (state.bookId != null) {
                    bookRepository.updateWrittenBook(
                        bookId = state.bookId,
                        title = state.title,
                        content = state.content,
                        format = state.saveFormat
                    )
                    state.bookId
                } else {
                    bookRepository.createWrittenBook(
                        title = state.title,
                        content = state.content,
                        format = state.saveFormat,
                        folderId = folderId
                    )
                }

                var message = when (state.saveFormat) {
                    BookRepository.WriteSaveFormat.TXT -> "已保存为 TXT"
                    BookRepository.WriteSaveFormat.PDF -> "已保存为 PDF"
                }
                if (state.uploadToRemote) {
                    val token = themePreferences.githubToken.first().trim()
                    require(token.isNotEmpty()) {
                        "要保存到仓库，请先在书架设置里填写 Token"
                    }
                    val book = bookRepository.getBook(id) ?: error("保存后找不到文稿")
                    val upload = githubBooksUploader.uploadBook(book).getOrThrow()
                    message = "$message，并上传到仓库：${upload.remoteId}"
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
        private val chapterLineRegex = Regex(
            """^第[\d零一二三四五六七八九十百千两]+章|^序章|^终章|^楔子|^尾声|^番外|^Chapter\s+\d+"""
        )

        fun countChapters(content: String): Int {
            return content.lineSequence().count { line ->
                val t = line.trim()
                t.isNotEmpty() && chapterLineRegex.containsMatchIn(t)
            }
        }

        fun extractImages(content: String): List<String> {
            return WriteMarkers.imageRegex.findAll(content)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }

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
