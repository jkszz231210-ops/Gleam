package com.superscreenshot.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Simple HSV color wheel:
 * - Hue: angle (0..360)
 * - Saturation: radius (0..1)
 * - Value fixed at 1.0 (user can reset to black via button in UI)
 *
 * Tap/drag to pick color. Designed for "solid background color" selection.
 */
class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface OnColorChangedListener {
        fun onColorChanged(color: Int)
    }

    private var listener: OnColorChangedListener? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private val rect = RectF()

    private var shader: Shader? = null
    private val shaderMatrix = Matrix()

    private var currentHue = 0f
    private var currentSat = 0f

    init {
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = context.resources.displayMetrics.density * 1.5f
        ringPaint.color = 0x55FFFFFF

        selectorPaint.style = Paint.Style.STROKE
        selectorPaint.strokeWidth = context.resources.displayMetrics.density * 3.0f
        selectorPaint.color = Color.WHITE
        selectorPaint.setShadowLayer(context.resources.displayMetrics.density * 2f, 0f, 0f, 0x66000000)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setOnColorChangedListener(l: OnColorChangedListener?) {
        listener = l
    }

    fun setColor(color: Int) {
        // Convert to HSV for selector indicator.
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        currentHue = hsv[0].coerceIn(0f, 360f)
        currentSat = hsv[1].coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = max(paddingLeft + paddingRight, paddingTop + paddingBottom).toFloat()
        val size = min(w.toFloat(), h.toFloat()) - pad
        radius = (size / 2f).coerceAtLeast(1f)
        centerX = w / 2f
        centerY = h / 2f
        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // Hue sweep + saturation radial (white center -> transparent edge)
        val hue = SweepGradient(centerX, centerY, intArrayOf(
            Color.RED,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA,
            Color.RED,
        ), null)
        val sat = RadialGradient(centerX, centerY, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
        shader = ComposeShader(hue, sat, android.graphics.PorterDuff.Mode.MULTIPLY)
        paint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Make sure shader is centered (in case of translation)
        shader?.setLocalMatrix(shaderMatrix)
        canvas.drawOval(rect, paint)
        canvas.drawOval(rect, ringPaint)

        // Draw selector
        val r = (currentSat * radius).coerceIn(0f, radius)
        val angle = Math.toRadians(currentHue.toDouble())
        val sx = centerX + (kotlin.math.cos(angle).toFloat() * r)
        val sy = centerY + (kotlin.math.sin(angle).toFloat() * r)
        val selR = context.resources.displayMetrics.density * 10f
        canvas.drawCircle(sx, sy, selR, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = hypot(dx, dy)
                if (dist > radius * 1.05f) return false

                val sat = (dist / radius).coerceIn(0f, 1f)
                var hue = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                if (hue < 0) hue += 360f

                currentHue = hue
                currentSat = sat

                val hsv = floatArrayOf(currentHue, currentSat, 1f)
                val color = Color.HSVToColor(0xFF, hsv)
                listener?.onColorChanged(color)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> return true
        }
        return super.onTouchEvent(event)
    }
}


