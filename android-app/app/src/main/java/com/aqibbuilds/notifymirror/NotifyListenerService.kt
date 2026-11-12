package com.aqibbuilds.notifymirror

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifyListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Ignore our own notifications (incl. the foreground-service one).
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getString(Notification.EXTRA_BIG_TEXT)
            ?: ""
        val ticker = sbn.notification.tickerText?.toString()

        // Skip empty / system ongoing notifications with no title+text.
        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            sbn.packageName
        }

        val id = "${sbn.packageName}:${sbn.postTime}"
        WsClient.sendNotification(
            id = id,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timeMillis = sbn.postTime,
            ticker = ticker
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val id = "${sbn.packageName}:${sbn.postTime}"
        WsClient.sendRemoved(id)
    }
}
