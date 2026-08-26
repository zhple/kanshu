package com.kanshu.reader.ui.write

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kanshu.reader.data.repo.BookRepository
import com.kanshu.reader.reader.WriteBlock
import kotlin.math.roundToInt

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
                    Text("将在当前光标附近插入「第${state.chapterCount + 1}章」。可填写章节名（可选）。")
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
                        "图片会直接显示在正文里，可调大小和上下位置；保存为 PDF 后书架里也能看到图。"
                    } else {
                        "TXT 不含真实图片。有插图时请选 PDF。"
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

                Text("正文", style = MaterialTheme.typography.labelLarge)
                state.blocks.forEachIndexed { index, block ->
                    when (block) {
                        is WriteBlock.Paragraph -> {
                            OutlinedTextField(
                                value = block.text,
                                onValueChange = { viewModel.updateParagraph(index, it) },
                                placeholder = {
                                    Text(
                                        if (index == 0) "在这里写正文…" else "继续写…"
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 96.dp)
                                    .onFocusChanged { focus ->
                                        if (focus.isFocused) viewModel.setFocusedBlock(index)
                                    },
                                minLines = if (index == 0) 5 else 3
                            )
                        }
                        is WriteBlock.Image -> {
                            ImageBlockEditor(
                                path = block.path,
                                widthPercent = block.widthPercent,
                                file = viewModel.resolveImageFile(block.path),
                                canMoveUp = index > 0,
                                canMoveDown = index < state.blocks.lastIndex,
                                enabled = !state.saving,
                                onWidthChange = { viewModel.setImageWidth(index, it) },
                                onMoveUp = { viewModel.moveBlock(index, -1) },
                                onMoveDown = { viewModel.moveBlock(index, 1) },
                                onDelete = { viewModel.removeBlock(index) },
                                onFocus = { viewModel.setFocusedBlock(index) }
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
            }
        }
    }
}

@Composable
private fun ImageBlockEditor(
    path: String,
    widthPercent: Float,
    file: java.io.File?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onWidthChange: (Float) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onFocus: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("图片", style = MaterialTheme.typography.labelLarge)
            Row {
                IconButton(onClick = { onFocus(); onMoveUp() }, enabled = enabled && canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = { onFocus(); onMoveDown() }, enabled = enabled && canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
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
                    .heightIn(max = 360.dp)
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
            onValueChange = {
                onFocus()
                onWidthChange(it)
            },
            valueRange = 0.3f..1f,
            enabled = enabled
        )
    }
}
