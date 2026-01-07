package com.superscreenshot.settings

import android.content.Context
import android.graphics.Color

object BgColorPrefs {
    private const val PREFS = "super_screenshot_prefs"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_CAMERA_LENS = "camera_lens" // 0: back, 1: front
    private const val KEY_TARGET_ORIENTATION = "target_orientation" // 0: auto, 1: portrait, 2: landscape

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

    fun getCameraLens(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CAMERA_LENS, 0) // 默认后置
    }

    fun setCameraLens(context: Context, lens: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CAMERA_LENS, lens)
            .apply()
    }

    fun getTargetOrientation(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_TARGET_ORIENTATION, 0)
    }

    fun setTargetOrientation(context: Context, orientation: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TARGET_ORIENTATION, orientation)
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


