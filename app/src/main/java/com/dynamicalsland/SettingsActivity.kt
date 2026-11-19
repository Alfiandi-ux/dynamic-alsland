package com.dynamicalsland

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

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

        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("alsland_prefs", MODE_PRIVATE)

        // Island Size
        val seekSize = findViewById<SeekBar>(R.id.seekIslandSize)
        val txtSize = findViewById<TextView>(R.id.txtSizeValue)
        seekSize.progress = prefs.getInt("island_size", 50)
        txtSize.text = "${seekSize.progress}%"
        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                txtSize.text = "${progress}%"
                prefs.edit().putInt("island_size", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Notification Timeout
        val seekTimeout = findViewById<SeekBar>(R.id.seekTimeout)
        val txtTimeout = findViewById<TextView>(R.id.txtTimeoutValue)
        seekTimeout.progress = prefs.getInt("notif_timeout", 4)
        txtTimeout.text = "${seekTimeout.progress}s"
        seekTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                txtTimeout.text = "${progress}s"
                prefs.edit().putInt("notif_timeout", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Toggle switches
        setupSwitch(R.id.switchHaptic, "haptic", true, prefs)
        setupSwitch(R.id.switchMedia, "media_controls", true, prefs)
        setupSwitch(R.id.switchNotifications, "notifications", true, prefs)
        setupSwitch(R.id.switchBattery, "battery_indicator", true, prefs)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupSwitch(id: Int, key: String, default: Boolean, prefs: android.content.SharedPreferences) {
        val switch = findViewById<Switch>(id)
        switch.isChecked = prefs.getBoolean(key, default)
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
        }
    }
}
