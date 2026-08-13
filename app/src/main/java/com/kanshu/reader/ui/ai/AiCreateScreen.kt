package com.kanshu.reader.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
fun AiCreateScreen(
    viewModel: AiCreateViewModel,
    onBack: () -> Unit,
    onSessionReady: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
    }

    LaunchedEffect(state.createdSessionId) {
        val id = state.createdSessionId ?: return@LaunchedEffect
        viewModel.consumeCreated()
        onSessionReady(id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.step == AiCreateStep.REVIEW) {
                                viewModel.backToDraft()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        if (state.step == AiCreateStep.DRAFT) "新建角色聊天" else "确认提示词",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.step) {
                AiCreateStep.DRAFT -> {
                    Text(
                        "写得越具体，优化后的提示词越贴合。三项都必填。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.scenario,
                        onValueChange = viewModel::setScenario,
                        label = { Text("场景想法") },
                        placeholder = { Text("例：雨夜便利店，只剩我们两个…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        enabled = !state.optimizing
                    )
                    OutlinedTextField(
                        value = state.character,
                        onValueChange = viewModel::setCharacter,
                        label = { Text("对方角色人设") },
                        placeholder = { Text("姓名、性格、说话方式、与你的关系…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        enabled = !state.optimizing
                    )
                    OutlinedTextField(
                        value = state.userPersona,
                        onValueChange = viewModel::setUserPersona,
                        label = { Text("我的人设") },
                        placeholder = { Text("你是谁、性格、此刻想做什么…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        enabled = !state.optimizing
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::optimize,
                        enabled = !state.optimizing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.optimizing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在优化提示词…")
                            }
                        } else {
                            Text("生成完整提示词")
                        }
                    }
                }

                AiCreateStep.REVIEW -> {
                    Text(
                        "可直接改下面内容，满意后再开始。开始后 AI 会先发开场白。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        singleLine = true,
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.creating
                    )
                    OutlinedTextField(
                        value = state.openingHint,
                        onValueChange = viewModel::setOpeningHint,
                        label = { Text("开场指令") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.creating
                    )
                    OutlinedTextField(
                        value = state.systemPrompt,
                        onValueChange = viewModel::setSystemPrompt,
                        label = { Text("完整系统提示词") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        enabled = !state.creating
                    )
                    Button(
                        onClick = viewModel::startChat,
                        enabled = !state.creating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.creating) "创建中（锁定角色外观）…" else "开始对话")
                    }
                }
            }
        }
    }
}
