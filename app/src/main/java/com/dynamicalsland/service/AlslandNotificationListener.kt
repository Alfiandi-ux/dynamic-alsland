package com.dynamicalsland.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AlslandNotificationListener : NotificationListenerService() {

    companion object {
        private val IGNORED_PACKAGES = setOf(
            "com.dynamicalsland",
            "android",
            "com.android.systemui"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!OverlayService.isRunning) return
        if (sbn.packageName in IGNORED_PACKAGES) return
        if (!sbn.isClearable) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_NOTIFICATION
            putExtra("app_name", appName)
            putExtra("title", title)
            putExtra("text", text)
            putExtra("package_name", sbn.packageName)
            putExtra("key", sbn.key)
        }
        startService(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!OverlayService.isRunning) return

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_REMOVE_NOTIFICATION
            putExtra("key", sbn.key)
        }
        startService(intent)
    }
}
