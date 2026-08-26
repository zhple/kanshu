package com.kanshu.reader.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.ui.theme.readerPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfReaderScreen(
    viewModel: PdfReaderViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }
    var jumpError by remember { mutableStateOf<String?>(null) }
    val palette = readerPalette(themeMode)
    val density = LocalDensity.current
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx().roundToInt()
    }

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
            state.pageCount <= 0 -> {
                Text(
                    "空 PDF",
                    modifier = Modifier.align(Alignment.Center),
                    color = palette.text
                )
            }
            else -> {
                val pagerState = rememberPagerState(
                    initialPage = state.pageIndex.coerceIn(0, state.pageCount - 1),
                    pageCount = { state.pageCount }
                )

                LaunchedEffect(state.pageIndex) {
                    if (pagerState.currentPage != state.pageIndex) {
                        pagerState.scrollToPage(state.pageIndex)
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
                    PdfPageImage(
                        pageIndex = page,
                        maxWidthPx = screenWidthPx,
                        render = viewModel::renderPage,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (state.showControls) 72.dp else 12.dp,
                                bottom = if (state.showControls) 72.dp else 12.dp
                            )
                    )
                }

                if (state.showControls) {
                    PdfTopBar(
                        title = state.title,
                        themeMode = themeMode,
                        onBack = onBack,
                        onToggleTheme = viewModel::toggleTheme,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    PdfBottomBar(
                        canPrev = state.pageIndex > 0,
                        canNext = state.pageIndex < state.pageCount - 1,
                        pageLabel = "${state.pageIndex + 1} / ${state.pageCount}",
                        onPrev = viewModel::previousPage,
                        onNext = viewModel::nextPage,
                        onPageClick = {
                            jumpInput = (state.pageIndex + 1).toString()
                            jumpError = null
                            showJumpDialog = true
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (showJumpDialog && state.pageCount > 0) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("跳转到页码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("共 ${state.pageCount} 页，当前第 ${state.pageIndex + 1} 页")
                    OutlinedTextField(
                        value = jumpInput,
                        onValueChange = {
                            jumpInput = it.filter { ch -> ch.isDigit() }.take(6)
                            jumpError = null
                        },
                        singleLine = true,
                        label = { Text("页码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = jumpError != null,
                        supportingText = jumpError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val page = jumpInput.toIntOrNull()
                        val max = state.pageCount
                        when {
                            page == null || page < 1 || page > max ->
                                jumpError = "请输入 1–$max 之间的页码"
                            else -> {
                                viewModel.setPageIndex(page - 1)
                                showJumpDialog = false
                            }
                        }
                    }
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PdfPageImage(
    pageIndex: Int,
    maxWidthPx: Int,
    render: suspend (Int, Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex, maxWidthPx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, maxWidthPx) {
        val next = render(pageIndex, maxWidthPx)
        bitmap = next
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp == null || bmp.isRecycled) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "第 ${pageIndex + 1} 页",
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Composable
private fun PdfTopBar(
    title: String,
    themeMode: AppThemeMode,
    onBack: () -> Unit,
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
private fun PdfBottomBar(
    canPrev: Boolean,
    canNext: Boolean,
    pageLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, enabled = canPrev) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一页")
        }
        TextButton(onClick = onPageClick) {
            Text(pageLabel, style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一页")
        }
    }
}
