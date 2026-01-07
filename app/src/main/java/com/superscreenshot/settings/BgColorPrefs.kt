package com.superscreenshot.settings

import android.content.Context
import android.graphics.Color

object BgColorPrefs {
    private const val PREFS = "super_screenshot_prefs"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_CAMERA_LENS = "camera_lens" // 0: back, 1: front
    private const val KEY_TARGET_ORIENTATION = "target_orientation" // 0: auto, 1: portrait, 2: landscape

    // 0.2: reflection tuning
    // curveExpTenths: 10..80 => 1.0..8.0 (higher => brighter screen suppresses reflection more)
    private const val KEY_REFLECTION_CURVE_EXP_TENTHS = "reflection_curve_exp_tenths"
    // edgeEnhancePercent: 0..100 => 0.0..1.0
    private const val KEY_EDGE_ENHANCE_PERCENT = "edge_enhance_percent"

    private const val DEFAULT_BG_COLOR = 0xFF0B1B3A.toInt()
    private const val DEFAULT_REFLECTION_CURVE_EXP_TENTHS = 40 // 4.0
    private const val DEFAULT_EDGE_ENHANCE_PERCENT = 28 // 0.28

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
            .getInt(KEY_CAMERA_LENS, 1) // 默认改为前置 (1)，方便记录自己
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

    fun getReflectionCurveExp(context: Context): Float {
        val tenths = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_REFLECTION_CURVE_EXP_TENTHS, DEFAULT_REFLECTION_CURVE_EXP_TENTHS)
            .coerceIn(10, 80)
        return tenths / 10f
    }

    fun setReflectionCurveExp(context: Context, exp: Float) {
        val tenths = (exp * 10f).toInt().coerceIn(10, 80)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REFLECTION_CURVE_EXP_TENTHS, tenths)
            .apply()
    }

    fun getEdgeEnhanceWeight(context: Context): Float {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_EDGE_ENHANCE_PERCENT, DEFAULT_EDGE_ENHANCE_PERCENT)
            .coerceIn(0, 100)
        return p / 100f
    }

    fun setEdgeEnhanceWeight(context: Context, weight: Float) {
        val p = (weight * 100f).toInt().coerceIn(0, 100)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_EDGE_ENHANCE_PERCENT, p)
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


