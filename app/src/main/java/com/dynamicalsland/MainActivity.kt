package com.dynamicalsland

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dynamicalsland.service.OverlayService
import com.dynamicalsland.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnSettings: Button
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var permOverlay: LinearLayout
    private lateinit var permNotifListener: LinearLayout
    private lateinit var permNotif: LinearLayout
    private lateinit var checkOverlay: ImageView
    private lateinit var checkNotifListener: ImageView
    private lateinit var checkNotif: ImageView
    private lateinit var switchAutoStart: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }

        setContentView(R.layout.activity_main)
        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateServiceStatus()
    }

    private fun initViews() {
        btnToggle = findViewById(R.id.btnToggle)
        btnSettings = findViewById(R.id.btnSettings)
        statusText = findViewById(R.id.statusText)
        statusIndicator = findViewById(R.id.statusIndicator)
        permOverlay = findViewById(R.id.permOverlay)
        permNotifListener = findViewById(R.id.permNotifListener)
        permNotif = findViewById(R.id.permNotif)
        checkOverlay = findViewById(R.id.checkOverlay)
        checkNotifListener = findViewById(R.id.checkNotifListener)
        checkNotif = findViewById(R.id.checkNotif)
        switchAutoStart = findViewById(R.id.switchAutoStart)
    }

    private fun setupListeners() {
        btnToggle.setOnClickListener {
            if (OverlayService.isRunning) stopIslandService()
            else if (PermissionHelper.allPermissionsGranted(this)) startIslandService()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        permOverlay.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this))
                PermissionHelper.requestOverlayPermission(this)
        }

        permNotifListener.setOnClickListener {
            if (!PermissionHelper.hasNotificationListenerPermission(this))
                PermissionHelper.requestNotificationListenerPermission(this)
        }

        permNotif.setOnClickListener {
            if (!PermissionHelper.hasNotificationPermission(this))
                PermissionHelper.requestNotificationPermission(this)
        }

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("alsland_prefs", MODE_PRIVATE)
                .edit().putBoolean("auto_start", isChecked).apply()
        }

        val prefs = getSharedPreferences("alsland_prefs", MODE_PRIVATE)
        switchAutoStart.isChecked = prefs.getBoolean("auto_start", true)
    }

    private fun updatePermissionStatus() {
        val overlayGranted = PermissionHelper.hasOverlayPermission(this)
        val notifListenerGranted = PermissionHelper.hasNotificationListenerPermission(this)
        val notifGranted = PermissionHelper.hasNotificationPermission(this)

        setPermissionCheck(checkOverlay, overlayGranted)
        setPermissionCheck(checkNotifListener, notifListenerGranted)
        setPermissionCheck(checkNotif, notifGranted)

        btnToggle.isEnabled = PermissionHelper.allPermissionsGranted(this)
        btnToggle.alpha = if (btnToggle.isEnabled) 1f else 0.5f
    }

    private fun setPermissionCheck(imageView: ImageView, granted: Boolean) {
        imageView.setImageResource(
            if (granted) android.R.drawable.ic_menu_set_as else android.R.drawable.ic_menu_add
        )
        imageView.setColorFilter(
            if (granted) Color.parseColor("#4CAF50") else Color.parseColor("#FF5722")
        )
    }

    private fun updateServiceStatus() {
        if (OverlayService.isRunning) {
            statusText.text = "Active"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
            statusIndicator.setBackgroundColor(Color.parseColor("#4CAF50"))
            btnToggle.text = "⏹  Stop Dynamic Alsland"
            btnToggle.setBackgroundColor(Color.parseColor("#D32F2F"))
        } else {
            statusText.text = "Inactive"
            statusText.setTextColor(Color.parseColor("#FF5722"))
            statusIndicator.setBackgroundColor(Color.parseColor("#FF5722"))
            btnToggle.text = "▶  Start Dynamic Alsland"
            btnToggle.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
    }

    private fun startIslandService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        updateServiceStatus()
    }

    private fun stopIslandService() {
        val intent = Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP }
        startService(intent)
        window.decorView.postDelayed({ updateServiceStatus() }, 500)
    }
}
