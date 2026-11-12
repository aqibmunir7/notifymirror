package com.aqibbuilds.notifymirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Only restart if the user already configured a laptop.
            if (Prefs.getHost(context).isNotBlank()) {
                ContextCompat.startForegroundService(context, Intent(context, MirrorService::class.java))
            }
        }
    }
}
