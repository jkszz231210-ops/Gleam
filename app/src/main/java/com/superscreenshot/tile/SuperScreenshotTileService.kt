package com.superscreenshot.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
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
                Toast.makeText(this, "超级截屏：启动中…", Toast.LENGTH_SHORT).show()
                val intent =
                    Intent(this, CaptureActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
                Toast.makeText(this, "超级截屏：启动失败（${t.javaClass.simpleName}）", Toast.LENGTH_SHORT).show()
            }
        }

        if (isLocked) {
            unlockAndRun(run)
        } else {
            run.run()
        }
    }
}


