package com.kanshu.reader.ui.write

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                OutlinedTextField(
                    value = state.content,
                    onValueChange = viewModel::setContent,
                    label = { Text("正文") },
                    placeholder = { Text("随便写点什么…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    minLines = 12
                )
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
