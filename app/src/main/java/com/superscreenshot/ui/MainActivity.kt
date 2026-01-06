package com.superscreenshot.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
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

        fun refresh() {
            val c = BgColorPrefs.getBgColor(this)
            current.text = getString(R.string.current_bg_color, String.format("#%08X", c))
        }

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


