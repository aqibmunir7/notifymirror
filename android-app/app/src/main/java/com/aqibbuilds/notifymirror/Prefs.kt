package com.aqibbuilds.notifymirror

import android.content.Context

object Prefs {
    private const val NAME = "notifymirror"
    private const val KEY_HOST = "laptop_host"
    private const val KEY_PORT = "laptop_port"

    fun getHost(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_HOST, "") ?: ""

    fun getPort(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PORT, 8765)

    fun save(context: Context, host: String, port: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port)
            .apply()
    }
}
