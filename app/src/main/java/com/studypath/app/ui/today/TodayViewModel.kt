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
        val taskId: Long,
        val task: AiTask,
        val label: String,
        val planTitle: String,
        /** 历史对话（来自数据库，跨多次执行持久保存） */
        val messages: List<com.studypath.app.data.db.CoachMessageEntity> = emptyList(),
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

    // ---- 任务 AI 教练（历史持久化，跨多次执行接着问） ----

    private val _coach = MutableStateFlow<CoachUi>(CoachUi.Closed)
    val coach: StateFlow<CoachUi> = _coach

    fun openCoach(row: TodayTaskRow) {
        val t = AiTask(
            title = row.task.title, detail = row.task.detail, method = row.task.method,
            deliverable = row.task.deliverable, checkpoint = row.task.checkpoint,
            pitfall = row.task.pitfall, resource = row.task.resource,
            estimatedMinutes = row.task.estimatedMinutes,
        )
        val open = CoachUi.Open(
            taskId = row.task.id,
            task = t,
            label = "${row.planTitle} · ${row.phaseTitle}",
            planTitle = row.planTitle,
        )
        _coach.value = open
        // 打开即加载该任务的历史对话：分几天做的任务，AI 记得之前聊过什么
        viewModelScope.launch {
            val history = repository.coachHistory(row.task.id)
            val cur = _coach.value as? CoachUi.Open
            if (cur != null && cur.taskId == row.task.id && cur.messages.isEmpty()) {
                _coach.value = cur.copy(messages = history)
            }
        }
    }

    fun closeCoach() { _coach.value = CoachUi.Closed }

    /** 清空当前任务的历史对话，重新开始 */
    fun clearCoachHistory() {
        val cur = _coach.value as? CoachUi.Open ?: return
        viewModelScope.launch {
            repository.clearCoach(cur.taskId)
            _coach.value = cur.copy(messages = emptyList())
        }
    }

    fun askCoach(question: String) {
        val cur = _coach.value as? CoachUi.Open ?: return
        if (question.isBlank() || cur.loading) return
        viewModelScope.launch {
            _coach.value = cur.copy(loading = true, error = null)
            // 用户消息先落库
            repository.addCoachMessage(
                com.studypath.app.data.db.CoachMessageEntity(
                    taskId = cur.taskId, role = "user", content = question.trim(),
                )
            )
            // 历史来自数据库：无论隔了多久，AI 都能接着上次的上下文
            val history = repository.coachHistory(cur.taskId)
            val config = repository.observeDefaultConfig().first()
            if (config == null) {
                _coach.value = cur.copy(
                    messages = history, loading = false,
                    error = "尚未配置模型 API，请先到「设置」添加",
                )
                return@launch
            }
            val system = PlanPrompts.taskCoachSystem(cur.planTitle, cur.task, cur.label)
            val apiMessages = listOf(ChatMessage("system", system)) +
                history.map { ChatMessage(it.role, it.content) }
            aiClient.complete(config, apiMessages).fold(
                onSuccess = { reply ->
                    repository.addCoachMessage(
                        com.studypath.app.data.db.CoachMessageEntity(
                            taskId = cur.taskId, role = "assistant", content = reply.trim(),
                        )
                    )
                    _coach.value = CoachUi.Open(
                        taskId = cur.taskId, task = cur.task, label = cur.label,
                        planTitle = cur.planTitle,
                        messages = repository.coachHistory(cur.taskId),
                    )
                },
                onFailure = { e ->
                    _coach.value = cur.copy(messages = history, loading = false, error = "发送失败：${e.message}")
                },
            )
        }
    }
}
