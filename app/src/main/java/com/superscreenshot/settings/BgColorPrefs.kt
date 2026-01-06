package com.superscreenshot.settings

import android.content.Context
import android.graphics.Color

object BgColorPrefs {
    private const val PREFS = "super_screenshot_prefs"
    private const val KEY_BG_COLOR = "bg_color"

    // 默认给一个“电影感”的深蓝，仍属于纯色
    private const val DEFAULT_BG_COLOR = 0xFF0B1B3A.toInt()

    fun getBgColor(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_BG_COLOR, DEFAULT_BG_COLOR)
    }

    fun setBgColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BG_COLOR, color or (0xFF shl 24))
            .apply()
    }

    fun presets(): List<Int> = listOf(
        Color.BLACK,
        Color.WHITE,
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        0xFFFFC107.toInt(), // amber
        0xFFFF00FF.toInt(), // magenta
        DEFAULT_BG_COLOR,
    )
}


