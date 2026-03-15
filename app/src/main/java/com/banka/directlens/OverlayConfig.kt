/*
 * Copyright (C) 2026 Asanoha Labs
 * DirectLens is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package com.banka.directlens

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class GestureType {
    SINGLE_TAP, DOUBLE_TAP, LONG_PRESS, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT;
    fun getFriendlyName() = name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
}

enum class ActionType {
    NONE, CTS_LENS, SCREENSHOT, FLASHLIGHT, HOME, BACK, RECENTS, LOCK_SCREEN,
    OPEN_NOTIFICATIONS, OPEN_QUICK_SETTINGS, OPEN_APP, SCROLL_TOP, SCROLL_BOTTOM,
    SCREEN_OFF, TOGGLE_AUTO_ROTATE, MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS;
    fun getFriendlyName() = name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
}

data class OverlaySegment(
    val width: Int = 300,
    val height: Int = 150,
    val xOffset: Int = 0,
    val yOffset: Int = 0,
    val isEnabled: Boolean = true,
    val gestures: Map<GestureType, ActionType> = mapOf(GestureType.LONG_PRESS to ActionType.CTS_LENS),
    val gestureData: Map<GestureType, String> = emptyMap()
)

data class OverlayConfig(
    val isEnabled: Boolean = true,
    val isEnabledInLandscape: Boolean = false,
    val isVisible: Boolean = false,
    val activeSegmentIndex: Int = -1,
    val segments: List<OverlaySegment> = emptyList()
)

class OverlayConfigurationManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("directlens_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getDefaultConfig(): OverlayConfig {
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // Detection du mode de navigation
        val navMode = Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)
        val isGestureMode = navMode == 2

        // Zone 1 : Pilule centrale (Optimisée pour Gestes)
        val w1 = (screenWidth * 0.28f).toInt()
        val h1 = (40f * metrics.density).toInt()
        val x1 = (screenWidth - w1) / 2
        val y1 = screenHeight - h1
        val centerPill = OverlaySegment(
            width = w1,
            height = h1,
            xOffset = x1,
            yOffset = y1,
            isEnabled = isGestureMode,
            gestures = mapOf(GestureType.LONG_PRESS to ActionType.CTS_LENS)
        )

        // Zone 2 : Petit cube coin (Optimisée pour Boutons)
        val cubeSize = 100
        val cornerCube = OverlaySegment(
            width = cubeSize,
            height = cubeSize,
            xOffset = screenWidth - cubeSize,
            yOffset = screenHeight - cubeSize,
            isEnabled = !isGestureMode,
            gestures = mapOf(GestureType.LONG_PRESS to ActionType.CTS_LENS)
        )

        return OverlayConfig(
            isEnabled = true,
            isEnabledInLandscape = false,
            isVisible = false,
            activeSegmentIndex = -1,
            segments = listOf(centerPill, cornerCube)
        )
    }

    fun getConfig(): OverlayConfig {
        val json = prefs.getString("overlay_config", null)
        if (json != null) {
            try {
                val type = object : TypeToken<OverlayConfig>() {}.type
                return gson.fromJson(json, type)
            } catch (e: Exception) {
                return getDefaultConfig()
            }
        }
        return getDefaultConfig()
    }

    fun saveConfig(config: OverlayConfig) {
        prefs.edit().putString("overlay_config", gson.toJson(config)).apply()
    }

    fun resetConfig() {
        prefs.edit().remove("overlay_config").apply()
    }
}
