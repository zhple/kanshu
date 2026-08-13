package com.kanshu.reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.db.FolderEntity
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.remote.DEFAULT_REMOTE_FOLDER
import com.kanshu.reader.data.remote.DefaultBooksSync
import com.kanshu.reader.data.remote.GithubBooksUploader
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BookSourceFilter {
    ALL,
    REMOTE,
    LOCAL
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val themePreferences: ThemePreferences,
    private val defaultBooksSync: DefaultBooksSync,
    private val githubBooksUploader: GithubBooksUploader
) : ViewModel() {
    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow<String?>(null)
    val currentFolderName: StateFlow<String?> = _currentFolderName.asStateFlow()

    private val _sourceFilter = MutableStateFlow(BookSourceFilter.ALL)
    val sourceFilter: StateFlow<BookSourceFilter> = _sourceFilter.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    val hasGithubToken: StateFlow<Boolean> = themePreferences.githubToken
        .map { token -> token.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val folders: StateFlow<List<FolderEntity>> = bookRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val booksInFolder = _currentFolderId
        .flatMapLatest { folderId -> bookRepository.observeBooksInFolder(folderId) }

    val books: StateFlow<List<BookEntity>> = combine(booksInFolder, _sourceFilter) { list, filter ->
        when (filter) {
            BookSourceFilter.ALL -> list
            BookSourceFilter.REMOTE -> list.filter { it.isRemote }
            BookSourceFilter.LOCAL -> list.filter { !it.isRemote }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allBooks: StateFlow<List<BookEntity>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.DAY)

    init {
        syncDefaultBooks()
    }

    fun setSourceFilter(filter: BookSourceFilter) {
        _sourceFilter.value = filter
    }

    fun syncDefaultBooks(manual: Boolean = false) {
        viewModelScope.launch {
            val result = defaultBooksSync.sync()
            if (manual || result.added > 0 || result.failed > 0 || result.reassigned > 0) {
                _syncMessage.value = result.message
            }
        }
    }

    fun consumeSyncMessage() {
        _syncMessage.value = null
    }

    fun saveGithubToken(token: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(runCatching {
                require(token.trim().isNotEmpty()) { "Token 不能为空" }
                themePreferences.setGithubToken(token)
            })
        }
    }

    fun clearGithubToken() {
        viewModelScope.launch { themePreferences.clearGithubToken() }
    }

    fun uploadBookToRemote(book: BookEntity) {
        viewModelScope.launch {
            _uploading.value = true
            val result = githubBooksUploader.uploadBook(book)
            _uploading.value = false
            _syncMessage.value = result.fold(
                onSuccess = { it.message },
                onFailure = { it.message ?: "上传失败" }
            )
        }
    }

    fun openFolder(folder: FolderEntity) {
        _currentFolderId.value = folder.id
        _currentFolderName.value = folder.name
    }

    fun openRoot() {
        _currentFolderId.value = null
        _currentFolderName.value = null
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val next = if (themeMode.value == AppThemeMode.DAY) {
                AppThemeMode.NIGHT
            } else {
                AppThemeMode.DAY
            }
            themePreferences.setThemeMode(next)
        }
    }

    fun createFolder(name: String, onDone: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    val trimmed = name.trim()
                    bookRepository.createFolder(trimmed)
                    if (hasGithubToken.value) {
                        githubBooksUploader.ensureRemoteFolder(trimmed).getOrThrow()
                        "已创建并同步到远程：$trimmed"
                    } else {
                        "已创建本机分类。配置 Token 后可同步到远程。"
                    }
                }
            )
        }
    }

    fun renameFolder(id: Long, name: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    bookRepository.renameFolder(id, name)
                    if (_currentFolderId.value == id) {
                        _currentFolderName.value = name.trim()
                    }
                }
            )
        }
    }

    fun deleteFolder(id: Long, onDone: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    val folder = bookRepository.getFolder(id) ?: error("文件夹不存在")
                    val name = folder.name
                    bookRepository.deleteFolder(id)
                    if (_currentFolderId.value == id) openRoot()
                    if (hasGithubToken.value && name != DEFAULT_REMOTE_FOLDER) {
                        githubBooksUploader.removeRemoteFolder(name).getOrThrow()
                        "已删除并同步远程分类"
                    } else {
                        "已删除本机文件夹"
                    }
                }
            )
        }
    }

    fun renameBook(id: Long, title: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(runCatching { bookRepository.renameBook(id, title); Unit })
        }
    }

    fun moveBook(bookId: Long, folderId: Long?, onDone: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(
                runCatching {
                    val book = bookRepository.getBook(bookId) ?: error("书籍不存在")
                    bookRepository.moveBook(bookId, folderId)
                    if (!book.isRemote) {
                        return@runCatching "已移动"
                    }
                    val remoteId = book.remoteId?.takeIf { it.isNotBlank() }
                        ?: error("缺少远程 ID，请先上传到仓库")
                    val remoteFolder = if (folderId == null) {
                        DEFAULT_REMOTE_FOLDER
                    } else {
                        bookRepository.getFolder(folderId)?.name?.trim().orEmpty()
                            .ifBlank { DEFAULT_REMOTE_FOLDER }
                    }
                    if (hasGithubToken.value) {
                        githubBooksUploader.ensureRemoteFolder(remoteFolder).getOrThrow()
                        githubBooksUploader.updateRemoteBookFolder(remoteId, remoteFolder).getOrThrow()
                        if (folderId == null) {
                            val defaultId = bookRepository.ensureFolder(DEFAULT_REMOTE_FOLDER)
                            bookRepository.moveBook(bookId, defaultId)
                        }
                        "已移动并同步远程分类到「$remoteFolder」"
                    } else {
                        "已移动到本机。配置 Token 后才能同步远程分类。"
                    }
                }
            )
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
        }
    }

    fun bookCountInFolder(folderId: Long, all: List<BookEntity>): Int =
        all.count { it.folderId == folderId }

    companion object {
        fun factory(
            bookRepository: BookRepository,
            themePreferences: ThemePreferences,
            defaultBooksSync: DefaultBooksSync,
            githubBooksUploader: GithubBooksUploader
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(
                    bookRepository,
                    themePreferences,
                    defaultBooksSync,
                    githubBooksUploader
                ) as T
            }
        }
    }
}
