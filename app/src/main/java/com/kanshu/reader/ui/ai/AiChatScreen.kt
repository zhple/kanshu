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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kanshu.reader.data.db.AiMessageEntity
import java.io.File

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

    LaunchedEffect(visibleMessages.size, state.streamingText, state.generatingImageFor) {
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
                            MessageBubble(
                                message = msg.copy(content = content.ifBlank { "…" }),
                                generatingImage = state.generatingImageFor == msg.id,
                                canGenerateImage = msg.role == "assistant" &&
                                    msg.content.isNotBlank() &&
                                    !state.streaming &&
                                    state.generatingImageFor == null,
                                onGenerateImage = { viewModel.generateSceneImage(msg.id) }
                            )
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
                                ),
                                generatingImage = false,
                                canGenerateImage = false,
                                onGenerateImage = {}
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
private fun MessageBubble(
    message: AiMessageEntity,
    generatingImage: Boolean,
    canGenerateImage: Boolean,
    onGenerateImage: () -> Unit
) {
    val mine = message.role == "user"
    val hasImage = message.imagePath.isNotBlank() && File(message.imagePath).exists()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
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

        if (!mine && message.id > 0) {
            if (hasImage) {
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model = File(message.imagePath),
                    contentDescription = "场景图",
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (generatingImage) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "生成场景图中…（约数秒到十几秒）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (canGenerateImage) {
                TextButton(
                    onClick = onGenerateImage,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("生成场景图")
                }
            }
        }
    }
}
