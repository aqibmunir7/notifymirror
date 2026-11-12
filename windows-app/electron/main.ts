import { app, BrowserWindow, Notification, ipcMain, shell } from 'electron'
import * as path from 'path'
import * as fs from 'fs'
import * as os from 'os'
import { WebSocketServer, WebSocket } from 'ws'

const PORT = 8765
const STORE_FILE = path.join(app.getPath('userData'), 'notifications.json')
const MAX_HISTORY = 500

export interface PhoneNotification {
  id: string
  packageName: string
  appName: string
  title: string
  text: string
  time: number
  ticker?: string
}

let win: BrowserWindow | null = null
let history: PhoneNotification[] = []
let muted = false
let phoneConnected = false
let phoneName = ''

function loadHistory() {
  try {
    if (fs.existsSync(STORE_FILE)) {
      history = JSON.parse(fs.readFileSync(STORE_FILE, 'utf-8')) as PhoneNotification[]
    }
  } catch { history = [] }
}

function saveHistory() {
  try {
    fs.mkdirSync(path.dirname(STORE_FILE), { recursive: true })
    fs.writeFileSync(STORE_FILE, JSON.stringify(history.slice(0, MAX_HISTORY), null, 2))
  } catch { /* disk full / locked — history stays in memory */ }
}

function localIPs(): string[] {
  const out: string[] = []
  for (const [, addrs] of Object.entries(os.networkInterfaces())) {
    for (const a of addrs ?? []) {
      if (a.family === 'IPv4' && !a.internal) out.push(a.address)
    }
  }
  return out
}

function pushToUI(channel: string, payload: unknown) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload)
}

function handleNotification(n: PhoneNotification) {
  // Drop empties and our own noise
  if (!n.title && !n.text) return
  if (n.packageName === 'com.aqibbuilds.notifymirror') return
  // De-dupe by id, newest first
  history = [n, ...history.filter((h) => h.id !== n.id)].slice(0, MAX_HISTORY)
  saveHistory()
  pushToUI('notification', n)
  pushToUI('status', statusPayload())
  if (!muted) {
    const toast = new Notification({
      title: n.appName + (n.title ? ` — ${n.title}` : ''),
      body: n.text || '(no text)',
      silent: false
    })
    toast.on('click', () => { if (win) { win.show(); win.focus() } })
    toast.show()
  }
}

function statusPayload() {
  return { connected: phoneConnected, phoneName, ips: localIPs(), port: PORT, muted, count: history.length }
}

function startServer() {
  const wss = new WebSocketServer({ port: PORT })
  wss.on('connection', (ws: WebSocket) => {
    phoneConnected = true
    pushToUI('status', statusPayload())
    ws.on('message', (raw: Buffer) => {
      try {
        const msg = JSON.parse(raw.toString())
        if (msg.type === 'hello') {
          phoneName = msg.device || 'Android phone'
          pushToUI('status', statusPayload())
          ws.send(JSON.stringify({ type: 'ack', server: 'NotifyMirror' }))
        } else if (msg.type === 'notification') {
          handleNotification(msg as PhoneNotification)
        } else if (msg.type === 'removed') {
          history = history.filter((h) => h.id !== msg.id)
          saveHistory()
          pushToUI('removed', msg.id)
          pushToUI('status', statusPayload())
        } else if (msg.type === 'ping') {
          ws.send(JSON.stringify({ type: 'pong' }))
        }
      } catch { /* ignore one bad frame */ }
    })
    ws.on('close', () => {
      // Only mark disconnected when no clients remain
      if (wss.clients.size === 0) { phoneConnected = false; pushToUI('status', statusPayload()) }
    })
  })
  wss.on('error', (err) => console.error('WS server error:', err.message))
}

function createWindow() {
  win = new BrowserWindow({
    width: 980, height: 720, minWidth: 720, minHeight: 520,
    title: 'NotifyMirror',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })
  const devUrl = process.env.VITE_DEV_SERVER_URL
  if (devUrl) void win.loadURL(devUrl)
  else void win.loadFile(path.join(__dirname, '../dist/index.html'))
  win.on('closed', () => { win = null })
}

app.whenReady().then(() => {
  loadHistory()
  startServer()
  createWindow()
  ipcMain.handle('get-history', () => history)
  ipcMain.handle('get-status', () => statusPayload())
  ipcMain.handle('clear-history', () => { history = []; saveHistory(); return true })
  ipcMain.handle('set-muted', (_e, v: boolean) => { muted = v; return statusPayload() })
  ipcMain.handle('send-test', () => {
    handleNotification({
      id: 'test:' + Date.now(), packageName: 'com.test', appName: 'Test',
      title: 'Test notification', text: 'If you can see this, NotifyMirror is working.', time: Date.now()
    })
    return true
  })
  ipcMain.handle('open-wifi-settings', () => { void shell.openExternal('ms-settings:network-wifi') })
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow() })
})

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit() })
