package com.studypath.app.core

import com.studypath.app.core.ai.AiPlan
import kotlinx.serialization.json.Json

/** 解析模型输出的学习计划 JSON（容忍代码块标记、前后杂文字） */
object PlanParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(content: String): AiPlan {
        val cleaned = extractJsonObject(content)
        val plan = json.decodeFromString<AiPlan>(cleaned)
        require(plan.phases.isNotEmpty()) { "计划中没有包含任何阶段（phases 为空）" }
        return plan
    }

    /** 去掉 ```json 代码块标记与首尾无关文字，截取最外层 { ... } */
    internal fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "模型输出中未找到 JSON 内容" }
        return text.substring(start, end + 1)
    }
}
