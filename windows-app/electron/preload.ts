import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('notifyMirror', {
  getHistory: () => ipcRenderer.invoke('get-history'),
  getStatus: () => ipcRenderer.invoke('get-status'),
  clearHistory: () => ipcRenderer.invoke('clear-history'),
  setMuted: (v: boolean) => ipcRenderer.invoke('set-muted', v),
  sendTest: () => ipcRenderer.invoke('send-test'),
  openWifiSettings: () => ipcRenderer.invoke('open-wifi-settings'),
  onNotification: (cb: (n: unknown) => void) => ipcRenderer.on('notification', (_e, n) => cb(n)),
  onRemoved: (cb: (id: string) => void) => ipcRenderer.on('removed', (_e, id) => cb(id)),
  onStatus: (cb: (s: unknown) => void) => ipcRenderer.on('status', (_e, s) => cb(s))
})
