package com.kanshu.reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {
    val books: StateFlow<List<BookEntity>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.DAY)

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

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
        }
    }

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
