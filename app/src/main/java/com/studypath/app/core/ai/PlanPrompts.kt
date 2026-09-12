package com.studypath.app.core.ai

/** 用户在学习需求表单里填写的信息 */
data class LearningRequest(
    val topic: String,        // 想学什么
    val level: String,        // 当前水平
    val dailyMinutes: Int,    // 每天可投入时间（分钟）
    val deadlineWeeks: Int,   // 期望完成周期（周）
    val purpose: String,      // 学习目的
    val notes: String = "",   // 补充说明
)

object PlanPrompts {

    private const val SCHEMA = """
{
  "title": "计划标题",
  "overview": "整体规划思路（150字以内）",
  "phases": [
    {
      "title": "阶段标题",
      "summary": "本阶段目标说明",
      "tasks": [
        {
          "title": "小任务标题",
          "method": "这个任务怎么学（具体方法/步骤）",
          "resource": "推荐的学习资源（书/课/文档，选权威知名的）",
          "estimatedMinutes": 60
        }
      ]
    }
  ]
}
"""

    val SYSTEM = """你是一位资深的个性化学习规划师。你的任务是根据用户的学习需求，制定科学、可执行的学习计划。

要求：
1. 只输出一个 JSON 对象，不要输出任何其他文字、解释或 Markdown 代码块标记。
2. 严格按照如下结构输出（字段名完全一致，estimatedMinutes 必须是数字）：
$SCHEMA
3. 每天可投入时间拆分任务粒度：每个小任务的 estimatedMinutes 不得超过用户"每日可投入时间"，一般 30~120 分钟。
4. 按用户期望的周期安排阶段数量，让总时长与周期匹配。
5. method 要具体可操作（如：先看第X章、再做配套练习、最后写一篇总结），不要空话。
6. resource 只推荐真实存在且权威知名的资源（经典书籍、名校公开课、官方文档），不要编造链接。"""

    fun newUserPrompt(req: LearningRequest): String = buildString {
        appendLine("请为我制定学习计划：")
        appendLine("- 学习目标：${req.topic}")
        appendLine("- 当前水平：${req.level}")
        appendLine("- 每天可投入时间：${req.dailyMinutes} 分钟")
        appendLine("- 期望周期：${req.deadlineWeeks} 周")
        appendLine("- 学习目的：${req.purpose}")
        if (req.notes.isNotBlank()) appendLine("- 补充说明：${req.notes}")
    }

    fun replanPrompt(req: LearningRequest, planTitle: String, progressReport: String, reason: String): String =
        buildString {
            appendLine("我之前请你制定了学习计划「$planTitle」，原始需求如下：")
            appendLine("- 学习目标：${req.topic}；每天可投入 ${req.dailyMinutes} 分钟；期望周期 ${req.deadlineWeeks} 周")
            appendLine()
            appendLine("目前的实际学习进度：")
            appendLine(progressReport)
            appendLine()
            appendLine("我需要调整计划，原因：$reason")
            appendLine()
            appendLine("请只输出「从现在开始剩余部分」的新计划（JSON 结构与之前相同，含 title/overview/phases）。")
            appendLine("注意：已完成的内容不要重复安排；结合我的实际进度和剩余时间重新分配任务。")
        }

    /** 把当前任务进度压缩成给模型看的文本报告 */
    fun progressReport(tasks: List<Pair<String, Int>>): String = buildString {
        if (tasks.isEmpty()) appendLine("（还没有任何任务）")
        tasks.forEach { (title, progress) ->
            appendLine("- $title：" + if (progress >= 100) "已完成" else "完成 $progress%")
        }
    }
}
