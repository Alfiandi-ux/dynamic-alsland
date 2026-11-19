package com.dynamicalsland.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

sealed class IslandState {
    data object Idle : IslandState()

    data class Notification(
        val appName: String,
        val title: String,
        val text: String,
        val icon: Drawable? = null,
        val packageName: String = "",
        val key: String = ""
    ) : IslandState()

    data class MediaPlayback(
        val trackTitle: String,
        val artist: String,
        val albumArt: Bitmap? = null,
        val isPlaying: Boolean = false,
        val progress: Float = 0f,
        val packageName: String = ""
    ) : IslandState()

    data class Timer(
        val remainingMillis: Long,
        val totalMillis: Long,
        val isRunning: Boolean = true
    ) : IslandState()

    data class Charging(
        val batteryLevel: Int,
        val isCharging: Boolean
    ) : IslandState()

    data class PhoneCall(
        val callerName: String,
        val duration: Long = 0,
        val isActive: Boolean = false
    ) : IslandState()
}

data class IslandConfig(
    val pillWidthDp: Int = 130,
    val pillHeightDp: Int = 36,
    val expandedWidthDp: Int = 340,
    val expandedHeightDp: Int = 180,
    val compactExpandedHeightDp: Int = 56,
    val cornerRadiusDp: Int = 24,
    val topMarginDp: Int = 8,
    val backgroundColor: Int = 0xFF1A1A1A.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFF4CAF50.toInt(),
    val animationDurationMs: Long = 350,
    val notificationTimeoutMs: Long = 4000,
    val enableHaptic: Boolean = true,
    val enableMediaControls: Boolean = true,
    val enableNotifications: Boolean = true,
    val autoCollapseMs: Long = 5000
)
