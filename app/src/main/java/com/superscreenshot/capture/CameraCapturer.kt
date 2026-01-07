package com.superscreenshot.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
    ): Bitmap {
        val cameraProvider = awaitCameraProvider(context)
        val executor = ContextCompat.getMainExecutor(context)

        val preview =
            Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageCapture =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
                // 经验值：-2~-4 往往能显著降低“太亮的自拍感”
                val target = (-3).coerceIn(range.lower, range.upper)
                withTimeout(600) {
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
            return bmp
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
}


