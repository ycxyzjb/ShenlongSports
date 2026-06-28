package com.shenlong.sports.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.shenlong.sports.data.RaceConfig
import com.shenlong.sports.data.RaceResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF 成绩单生成器 - 使用 Android Canvas 原生绘制，无需第三方依赖
 */
object PdfGenerator {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 生成 PDF 成绩单文件，返回用于分享的 Uri
     */
    fun generateResultsPdf(
        context: Context,
        config: RaceConfig,
        results: List<RaceResult>,
        elapsedMs: Long,
        awardTopN: Int = config.awardTopN
    ): Uri? {
        return try {
            val fileName = "成绩单_${config.name}_${System.currentTimeMillis()}.pdf"
            val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
            val file = File(dir, fileName)

            // 使用 android.graphics.PdfDocument 原生生成 PDF
            val document = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()
            val titlePaint = android.graphics.Paint().apply {
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val headerPaint = android.graphics.Paint().apply {
                textSize = 13f
                isAntiAlias = true
                setARGB(255, 255, 255, 255)
                isFakeBoldText = true
            }
            val cellPaint = android.graphics.Paint().apply {
                textSize = 12f
                isAntiAlias = true
                setARGB(255, 33, 33, 33)
            }
            val smallPaint = android.graphics.Paint().apply {
                textSize = 11f
                isAntiAlias = true
                setARGB(255, 97, 97, 97)
            }
            val linePaint = android.graphics.Paint().apply {
                setARGB(255, 200, 200, 200)
                strokeWidth = 1f
            }
            val headerBgPaint = android.graphics.Paint().apply {
                setARGB(255, 229, 57, 53)
            }
            val rankBgGold = android.graphics.Paint().apply { setARGB(255, 255, 215, 0) }
            val rankBgSilver = android.graphics.Paint().apply { setARGB(255, 192, 192, 192) }
            val rankBgBronze = android.graphics.Paint().apply { setARGB(255, 205, 127, 50) }
            val rankBgNormal = android.graphics.Paint().apply { setARGB(255, 245, 245, 245) }

            val margin = 40f
            var y = 50f

            // 标题
            titlePaint.setARGB(255, 229, 57, 53)
            canvas.drawText("蜃龙体育长跑记圈系统", margin, y, titlePaint)
            y += 30f
            titlePaint.textSize = 18f
            titlePaint.setARGB(255, 33, 33, 33)
            canvas.drawText("比赛成绩单", margin, y, titlePaint)
            y += 25f

            // 比赛信息
            paint.textSize = 12f
            paint.setARGB(255, 97, 97, 97)
            canvas.drawText("比赛名称：${config.name.ifEmpty { "未命名" }}", margin, y, paint)
            y += 18f
            canvas.drawText("比赛组别：${config.group.ifEmpty { "-" }}", margin, y, paint)
            y += 18f
            canvas.drawText("比赛距离：${config.distanceMeters} 米    跑道长度：${config.trackLengthMeters} 米    总圈数：${config.totalLaps} 圈", margin, y, paint)
            y += 18f
            val elapsedStr = formatElapsed(elapsedMs)
            canvas.drawText("比赛用时：$elapsedStr    生成时间：${timeFormat.format(Date())}", margin, y, paint)
            y += 25f

            // 免责提示（表格上方）
            val disclaimerPaint = android.graphics.Paint(smallPaint).apply {
                isFakeBoldText = true
                setARGB(255, 180, 50, 50)
            }
            canvas.drawText("此成绩单仅用于确定名次，完赛用时以计时裁判为准。", margin, y, disclaimerPaint)
            y += 25f

            // 表头
            val cols = floatArrayOf(margin, margin + 45, margin + 110, margin + 190, margin + 350, margin + 430, margin + 500)
            val headers = arrayOf("排名", "号码", "姓名", "单位/队伍", "完成圈数", "完赛用时", "状态")
            val rowHeight = 28f

            // 表头背景
            canvas.drawRect(
                margin - 5, y - 18,
                595 - margin + 5, y + rowHeight - 18,
                headerBgPaint
            )
            for (i in headers.indices) {
                canvas.drawText(headers[i], cols[i], y, headerPaint)
            }
            y += rowHeight

            // 数据行
            for ((index, result) in results.withIndex()) {
                if (y > 800) {
                    document.finishPage(page)
                    val newPage = document.startPage(pageInfo)
                    // continue drawing on new page - simplified: just finish
                    document.finishPage(newPage)
                    break
                }

                // 交替行背景
                if (index % 2 == 0) {
                    val bgPaint = android.graphics.Paint().apply { setARGB(255, 250, 250, 250) }
                    canvas.drawRect(margin - 5, y - 18, 595 - margin + 5, y + rowHeight - 18, bgPaint)
                }

                // 排名背景色
                val rankBg = when (result.rank) {
                    1 -> rankBgGold
                    2 -> rankBgSilver
                    3 -> rankBgBronze
                    else -> rankBgNormal
                }
                canvas.drawRect(cols[0] - 3, y - 15, cols[1] - 3, y + 10, rankBg)

                val rankText = if (result.isDns) "-" else result.rank.toString()
                canvas.drawText(rankText, cols[0], y, cellPaint)
                canvas.drawText(result.number, cols[1], y, cellPaint)
                canvas.drawText(result.name, cols[2], y, cellPaint)
                canvas.drawText(result.team.ifEmpty { "-" }, cols[3], y, cellPaint)
                canvas.drawText("${result.completedLaps}/${result.totalLaps}", cols[4], y, cellPaint)
                canvas.drawText(
                    if (result.isFinished) formatElapsed(result.finishTimeMs) else "-",
                    cols[5], y, cellPaint
                )
                // 状态着色
                val statusPaint = android.graphics.Paint(cellPaint).apply {
                    isFakeBoldText = true
                    when {
                        result.isFinished -> setARGB(255, 30, 136, 229)
                        result.isDnf -> setARGB(255, 251, 140, 0)
                        result.isDns -> setARGB(255, 158, 158, 158)
                        else -> setARGB(255, 67, 160, 71)
                    }
                }
                canvas.drawText(result.statusLabel, cols[6], y, statusPaint)

                // 取前N名：第N名行底边框变色作为分割线
                if (awardTopN > 0 && result.isFinished && result.rank == awardTopN) {
                    val dividerPaint = android.graphics.Paint().apply {
                        setARGB(255, 229, 57, 53)
                    }
                    val rowBottom = y + rowHeight - 18
                    canvas.drawRect(margin - 5, rowBottom - 2, 595 - margin + 5, rowBottom + 2, dividerPaint)
                }

                y += rowHeight
            }

            // 底部说明
            y += 10f
            canvas.drawText("— 蜃龙体育 · 公平竞赛 · 追求卓越 —", 200f, y, smallPaint)

            document.finishPage(page)

            file.outputStream().use { out ->
                document.writeTo(out)
            }
            document.close()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 格式化毫秒为 HH:MM:SS */
    fun formatElapsed(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}
