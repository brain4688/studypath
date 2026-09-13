package com.studypath.app.ui.plan

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.api.ChatMessage
import com.studypath.app.data.db.DeliveryEntity
import com.studypath.app.data.db.PhaseWithTasks
import com.studypath.app.data.db.TaskEntity
import com.studypath.app.data.reminder.describeDay
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    var showReminderDialog by remember { mutableStateOf(false) }
    var showDeliveryFor by remember { mutableStateOf<TaskEntity?>(null) }
    val context = LocalContext.current
    val expandedPhases by viewModel.expandedPhases.collectAsStateWithLifecycle()

    // 折叠默认态：首次进入只展开当前阶段
    LaunchedEffect(ui.phases) {
        if (expandedPhases == null && ui.phases.isNotEmpty()) {
            val current = ui.phases.firstOrNull { it.phase.orderIndex == ui.currentPhaseOrder }
            viewModel.setAllExpanded(expandAll = false, ids = listOfNotNull(current?.phase?.id))
        }
    }

    LaunchedEffect(replanState) {
        if (replanState is ReplanState.Done) {
            viewModel.consumeReplanState()
        }
    }

    Scaffold(
        topBar = {
            com.studypath.app.ui.theme.PaperTopBar(
                title = ui.plan?.title ?: "计划详情",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showReminderDialog = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Notifications, "提醒设置", Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showExportDialog = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.SaveAlt, "导出计划", Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showReplanDialog = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Autorenew, "AI 调整计划", Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Delete, "删除计划", Modifier.size(20.dp))
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
            if (ui.phases.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val allIds = ui.phases.map { it.phase.id }
                        val expanded = expandedPhases ?: emptySet()
                        Text(
                            "阶段任务 · ${expanded.size}/${ui.phases.size} 已展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            if (expanded.size == ui.phases.size) viewModel.setAllExpanded(false, allIds)
                            else viewModel.setAllExpanded(true, allIds)
                        }) {
                            Icon(
                                if (expanded.size == ui.phases.size) Icons.Default.UnfoldLess
                                else Icons.Default.UnfoldMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (expanded.size == ui.phases.size) "全部收起" else "全部展开",
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            ui.phases.forEach { phase ->
                val isExpanded = (expandedPhases ?: emptySet()).contains(phase.phase.id)
                item(key = "phase_${phase.phase.id}") {
                    PhaseHeader(
                        phase = phase,
                        expanded = isExpanded,
                        onToggle = { viewModel.togglePhase(phase.phase.id) },
                    )
                }
                if (isExpanded) {
                    itemsIndexed(phase.tasks, key = { _, t -> "task_${t.id}" }) { idx, task ->
                        TaskItem(
                            task = task,
                            index = idx,
                            onProgress = { p -> viewModel.setTaskProgress(task.id, p) },
                            onDelivery = { showDeliveryFor = task },
                        )
                    }
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
    showDeliveryFor?.let { task ->
        com.studypath.app.ui.components.DeliveryDialogShared(
            taskId = task.id,
            taskTitle = task.title,
            onDismiss = { showDeliveryFor = null },
            onAdd = { text, imagePath -> viewModel.addDelivery(task.id, text, imagePath) },
            onDelete = { viewModel.deleteDelivery(it) },
            observe = viewModel::observeDeliveries,
        )
    }
    if (showReminderDialog) {
        ReminderDialog(
            onDismiss = { showReminderDialog = false },
            loadState = { viewModel.loadReminder(it) },
            onSave = { enabled, h, m -> viewModel.setReminder(context, enabled, h, m) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseHeader(phase: PhaseWithTasks, expanded: Boolean, onToggle: () -> Unit) {
    val days = phase.tasks.map { it.scheduledDate }.filter { it > 0 }
    val rangeText = if (days.isNotEmpty()) {
        val s = java.time.LocalDate.ofEpochDay(days.min())
        val e = java.time.LocalDate.ofEpochDay(days.max())
        val fmt = { d: java.time.LocalDate -> "${d.monthValue}.${d.dayOfMonth}" }
        if (days.size == 1) fmt(s) else "${fmt(s)} - ${fmt(e)}"
    } else ""
    val done = phase.tasks.count { it.progress >= 100 }
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (rangeText.isBlank()) phase.phase.title
                    else "${phase.phase.title}（$rangeText）",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${phase.tasks.size} 个任务 · 已完成 $done" +
                        (phase.phase.summary.takeIf { expanded && it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: TaskEntity,
    index: Int,
    onProgress: (Int) -> Unit,
    onDelivery: () -> Unit,
) {
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
            // 工具行：交付记录
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(
                    onClick = onDelivery,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("交付记录", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 进度滑条
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
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

/** 每计划独立的提醒设置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(
    onDismiss: () -> Unit,
    loadState: (Context) -> Pair<Boolean, Pair<Int, Int>>,
    onSave: (Boolean, Int, Int) -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(loadState(context).first) }
    var time by remember { mutableStateOf(loadState(context).second) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onSave(true, time.first, time.second)
        else permissionDenied = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本计划的每日提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每天提醒今日任务", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (enabled) "已开启 · %02d:%02d".format(time.first, time.second) else "已关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { want ->
                            if (want && android.os.Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                enabled = want
                                onSave(want, time.first, time.second)
                            }
                        },
                    )
                }
                if (enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("提醒时间", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            // 依次 -15 分钟步进循环过于绕，直接用两个 +/- 按钮调小时和分钟
                        }) { Text("") }
                        OutlinedButton(onClick = {
                            val t = time.first * 60 + time.second - 15
                            val m = ((t % 1440) + 1440) % 1440
                            time = (m / 60) to (m % 60)
                            onSave(enabled, time.first, time.second)
                        }) { Text("-15分") }
                        Text("%02d:%02d".format(time.first, time.second), style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = {
                            val t = time.first * 60 + time.second + 15
                            val m = ((t % 1440) + 1440) % 1440
                            time = (m / 60) to (m % 60)
                            onSave(enabled, time.first, time.second)
                        }) { Text("+15分") }
                    }
                }
                if (permissionDenied) {
                    Text(
                        "未授予通知权限，提醒将无法弹出。可到系统设置中手动开启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "每个计划的提醒相互独立：当天该计划有未完成任务时才会通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
