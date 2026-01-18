package com.trae.gestureplugin

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

import android.util.Log
import android.graphics.Color

class GestureView(context: Context, private val isLeft: Boolean, private val onGesture: (GestureType) -> Unit) : View(context) {
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            Log.d("GestureView", "onFling detected")
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            classifyAndEmit(dx, dy)
            return true
        }
    })

    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L

    init {
        isClickable = true
        isFocusable = false
        // 临时背景色，方便调试看到区域 (淡红色)
        setBackgroundColor(Color.parseColor("#33FF0000"))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("GestureView", "onTouchEvent: ${event.actionMasked}, x=${event.x}, y=${event.y}")
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                val dy = event.y - startY
                val dt = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val speedX = abs(dx) * 1000f / dt
                val speedY = abs(dy) * 1000f / dt
                if (speedX < 50f && speedY < 50f) {
                    // 太慢的拖动不作为手势
                } else {
                    classifyAndEmit(dx, dy)
                }
            }
        }
        return detector.onTouchEvent(event)
    }

    private fun classifyAndEmit(dx: Float, dy: Float) {
        Log.d("GestureView", "classifyAndEmit: dx=$dx, dy=$dy")
        val gesture = if (abs(dx) > abs(dy)) {
            if (isLeft) GestureType.LEFT_HORIZONTAL else GestureType.RIGHT_HORIZONTAL
        } else {
            if (dy < 0) {
                if (isLeft) GestureType.LEFT_UP else GestureType.RIGHT_UP
            } else {
                if (isLeft) GestureType.LEFT_DOWN else GestureType.RIGHT_DOWN
            }
        }
        Log.d("GestureView", "Emitting gesture: $gesture")
        onGesture(gesture)
    }
}
