package com.dynamicalsland.model

sealed class IslandEvent {
    data class NotificationReceived(
        val appName: String,
        val title: String,
        val text: String,
        val packageName: String,
        val key: String
    ) : IslandEvent()

    data class NotificationRemoved(val key: String) : IslandEvent()

    data class MediaStateChanged(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val packageName: String
    ) : IslandEvent()

    data object MediaStopped : IslandEvent()
    data object Collapse : IslandEvent()
    data object Toggle : IslandEvent()

    data class TimerStarted(val durationMs: Long) : IslandEvent()
    data object TimerStopped : IslandEvent()

    data class BatteryChanged(val level: Int, val isCharging: Boolean) : IslandEvent()
}
