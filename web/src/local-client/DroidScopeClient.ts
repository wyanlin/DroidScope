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
  id: string
  order: number
  title: string
  packageName: string | null
  componentName: string | null
  userId: number
  displayId: number
  pid: number
  uid: number
  focused: boolean
  visible: boolean
  hasSurface: boolean
  relatedActivityId: string | null
  rawBlock: string
}

export interface WindowSnapshot {
  focusedTarget: string | null
  windows: WindowInfo[]
}

export type ActivityState = 'RESUMED' | 'PAUSED' | 'UNKNOWN'

export interface ActivityInfo {
  id: string
  userId: number
  packageName: string | null
  componentName: string | null
  pid: number
  state: ActivityState
  relatedWindowIds: string[]
  rawBlock: string
}

export interface ActivityWindowSnapshot {
  capturedAtEpochMs: number
  windows: WindowInfo[]
  activities: ActivityInfo[]
}

export interface DroidScopeClient {
  getHealth(): Promise<CoreHealth>
  listDevices(): Promise<DeviceSummary[]>
  getWindows(serial: string): Promise<WindowSnapshot>
  getActivityWindowSnapshot(serial: string): Promise<ActivityWindowSnapshot>
  subscribeDevices(onDevices: (devices: DeviceSummary[]) => void): () => void
}
