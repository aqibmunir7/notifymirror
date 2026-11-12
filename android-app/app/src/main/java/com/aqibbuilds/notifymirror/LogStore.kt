package com.aqibbuilds.notifymirror

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LogEntry(
    val appName: String,
    val title: String,
    val text: String,
    val timeMillis: Long,
    val packageName: String = ""
)

object LogStore {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun add(entry: LogEntry) {
        _logs.value = (listOf(entry) + _logs.value).take(20)
    }
}
