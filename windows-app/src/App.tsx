import { useEffect, useMemo, useState } from 'react'
import type { PhoneNotification, Status } from './types'

const DEMO: PhoneNotification[] = [
  { id: 'demo1', packageName: 'com.whatsapp', appName: 'WhatsApp', title: 'Sarah', text: 'Did you see the new bakery photos?', time: Date.now() - 2 * 60_000 },
  { id: 'demo2', packageName: 'com.google.android.gm', appName: 'Gmail', title: 'New reply on your outreach', text: 'Hi Aqib — love the preview, can we talk on Monday?', time: Date.now() - 9 * 60_000 },
  { id: 'demo3', packageName: 'com.instagram.android', appName: 'Instagram', title: 'shona liked your message', text: '❤️', time: Date.now() - 22 * 60_000 },
]

function timeAgo(t: number) {
  const s = Math.floor((Date.now() - t) / 1000)
  if (s < 60) return 'just now'
  if (s < 3600) return Math.floor(s / 60) + 'm ago'
  if (s < 86400) return Math.floor(s / 3600) + 'h ago'
  return new Date(t).toLocaleDateString()
}

function appColor(app: string) {
  const map: Record<string, string> = {
    WhatsApp: '#25D366', Gmail: '#EA4335', Instagram: '#E1306C',
    SMS: '#34A853', 'Phone': '#4285F4', YouTube: '#FF0000', Test: '#9B8CFF'
  }
  return map[app] ?? '#7C8CF8'
}

export default function App() {
  const api = (window as unknown as { notifyMirror?: Window['notifyMirror'] }).notifyMirror
  const isDesktop = !!api
  const [items, setItems] = useState<PhoneNotification[]>(isDesktop ? [] : DEMO)
  const [status, setStatus] = useState<Status | null>(null)
  const [query, setQuery] = useState('')
  const [appFilter, setAppFilter] = useState('All')
  const [demoMode, setDemoMode] = useState(!isDesktop)

  useEffect(() => {
    if (!api) return
    api.getHistory().then(setItems).catch(() => {})
    api.getStatus().then(setStatus).catch(() => {})
    api.onNotification((n) => setItems((prev) => [n, ...prev.filter((p) => p.id !== n.id)].slice(0, 500)))
    api.onRemoved((id) => setItems((prev) => prev.filter((p) => p.id !== id)))
    api.onStatus((s) => setStatus(s as Status))
  }, [api])

  const apps = useMemo(() => ['All', ...Array.from(new Set(items.map((i) => i.appName)))], [items])
  const filtered = items.filter((i) =>
    (appFilter === 'All' || i.appName === appFilter) &&
    (i.title + ' ' + i.text + ' ' + i.appName).toLowerCase().includes(query.toLowerCase())
  )

  const ip = status?.ips?.[0] ?? '—'
  const connected = demoMode ? true : !!status?.connected

  return (
    <div className="shell">
      <header className="header">
        <div>
          <h1>NotifyMirror</h1>
          <p className="sub">Your OnePlus notifications, on this laptop</p>
        </div>
        <div className={`pill ${connected ? 'on' : 'off'}`}>
          <span className="dot" />{demoMode ? 'Demo mode' : connected ? `Connected — ${status?.phoneName || 'Phone'}` : 'Waiting for phone…'}
        </div>
      </header>

      <section className="connect-card">
        <div>
          <div className="label">On your phone, connect to</div>
          <div className="ip">{ip}<span className="port">:{status?.port ?? 8765}</span></div>
          <div className="hint">Same Wi-Fi as this laptop. Type this IP into the NotifyMirror Android app → Save &amp; Connect.</div>
        </div>
        <div className="actions">
          <button onClick={() => { if (api) void api.sendTest(); else setItems((p) => [{ id: 'demo' + Date.now(), packageName: 'com.test', appName: 'Test', title: 'Test notification', text: 'If you can see this, NotifyMirror is working.', time: Date.now() }, ...p]) }}>
            Send test notification
          </button>
          <button className="ghost" onClick={() => { if (api) { void api.setMuted(!status?.muted).then(setStatus) } }}>
            {status?.muted ? 'Unmute toasts' : 'Mute toasts'}
          </button>
          <button className="ghost danger" onClick={() => { if (api) void api.clearHistory().then(() => setItems([])); else setItems([]) }}>
            Clear history
          </button>
          {!isDesktop && <button className="ghost" onClick={() => setDemoMode(!demoMode)}>{demoMode ? 'Hide demo' : 'Show demo'}</button>}
        </div>
      </section>

      <div className="toolbar">
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search notifications…" />
        <select value={appFilter} onChange={(e) => setAppFilter(e.target.value)}>
          {apps.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
        <span className="count">{filtered.length} shown · {items.length} total</span>
      </div>

      <main className="list">
        {filtered.length === 0 && (
          <div className="empty">
            <div className="empty-icon">📱 → 💻</div>
            <h2>No notifications yet</h2>
            <p>Open the Android app, enter <b>{ip}:{status?.port ?? 8765}</b>, tap Save &amp; Connect, then send yourself a WhatsApp message. It will appear here instantly.</p>
          </div>
        )}
        {filtered.map((n) => (
          <article className="card" key={n.id}>
            <div className="avatar" style={{ background: appColor(n.appName) }}>{n.appName.slice(0, 1)}</div>
            <div className="body">
              <div className="row"><b>{n.appName}</b><span className="time">{timeAgo(n.time)}</span></div>
              {n.title && <div className="title">{n.title}</div>}
              <div className="text">{n.text}</div>
              <div className="meta">{n.packageName} · {new Date(n.time).toLocaleTimeString()}</div>
            </div>
          </article>
        ))}
      </main>

      <footer>Local only — notifications travel over your Wi-Fi, never to a cloud server. Port 8765.</footer>
    </div>
  )
}
