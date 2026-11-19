package com.dynamicalsland.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.dynamicalsland.MainActivity
import com.dynamicalsland.R
import com.dynamicalsland.model.IslandConfig
import com.dynamicalsland.model.IslandState
import com.dynamicalsland.view.DynamicIslandView
import com.dynamicalsland.util.dpToPx

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "dynamic_alsland_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW_NOTIFICATION = "com.dynamicalsland.SHOW_NOTIFICATION"
        const val ACTION_REMOVE_NOTIFICATION = "com.dynamicalsland.REMOVE_NOTIFICATION"
        const val ACTION_STOP = "com.dynamicalsland.STOP"

        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var islandView: DynamicIslandView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var config = IslandConfig()
    private val handler = Handler(Looper.getMainLooper())

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeMediaController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", 100)
            val plugged = intent.getIntExtra("plugged", 0)
            val batteryPct = (level * 100) / scale
            val isCharging = plugged != 0

            if (isCharging) {
                handler.post {
                    islandView.setState(IslandState.Charging(batteryPct, true))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        setupIslandView()
        setupMediaListener()
        registerBatteryReceiver()
    }

    private fun setupIslandView() {
        islandView = DynamicIslandView(this)
        islandView.updateConfig(config)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            dpToPx(config.pillWidthDp),
            dpToPx(config.pillHeightDp),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(config.topMarginDp)
        }

        islandView.onMediaAction = { action -> handleMediaAction(action) }
        islandView.onIslandTapped = { }

        windowManager.addView(islandView, layoutParams)
    }

    private fun setupMediaListener() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                if (!controllers.isNullOrEmpty()) {
                    setActiveMediaController(controllers[0])
                } else {
                    handler.post { islandView.setState(IslandState.Idle) }
                }
            }

            val component = android.content.ComponentName(this, AlslandNotificationListener::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(listener, component)

            val activeSessions = mediaSessionManager?.getActiveSessions(component)
            if (!activeSessions.isNullOrEmpty()) {
                setActiveMediaController(activeSessions[0])
            }
        } catch (e: SecurityException) {
            // Notification listener permission not granted yet
        }
    }

    private fun setActiveMediaController(controller: MediaController) {
        mediaCallback?.let { activeMediaController?.unregisterCallback(it) }
        activeMediaController = controller

        mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { updateMediaState() }
            override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { updateMediaState() }
        }

        controller.registerCallback(mediaCallback!!, handler)
        updateMediaState()
    }

    private fun updateMediaState() {
        val controller = activeMediaController ?: return
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        if (metadata != null) {
            val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val albumArt = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
            val duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
            val position = playbackState?.position ?: 0
            val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

            handler.post {
                islandView.setState(
                    IslandState.MediaPlayback(
                        trackTitle = title, artist = artist, albumArt = albumArt,
                        isPlaying = isPlaying, progress = progress, packageName = controller.packageName
                    )
                )
            }
        }
    }

    private fun handleMediaAction(action: String) {
        val controller = activeMediaController ?: return
        when (action) {
            "toggle" -> {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING)
                    controller.transportControls.pause()
                else controller.transportControls.play()
            }
            "next" -> controller.transportControls.skipToNext()
            "prev" -> controller.transportControls.skipToPrevious()
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_NOTIFICATION -> {
                val appName = intent.getStringExtra("app_name") ?: ""
                val title = intent.getStringExtra("title") ?: ""
                val text = intent.getStringExtra("text") ?: ""
                val pkgName = intent.getStringExtra("package_name") ?: ""
                val key = intent.getStringExtra("key") ?: ""

                handler.post {
                    islandView.setState(
                        IslandState.Notification(appName = appName, title = title, text = text, packageName = pkgName, key = key)
                    )
                }
            }
            ACTION_REMOVE_NOTIFICATION -> {
                handler.post { islandView.setState(IslandState.Idle) }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Dynamic Alsland Service", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps Dynamic Alsland running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dynamic Alsland")
            .setContentText("Dynamic Island is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        islandView.cleanup()
        try { windowManager.removeView(islandView) } catch (_: Exception) {}
        mediaCallback?.let { activeMediaController?.unregisterCallback(it) }
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
