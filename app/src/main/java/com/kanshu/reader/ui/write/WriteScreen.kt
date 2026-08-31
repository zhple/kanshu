package com.kanshu.reader.ui.write

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.kanshu.reader.reader.WriteBlocks
import kotlinx.coroutines.flow.distinctUntilChanged
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

    LaunchedEffect(state.statusHint) {
        val msg = state.statusHint ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeStatus()
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

    if (state.showOutline) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowOutline(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "章节大纲",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (state.outline.isEmpty()) {
                    Text(
                        "还没有章节标题。点「下一章」插入「第N章」后会出现在这里。",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(state.outline, key = { "${it.blockIndex}-${it.title}" }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.jumpToOutline(item) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "第 ${item.pageIndex + 1} 页 · 约 ${item.charCount} 字",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (!state.focusMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                if (state.bookId != null) "编辑文稿" else "写点东西",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                buildString {
                                    append(state.charCount)
                                    append(" 字")
                                    if (state.sessionGain > 0) {
                                        append(" · 本会话 +")
                                        append(state.sessionGain)
                                    }
                                    when {
                                        state.autoSaving -> append(" · 自动保存中")
                                        state.dirty -> append(" · 未保存")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setShowOutline(true) }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "大纲")
                        }
                        IconButton(onClick = viewModel::toggleFocusMode) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "专注模式")
                        }
                        TextButton(
                            onClick = { viewModel.save(navigateAway = true) },
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
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!state.loading && state.pages.isNotEmpty()) {
                Column {
                    WriteStatsBar(
                        charCount = state.charCount,
                        sessionGain = state.sessionGain,
                        focusMode = state.focusMode,
                        onExitFocus = viewModel::toggleFocusMode
                    )
                    WritePageBar(
                        pageLabel = "${state.pageIndex + 1} / ${state.pages.size}",
                        pageTitle = state.pages.getOrNull(state.pageIndex)?.title.orEmpty(),
                        canPrev = state.pageIndex > 0,
                        canNext = true,
                        onPrev = viewModel::goPrevPage,
                        onNext = viewModel::goNextPage
                    )
                }
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
                AnimatedVisibility(visible = !state.focusMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
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
                            "用下方按钮翻页 · 写满自动下一页 · 约 18 秒自动保存",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                    userScrollEnabled = false
                ) { page ->
                    val pageInfo = state.pages.getOrNull(page)
                    if (pageInfo == null || pageInfo.size <= 0) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("这一页还是空的，写点内容吧")
                        }
                    } else {
                        val pageBlocks = state.blocks.subList(pageInfo.startIndex, pageInfo.endExclusive)
                        WritePageEditor(
                            pageBlocks = pageBlocks,
                            pageText = WriteBlocks.pagePlainText(state.blocks, pageInfo),
                            globalStartIndex = pageInfo.startIndex,
                            enabled = !state.saving,
                            resolveImage = viewModel::resolveImageFile,
                            onPageTextChange = viewModel::updatePagePlainText,
                            onCharBudgetChange = viewModel::setPageCharBudget,
                            onImageWidth = viewModel::setImageWidth,
                            onDelete = viewModel::removeBlock
                        )
                    }
                }

                AnimatedVisibility(visible = !state.focusMode) {
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
}

@Composable
private fun WriteStatsBar(
    charCount: Int,
    sessionGain: Int,
    focusMode: Boolean,
    onExitFocus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "全文 $charCount 字" + if (sessionGain > 0) " · 本会话 +$sessionGain" else "",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        if (focusMode) {
            IconButton(onClick = onExitFocus) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "退出专注")
            }
        }
    }
}

@Composable
private fun WritePageEditor(
    pageBlocks: List<WriteBlock>,
    pageText: String,
    globalStartIndex: Int,
    enabled: Boolean,
    resolveImage: (String) -> java.io.File?,
    onPageTextChange: (String, Int) -> Unit,
    onCharBudgetChange: (Int) -> Unit,
    onImageWidth: (Int, Float) -> Unit,
    onDelete: (Int) -> Unit
) {
    val density = LocalDensity.current
    val lineHeight = 28.sp
    var charBudget by remember { mutableIntStateOf(WriteBlocks.PAGE_CHAR_BUDGET) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pageBlocks.forEachIndexed { localIndex, block ->
            if (block is WriteBlock.Image) {
                val globalIndex = globalStartIndex + localIndex
                ImageBlockEditor(
                    path = block.path,
                    widthPercent = block.widthPercent,
                    file = resolveImage(block.path),
                    enabled = enabled,
                    onWidthChange = { onImageWidth(globalIndex, it) },
                    onDelete = { onDelete(globalIndex) }
                )
            }
        }

        BasicTextField(
            value = pageText,
            onValueChange = { onPageTextChange(it, charBudget) },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = lineHeight,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val linePx = with(density) { lineHeight.toPx() }
                    val charPx = with(density) { 16.sp.toPx() * 0.95f }
                    val padPx = with(density) { 24.dp.toPx() }
                    val usableH = (size.height - padPx).coerceAtLeast(linePx)
                    val usableW = (size.width - padPx).coerceAtLeast(charPx * 12)
                    val lines = (usableH / linePx).toInt().coerceAtLeast(1)
                    val charsPerLine = (usableW / charPx).toInt().coerceAtLeast(12)
                    val next = (lines * charsPerLine * 0.88).toInt().coerceIn(80, WriteBlocks.PAGE_CHAR_BUDGET)
                    if (kotlin.math.abs(charBudget - next) >= 8) {
                        charBudget = next
                        onCharBudgetChange(next)
                    }
                },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (pageText.isEmpty()) {
                        Text(
                            "在这里写…写满会自动翻页；下方按钮也可翻页",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = lineHeight)
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun ImageBlockEditor(
    path: String,
    widthPercent: Float,
    file: java.io.File?,
    enabled: Boolean,
    onWidthChange: (Float) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "图片",
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
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        Text(
            "宽度 ${(widthPercent * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = widthPercent,
            onValueChange = onWidthChange,
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
