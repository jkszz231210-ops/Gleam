package com.superscreenshot.capture

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

object ScreenQuadDetector {

    data class Quad(val p0: Point, val p1: Point, val p2: Point, val p3: Point) {
        fun asList(): List<Point> = listOf(p0, p1, p2, p3)
    }

    /**
     * 从相机帧中找“最大且近似矩形”的四边形，作为屏幕区域。
     * 找不到则返回 null。
     */
    fun detect(cameraFrame: Bitmap): Quad? {
        val rgba = Mat()
        Utils.bitmapToMat(cameraFrame, rgba)

        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 60.0, 160.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var bestQuad: List<Point>? = null
        var bestArea = 0.0

        val approx = MatOfPoint2f()
        val contour2f = MatOfPoint2f()

        for (c in contours) {
            contour2f.fromList(c.toList())
            val peri = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
            val pts = approx.toList()
            if (pts.size != 4) continue

            val area = abs(Imgproc.contourArea(MatOfPoint(*pts.toTypedArray())))
            if (area < bestArea) continue

            // 过滤太小的候选（屏幕一般占画面不小比例）
            val frameArea = cameraFrame.width.toDouble() * cameraFrame.height.toDouble()
            if (area < frameArea * 0.08) continue

            bestArea = area
            bestQuad = pts
        }

        val ordered = bestQuad?.let { orderQuadPoints(it) } ?: return null
        return Quad(ordered[0], ordered[1], ordered[2], ordered[3])
    }

    /**
     * 返回顺序：tl, tr, br, bl
     */
    private fun orderQuadPoints(pts: List<Point>): List<Point> {
        require(pts.size == 4)

        val sumSorted = pts.sortedBy { it.x + it.y }
        val diffSorted = pts.sortedBy { it.y - it.x }

        val tl = sumSorted.first()
        val br = sumSorted.last()
        val tr = diffSorted.first()
        val bl = diffSorted.last()

        return listOf(tl, tr, br, bl)
    }
}


