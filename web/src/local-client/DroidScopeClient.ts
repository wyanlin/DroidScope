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

export interface DroidScopeClient {
  getHealth(): Promise<CoreHealth>
  listDevices(): Promise<DeviceSummary[]>
}
