package com.studypath.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.core.PlanParser
import com.studypath.app.core.ai.PlanPrompts
import com.studypath.app.core.export.XlsxWriter
import com.studypath.app.data.api.AiClient
import com.studypath.app.data.db.ApiConfigEntity
import com.studypath.app.data.db.PhaseWithTasks
import com.studypath.app.data.db.PlanEntity
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
)

sealed interface ReplanState {
    data object Idle : ReplanState
    data object Loading : ReplanState
    data class Error(val message: String) : ReplanState
    data object Done : ReplanState
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
            PlanDetailUi(
                plan, phases,
                com.studypath.app.core.ProgressCalculator.percent(weighted, total),
                todayCount = todayTasks.size,
                todayMinutes = todayTasks.sumOf { it.estimatedMinutes },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanDetailUi())

    private val _replanState = MutableStateFlow<ReplanState>(ReplanState.Idle)
    val replanState: StateFlow<ReplanState> = _replanState

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
