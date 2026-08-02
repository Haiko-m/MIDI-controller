package com.example.midipad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Full-surface XY pad. Reports normalised coordinates with a bottom-left
 * origin, so up and right both mean "more", matching every hardware pad.
 *
 * The value latches when you lift your finger: a filter sweep should stay
 * where you left it rather than snapping back.
 */
class XYPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun interface OnValueChangeListener {
        fun onValueChange(x: Float, y: Float)
    }

    var listener: OnValueChangeListener? = null

    var valueX = 0.5f
        private set
    var valueY = 0.5f
        private set

    private var touched = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var hasBeenTouched = false

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val colorSurface = ContextCompat.getColor(context, R.color.surface)
    private val colorEdge = ContextCompat.getColor(context, R.color.surface_edge)
    private val colorGrid = ContextCompat.getColor(context, R.color.grid)
    private val colorX = ContextCompat.getColor(context, R.color.axis_x)
    private val colorY = ContextCompat.getColor(context, R.color.axis_y)
    private val colorDim = ContextCompat.getColor(context, R.color.text_dim)

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorSurface }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = colorEdge
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = colorGrid
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorDim
        textSize = dp(11f)
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorDim
        textSize = dp(13f)
        textAlign = Paint.Align.CENTER
    }

    private val bounds = RectF()
    private val corner = dp(14f)
    private val padding = dp(2f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(padding, padding, w - padding, h - padding)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                touched = true
                hasBeenTouched = true
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                updateFrom(event, 0)
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) updateFrom(event, index)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) release()
            }

            MotionEvent.ACTION_UP -> {
                release()
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> release()
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun release() {
        touched = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun updateFrom(event: MotionEvent, pointerIndex: Int) {
        val x = ((event.getX(pointerIndex) - bounds.left) / bounds.width()).coerceIn(0f, 1f)
        // Screen y grows downward; flip it so the top of the pad is the maximum.
        val y = 1f - ((event.getY(pointerIndex) - bounds.top) / bounds.height()).coerceIn(0f, 1f)
        if (x == valueX && y == valueY) return
        valueX = x
        valueY = y
        listener?.onValueChange(x, y)
    }

    /** Moves the crosshair without emitting a change, e.g. when restoring state. */
    fun setValueSilently(x: Float, y: Float) {
        valueX = x.coerceIn(0f, 1f)
        valueY = y.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(bounds, corner, corner, surfacePaint)

        val divisions = 4
        for (i in 1 until divisions) {
            val fraction = i / divisions.toFloat()
            val gx = bounds.left + bounds.width() * fraction
            val gy = bounds.top + bounds.height() * fraction
            canvas.drawLine(gx, bounds.top, gx, bounds.bottom, gridPaint)
            canvas.drawLine(bounds.left, gy, bounds.right, gy, gridPaint)
        }

        canvas.drawRoundRect(bounds, corner, corner, edgePaint)

        if (!hasBeenTouched) {
            canvas.drawText(
                context.getString(R.string.pad_hint),
                bounds.centerX(),
                bounds.centerY(),
                hintPaint
            )
        }

        val px = bounds.left + bounds.width() * valueX
        val py = bounds.bottom - bounds.height() * valueY
        val lineAlpha = if (touched) 190 else 90

        crosshairPaint.color = colorX
        crosshairPaint.alpha = lineAlpha
        canvas.drawLine(px, bounds.top, px, bounds.bottom, crosshairPaint)

        crosshairPaint.color = colorY
        crosshairPaint.alpha = lineAlpha
        canvas.drawLine(bounds.left, py, bounds.right, py, crosshairPaint)

        val radius = if (touched) dp(26f) else dp(20f)
        knobPaint.style = Paint.Style.FILL
        knobPaint.color = Color.WHITE
        knobPaint.alpha = if (touched) 36 else 18
        canvas.drawCircle(px, py, radius, knobPaint)

        knobPaint.style = Paint.Style.STROKE
        knobPaint.strokeWidth = dp(1.5f)
        knobPaint.color = Color.WHITE
        knobPaint.alpha = if (touched) 220 else 140
        canvas.drawCircle(px, py, radius, knobPaint)

        knobPaint.style = Paint.Style.FILL
        knobPaint.alpha = 255
        canvas.drawCircle(px, py, dp(3f), knobPaint)

        val inset = dp(12f)
        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.color = colorX
        canvas.drawText(format("X", valueX), bounds.left + inset, bounds.bottom - inset, labelPaint)

        labelPaint.textAlign = Paint.Align.RIGHT
        labelPaint.color = colorY
        canvas.drawText(format("Y", valueY), bounds.right - inset, bounds.bottom - inset, labelPaint)
    }

    private fun format(axis: String, value: Float): String =
        "$axis ${(value * 16383).toInt().toString().padStart(5, ' ')}"
}
