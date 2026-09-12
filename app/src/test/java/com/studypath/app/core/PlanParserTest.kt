package com.studypath.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {

    @Test
    fun `parses plain json output`() {
        val json = """
            {
              "title": "Python 入门",
              "overview": "四周入门计划",
              "phases": [
                {
                  "title": "基础语法",
                  "summary": "变量与控制流",
                  "tasks": [
                    {"title": "学习变量与类型", "method": "看教程后写示例", "resource": "官方文档", "estimatedMinutes": 60}
                  ]
                }
              ]
            }
        """.trimIndent()
        val plan = PlanParser.parse(json)
        assertEquals("Python 入门", plan.title)
        assertEquals(1, plan.phases.size)
        assertEquals(60, plan.phases[0].tasks[0].estimatedMinutes)
    }

    @Test
    fun `parses output wrapped in markdown code fence`() {
        val wrapped = """
            好的，这是你的学习计划：
            ```json
            {"title":"T","overview":"O","phases":[{"title":"P1","summary":"S","tasks":[{"title":"T1","method":"M","resource":"R","estimatedMinutes":30}]}]}
            ```
            祝学习顺利！
        """.trimIndent()
        val plan = PlanParser.parse(wrapped)
        assertEquals("T", plan.title)
    }

    @Test
    fun `parses output with leading and trailing chatter`() {
        val noisy = """好的！{"title":"X","overview":"Y","phases":[{"title":"P","summary":"S","tasks":[]}]} 以上。"""
        val plan = PlanParser.parse(noisy)
        assertEquals("X", plan.title)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val plan = PlanParser.parse("""{"phases":[{"title":"P","summary":"S","tasks":[{"title":"t1"}]}]}""")
        assertEquals("", plan.overview)
        assertEquals(60, plan.phases[0].tasks[0].estimatedMinutes) // 默认 60
    }

    @Test
    fun `empty phases rejected`() {
        val thrown = runCatching { PlanParser.parse("""{"title":"X","phases":[]}""") }
        assertTrue(thrown.isFailure)
    }

    @Test
    fun `no json at all rejected`() {
        val thrown = runCatching { PlanParser.parse("抱歉，我无法生成计划。") }
        assertTrue(thrown.isFailure)
    }
}
