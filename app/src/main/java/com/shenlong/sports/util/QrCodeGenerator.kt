package com.shenlong.sports.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码生成工具 - 为运动员号码布生成二维码
 *
 * 二维码内容格式: SL<号码>
 * 例如: 1号运动员 -> SL1, 12号运动员 -> SL12
 * SL前缀用于区分其他二维码，避免误识别
 */
object QrCodeGenerator {

    /**
     * 生成带编号和姓名的号码布二维码图片
     * @param number 运动员号码
     * @param name 运动员姓名（可选）
     * @param qrSize 二维码尺寸
     * @return 包含二维码+编号+姓名的完整图片
     */
    fun generate(number: String, name: String = "", qrSize: Int = 512): Bitmap {
        val content = "SL$number"
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
        val qrBitmap = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.ARGB_8888)
        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                qrBitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        // 计算文字区域高度
        val numberTextSize = qrSize * 0.18f  // 编号字号
        val nameTextSize = qrSize * 0.10f    // 姓名字号
        val padding = qrSize * 0.06f
        val textAreaHeight = numberTextSize + (if (name.isNotEmpty()) nameTextSize + padding else 0f) + padding * 3

        // 创建完整图片：二维码 + 文字区域
        val totalHeight = qrSize + textAreaHeight.toInt()
        val result = Bitmap.createBitmap(qrSize, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 白色背景
        canvas.drawColor(Color.WHITE)

        // 绘制二维码
        canvas.drawBitmap(qrBitmap, 0f, 0f, null)

        // 绘制编号（大号红色）
        val numberPaint = Paint().apply {
            color = Color.RED
            textSize = numberTextSize
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val numberY = qrSize + padding + numberTextSize
        canvas.drawText("${number}号", qrSize / 2f, numberY, numberPaint)

        // 绘制姓名（中号黑色）
        if (name.isNotEmpty()) {
            val namePaint = Paint().apply {
                color = Color.BLACK
                textSize = nameTextSize
                typeface = Typeface.DEFAULT
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val nameY = numberY + padding + nameTextSize
            canvas.drawText(name, qrSize / 2f, nameY, namePaint)
        }

        qrBitmap.recycle()
        return result
    }

    /**
     * 检查扫描到的内容是否为本APP生成的二维码
     * 返回运动员号码，如果不是则返回null
     */
    fun parseAthleteNumber(content: String): String? {
        return if (content.startsWith("SL")) content.removePrefix("SL") else null
    }
}
