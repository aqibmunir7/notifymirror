# NotifyMirror — Android app — Build notes

**App:** NotifyMirror (`com.aqibbuilds.notifymirror`), Kotlin + Jetpack Compose (Material3), minSdk 26, targetSdk 34.
**What it does:** listens to every notification on the OnePlus phone with a
`NotificationListenerService` and forwards it over WebSocket to the Windows
laptop, which runs a WebSocket server on port **8765**. Local Wi-Fi only —
no Firebase, no internet server.

**Built APK (verified 2026-11-12, rebuilt 16:05 PKT after launcher icons switched to vector adaptive icons):**
`app/build/outputs/apk/debug/app-debug.apk` — **8.9 MB (9,249,813 bytes)**,
SHA-256 `a33a3cbed22659f75c92bd584bce202a2790442759fb76dab9ed660d350da8a8`,
package `com.aqibbuilds.notifymirror`, version 1.0 (code 1).
`./gradlew assembleDebug` — **BUILD SUCCESSFUL, 0 warnings** (final run).

## Protocol (matches the Windows app exactly)

- Connect to `ws://<laptop-ip>:8765` (IP + port typed in the app, saved in SharedPreferences).
- On connect: `{"type":"hello","device":"OnePlus","deviceId":"<android-id>"}`
- On each notification: `{"type":"notification","id":"<package>:<postTime>","packageName":"...","appName":"...","title":"...","text":"...","time":<epoch ms>,"ticker":"..."}`
- On removal: `{"type":"removed","id":"..."}`
- Heartbeat `{"type":"ping"}` every 25 s; reconnect with backoff 2 s / 5 s / 10 s / 30 s.
- The app ignores its own notifications and any notification with no title and no text.

## Files

- `settings.gradle`, `build.gradle`, `gradle.properties`, `local.properties`
- `gradlew` / `gradlew.bat` + `gradle/wrapper/` (Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24)
- `app/build.gradle` — Compose BOM 2024.06.00, OkHttp 4.12.0, coroutines 1.8.1
- `app/src/main/AndroidManifest.xml` — INTERNET, ACCESS_NETWORK_STATE,
  FOREGROUND_SERVICE (+DATA_SYNC), POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED
- `app/src/main/java/com/aqibbuilds/notifymirror/`
  - `MainActivity.kt` — dark Compose UI: connection status, laptop IP + port
    inputs, Save & Connect, Notification Access + battery-optimisation buttons,
    live log of the last 20 forwarded notifications, Send-test button
  - `NotifyListenerService.kt` — `NotificationListenerService`, forwards
    posted notifications, sends `removed` on dismissal
  - `MirrorService.kt` — foreground service (`dataSync`) holding the WebSocket,
    persistent notification "NotifyMirror is running — mirroring to <ip>"
  - `WsClient.kt` — OkHttp WebSocket, hello/ping/reconnect logic
  - `BootReceiver.kt` — restarts the service after reboot (if a laptop is configured)
  - `Prefs.kt`, `LogStore.kt` — settings + last-20 log

## Install the APK on the OnePlus

**Option A — USB + adb (easiest):**
1. On the phone: Settings → About phone → tap **Build number** 7 times to
   enable Developer options, then Developer options → turn on **USB debugging**.
2. Connect the phone to the laptop with a USB cable, allow the debugging prompt.
3. On the laptop: `adb install app-debug.apk`
   (or `adb install -r app-debug.apk` if reinstalling over an older build).

**Option B — no cable:**
Send the APK file to the phone (WhatsApp to yourself, Google Drive, USB file
transfer, etc.), open it on the phone and tap **Install**. If Android blocks
it, allow "Install unknown apps" for the app you opened it from (Chrome /
Files), then tap Install again.

## First run — 3 manual steps on the phone

1. **Find the laptop IP:** on the Windows laptop, the NotifyMirror Windows app
   shows its IP (or run `ipconfig` in Command Prompt → IPv4 Address, e.g.
   `192.168.1.5`). The phone and laptop must be on the **same Wi-Fi**.
2. **Start the Windows app first** so its WebSocket server is listening on
   port 8765.
3. **On the phone, open NotifyMirror:**
   - Type the laptop IP, leave Port as **8765**, tap **Save & Connect**.
     Status should turn green: **Connected**.
   - Tap **Open Notification Access settings** → find **NotifyMirror** in the
     list → turn it **ON**. This is the permission that lets the app actually
     see notifications — without it, nothing is forwarded. The status line in
     the app shows "Notification Access: granted ✓" when done.
   - Tap **Allow running in background (battery)** → allow NotifyMirror to
     ignore battery optimisation, so Android doesn't kill the connection.
   - Tap **Send test notification** — a fake WhatsApp test message should
     appear in the app's log *and* pop up on the laptop. Then send yourself a
     real WhatsApp/message and confirm it appears on the laptop too.

## Rebuilding

On a normal machine (Windows / Mac / Linux with Android Studio or the SDK):

```sh
cd android-app
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

**In this sandbox only:** the Gradle daemon can't talk to its client here
(loopback TCP is intercepted) and Java needs the egress CA + IPv4 for
downloads, so use the provided launcher instead:

```sh
cd android-app
./gradle-inprocess.sh assembleDebug
```

It runs Gradle in-process (no daemon), with `GRADLE_USER_HOME=~/workspace/.gradle-nm`
(fresh proxy credentials + a `cacerts` copy containing the Hatch egress CA).
`./gradlew` itself was generated by that launcher and is the standard wrapper.

## Note on the GitHub copy
The GitHub repo holds source only (no binaries): the launcher PNGs were
replaced with vector adaptive icons (`mipmap-anydpi-v26/*.xml`), the
`gradle-wrapper.jar` is not committed, and `gradle-inprocess.sh` is a
sandbox-only helper and is not in the repo. Open the project in Android
Studio and let it sync/download Gradle 8.7, or run `gradle wrapper` once —
then `./gradlew assembleDebug` works as normal.
