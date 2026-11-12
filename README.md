# NotifyMirror — Phone notifications on your Windows laptop

Your own app. No cloud, no subscription. Your OnePlus sends every notification
over your home Wi-Fi to your laptop, which shows it as a native Windows toast
and keeps it in a history list.

## How it works

```
OnePlus (Android app)  --WebSocket ws://<laptop-ip>:8765-->  Windows (Electron app)
  NotificationListenerService                                  ws server + native toasts
  captures WhatsApp, Gmail, Instagram, SMS, calls...           + searchable history
```

- **Transport:** WebSocket, port 8765, local Wi-Fi only. Nothing leaves your network.
- **Protocol:** JSON — `hello` / `notification` / `removed` / `ping` / `pong`.
  See `windows-app/electron/main.ts` and the Android `MirrorClient.kt` — same shape both sides.

## Folder layout

```
notification-mirror-app/
  README.md                  <- this file
  android-app/               <- Kotlin + Jetpack Compose app for the OnePlus
  windows-app/               <- Electron + React + TypeScript app for the laptop
    electron/main.ts         <- WebSocket server (8765), toasts, history file
    electron/preload.ts      <- safe bridge to the UI
    src/App.tsx              <- laptop UI: status, IP, search, history
```

## Setup — 10 minutes

### 1. Windows laptop
```bash
cd windows-app
npm install
npm run build
npm start          # dev run — shows the app + starts the server on 8765
# For a real installer later: npm run dist:win   (builds a .exe installer)
```
- The app shows your laptop's Wi-Fi IP big at the top, e.g. `192.168.1.12:8765`.
  **Write that down — the phone needs it.**
- Windows Firewall: on first run, allow NotifyMirror on Private networks.
  If the phone can't connect, that's almost always the firewall.
- Both devices must be on the **same Wi-Fi**.

### 2. OnePlus phone
- Install `android-app/app/build/outputs/apk/debug/app-debug.apk`
  (transfer it, or `adb install app-debug.apk`).
- Open NotifyMirror → enter the laptop IP + port 8765 → **Save & Connect**.
- Tap **Enable Notification Access** → turn on NotifyMirror in Settings.
- Tap **Ignore battery optimisation** so Android doesn't kill the connection.
- Send yourself a WhatsApp message — it should pop up on the laptop instantly.

### 3. Test without the phone
In the Windows app, click **Send test notification**. If a toast appears,
the laptop side is working — the problem is then the phone side / Wi-Fi / firewall.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Phone says Disconnected | Same Wi-Fi? Laptop IP changed? (Router reboots change it — re-enter it.) Firewall allowed? |
| Laptop shows nothing, phone says Connected | Check the app's log — empty title+text notifications are skipped by design. |
| Stops after a while | Battery optimisation on the phone — set NotifyMirror to Unrestricted. Keep the foreground notification. |
| Works at home, not outside | By design — v1 is local Wi-Fi only. Cloud relay (Firebase/Supabase) is the v2 step if you want it anywhere. |

## What's v1 / what's next

- **v1 (this build):** view all notifications, native toasts, search/filter by app, history saved locally (500 max), mute, clear, test button.
- **v2 ideas:** reply to WhatsApp/SMS from the laptop (RemoteInput), dismiss on phone from laptop, cloud relay for outside Wi-Fi, per-app mute.

## Verified

- Windows app: `npm run build` passes (TypeScript + Vite). Electron launched under
  Xvfb and its WebSocket server answered on port 8765. Protocol test (hello →
  notification → ping) passes.
- Android app: built with Gradle — see `android-app/BUILD_NOTES.md` for the APK
  path and install steps.
