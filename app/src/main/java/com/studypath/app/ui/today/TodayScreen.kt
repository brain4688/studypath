package com.studypath.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.db.TaskEntity
import com.studypath.app.data.reminder.describeDay
import com.studypath.app.ui.components.DeliveryDialogShared
import com.studypath.app.ui.components.TaskCoachDialogShared
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenPlan: (Long) -> Unit,
) {
    val tasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val coach by viewModel.coach.collectAsStateWithLifecycle()
    var deliveryFor by remember { mutableStateOf<TaskEntity?>(null) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            com.studypath.app.ui.theme.PaperTopBar(
                title = "今日任务",
                subtitle = "${today.monthValue}月${today.dayOfMonth}日 · 共 ${tasks.size} 项 · " +
                    "未完成 ${tasks.count { it.task.progress < 100 }} 项",
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🌤", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(10.dp))
                Text("今天没有安排任务", style = MaterialTheme.typography.titleMedium)
                Text(
                    "去主页打开一个计划看看进度，\n或者休息一天吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tasks, key = { it.task.id }) { row ->
                TodayTaskCard(
                    row = row,
                    onProgress = { p -> viewModel.setProgress(row.task.id, p) },
                    onAskAi = { viewModel.openCoach(row) },
                    onDelivery = { deliveryFor = row.task },
                    onOpenPlan = onOpenPlan,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    deliveryFor?.let { task ->
        DeliveryDialogShared(
            taskId = task.id,
            taskTitle = task.title,
            onDismiss = { deliveryFor = null },
            onAdd = { text, imagePath -> viewModel.addDelivery(task.id, task.planId, text, imagePath) },
            onDelete = { viewModel.deleteDelivery(it) },
            observe = viewModel::observeDeliveries,
        )
    }
    when (val c = coach) {
        is CoachUi.Open -> TaskCoachDialogShared(
            state = c,
            onDismiss = { viewModel.closeCoach() },
            onAsk = { viewModel.askCoach(it) },
        )
        else -> Unit
    }
}

@Composable
private fun TodayTaskCard(
    row: com.studypath.app.data.db.TodayTaskRow,
    onProgress: (Int) -> Unit,
    onAskAi: () -> Unit,
    onDelivery: () -> Unit,
    onOpenPlan: (Long) -> Unit,
) {
    val task = row.task
    var sliderValue by remember(task.id, task.progress) { mutableStateOf(task.progress.toFloat()) }
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            // 计划/阶段 归属行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.planTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(
                    onClick = { onOpenPlan(task.planId) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) { Text("进入计划 ›", style = MaterialTheme.typography.labelSmall) }
            }
            Text(
                "阶段：${row.phaseTitle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 主题
            Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            // 目标
            if (task.detail.isNotBlank()) {
                Text(
                    "目标：${task.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 步骤
            if (task.method.isNotBlank()) {
                Text(
                    "步骤：${task.method}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (task.deliverable.isNotBlank()) {
                Text(
                    "交付物：${task.deliverable}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 操作行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                TextButton(onClick = onAskAi,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.School, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("问 AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDelivery,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("成果打卡", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        sliderValue = if (sliderValue >= 100f) 0f else 100f
                        onProgress(sliderValue.toInt())
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(if (task.progress >= 100) "撤销完成" else "完成打卡",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            // 滑条
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onProgress(sliderValue.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (task.progress >= 100) "✓ ${describeDay(task.scheduledDate) ?: ""}"
                    else "${task.progress}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
