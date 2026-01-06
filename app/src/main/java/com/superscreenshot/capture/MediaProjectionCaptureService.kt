package com.superscreenshot.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ResultReceiver
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.superscreenshot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediaProjectionCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_GUARD_START -> {
                startForegroundCompat()
                val receiver = intent.getParcelableExtraCompat<ResultReceiver>(EXTRA_GUARD_RECEIVER)
                try {
                    receiver?.send(RESULT_GUARD_READY, null)
                } catch (_: Throwable) {
                }
                // 防止 ROM 异常情况下常驻：最多护航 8 秒
                scope.launch {
                    try {
                        kotlinx.coroutines.delay(8000)
                    } catch (_: Throwable) {
                    } finally {
                        stopForegroundCompat()
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }

            ACTION_GUARD_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.getParcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)
        val receiver = intent.getParcelableExtraCompat<ResultReceiver>(EXTRA_RESULT_RECEIVER)

        if (resultCode == 0 || data == null || receiver == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()

        scope.launch {
            try {
                val bmp = MediaProjectionScreenshotter.captureOnce(
                    context = this@MediaProjectionCaptureService,
                    resultCode = resultCode,
                    data = data,
                )
                receiver.send(RESULT_OK, Intent().apply { putExtra(EXTRA_BITMAP, bmp) }.extras)
            } catch (t: Throwable) {
                receiver.send(
                    RESULT_ERROR,
                    Intent().apply {
                        putExtra(EXTRA_ERROR, (t.toString()).take(1000))
                    }.extras,
                )
            } finally {
                stopForegroundCompat()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch =
                NotificationChannel(
                    CHANNEL_ID,
                    "SuperScreenshot",
                    NotificationManager.IMPORTANCE_LOW,
                )
            nm.createNotificationChannel(ch)
        }
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(getString(R.string.tile_label))
                .setContentText(getString(R.string.capture_in_progress))
                .setOngoing(true)
                .setSilent(true)
                .build()

        // Android 10+：建议/在更高版本上可能强制要求传入 foreground service type
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {
        }
    }

    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }
    }

    companion object {
        const val CHANNEL_ID = "super_screenshot_mp"
        const val NOTIF_ID = 1001

        const val ACTION_GUARD_START = "com.superscreenshot.action.GUARD_START"
        const val ACTION_GUARD_STOP = "com.superscreenshot.action.GUARD_STOP"
        const val EXTRA_GUARD_RECEIVER = "guardReceiver"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_RESULT_RECEIVER = "resultReceiver"

        const val EXTRA_BITMAP = "bitmap"
        const val EXTRA_ERROR = "error"

        const val RESULT_OK = 1
        const val RESULT_ERROR = 2
        const val RESULT_GUARD_READY = 3
    }
}


