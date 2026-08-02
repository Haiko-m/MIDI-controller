package com.example.midipad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/**
 * A momentary trigger. A normal Button fires on release, which is half a beat
 * too late for a drum pad, so this sends on touch down and releases on lift.
 */
class PadButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var label: String = ""
        set(value) {
            field = value
            contentDescription = value
            invalidate()
        }

    var onPress: (() -> Unit)? = null
    var onRelease: (() -> Unit)? = null

    private var held = false

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val colorSurface = ContextCompat.getColor(context, R.color.surface)
    private val colorEdge = ContextCompat.getColor(context, R.color.surface_edge)
    private val colorAccent = ContextCompat.getColor(context, R.color.axis_y)
    private val colorText = ContextCompat.getColor(context, R.color.text_primary)
    private val colorBg = ContextCompat.getColor(context, R.color.bg)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(15f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val bounds = RectF()
    private val corner = dp(12f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = dp(1f)
        bounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                held = true
                isPressed = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPress?.invoke()
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (held) {
                    held = false
                    isPressed = false
                    onRelease?.invoke()
                    invalidate()
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        fillPaint.color = if (held) colorAccent else colorSurface
        canvas.drawRoundRect(bounds, corner, corner, fillPaint)

        edgePaint.color = if (held) colorAccent else colorEdge
        canvas.drawRoundRect(bounds, corner, corner, edgePaint)

        textPaint.color = if (held) colorBg else colorText
        val baseline = bounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
    }
}
