package com.studypath.app.data.repo

import com.studypath.app.core.ProgressCalculator
import com.studypath.app.data.db.ApiConfigDao
import com.studypath.app.data.db.ApiConfigEntity
import com.studypath.app.data.db.ChatDao
import com.studypath.app.data.db.ChatMessageEntity
import com.studypath.app.data.db.DeliveryDao
import com.studypath.app.data.db.DeliveryEntity
import com.studypath.app.data.db.PhaseDao
import com.studypath.app.data.db.PhaseEntity
import com.studypath.app.data.db.PhaseWithTasks
import com.studypath.app.data.db.PlanDao
import com.studypath.app.data.db.PlanEntity
import com.studypath.app.data.db.PlanProgressRow
import com.studypath.app.data.db.TaskDao
import com.studypath.app.data.db.TaskEntity
import com.studypath.app.core.ai.AiPhase
import com.studypath.app.core.ai.AiPlan
import com.studypath.app.core.ai.AiTask
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
    private val chatDao: ChatDao,
    private val deliveryDao: DeliveryDao,
) {
    // ---------- 查询 ----------

    fun observePlans(): Flow<List<PlanCard>> =
        combine(planDao.observeAll(), taskDao.observePlanProgress()) { plans, rows ->
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

    /** 今天（某日期）的全部任务，跨计划聚合 */
    fun observeTasksByDay(epochDay: Long): Flow<List<com.studypath.app.data.db.TodayTaskRow>> =
        taskDao.observeByDay(epochDay)

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
                        detail = t.detail,
                        method = t.method,
                        deliverable = t.deliverable,
                        checkpoint = t.checkpoint,
                        pitfall = t.pitfall,
                        resource = t.resource,
                        estimatedMinutes = t.estimatedMinutes.coerceAtLeast(5),
                        scheduledDate = parseDate(t.scheduledDate),
                        progress = (t.progress ?: 0).coerceIn(0, 100),
                        orderIndex = taskIdx,
                    )
                }
            )
        }
    }

    // ---------- 任务进度 ----------

    suspend fun setTaskProgress(taskId: Long, progress: Int) =
        taskDao.setProgress(taskId, progress.coerceIn(0, 100))

    // ---------- 聊天（AI 规划师） ----------

    fun observeChat(): Flow<List<ChatMessageEntity>> = chatDao.observeAll()

    suspend fun addChatMessage(role: String, content: String) =
        chatDao.insert(ChatMessageEntity(role = role, content = content))

    suspend fun clearChat() = chatDao.clear()

    suspend fun chatCount(): Int = chatDao.count()

    // ---------- 交付记录 ----------

    fun observeDeliveries(taskId: Long): Flow<List<DeliveryEntity>> = deliveryDao.observeByTask(taskId)

    suspend fun addDelivery(delivery: DeliveryEntity) = deliveryDao.insert(delivery)

    suspend fun deleteDelivery(id: Long) = deliveryDao.deleteById(id)

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

    /** 导出为可再导入的 JSON（含各任务进度，可作为备份/分享格式） */
    fun planToJson(plan: PlanEntity, phases: List<PhaseWithTasks>): String {
        val aiPlan = AiPlan(
            title = plan.title,
            overview = plan.overview,
            phases = phases.map { p ->
                AiPhase(
                    title = p.phase.title,
                    summary = p.phase.summary,
                    tasks = p.tasks.map {
                        AiTask(
                            it.title, it.detail, it.method, it.deliverable,
                            it.checkpoint, it.pitfall, it.resource, it.estimatedMinutes,
                            formatDate(it.scheduledDate), it.progress,
                        )
                    },
                )
            },
        )
        return exportJson.encodeToString(AiPlan.serializer(), aiPlan)
    }
}

/** "yyyy-MM-dd" → epoch day；解析失败或为空返回 -1（未排期） */
internal fun parseDate(text: String?): Long =
    text?.trim()?.takeIf { it.isNotBlank() }?.let {
        runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrDefault(-1L)
    } ?: -1L

/** epoch day → "yyyy-MM-dd"；-1 返回 null */
internal fun formatDate(epochDay: Long): String? =
    if (epochDay < 0) null else java.time.LocalDate.ofEpochDay(epochDay).toString()

private val exportJson = kotlinx.serialization.json.Json {
    encodeDefaults = true
    prettyPrint = true
    ignoreUnknownKeys = true
}
