package com.superscreenshot.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CameraCapturer {

    suspend fun captureBackCameraOnce(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
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

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )

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


