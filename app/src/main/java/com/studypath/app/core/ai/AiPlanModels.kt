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
    val method: String = "",
    val resource: String = "",
    val estimatedMinutes: Int = 60,
)
