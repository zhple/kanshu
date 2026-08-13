package com.kanshu.reader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.BuildConfig
import com.kanshu.reader.KanshuApp
import com.kanshu.reader.data.db.BookEntity
import com.kanshu.reader.data.db.FolderEntity
import com.kanshu.reader.data.prefs.AppThemeMode
import com.kanshu.reader.update.AppUpdateInfo
import com.kanshu.reader.update.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File
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
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val currentFolderName by viewModel.currentFolderName.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    val hasGithubToken by viewModel.hasGithubToken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as KanshuApp
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updatingApk by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(0f) }
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }
    var pendingDeleteBook by remember { mutableStateOf<BookEntity?>(null) }
    var pendingDeleteFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var pendingMoveBook by remember { mutableStateOf<BookEntity?>(null) }
    var pendingRenameBook by remember { mutableStateOf<BookEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var bookMenu by remember { mutableStateOf<BookEntity?>(null) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var pendingUploadBook by remember { mutableStateOf<BookEntity?>(null) }


    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingInstallFile
        if (file != null && UpdateChecker.canInstallPackages(context)) {
            runCatching { UpdateChecker.installApk(context, file) }
                .onFailure { err ->
                    scope.launch {
                        snackbarHostState.showSnackbar(err.message ?: "无法打开安装界面")
                    }
                }
            pendingInstallFile = null
        } else if (!UpdateChecker.canInstallPackages(context)) {
            scope.launch {
                snackbarHostState.showSnackbar("仍未允许安装未知应用，无法自动更新")
            }
        }
    }

    fun startInAppUpdate(info: AppUpdateInfo) {
        scope.launch {
            updatingApk = true
            updateProgress = 0f
            val result = UpdateChecker.downloadApk(context, info) { p ->
                updateProgress = p
            }
            updatingApk = false
            result.onSuccess { file ->
                updateInfo = null
                if (!UpdateChecker.canInstallPackages(context)) {
                    pendingInstallFile = file
                    snackbarHostState.showSnackbar("请允许「看书」安装未知应用，然后返回继续安装")
                    installPermissionLauncher.launch(
                        UpdateChecker.installPermissionSettingsIntent(context)
                    )
                } else {
                    runCatching { UpdateChecker.installApk(context, file) }
                        .onFailure {
                            snackbarHostState.showSnackbar(it.message ?: "无法打开安装界面")
                        }
                }
            }.onFailure {
                snackbarHostState.showSnackbar(it.message ?: "下载失败")
            }
        }
    }

    val inFolder = currentFolderId != null

    LaunchedEffect(Unit) {
        UpdateChecker.check().onSuccess { info ->
            if (info != null) updateInfo = info
        }
    }

    LaunchedEffect(syncMessage) {
        val msg = syncMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSyncMessage()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = app.container.bookRepository.importBook(
                contentResolver = context.contentResolver,
                uri = uri,
                folderId = currentFolderId
            )
            importing = false
            result.onSuccess {
                snackbarHostState.showSnackbar("导入成功（本地书）")
            }.onFailure {
                snackbarHostState.showSnackbar(it.message ?: "导入失败")
            }
        }
    }

    fun checkUpdate(manual: Boolean) {
        scope.launch {
            checkingUpdate = true
            viewModel.syncDefaultBooks(manual = true)
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

    fun requestUpload(book: BookEntity) {
        if (!hasGithubToken) {
            pendingUploadBook = book
            tokenInput = ""
            showTokenDialog = true
            bookMenu = null
            return
        }
        bookMenu = null
        viewModel.uploadBookToRemote(book)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (inFolder) {
                        IconButton(onClick = viewModel::openRoot) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = currentFolderName ?: "看书",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (inFolder) {
                                "文件夹 · ${if (themeMode == AppThemeMode.DAY) "白天" else "黑夜"}"
                            } else if (themeMode == AppThemeMode.DAY) {
                                "白天模式"
                            } else {
                                "黑夜模式"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        tokenInput = ""
                        showTokenDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "仓库上传设置")
                    }
                    if (!inFolder) {
                        IconButton(onClick = {
                            folderNameInput = ""
                            showCreateFolder = true
                        }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
                        }
                    }
                    IconButton(onClick = { viewModel.syncDefaultBooks(manual = true) }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "同步仓库书")
                    }
                    IconButton(
                        onClick = { checkUpdate(manual = true) },
                        enabled = !checkingUpdate
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = "检查更新")
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
                                "application/pdf",
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
        val showFolders = !inFolder && folders.isNotEmpty() && sourceFilter != BookSourceFilter.LOCAL
        val empty = books.isEmpty() && !showFolders

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = sourceFilter == BookSourceFilter.ALL,
                        onClick = { viewModel.setSourceFilter(BookSourceFilter.ALL) },
                        label = { Text("全部") }
                    )
                    FilterChip(
                        selected = sourceFilter == BookSourceFilter.REMOTE,
                        onClick = { viewModel.setSourceFilter(BookSourceFilter.REMOTE) },
                        label = { Text("仓库书") }
                    )
                    FilterChip(
                        selected = sourceFilter == BookSourceFilter.LOCAL,
                        onClick = { viewModel.setSourceFilter(BookSourceFilter.LOCAL) },
                        label = { Text("我的上传") }
                    )
                }

                if (empty) {
                    EmptyLibrary(
                        inFolder = inFolder,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!inFolder && sourceFilter != BookSourceFilter.LOCAL) {
                            items(folders, key = { "f-${it.id}" }) { folder ->
                                FolderRow(
                                    folder = folder,
                                    bookCount = viewModel.bookCountInFolder(folder.id, allBooks),
                                    onClick = { viewModel.openFolder(folder) },
                                    onDelete = { pendingDeleteFolder = folder }
                                )
                            }
                        }
                        items(books, key = { "b-${it.id}" }) { book ->
                            BookRow(
                                book = book,
                                onClick = { onOpenBook(book.id) },
                                onMenu = { bookMenu = book }
                            )
                        }
                    }
                }
            }

            if (uploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在上传到 GitHub…", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }

    bookMenu?.let { book ->
        AlertDialog(
            onDismissRequest = { bookMenu = null },
            title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (book.isRemote) {
                            "仓库书 · 可改名、上传覆盖，或移动/删除本地副本"
                        } else {
                            "本地书 · 可改名，或上传到远程仓库供同步"
                        }
                    )
                    TextButton(
                        onClick = {
                            renameInput = book.title
                            pendingRenameBook = book
                            bookMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("修改书名")
                    }
                    TextButton(
                        onClick = { requestUpload(book) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (book.isRemote) "重新上传到仓库" else "上传到远程仓库")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMoveBook = book
                        bookMenu = null
                    }
                ) { Text("移动") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingDeleteBook = book
                            bookMenu = null
                        }
                    ) { Text("删除") }
                    TextButton(onClick = { bookMenu = null }) { Text("取消") }
                }
            }
        )
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = {
                showTokenDialog = false
                pendingUploadBook = null
            },
            title = { Text("上传权限") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "上传需要 GitHub Token，只保存在本机，不会进仓库或安装包。创建时勾选 repo 权限即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "状态：${if (hasGithubToken) "已配置" else "未配置"}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        singleLine = true,
                        label = { Text("GitHub Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveGithubToken(tokenInput) { result ->
                            result.onSuccess {
                                showTokenDialog = false
                                val book = pendingUploadBook
                                pendingUploadBook = null
                                scope.launch { snackbarHostState.showSnackbar("Token 已保存到本机") }
                                if (book != null) {
                                    viewModel.uploadBookToRemote(book)
                                }
                            }.onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar(it.message ?: "保存失败")
                                }
                            }
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (hasGithubToken) {
                        TextButton(
                            onClick = {
                                viewModel.clearGithubToken()
                                scope.launch { snackbarHostState.showSnackbar("已清除 Token") }
                            }
                        ) { Text("清除") }
                    }
                    TextButton(
                        onClick = {
                            showTokenDialog = false
                            pendingUploadBook = null
                        }
                    ) { Text("取消") }
                }
            }
        )
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("新建分类文件夹") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (hasGithubToken) {
                            "已配置 Token：创建后会同步到远程仓库，朋友同步仓库书后也能看到这个分类。"
                        } else {
                            "未配置 Token：先在本机创建。配置 Token 后，把仓库书移入分类即可同步到远程。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        singleLine = true,
                        label = { Text("分类名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolder(folderNameInput) { result ->
                            result.onSuccess { msg ->
                                showCreateFolder = false
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }.onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar(it.message ?: "创建失败")
                                }
                            }
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false }) { Text("取消") }
            }
        )
    }

    pendingRenameBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRenameBook = null },
            title = { Text("修改书名") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("书名") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameBook(book.id, renameInput) { result ->
                            result.onSuccess {
                                pendingRenameBook = null
                                scope.launch { snackbarHostState.showSnackbar("已改名") }
                            }.onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar(it.message ?: "改名失败")
                                }
                            }
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRenameBook = null }) { Text("取消") }
            }
        )
    }

    pendingMoveBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingMoveBook = null },
            title = { Text("移动「${book.title}」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.moveBook(book.id, null) { result ->
                                result.onSuccess { msg ->
                                    pendingMoveBook = null
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(it.message ?: "移动失败")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (book.isRemote) "仓库书（默认分类）" else "根目录（未分类）")
                    }
                    folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                viewModel.moveBook(book.id, folder.id) { result ->
                                    result.onSuccess { msg ->
                                        pendingMoveBook = null
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }.onFailure {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(it.message ?: "移动失败")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingMoveBook = null }) { Text("关闭") }
            }
        )
    }

    pendingDeleteBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBook = null },
            title = { Text("删除书籍") },
            text = {
                Text(
                    if (book.isRemote) {
                        "确定删除「${book.title}」吗？这是仓库书，删除后下次同步可能重新下载。"
                    } else {
                        "确定删除「${book.title}」吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(book)
                        pendingDeleteBook = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBook = null }) { Text("取消") }
            }
        )
    }

    pendingDeleteFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFolder = null },
            title = { Text("删除文件夹") },
            text = { Text("删除「${folder.name}」后，其中的书会回到默认位置，不会删除书籍。若已配置 Token，远程分类也会同步删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id) { result ->
                            result.onSuccess { msg ->
                                pendingDeleteFolder = null
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }.onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar(it.message ?: "删除失败")
                                }
                            }
                        }
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFolder = null }) { Text("取消") }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {
                if (!updatingApk) updateInfo = null
            },
            title = { Text("发现新版本 v${info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info.releaseNotes.ifBlank { "有可用更新，建议升级。" })
                    if (updatingApk) {
                        Text("正在下载安装包… ${(updateProgress * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = { updateProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "将在应用内下载，完成后自动打开安装界面。系统会再确认一次安装。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { startInAppUpdate(info) },
                    enabled = !updatingApk
                ) { Text(if (updatingApk) "下载中…" else "立即更新") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            UpdateChecker.openDownloadPage(context, info.apkUrl)
                        },
                        enabled = !updatingApk
                    ) { Text("浏览器下载") }
                    TextButton(
                        onClick = { updateInfo = null },
                        enabled = !updatingApk
                    ) { Text("稍后") }
                }
            }
        )
    }
}

@Composable
private fun EmptyLibrary(inFolder: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (inFolder) "这个文件夹还是空的" else "书架空空如也",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (inFolder) {
                    "点右下角导入书籍，或从根目录把书移进来"
                } else {
                    "可同步仓库书，也可本地导入后上传到远程仓库"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderEntity,
    bookCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$bookCount 本书",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除文件夹")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: BookEntity,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    val icon: ImageVector = if (book.format.equals("PDF", ignoreCase = true)) {
        Icons.Default.PictureAsPdf
    } else {
        Icons.AutoMirrored.Filled.MenuBook
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (book.isRemote) "仓库" else "本地",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (book.isRemote) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (book.isRemote) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
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
        IconButton(onClick = onMenu) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多")
        }
    }
}

private fun progressLabel(book: BookEntity): String {
    if (book.lastReadAt == 0L) return "未开始阅读"
    val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return if (book.format.equals("PDF", ignoreCase = true)) {
        "读到第 ${book.chapterIndex + 1} 页 · ${df.format(Date(book.lastReadAt))}"
    } else {
        "读到第 ${book.chapterIndex + 1} 章 · ${df.format(Date(book.lastReadAt))}"
    }
}
