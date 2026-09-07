import { useEffect, useState } from 'react'
import type { DeviceSummary, DroidScopeClient, WindowInfo } from '../local-client/DroidScopeClient'

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
  const [windows, setWindows] = useState<WindowInfo[] | null>(null)
  const [selected, setSelected] = useState<WindowInfo | null>(null)
  const [query, setQuery] = useState('')
  const [selectedSerial, setSelectedSerial] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const [windowError, setWindowError] = useState(false)

  useEffect(() => {
    let active = true
    setError(false)
    client.listDevices().then((nextDevices) => {
      if (!active) return
      setDevices(nextDevices)
      setError(false)
      const ready = nextDevices.find((device) => device.ready)
      if (active) setSelectedSerial(ready?.serial ?? null)
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

  useEffect(() => {
    if (!selectedSerial) { setWindows([]); setSelected(null); return }
    let active = true
    setRefreshing(true)
    setWindowError(false)
    setWindows(null)
    client.getWindows(selectedSerial).then((snapshot) => {
      if (active) { setWindows(snapshot.windows); setSelected(null); setRefreshing(false) }
    }).catch(() => { if (active) { setWindows([]); setWindowError(true); setRefreshing(false) } })
    return () => { active = false }
  }, [client, selectedSerial, refreshKey])

  const filteredWindows = windows?.filter((window) => {
    const needle = query.trim().toLowerCase()
    return !needle || [window.title, window.packageName ?? '', String(window.displayId)].some((value) => value.toLowerCase().includes(needle))
  })

  return (
    <main className="app-shell">
      <header><h1>DroidScope</h1><span>Window Inspector</span></header>
      {devices === null && !error && <p>Connecting to Local Core…</p>}
      {error && devices === null && <p>ADB is unavailable.</p>}
      {devices?.length === 0 && <p>No Android devices found.</p>}
      {devices && devices.length > 0 && <div className="device-bar"><label>Device <select value={selectedSerial ?? ''} onChange={(event) => setSelectedSerial(event.target.value || null)}>{devices.map((device) => <option key={device.serial} value={device.serial}>{device.serial} — {stateLabel[device.state]}</option>)}</select></label><button className="refresh-button" onClick={() => setRefreshKey((value) => value + 1)} disabled={!selectedSerial || refreshing}>{refreshing ? 'Refreshing…' : 'Refresh Snapshot'}</button>{windowError && <span className="window-error">Window capture failed</span>}</div>}
      {windows !== null && <section className="inspector">
        <div className="window-list">
          <input aria-label="Search windows" placeholder="Search windows" value={query} onChange={(event) => setQuery(event.target.value)} />
          <h2>Windows <small>{filteredWindows?.length ?? 0}</small></h2>
          {filteredWindows?.map((window) => <button className={selected === window ? 'window-row selected' : 'window-row'} key={`${window.order}-${window.title}`} onClick={() => setSelected(window)}>
            <strong>{window.title}</strong><span>{window.packageName ?? 'Unknown package'} · Display {window.displayId}</span><em>{window.focused ? '◆ FOCUSED' : window.visible ? '● VISIBLE' : '○ HIDDEN'}</em>
          </button>)}
          {filteredWindows?.length === 0 && <p>No matching windows.</p>}
        </div>
        <aside className="window-detail"><h2>Overview</h2>{selected ? <><h3>{selected.title}</h3><dl><dt>Package</dt><dd>{selected.packageName ?? '—'}</dd><dt>PID</dt><dd>{selected.pid < 0 ? '—' : selected.pid}</dd><dt>UID</dt><dd>{selected.uid < 0 ? '—' : selected.uid}</dd><dt>Display</dt><dd>{selected.displayId}</dd><dt>Focused</dt><dd>{String(selected.focused)}</dd><dt>Visible</dt><dd>{String(selected.visible)}</dd><dt>Has Surface</dt><dd>{String(selected.hasSurface)}</dd></dl><h2>Raw</h2><pre>{selected.rawBlock}</pre></> : <p>Select a window to inspect its details.</p>}</aside>
      </section>}
    </main>
  )
}
