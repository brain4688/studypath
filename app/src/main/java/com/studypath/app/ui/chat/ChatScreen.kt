package com.studypath.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.db.ChatMessageEntity

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onCreated: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新消息到达时滚到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(state) {
        val s = state
        if (s is ChatUiState.PlanReady) {
            viewModel.consumeState()
            onCreated(s.planId)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyChatHint(onOpenSettings = onOpenSettings) }
            }
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }
        }

        // 底部操作与输入区
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.newConversation() }) { Text("新对话") }
                Box(modifier = Modifier.weight(1f)) {
                    when (val s = state) {
                        is ChatUiState.Planning -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.widthIn(max = 20.dp))
                                Text("  正在根据对话生成计划…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ChatUiState.Error -> {
                            Text(
                                s.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        else -> {
                            Text(
                                "聊清楚需求后点右边",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Button(
                    onClick = { viewModel.generatePlan() },
                    enabled = state !is ChatUiState.Planning && messages.isNotEmpty(),
                ) { Text("生成学习计划") }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("描述你想学什么…") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && state !is ChatUiState.Planning,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            border = if (isUser) null else androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outline
            ),
            shape = RoundedCornerShape(
                topStart = 14.dp, topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun EmptyChatHint(onOpenSettings: () -> Unit) {    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("💬", style = MaterialTheme.typography.displayMedium)
        Text("和 AI 顾问聊聊你的学习需求", style = MaterialTheme.typography.titleMedium)
        Text(
            "它会一步步问你：想学什么、基础如何、\n每天有多少时间、目标是什么…\n聊清楚后点击【生成学习计划】",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
