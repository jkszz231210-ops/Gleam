package com.superscreenshot.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

object SimpleCompositor {
    /**
     * MVP 合成：把系统截图缩放裁切到与相机帧一致，然后用固定 alpha 叠加相机帧。
     *
     * 注意：这是“先跑通”的方案；真正的屏幕区域对齐/残差分层会在后续 todo 中实现。
     */
    fun simpleBlend(screenshot: Bitmap, cameraFrame: Bitmap, cameraAlpha: Float): Bitmap {
        val dstW = cameraFrame.width
        val dstH = cameraFrame.height
        val shot = centerCropTo(screenshot, dstW, dstH)

        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(shot, 0f, 0f, null)

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (255f * cameraAlpha.coerceIn(0f, 1f)).toInt()
            }
        canvas.drawBitmap(cameraFrame, 0f, 0f, paint)
        return out
    }

    private fun centerCropTo(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val scale = max(dstW.toFloat() / src.width.toFloat(), dstH.toFloat() / src.height.toFloat())
        val scaledW = (src.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = ((scaledW - dstW) / 2).coerceAtLeast(0)
        val y = ((scaledH - dstH) / 2).coerceAtLeast(0)
        val cropW = dstW.coerceAtMost(scaled.width - x)
        val cropH = dstH.coerceAtMost(scaled.height - y)
        return Bitmap.createBitmap(scaled, x, y, cropW, cropH)
    }
}


