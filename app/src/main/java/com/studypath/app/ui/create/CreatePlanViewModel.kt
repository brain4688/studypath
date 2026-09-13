package com.studypath.app.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.core.ai.AiPhase
import com.studypath.app.core.ai.AiPlan
import com.studypath.app.core.ai.AiTask
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.launch

/** 草稿任务 */
data class DraftTask(
    var title: String = "",
    var detail: String = "",
    var method: String = "",
    var deliverable: String = "",
    var checkpoint: String = "",
    var pitfall: String = "",
    var resource: String = "",
    var estimatedMinutes: Int = 60,
    var date: String = "",
)

/** 草稿阶段 */
data class DraftPhase(
    var title: String = "",
    var summary: String = "",
    val tasks: MutableList<DraftTask> = mutableStateListOf(),
)

class CreatePlanViewModel(
    private val repository: PlanRepository,
) : ViewModel() {

    var planTitle by mutableStateOf("")
    var planOverview by mutableStateOf("")
    val phases = mutableStateListOf<DraftPhase>()
    var savedPlanId by mutableStateOf<Long?>(null)
    var errorMsg by mutableStateOf<String?>(null)

    fun addPhase() {
        phases.add(DraftPhase(title = "第 ${phases.size + 1} 阶段"))
    }

    fun removePhase(index: Int) {
        if (index in phases.indices) phases.removeAt(index)
    }

    fun addTask(phaseIndex: Int, task: DraftTask) {
        if (phaseIndex in phases.indices) phases[phaseIndex].tasks.add(task)
    }

    fun removeTask(phaseIndex: Int, taskIndex: Int) {
        val phase = phases.getOrNull(phaseIndex) ?: return
        if (taskIndex in phase.tasks.indices) phase.tasks.removeAt(taskIndex)
    }

    fun save(onDone: (Long) -> Unit) {
        val t = planTitle.trim()
        if (t.isBlank()) { errorMsg = "请先填写计划标题"; return }
        if (phases.isEmpty()) { errorMsg = "请至少添加一个阶段"; return }
        if (phases.any { it.tasks.isEmpty() }) { errorMsg = "每个阶段至少要有一个小任务"; return }
        viewModelScope.launch {
            val aiPlan = AiPlan(
                title = t,
                overview = planOverview.trim(),
                phases = phases.map { p ->
                    AiPhase(
                        title = p.title.trim(),
                        summary = p.summary.trim(),
                        tasks = p.tasks.map {
                            AiTask(
                                title = it.title.trim(),
                                detail = it.detail.trim(),
                                method = it.method.trim(),
                                deliverable = it.deliverable.trim(),
                                checkpoint = it.checkpoint.trim(),
                                pitfall = it.pitfall.trim(),
                                resource = it.resource.trim(),
                                estimatedMinutes = it.estimatedMinutes.coerceAtLeast(5),
                                scheduledDate = it.date.ifBlank { null },
                            )
                        },
                    )
                },
            )
            savedPlanId = repository.createPlan(aiPlan, t, "手动创建")
            onDone(savedPlanId!!)
        }
    }
}
