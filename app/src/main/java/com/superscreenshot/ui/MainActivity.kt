package com.superscreenshot.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.appcompat.app.AppCompatActivity
import com.superscreenshot.R
import com.superscreenshot.settings.BgColorPrefs

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cameraToggle = findViewById<MaterialButtonToggleGroup>(R.id.cameraToggle)
        val orientationToggle = findViewById<MaterialButtonToggleGroup>(R.id.orientationToggle)
        val curveSeek = findViewById<SeekBar>(R.id.reflectionCurveSeek)
        val curveValue = findViewById<TextView>(R.id.reflectionCurveValue)
        val edgeSeek = findViewById<SeekBar>(R.id.edgeEnhanceSeek)
        val edgeValue = findViewById<TextView>(R.id.edgeEnhanceValue)

        fun refresh() {
            if (BgColorPrefs.getCameraLens(this) == 1) {
                cameraToggle.check(R.id.btnCameraFront)
            } else {
                cameraToggle.check(R.id.btnCameraBack)
            }

            when (BgColorPrefs.getTargetOrientation(this)) {
                2 -> orientationToggle.check(R.id.btnOrientLandscape)
                1 -> orientationToggle.check(R.id.btnOrientPortrait)
                else -> orientationToggle.check(R.id.btnOrientAuto)
            }

            val exp = BgColorPrefs.getReflectionCurveExp(this)
            curveValue.text = "强度曲线：${"%.1f".format(exp)}（越大：亮屏更压，暗屏更强）"
            curveSeek.progress = ((exp * 10f).toInt() - 10).coerceIn(0, 70)

            val edge = BgColorPrefs.getEdgeEnhanceWeight(this)
            edgeValue.text = "轮廓增强：${(edge * 100).toInt()}%"
            edgeSeek.progress = (edge * 100f).toInt().coerceIn(0, 100)
        }

        cameraToggle.addOnButtonCheckedListener(
            MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@OnButtonCheckedListener
                val lens = if (checkedId == R.id.btnCameraFront) 1 else 0
                BgColorPrefs.setCameraLens(this, lens)
            },
        )

        orientationToggle.addOnButtonCheckedListener(
            MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@OnButtonCheckedListener
                val orient =
                    when (checkedId) {
                        R.id.btnOrientLandscape -> 2
                        R.id.btnOrientPortrait -> 1
                        else -> 0 // btnOrientAuto
                    }
                BgColorPrefs.setTargetOrientation(this, orient)
            },
        )

        curveSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val exp = (10 + progress).coerceIn(10, 80) / 10f
                BgColorPrefs.setReflectionCurveExp(this@MainActivity, exp)
                curveValue.text = "强度曲线：${"%.1f".format(exp)}（越大：亮屏更压，暗屏更强）"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        edgeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val edge = progress.coerceIn(0, 100) / 100f
                BgColorPrefs.setEdgeEnhanceWeight(this@MainActivity, edge)
                edgeValue.text = "轮廓增强：${progress.coerceIn(0, 100)}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        refresh()
    }
}


