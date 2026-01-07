package com.superscreenshot.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.superscreenshot.ui.CaptureActivity

class SuperScreenshotTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // HyperOS/部分 ROM：锁屏或后台限制时，直接 startActivity 可能被吞掉
        val run = Runnable {
            try {
                val intent =
                    Intent(this, CaptureActivity::class.java)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        )

                if (Build.VERSION.SDK_INT >= 34) {
                    val pi =
                        PendingIntent.getActivity(
                            this,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } catch (t: Throwable) {
                // 不弹 Toast，避免被截进图里；失败时只能静默
            }
        }

        if (isLocked) {
            unlockAndRun(run)
        } else {
            run.run()
        }
    }
}


