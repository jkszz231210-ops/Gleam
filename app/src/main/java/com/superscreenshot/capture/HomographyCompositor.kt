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

            // output = shotMat + alpha * residual
            val out = Mat()
            Core.addWeighted(shotMat, 1.0, residual, alpha.coerceIn(0f, 1f).toDouble(), 0.0, out)

            val outRgba = Mat()
            val residualRgba = Mat()
            Imgproc.cvtColor(out, outRgba, Imgproc.COLOR_RGB2RGBA)
            Imgproc.cvtColor(residual, residualRgba, Imgproc.COLOR_RGB2RGBA)

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

    fun composeFromWarpedAndResidual(screenWarped: Bitmap, residual: Bitmap, alpha: Float): Bitmap {
        val w = screenWarped.width
        val h = screenWarped.height
        require(residual.width == w && residual.height == h)

        val base = Mat()
        val res = Mat()
        Utils.bitmapToMat(screenWarped, base)
        Utils.bitmapToMat(residual, res)
        Imgproc.cvtColor(base, base, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(res, res, Imgproc.COLOR_RGBA2RGB)

        val out = Mat()
        Core.addWeighted(base, 1.0, res, alpha.coerceIn(0f, 1f).toDouble(), 0.0, out)

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
        val out = SimpleCompositor.simpleBlend(screenshot, cameraScaled, cameraAlpha = alpha)
        
        return Result(
            output = out,
            screenWarped = screenshot,
            residual = cameraScaled,
            usedFallback = true,
        )
    }
}


