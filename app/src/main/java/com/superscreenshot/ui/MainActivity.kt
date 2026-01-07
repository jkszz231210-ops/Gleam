package com.superscreenshot.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.superscreenshot.R
import com.superscreenshot.settings.BgColorPrefs

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chips = findViewById<LinearLayout>(R.id.colorChips)
        val current = findViewById<TextView>(R.id.currentColorText)
        val cameraGroup = findViewById<RadioGroup>(R.id.cameraGroup)
        val orientationGroup = findViewById<RadioGroup>(R.id.orientationGroup)
        val curveSeek = findViewById<SeekBar>(R.id.reflectionCurveSeek)
        val curveValue = findViewById<TextView>(R.id.reflectionCurveValue)
        val edgeSeek = findViewById<SeekBar>(R.id.edgeEnhanceSeek)
        val edgeValue = findViewById<TextView>(R.id.edgeEnhanceValue)

        fun refresh() {
            val c = BgColorPrefs.getBgColor(this)
            current.text = getString(R.string.current_bg_color, String.format("#%08X", c))

            if (BgColorPrefs.getCameraLens(this) == 1) {
                findViewById<RadioButton>(R.id.rbFrontCamera).isChecked = true
            } else {
                findViewById<RadioButton>(R.id.rbBackCamera).isChecked = true
            }

            when (BgColorPrefs.getTargetOrientation(this)) {
                2 -> findViewById<RadioButton>(R.id.rbLandscape).isChecked = true
                1 -> findViewById<RadioButton>(R.id.rbPortrait).isChecked = true
                else -> findViewById<RadioButton>(R.id.rbAutoOrientation).isChecked = true
            }

            val exp = BgColorPrefs.getReflectionCurveExp(this)
            curveValue.text = "强度曲线：${"%.1f".format(exp)}（越大：亮屏更压，暗屏更强）"
            curveSeek.progress = ((exp * 10f).toInt() - 10).coerceIn(0, 70)

            val edge = BgColorPrefs.getEdgeEnhanceWeight(this)
            edgeValue.text = "轮廓增强：${(edge * 100).toInt()}%"
            edgeSeek.progress = (edge * 100f).toInt().coerceIn(0, 100)
        }

        cameraGroup.setOnCheckedChangeListener { _, checkedId ->
            val lens = if (checkedId == R.id.rbFrontCamera) 1 else 0
            BgColorPrefs.setCameraLens(this, lens)
        }

        orientationGroup.setOnCheckedChangeListener { _, checkedId ->
            val orient =
                when (checkedId) {
                    R.id.rbLandscape -> 2
                    R.id.rbPortrait -> 1
                    else -> 0 // rbAutoOrientation
                }
            BgColorPrefs.setTargetOrientation(this, orient)
        }

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

        chips.removeAllViews()
        val size = resources.displayMetrics.density * 40f
        val margin = (resources.displayMetrics.density * 10f).toInt()

        for (color in BgColorPrefs.presets()) {
            val v = TextView(this)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((resources.displayMetrics.density * 1f).toInt(), 0x55FFFFFF)
            }
            v.background = bg
            v.setOnClickListener {
                BgColorPrefs.setBgColor(this, color)
                refresh()
            }
            val lp = LinearLayout.LayoutParams(size.toInt(), size.toInt())
            lp.setMargins(margin, margin, margin, margin)
            chips.addView(v, lp)
        }

        refresh()
    }
}


