package com.studypath.app.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.core.ai.AiTask
import com.studypath.app.core.ai.PlanPrompts
import com.studypath.app.data.api.AiClient
import com.studypath.app.data.api.ChatMessage
import com.studypath.app.data.db.TodayTaskRow
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface CoachUi {
    data object Closed : CoachUi
    data class Open(
        val task: AiTask,
        val label: String,
        val planTitle: String,
        val messages: List<ChatMessage> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    ) : CoachUi
}

class TodayViewModel(
    private val repository: PlanRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    /** 今天的全部任务（跨计划） */
    val todayTasks: StateFlow<List<TodayTaskRow>> =
        repository.observeTasksByDay(LocalDate.now().toEpochDay())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setProgress(taskId: Long, progress: Int) {
        viewModelScope.launch { repository.setTaskProgress(taskId, progress) }
    }

    // ---- 交付记录 ----

    fun observeDeliveries(taskId: Long) = repository.observeDeliveries(taskId)

    fun addDelivery(taskId: Long, planId: Long, text: String, imagePath: String?) {
        if (text.isBlank() && imagePath == null) return
        viewModelScope.launch {
            repository.addDelivery(
                com.studypath.app.data.db.DeliveryEntity(
                    taskId = taskId, planId = planId, text = text.trim(), imagePath = imagePath,
                )
            )
        }
    }

    fun deleteDelivery(id: Long) {
        viewModelScope.launch { repository.deleteDelivery(id) }
    }

    // ---- 任务 AI 教练 ----

    private val _coach = MutableStateFlow<CoachUi>(CoachUi.Closed)
    val coach: StateFlow<CoachUi> = _coach

    fun openCoach(row: TodayTaskRow) {
        val t = AiTask(
            title = row.task.title, detail = row.task.detail, method = row.task.method,
            deliverable = row.task.deliverable, checkpoint = row.task.checkpoint,
            pitfall = row.task.pitfall, resource = row.task.resource,
            estimatedMinutes = row.task.estimatedMinutes,
        )
        _coach.value = CoachUi.Open(
            task = t,
            label = "${row.planTitle} · ${row.phaseTitle}",
            planTitle = row.planTitle,
        )
    }

    fun closeCoach() { _coach.value = CoachUi.Closed }

    fun askCoach(question: String) {
        val cur = _coach.value as? CoachUi.Open ?: return
        if (question.isBlank() || cur.loading) return
        viewModelScope.launch {
            _coach.value = cur.copy(loading = true, error = null)
            val history = cur.messages + ChatMessage("user", question.trim())
            val config = repository.observeDefaultConfig().first()
            if (config == null) {
                _coach.value = cur.copy(
                    messages = history, loading = false,
                    error = "尚未配置模型 API，请先到「设置」添加",
                )
                return@launch
            }
            val system = PlanPrompts.taskCoachSystem(cur.planTitle, cur.task, cur.label)
            aiClient.complete(config, listOf(ChatMessage("system", system)) + history).fold(
                onSuccess = { reply ->
                    _coach.value = CoachUi.Open(
                        task = cur.task, label = cur.label, planTitle = cur.planTitle,
                        messages = history + ChatMessage("assistant", reply.trim()),
                    )
                },
                onFailure = { e ->
                    _coach.value = cur.copy(messages = history, loading = false, error = "发送失败：${e.message}")
                },
            )
        }
    }
}
