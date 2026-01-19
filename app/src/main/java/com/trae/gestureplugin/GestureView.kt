package com.trae.gestureplugin

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

import android.graphics.drawable.GradientDrawable

class GestureView(context: Context, private val isLeft: Boolean, private val onGesture: (GestureType) -> Unit) : FrameLayout(context) {
    // ... (detector implementation remains the same) ...
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

    private val arrowView: ImageView

    init {
        isClickable = true
        isFocusable = false
        // 设置默认视觉：红色半透明背景 + 白色描边
        setVisualsEnabled(true)
        
        arrowView = ImageView(context)
        arrowView.setImageResource(R.drawable.ic_arrow_right)
        arrowView.visibility = View.GONE
        
        val size = (48 * resources.displayMetrics.density).toInt()
        val lp = LayoutParams(size, size)
        lp.gravity = Gravity.CENTER
        addView(arrowView, lp)
    }

    fun setVisualsEnabled(enabled: Boolean) {
        if (enabled) {
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#CCFF0000")) // Red 80% Alpha
            bg.setStroke((0.5f * resources.displayMetrics.density).toInt().coerceAtLeast(1), Color.WHITE)
            background = bg
        } else {
            background = null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ... (touch implementation remains the same) ...
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
                
                if (abs(dx) < 10 && abs(dy) < 10 && dt < 200) {
                    performClick()
                    return true
                }

                val speedX = abs(dx) * 1000f / dt
                val speedY = abs(dy) * 1000f / dt
                
                if (speedX < 200f && speedY < 200f) {
                    // 太慢
                } else {
                    classifyAndEmit(dx, dy)
                }
            }
        }
        return detector.onTouchEvent(event)
    }
    
    // ... (rest of the class) ...

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
        
        if (Prefs.getShowAnimation(context)) {
            showAnimation(dx, dy)
        }
        
        Log.d("GestureView", "Emitting gesture: $gesture")
        onGesture(gesture)
    }

    private fun showAnimation(dx: Float, dy: Float) {
        arrowView.visibility = View.VISIBLE
        arrowView.alpha = 0f
        arrowView.scaleX = 0.5f
        arrowView.scaleY = 0.5f
        
        // Reset translation
        arrowView.translationX = 0f
        arrowView.translationY = 0f

        val color: Int
        val rotation: Float
        val moveX: Float
        val moveY: Float
        val distance = 100f // 移动距离 px

        if (abs(dx) > abs(dy)) {
            // Horizontal
            if (dx > 0) { // Right
                color = Color.GREEN
                rotation = 0f
                moveX = distance
                moveY = 0f
            } else { // Left
                color = Color.YELLOW
                rotation = 180f
                moveX = -distance
                moveY = 0f
            }
        } else {
            // Vertical
            if (dy < 0) { // Up
                color = Color.BLUE
                rotation = -90f
                moveX = 0f
                moveY = -distance
            } else { // Down
                color = Color.MAGENTA
                rotation = 90f
                moveX = 0f
                moveY = distance
            }
        }

        arrowView.setColorFilter(color)
        arrowView.rotation = rotation

        val animator = ObjectAnimator.ofPropertyValuesHolder(
            arrowView,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.5f, 1.2f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 1.2f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, moveX),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, moveY)
        )
        animator.duration = 400
        animator.interpolator = DecelerateInterpolator()
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                arrowView.visibility = View.GONE
            }
        })
        animator.start()
    }
}
