package com.studypath.app.ui.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.db.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    viewModel: CreatePlanViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            com.studypath.app.ui.theme.PaperTopBar(
                title = "手动创建计划",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = viewModel.planTitle,
                onValueChange = { viewModel.planTitle = it },
                label = { Text("计划标题（必填）") },
                placeholder = { Text("例如：Python 数据分析入门") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.planOverview,
                onValueChange = { viewModel.planOverview = it },
                label = { Text("总体说明（可选）") },
                placeholder = { Text("这个计划的目标和大致思路") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            viewModel.phases.forEachIndexed { pIdx, phase ->
                PhaseDraftCard(
                    phase = phase,
                    onRemovePhase = { viewModel.removePhase(pIdx) },
                    onAddTask = { viewModel.addTask(pIdx, it) },
                    onRemoveTask = { viewModel.removeTask(pIdx, it) },
                )
            }

            OutlinedButton(onClick = { viewModel.addPhase() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加阶段")
            }

            viewModel.errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { viewModel.save(onCreated) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("保存计划") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseDraftCard(
    phase: DraftPhase,
    onRemovePhase: () -> Unit,
    onAddTask: (DraftTask) -> Unit,
    onRemoveTask: (Int) -> Unit,
) {
    var editingTitle by remember { mutableStateOf(phase.title) }
    var editingSummary by remember { mutableStateOf(phase.summary) }
    var showTaskEditor by remember { mutableStateOf(false) }

    // 同步编辑回草稿
    phase.title = editingTitle
    phase.summary = editingSummary

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阶段", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemovePhase, modifier = Modifier.width(32.dp)) {
                    Icon(Icons.Default.Close, "删除阶段", Modifier.width(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = editingTitle, onValueChange = { editingTitle = it },
                label = { Text("阶段标题") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = editingSummary, onValueChange = { editingSummary = it },
                label = { Text("阶段目标（可选）") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            phase.tasks.forEachIndexed { tIdx, task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title.ifBlank { "（未命名任务）" },
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append("约 ${task.estimatedMinutes} 分钟")
                                if (task.date.isNotBlank()) append(" · ${task.date}")
                                if (task.deliverable.isNotBlank()) append(" · 交付物✓")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemoveTask(tIdx) }) {
                        Icon(Icons.Default.Close, "删除任务", Modifier.width(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = { showTaskEditor = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加小任务")
            }
        }
    }

    if (showTaskEditor) {
        TaskDraftDialog(
            onDismiss = { showTaskEditor = false },
            onSave = {
                onAddTask(it)
                showTaskEditor = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDraftDialog(
    onDismiss: () -> Unit,
    onSave: (DraftTask) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("") }
    var deliverable by remember { mutableStateOf("") }
    var checkpoint by remember { mutableStateOf("") }
    var pitfall by remember { mutableStateOf("") }
    var resource by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(60) }
    var date by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = if (date.isNotBlank()) {
                LocalDate.parse(date).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            } else System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加小任务", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("任务标题（必填）") },
                    placeholder = { Text("例如：刷完牛客SQL基础查询10题") },
                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = detail, onValueChange = { detail = it },
                    label = { Text("学什么/做什么（分号列举）") },
                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall, minLines = 1)
                if (showMore) {
                    OutlinedTextField(value = method, onValueChange = { method = it },
                        label = { Text("怎么做：执行步骤") },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = deliverable, onValueChange = { deliverable = it },
                        label = { Text("交付物：完成后的产出") },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = checkpoint, onValueChange = { checkpoint = it },
                        label = { Text("达标标准") },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = pitfall, onValueChange = { pitfall = it },
                        label = { Text("常见坑（可选）") },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = resource, onValueChange = { resource = it },
                        label = { Text("推荐资源（可选）") },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("预计时长", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { if (minutes > 15) minutes -= 15 }) { Text("-") }
                    Text("  $minutes 分钟  ", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { if (minutes < 480) minutes += 15 }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("计划日期", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(date.ifBlank { "选择日期（可选）" })
                    }
                    if (date.isNotBlank()) {
                        IconButton(onClick = { date = "" }, modifier = Modifier.width(32.dp)) {
                            Icon(Icons.Default.Close, "清除日期", Modifier.width(14.dp))
                        }
                    }
                }
                TextButton(onClick = { showMore = !showMore }) {
                    Text(if (showMore) "收起可选字段" else "填写更多字段（怎么做/交付物/达标…）")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(
                    DraftTask(
                        title = title, detail = detail, method = method,
                        deliverable = deliverable, checkpoint = checkpoint,
                        pitfall = pitfall, resource = resource,
                        estimatedMinutes = minutes, date = date,
                    )
                )
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
