import { useEffect, useState } from 'react'
import type { DeviceSummary, DroidScopeClient } from '../local-client/DroidScopeClient'

interface AppProps {
  client: DroidScopeClient
}

const stateLabel: Record<DeviceSummary['state'], string> = {
  device: 'Ready',
  unauthorized: 'Unauthorized',
  offline: 'Offline',
  unknown: 'Unknown',
}

export function App({ client }: AppProps) {
  const [devices, setDevices] = useState<DeviceSummary[] | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let active = true
    setError(false)
    client.listDevices().then((nextDevices) => {
      if (!active) return
      setDevices(nextDevices)
      setError(false)
    }).catch(() => {
      if (active) setError(true)
    })
    const unsubscribe = client.subscribeDevices((nextDevices) => {
      if (active) {
        setDevices(nextDevices)
        setError(false)
      }
    })
    return () => {
      active = false
      unsubscribe()
    }
  }, [client])

  return (
    <main className="app-shell">
      <h1>DroidScope</h1>
      {devices === null && !error && <p>Connecting to Local Core…</p>}
      {error && devices === null && <p>ADB is unavailable.</p>}
      {devices?.length === 0 && <p>No Android devices found.</p>}
      {devices && devices.length > 0 && (
        <ul>
          {devices.map((device) => <li key={device.serial}>{device.serial} — {stateLabel[device.state]}</li>)}
        </ul>
      )}
    </main>
  )
}
