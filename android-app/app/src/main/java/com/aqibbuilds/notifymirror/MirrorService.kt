package com.aqibbuilds.notifymirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings

class MirrorService : Service() {

    companion object {
        const val CHANNEL_ID = "notifymirror_running"
        const val NOTIF_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = Prefs.getHost(this)
        val port = Prefs.getPort(this)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val target = if (host.isBlank()) "not configured" else "$host:$port"
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifyMirror is running")
            .setContentText("Mirroring to $target")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
        if (host.isNotBlank()) {
            WsClient.connect(host, port, deviceId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        WsClient.disconnect()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NotifyMirror running",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows while notification mirroring is active" }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }
}
