package com.kanshu.reader.ui.write

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kanshu.reader.data.repo.BookRepository

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text("将在文末插入「第${state.chapterCount + 1}章」。可填写章节名（可选）。")
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
                ) {
                    Text("插入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChapterDialog = false }) {
                    Text("取消")
                }
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
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(if (state.saving) "保存中…" else "保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    singleLine = true,
                    label = { Text("标题") },
                    placeholder = { Text("未命名文稿") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("保存格式", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.saveFormat == BookRepository.WriteSaveFormat.TXT,
                        onClick = {
                            viewModel.setSaveFormat(BookRepository.WriteSaveFormat.TXT)
                        },
                        label = { Text("TXT 文本") }
                    )
                    FilterChip(
                        selected = state.saveFormat == BookRepository.WriteSaveFormat.PDF,
                        onClick = {
                            viewModel.setSaveFormat(BookRepository.WriteSaveFormat.PDF)
                        },
                        label = { Text("PDF 文档") }
                    )
                }
                Text(
                    if (state.saveFormat == BookRepository.WriteSaveFormat.PDF) {
                        "PDF 适合带插图；书架里用 PDF 阅读器打开，仍可再编辑。"
                    } else {
                        "TXT 适合纯文字；插图会变成文字占位。含图时建议选 PDF。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showChapterDialog = true },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LibraryAdd, contentDescription = null)
                            Text("下一章")
                        }
                    }
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Text("插入图片")
                        }
                    }
                }
                if (state.chapterCount > 0) {
                    Text(
                        "已有 ${state.chapterCount} 个章节标题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = state.content,
                    onValueChange = viewModel::setContent,
                    label = { Text("正文") },
                    placeholder = { Text("写完一章后点「下一章」；可插入图片…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    minLines = 12
                )

                if (state.insertedImages.isNotEmpty()) {
                    Text("已插入图片", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.insertedImages.forEach { path ->
                            val file = viewModel.resolveImageFile(path)
                            AsyncImage(
                                model = file,
                                contentDescription = path,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
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
                    Column {
                        Text("同时保存到远程仓库")
                        Text(
                            if (hasGithubToken) {
                                "勾选后会上传到 GitHub，朋友同步就能看到"
                            } else {
                                "需先在书架设置里填写 Token"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "默认只保存在本机书架，可随时再打开继续改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
