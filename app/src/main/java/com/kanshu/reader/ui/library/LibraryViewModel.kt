package com.kanshu.reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.db.FolderEntity
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {
    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow<String?>(null)
    val currentFolderName: StateFlow<String?> = _currentFolderName.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = bookRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books: StateFlow<List<BookEntity>> = _currentFolderId
        .flatMapLatest { folderId -> bookRepository.observeBooksInFolder(folderId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allBooks: StateFlow<List<BookEntity>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.DAY)

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

    fun createFolder(name: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onDone(runCatching { bookRepository.createFolder(name); Unit })
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

    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            bookRepository.deleteFolder(id)
            if (_currentFolderId.value == id) openRoot()
        }
    }

    fun moveBook(bookId: Long, folderId: Long?) {
        viewModelScope.launch {
            bookRepository.moveBook(bookId, folderId)
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
            themePreferences: ThemePreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(bookRepository, themePreferences) as T
            }
        }
    }
}
