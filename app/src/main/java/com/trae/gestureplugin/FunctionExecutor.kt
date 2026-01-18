package com.trae.gestureplugin

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings

import android.util.Log

import android.widget.Toast

object FunctionExecutor {
    fun execute(ctx: Context, function: FunctionType, packageName: String?) {
        Log.d("FunctionExecutor", "execute: $function")
        when (function) {
            FunctionType.BACK -> back()
            FunctionType.SCREENSHOT -> screenshot(ctx)
            FunctionType.LAUNCH_APP -> launchApp(ctx, packageName)
            FunctionType.CLEAN -> clean(ctx)
        }
    }

    private fun back() {
        GestureAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun screenshot(ctx: Context) {
        val intent = Intent(ctx, CaptureActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun launchApp(ctx: Context, pkg: String?) {
        if (pkg == null) return
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun clean(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val apps = ctx.packageManager.getInstalledApplications(0)
        apps.filter { it.packageName != ctx.packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .forEach { am.killBackgroundProcesses(it.packageName) }
        Runtime.getRuntime().gc()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(ctx, "系统清理完成", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureOverlayPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return if (!Settings.canDrawOverlays(ctx)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                false
            } else true
        }
        return true
    }
}
