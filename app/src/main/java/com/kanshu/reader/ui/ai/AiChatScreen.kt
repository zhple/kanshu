package com.kanshu.reader.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanshu.reader.data.db.AiMessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val visibleMessages = remember(messages) {
        messages.filter { it.role == "user" || it.role == "assistant" }
    }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    LaunchedEffect(visibleMessages.size, state.streamingText) {
        if (visibleMessages.isNotEmpty() || state.streamingText.isNotEmpty()) {
            listState.animateScrollToItem(
                (visibleMessages.size + if (state.streamingText.isNotEmpty()) 1 else 0)
                    .coerceAtLeast(1) - 1
            )
        }
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
                        state.session?.title ?: "角色聊天",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
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
        ) {
            if (state.session == null && state.error == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleMessages, key = { it.id }) { msg ->
                        // 正在流式更新的空 assistant 气泡用 streamingText 覆盖显示
                        val content = if (
                            state.streaming &&
                            msg.role == "assistant" &&
                            msg.id == visibleMessages.lastOrNull { it.role == "assistant" }?.id &&
                            state.streamingText.isNotEmpty()
                        ) {
                            state.streamingText
                        } else {
                            msg.content
                        }
                        if (content.isNotBlank() || msg.role == "user") {
                            MessageBubble(message = msg.copy(content = content.ifBlank { "…" }))
                        }
                    }
                    if (state.streaming && visibleMessages.none { it.role == "assistant" && it.content.isEmpty() } &&
                        state.streamingText.isNotEmpty() &&
                        visibleMessages.lastOrNull()?.role != "assistant"
                    ) {
                        item(key = "streaming") {
                            MessageBubble(
                                message = AiMessageEntity(
                                    id = -1,
                                    sessionId = 0,
                                    role = "assistant",
                                    content = state.streamingText
                                )
                            )
                        }
                    }
                    if (state.streaming && state.streamingText.isEmpty()) {
                        item(key = "thinking") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::setInput,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 140.dp),
                        placeholder = {
                            Text(
                                if (!state.openingDone || state.streaming) "等待开场…" else "说点什么…"
                            )
                        },
                        enabled = state.openingDone && !state.streaming
                    )
                    IconButton(
                        onClick = viewModel::send,
                        enabled = state.openingDone && !state.streaming && state.input.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AiMessageEntity) {
    val mine = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (mine) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = if (mine) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
