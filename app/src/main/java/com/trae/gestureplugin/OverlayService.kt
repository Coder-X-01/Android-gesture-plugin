package com.trae.gestureplugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.util.Log
import android.widget.ImageView

import android.content.BroadcastReceiver
import android.content.IntentFilter

import android.content.pm.PackageManager

import android.view.HapticFeedbackConstants
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.view.View

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var leftView: GestureView? = null
    private var rightView: GestureView? = null
    private var controlView: ImageView? = null
    private var homePackage: String? = null
    private var isGestureHiddenByUser = false
    
    private val windowStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.trae.gestureplugin.ACTION_CONFIG_CHANGED") {
                updateOverlayLayout()
                return
            }

            val pkg = intent?.getStringExtra("package_name")
            Log.d("OverlayService", "Window state changed: $pkg")
            
            // Check logic
            if (pkg == packageName) {
                // In App: Disable everything completely
                Log.d("OverlayService", "Entering self, disabling gestures and control")
                setOverlayVisibility(false)
                controlView?.visibility = View.GONE
            } else {
                // Other Apps: Enable control button
                controlView?.visibility = View.VISIBLE
                
                // Check if user manually hid it
                if (isGestureHiddenByUser) {
                    setOverlayVisibility(false)
                } else {
                    setOverlayVisibility(true)
                    // Desktop detection
                    if (pkg == homePackage) {
                        Log.d("OverlayService", "Entering Desktop, hiding visuals")
                        setGestureVisuals(false)
                    } else if (pkg == "com.android.systemui") {
                        Log.d("OverlayService", "Entering SystemUI, hiding visuals")
                        setGestureVisuals(false)
                    } else {
                        Log.d("OverlayService", "Entering App, showing visuals")
                        setGestureVisuals(true)
                    }
                }
            }
        }
    }
    
    private fun setGestureVisuals(visible: Boolean) {
        leftView?.setVisualsEnabled(visible)
        rightView?.setVisualsEnabled(visible)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("OverlayService", "onCreate")
        
        isGestureHiddenByUser = !Prefs.getIsGestureVisible(this)
        
        // Find Launcher Package
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        homePackage = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        Log.d("OverlayService", "Home Package: $homePackage")

        try {
            createChannel()
            startForeground(1, buildNotification())
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            addOverlay()
            addControlView()
            
            // Register receiver
            val filter = IntentFilter("com.trae.gestureplugin.ACTION_WINDOW_CHANGED")
            filter.addAction("com.trae.gestureplugin.ACTION_CONFIG_CHANGED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(windowStateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(windowStateReceiver, filter)
            }
            
            // Check initial state
            val currentPkg = GestureAccessibilityService.currentPackage
            if (currentPkg == packageName) {
                setOverlayVisibility(false)
                controlView?.visibility = View.GONE
            } else {
                if (isGestureHiddenByUser) {
                    setOverlayVisibility(false)
                } else if (currentPkg == homePackage || currentPkg == "com.android.systemui") {
                    setGestureVisuals(false)
                }
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error in onCreate", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(windowStateReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        removeOverlay()
        controlView?.let { wm.removeView(it) }
        super.onDestroy()
    }
    
    private fun setOverlayVisibility(visible: Boolean) {
        // 这个方法用于完全禁用（如在应用内时），所以保留 GONE
        val visibility = if (visible) View.VISIBLE else View.GONE
        leftView?.visibility = visibility
        rightView?.visibility = visibility
    }

    private fun addOverlay() {
        Log.d("OverlayService", "addOverlay")
        try {
            val width = Prefs.getGestureWidth(this)
            val height = Prefs.getGestureHeight(this)

            val lpLeft = WindowManager.LayoutParams(
                width,
                height,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lpLeft.gravity = Gravity.START or Gravity.CENTER_VERTICAL

            val lpRight = WindowManager.LayoutParams(
                width,
                height,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lpRight.gravity = Gravity.END or Gravity.CENTER_VERTICAL

            leftView = GestureView(this, true) { g -> onGesture(g) }
            rightView = GestureView(this, false) { g -> onGesture(g) }
            
            // 初始状态：如果用户设置了隐藏，仅设为透明，不设为 GONE，确保手势可用
            if (isGestureHiddenByUser) {
                leftView?.alpha = 0f
                rightView?.alpha = 0f
            }

            wm.addView(leftView, lpLeft)
            wm.addView(rightView, lpRight)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding overlay", e)
        }
    }

    private fun addControlView() {
        try {
            val size = 40 // px as requested
            val lp = WindowManager.LayoutParams(
                size,
                size,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.BOTTOM or Gravity.END
            lp.x = 20 // margin
            lp.y = 20 // margin

            controlView = ImageView(this).apply {
                setImageResource(R.drawable.ic_toggle_visibility)
                setBackgroundResource(R.drawable.bg_control_button)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    toggleGestures()
                }
            }
            wm.addView(controlView, lp)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding control view", e)
        }
    }

    private fun toggleGestures() {
        isGestureHiddenByUser = !isGestureHiddenByUser
        Prefs.setIsGestureVisible(this, !isGestureHiddenByUser)
        
        // 修正：隐藏时仅设置透明度，不设置 GONE，确保触摸依然有效
        if (isGestureHiddenByUser) {
            leftView?.animate()?.alpha(0f)?.setDuration(300)?.start()
            rightView?.animate()?.alpha(0f)?.setDuration(300)?.start()
        } else {
            leftView?.animate()?.alpha(1f)?.setDuration(300)?.start()
            rightView?.animate()?.alpha(1f)?.setDuration(300)?.start()
        }
    }
    
    private fun updateOverlayLayout() {
        if (leftView == null || rightView == null) return
        val width = Prefs.getGestureWidth(this)
        val height = Prefs.getGestureHeight(this)
        val isVisible = Prefs.getIsGestureVisible(this)
        
        try {
            // 更新尺寸
            val lpLeft = leftView?.layoutParams as WindowManager.LayoutParams
            lpLeft.width = width
            lpLeft.height = height
            wm.updateViewLayout(leftView, lpLeft)
            
            val lpRight = rightView?.layoutParams as WindowManager.LayoutParams
            lpRight.width = width
            lpRight.height = height
            wm.updateViewLayout(rightView, lpRight)

            // 更新可见性（Alpha 动画）
            val targetAlpha = if (isVisible) 1f else 0f
            leftView?.animate()?.alpha(targetAlpha)?.setDuration(300)?.start()
            rightView?.animate()?.alpha(targetAlpha)?.setDuration(300)?.start()
            
        } catch (e: Exception) {
            Log.e("OverlayService", "Error updating overlay", e)
        }
    }

    private fun removeOverlay() {
        leftView?.let { wm.removeView(it) }
        rightView?.let { wm.removeView(it) }
        leftView = null
        rightView = null
    }

    private fun onGesture(gesture: GestureType) {
        Log.d("OverlayService", "onGesture received: $gesture")
        val func = Prefs.getFunction(this, gesture)
        val pkg = Prefs.getAppPackage(this, gesture)
        Log.d("OverlayService", "Executing function: $func, pkg=$pkg")
        FunctionExecutor.execute(this, func, pkg)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("gesture_overlay", "Gesture Overlay", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, "gesture_overlay")
        } else {
            android.app.Notification.Builder(this)
        }
        return builder.setContentTitle(getString(R.string.notification_running)).setSmallIcon(android.R.drawable.ic_menu_info_details).build()
    }
}
