package com.studypath.app.ui.newplan

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.db.ApiConfigEntity

@Composable
fun NewPlanScreen(
    viewModel: NewPlanViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle(initialValue = emptyList())
    val state by viewModel.state.collectAsStateWithLifecycle()

    var configId by remember { mutableStateOf<Long?>(null) }
    var topic by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var dailyMinutes by remember { mutableIntStateOf(60) }
    var weeks by remember { mutableIntStateOf(8) }
    var purpose by remember { mutableStateOf("求职/面试") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        val s = state
        if (s is GenerateState.Success) onCreated(s.planId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 模型选择
        if (configs.isEmpty()) {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("尚未配置模型 API，点击前往设置")
            }
        } else {
            ModelPicker(
                configs = configs,
                selectedId = configId ?: configs.firstOrNull()?.id,
                onSelect = { configId = it },
            )
        }

        OutlinedTextField(
            value = topic, onValueChange = { topic = it },
            label = { Text("想学什么？（必填）") },
            placeholder = { Text("例如：Python 数据分析 / 高等数学下册 / 雅思口语") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = level, onValueChange = { level = it },
            label = { Text("当前水平") },
            placeholder = { Text("例如：完全零基础 / 会 C 语言 / 高考 120 分") },
            modifier = Modifier.fillMaxWidth(),
        )

        StepperField(
            label = "每天可投入（分钟）", value = dailyMinutes, step = 30, range = 15..480,
            onChange = { dailyMinutes = it },
        )
        StepperField(
            label = "期望周期（周）", value = weeks, step = 1, range = 1..52,
            onChange = { weeks = it },
        )

        Text("学习目的", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("求职/面试", "考试/升学", "兴趣/自学").forEach { p ->
                FilterChip(selected = purpose == p, onClick = { purpose = p }, label = { Text(p) })
            }
        }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("补充说明（可选）") },
            placeholder = { Text("时间安排偏好、已有基础、特别要求…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        when (val s = state) {
            is GenerateState.Error -> {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is GenerateState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("AI 正在为你生成学习计划，约需 10~60 秒…")
                }
            }
            else -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, enabled = state !is GenerateState.Loading) {
                Text("取消")
            }
            Button(
                onClick = {
                    viewModel.generate(
                        configId = configId ?: configs.firstOrNull()?.id ?: -1,
                        topic = topic, level = level,
                        dailyMinutes = dailyMinutes, deadlineWeeks = weeks,
                        purpose = purpose, notes = notes,
                    )
                },
                enabled = state !is GenerateState.Loading && configs.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("生成学习计划") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(configs: List<ApiConfigEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = configs.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.name}（${it.model}）" } ?: "选择模型",
            onValueChange = {},
            readOnly = true,
            label = { Text("使用模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            configs.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.name}（${c.model}）${if (c.isDefault) " ·默认" else ""}") },
                    onClick = { onSelect(c.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun StepperField(label: String, value: Int, step: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { onChange((value - step).coerceIn(range.first, range.last)) }) { Text("−") }
            Text("$value", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onChange((value + step).coerceIn(range.first, range.last)) }) { Text("+") }
        }
    }
}
