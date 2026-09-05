import type { CoreHealth, DeviceSummary, DroidScopeClient } from './DroidScopeClient'

export class HttpDroidScopeClient implements DroidScopeClient {
  private readonly token: string

  constructor(token: string) {
    this.token = token
  }

  async getHealth(): Promise<CoreHealth> {
    const response = await fetch('/api/v1/health')
    if (!response.ok) throw new Error(`HEALTH_FAILED:${response.status}`)
    return response.json()
  }

  async listDevices(): Promise<DeviceSummary[]> {
    const response = await fetch('/api/v1/devices', {
      headers: { 'X-DroidScope-Session': this.token },
    })
    if (!response.ok) throw new Error(`DEVICE_LIST_FAILED:${response.status}`)
    return (await response.json()).devices
  }
}
