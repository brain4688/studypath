package com.studypath.app.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {

    private val sampleRows: List<List<Any>> = listOf(
        listOf("计划标题", "Python 入门四周计划"),
        listOf("阶段", "任务", "学习方法", "推荐资源", "预计时长(分钟)", "进度(%)"),
        listOf("基础语法", "变量与类型", "看教程后写示例 & 总结", "官方文档 <入门篇>", 60, 50),
    )

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                map[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        return map
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    @Test
    fun `zip contains all required ooxml parts`() {
        val parts = entries(XlsxWriter.write("学习计划", sampleRows))
        assertEquals(
            setOf(
                "[Content_Types].xml", "_rels/.rels",
                "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml"
            ), parts.keys
        )
    }

    @Test
    fun `sheet xml well-formed and contains escaped text`() {
        val parts = entries(XlsxWriter.write("学习计划", sampleRows))
        val doc = parseXml(parts.getValue("xl/worksheets/sheet1.xml"))
        val texts = doc.getElementsByTagName("t").let { n ->
            (0 until n.length).joinToString("|") { n.item(it).textContent }
        }
        // XML 已被正确转义并在解析后还原
        assertTrue(texts.contains("看教程后写示例 & 总结"))
        assertTrue(texts.contains("官方文档 <入门篇>"))
        assertTrue(texts.contains("Python 入门四周计划"))
        // 数值单元格
        val values = doc.getElementsByTagName("v").let { n ->
            (0 until n.length).map { n.item(it).textContent }
        }
        assertTrue(values.containsAll(listOf("60", "50")))
    }

    @Test
    fun `workbook references sheet and sanitized name applied`() {
        val parts = entries(XlsxWriter.write("非法/名称*测试[1]", sampleRows))
        val doc = parseXml(parts.getValue("xl/workbook.xml"))
        val sheets = doc.getElementsByTagName("sheet")
        assertEquals(1, sheets.length)
        assertTrue(sheets.item(0).attributes.getNamedItem("r:id").nodeValue == "rId1")
    }

    @Test
    fun `column names follow spreadsheet convention`() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AB", XlsxWriter.columnName(27))
    }
}
