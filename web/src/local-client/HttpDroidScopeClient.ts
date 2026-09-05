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

  subscribeDevices(onDevices: (devices: DeviceSummary[]) => void): () => void {
    let source: EventSource | undefined
    let cancelled = false
    void fetch('/api/v1/event-tickets', { method: 'POST', headers: { 'X-DroidScope-Session': this.token } })
      .then(async (response) => {
        if (!response.ok) throw new Error(`EVENT_TICKET_FAILED:${response.status}`)
        return response.json()
      })
      .then(({ ticket }) => {
        if (cancelled) return
        source = new EventSource(`/api/v1/events?ticket=${encodeURIComponent(ticket)}`)
        source.addEventListener('devices', (event) => onDevices(JSON.parse((event as MessageEvent).data).devices))
      })
      .catch(() => undefined)
    return () => { cancelled = true; source?.close() }
  }
}
