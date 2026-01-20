package com.trae.gestureplugin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

object GameUtils {
    private val KNOWN_GAME_PACKAGES = listOf(
        "com.tencent.tmgp", // Tencent Games
        "com.miHoYo",       // miHoYo Games
        "com.netease",      // NetEase Games
        "com.supercell",    // Supercell Games
        "com.activision",   // Activision
        "com.ea.gp",        // EA
        "com.nianticlabs",  // Niantic
        "com.epicgames",    // Epic Games
        "com.gameloft",     // Gameloft
        "com.nintendo",     // Nintendo
        "com.ubisoft",      // Ubisoft
        "unity.WA",         // Some Unity games
        "com.unity3d",      // Unity
        "com.mojang",       // Minecraft
        "com.roblox",       // Roblox
        "com.king",         // King (Candy Crush)
        "com.lilith",       // Lilith Games
        "com.mobile.legends", // Mobile Legends
        "com.dts.freefireth", // Free Fire
        "com.igame.atom",   // Atomic
        "com.ngame.allstar",
        "com.vng",
        "com.garena",
        "jp.konami",
        "com.square_enix"
    )

    fun isGame(context: Context, packageName: String): Boolean {
        // Check known game packages
        if (KNOWN_GAME_PACKAGES.any { packageName.contains(it, ignoreCase = true) }) {
            return true
        }

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) return true
            }
            
            // Legacy check
            if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) == ApplicationInfo.FLAG_IS_GAME) {
                return true
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
}
