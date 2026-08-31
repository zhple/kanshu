package com.kanshu.reader.ui.write

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanshu.reader.data.ai.DeepSeekClient
import com.kanshu.reader.data.prefs.ThemePreferences
import com.kanshu.reader.data.remote.GithubBooksUploader
import com.kanshu.reader.data.repo.BookRepository
import com.kanshu.reader.reader.WriteBlock
import com.kanshu.reader.reader.WriteBlocks
import com.kanshu.reader.reader.WriteEditPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class WriteUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val autoSaving: Boolean = false,
    val dirty: Boolean = false,
    val title: String = "",
    val blocks: List<WriteBlock> = listOf(WriteBlock.Paragraph("")),
    val pages: List<WriteEditPage> = listOf(WriteEditPage("正文", 0, 1)),
    val outline: List<WriteBlocks.OutlineItem> = emptyList(),
    val pageIndex: Int = 0,
    val saveFormat: BookRepository.WriteSaveFormat = BookRepository.WriteSaveFormat.TXT,
    val uploadToRemote: Boolean = false,
    val bookId: Long? = null,
    val chapterCount: Int = 0,
    val charCount: Int = 0,
    val sessionGain: Int = 0,
    val focusedBlockIndex: Int = 0,
    val focusMode: Boolean = false,
    val showOutline: Boolean = false,
    val statusHint: String? = null,
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
    private val githubBooksUploader: GithubBooksUploader,
    private val deepSeekClient: DeepSeekClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(WriteUiState(bookId = bookId, loading = bookId != null))
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()

    val hasGithubToken: StateFlow<Boolean> = themePreferences.githubToken
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var baselineChars = 0
    private var autoSaveJob: Job? = null

    init {
        if (bookId != null) load(bookId) else {
            baselineChars = 0
            startAutoSaveLoop()
        }
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                val book = bookRepository.getBook(id) ?: error("文稿不存在")
                require(bookRepository.canEditBook(id)) { "这篇文稿无法编辑" }
                val content = bookRepository.readTextContent(id)
                val blocks = WriteBlocks.parse(content)
                baselineChars = WriteBlocks.charCount(blocks)
                _uiState.update {
                    rebuild(
                        it.copy(
                            loading = false,
                            title = book.title,
                            blocks = blocks,
                            bookId = id,
                            saveFormat = BookRepository.WriteSaveFormat.TXT,
                            focusedBlockIndex = 0,
                            pageIndex = 0,
                            dirty = false,
                            sessionGain = 0
                        )
                    )
                }
                startAutoSaveLoop()
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
        val chars = WriteBlocks.charCount(state.blocks)
        val sessionGain = (chars - baselineChars).coerceAtLeast(0)
        return state.copy(
            pages = pages,
            pageIndex = pageIndex,
            chapterCount = countChapters(WriteBlocks.serialize(state.blocks)),
            outline = WriteBlocks.outline(state.blocks),
            charCount = chars,
            sessionGain = sessionGain
        )
    }

    private fun markDirty(transform: (WriteUiState) -> WriteUiState) {
        _uiState.update { state ->
            rebuild(transform(state)).copy(dirty = true, statusHint = null)
        }
    }

    fun setTitle(value: String) {
        markDirty { it.copy(title = value) }
    }

    fun setPageIndex(index: Int) {
        _uiState.update { state ->
            val max = (state.pages.size - 1).coerceAtLeast(0)
            state.copy(pageIndex = index.coerceIn(0, max))
        }
    }

    fun jumpToOutline(item: WriteBlocks.OutlineItem) {
        _uiState.update {
            it.copy(
                pageIndex = item.pageIndex.coerceIn(0, (it.pages.size - 1).coerceAtLeast(0)),
                focusedBlockIndex = item.blockIndex,
                showOutline = false
            )
        }
    }

    fun setFocusedBlock(index: Int) {
        _uiState.update { it.copy(focusedBlockIndex = index.coerceAtLeast(0)) }
    }

    fun toggleFocusMode() {
        _uiState.update { it.copy(focusMode = !it.focusMode, showOutline = false) }
    }

    fun setShowOutline(show: Boolean) {
        _uiState.update { it.copy(showOutline = show) }
    }

    fun updatePagePlainText(text: String, charBudget: Int = WriteBlocks.PAGE_CHAR_BUDGET) {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.pageIndex) ?: return
        val split = WriteBlocks.splitTextOverflow(text, charBudget)
        if (split.overflow.isNotEmpty()) {
            val blocks = state.blocks.toMutableList()
            WriteBlocks.setPagePlainText(blocks, page, split.keep)
            val pagesAfterKeep = WriteBlocks.buildPages(blocks)
            val currentPage = pagesAfterKeep.getOrNull(state.pageIndex) ?: page
            val insertAt = currentPage.endExclusive.coerceAtMost(blocks.size)
            blocks.add(insertAt, WriteBlock.Paragraph(split.overflow))
            val overflowIndex = insertAt
            _uiState.update {
                val rebuilt = rebuild(it.copy(blocks = blocks, dirty = true))
                val pageIdx = rebuilt.pages.indexOfFirst { p ->
                    overflowIndex in p.startIndex until p.endExclusive
                }.coerceAtLeast(rebuilt.pageIndex)
                rebuilt.copy(
                    pageIndex = pageIdx,
                    statusHint = "本页已满，已自动翻到下一页"
                )
            }
        } else {
            markDirty { s ->
                val blocks = s.blocks.toMutableList()
                WriteBlocks.setPagePlainText(blocks, page, text)
                s.copy(blocks = blocks)
            }
        }
    }

    fun pagePlainText(pageIndex: Int = _uiState.value.pageIndex): String {
        val state = _uiState.value
        val page = state.pages.getOrNull(pageIndex) ?: return ""
        return WriteBlocks.pagePlainText(state.blocks, page)
    }

    fun updateParagraph(index: Int, text: String) {
        markDirty { state ->
            val blocks = state.blocks.toMutableList()
            if (index !in blocks.indices) return@markDirty state
            val current = blocks[index] as? WriteBlock.Paragraph ?: return@markDirty state
            blocks[index] = current.copy(text = text)
            state.copy(blocks = blocks, focusedBlockIndex = index)
        }
    }

    fun setImageWidth(index: Int, widthPercent: Float) {
        markDirty { state ->
            val blocks = state.blocks.toMutableList()
            val current = blocks.getOrNull(index) as? WriteBlock.Image ?: return@markDirty state
            blocks[index] = current.copy(widthPercent = widthPercent.coerceIn(0.3f, 1f))
            state.copy(blocks = blocks)
        }
    }

    fun moveWithinPage(fromLocal: Int, toLocal: Int) {
        if (fromLocal == toLocal) return
        markDirty { state ->
            val page = state.pages.getOrNull(state.pageIndex) ?: return@markDirty state
            val from = page.startIndex + fromLocal
            val to = page.startIndex + toLocal
            if (from !in state.blocks.indices || to !in state.blocks.indices) return@markDirty state
            if (from !in page.startIndex until page.endExclusive ||
                to !in page.startIndex until page.endExclusive
            ) {
                return@markDirty state
            }
            val blocks = state.blocks.toMutableList()
            val item = blocks.removeAt(from)
            blocks.add(to, item)
            state.copy(blocks = blocks, focusedBlockIndex = to)
        }
    }

    fun removeBlock(index: Int) {
        markDirty { state ->
            if (index !in state.blocks.indices) return@markDirty state
            val blocks = state.blocks.toMutableList()
            blocks.removeAt(index)
            if (blocks.isEmpty() || blocks.last() !is WriteBlock.Paragraph) {
                blocks += WriteBlock.Paragraph("")
            }
            state.copy(
                blocks = blocks,
                focusedBlockIndex = index.coerceIn(0, blocks.lastIndex)
            )
        }
    }

    fun setSaveFormat(format: BookRepository.WriteSaveFormat) {
        _uiState.update { it.copy(saveFormat = format, dirty = true) }
    }

    fun setUploadToRemote(value: Boolean) {
        _uiState.update { it.copy(uploadToRemote = value) }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(savedMessage = null, savedBookId = null) }
    }

    fun consumeStatus() {
        _uiState.update { it.copy(statusHint = null) }
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
        markDirty { s ->
            val blocks = s.blocks.toMutableList()
            blocks += WriteBlock.Paragraph(heading)
            blocks += WriteBlock.Paragraph("")
            s.copy(
                blocks = blocks,
                focusedBlockIndex = blocks.lastIndex
            )
        }
        val rebuilt = _uiState.value
        val pageIdx = rebuilt.pages.indexOfFirst {
            rebuilt.focusedBlockIndex in it.startIndex until it.endExclusive
        }.coerceAtLeast(0)
        _uiState.update {
            it.copy(pageIndex = pageIdx, statusHint = "新章节：$heading（空白页）")
        }
    }

    fun insertImage(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                val relative = bookRepository.importWriteImage(contentResolver, uri)
                markDirty { state ->
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
                    state.copy(
                        blocks = blocks,
                        saveFormat = BookRepository.WriteSaveFormat.PDF,
                        focusedBlockIndex = insertAt + 1
                    )
                }
                val rebuilt = _uiState.value
                val pageIdx = rebuilt.pages.indexOfFirst { page ->
                    rebuilt.focusedBlockIndex - 1 in page.startIndex until page.endExclusive
                }.coerceAtLeast(0)
                _uiState.update { it.copy(pageIndex = pageIdx) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "插入图片失败") }
            }
        }
    }

    fun resolveImageFile(path: String): File? = bookRepository.resolveWriteImage(path)

    fun save(navigateAway: Boolean = true) {
        viewModelScope.launch {
            persist(navigateAway = navigateAway, fromAuto = false)
        }
    }

    private fun startAutoSaveLoop() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(18_000L)
                val s = _uiState.value
                if (s.dirty && !s.saving && !s.loading && !WriteBlocks.contentLooksEmpty(s.blocks)) {
                    persist(navigateAway = false, fromAuto = true)
                }
            }
        }
    }

    private suspend fun persist(navigateAway: Boolean, fromAuto: Boolean) {
        val state = _uiState.value
        if (state.saving || state.autoSaving) return
        _uiState.update {
            it.copy(
                saving = !fromAuto,
                autoSaving = fromAuto,
                error = null
            )
        }
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
            if (!fromAuto && state.uploadToRemote) {
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
            baselineChars = WriteBlocks.charCount(_uiState.value.blocks)
            _uiState.update {
                it.copy(
                    saving = false,
                    autoSaving = false,
                    bookId = id,
                    dirty = false,
                    sessionGain = 0,
                    statusHint = if (fromAuto) "已自动保存" else null,
                    savedBookId = if (navigateAway && !fromAuto) id else null,
                    savedMessage = if (navigateAway && !fromAuto) message else null
                )
            }
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    saving = false,
                    autoSaving = false,
                    error = if (fromAuto) null else (e.message ?: "保存失败"),
                    statusHint = if (fromAuto) "自动保存失败：${e.message ?: "未知错误"}" else null
                )
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
            githubBooksUploader: GithubBooksUploader,
            deepSeekClient: DeepSeekClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WriteViewModel(
                    bookId,
                    folderId,
                    bookRepository,
                    themePreferences,
                    githubBooksUploader,
                    deepSeekClient
                ) as T
            }
        }
    }
}
