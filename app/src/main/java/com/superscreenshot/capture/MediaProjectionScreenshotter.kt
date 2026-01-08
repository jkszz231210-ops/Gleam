package com.superscreenshot.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.superscreenshot.settings.BgColorPrefs
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MediaProjectionScreenshotter {
    private const val TIMEOUT_MS = 1800L

    suspend fun captureOnce(context: Context, resultCode: Int, data: Intent): Bitmap {
        val mgr = context.getSystemService(MediaProjectionManager::class.java)
        val projection = mgr.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection is null")

        val wm = context.getSystemService(WindowManager::class.java)
        val displayMetrics = DisplayMetrics()
        var (width, height) =
            if (Build.VERSION.SDK_INT >= 30) {
                val b = wm.currentWindowMetrics.bounds
                displayMetrics.densityDpi = context.resources.displayMetrics.densityDpi
                Pair(b.width(), b.height())
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(displayMetrics)
                Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
            }

        // 根据设置强制调整方向
        val targetOrient = BgColorPrefs.getTargetOrientation(context)
        if (targetOrient == 1) { // 强制竖屏
            if (width > height) {
                val tmp = width; width = height; height = tmp
            }
        } else if (targetOrient == 2) { // 强制横屏
            if (height > width) {
                val tmp = width; width = height; height = tmp
            }
        }

        require(width > 0 && height > 0) { "Invalid capture size: ${width}x$height" }

        val handlerThread = HandlerThread("mp-shot").apply { start() }
        val handler = Handler(handlerThread.looper)

        // Android 16：必须在开始 capture 前注册 callback
        val callback =
            object : MediaProjection.Callback() {
                override fun onStop() {
                    // 单次截屏场景里无需在这里做复杂事；资源释放在 finally。
                }
            }
        try {
            projection.registerCallback(callback, handler)
        } catch (_: Throwable) {
            // 某些 ROM 可能抛异常；让后续逻辑决定是否继续/报错
        }

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null
        val done = AtomicBoolean(false)

        try {
            virtualDisplay =
                try {
                    projection.createVirtualDisplay(
                        context.getString(com.superscreenshot.R.string.virtual_display_name),
                        width,
                        height,
                        displayMetrics.densityDpi,
                        0,
                        reader.surface,
                        null,
                        handler,
                    )
                } catch (t: Throwable) {
                    throw IllegalStateException("虚拟显示创建失败，可能是截屏权限/窗口异常", t)
                }
            requireNotNull(virtualDisplay) { "VirtualDisplay is null" }

            return try {
                withTimeout(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    reader.setOnImageAvailableListener(
                        { ir ->
                            // ImageReader 可能连续触发多次；只允许第一次成功/失败，后续全部忽略
                            if (!done.compareAndSet(false, true)) {
                                var extra: Image? = null
                                try {
                                    extra = ir.acquireLatestImage()
                                } catch (_: Throwable) {
                                } finally {
                                    try {
                                        extra?.close()
                                    } catch (_: Throwable) {
                                    }
                                }
                                return@setOnImageAvailableListener
                            }

                            // 先移除 listener，避免并发/重复回调导致 continuation 二次 resume
                            ir.setOnImageAvailableListener(null, null)

                            var image: Image? = null
                            try {
                                image = ir.acquireLatestImage()
                                    ?: throw IllegalStateException("Image is null")
                                val bmp = imageToBitmap(image)
                                cont.resume(bmp)
                            } catch (t: Throwable) {
                                cont.resumeWithException(t)
                            } finally {
                                image?.close()
                            }
                        },
                        handler,
                    )

                        cont.invokeOnCancellation {
                            // on timeout/cancel: stop listening
                            try {
                                reader.setOnImageAvailableListener(null, null)
                            } catch (_: Throwable) {
                            }
                            done.set(true)
                        }
                }
            }
            } catch (t: TimeoutCancellationException) {
                throw IllegalStateException("截屏超时：未在 ${TIMEOUT_MS}ms 内收到图像帧，请重试", t)
            }
        } finally {
            try {
                virtualDisplay?.release()
            } catch (_: Throwable) {
            }
            try {
                reader.close()
            } catch (_: Throwable) {
            }
            try {
                try {
                    projection.unregisterCallback(callback)
                } catch (_: Throwable) {
                }
                projection.stop()
            } catch (_: Throwable) {
            }
            handlerThread.quitSafely()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap =
            Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888,
            )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
}


