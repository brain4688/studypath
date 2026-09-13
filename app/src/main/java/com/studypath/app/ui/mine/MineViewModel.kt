package com.studypath.app.ui.mine

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.data.reminder.ReminderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** 已是最新版本 */
    data object Latest : UpdateState
    /** 有新版本，携带最新 tag */
    data class Available(val tag: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

class MineViewModel : ViewModel() {

    var username by mutableStateOf("学习者")
        private set
    var avatarPath by mutableStateOf<String?>(null)
        private set

    var globalNotifyEnabled by mutableStateOf(true)
        private set
    var defaultNotifyTime by mutableStateOf(ReminderPrefs.DEFAULT_HOUR to ReminderPrefs.DEFAULT_MINUTE)
        private set

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    companion object {
        const val GITHUB_URL = "https://github.com/brain4688/studypath"
        const val RELEASES_URL = "https://github.com/brain4688/studypath/releases/latest"
        private const val API_URL = "https://api.github.com/repos/brain4688/studypath/releases/latest"
    }

    fun load(context: Context) {
        username = ReminderPrefs.username(context)
        avatarPath = ReminderPrefs.avatarPath(context)
        globalNotifyEnabled = ReminderPrefs.isGlobalEnabled(context)
        defaultNotifyTime = ReminderPrefs.defaultTime(context)
    }

    fun setUsername(context: Context, name: String) {
        val trimmed = name.trim().ifBlank { "学习者" }
        ReminderPrefs.saveUsername(context, trimmed)
        username = trimmed
    }

    fun setAvatar(context: Context, path: String?) {
        ReminderPrefs.saveAvatar(context, path)
        avatarPath = path
    }

    fun setGlobalNotify(context: Context, enabled: Boolean) {
        ReminderPrefs.saveGlobalEnabled(context, enabled)
        globalNotifyEnabled = enabled
    }

    fun setDefaultNotifyTime(context: Context, hour: Int, minute: Int) {
        ReminderPrefs.saveDefaultTime(context, hour, minute)
        defaultNotifyTime = hour to minute
    }

    /** 查询 GitHub 最新 Release，与当前版本号比较 */
    fun checkUpdate(context: Context) {
        if (_update.value is UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            val current = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            }.getOrDefault("")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(API_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                        ?: error("未找到版本信息")
                }
            }
            _update.value = result.fold(
                onSuccess = { tag ->
                    val clean = tag.removePrefix("v")
                    if (clean.isBlank() || current.isBlank() || clean <= current) UpdateState.Latest
                    else UpdateState.Available(tag)
                },
                onFailure = { UpdateState.Error("检查失败：${it.message}（网络受限时可手动前往 GitHub 查看）") },
            )
        }
    }
}
