package com.aqibbuilds.notifymirror

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPostNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var listenerEnabled by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NotifyMirrorScreen(activity = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
    }

    fun openNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Could not open battery settings", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveAndConnect(host: String, portText: String) {
        val port = portText.toIntOrNull() ?: 8765
        if (host.isBlank()) {
            Toast.makeText(this, "Enter your laptop IP first", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.save(this, host, port)
        val intent = Intent(this, MirrorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        // Also make sure the listener service is bound by the system:
        // toggling is not possible programmatically; the user must enable
        // Notification Access once (button above).
        Toast.makeText(this, "Saved — connecting to $host:$port", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun NotifyMirrorScreen(activity: MainActivity) {
    val status by WsClient.status.collectAsState()
    val logs by LogStore.logs.collectAsState()
    var host by remember { mutableStateOf(Prefs.getHost(activity)) }
    var port by remember { mutableStateOf(Prefs.getPort(activity).toString()) }
    val listenerEnabled = activity.listenerEnabled

    val (statusText, statusColor) = when (status) {
        ConnectionStatus.CONNECTED -> "Connected" to Color(0xFF4CAF50)
        ConnectionStatus.CONNECTING -> "Connecting…" to Color(0xFFFFC107)
        ConnectionStatus.DISCONNECTED -> "Disconnected" to Color(0xFFF44336)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("NotifyMirror", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Phone notifications → your Windows laptop",
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("● ", color = statusColor, fontSize = 18.sp)
                    Text(statusText, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (host.isBlank()) "No laptop configured yet"
                    else "Target: $host:${port.ifBlank { "8765" }}",
                    color = Color(0xFFCCCCCC)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (listenerEnabled) "Notification Access: granted ✓"
                    else "Notification Access: NOT granted — tap below",
                    color = if (listenerEnabled) Color(0xFF4CAF50) else Color(0xFFFFC107)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Laptop IP address") },
            placeholder = { Text("e.g. 192.168.1.5") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { activity.saveAndConnect(host, port) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) { Text("Save & Connect") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { activity.openNotificationAccess() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open Notification Access settings") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { activity.openBatterySettings() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Allow running in background (battery)") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { WsClient.sendTest() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send test notification") }

        Spacer(Modifier.height(16.dp))
        Text("Recent notifications (${logs.size})", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text(
                "Nothing forwarded yet. Get a notification on your phone and it will appear here — and on your laptop.",
                color = Color(0xFF888888)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(entry.appName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(formatTime(entry.timeMillis), color = Color(0xFF888888), fontSize = 12.sp)
                            }
                            if (entry.title.isNotBlank()) {
                                Text(entry.title, color = Color(0xFFDDDDDD), fontWeight = FontWeight.SemiBold)
                            }
                            if (entry.text.isNotBlank()) {
                                Text(entry.text, color = Color(0xFFBBBBBB))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
