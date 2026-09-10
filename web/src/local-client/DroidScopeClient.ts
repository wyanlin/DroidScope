export type DeviceState = 'device' | 'unauthorized' | 'offline' | 'unknown'

export interface DeviceSummary {
  serial: string
  state: DeviceState
  ready: boolean
}

export interface CoreHealth {
  status: 'ok'
  platform: string
  version: string
}

export interface WindowInfo {
  order: number
  title: string
  packageName: string | null
  displayId: number
  pid: number
  uid: number
  focused: boolean
  visible: boolean
  hasSurface: boolean
  rawBlock: string
}

export interface WindowSnapshot {
  focusedTarget: string | null
  windows: WindowInfo[]
}

export interface DroidScopeClient {
  getHealth(): Promise<CoreHealth>
  listDevices(): Promise<DeviceSummary[]>
  getWindows(serial: string): Promise<WindowSnapshot>
  subscribeDevices(onDevices: (devices: DeviceSummary[]) => void): () => void
}
