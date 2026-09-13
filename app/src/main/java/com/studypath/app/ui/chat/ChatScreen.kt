package com.studypath.app.ui.chat

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.core.ai.AiPlan
import com.studypath.app.data.db.ChatMessageEntity
import com.studypath.app.data.reminder.describeDay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 学习规划师") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state is ChatUiState.Planning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    TextButton(
                        onClick = { viewModel.newConversation() },
                        enabled = messages.isNotEmpty() && state !is ChatUiState.Planning,
                    ) { Text("新对话") }
                    Button(
                        onClick = { viewModel.generatePlan() },
                        enabled = messages.isNotEmpty() && state !is ChatUiState.Planning,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("生成计划") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().imePadding().padding(padding)) {
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

            // 底部：状态提示 + 输入行
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (val s = state) {
                    is ChatUiState.Error -> Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is ChatUiState.Planning -> Text(
                        "AI 正在根据你们的对话制定计划…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    else -> Text(
                        "聊清楚需求后，点右上角【生成计划】",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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

    // 生成后的预览确认
    val preview = state as? ChatUiState.Preview
    if (preview != null) {
        PlanPreviewDialog(
            plan = preview.plan,
            onConfirm = { viewModel.confirmPreview() },
            onDismiss = { viewModel.dismissPreview() },
        )
    }
}

/** 生成结果的预览确认：用户确认后才正式创建计划 */
@Composable
private fun PlanPreviewDialog(
    plan: AiPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预览学习计划") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(plan.title, style = MaterialTheme.typography.titleMedium)
                    if (plan.overview.isNotBlank()) {
                        Text(
                            plan.overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                plan.phases.forEach { phase ->
                    item(key = "phase_${phase.title}") {
                        Column {
                            Text(
                                "阶段 · ${phase.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            if (phase.summary.isNotBlank()) {
                                Text(
                                    phase.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    items(phase.tasks) { t ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                t.scheduledDate?.let { date ->
                                    val day = runCatching {
                                        describeDay(LocalDate.parse(date).toEpochDay())
                                    }.getOrNull()
                                    if (day != null) {
                                        Text(
                                            day,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                }
                                Text(
                                    "约 ${t.estimatedMinutes} 分钟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(t.title, style = MaterialTheme.typography.bodyMedium)
                            if (t.deliverable.isNotBlank()) {
                                Text(
                                    "交付物：${t.deliverable}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "确认后将正式创建计划并开始打卡；不满意可继续和 AI 沟通调整。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("确认创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续聊聊") } },
    )
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
private fun EmptyChatHint(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("💬", style = MaterialTheme.typography.displayMedium)
        Text("和 AI 顾问聊聊你的学习需求", style = MaterialTheme.typography.titleMedium)
        Text(
            "它会一步步问你：想学什么、基础如何、\n每天有多少时间、目标是什么…\n聊清楚后点右上角【生成计划】",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
