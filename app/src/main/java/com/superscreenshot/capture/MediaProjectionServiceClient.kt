package com.superscreenshot.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MediaProjectionServiceClient {

    suspend fun captureOnce(context: Context, resultCode: Int, data: Intent): android.graphics.Bitmap {
        return suspendCancellableCoroutine { cont ->
            val receiver =
                object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode2: Int, resultData: Bundle?) {
                        when (resultCode2) {
                            MediaProjectionCaptureService.RESULT_OK -> {
                                val bmp =
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        resultData?.getParcelable(
                                            MediaProjectionCaptureService.EXTRA_BITMAP,
                                            android.graphics.Bitmap::class.java,
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        resultData?.getParcelable(MediaProjectionCaptureService.EXTRA_BITMAP)
                                    }
                                if (bmp != null) cont.resume(bmp)
                                else cont.resumeWithException(IllegalStateException("bitmap missing"))
                            }

                            MediaProjectionCaptureService.RESULT_ERROR -> {
                                val err = resultData?.getString(MediaProjectionCaptureService.EXTRA_ERROR) ?: "Error"
                                cont.resumeWithException(SecurityException(err))
                            }

                            else -> cont.resumeWithException(IllegalStateException("unknown result"))
                        }
                    }
                }

            val svc =
                Intent(context, MediaProjectionCaptureService::class.java).apply {
                    putExtra(MediaProjectionCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(MediaProjectionCaptureService.EXTRA_RESULT_DATA, data)
                    putExtra(MediaProjectionCaptureService.EXTRA_RESULT_RECEIVER, receiver)
                }

            try {
                ContextCompat.startForegroundService(context, svc)
            } catch (t: Throwable) {
                cont.resumeWithException(t)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try {
                    context.stopService(svc)
                } catch (_: Throwable) {
                }
            }
        }
    }
}


