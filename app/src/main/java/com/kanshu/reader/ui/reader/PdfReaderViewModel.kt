package com.kanshu.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.repo.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class PdfReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val pageCount: Int = 0,
    val pageIndex: Int = 0,
    val showControls: Boolean = true
)

class PdfReaderViewModel(
    private val bookId: Long,
    private val bookRepository: BookRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.DAY)

    private var pdfFile: File? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val renderMutex = Mutex()

    init {
        open()
    }

    private fun open() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val book = bookRepository.getBook(bookId) ?: error("书籍不存在")
                val file = bookRepository.resolveFile(book)
                pdfFile = file
                withContext(Dispatchers.IO) {
                    closeRendererLocked()
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd!!)
                }
                val count = renderer?.pageCount ?: 0
                val page = book.chapterIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        loading = false,
                        title = book.title,
                        pageCount = count,
                        pageIndex = page
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "无法打开 PDF")
                }
            }
        }
    }

    suspend fun renderPage(index: Int, maxWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val pdf = renderer ?: return@withLock null
            if (index !in 0 until pdf.pageCount || maxWidth <= 0) return@withLock null
            pdf.openPage(index).use { page ->
                val scale = maxWidth.toFloat() / page.width.toFloat()
                val width = maxWidth
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun setPageIndex(index: Int) {
        val state = _uiState.value
        if (state.pageCount == 0) return
        val page = index.coerceIn(0, state.pageCount - 1)
        if (page == state.pageIndex) return
        _uiState.update { it.copy(pageIndex = page) }
        viewModelScope.launch {
            bookRepository.updateProgress(bookId, page, 0)
        }
    }

    fun previousPage() = setPageIndex(_uiState.value.pageIndex - 1)

    fun nextPage() = setPageIndex(_uiState.value.pageIndex + 1)

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
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

    private fun closeRendererLocked() {
        renderer?.close()
        renderer = null
        pfd?.close()
        pfd = null
    }

    override fun onCleared() {
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        renderer = null
        try {
            pfd?.close()
        } catch (_: Exception) {
        }
        pfd = null
        super.onCleared()
    }

    companion object {
        fun factory(
            bookId: Long,
            bookRepository: BookRepository,
            themePreferences: ThemePreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PdfReaderViewModel(bookId, bookRepository, themePreferences) as T
            }
        }
    }
}
