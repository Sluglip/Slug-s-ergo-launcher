package com.slug.ergolauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** A saturation/brightness square with a hue bar underneath. Drag either one to choose a color. */
class ColorPickerView(
    context: Context,
    initial: Int,
    private val onChange: (Int) -> Unit
) : View(context) {

    private val hsv = FloatArray(3)
    private val u = resources.displayMetrics.density
    private val svH = 170 * u
    private val gap = 24 * u
    private val hueH = 30 * u
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var region = 0

    init {
        Color.colorToHSV(initial, hsv)
    }

    /** Sets the color from outside (for example when a hex code is typed). */
    fun setColor(c: Int) {
        val keepHue = hsv[0]
        Color.colorToHSV(c, hsv)
        if (hsv[1] == 0f || hsv[2] == 0f) hsv[0] = keepHue
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (svH + gap + hueH).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val hueColor = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))

        val valueShader = LinearGradient(0f, 0f, 0f, svH, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
        val satShader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        paint.shader = ComposeShader(valueShader, satShader, PorterDuff.Mode.MULTIPLY)
        canvas.drawRoundRect(0f, 0f, w, svH, 14 * u, 14 * u, paint)

        val tx = hsv[1] * w
        val ty = (1f - hsv[2]) * svH
        drawThumb(canvas, tx, ty, 11 * u, Color.HSVToColor(hsv))

        val top = svH + gap
        paint.shader = LinearGradient(
            0f, top, w, top,
            intArrayOf(
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(0f, top, w, top + hueH, hueH / 2, hueH / 2, paint)
        paint.shader = null
        drawThumb(canvas, hsv[0] / 360f * w, top + hueH / 2, 13 * u, hueColor)
    }

    private fun drawThumb(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * u
        paint.color = Color.WHITE
        c.drawCircle(x, y, r, paint)
        paint.strokeWidth = u
        paint.color = Color.argb(140, 0, 0, 0)
        c.drawCircle(x, y, r + 1.5f * u, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat()
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            region = if (e.y <= svH + gap / 2) 0 else 1
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            if (region == 0) {
                hsv[1] = (e.x / w).coerceIn(0f, 1f)
                hsv[2] = (1f - e.y / svH).coerceIn(0.2f, 1f)
            } else {
                hsv[0] = (e.x / w).coerceIn(0f, 0.999f) * 360f
            }
            invalidate()
            onChange(Color.HSVToColor(hsv))
            return true
        }
        return true
    }
}
