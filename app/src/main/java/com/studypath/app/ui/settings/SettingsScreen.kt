package com.studypath.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.api.ProviderPreset
import com.studypath.app.data.api.ProviderPresets
import com.studypath.app.data.db.ApiConfigEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testingId by viewModel.testingId.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by viewModel.reminderTime.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadReminder(context) }

    var editing by remember { mutableStateOf<ApiConfigEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ApiConfigEntity?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }

    // Android 13+ 通知运行时权限
    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val (h, m) = reminderTime
        viewModel.setReminder(context, granted && pendingEnable, h, m)
        pendingEnable = false
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, "新增配置")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "应用使用 OpenAI 兼容接口调用大模型。API Key 仅保存在你手机本地，请使用自己的 Key（BYOK）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (configs.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("还没有模型配置", style = MaterialTheme.typography.titleSmall)
                            Text("点击右下角 + 添加，推荐先配置 DeepSeek 或智谱（国内可直连，价格低）。",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(configs, key = { it.id }) { config ->
                ConfigItem(
                    config = config,
                    testing = testingId == config.id,
                    onTest = { viewModel.testConnection(config) },
                    onEdit = { editing = config; showEditor = true },
                    onSetDefault = { viewModel.setDefault(config.id) },
                    onDelete = { deleting = config },
                )
            }
            testResult?.let {
                item { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            // ---- 每日提醒 ----
            item { com.studypath.app.ui.theme.SectionLabel("每日学习提醒") }
            item {
                val timeText = "%02d:%02d".format(reminderTime.first, reminderTime.second)
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("按计划日期提醒今天该学的内容", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (reminderEnabled) "每天 $timeText 提醒（当天有未完成任务时）"
                                    else "已关闭，开启后可选择提醒时间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { wantOn ->
                                    if (wantOn) {
                                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                                            pendingEnable = true
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            val (h, m) = reminderTime
                                            viewModel.setReminder(context, true, h, m)
                                        }
                                    } else {
                                        val (h, m) = reminderTime
                                        viewModel.setReminder(context, false, h, m)
                                    }
                                },
                            )
                        }
                        if (reminderEnabled) {
                            TextButton(onClick = { showTimePicker = true }) {
                                Text("提醒时间：$timeText")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showEditor) {
        ConfigEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { viewModel.save(it); showEditor = false },
        )
    }
    if (showTimePicker) {
        val state = androidx.compose.material3.rememberTimePickerState(
            initialHour = reminderTime.first,
            initialMinute = reminderTime.second,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = { androidx.compose.material3.TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminder(context, true, state.hour, state.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
        )
    }
    deleting?.let { config ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除配置「${config.name}」？") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(config); deleting = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ConfigItem(
    config: ApiConfigEntity,
    testing: Boolean,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSetDefault) {
                    Icon(
                        if (config.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "设为默认",
                        tint = if (config.isDefault) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(config.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${config.model} · ${config.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (testing) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    TextButton(onClick = onTest) { Text("测试") }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑", Modifier.size(18.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun ConfigEditorDialog(
    initial: ApiConfigEntity?,
    onDismiss: () -> Unit,
    onSave: (ApiConfigEntity) -> Unit,
) {
    var presetIndex by remember { mutableStateOf(-1) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }

    val valid = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增模型配置" else "编辑模型配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("服务商预设", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    ProviderPresets.all.forEachIndexed { i, preset ->
                        androidx.compose.material3.FilterChip(
                            selected = presetIndex == i,
                            onClick = {
                                presetIndex = i
                                if (preset.baseUrl.isNotBlank()) {
                                    if (name.isBlank() || ProviderPresets.all.any { it.display == name }) name = preset.display
                                    baseUrl = preset.baseUrl
                                    model = preset.defaultModel
                                }
                            },
                            label = { Text(preset.display) },
                        )
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("Base URL") }, placeholder = { Text("https://api.deepseek.com/v1") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it },
                    label = { Text("模型名称") }, placeholder = { Text("deepseek-chat") },
                    modifier = Modifier.fillMaxWidth())
                if (presetIndex >= 0 && ProviderPresets.all[presetIndex].keyUrl.isNotBlank()) {
                    KeyHint(ProviderPresets.all[presetIndex])
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    ApiConfigEntity(
                        id = initial?.id ?: 0,
                        name = name.trim(), baseUrl = baseUrl.trim().trimEnd('/'),
                        apiKey = apiKey.trim(), model = model.trim(),
                        isDefault = initial?.isDefault ?: false,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun KeyHint(preset: ProviderPreset) {
    Text(
        "获取 Key：${preset.keyUrl}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
