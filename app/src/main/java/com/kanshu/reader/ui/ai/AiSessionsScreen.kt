package com.kanshu.reader.ui.ai

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Forum
import com.kanshu.reader.data.db.AiSessionEntity
import com.kanshu.reader.ui.components.KanshuAmbientBackground
import com.kanshu.reader.ui.components.KanshuEmptyState
import com.kanshu.reader.ui.components.kanshuFloat
import com.kanshu.reader.ui.components.kanshuListCard
import com.kanshu.reader.ui.components.kanshuPressScale
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSessionsScreen(
    viewModel: AiSessionsViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val keys by viewModel.keysStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showKeyDialog by remember { mutableStateOf(false) }
    var deepseekInput by remember { mutableStateOf("") }
    var siliconInput by remember { mutableStateOf("") }
    var minimaxInput by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<AiSessionEntity?>(null) }

    val statusText = buildString {
        append(if (keys.hasDeepseek) "DeepSeek✓" else "DeepSeek未配")
        append(" · ")
        append(if (keys.hasSiliconflow) "生图✓" else "生图未配")
        append(" · ")
        append(if (keys.hasMinimax) "语音✓" else "语音未配")
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
                    Column {
                        Text("角色场景聊天", fontWeight = FontWeight.Bold)
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        deepseekInput = ""
                        siliconInput = ""
                        minimaxInput = ""
                        showKeyDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "API Key")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.kanshuFloat(),
                onClick = {
                    if (!keys.hasDeepseek) {
                        deepseekInput = ""
                        siliconInput = ""
                        minimaxInput = ""
                        showKeyDialog = true
                        scope.launch { snackbarHostState.showSnackbar("开始前请先填写 DeepSeek API Key") }
                    } else {
                        onCreate()
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建聊天")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        KanshuAmbientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        if (sessions.isEmpty()) {
            KanshuEmptyState(
                icon = Icons.Default.Forum,
                title = "还没有角色聊天",
                subtitle = "点右下角新建：写场景、对方人设和我的人设",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onDelete = { pendingDelete = session }
                    )
                }
            }
        }
        }
    }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("API Key 设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "只保存在本机。聊天 DeepSeek · 生图硅基流动 · 朗读 MiniMax。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = deepseekInput,
                        onValueChange = { deepseekInput = it },
                        singleLine = true,
                        label = { Text("DeepSeek API Key") },
                        placeholder = {
                            Text(if (keys.hasDeepseek) "已保存，留空则不改" else "必填")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = siliconInput,
                        onValueChange = { siliconInput = it },
                        singleLine = true,
                        label = { Text("硅基流动 API Key") },
                        placeholder = {
                            Text(if (keys.hasSiliconflow) "已保存，留空则不改" else "生图用")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minimaxInput,
                        onValueChange = { minimaxInput = it },
                        singleLine = true,
                        label = { Text("MiniMax API Key") },
                        placeholder = {
                            Text(if (keys.hasMinimax) "已保存，留空则不改" else "朗读用，需手动填写")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val d = deepseekInput.trim().takeIf { it.isNotEmpty() }
                        val s = siliconInput.trim().takeIf { it.isNotEmpty() }
                        val m = minimaxInput.trim().takeIf { it.isNotEmpty() }
                        if (d == null && s == null && m == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("请至少填写一项 Key")
                            }
                            return@TextButton
                        }
                        if (!keys.hasDeepseek && d == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("请填写 DeepSeek API Key")
                            }
                            return@TextButton
                        }
                        viewModel.saveKeys(d, s, m) { result ->
                            result.onSuccess {
                                showKeyDialog = false
                                scope.launch { snackbarHostState.showSnackbar("已保存") }
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
                    if (keys.hasDeepseek) {
                        TextButton(onClick = {
                            viewModel.clearDeepseekApiKey()
                            scope.launch { snackbarHostState.showSnackbar("已清除 DeepSeek Key") }
                        }) { Text("清DeepSeek") }
                    }
                    if (keys.hasSiliconflow) {
                        TextButton(onClick = {
                            viewModel.clearSiliconflowApiKey()
                            scope.launch { snackbarHostState.showSnackbar("已清除生图 Key") }
                        }) { Text("清生图") }
                    }
                    if (keys.hasMinimax) {
                        TextButton(onClick = {
                            viewModel.clearMinimaxApiKey()
                            scope.launch { snackbarHostState.showSnackbar("已清除语音 Key") }
                        }) { Text("清语音") }
                    }
                    TextButton(onClick = { showKeyDialog = false }) { Text("取消") }
                }
            }
        )
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定删除「${session.title}」吗？聊天记录会一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        pendingDelete = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SessionRow(
    session: AiSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val time = remember(session.updatedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .kanshuPressScale(interactionSource)
            .kanshuListCard()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}
