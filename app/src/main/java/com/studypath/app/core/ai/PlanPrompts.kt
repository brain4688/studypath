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
      "title": "阶段标题（建议带序号与主题，如：第1周 · 地基）",
      "summary": "本阶段目标（一句话）",
      "tasks": [
        {
          "title": "任务标题（具体到一件事，如：刷完牛客SQL基础查询20题）",
          "detail": "具体学什么/做什么：用分号列举知识点、题目范围、要写的代码或产出内容，越具体越好",
          "method": "怎么做：执行步骤（先…再…最后…），含用的工具",
          "deliverable": "交付物：完成后应拿到的可检验产出（脚本/笔记/图表/看板链接/报告）",
          "checkpoint": "达标标准：怎样才算完成，必须可检验可量化",
          "pitfall": "常见坑：这一步最容易踩的坑或走偏的方向",
          "resource": "推荐资源（真实权威的书/官方文档/知名课程/题库，可多个）",
          "estimatedMinutes": 90,
          "date": "2026-09-14"
        }
      ]
    }
  ]
}
"""

    val SYSTEM = """你是一位资深的个性化学习规划师。你的任务是根据用户的学习需求与聊天记录，制定科学、可执行、高度细化的学习计划。

【输出格式】只输出一个 JSON 对象，不要输出任何其他文字、解释或 Markdown 代码块标记。严格按照如下结构（字段名完全一致，estimatedMinutes 必须是数字）：
$SCHEMA

【细化硬性要求】
1. 任务粒度：每个任务 30~150 分钟（不超过用户每日可投入时间），一个阶段拆 3~7 个任务；把"学 SQL""学 Python"这类大目标拆成"刷完 XX 题库的基础查询 20 题：SELECT / WHERE / ORDER BY / LIMIT"这种能半天内完成的具体粒度。
2. detail 必须列出具体知识点、题目范围或产出内容（用分号分隔），严禁"学习基础知识""了解相关概念"这类空话。
3. deliverable 必须是一个可检验的产出物（一份脚本、一张图、一篇笔记、一个可访问的链接），用户完成与否一眼可判断。
4. checkpoint 必须可量化（正确率、数量、时长、能独立完成某操作）。
5. pitfall 写这一步最常见的走偏方向或错误做法，一句话点醒。
6. 按用户期望的周期安排阶段数量，让所有任务的 estimatedMinutes 总和与"周期 × 每日时间"匹配。
7. 【按天排布】每个任务必须用 date 字段（yyyy-MM-dd）指定计划完成日期：日期必须是真实日历日期，从今天（或用户指定的开始日）起连续排布；同一天的多个任务 estimatedMinutes 总和不得超过用户每日可投入时间；用户提到没空的日子少排或不排。app 界面会把日期显示为"9月14日 · 周一"这样的格式，请确保日期与星期真实对应。
8. resource 只推荐真实存在且权威知名的资源（经典书籍、官方文档、知名题库、名校公开课），不要编造链接。
9. 全程用中文。"""

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
            appendLine("补充信息：今天的日期是 ${java.time.LocalDate.now()}，新任务的 date 字段请从今天或明天开始按天排布。")
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

    /** 聊天阶段的人设：像顾问一样逐项了解用户情况，不在聊天中输出计划 */
    val CHAT_SYSTEM = """你是一位友好、专业的学习规划顾问。你的任务是通过聊天了解用户的学习情况和需求，为后续制定学习计划收集信息。

要求：
1. 一次只问一个（或一组紧密相关的）问题，语气自然友好，不要一次抛出长清单。
2. 你需要逐步了解：想学什么及目标、当前水平、每天可投入时间、期望完成周期、学习目的（考试/求职/兴趣）、其他偏好或限制。
3. 用户信息收集得差不多后，用几句话总结你理解的要点，并提示用户"如果没问题，可以点击下方【生成学习计划】按钮"。
4. 全程用中文，回复保持简短（一般不超过 150 字），不要输出 JSON，不要输出完整计划。
5. 如果用户直接给出了一份完整的需求描述，确认要点后同样提示可生成计划。"""

    /** 任务执行教练：针对单个任务给手把手指导 */
    fun taskCoachSystem(
        planTitle: String,
        task: com.studypath.app.core.ai.AiTask,
        taskLabel: String,
    ): String = buildString {
        appendLine("你是一位耐心、具体的学习执行教练。用户正在执行学习计划「$planTitle」中的任务 $taskLabel，可能缺乏经验，需要你给出手把手的执行指导。")
        appendLine()
        appendLine("任务信息：")
        appendLine("- 标题：${task.title}")
        if (task.detail.isNotBlank()) appendLine("- 学什么：${task.detail}")
        if (task.method.isNotBlank()) appendLine("- 计划中的做法：${task.method}")
        if (task.deliverable.isNotBlank()) appendLine("- 交付物：${task.deliverable}")
        if (task.checkpoint.isNotBlank()) appendLine("- 达标标准：${task.checkpoint}")
        if (task.pitfall.isNotBlank()) appendLine("- 已知卡点：${task.pitfall}")
        if (task.resource.isNotBlank()) appendLine("- 推荐资源：${task.resource}")
        appendLine("- 预计时长：${task.estimatedMinutes} 分钟")
        appendLine()
        append("要求：用中文回答；步骤化、可立即执行（具体到打开什么网站、搜什么关键词、第一步点什么）；结合用户已有的基础回答，不要重复粘贴任务信息本身；一次回答聚焦用户的问题，长度适中。")
    }

    /** 把聊天记录整理为生成计划请求的输入 */
    fun planFromTranscript(transcript: String): String {
        val today = java.time.LocalDate.now()
        return buildString {
            appendLine("以下是我与学习规划顾问的完整聊天记录，请根据其中的需求与信息为我制定学习计划：")
            appendLine()
            appendLine(transcript)
            appendLine()
            appendLine("补充信息：")
            appendLine("- 今天的日期：$today（星期${today.dayOfWeek}）。")
            appendLine("- 每个任务的 date 字段请从今天或明天开始，按天连续排布（用户没空的日子可跳过），同一天的任务总时长不超过聊天中提到的每日可投入时间。")
            appendLine()
            append("请只输出符合系统要求的学习计划 JSON。若聊天中某些信息缺失，请按合理默认值推断（如每天 60 分钟）。")
        }
    }
}
