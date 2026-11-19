package com.dynamicalsland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dynamicalsland.service.OverlayService
import com.dynamicalsland.util.PermissionHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("alsland_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", true)

            if (autoStart && PermissionHelper.allPermissionsGranted(context)) {
                val serviceIntent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
