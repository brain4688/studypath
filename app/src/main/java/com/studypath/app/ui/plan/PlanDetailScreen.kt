package com.studypath.app.ui.plan

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.db.PhaseWithTasks
import com.studypath.app.data.db.TaskEntity
import com.studypath.app.data.reminder.describeDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    viewModel: PlanDetailViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val replanState by viewModel.replanState.collectAsStateWithLifecycle()

    var showReplanDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(replanState) {
        if (replanState is ReplanState.Done) {
            viewModel.consumeReplanState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.plan?.title ?: "计划详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.SaveAlt, "导出计划")
                    }
                    IconButton(onClick = { showReplanDialog = true }) {
                        Icon(Icons.Default.Autorenew, "AI 调整计划")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "删除计划")
                    }
                },
            )
        },
    ) { padding ->
        if (ui.plan == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OverviewCard(ui = ui) }
            ui.phases.forEach { phase ->
                item(key = "phase_${phase.phase.id}") { PhaseHeader(phase) }
                itemsIndexed(phase.tasks, key = { _, t -> "task_${t.id}" }) { idx, task ->
                    TaskItem(
                        task = task,
                        index = idx,
                        onProgress = { p -> viewModel.setTaskProgress(task.id, p) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showReplanDialog) {
        ReplanDialog(
            state = replanState,
            onDismiss = { showReplanDialog = false; viewModel.consumeReplanState() },
            onConfirm = { reason -> viewModel.replan(reason) },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除计划？") },
            text = { Text("将删除该计划的全部阶段与任务进度，不可恢复。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deletePlan(onDeleted) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
        )
    }
    if (showExportDialog) {
        ExportDialog(
            planTitle = ui.plan?.title ?: "学习计划",
            onDismiss = { showExportDialog = false },
            onExportXlsx = { viewModel.exportXlsx() },
            onExportJson = { viewModel.exportJson() },
        )
    }
}

@Composable
private fun OverviewCard(ui: PlanDetailUi) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "总进度 · TOTAL PROGRESS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(4.dp))
            Text(ui.plan?.title ?: "", style = MaterialTheme.typography.titleLarge)
            if (!ui.plan?.overview.isNullOrBlank()) {
                Text(
                    ui.plan!!.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.studypath.app.ui.theme.PaperProgressBar(
                    percent = ui.percent,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${ui.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text(
                if (ui.todayCount > 0) "今日待学 · ${ui.todayCount} 项 · 约 ${ui.todayMinutes} 分钟"
                else "今天没有安排的任务，休息一下 🌿",
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.todayCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhaseHeader(phase: PhaseWithTasks) {
    val days = phase.tasks.map { it.scheduledDate }.filter { it > 0 }
    val rangeText = if (days.isNotEmpty()) {
        val s = java.time.LocalDate.ofEpochDay(days.min())
        val e = java.time.LocalDate.ofEpochDay(days.max())
        val fmt = { d: java.time.LocalDate -> "${d.monthValue}.${d.dayOfMonth}" }
        if (days.size == 1) fmt(s) else "${fmt(s)} - ${fmt(e)}"
    } else ""
    Column {
        com.studypath.app.ui.theme.SectionLabel(
            if (rangeText.isBlank()) "阶段 · ${phase.phase.title}"
            else "阶段 · ${phase.phase.title}（$rangeText）"
        )
        if (phase.phase.summary.isNotBlank()) {
            Text(
                phase.phase.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskItem(task: TaskEntity, index: Int, onProgress: (Int) -> Unit) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    var sliderValue by remember(task.id, task.progress) { mutableStateOf(task.progress.toFloat()) }
    val hasDetail = task.method.isNotBlank() || task.checkpoint.isNotBlank() || task.resource.isNotBlank()
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            // 序号行 + 日期 + 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "T${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(9.dp))
                describeDay(task.scheduledDate)?.let { day ->
                    Text(
                        day,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day == "今天") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    when {
                        task.progress >= 100 -> "已完成"
                        task.progress > 0 -> "进行中 ${task.progress}%"
                        else -> "未开始"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        task.progress >= 100 -> MaterialTheme.colorScheme.primary
                        task.progress > 0 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${task.estimatedMinutes} 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 标题
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            // 学什么
            if (task.detail.isNotBlank()) {
                Text(
                    task.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 交付物（玉绿）
            if (task.deliverable.isNotBlank()) {
                MetaLine("交付物", task.deliverable, valueColor = MaterialTheme.colorScheme.primary)
            }
            // 展开：怎么做 / 达标 / 资源
            if (hasDetail || task.detail.length > 60) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        (if (expanded) "收起细节 " else "查看执行细节 ") + if (expanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (expanded) {
                    Column {
                        if (task.method.isNotBlank()) MetaLine("怎么做", task.method)
                        if (task.checkpoint.isNotBlank()) MetaLine("达标", task.checkpoint, valueColor = MaterialTheme.colorScheme.primary)
                        if (task.resource.isNotBlank()) MetaLine("资源", task.resource)
                    }
                }
            } else if (task.resource.isNotBlank()) {
                MetaLine("资源", task.resource)
            }
            // 常见坑（琥珀警示块）
            if (task.pitfall.isNotBlank()) {
                Text(
                    "⚠ 卡点  " + task.pitfall,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            // 进度滑条
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onProgress(sliderValue.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {
                        sliderValue = if (sliderValue >= 100f) 0f else 100f
                        onProgress(sliderValue.toInt())
                    },
                    label = { Text(if (sliderValue >= 100f) "重置" else "完成") },
                )
            }
        }
    }
}

/** 手册风 meta kv 行：等宽小标签 + 内容 */
@Composable
private fun MetaLine(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReplanDialog(    state: ReplanState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("让 AI 调整计划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 会根据你各任务的实际完成进度，重新安排剩余学习内容（已完成的记录会保留）。")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("为什么调整？（可选）") },
                    placeholder = { Text("例如：最近时间变少 / 某部分比预想难 / 想加快节奏") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (state) {
                    is ReplanState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("正在重新规划…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is ReplanState.Error -> Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = state !is ReplanState.Loading) {
                Text("开始调整")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = state !is ReplanState.Loading) { Text("关闭") }
        },
    )
}

@Composable
private fun ExportDialog(
    planTitle: String,
    onDismiss: () -> Unit,
    onExportXlsx: () -> ByteArray?,
    onExportJson: () -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val saveXlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val bytes = onExportXlsx()
            if (bytes == null) { toast("导出失败：计划为空"); return@rememberLauncherForActivityResult }
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                        .isSuccess
                }
                toast(if (ok) "已导出 Excel（.xlsx）" else "保存失败")
            }
        }
    }
    val saveJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri != null && json != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                }
                toast(if (ok) "已导出 JSON（含进度，可重新导入）" else "保存失败")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出计划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("「$planTitle」", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val name = (planTitle.take(20).ifBlank { "学习计划" }) + ".xlsx"
                    saveXlsx.launch(name)
                }) { Text("导出 Excel（.xlsx，可用 Excel/WPS 打开）") }
                TextButton(onClick = {
                    pendingJson = onExportJson()
                    if (pendingJson == null) toast("导出失败：计划为空")
                    else saveJson.launch((planTitle.take(20).ifBlank { "学习计划" }) + ".json")
                }) { Text("导出 JSON（含任务进度，可导入本 App）") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
