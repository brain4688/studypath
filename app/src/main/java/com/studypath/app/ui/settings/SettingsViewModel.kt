package com.studypath.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.data.api.AiClient
import com.studypath.app.data.db.ApiConfigEntity
import com.studypath.app.data.reminder.ReminderPrefs
import com.studypath.app.data.reminder.ReminderScheduler
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: PlanRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    val configs = repository.observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    private val _testingId = MutableStateFlow<Long?>(null)
    val testingId: StateFlow<Long?> = _testingId

    // ---- 每日提醒 ----

    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled

    private val _reminderTime = MutableStateFlow(ReminderPrefs.DEFAULT_HOUR to ReminderPrefs.DEFAULT_MINUTE)
    val reminderTime: StateFlow<Pair<Int, Int>> = _reminderTime

    fun loadReminder(context: Context) {
        _reminderEnabled.value = ReminderPrefs.isEnabled(context)
        _reminderTime.value = ReminderPrefs.time(context)
    }

    /** 更新提醒设置；enabled 时重排 WorkManager 定时任务 */
    fun setReminder(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        ReminderPrefs.save(context, enabled, hour, minute)
        _reminderEnabled.value = enabled
        _reminderTime.value = hour to minute
        if (enabled) ReminderScheduler.schedule(context, hour, minute)
        else ReminderScheduler.cancel(context)
    }

    fun save(config: ApiConfigEntity) {
        viewModelScope.launch { repository.saveConfig(config) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { repository.setDefaultConfig(id) }
    }

    fun delete(config: ApiConfigEntity) {
        viewModelScope.launch { repository.deleteConfig(config) }
    }

    fun testConnection(config: ApiConfigEntity) {
        viewModelScope.launch {
            _testingId.value = config.id
            _testResult.value = null
            val result = aiClient.testConnection(config)
            _testResult.value = result.fold(
                onSuccess = { "✅ ${config.name} 连接成功" },
                onFailure = { "❌ ${config.name} 失败：${it.message}" },
            )
            _testingId.value = null
        }
    }

    fun clearTestResult() { _testResult.value = null }
}
