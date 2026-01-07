package com.superscreenshot.capture

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

object HomographyCompositor {
    var isOpenCVReady: Boolean = false
        private set

    fun initOpenCV(): Boolean {
        isOpenCVReady = org.opencv.android.OpenCVLoader.initDebug()
        return isOpenCVReady
    }

    data class Result(
        val output: Bitmap,
        val screenWarped: Bitmap,
        val residual: Bitmap,
        val usedFallback: Boolean,
    )

    /**
     * 输出尺寸以 cameraFrame 为准：更贴近“生活记录”，保留边缘/手/环境暗部。
     *
     * 合成思路：
     * - 在 cameraFrame 中检测屏幕四边形 quad
     * - 将 screenshot 通过 homography warp 到 quad 区域（得到 screenWarpedFull）
     * - residual = clamp(camera - screenWarpedFull)
     * - output = screenWarpedFull + alpha * residual
     */
    /**
     * 核心改进：以截图（screenshot）的分辨率为输出基准，
     * 将相机帧（cameraFrame）中的反射层 逆向 Warp 到截图空间。
     */
    fun compose(
        screenshot: Bitmap,
        cameraFrame: Bitmap,
        alpha: Float = 0.45f,
        edgeWeight: Float = 0.25f,
    ): Result {
        if (!isOpenCVReady) return fallback(screenshot, cameraFrame, alpha)

        return try {
            val quad = ScreenQuadDetector.detect(cameraFrame)
                ?: return fallback(screenshot, cameraFrame, alpha)

            val camMat = Mat()
            Utils.bitmapToMat(cameraFrame, camMat)
            Imgproc.cvtColor(camMat, camMat, Imgproc.COLOR_RGBA2RGB)

            val shotW = screenshot.width
            val shotH = screenshot.height
            val outputBmp = screenshot.copy(Bitmap.Config.ARGB_8888, true)

            // 计算从 相机四边形 到 截图矩形 的变换
            val src = MatOfPoint2f(quad.p0, quad.p1, quad.p2, quad.p3) // 相机中的屏幕四角
            val dst = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(shotW.toDouble(), 0.0),
                Point(shotW.toDouble(), shotH.toDouble()),
                Point(0.0, shotH.toDouble())
            )

            val h = Imgproc.getPerspectiveTransform(src, dst)
            val reflectionWarped = Mat(Size(shotW.toDouble(), shotH.toDouble()), camMat.type())
            Imgproc.warpPerspective(camMat, reflectionWarped, h, reflectionWarped.size())

            // 现在 reflectionWarped 就是对齐到截图分辨率的“反射层”
            val shotMat = Mat()
            Utils.bitmapToMat(screenshot, shotMat)
            Imgproc.cvtColor(shotMat, shotMat, Imgproc.COLOR_RGBA2RGB)

            // residual = reflectionWarped - shotMat (提取纯反射)
            val residual = Mat()
            Core.subtract(reflectionWarped, shotMat, residual)

            // 关键：只在“屏幕暗部”展示反射，避免整体变亮/自拍感
            val residualMasked = applyDarkScreenMask(shotMat, residual, edgeWeight)

            // output = shotMat + alpha * residualMasked
            val out = Mat()
            Core.addWeighted(
                shotMat,
                1.0,
                residualMasked,
                alpha.coerceIn(0f, 1f).toDouble(),
                0.0,
                out,
            )

            val outRgba = Mat()
            val residualRgba = Mat()
            Imgproc.cvtColor(out, outRgba, Imgproc.COLOR_RGB2RGBA)
            Imgproc.cvtColor(residualMasked, residualRgba, Imgproc.COLOR_RGB2RGBA)

            val residualBmp = Bitmap.createBitmap(shotW, shotH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outRgba, outputBmp)
            Utils.matToBitmap(residualRgba, residualBmp)

            Result(
                output = outputBmp,
                screenWarped = screenshot, // 在此模式下 base 就是原始截图
                residual = residualBmp,
                usedFallback = false,
            )
        } catch (t: Throwable) {
            fallback(screenshot, cameraFrame, alpha)
        }
    }

    fun composeFromWarpedAndResidual(screenWarped: Bitmap, residual: Bitmap, alpha: Float, edgeWeight: Float = 0.25f): Bitmap {
        val w = screenWarped.width
        val h = screenWarped.height
        require(residual.width == w && residual.height == h)

        val base = Mat()
        val res = Mat()
        Utils.bitmapToMat(screenWarped, base)
        Utils.bitmapToMat(residual, res)
        Imgproc.cvtColor(base, base, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(res, res, Imgproc.COLOR_RGBA2RGB)

        val resMasked = applyDarkScreenMask(base, res, edgeWeight)

        val out = Mat()
        Core.addWeighted(base, 1.0, resMasked, alpha.coerceIn(0f, 1f).toDouble(), 0.0, out)

        val outRgba = Mat()
        Imgproc.cvtColor(out, outRgba, Imgproc.COLOR_RGB2RGBA)
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outRgba, outBmp)
        return outBmp
    }

    private fun warpScreenshotToQuad(
        screenshot: Bitmap,
        camSize: Size,
        quadTlTrBrBl: List<Point>,
    ): Mat? {
        if (quadTlTrBrBl.size != 4) return null

        val shotMat = Mat()
        Utils.bitmapToMat(screenshot, shotMat)
        Imgproc.cvtColor(shotMat, shotMat, Imgproc.COLOR_RGBA2RGB)

        // 源点：截图四角
        val src =
            MatOfPoint2f(
                Point(0.0, 0.0),
                Point(screenshot.width.toDouble(), 0.0),
                Point(screenshot.width.toDouble(), screenshot.height.toDouble()),
                Point(0.0, screenshot.height.toDouble()),
            )

        val (tl, tr, br, bl) = quadTlTrBrBl
        val dst = MatOfPoint2f(tl, tr, br, bl)

        val h = Imgproc.getPerspectiveTransform(src, dst)

        val out = Mat(camSize, CvType.CV_8UC3, Scalar(0.0, 0.0, 0.0))
        Imgproc.warpPerspective(
            shotMat,
            out,
            h,
            camSize,
            Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT,
            Scalar(0.0, 0.0, 0.0),
        )
        return out
    }

    private fun fallback(screenshot: Bitmap, cameraFrame: Bitmap, alpha: Float): Result {
        // 回退模式下，依然以截图分辨率为准
        val shotW = screenshot.width
        val shotH = screenshot.height
        
        // 将相机帧缩放到截图大小作为残差层
        val cameraScaled = Bitmap.createScaledBitmap(cameraFrame, shotW, shotH, true)
        // 回退也做暗部遮罩（纯 Kotlin 版本会更慢，这里用简单“降低强度”替代）
        val out = SimpleCompositor.simpleBlend(screenshot, cameraScaled, cameraAlpha = (alpha * 0.55f))
        
        return Result(
            output = out,
            screenWarped = screenshot,
            residual = cameraScaled,
            usedFallback = true,
        )
    }

    /**
     * 根据屏幕内容亮度生成遮罩：越暗 -> 越显示反射；越亮 -> 越抑制反射。
     */
    private fun applyDarkScreenMask(screenRgb: Mat, residualRgb: Mat, edgeWeight: Float): Mat {
        // 核心目标：
        // 1) 反射强度随屏幕亮度变化：屏幕亮 -> 反射几乎消失；屏幕暗 -> 反射明显
        // 2) 反射强调“轮廓”，弱化内部细节：更像玻璃里隐隐出现的人/环境，而不是相机拍清细节
        //
        // mask = (1 - gray(screen))^3  -> 暗部更强，亮部更弱
        val gray = Mat()
        Imgproc.cvtColor(screenRgb, gray, Imgproc.COLOR_RGB2GRAY)

        val grayF = Mat()
        gray.convertTo(grayF, CvType.CV_32F, 1.0 / 255.0)

        // 全局强度（屏幕越亮越趋近 0）
        val mean = Core.mean(grayF).`val`[0].coerceIn(0.0, 1.0)
        val global = ((1.0 - mean) * (1.0 - mean)).coerceIn(0.0, 1.0)

        val inv = Mat(grayF.size(), CvType.CV_32F, Scalar(1.0))
        Core.subtract(inv, grayF, inv) // inv = 1 - gray
        Core.multiply(inv, inv, inv) // inv = inv^2
        Core.multiply(inv, inv, inv) // inv = inv^3

        // residualF = residual * inv * strength
        val residualF = Mat()
        residualRgb.convertTo(residualF, CvType.CV_32F)

        val inv3 = Mat()
        Imgproc.cvtColor(inv, inv3, Imgproc.COLOR_GRAY2RGB) // 3 通道遮罩

        Core.multiply(residualF, inv3, residualF)
        // 屏幕越暗 global 越大，整体反射越强；同时整体强度更大一些，解决“暗屏反射不够”
        Core.multiply(residualF, Scalar(0.45 * global, 0.45 * global, 0.45 * global), residualF)

        val masked8 = Mat()
        residualF.convertTo(masked8, CvType.CV_8UC3) // 回到 0..255

        // 细节处理：模糊 + 边缘增强（“轮廓清晰、内部朦胧”）
        val blurred = Mat()
        Imgproc.GaussianBlur(masked8, blurred, Size(0.0, 0.0), 3.0)

        val grayRes = Mat()
        Imgproc.cvtColor(blurred, grayRes, Imgproc.COLOR_RGB2GRAY)

        val edges = Mat()
        Imgproc.Canny(grayRes, edges, 12.0, 40.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)

        val edgesRgb = Mat()
        Imgproc.cvtColor(edges, edgesRgb, Imgproc.COLOR_GRAY2RGB)

        val out = Mat()
        // edges 只做轻量增强，避免出现“线稿感”
        Core.addWeighted(blurred, 1.0, edgesRgb, edgeWeight.coerceIn(0f, 1f).toDouble(), 0.0, out)
        return out
    }
}


