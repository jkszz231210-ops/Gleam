package com.superscreenshot.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

object ResidualBackgroundReplacer {

    /**
     * 对 residual 层做“背景纯色替换”。返回 null 表示分割置信度不够（回退不替换）。
     */
    suspend fun replaceBackgroundOrNull(residual: Bitmap, solidColor: Int): Bitmap? {
        val options =
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()

        val segmenter = Segmentation.getClient(options)
        val image = InputImage.fromBitmap(residual, 0)

        val mask =
            try {
                segmenter.process(image).await()
            } catch (_: Throwable) {
                return null
            } finally {
                try {
                    segmenter.close()
                } catch (_: Throwable) {
                }
            }

        // 若几乎没有前景，说明暗反射下分割失败，直接回退
        val fgMass = estimateForegroundMass(mask)
        if (fgMass < 0.02f) return null

        val out = Bitmap.createBitmap(residual.width, residual.height, Bitmap.Config.ARGB_8888)

        val w = residual.width
        val h = residual.height
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        residual.getPixels(inPixels, 0, w, 0, 0, w, h)

        // 背景色在 residual 空间里不宜太“实”，用一个固定强度系数
        val beta = 0.55f
        val bgR = ((Color.red(solidColor) * beta).roundToInt()).coerceIn(0, 255)
        val bgG = ((Color.green(solidColor) * beta).roundToInt()).coerceIn(0, 255)
        val bgB = ((Color.blue(solidColor) * beta).roundToInt()).coerceIn(0, 255)

        val mw = mask.width
        val mh = mask.height
        val buf = mask.buffer
        buf.rewind()

        // 把 mask buffer 读成 float 数组，便于随机访问
        val maskArr = FloatArray(mw * mh)
        var i = 0
        while (i < maskArr.size && buf.remaining() >= 4) {
            maskArr[i] = buf.float
            i++
        }

        for (y in 0 until h) {
            val my = (y * mh) / h
            for (x in 0 until w) {
                val mx = (x * mw) / w
                val m = maskArr[my * mw + mx].coerceIn(0f, 1f) // foreground prob

                val c = inPixels[y * w + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // out = residual*m + bg*(1-m)
                val or = (r * m + bgR * (1f - m)).roundToInt().coerceIn(0, 255)
                val og = (g * m + bgG * (1f - m)).roundToInt().coerceIn(0, 255)
                val ob = (b * m + bgB * (1f - m)).roundToInt().coerceIn(0, 255)
                outPixels[y * w + x] = (0xFF shl 24) or (or shl 16) or (og shl 8) or ob
            }
        }

        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun estimateForegroundMass(mask: SegmentationMask): Float {
        val buf = mask.buffer
        buf.rewind()
        val total = mask.width * mask.height
        var sum = 0f
        var i = 0
        while (i < total && buf.remaining() >= 4) {
            sum += buf.float.coerceIn(0f, 1f)
            i++
        }
        return if (i == 0) 0f else sum / i.toFloat()
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }


