package com.studypath.app.core.ai

import kotlinx.serialization.Serializable

/** AI 返回的结构化学习计划（用于反序列化模型输出） */
@Serializable
data class AiPlan(
    val title: String = "",
    val overview: String = "",
    val phases: List<AiPhase> = emptyList(),
)

@Serializable
data class AiPhase(
    val title: String = "",
    val summary: String = "",
    val tasks: List<AiTask> = emptyList(),
)

@Serializable
data class AiTask(
    val title: String = "",
    /** 具体学什么/做什么：分号列举知识点、题目范围、产出内容 */
    val detail: String = "",
    /** 怎么做：执行步骤 */
    val method: String = "",
    /** 交付物：完成后应拿到的可检验产出 */
    val deliverable: String = "",
    /** 达标标准：怎样才算完成（可量化） */
    val checkpoint: String = "",
    /** 常见坑：最容易踩的坑或走偏的方向 */
    val pitfall: String = "",
    val resource: String = "",
    val estimatedMinutes: Int = 60,
    /** 计划完成日期，yyyy-MM-dd；AI 生成时包含，导入旧 JSON 时可为空 */
    val scheduledDate: String? = null,
    /** 导出/导入时携带的完成度（0..100）；AI 生成时不包含，默认从头开始 */
    val progress: Int? = null,
)
