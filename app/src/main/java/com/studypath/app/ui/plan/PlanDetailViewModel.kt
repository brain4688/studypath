package com.studypath.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.core.PlanParser
import com.studypath.app.core.ai.AiTask
import com.studypath.app.core.ai.PlanPrompts
import com.studypath.app.core.export.XlsxWriter
import com.studypath.app.data.api.AiClient
import com.studypath.app.data.api.ChatMessage
import com.studypath.app.data.db.ApiConfigEntity
import com.studypath.app.data.db.DeliveryEntity
import com.studypath.app.data.db.PhaseWithTasks
import com.studypath.app.data.db.PlanEntity
import com.studypath.app.data.reminder.ReminderPrefs
import com.studypath.app.data.reminder.ReminderScheduler
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanDetailUi(
    val plan: PlanEntity? = null,
    val phases: List<PhaseWithTasks> = emptyList(),
    val percent: Int = 0,
    /** 今天未完成的任务数与总分钟数（按计划日期） */
    val todayCount: Int = 0,
    val todayMinutes: Int = 0,
    /** 当前应学习的阶段序号（第一个包含今天任务的阶段，否则第一个未完成的阶段） */
    val currentPhaseOrder: Int = 0,
)

sealed interface ReplanState {
    data object Idle : ReplanState
    data object Loading : ReplanState
    data class Error(val message: String) : ReplanState
    data object Done : ReplanState
}

/** 任务 AI 教练会话状态 */
sealed interface CoachState {
    data object Closed : CoachState
    /** 打开的任务（任务名 + 所属阶段名） */
    data class Open(
        val task: AiTask,
        val label: String,
        val planTitle: String,
        val messages: List<ChatMessage> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    ) : CoachState
}

class PlanDetailViewModel(
    private val planId: Long,
    private val repository: PlanRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    val ui: StateFlow<PlanDetailUi> =
        combine(repository.observePlan(planId), repository.observePhases(planId)) { plan, phases ->
            val weighted = phases.sumOf { p -> p.tasks.sumOf { it.estimatedMinutes.toLong() * it.progress } }
            val total = phases.sumOf { p -> p.tasks.sumOf { it.estimatedMinutes.toLong() } }
            val today = java.time.LocalDate.now().toEpochDay()
            val todayTasks = phases.flatMap { it.tasks }
                .filter { it.scheduledDate == today && it.progress < 100 }
            val currentOrder = (phases.firstOrNull { p -> p.tasks.any { it.scheduledDate == today } }
                ?: phases.firstOrNull { p -> p.tasks.any { it.progress < 100 } }
                ?: phases.firstOrNull())?.phase?.orderIndex ?: 0
            PlanDetailUi(
                plan, phases,
                com.studypath.app.core.ProgressCalculator.percent(weighted, total),
                todayCount = todayTasks.size,
                todayMinutes = todayTasks.sumOf { it.estimatedMinutes },
                currentPhaseOrder = currentOrder,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanDetailUi())

    private val _replanState = MutableStateFlow<ReplanState>(ReplanState.Idle)
    val replanState: StateFlow<ReplanState> = _replanState

    private val _coach = MutableStateFlow<CoachState>(CoachState.Closed)
    val coach: StateFlow<CoachState> = _coach

    /** 已展开的阶段 id 集合；空集表示尚未初始化（由 UI 决定默认展开） */
    private val _expandedPhases = MutableStateFlow<Set<Long>?>(null)
    val expandedPhases: StateFlow<Set<Long>?> = _expandedPhases

    fun togglePhase(phaseId: Long) {
        val cur = _expandedPhases.value ?: emptySet()
        _expandedPhases.value = if (phaseId in cur) cur - phaseId else cur + phaseId
    }

    fun setAllExpanded(expandAll: Boolean, ids: List<Long>) {
        _expandedPhases.value = if (expandAll) ids.toSet() else emptySet()
    }

    // ---------- 任务 AI 教练 ----------

    fun openCoach(phaseTitle: String, index: Int, task: com.studypath.app.data.db.TaskEntity) {
        val t = AiTask(
            title = task.title, detail = task.detail, method = task.method,
            deliverable = task.deliverable, checkpoint = task.checkpoint,
            pitfall = task.pitfall, resource = task.resource, estimatedMinutes = task.estimatedMinutes,
        )
        _coach.value = CoachState.Open(
            task = t,
            label = "T${index + 1}（${phaseTitle}）",
            planTitle = ui.value.plan?.title ?: "",
        )
    }

    fun closeCoach() { _coach.value = CoachState.Closed }

    fun askCoach(question: String) {
        val cur = _coach.value as? CoachState.Open ?: return
        if (question.isBlank() || cur.loading) return
        viewModelScope.launch {
            _coach.value = cur.copy(loading = true, error = null)
            val history = cur.messages + ChatMessage("user", question.trim())
            val config: ApiConfigEntity? = repository.observeDefaultConfig().first()
            if (config == null) {
                _coach.value = cur.copy(
                    messages = history, loading = false,
                    error = "尚未配置模型 API，请先到「设置」添加",
                )
                return@launch
            }
            val system = PlanPrompts.taskCoachSystem(cur.planTitle, cur.task, cur.label)
            val messages = listOf(ChatMessage("system", system)) + history
            aiClient.complete(config, messages).fold(
                onSuccess = { reply ->
                    _coach.value = CoachState.Open(
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

    // ---------- 交付记录 ----------

    fun observeDeliveries(taskId: Long) = repository.observeDeliveries(taskId)

    fun addDelivery(taskId: Long, text: String, imagePath: String?) {
        if (text.isBlank() && imagePath == null) return
        viewModelScope.launch {
            repository.addDelivery(
                DeliveryEntity(taskId = taskId, planId = planId, text = text.trim(), imagePath = imagePath)
            )
        }
    }

    fun deleteDelivery(id: Long) {
        viewModelScope.launch { repository.deleteDelivery(id) }
    }

    // ---------- 每计划独立提醒 ----------

    fun loadReminder(context: android.content.Context): Pair<Boolean, Pair<Int, Int>> =
        ReminderPrefs.isEnabled(context, planId) to ReminderPrefs.time(context, planId)

    fun setReminder(context: android.content.Context, enabled: Boolean, hour: Int, minute: Int) {
        ReminderPrefs.save(context, planId, enabled, hour, minute)
        if (enabled) ReminderScheduler.schedule(context, planId, hour, minute)
        else ReminderScheduler.cancel(context, planId)
    }

    fun setTaskProgress(taskId: Long, progress: Int) {
        viewModelScope.launch { repository.setTaskProgress(taskId, progress) }
    }

    fun deletePlan(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlan(planId)
            onDone()
        }
    }

    /** AI 动态重规划：根据剩余未完成任务与用户理由，重新生成后续计划 */
    fun replan(reason: String) {
        if (_replanState.value is ReplanState.Loading) return
        _replanState.value = ReplanState.Loading
        viewModelScope.launch {
            runCatching {
                val plan = repository.observePlan(planId).first()
                    ?: error("计划不存在")
                val config: ApiConfigEntity = repository.observeDefaultConfig().first()
                    ?: error("尚未配置模型 API")
                val tasks = repository.getPlanTasks(planId)
                val remaining = tasks.filter { it.progress < 100 }
                require(remaining.isNotEmpty()) { "所有任务都已完成，无需调整计划" }
                val request = com.studypath.app.core.ai.LearningRequest(
                    topic = plan.goal,
                    level = "已在学习过程中（详见进度报告）",
                    dailyMinutes = 60,
                    deadlineWeeks = (remaining.sumOf { it.estimatedMinutes } / 3600 / 7).coerceIn(1, 52) + 1,
                    purpose = "继续完成计划「${plan.title}」",
                )
                val report = PlanPrompts.progressReport(tasks.map { it.title to it.progress })
                val prompt = PlanPrompts.replanPrompt(request, plan.title, report, reason)
                val content = aiClient.complete(config, PlanPrompts.SYSTEM, prompt).getOrThrow()
                PlanParser.parse(content)
            }.fold(
                onSuccess = { aiPlan ->
                    repository.applyReplan(planId, aiPlan)
                    _replanState.value = ReplanState.Done
                },
                onFailure = { e -> _replanState.value = ReplanState.Error(e.message ?: "未知错误") },
            )
        }
    }

    fun consumeReplanState() {
        if (_replanState.value is ReplanState.Done || _replanState.value is ReplanState.Error) {
            _replanState.value = ReplanState.Idle
        }
    }

    /** 导出当前计划为 .xlsx 字节流（纯 Kotlin 生成，无第三方依赖） */
    fun exportXlsx(): ByteArray? {
        val u = ui.value
        val plan = u.plan ?: return null
        val rows = mutableListOf<List<Any>>()
        rows += listOf("计划标题", plan.title)
        rows += listOf("学习目标", plan.goal)
        rows += listOf("总体说明", plan.overview)
        rows += listOf("生成模型", plan.configName)
        rows += listOf("")
        rows += listOf("阶段", "任务", "学什么", "怎么做", "交付物", "达标标准", "常见坑", "推荐资源", "预计时长(分钟)", "进度(%)", "状态")
        u.phases.forEach { phase ->
            phase.tasks.forEach { t ->
                rows += listOf(
                    phase.phase.title, t.title, t.detail, t.method, t.deliverable,
                    t.checkpoint, t.pitfall, t.resource,
                    t.estimatedMinutes, t.progress,
                    when {
                        t.progress >= 100 -> "已完成"
                        t.progress > 0 -> "进行中"
                        else -> "未开始"
                    },
                )
            }
        }
        val totalMinutes = u.phases.sumOf { p -> p.tasks.sumOf { it.estimatedMinutes } }
        rows += listOf("")
        rows += listOf("合计", "${u.phases.sumOf { it.tasks.size }} 个任务", "", "", totalMinutes, u.percent, "")
        return XlsxWriter.write(plan.title, rows)
    }

    /** 导出当前计划为可再导入的 JSON（含任务进度，作为备份/分享格式） */
    fun exportJson(): String? {
        val u = ui.value
        val plan = u.plan ?: return null
        return repository.planToJson(plan, u.phases)
    }
}
