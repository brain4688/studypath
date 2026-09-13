package com.studypath.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.db.DeliveryEntity
import com.studypath.app.ui.today.CoachUi
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 任务 AI 执行教练：带着任务完整上下文的多轮问答（历史按任务持久化，可跨天接着问） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCoachDialogShared(
    state: CoachUi.Open,
    onDismiss: () -> Unit,
    onAsk: (String) -> Unit,
    onClear: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.loading) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("执行教练 · ${state.label}", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.messages.isEmpty()) "把任务拆成几步慢慢问，教练会记住你们之前聊过的内容"
                        else "历史对话已保存，可以直接接着上次问",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.messages.isNotEmpty()) {
                        TextButton(
                            onClick = onClear,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.Delete, "清空对话", Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("清空对话", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "「${state.task.title}」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            state.task.detail.ifBlank { state.task.method },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    items(state.messages, key = { it.id }) { m ->
                        Column {
                            Text(
                                DateTimeFormatter.ofPattern("MM-dd HH:mm")
                                    .format(Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (m.role == "user") {
                                Text(
                                    "我：${m.content}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    m.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .padding(10.dp),
                                )
                            }
                        }
                    }
                    if (state.loading) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("教练思考中…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    state.error?.let {
                        item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("例如：第一步具体做什么？") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                    )
                    IconButton(
                        onClick = { onAsk(input); input = "" },
                        enabled = input.isNotBlank() && !state.loading,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "发送") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 交付记录：文字 + 图片凭证（两处共用） */
@Composable
fun DeliveryDialogShared(
    taskId: Long,
    taskTitle: String,
    onDismiss: () -> Unit,
    onAdd: (String, String?) -> Unit,
    onDelete: (Long) -> Unit,
    observe: (Long) -> Flow<List<DeliveryEntity>>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    val deliveries by observe(taskId).collectAsStateWithLifecycle(initialValue = emptyList())

    val pickImage = rememberLauncherForActivityResultImage { uri ->
        if (uri != null) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.filesDir, "deliveries").apply { mkdirs() }
                        val file = File(dir, "d_${taskId}_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file.absolutePath
                    }.getOrNull()
                }
                pendingImage = copied
                if (copied == null) {
                    android.widget.Toast.makeText(context, "读取图片失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("交付记录", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "「$taskTitle」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (deliveries.isEmpty()) {
                        item {
                            Text(
                                "还没有交付记录。完成任务后把成果（一段总结、笔记截图、看板链接…）存到这里，直观看见自己的学习轨迹。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(deliveries, key = { it.id }) { d ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        DateTimeFormatter.ofPattern("MM-dd HH:mm")
                                            .format(Instant.ofEpochMilli(d.createdAt).atZone(ZoneId.systemDefault())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = { onDelete(d.id) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) {
                                        Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (d.text.isNotBlank()) Text(d.text, style = MaterialTheme.typography.bodySmall)
                                d.imagePath?.let { path ->
                                    val bmp = remember(path) { decodeSampledBitmap(path) }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "交付图片",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp)
                                                .padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("写下这段学习的成果或心得…") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        minLines = 1,
                        maxLines = 3,
                    )
                    IconButton(onClick = { pickImage.launch("image/*") }) {
                        Icon(Icons.Default.Add, "添加图片")
                    }
                    Button(
                        onClick = { onAdd(text, pendingImage); text = ""; pendingImage = null },
                        enabled = text.isNotBlank() || pendingImage != null,
                    ) { Text("保存") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun rememberLauncherForActivityResultImage(onResult: (android.net.Uri?) -> Unit) =
    androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = onResult,
    )

internal fun decodeSampledBitmap(path: String): Bitmap? = runCatching {
    BitmapFactory.decodeFile(path)?.let {
        val scale = 1024f / maxOf(it.width, it.height).coerceAtLeast(1)
        Bitmap.createScaledBitmap(
            it,
            (it.width * scale).toInt().coerceAtLeast(1),
            (it.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}.getOrNull()
