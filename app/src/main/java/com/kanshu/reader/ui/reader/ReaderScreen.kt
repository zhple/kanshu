package com.kanshu.reader.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.ui.theme.readerPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private val ReaderFontSize = 18.sp
private val ReaderHorizontalPadding = 20.dp
private val ReaderVerticalPadding = 12.dp
/** Reserve space so pages don't sit under status/nav/toolbars */
private val TopContentReserve = 72.dp
private val BottomContentReserve = 72.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val palette = readerPalette(themeMode)
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = palette.text
                )
            }
            state.error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error ?: "出错了", color = palette.text)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onBack) { Text("返回") }
                }
            }
            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val contentWidthPx = with(density) {
                        (maxWidth - ReaderHorizontalPadding * 2).toPx().roundToInt()
                    }
                    val contentHeightPx = with(density) {
                        (maxHeight - TopContentReserve - BottomContentReserve - ReaderVerticalPadding * 2)
                            .toPx()
                            .roundToInt()
                            .coerceAtLeast(1)
                    }
                    val textSizePx = with(density) { ReaderFontSize.toPx() }

                    LaunchedEffect(contentWidthPx, contentHeightPx, textSizePx, state.chapters.size) {
                        if (state.chapters.isNotEmpty()) {
                            viewModel.onPageSizeReady(contentWidthPx, contentHeightPx, textSizePx)
                        }
                    }

                    if (state.pages.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = palette.text
                        )
                    } else {
                        val pagerState = rememberPagerState(
                            initialPage = state.pageIndex.coerceIn(0, state.pages.lastIndex),
                            pageCount = { state.pages.size }
                        )

                        LaunchedEffect(state.pageIndex, state.pages.size) {
                            val target = state.pageIndex.coerceIn(0, state.pages.lastIndex)
                            if (pagerState.currentPage != target) {
                                pagerState.scrollToPage(target)
                            }
                        }

                        LaunchedEffect(pagerState, state.pages.size) {
                            snapshotFlow { pagerState.settledPage }
                                .distinctUntilChanged()
                                .collect { page ->
                                    viewModel.setPageIndex(page)
                                }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val w = size.width
                                            when {
                                                offset.x < w * 0.28f -> viewModel.previousPage()
                                                offset.x > w * 0.72f -> viewModel.nextPage()
                                                else -> viewModel.toggleControls()
                                            }
                                        }
                                    )
                                },
                            beyondViewportPageCount = 1
                        ) { page ->
                            val readerPage = state.pages[page]
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        start = ReaderHorizontalPadding,
                                        end = ReaderHorizontalPadding,
                                        top = TopContentReserve,
                                        bottom = BottomContentReserve
                                    )
                                    .padding(vertical = ReaderVerticalPadding)
                            ) {
                                Text(
                                    text = readerPage.text,
                                    color = palette.text,
                                    fontSize = ReaderFontSize,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    if (state.showControls) {
                        ReaderTopBar(
                            title = state.title,
                            themeMode = themeMode,
                            onBack = onBack,
                            onToc = viewModel::openToc,
                            onToggleTheme = viewModel::toggleTheme,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        val current = state.pages.getOrNull(state.pageIndex)
                        ReaderBottomBar(
                            canPrev = state.pageIndex > 0,
                            canNext = state.pageIndex < state.pages.lastIndex,
                            pageLabel = if (state.pages.isEmpty()) {
                                "—"
                            } else {
                                "${state.pageIndex + 1} / ${state.pages.size}"
                            },
                            chapterLabel = current?.chapterTitle.orEmpty(),
                            onPrev = viewModel::previousPage,
                            onNext = viewModel::nextPage,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    if (state.showToc) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeToc,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Text(
                text = "目录",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(state.chapters) { index, chapter ->
                    Text(
                        text = chapter.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectChapter(index) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        color = if (state.pages.getOrNull(state.pageIndex)?.chapterIndex == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    themeMode: AppThemeMode,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onToc) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录")
        }
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (themeMode == AppThemeMode.DAY) {
                    Icons.Default.DarkMode
                } else {
                    Icons.Default.LightMode
                },
                contentDescription = "切换昼夜"
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    canPrev: Boolean,
    canNext: Boolean,
    pageLabel: String,
    chapterLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (chapterLabel.isNotBlank()) {
            Text(
                text = chapterLabel,
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
            Text(pageLabel, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onNext, enabled = canNext) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一页")
            }
        }
    }
}
