package com.trae.gestureplugin

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "gesture_prefs"
    private fun sp(ctx: Context): SharedPreferences = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getFunction(ctx: Context, gesture: GestureType): FunctionType {
        val v = sp(ctx).getString("func_${gesture.name}", FunctionType.BACK.name) ?: FunctionType.BACK.name
        return FunctionType.valueOf(v)
    }

    fun setFunction(ctx: Context, gesture: GestureType, function: FunctionType) {
        sp(ctx).edit().putString("func_${gesture.name}", function.name).apply()
    }

    fun getAppPackage(ctx: Context, gesture: GestureType): String? {
        return sp(ctx).getString("app_${gesture.name}", null)
    }

    fun setAppPackage(ctx: Context, gesture: GestureType, pkg: String?) {
        sp(ctx).edit().putString("app_${gesture.name}", pkg).apply()
    }
}
