package com.antigravity.mousekeyboard.controller.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class TrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onPointerDelta: ((Float, Float) -> Unit)? = null
    var onClick: ((Boolean) -> Unit)? = null // true = Left, false = Right
    var onScroll: ((Float, Float) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var pointerCount = 1

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                downTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    onPointerDelta?.invoke(dx, dy)
                    lastX = event.x
                    lastY = event.y
                } else if (pointerCount == 2) {
                    val dy = event.y - lastY
                    if (abs(dy) > 2f) {
                        onScroll?.invoke(0f, dy)
                        lastY = event.y
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - downTime
                val distance = abs(event.x - lastX) + abs(event.y - lastY)

                if (duration < 200 && distance < 15) {
                    performHaptic()
                    onClick?.invoke(true) // Left Click
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val duration = System.currentTimeMillis() - downTime
                if (duration < 200 && pointerCount == 2) {
                    performHaptic()
                    onClick?.invoke(false) // Right Click
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun performHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }
}
