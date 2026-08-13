package com.kanshu.reader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import com.kanshu.reader.BuildConfig
import com.kanshu.reader.update.AppUpdateInfo
import com.kanshu.reader.update.UpdateChecker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.KanshuApp
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.prefs.AppThemeMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenBook: (Long) -> Unit
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as KanshuApp
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }
    var importing by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        // 进入书架时静默检查一次
        UpdateChecker.check().onSuccess { info ->
            if (info != null) updateInfo = info
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = app.container.bookRepository.importBook(context.contentResolver, uri)
            importing = false
            result.onSuccess {
                snackbarHostState.showSnackbar("导入成功")
            }.onFailure {
                snackbarHostState.showSnackbar(it.message ?: "导入失败")
            }
        }
    }

    fun checkUpdate(manual: Boolean) {
        scope.launch {
            checkingUpdate = true
            val result = UpdateChecker.check()
            checkingUpdate = false
            result.onSuccess { info ->
                if (info == null) {
                    if (manual) {
                        snackbarHostState.showSnackbar("已是最新版 v${BuildConfig.VERSION_NAME}")
                    }
                } else {
                    updateInfo = info
                }
            }.onFailure {
                if (manual) {
                    snackbarHostState.showSnackbar(it.message ?: "检查更新失败")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("看书", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (themeMode == AppThemeMode.DAY) "白天模式" else "黑夜模式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { checkUpdate(manual = true) },
                        enabled = !checkingUpdate
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "检查更新"
                        )
                    }
                    IconButton(onClick = viewModel::toggleTheme) {
                        Icon(
                            imageVector = if (themeMode == AppThemeMode.DAY) {
                                Icons.Default.DarkMode
                            } else {
                                Icons.Default.LightMode
                            },
                            contentDescription = "切换昼夜模式"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!importing) {
                        picker.launch(
                            arrayOf(
                                "text/plain",
                                "application/epub+zip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "导入书籍")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (books.isEmpty()) {
            EmptyLibrary(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        onClick = { onOpenBook(book.id) },
                        onLongClick = { pendingDelete = book }
                    )
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除书籍") },
            text = { Text("确定删除「${book.title}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(book)
                        pendingDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 v${info.versionName}") },
            text = {
                Text(
                    info.releaseNotes.ifBlank { "有可用更新，建议升级以获得更好阅读体验。" }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        UpdateChecker.openDownload(context, info.apkUrl)
                        updateInfo = null
                    }
                ) {
                    Text("下载更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("稍后")
                }
            }
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text("书架空空如也", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "点击右下角按钮导入 TXT / EPUB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: BookEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${book.author} · ${book.format}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = progressLabel(book),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onLongClick) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}

private fun progressLabel(book: BookEntity): String {
    if (book.lastReadAt == 0L) return "未开始阅读"
    val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return "读到第 ${book.chapterIndex + 1} 章 · ${df.format(Date(book.lastReadAt))}"
}
