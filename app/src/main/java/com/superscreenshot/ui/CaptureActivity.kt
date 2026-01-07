package com.superscreenshot.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Bundle as OsBundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.superscreenshot.R
import com.superscreenshot.capture.CameraCapturer
import com.superscreenshot.capture.HomographyCompositor
import com.superscreenshot.capture.MediaProjectionCaptureService
import com.superscreenshot.capture.MediaProjectionScreenshotter
import com.superscreenshot.capture.ProtectedContentDetector
import com.superscreenshot.capture.ResidualBackgroundReplacer
import com.superscreenshot.capture.SimpleCompositor
import com.superscreenshot.settings.BgColorPrefs
import com.superscreenshot.storage.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CaptureActivity : AppCompatActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null) {
                finish()
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                runCapture(result.resultCode, data)
            }
        }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cameraOk = grants[Manifest.permission.CAMERA] == true
            val storageOk =
                if (Build.VERSION.SDK_INT >= 29) true else grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
            val notifOk =
                if (Build.VERSION.SDK_INT >= 33) (grants[Manifest.permission.POST_NOTIFICATIONS] == true) else true
            if (!cameraOk || !storageOk) {
                Toast.makeText(this, R.string.capture_failed_generic, Toast.LENGTH_SHORT).show()
                finish()
                return@registerForActivityResult
            }
            // Android 13+：前台服务必须能成功发出通知，否则可能直接抛异常（某些 ROM/新版本更严格）
            if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                Toast.makeText(this, "请先允许通知权限（用于前台截屏服务）", Toast.LENGTH_LONG).show()
                finish()
                return@registerForActivityResult
            }
            requestMediaProjection()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture)

        // Prevent duplicate triggers if Tile is tapped repeatedly.
        if (savedInstanceState != null) return

        if (!hasAllPermissions()) {
            requestPermissions()
            return
        }

        requestMediaProjection()
    }

    private fun hasAllPermissions(): Boolean {
        val cameraOk =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storageOk =
            if (Build.VERSION.SDK_INT >= 29) true
            else ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        return cameraOk && storageOk
    }

    private fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionsLauncher.launch(perms)
    }

    private fun requestMediaProjection() {
        val mgr = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private suspend fun runCapture(resultCode: Int, data: Intent) {
        val status = findViewById<TextView>(R.id.statusText)
        val previewView = findViewById<PreviewView>(R.id.hiddenPreviewView)
        status.setText(R.string.capture_in_progress)

        var shouldAutoFinish = true

        try {
            // OpenCV 初始化
            val isOpenCVInitialized = HomographyCompositor.initOpenCV()

            // Android 16/部分 ROM：要求在 mediaProjection 前台服务运行期间进行捕获
            val guardStart =
                Intent(this, MediaProjectionCaptureService::class.java).apply {
                    action = MediaProjectionCaptureService.ACTION_GUARD_START
                }
            val guardStop =
                Intent(this, MediaProjectionCaptureService::class.java).apply {
                    action = MediaProjectionCaptureService.ACTION_GUARD_STOP
                }
            // 等到前台服务真正进入 mediaProjection 状态再截屏（Android 16/HyperOS 否则会直接 SecurityException）
            awaitGuardReady(guardStart)

            // 关键修复：增加延迟，让系统授权弹窗彻底消失，避免截到弹窗或桌面未完全显示
            kotlinx.coroutines.delay(1000)

            val screenshot =
                try {
                    withContext(Dispatchers.Default) {
                        MediaProjectionScreenshotter.captureOnce(
                            context = this@CaptureActivity,
                            resultCode = resultCode,
                            data = data,
                        )
                    }
                } catch (t: Throwable) {
                    showErrorDialog("截屏失败", t)
                    shouldAutoFinish = false
                    return
                } finally {
                    try {
                        startService(guardStop)
                    } catch (_: Throwable) {
                    }
                }

            if (ProtectedContentDetector.isLikelyProtected(screenshot)) {
                Toast.makeText(this, R.string.capture_failed_protected, Toast.LENGTH_LONG).show()
                shouldAutoFinish = false
                return
            }

            // 检查 Activity 状态，防止在授权弹窗后 activity 已 stop 导致相机绑定失败
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                Toast.makeText(this, "页面已不可见，取消捕捉", Toast.LENGTH_SHORT).show()
                shouldAutoFinish = false
                return
            }

            val useFront = BgColorPrefs.getCameraLens(this@CaptureActivity) == 1
            val cameraBitmap =
                try {
                    CameraCapturer.captureCameraOnce(
                        context = this@CaptureActivity,
                        lifecycleOwner = this@CaptureActivity,
                        previewView = previewView,
                        useFront = useFront
                    )
                } catch (t: Throwable) {
                    showErrorDialog("相机失败", t)
                    shouldAutoFinish = false
                    return
                }

            val output =
                withContext(Dispatchers.Default) {
                    if (isOpenCVInitialized) {
                        try {
                            val base = HomographyCompositor.compose(
                                screenshot = screenshot,
                                cameraFrame = cameraBitmap,
                                alpha = 0.55f,
                            )

                            val bgColor = BgColorPrefs.getBgColor(this@CaptureActivity)
                            val replacedResidual = ResidualBackgroundReplacer.replaceBackgroundOrNull(base.residual, bgColor)

                            if (replacedResidual != null && !base.usedFallback) {
                                HomographyCompositor.composeFromWarpedAndResidual(
                                    screenWarped = base.screenWarped,
                                    residual = replacedResidual,
                                    alpha = 0.55f,
                                )
                            } else {
                                base.output
                            }
                        } catch (t: Throwable) {
                            // 若 OpenCV 逻辑执行中仍崩溃，最后回退到简单合成
                            SimpleCompositor.simpleBlend(screenshot, cameraBitmap, 0.35f)
                        }
                    } else {
                        // OpenCV 未初始化，直接走简单合成
                        SimpleCompositor.simpleBlend(screenshot, cameraBitmap, 0.35f)
                    }
                }

            val uri =
                withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveJpeg(
                        context = this@CaptureActivity,
                        bitmap = output,
                        displayName = "SuperScreenshot_${System.currentTimeMillis()}.jpg",
                    )
                }

            if (uri != null) {
                Toast.makeText(this, R.string.capture_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.capture_failed_generic, Toast.LENGTH_SHORT).show()
                shouldAutoFinish = false
            }
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.capture_failed_generic, Toast.LENGTH_SHORT).show()
            shouldAutoFinish = false
        } finally {
            if (shouldAutoFinish) {
                // 给 Toast 一点时间展示
                window.decorView.postDelayed({ finish() }, 900)
            } else {
                status.text = "失败：请查看弹窗信息（可复制），然后发我。"
            }
        }
    }

    private fun showErrorDialog(title: String, t: Throwable) {
        val text = "${t::class.java.name}\n\n${t.message ?: "无 message"}\n\n${t.stackTraceToString().take(4000)}"
        val cm = getSystemService(ClipboardManager::class.java)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton("复制") { _, _ ->
                cm.setPrimaryClip(ClipData.newPlainText("error", text))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭") { _, _ -> finish() }
            .show()
    }

    private suspend fun awaitGuardReady(guardStart: Intent) {
        suspendCancellableCoroutine<Unit> { cont ->
            val receiver =
                object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: OsBundle?) {
                        if (resultCode == MediaProjectionCaptureService.RESULT_GUARD_READY) {
                            cont.resume(Unit)
                        }
                    }
                }

            guardStart.putExtra(MediaProjectionCaptureService.EXTRA_GUARD_RECEIVER, receiver)
            try {
                ContextCompat.startForegroundService(this, guardStart)
            } catch (t: Throwable) {
                cont.resumeWithException(t)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                // 不做 stop，避免误停；真正 stop 在 finally 里
            }
        }
    }
}


