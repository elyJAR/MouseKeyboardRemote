package com.antigravity.mousekeyboard.receiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

class CursorOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var cursorView: View? = null
    private val layoutParams = WindowManager.LayoutParams()

    private var cursorX = 960f
    private var cursorY = 540f
    private var screenWidth = 1920
    private var screenHeight = 1080

    private var isClicking = false

    init {
        updateScreenDimensions()
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f

        layoutParams.apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = 64
            height = 64
            gravity = Gravity.TOP or Gravity.START
            x = cursorX.toInt()
            y = cursorY.toInt()
        }
    }

    fun updateScreenDimensions() {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    fun showCursor() {
        if (!Settings.canDrawOverlays(context)) return
        if (cursorView == null) {
            cursorView = object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                private val pointerPath = Path()

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    pointerPath.reset()
                    // Draw mouse arrow pointer
                    pointerPath.moveTo(0f, 0f)
                    pointerPath.lineTo(0f, 40f)
                    pointerPath.lineTo(12f, 28f)
                    pointerPath.lineTo(24f, 44f)
                    pointerPath.lineTo(32f, 40f)
                    pointerPath.lineTo(20f, 24f)
                    pointerPath.lineTo(36f, 24f)
                    pointerPath.close()

                    if (isClicking) {
                        paint.color = Color.parseColor("#00E676") // Accent green on click
                    } else {
                        paint.color = Color.WHITE
                    }

                    canvas.drawPath(pointerPath, paint)
                    canvas.drawPath(pointerPath, strokePaint)
                }
            }
            try {
                windowManager.addView(cursorView, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideCursor() {
        cursorView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cursorView = null
        }
    }

    fun moveCursor(dx: Float, dy: Float) {
        // Apply smooth sensitivity multiplier
        val sensitivity = 1.2f
        cursorX = min(max(0f, cursorX + dx * sensitivity), screenWidth.toFloat())
        cursorY = min(max(0f, cursorY + dy * sensitivity), screenHeight.toFloat())

        layoutParams.x = cursorX.toInt()
        layoutParams.y = cursorY.toInt()

        cursorView?.let { view ->
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setClickState(clicking: Boolean) {
        isClicking = clicking
        cursorView?.postInvalidate()
    }

    fun getCursorPosition(): Pair<Float, Float> {
        return Pair(cursorX, cursorY)
    }
}
