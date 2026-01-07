package com.superscreenshot.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CameraCapturer {

    suspend fun captureCameraOnce(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFront: Boolean = false,
        targetRotation: Int = Surface.ROTATION_0,
    ): Bitmap {
        val cameraProvider = awaitCameraProvider(context)
        val executor = ContextCompat.getMainExecutor(context)

        val preview =
            Preview.Builder()
                .setTargetRotation(targetRotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageCapture =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setTargetRotation(targetRotation)
                .build()

        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider.unbindAll()
        val camera: Camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview,
            imageCapture,
        )

        // 让画面更“像反射”：尽量压暗曝光（如果设备支持）
        try {
            val range = camera.cameraInfo.exposureState.exposureCompensationRange
            // Kotlin IntRange 没有 isEmpty()；用 lower<=upper 判断
            if (range.lower <= range.upper && range.lower < 0) {
                // 尽量压暗到设备允许的最小值，避免“自拍太亮”的感觉
                val target = range.lower
                withTimeout(900) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        camera.cameraControl.setExposureCompensationIndex(target)
                            .addListener(
                                { cont.resume(Unit) },
                                executor,
                            )
                    }
                }
            }
        } catch (_: Throwable) {
            // 忽略：部分机型/权限下不支持曝光补偿控制
        }

        val file = File.createTempFile("ss_cam_", ".jpg", context.cacheDir)

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val output =
                    ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    output,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exception: ImageCaptureException) {
                            cont.resumeWithException(exception)
                        }

                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            cont.resume(Unit)
                        }
                    },
                )
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("decode camera bitmap failed")
            return fixExifRotation(bmp, file.absolutePath)
        } finally {
            cameraProvider.unbindAll()
            try {
                file.delete()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider {
        val future = ProcessCameraProvider.getInstance(context)
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    private fun fixExifRotation(bitmap: Bitmap, filePath: String): Bitmap {
        val orientation =
            try {
                ExifInterface(filePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }

        val degrees =
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        if (degrees == 0) return bitmap

        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}


