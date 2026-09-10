import { useEffect, useState } from 'react'
import type { DeviceSummary, DroidScopeClient, WindowInfo } from '../local-client/DroidScopeClient'

interface AppProps {
  client: DroidScopeClient
}

type VisibilityFilter = 'all' | 'visible' | 'hidden'

const stateLabel: Record<DeviceSummary['state'], string> = {
  device: 'Ready',
  unauthorized: 'Unauthorized',
  offline: 'Offline',
  unknown: 'Unknown',
}

export function App({ client }: AppProps) {
  const [devices, setDevices] = useState<DeviceSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [windows, setWindows] = useState<WindowInfo[] | null>(null)
  const [selected, setSelected] = useState<WindowInfo | null>(null)
  const [query, setQuery] = useState('')
  const [selectedSerial, setSelectedSerial] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const [windowError, setWindowError] = useState<string | null>(null)
  const [visibilityFilter, setVisibilityFilter] = useState<VisibilityFilter>('all')

  const selectReadyDevice = (nextDevices: DeviceSummary[]) => {
    setSelectedSerial((current) => {
      if (current && nextDevices.some((device) => device.serial === current && device.ready)) return current
      return nextDevices.find((device) => device.ready)?.serial ?? null
    })
  }

  useEffect(() => {
    let active = true
    setError(null)
    client.listDevices().then((nextDevices) => {
      if (!active) return
      setDevices(nextDevices)
      setError(null)
      if (active) selectReadyDevice(nextDevices)
    }).catch((requestError: Error) => {
      if (active) setError(requestError.message)
    })
    const unsubscribe = client.subscribeDevices((nextDevices) => {
      if (active) {
        setDevices(nextDevices)
        setError(null)
        selectReadyDevice(nextDevices)
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
    setWindowError(null)
    setWindows(null)
    client.getWindows(selectedSerial).then((snapshot) => {
      if (active) { setWindows(snapshot.windows); setSelected(null); setRefreshing(false) }
    }).catch((requestError: Error) => { if (active) { setWindows([]); setWindowError(requestError.message); setRefreshing(false) } })
    return () => { active = false }
  }, [client, selectedSerial, refreshKey])

  const filteredWindows = windows?.filter((window) => {
    const needle = query.trim().toLowerCase()
    const matchesSearch = !needle || [window.title, window.packageName ?? '', String(window.displayId)].some((value) => value.toLowerCase().includes(needle))
    const matchesVisibility = visibilityFilter === 'all'
      || (visibilityFilter === 'visible' && window.visible)
      || (visibilityFilter === 'hidden' && !window.visible)
    return matchesSearch && matchesVisibility
  })

  return (
    <main className="app-shell">
      <header><h1>DroidScope</h1><span>Window Inspector</span></header>
      {devices === null && !error && <p>Connecting to Local Core…</p>}
      {error && devices === null && <p>Local Core request failed: {error}</p>}
      {devices?.length === 0 && <p>No Android devices found.</p>}
      {devices && devices.length > 0 && <div className="device-bar"><label>Device <select value={selectedSerial ?? ''} onChange={(event) => setSelectedSerial(event.target.value || null)}>{devices.map((device) => <option key={device.serial} value={device.serial}>{device.serial} — {stateLabel[device.state]}</option>)}</select></label><button className="refresh-button" onClick={() => setRefreshKey((value) => value + 1)} disabled={!selectedSerial || refreshing}>{refreshing ? 'Refreshing…' : 'Refresh Snapshot'}</button>{windowError && <span className="window-error">Window capture failed: {windowError}</span>}</div>}
      {windows !== null && <section className="inspector">
        <div className="window-list">
          <input aria-label="Search windows" placeholder="Search windows" value={query} onChange={(event) => setQuery(event.target.value)} />
          <div className="visibility-filter" aria-label="Window visibility filter">
            <button className={visibilityFilter === 'all' ? 'active' : ''} onClick={() => setVisibilityFilter('all')}>All</button>
            <button className={visibilityFilter === 'visible' ? 'active' : ''} onClick={() => setVisibilityFilter('visible')}>Visible</button>
            <button className={visibilityFilter === 'hidden' ? 'active' : ''} onClick={() => setVisibilityFilter('hidden')}>Hidden</button>
          </div>
          <h2>Windows <small>{filteredWindows?.length ?? 0}</small></h2>
          {filteredWindows?.map((window) => <button className={selected === window ? 'window-row selected' : 'window-row'} key={`${window.order}-${window.title}`} onClick={() => setSelected(window)}>
            <strong>{window.title}</strong><span>{window.packageName ?? 'Unknown package'} · Display {window.displayId}</span><em className={window.focused ? 'state-focused' : window.visible ? 'state-visible' : 'state-hidden'}>{window.focused ? '◆ FOCUSED' : window.visible ? '● VISIBLE' : '○ HIDDEN'}</em>
          </button>)}
          {filteredWindows?.length === 0 && <p>No matching windows.</p>}
        </div>
        <aside className="window-detail"><h2>Overview</h2>{selected ? <><h3>{selected.title}</h3><dl><dt>Package</dt><dd>{selected.packageName ?? '—'}</dd><dt>PID</dt><dd>{selected.pid < 0 ? '—' : selected.pid}</dd><dt>UID</dt><dd>{selected.uid < 0 ? '—' : selected.uid}</dd><dt>Display</dt><dd>{selected.displayId}</dd><dt>Focused</dt><dd>{String(selected.focused)}</dd><dt>Visible</dt><dd>{String(selected.visible)}</dd><dt>Has Surface</dt><dd>{String(selected.hasSurface)}</dd></dl><h2>Raw</h2><pre>{selected.rawBlock}</pre></> : <p>Select a window to inspect its details.</p>}</aside>
      </section>}
    </main>
  )
}
