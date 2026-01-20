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
    private var homePackage: String? = null
    private var isGestureHiddenByUser = false
    private var isCurrentStateBlocked = false
    
    private val windowStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent?.action == "com.trae.gestureplugin.ACTION_CONFIG_CHANGED") {
                    isGestureHiddenByUser = !Prefs.getIsGestureVisible(this@OverlayService)
                    updateOverlayLayout()
                    return
                }

                val pkg = intent?.getStringExtra("package_name")
                Log.d("OverlayService", "Window state changed: $pkg")
                
                // Check logic
                if (pkg == packageName) {
                    // In App: Disable everything completely
                    Log.d("OverlayService", "Entering self, disabling gestures")
                    isCurrentStateBlocked = true
                    setOverlayVisibility(false)
                } else {
                    // Check Blocked Apps / Games
                    val blockedApps = Prefs.getBlockedApps(this@OverlayService)
                    val isGameMode = Prefs.getGameModeEnabled(this@OverlayService)
                    val isBlocked = pkg != null && blockedApps.contains(pkg)
                    val isGame = isGameMode && pkg != null && GameUtils.isGame(this@OverlayService, pkg)
                    
                    isCurrentStateBlocked = isBlocked || isGame

                    // Debug Toast for user feedback
                    if (pkg != null) {
                         // Enable toast for user debugging to trace package name
                         val debugMsg = if (isBlocked) "Blocked" else if (isGame) "Game" else "Active"
                         // android.widget.Toast.makeText(context, "Pkg: $pkg\nState: $debugMsg", android.widget.Toast.LENGTH_SHORT).show()
                    }

                    // If starting/resuming a blocked app or game, we must HIDE the overlay.
                    // If leaving such an app, we must SHOW it.
                    if (isCurrentStateBlocked) {
                        Log.d("OverlayService", "Blocked/Game detected ($pkg), disabling gestures")
                        setOverlayVisibility(false)
                    } else {
                        setOverlayVisibility(true) // Ensure visible for touch
                        
                        if (pkg == homePackage) {
                             // Desktop: Hide Visuals (SystemUI removed from here to fix flickering)
                             setGestureVisuals(false)
                             // Use 0.01f instead of 0f to ensure touch events are still received
                             leftView?.animate()?.alpha(0.01f)?.setDuration(300)?.start()
                             rightView?.animate()?.alpha(0.01f)?.setDuration(300)?.start()
                        } else {
                            // Normal App
                            if (isGestureHiddenByUser) {
                                 setGestureVisuals(false)
                                 leftView?.animate()?.alpha(0.01f)?.setDuration(300)?.start()
                                 rightView?.animate()?.alpha(0.01f)?.setDuration(300)?.start()
                            } else {
                                 setGestureVisuals(true)
                                 leftView?.animate()?.alpha(1f)?.setDuration(300)?.start()
                                 rightView?.animate()?.alpha(1f)?.setDuration(300)?.start()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OverlayService", "Error in windowStateReceiver", e)
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
            
            if (currentPkg != null) {
                if (currentPkg == packageName) {
                    isCurrentStateBlocked = true
                } else {
                    val blockedApps = Prefs.getBlockedApps(this)
                    val isGameMode = Prefs.getGameModeEnabled(this)
                    val isBlocked = blockedApps.contains(currentPkg)
                    val isGame = isGameMode && GameUtils.isGame(this, currentPkg)
                    isCurrentStateBlocked = isBlocked || isGame
                }
            }

            if (isCurrentStateBlocked) {
                setOverlayVisibility(false)
            } else {
                if (isGestureHiddenByUser) {
                    setOverlayVisibility(false)
                } else if (currentPkg == homePackage || currentPkg == "com.android.systemui") {
                    setGestureVisuals(false)
                }
            }
        
        // Force a layout update to ensure views are added
        updateOverlayLayout()

        // Notify user that service started successfully
        android.widget.Toast.makeText(this, R.string.toast_service_started, android.widget.Toast.LENGTH_SHORT).show()

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
        
        // Notify user that service stopped
        android.widget.Toast.makeText(this, R.string.toast_service_stopped, android.widget.Toast.LENGTH_SHORT).show()
        
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
            // Remove existing views first to avoid duplicates or leaks
            removeOverlay()

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
                leftView?.alpha = 0.01f
                rightView?.alpha = 0.01f
            }

            wm.addView(leftView, lpLeft)
            wm.addView(rightView, lpRight)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding overlay", e)
        }
    }


    
    private fun updateOverlayLayout() {
        if (leftView == null || rightView == null) return
        val width = Prefs.getGestureWidth(this)
        val height = Prefs.getGestureHeight(this)
        val isVisible = Prefs.getIsGestureVisible(this)
        
        try {
            // Check blocking state first
            if (isCurrentStateBlocked) {
                 setOverlayVisibility(false)
                 return
            } else {
                 setOverlayVisibility(true)
            }

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
