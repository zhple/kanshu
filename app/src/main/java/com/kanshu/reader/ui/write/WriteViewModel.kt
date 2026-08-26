package com.kanshu.reader.ui.write

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.remote.GithubBooksUploader
import com.kanshu.reader.data.repo.BookRepository
import com.kanshu.reader.reader.WriteBlock
import com.kanshu.reader.reader.WriteBlocks
import com.kanshu.reader.reader.WriteEditPage
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
    val blocks: List<WriteBlock> = listOf(WriteBlock.Paragraph("")),
    val pages: List<WriteEditPage> = listOf(WriteEditPage("正文", 0, 1)),
    val pageIndex: Int = 0,
    val saveFormat: BookRepository.WriteSaveFormat = BookRepository.WriteSaveFormat.TXT,
    val uploadToRemote: Boolean = false,
    val bookId: Long? = null,
    val chapterCount: Int = 0,
    val focusedBlockIndex: Int = 0,
    val error: String? = null,
    val savedMessage: String? = null,
    val savedBookId: Long? = null
) {
    val content: String get() = WriteBlocks.serialize(blocks)
}

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
                val blocks = WriteBlocks.parse(content)
                val format = when {
                    book.format.equals("PDF", ignoreCase = true) ->
                        BookRepository.WriteSaveFormat.PDF
                    else -> BookRepository.WriteSaveFormat.TXT
                }
                _uiState.update {
                    rebuild(
                        it.copy(
                            loading = false,
                            title = book.title,
                            blocks = blocks,
                            bookId = id,
                            saveFormat = format,
                            focusedBlockIndex = 0,
                            pageIndex = 0
                        )
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    private fun rebuild(state: WriteUiState): WriteUiState {
        val pages = WriteBlocks.buildPages(state.blocks)
        val pageIndex = state.pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        return state.copy(
            pages = pages,
            pageIndex = pageIndex,
            chapterCount = countChapters(WriteBlocks.serialize(state.blocks))
        )
    }

    fun setTitle(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun setPageIndex(index: Int) {
        _uiState.update { state ->
            val max = (state.pages.size - 1).coerceAtLeast(0)
            state.copy(pageIndex = index.coerceIn(0, max))
        }
    }

    fun setFocusedBlock(index: Int) {
        _uiState.update { it.copy(focusedBlockIndex = index.coerceAtLeast(0)) }
    }

    fun updateParagraph(index: Int, text: String) {
        _uiState.update { state ->
            val blocks = state.blocks.toMutableList()
            if (index !in blocks.indices) return@update state
            val current = blocks[index] as? WriteBlock.Paragraph ?: return@update state
            blocks[index] = current.copy(text = text)
            rebuild(state.copy(blocks = blocks, focusedBlockIndex = index))
        }
    }

    fun setImageWidth(index: Int, widthPercent: Float) {
        _uiState.update { state ->
            val blocks = state.blocks.toMutableList()
            val current = blocks.getOrNull(index) as? WriteBlock.Image ?: return@update state
            blocks[index] = current.copy(widthPercent = widthPercent.coerceIn(0.3f, 1f))
            rebuild(state.copy(blocks = blocks))
        }
    }

    /** 在当前编辑页内拖拽排序（from/to 为页内局部下标）。 */
    fun moveWithinPage(fromLocal: Int, toLocal: Int) {
        if (fromLocal == toLocal) return
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.pageIndex) ?: return@update state
            val from = page.startIndex + fromLocal
            val to = page.startIndex + toLocal
            if (from !in state.blocks.indices || to !in state.blocks.indices) return@update state
            // 页内重排不改分页边界，避免拖拽过程中页结构抖动
            if (from !in page.startIndex until page.endExclusive ||
                to !in page.startIndex until page.endExclusive
            ) {
                return@update state
            }
            val blocks = state.blocks.toMutableList()
            val item = blocks.removeAt(from)
            blocks.add(to, item)
            state.copy(blocks = blocks, focusedBlockIndex = to)
        }
    }

    fun moveBlock(index: Int, delta: Int) {
        _uiState.update { state ->
            val target = index + delta
            if (index !in state.blocks.indices || target !in state.blocks.indices) return@update state
            val blocks = state.blocks.toMutableList()
            val item = blocks.removeAt(index)
            blocks.add(target, item)
            if (blocks.last() !is WriteBlock.Paragraph) {
                blocks += WriteBlock.Paragraph("")
            }
            val rebuilt = rebuild(state.copy(blocks = blocks, focusedBlockIndex = target))
            // 尽量留在包含该块的页
            val pageIdx = rebuilt.pages.indexOfFirst { target in it.startIndex until it.endExclusive }
                .coerceAtLeast(0)
            rebuilt.copy(pageIndex = pageIdx)
        }
    }

    fun removeBlock(index: Int) {
        _uiState.update { state ->
            if (index !in state.blocks.indices) return@update state
            val blocks = state.blocks.toMutableList()
            blocks.removeAt(index)
            if (blocks.isEmpty() || blocks.last() !is WriteBlock.Paragraph) {
                blocks += WriteBlock.Paragraph("")
            }
            rebuild(
                state.copy(
                    blocks = blocks,
                    focusedBlockIndex = index.coerceIn(0, blocks.lastIndex)
                )
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
        val blocks = state.blocks.toMutableList()
        val focus = state.focusedBlockIndex.coerceIn(0, blocks.lastIndex)
        val insertAt = when (val cur = blocks.getOrNull(focus)) {
            is WriteBlock.Paragraph -> {
                val base = cur.text.trimEnd()
                blocks[focus] = cur.copy(
                    text = if (base.isBlank()) heading else "$base\n\n$heading"
                )
                focus + 1
            }
            else -> {
                blocks.add(focus + 1, WriteBlock.Paragraph(heading))
                focus + 2
            }
        }
        if (insertAt >= blocks.size || blocks.getOrNull(insertAt) !is WriteBlock.Paragraph) {
            blocks.add(insertAt.coerceAtMost(blocks.size), WriteBlock.Paragraph(""))
        }
        val rebuilt = rebuild(
            state.copy(
                blocks = blocks,
                focusedBlockIndex = insertAt.coerceIn(0, blocks.lastIndex)
            )
        )
        val pageIdx = rebuilt.pages.indexOfFirst {
            rebuilt.focusedBlockIndex in it.startIndex until it.endExclusive
        }.coerceAtLeast(0)
        _uiState.value = rebuilt.copy(pageIndex = pageIdx)
    }

    fun insertImage(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                val relative = bookRepository.importWriteImage(contentResolver, uri)
                _uiState.update { state ->
                    val blocks = state.blocks.toMutableList()
                    val page = state.pages.getOrNull(state.pageIndex)
                    val focus = state.focusedBlockIndex.coerceIn(
                        0,
                        (blocks.size - 1).coerceAtLeast(0)
                    )
                    val insertAt = when {
                        page != null && focus in page.startIndex until page.endExclusive ->
                            (focus + 1).coerceAtMost(page.endExclusive)
                        page != null -> page.endExclusive
                        else -> (focus + 1).coerceAtMost(blocks.size)
                    }
                    blocks.add(insertAt, WriteBlock.Image(relative, widthPercent = 1f))
                    blocks.add(insertAt + 1, WriteBlock.Paragraph(""))
                    val rebuilt = rebuild(
                        state.copy(
                            blocks = blocks,
                            saveFormat = BookRepository.WriteSaveFormat.PDF,
                            focusedBlockIndex = insertAt + 1
                        )
                    )
                    val pageIdx = rebuilt.pages.indexOfFirst {
                        insertAt in it.startIndex until it.endExclusive
                    }.coerceAtLeast(0)
                    rebuilt.copy(pageIndex = pageIdx)
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
                require(!WriteBlocks.contentLooksEmpty(state.blocks)) { "先写点内容再保存" }
                val content = WriteBlocks.serialize(state.blocks)
                val id = if (state.bookId != null) {
                    bookRepository.updateWrittenBook(
                        bookId = state.bookId,
                        title = state.title,
                        content = content,
                        format = state.saveFormat
                    )
                    state.bookId
                } else {
                    bookRepository.createWrittenBook(
                        title = state.title,
                        content = content,
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
