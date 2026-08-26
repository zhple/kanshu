package com.kanshu.reader.ui.write

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kanshu.reader.data.repo.BookRepository
import com.kanshu.reader.reader.WriteBlock
import kotlinx.coroutines.flow.distinctUntilChanged
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WriteScreen(
    viewModel: WriteViewModel,
    onBack: () -> Unit,
    onSaved: (bookId: Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasGithubToken by viewModel.hasGithubToken.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showChapterDialog by remember { mutableStateOf(false) }
    var chapterSubtitle by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.insertImage(context.contentResolver, uri)
    }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
    }

    LaunchedEffect(state.savedMessage, state.savedBookId) {
        val msg = state.savedMessage ?: return@LaunchedEffect
        val id = state.savedBookId ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSaved()
        onSaved(id)
    }

    if (showChapterDialog) {
        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = { Text("开始下一章") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("插入「第${state.chapterCount + 1}章」并尽量翻到新页。")
                    OutlinedTextField(
                        value = chapterSubtitle,
                        onValueChange = { chapterSubtitle = it },
                        singleLine = true,
                        label = { Text("章节名（可选）") },
                        placeholder = { Text("例如：初遇") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startNextChapter(chapterSubtitle)
                        chapterSubtitle = ""
                        showChapterDialog = false
                    }
                ) { Text("插入") }
            },
            dismissButton = {
                TextButton(onClick = { showChapterDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        if (state.bookId != null) "编辑文稿" else "写点东西",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.saving && !state.loading
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Text(if (state.saving) "保存中…" else "保存")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!state.loading && state.pages.isNotEmpty()) {
                WritePageBar(
                    pageLabel = "${state.pageIndex + 1} / ${state.pages.size}",
                    pageTitle = state.pages.getOrNull(state.pageIndex)?.title.orEmpty(),
                    canPrev = state.pageIndex > 0,
                    canNext = state.pageIndex < state.pages.lastIndex,
                    onPrev = { viewModel.setPageIndex(state.pageIndex - 1) },
                    onNext = { viewModel.setPageIndex(state.pageIndex + 1) }
                )
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    singleLine = true,
                    label = { Text("标题") },
                    placeholder = { Text("未命名文稿") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.saveFormat == BookRepository.WriteSaveFormat.TXT,
                        onClick = {
                            viewModel.setSaveFormat(BookRepository.WriteSaveFormat.TXT)
                        },
                        label = { Text("TXT") }
                    )
                    FilterChip(
                        selected = state.saveFormat == BookRepository.WriteSaveFormat.PDF,
                        onClick = {
                            viewModel.setSaveFormat(BookRepository.WriteSaveFormat.PDF)
                        },
                        label = { Text("PDF") }
                    )
                    OutlinedButton(
                        onClick = { showChapterDialog = true },
                        enabled = !state.saving
                    ) {
                        Icon(Icons.Default.LibraryAdd, contentDescription = null)
                        Text("下一章", modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !state.saving
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Text("插图", modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Text(
                    "左右翻页编辑；长按图片左侧把手可拖动调整位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val pagerState = rememberPagerState(
                    initialPage = state.pageIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)),
                    pageCount = { state.pages.size.coerceAtLeast(1) }
                )
                LaunchedEffect(state.pageIndex, state.pages.size) {
                    val target = state.pageIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))
                    if (pagerState.currentPage != target) {
                        pagerState.scrollToPage(target)
                    }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { viewModel.setPageIndex(it) }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    beyondViewportPageCount = 0,
                    userScrollEnabled = true
                ) { page ->
                    val pageInfo = state.pages.getOrNull(page)
                    if (pageInfo == null || pageInfo.size <= 0) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("这一页还是空的，写点内容或插入图片吧")
                        }
                    } else {
                        val pageBlocks = state.blocks.subList(pageInfo.startIndex, pageInfo.endExclusive)
                        WritePageEditor(
                            pageBlocks = pageBlocks,
                            globalStartIndex = pageInfo.startIndex,
                            enabled = !state.saving,
                            resolveImage = viewModel::resolveImageFile,
                            onParagraphChange = viewModel::updateParagraph,
                            onFocus = viewModel::setFocusedBlock,
                            onImageWidth = viewModel::setImageWidth,
                            onDelete = viewModel::removeBlock,
                            onMoveWithinPage = viewModel::moveWithinPage
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = state.uploadToRemote,
                        onCheckedChange = viewModel::setUploadToRemote,
                        enabled = !state.saving
                    )
                    Text(
                        if (hasGithubToken) "同时上传到远程仓库" else "上传需先配置 Token",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WritePageEditor(
    pageBlocks: List<WriteBlock>,
    globalStartIndex: Int,
    enabled: Boolean,
    resolveImage: (String) -> java.io.File?,
    onParagraphChange: (Int, String) -> Unit,
    onFocus: (Int) -> Unit,
    onImageWidth: (Int, Float) -> Unit,
    onDelete: (Int) -> Unit,
    onMoveWithinPage: (Int, Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMoveWithinPage(from.index, to.index)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = pageBlocks,
            key = { _, block -> block.id }
        ) { localIndex, block ->
            val globalIndex = globalStartIndex + localIndex
            ReorderableItem(reorderableState, key = block.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
                when (block) {
                    is WriteBlock.Paragraph -> {
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { onParagraphChange(globalIndex, it) },
                            placeholder = {
                                Text(if (localIndex == 0) "在这一页写正文…" else "继续写…")
                            },
                            enabled = enabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .onFocusChanged { if (it.isFocused) onFocus(globalIndex) },
                            minLines = 4
                        )
                    }
                    is WriteBlock.Image -> {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ImageBlockEditor(
                                path = block.path,
                                widthPercent = block.widthPercent,
                                file = resolveImage(block.path),
                                enabled = enabled,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                onWidthChange = { onImageWidth(globalIndex, it) },
                                onDelete = { onDelete(globalIndex) },
                                onFocus = { onFocus(globalIndex) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBlockEditor(
    path: String,
    widthPercent: Float,
    file: java.io.File?,
    enabled: Boolean,
    dragHandleModifier: Modifier,
    onWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    onFocus: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "长按拖动",
                modifier = dragHandleModifier.padding(end = 8.dp)
            )
            Text(
                "图片 · 长按左侧拖动",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = file,
                contentDescription = path,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(widthPercent.coerceIn(0.3f, 1f))
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            )
        }
        Text(
            "宽度 ${(widthPercent * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = widthPercent,
            onValueChange = {
                onFocus()
                onWidthChange(it)
            },
            valueRange = 0.3f..1f,
            enabled = enabled
        )
    }
}

@Composable
private fun WritePageBar(
    pageLabel: String,
    pageTitle: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (pageTitle.isNotBlank()) {
            Text(
                text = pageTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, enabled = canPrev) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一页")
            }
            Text(pageLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onNext, enabled = canNext) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一页")
            }
        }
    }
}
