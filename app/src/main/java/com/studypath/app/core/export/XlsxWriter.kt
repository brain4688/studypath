package com.studypath.app.core.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 极简 .xlsx 生成器（零第三方依赖）：
 * 直接拼接 OOXML 部件并打包为 zip，字符串使用 inlineStr（免 sharedStrings），
 * 兼容 Microsoft Excel / WPS / LibreOffice。
 * 仅支持单工作表、字符串与数值单元格，足够覆盖"导出学习计划"场景。
 */
object XlsxWriter {

    fun write(sheetName: String, rows: List<List<Any>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbookXml(sanitizeSheetName(sheetName)))
            entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entry("xl/worksheets/sheet1.xml", sheetXml(rows))
        }
        return out.toByteArray()
    }

    // ---------- 各 OOXML 部件 ----------

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

    private fun workbookXml(sheetName: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${escape(sheetName)}" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun sheetXml(rows: List<List<Any>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<cols>""")
        append("""<col min="1" max="1" width="18" customWidth="1"/>""")
        append("""<col min="2" max="2" width="28" customWidth="1"/>""")
        append("""<col min="3" max="8" width="34" customWidth="1"/>""")
        append("""<col min="9" max="11" width="13" customWidth="1"/>""")
        append("""</cols><sheetData>""")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val ref = "${columnName(c)}${r + 1}"
                when (value) {
                    is Number -> append("""<c r="$ref"><v>${value}</v></c>""")
                    else -> append(
                        """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escape(value.toString())}</t></is></c>"""
                    )
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    // ---------- 工具 ----------

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** 0→A, 1→B, …, 26→AA */
    internal fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, 'A' + i % 26)
            i = i / 26 - 1
        }
        return sb.toString()
    }

    /** 工作表名非法字符过滤，最长 31 字符 */
    internal fun sanitizeSheetName(name: String): String =
        name.replace(Regex("""[\[\]:*?/\\]"""), " ").take(31).ifBlank { "Sheet1" }
}
