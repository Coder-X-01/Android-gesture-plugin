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
        
        // Try to recover current state immediately
        try {
            val rootNode = rootInActiveWindow
            val pkg = rootNode?.packageName?.toString()
            if (pkg != null) {
                Log.d("GestureAccessService", "Recovered current package: $pkg")
                currentPackage = pkg
                val intent = Intent("com.trae.gestureplugin.ACTION_WINDOW_CHANGED")
                intent.putExtra("package_name", pkg)
                intent.setPackage(this.packageName)
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e("GestureAccessService", "Error recovering state", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d("GestureAccessService", "Window changed: $packageName")
            
            if (packageName != null) {
                // Ignore events from our own package to prevent self-hiding loop
                if (packageName == this.packageName) return

                currentPackage = packageName
                val intent = Intent("com.trae.gestureplugin.ACTION_WINDOW_CHANGED")
                intent.putExtra("package_name", packageName)
                intent.setPackage(this.packageName) // Explicit broadcast to self
                sendBroadcast(intent)
                
                // Debug: verify event capture
                // android.widget.Toast.makeText(this, "Event: $packageName", android.widget.Toast.LENGTH_SHORT).show()
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
