package com.example.btgamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2D34")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F5D75")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var stickRadius = 0f
    private var stickX = 0f
    private var stickY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    var onMoveListener: ((x: Int, y: Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) * 0.42f
        stickRadius = baseRadius * 0.42f
        stickX = centerX
        stickY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(centerX, centerY, baseRadius, borderPaint)
        canvas.drawCircle(stickX, stickY, stickRadius, stickPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                    val index = event.actionIndex
                    activePointerId = event.getPointerId(index)
                    updateStick(event.getX(index), event.getY(index))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index != -1) {
                    updateStick(event.getX(index), event.getY(index))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val index = event.actionIndex
                if (event.getPointerId(index) == activePointerId || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    stickX = centerX
                    stickY = centerY
                    invalidate()
                    onMoveListener?.invoke(128, 128)
                }
            }
        }
        return true
    }

    private fun updateStick(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < baseRadius) {
            stickX = touchX
            stickY = touchY
        } else {
            val angle = atan2(dy, dx)
            stickX = centerX + cos(angle) * baseRadius
            stickY = centerY + sin(angle) * baseRadius
        }
        invalidate()

        val normX = (((stickX - centerX) / baseRadius + 1f) * 127.5f).toInt().coerceIn(0, 255)
        val normY = (((stickY - centerY) / baseRadius + 1f) * 127.5f).toInt().coerceIn(0, 255)
        onMoveListener?.invoke(normX, normY)
    }
}
