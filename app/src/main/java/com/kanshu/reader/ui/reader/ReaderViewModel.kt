package com.kanshu.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.repo.BookRepository
import com.kanshu.reader.reader.BookFormat
import com.kanshu.reader.reader.BookParser
import com.kanshu.reader.reader.Chapter
import com.kanshu.reader.reader.ReaderPage
import com.kanshu.reader.reader.TextPaginator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val chapters: List<Chapter> = emptyList(),
    val pages: List<ReaderPage> = emptyList(),
    val pageIndex: Int = 0,
    val pendingChapterIndex: Int = 0,
    val pendingPageInChapter: Int = 0,
    val showControls: Boolean = true,
    val showToc: Boolean = false,
    val needsMeasure: Boolean = true
)

class ReaderViewModel(
    private val bookId: Long,
    private val bookRepository: BookRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.DAY)

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val book = bookRepository.getBook(bookId)
                    ?: error("书籍不存在")
                val format = BookFormat.fromStored(book.format)
                val parsed = withContext(Dispatchers.IO) {
                    BookParser.parse(
                        file = bookRepository.resolveFile(book),
                        format = format,
                        fallbackTitle = book.title
                    )
                }
                val chapterIndex = book.chapterIndex.coerceIn(
                    0,
                    (parsed.chapters.size - 1).coerceAtLeast(0)
                )
                _uiState.update {
                    it.copy(
                        loading = false,
                        title = parsed.metadata.title.ifBlank { book.title },
                        chapters = parsed.chapters,
                        pendingChapterIndex = chapterIndex,
                        pendingPageInChapter = book.scrollOffset.coerceAtLeast(0),
                        needsMeasure = true,
                        pages = emptyList(),
                        pageIndex = 0
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    fun onPageSizeReady(widthPx: Int, heightPx: Int, textSizePx: Float) {
        val state = _uiState.value
        if (state.chapters.isEmpty() || widthPx <= 0 || heightPx <= 0) return

        viewModelScope.launch(Dispatchers.Default) {
            val pages = TextPaginator.paginateBook(
                chapters = state.chapters,
                widthPx = widthPx,
                heightPx = heightPx,
                textSizePx = textSizePx
            )
            if (pages.isEmpty()) {
                _uiState.update { it.copy(pages = emptyList(), needsMeasure = false) }
                return@launch
            }

            val current = state.pages.getOrNull(state.pageIndex)
            val targetChapter = current?.chapterIndex ?: state.pendingChapterIndex
            val targetPageInChapter = if (current != null) {
                state.pages.take(state.pageIndex + 1).count { it.chapterIndex == current.chapterIndex } - 1
            } else {
                state.pendingPageInChapter
            }

            val inChapter = pages.withIndex().filter { it.value.chapterIndex == targetChapter }
            val target = if (inChapter.isEmpty()) {
                0
            } else {
                inChapter.getOrNull(
                    targetPageInChapter.coerceIn(0, inChapter.lastIndex)
                )?.index ?: inChapter.first().index
            }

            _uiState.update {
                it.copy(
                    pages = pages,
                    pageIndex = target.coerceIn(0, pages.lastIndex),
                    needsMeasure = false
                )
            }
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls, showToc = false) }
    }

    fun openToc() {
        _uiState.update { it.copy(showToc = true, showControls = true) }
    }

    fun closeToc() {
        _uiState.update { it.copy(showToc = false) }
    }

    fun selectChapter(index: Int) {
        val state = _uiState.value
        val chapter = index.coerceIn(0, (state.chapters.size - 1).coerceAtLeast(0))
        val page = state.pages.indexOfFirst { it.chapterIndex == chapter }.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                pageIndex = page,
                showToc = false,
                pendingChapterIndex = chapter,
                pendingPageInChapter = 0
            )
        }
        persistProgress(page)
    }

    fun setPageIndex(index: Int) {
        val state = _uiState.value
        if (state.pages.isEmpty()) return
        val page = index.coerceIn(0, state.pages.lastIndex)
        if (page == state.pageIndex) return
        _uiState.update { it.copy(pageIndex = page) }
        persistProgress(page)
    }

    fun previousPage() {
        setPageIndex(_uiState.value.pageIndex - 1)
    }

    fun nextPage() {
        setPageIndex(_uiState.value.pageIndex + 1)
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

    private fun persistProgress(globalPageIndex: Int) {
        val state = _uiState.value
        val page = state.pages.getOrNull(globalPageIndex) ?: return
        val pageInChapter = state.pages
            .take(globalPageIndex + 1)
            .count { it.chapterIndex == page.chapterIndex } - 1
        viewModelScope.launch {
            bookRepository.updateProgress(
                id = bookId,
                chapterIndex = page.chapterIndex,
                scrollOffset = pageInChapter.coerceAtLeast(0)
            )
        }
    }

    companion object {
        fun factory(
            bookId: Long,
            bookRepository: BookRepository,
            themePreferences: ThemePreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(bookId, bookRepository, themePreferences) as T
            }
        }
    }
}
