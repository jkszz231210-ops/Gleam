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

            val warped = warpScreenshotToQuad(screenshot, camMat.size(), quad.asList())
                ?: return fallback(screenshot, cameraFrame, alpha)

            // residual = camera - warped (clamp 0..255)
            val residual = Mat()
            Core.subtract(camMat, warped, residual)

            // output = warped + alpha * residual
            val out = Mat()
            Core.addWeighted(warped, 1.0, residual, alpha.coerceIn(0f, 1f).toDouble(), 0.0, out)

            val outBmp = Bitmap.createBitmap(cameraFrame.width, cameraFrame.height, Bitmap.Config.ARGB_8888)
            val warpedBmp = Bitmap.createBitmap(cameraFrame.width, cameraFrame.height, Bitmap.Config.ARGB_8888)
            val residualBmp = Bitmap.createBitmap(cameraFrame.width, cameraFrame.height, Bitmap.Config.ARGB_8888)

            // 转回 RGBA，方便 Bitmap 接收
            val outRgba = Mat()
            val warpedRgba = Mat()
            val residualRgba = Mat()
            Imgproc.cvtColor(out, outRgba, Imgproc.COLOR_RGB2RGBA)
            Imgproc.cvtColor(warped, warpedRgba, Imgproc.COLOR_RGB2RGBA)
            Imgproc.cvtColor(residual, residualRgba, Imgproc.COLOR_RGB2RGBA)

            Utils.matToBitmap(outRgba, outBmp)
            Utils.matToBitmap(warpedRgba, warpedBmp)
            Utils.matToBitmap(residualRgba, residualBmp)

            Result(
                output = outBmp,
                screenWarped = warpedBmp,
                residual = residualBmp,
                usedFallback = false,
            )
        } catch (t: Throwable) {
            // 捕获 UnsatisfiedLinkError 或 Mat 操作异常
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
        val out = SimpleCompositor.simpleBlend(screenshot, cameraFrame, cameraAlpha = alpha)
        return Result(
            output = out,
            screenWarped = out,
            residual = out,
            usedFallback = true,
        )
    }
}


