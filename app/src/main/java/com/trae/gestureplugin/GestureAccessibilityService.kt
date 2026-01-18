package com.trae.gestureplugin

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        var instance: AccessibilityService? = null
    }
}
