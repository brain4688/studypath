package com.studypath.app.ui.mine

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studypath.app.data.reminder.ReminderPrefs
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    viewModel: MineViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val update by viewModel.update.collectAsStateWithLifecycle()
    var showNameEditor by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load(context) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val copied = runCatching {
                val dir = File(context.filesDir, "profile").apply { mkdirs() }
                val file = File(dir, "avatar.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            }.getOrNull()
            viewModel.setAvatar(context, copied)
        }
    }

    Scaffold(
        topBar = { com.studypath.app.ui.theme.PaperTopBar(title = "我的") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- 头像与用户名 ----
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .clickable { pickAvatar.launch("image/*") }
                    .padding(4.dp),
            ) {
                val bmp = viewModel.avatarPath?.let { path ->
                    remember(path) { com.studypath.app.ui.components.decodeSampledBitmap(path) }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "头像",
                        modifier = Modifier.size(88.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "头像",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "更换头像",
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(viewModel.username, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { showNameEditor = true }) {
                    Icon(Icons.Default.Edit, "修改用户名", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "点击头像和名字可以自定义",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            // ---- 设置 ----
            SectionCard("设置") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("学习提醒总开关", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (viewModel.globalNotifyEnabled) "已开启（各计划可在详情页单独设置）" else "已关闭，所有计划静默",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = viewModel.globalNotifyEnabled,
                        onCheckedChange = { viewModel.setGlobalNotify(context, it) },
                    )
                }
                if (viewModel.globalNotifyEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("默认提醒时间", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "%02d:%02d（每个计划可单独改）".format(
                                    viewModel.defaultNotifyTime.first, viewModel.defaultNotifyTime.second
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = {
                            val t = viewModel.defaultNotifyTime.first * 60 + viewModel.defaultNotifyTime.second - 15
                            val m = ((t % 1440) + 1440) % 1440
                            viewModel.setDefaultNotifyTime(context, m / 60, m % 60)
                        }) { Text("-15分") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = {
                            val t = viewModel.defaultNotifyTime.first * 60 + viewModel.defaultNotifyTime.second + 15
                            val m = ((t % 1440) + 1440) % 1440
                            viewModel.setDefaultNotifyTime(context, m / 60, m % 60)
                        }) { Text("+15分") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SettingRow("模型设置", "配置大模型 API（DeepSeek/智谱等）") { onOpenSettings() }
            }

            Spacer(Modifier.height(14.dp))

            // ---- 关于 ----
            SectionCard("关于") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("版本", style = MaterialTheme.typography.bodyMedium)
                        val version = remember {
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull() ?: "?"
                        }
                        Text(
                            when (val u = update) {
                                is UpdateState.Checking -> "v$version · 检查更新中…"
                                is UpdateState.Latest -> "v$version · 已是最新"
                                is UpdateState.Available -> "v$version · 发现新版本 ${u.tag}，点击前往下载"
                                is UpdateState.Error -> "v$version · ${u.message}"
                                else -> "v$version · 点击检查更新"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (update is UpdateState.Available) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        when (val u = update) {
                            is UpdateState.Available -> openUrl(context, MineViewModel.RELEASES_URL)
                            else -> viewModel.checkUpdate(context)
                        }
                    }) { Text(if (update is UpdateState.Available) "去更新" else "检查更新") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SettingRow("制作人", "Zane · 点击访问作者主页") {
                    openUrl(context, MineViewModel.AUTHOR_URL)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "☕ 请作者喝杯咖啡，鼓励一下",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clickable { showDonate = true }
                    .padding(vertical = 8.dp),
            )
            Text(
                "StudyPath · 你的数据只保存在手机本地",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (showDonate) {
        AlertDialog(
            onDismissRequest = { showDonate = false },
            title = { Text("请作者喝杯咖啡 ☕") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "如果 StudyPath 对你的学习有帮助，扫码鼓励一下作者吧～",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Image(
                        painter = androidx.compose.ui.res.painterResource(
                            com.studypath.app.R.drawable.donate_wechat
                        ),
                        contentDescription = "微信收款码",
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                    Text("微信", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Image(
                        painter = androidx.compose.ui.res.painterResource(
                            com.studypath.app.R.drawable.donate_alipay
                        ),
                        contentDescription = "支付宝收款码",
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                    Text("支付宝", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showDonate = false }) { Text("关闭") } },
        )
    }
    if (showNameEditor) {
        var name by remember { mutableStateOf(viewModel.username) }
        AlertDialog(
            onDismissRequest = { showNameEditor = false },
            title = { Text("修改用户名") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setUsername(context, name); showNameEditor = false }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showNameEditor = false }) { Text("取消") } },
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
