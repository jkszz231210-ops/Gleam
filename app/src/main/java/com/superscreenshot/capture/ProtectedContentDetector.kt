package com.superscreenshot.capture

import android.graphics.Bitmap
import kotlin.math.abs

object ProtectedContentDetector {
    /**
     * 目标：尽量只在“被系统置黑的受保护内容”时命中，避免把正常暗场误判。
     *
     * 经验策略：
     * - 受保护内容的截图往往是“几乎全 0/全黑”，而且噪声/纹理极少。
     * - 正常暗场（比如电影暗画面）通常仍有少量非零像素与纹理变化。
     */
    fun isLikelyProtected(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true

        // 采样降低开销
        val sampleW = 64
        val sampleH = 64
        val stepX = (w / sampleW).coerceAtLeast(1)
        val stepY = (h / sampleH).coerceAtLeast(1)

        var count = 0
        var nearBlack = 0
        var sum = 0.0
        var sumSq = 0.0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
                sum += luma
                sumSq += luma * luma

                if (r <= 2 && g <= 2 && b <= 2) nearBlack++
                count++
                x += stepX
            }
            y += stepY
        }

        if (count == 0) return true

        val mean = sum / count
        val variance = (sumSq / count) - mean * mean
        val std = kotlin.math.sqrt(abs(variance))
        val blackRatio = nearBlack.toDouble() / count.toDouble()

        // “几乎全黑 + 几乎无纹理变化”才判定为受保护
        return blackRatio >= 0.995 && std <= 1.2
    }
}


