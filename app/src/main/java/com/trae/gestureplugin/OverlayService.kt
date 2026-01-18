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

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var leftView: GestureView? = null
    private var rightView: GestureView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("OverlayService", "onCreate")
        createChannel()
        startForeground(1, buildNotification())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlay()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun addOverlay() {
        Log.d("OverlayService", "addOverlay")
        val lpLeft = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.15f).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lpLeft.gravity = Gravity.START or Gravity.CENTER_VERTICAL

        val lpRight = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.15f).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lpRight.gravity = Gravity.END or Gravity.CENTER_VERTICAL

        leftView = GestureView(this, true) { g -> onGesture(g) }
        rightView = GestureView(this, false) { g -> onGesture(g) }
        wm.addView(leftView, lpLeft)
        wm.addView(rightView, lpRight)
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
        return builder.setContentTitle("手势插件正在运行").setSmallIcon(android.R.drawable.ic_menu_info_details).build()
    }
}
