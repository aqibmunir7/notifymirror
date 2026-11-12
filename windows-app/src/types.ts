interface PhoneNotification { id: string; packageName: string; appName: string; title: string; text: string; time: number }
interface Status { connected: boolean; phoneName: string; ips: string[]; port: number; muted: boolean; count: number }
declare global {
  interface Window {
    notifyMirror: {
      getHistory: () => Promise<PhoneNotification[]>
      getStatus: () => Promise<Status>
      clearHistory: () => Promise<boolean>
      setMuted: (v: boolean) => Promise<Status>
      sendTest: () => Promise<boolean>
      openWifiSettings: () => Promise<void>
      onNotification: (cb: (n: PhoneNotification) => void) => void
      onRemoved: (cb: (id: string) => void) => void
      onStatus: (cb: (s: Status) => void) => void
    }
  }
}
export type { PhoneNotification, Status }
