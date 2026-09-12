package com.studypath.app.ui.home

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.repo.PlanCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNewPlan: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val defaultConfig by viewModel.defaultConfig.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewPlan,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建学习计划") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("让 AI 为你规划每一段学习", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = defaultConfig?.let { "当前模型：${it.name} · ${it.model}" }
                                ?: "尚未配置模型，请先在设置中添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "模型设置")
                    }
                }
            }

            if (plans.isEmpty()) {
                item { EmptyHint(hasConfig = defaultConfig != null, onOpenSettings = onOpenSettings) }
            } else {
                items(plans, key = { it.plan.id }) { card ->
                    PlanCardItem(card = card, onClick = { onOpenPlan(card.plan.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(hasConfig: Boolean, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📚", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text("还没有学习计划", style = MaterialTheme.typography.titleMedium)
        Text(
            "描述你的学习需求，AI 会帮你拆解成\n阶段、小任务、学习方法和资源",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (!hasConfig) {
            Button(onClick = onOpenSettings) { Text("先去配置模型 API") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanCardItem(card: PlanCard, onClick: () -> Unit) {
    val dateText = remember(card.plan.createdAt) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(card.plan.createdAt))
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(card.plan.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                card.plan.goal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { card.percent / 100f },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text("${card.percent}%", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "$dateText · 总时长约 ${card.totalMinutes / 60} 小时",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
