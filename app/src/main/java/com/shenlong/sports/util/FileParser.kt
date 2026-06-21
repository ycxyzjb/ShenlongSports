package com.shenlong.sports.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 文件解析工具 - 支持从Excel(.xlsx)和Word(.docx)文件导入运动员数据
 *
 * 原理：xlsx和docx本质上是ZIP压缩包，内部由XML文件组成。
 * 直接使用Android自带的XmlPullParser解析，无需引入第三方库。
 *
 * xlsx结构：
 *   - xl/sharedStrings.xml  存放所有单元格文本
 *   - xl/worksheets/sheet1.xml  存放单元格引用
 *
 * docx结构：
 *   - word/document.xml  存放正文内容
 */
object FileParser {

    /**
     * 解析文件，返回运动员数据列表
     * 每条数据格式：号码,姓名,单位
     */
    fun parseFile(context: Context, uri: Uri): ParseResult {
        val fileName = getFileName(context, uri) ?: ""

        return when {
            fileName.endsWith(".xlsx", ignoreCase = true) -> parseXlsx(context, uri)
            fileName.endsWith(".docx", ignoreCase = true) -> parseDocx(context, uri)
            fileName.endsWith(".csv", ignoreCase = true) ||
            fileName.endsWith(".txt", ignoreCase = true) -> parseText(context, uri)
            else -> {
                // 尝试根据MIME类型判断
                val mimeType = context.contentResolver.getType(uri) ?: ""
                when {
                    mimeType.contains("spreadsheet") ||
                    mimeType.contains("excel") ||
                    mimeType.contains("xlsx") -> parseXlsx(context, uri)
                    mimeType.contains("word") ||
                    mimeType.contains("docx") -> parseDocx(context, uri)
                    else -> parseText(context, uri)
                }
            }
        }
    }

    /**
     * 解析Excel(.xlsx)文件
     */
    private fun parseXlsx(context: Context, uri: Uri): ParseResult {
        val rows = mutableListOf<List<String>>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val zipStream = ZipInputStream(input)

                // 第一步：读取共享字符串表
                val sharedStrings = mutableListOf<String>()
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        sharedStrings.addAll(parseSharedStrings(zipStream))
                    }
                    entry = zipStream.nextEntry
                }

                // 第二步：读取工作表
                zipStream.close()
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    val zipStream2 = ZipInputStream(input2)
                    var entry2 = zipStream2.nextEntry
                    while (entry2 != null) {
                        if (entry2.name == "xl/worksheets/sheet1.xml") {
                            rows.addAll(parseSheet(zipStream2, sharedStrings))
                        }
                        entry2 = zipStream2.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Excel文件解析失败：${e.message}")
        }

        // 转换为运动员数据
        return convertRowsToAthletes(rows, errors)
    }

    /**
     * 解析xlsx中的共享字符串表
     */
    private fun parseSharedStrings(inputStream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var inSi = false  // 在<si>标签内
        var currentText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            inSi = true
                            currentText = StringBuilder()
                        }
                        "t" -> {
                            if (inSi) {
                                val text = parser.nextText()
                                currentText.append(text)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si" && inSi) {
                        inSi = false
                        strings.add(currentText.toString().trim())
                    }
                }
            }
            eventType = parser.next()
        }

        return strings
    }

    /**
     * 解析xlsx中的工作表
     */
    private fun parseSheet(inputStream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()

        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var inCell = false
        var cellType = ""  // "s"=共享字符串引用, 其他=内联值
        var inValue = false
        var cellValue = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow.clear()
                        }
                        "c" -> {
                            inCell = true
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue = ""
                        }
                        "v" -> {
                            if (inCell) {
                                inValue = true
                                cellValue = parser.nextText() ?: ""
                            }
                        }
                        "t" -> {
                            // 内联字符串（部分xlsx不使用共享字符串表）
                            if (inCell && cellType == "inlineStr") {
                                inValue = true
                                cellValue = parser.nextText() ?: ""
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            inCell = false
                            // 根据类型解析单元格值
                            val value = when {
                                cellType == "s" && cellValue.isNotEmpty() -> {
                                    val index = cellValue.toIntOrNull() ?: -1
                                    if (index in sharedStrings.indices) sharedStrings[index] else cellValue
                                }
                                cellType == "inlineStr" -> cellValue
                                cellType == "b" -> {
                                    // 布尔值
                                    if (cellValue == "1") "TRUE" else "FALSE"
                                }
                                else -> cellValue
                            }
                            currentRow.add(value)
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                rows.add(currentRow.toList())
                            }
                        }
                        "v" -> inValue = false
                    }
                }
            }
            eventType = parser.next()
        }

        return rows
    }

    /**
     * 解析Word(.docx)文件
     * 提取文档中的表格和纯文本
     */
    private fun parseDocx(context: Context, uri: Uri): ParseResult {
        val rows = mutableListOf<List<String>>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val zipStream = ZipInputStream(input)
                var entry = zipStream.nextEntry

                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        parseDocxContent(zipStream, rows)
                    }
                    entry = zipStream.nextEntry
                }
            }
        } catch (e: Exception) {
            errors.add("Word文件解析失败：${e.message}")
        }

        return convertRowsToAthletes(rows, errors)
    }

    /**
     * 解析docx的document.xml
     * 提取表格中的数据（Word中运动员数据通常以表格形式存在）
     */
    private fun parseDocxContent(inputStream: InputStream, rows: MutableList<List<String>>) {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var inTableRow = false
        var inTableCell = false
        var currentRow = mutableListOf<String>()
        var cellText = StringBuilder()
        var inParagraph = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "tr" -> {
                            inTableRow = true
                            currentRow = mutableListOf()
                        }
                        "tc" -> {
                            inTableCell = true
                            cellText = StringBuilder()
                        }
                        "p" -> {
                            if (inTableCell) {
                                inParagraph = true
                                // 段落间加换行（同一单元格多段落）
                                if (cellText.isNotEmpty()) cellText.append("\n")
                            }
                        }
                        "t" -> {
                            if (inParagraph || inTableCell) {
                                val text = parser.nextText() ?: ""
                                cellText.append(text)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "p" -> inParagraph = false
                        "tc" -> {
                            inTableCell = false
                            currentRow.add(cellText.toString().trim())
                        }
                        "tr" -> {
                            inTableRow = false
                            if (currentRow.isNotEmpty()) {
                                rows.add(currentRow.toList())
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        // 如果没有表格，尝试提取纯文本中的行
        if (rows.isEmpty()) {
            parseDocxPlainText(inputStream, rows)
        }
    }

    /**
     * 当Word中没有表格时，提取纯文本按行解析
     */
    private fun parseDocxPlainText(inputStream: InputStream, rows: MutableList<List<String>>) {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        val text = parser.nextText() ?: ""
                        currentLine.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p") {
                        val line = currentLine.toString().trim()
                        if (line.isNotEmpty()) {
                            lines.add(line)
                        }
                        currentLine = StringBuilder()
                    }
                }
            }
            eventType = parser.next()
        }

        // 将每行文本按分隔符拆分
        for (line in lines) {
            val parts = line.split(",", "，", "\t", " ").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) {
                rows.add(parts)
            }
        }
    }

    /**
     * 解析纯文本文件（TXT/CSV）
     */
    private fun parseText(context: Context, uri: Uri): ParseResult {
        val errors = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val content = input.bufferedReader().readText()
                content.split("\n").forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        val parts = trimmed.split(",", "，", "\t").map { it.trim() }.filter { it.isNotEmpty() }
                        if (parts.isNotEmpty()) {
                            rows.add(parts)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("文本文件解析失败：${e.message}")
        }

        return convertRowsToAthletes(rows, errors)
    }

    /**
     * 将行数据转换为运动员格式
     * 支持以下列格式：
     * - 号码, 姓名, 单位
     * - 序号, 号码, 姓名, 单位（自动跳过序号列）
     * - 姓名, 号码, 单位（自动识别号码列）
     */
    private fun convertRowsToAthletes(rows: List<List<String>>, errors: MutableList<String>): ParseResult {
        val lines = mutableListOf<String>()
        var skippedHeader = false

        for ((index, row) in rows.withIndex()) {
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            // 跳过表头行（第一行包含"号码"、"姓名"、"序号"等关键词）
            if (!skippedHeader && index == 0) {
                val headerText = row.joinToString("").lowercase()
                if (headerText.contains("号码") || headerText.contains("姓名") ||
                    headerText.contains("序号") || headerText.contains("编号") ||
                    headerText.contains("单位") || headerText.contains("队伍") ||
                    headerText.contains("name") || headerText.contains("number") ||
                    headerText.contains("no") || headerText.contains("team")
                ) {
                    skippedHeader = true
                    continue
                }
                skippedHeader = true
            }

            // 尝试从行数据中提取号码、姓名、单位
            val athleteData = extractAthleteFromRow(row)
            if (athleteData != null) {
                lines.add(athleteData)
            }
        }

        return ParseResult(lines, errors)
    }

    /**
     * 从一行数据中提取运动员信息
     * 返回格式：号码,姓名,单位
     */
    private fun extractAthleteFromRow(row: List<String>): String? {
        val nonEmpty = row.map { it.trim() }.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return null

        // 尝试找到号码列（纯数字或数字开头的列）
        val numberIndex = nonEmpty.indexOfFirst {
            it.matches(Regex("^\\d+$")) || it.matches(Regex("^\\d+[号#]$"))
        }

        return when {
            // 找到了号码列
            numberIndex >= 0 -> {
                val number = nonEmpty[numberIndex].removeSuffix("号").removeSuffix("#")
                val name = if (numberIndex + 1 < nonEmpty.size) nonEmpty[numberIndex + 1] else ""
                val team = if (numberIndex + 2 < nonEmpty.size) nonEmpty[numberIndex + 2] else ""
                if (name.isNotBlank()) "$number,$name,$team" else null
            }
            // 2列：号码,姓名
            nonEmpty.size == 2 -> {
                val first = nonEmpty[0]
                val second = nonEmpty[1]
                // 判断哪个是号码（纯数字）
                if (first.all { it.isDigit() }) "$first,$second,"
                else if (second.all { it.isDigit() }) "$second,$first,"
                else null
            }
            // 3列及以上：可能是 序号,号码,姓名,单位 或 号码,姓名,单位
            nonEmpty.size >= 3 -> {
                // 第一列是纯数字，可能是序号也可能是号码
                val first = nonEmpty[0]
                val second = nonEmpty[1]
                if (first.all { it.isDigit() } && second.all { it.isDigit() }) {
                    // 两列都是数字，第一列是序号，第二列是号码
                    val number = second
                    val name = nonEmpty[2]
                    val team = if (nonEmpty.size > 3) nonEmpty[3] else ""
                    "$number,$name,$team"
                } else if (first.all { it.isDigit() }) {
                    // 第一列是号码
                    val number = first
                    val name = second
                    val team = nonEmpty[2]
                    "$number,$name,$team"
                } else {
                    // 无法确定号码列，尝试用第二列
                    null
                }
            }
            else -> null
        }
    }

    /**
     * 获取文件名
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = it.getString(nameIndex)
            }
        }
        return name
    }
}

/**
 * 文件解析结果
 */
data class ParseResult(
    val lines: List<String>,  // 格式：号码,姓名,单位
    val errors: List<String>
)
