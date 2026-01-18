package com.trae.gestureplugin

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("GestureAccessService", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d("GestureAccessService", "Window changed: $packageName")
            
            if (packageName != null) {
                currentPackage = packageName
                val intent = Intent("com.trae.gestureplugin.ACTION_WINDOW_CHANGED")
                intent.putExtra("package_name", packageName)
                sendBroadcast(intent)
            }
        }
    }

    override fun onInterrupt() {
        Log.d("GestureAccessService", "Service Interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        var instance: AccessibilityService? = null
        var currentPackage: String? = null
    }
}
