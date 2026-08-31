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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
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
import com.kanshu.reader.data.ai.DeepSeekClient
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
    val hasDeepseekKey by viewModel.hasDeepseekKey.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showChapterDialog by remember { mutableStateOf(false) }
    var chapterSubtitle by remember { mutableStateOf("") }
    var showAiDialog by remember { mutableStateOf(false) }
    var aiHint by remember { mutableStateOf("") }
    var pendingAiMode by remember { mutableStateOf(DeepSeekClient.WriteAssistMode.CONTINUE) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var goalInput by remember { mutableStateOf("") }

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

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = {
                Text(
                    when (pendingAiMode) {
                        DeepSeekClient.WriteAssistMode.CONTINUE -> "AI 续写"
                        DeepSeekClient.WriteAssistMode.POLISH -> "AI 润色"
                        DeepSeekClient.WriteAssistMode.EXPAND -> "AI 扩写"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (hasDeepseekKey) {
                            "基于当前聚焦段落调用 DeepSeek。结果会先预览，确认后再写入。"
                        } else {
                            "请先在「角色场景聊天」设置里填写 DeepSeek API Key。"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = aiHint,
                        onValueChange = { aiHint = it },
                        label = { Text("补充要求（可选）") },
                        placeholder = { Text("例如：偏虐、多对话、写到天黑") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAiDialog = false
                        viewModel.requestAiAssist(pendingAiMode, aiHint)
                        aiHint = ""
                    },
                    enabled = hasDeepseekKey && !state.aiBusy
                ) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = { showAiDialog = false }) { Text("取消") }
            }
        )
    }

    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("每日写作目标") },
            text = {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it.filter(Char::isDigit).take(5) },
                    label = { Text("字数") },
                    supportingText = { Text("当前 ${state.dailyGoal} 字/天") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        goalInput.toIntOrNull()?.let { viewModel.setDailyGoal(it) }
                        showGoalDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("取消") }
            }
        )
    }

    if (state.aiPreview != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAiPreview,
            title = { Text("AI 结果预览") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(state.aiPreview.orEmpty())
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::applyAiPreview) { Text("写入文稿") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAiPreview) { Text("丢弃") }
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
                        dailyDone = state.dailyDone,
                        dailyGoal = state.dailyGoal,
                        focusMode = state.focusMode,
                        onGoalClick = {
                            goalInput = state.dailyGoal.toString()
                            showGoalDialog = true
                        },
                        onExitFocus = viewModel::toggleFocusMode
                    )
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

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingAiMode = DeepSeekClient.WriteAssistMode.CONTINUE
                                    showAiDialog = true
                                },
                                enabled = !state.aiBusy
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Text("续写", modifier = Modifier.padding(start = 4.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingAiMode = DeepSeekClient.WriteAssistMode.POLISH
                                    showAiDialog = true
                                },
                                enabled = !state.aiBusy
                            ) { Text("润色") }
                            OutlinedButton(
                                onClick = {
                                    pendingAiMode = DeepSeekClient.WriteAssistMode.EXPAND
                                    showAiDialog = true
                                },
                                enabled = !state.aiBusy
                            ) { Text("扩写") }
                            if (state.aiBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .height(24.dp)
                                        .width(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        Text(
                            "左右翻页 · 长按图片把手拖动 · 约 18 秒自动保存 · 专注模式可隐藏工具栏",
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
                            enabled = !state.saving && !state.aiBusy,
                            resolveImage = viewModel::resolveImageFile,
                            onParagraphChange = viewModel::updateParagraph,
                            onFocus = viewModel::setFocusedBlock,
                            onImageWidth = viewModel::setImageWidth,
                            onDelete = viewModel::removeBlock,
                            onMoveWithinPage = viewModel::moveWithinPage
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
    dailyDone: Int,
    dailyGoal: Int,
    focusMode: Boolean,
    onGoalClick: () -> Unit,
    onExitFocus: () -> Unit
) {
    val progress = if (dailyGoal <= 0) 0f else (dailyDone.toFloat() / dailyGoal).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "全文 $charCount · 今日 $dailyDone / $dailyGoal",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onGoalClick)
            )
            if (sessionGain > 0) {
                Text(
                    "+$sessionGain",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
            }
            if (focusMode) {
                IconButton(onClick = onExitFocus) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "退出专注")
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
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
