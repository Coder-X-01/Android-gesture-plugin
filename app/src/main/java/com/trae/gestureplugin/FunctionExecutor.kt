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
        if (Prefs.getUseMediaProjection(ctx)) {
            // 使用旧版 MediaProjection (会有弹窗)
            val intent = Intent(ctx, CaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } else {
            // 使用新版 AccessibilityService (无弹窗，Android 9.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val result = GestureAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                if (result != true) {
                    android.widget.Toast.makeText(ctx, "截屏失败，请确保辅助功能已开启", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(ctx, "当前系统版本不支持无弹窗截屏 (需 Android 9.0+)，请在设置中开启“启用截图确认弹窗”", android.widget.Toast.LENGTH_LONG).show()
            }
        }
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
