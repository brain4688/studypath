package com.studypath.app.ui.plan

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.db.PhaseWithTasks
import com.studypath.app.data.db.TaskEntity
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
                items(phase.tasks, key = { "task_${it.id}" }) { task ->
                    TaskItem(
                        task = task,
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ui.percent / 100f },
                    modifier = Modifier.size(72.dp),
                    trackColor = MaterialTheme.colorScheme.surface,
                )
                Text("${ui.percent}%", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("总进度", style = MaterialTheme.typography.labelLarge)
                Text(
                    ui.plan?.overview ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PhaseHeader(phase: PhaseWithTasks) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("阶段：${phase.phase.title}", style = MaterialTheme.typography.titleMedium)
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
private fun TaskItem(task: TaskEntity, onProgress: (Int) -> Unit) {
    var sliderValue by remember(task.id, task.progress) { mutableStateOf(task.progress.toFloat()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        task.progress >= 100 -> "✓ 完成"
                        task.progress > 0 -> "${task.progress}%"
                        else -> "未开始"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (task.progress >= 100) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.method.isNotBlank()) {
                Text("方法：${task.method}", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (task.resource.isNotBlank()) {
                Text("资源：${task.resource}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(
                "预计 ${task.estimatedMinutes} 分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
