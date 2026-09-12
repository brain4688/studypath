package com.studypath.app.data.repo

import com.studypath.app.core.ProgressCalculator
import com.studypath.app.data.db.ApiConfigDao
import com.studypath.app.data.db.ApiConfigEntity
import com.studypath.app.data.db.PhaseDao
import com.studypath.app.data.db.PhaseEntity
import com.studypath.app.data.db.PhaseWithTasks
import com.studypath.app.data.db.PlanDao
import com.studypath.app.data.db.PlanEntity
import com.studypath.app.data.db.PlanProgressRow
import com.studypath.app.data.db.TaskDao
import com.studypath.app.data.db.TaskEntity
import com.studypath.app.core.ai.AiPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** 首页计划卡片数据 */
data class PlanCard(
    val plan: PlanEntity,
    val percent: Int,
    val totalMinutes: Int,
    val taskCount: Int,
)

class PlanRepository(
    private val planDao: PlanDao,
    private val phaseDao: PhaseDao,
    private val taskDao: TaskDao,
    private val configDao: ApiConfigDao,
) {
    // ---------- 查询 ----------

    fun observePlans(): Flow<List<PlanCard>> =
        kotlinx.coroutines.flow.combine(
            planDao.observeAll(),
            taskDao.observePlanProgress(),
        ) { plans, rows ->
            val byId = rows.associateBy { it.planId }
            plans.map { plan ->
                val row = byId[plan.id]
                PlanCard(
                    plan = plan,
                    percent = ProgressCalculator.percent(row?.weighted ?: 0, row?.total ?: 0),
                    totalMinutes = (row?.total ?: 0).toInt(),
                    taskCount = 0,
                )
            }
        }

    fun observePlan(id: Long): Flow<PlanEntity?> = planDao.observeById(id)

    fun observePhases(planId: Long): Flow<List<PhaseWithTasks>> =
        phaseDao.observePhasesWithTasks(planId)

    fun observeConfigs(): Flow<List<ApiConfigEntity>> = configDao.observeAll()

    fun observeDefaultConfig(): Flow<ApiConfigEntity?> = configDao.observeDefault()

    suspend fun getPlanTasks(planId: Long): List<TaskEntity> = taskDao.getByPlan(planId)

    // ---------- 计划写入 ----------

    /** 由 AI 返回的结构化计划创建本地计划，返回 planId */
    suspend fun createPlan(ai: AiPlan, goal: String, configName: String): Long {
        val planId = planDao.insert(
            PlanEntity(title = ai.title.ifBlank { goal.take(30) }, goal = goal, overview = ai.overview, configName = configName)
        )
        insertPhases(planId, ai, startIndex = 0)
        return planId
    }

    /**
     * 动态重规划：删除未完成的任务（保留已完成的历史），
     * 再把 AI 生成的新阶段追加到计划末尾。
     */
    suspend fun applyReplan(planId: Long, remaining: AiPlan) {
        taskDao.deleteUnfinished(planId)
        phaseDao.deleteEmptyPhases(planId)
        insertPhases(planId, remaining, startIndex = (phaseDao.maxOrderIndex(planId) ?: -1) + 1)
    }

    private suspend fun insertPhases(planId: Long, ai: AiPlan, startIndex: Int) {
        ai.phases.forEachIndexed { phaseIdx, aiPhase ->
            val phase = PhaseEntity(
                planId = planId,
                title = aiPhase.title,
                summary = aiPhase.summary,
                orderIndex = startIndex + phaseIdx,
            )
            val phaseId = phaseDao.insert(phase)
            taskDao.insertAll(
                aiPhase.tasks.mapIndexed { taskIdx, t ->
                    TaskEntity(
                        phaseId = phaseId,
                        planId = planId,
                        title = t.title,
                        method = t.method,
                        resource = t.resource,
                        estimatedMinutes = t.estimatedMinutes.coerceAtLeast(5),
                        orderIndex = taskIdx,
                    )
                }
            )
        }
    }

    // ---------- 任务进度 ----------

    suspend fun setTaskProgress(taskId: Long, progress: Int) =
        taskDao.setProgress(taskId, progress.coerceIn(0, 100))

    // ---------- 配置 ----------

    suspend fun saveConfig(config: ApiConfigEntity): Long {
        val id = configDao.upsert(config)
        // 第一条配置自动设为默认
        val isOnly = configDao.observeAll().first().size == 1
        if (config.isDefault || isOnly) configDao.setDefault(id)
        return id
    }

    suspend fun setDefaultConfig(id: Long) = configDao.setDefault(id)
    suspend fun deleteConfig(config: ApiConfigEntity) = configDao.delete(config)

    suspend fun deletePlan(planId: Long) = planDao.deleteById(planId)
}
