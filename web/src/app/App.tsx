import { useEffect, useState } from 'react'
import type { ActivityInfo, ActivityWindowSnapshot, DeviceSummary, DroidScopeClient } from '../local-client/DroidScopeClient'

interface AppProps { client: DroidScopeClient }
type VisibilityFilter = 'all' | 'visible' | 'hidden'
type InspectorPage = 'windows' | 'activities'

const stateLabel: Record<DeviceSummary['state'], string> = {
  device: 'Ready', unauthorized: 'Unauthorized', offline: 'Offline', unknown: 'Unknown',
}
const activityLabel = (activity: ActivityInfo) => activity.componentName ?? activity.packageName ?? activity.id

export function App({ client }: AppProps) {
  const [devices, setDevices] = useState<DeviceSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [snapshot, setSnapshot] = useState<ActivityWindowSnapshot | null>(null)
  const [selectedWindowId, setSelectedWindowId] = useState<string | null>(null)
  const [selectedActivityId, setSelectedActivityId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [selectedSerial, setSelectedSerial] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const [captureError, setCaptureError] = useState<string | null>(null)
  const [visibilityFilter, setVisibilityFilter] = useState<VisibilityFilter>('all')
  const [page, setPage] = useState<InspectorPage>('windows')

  const selectReadyDevice = (nextDevices: DeviceSummary[]) => {
    setSelectedSerial((current) => current && nextDevices.some((device) => device.serial === current && device.ready)
      ? current : nextDevices.find((device) => device.ready)?.serial ?? null)
  }

  useEffect(() => {
    let active = true
    setError(null)
    client.listDevices().then((nextDevices) => {
      if (!active) return
      setDevices(nextDevices)
      setError(null)
      selectReadyDevice(nextDevices)
    }).catch((requestError: Error) => { if (active) setError(requestError.message) })
    const unsubscribe = client.subscribeDevices((nextDevices) => {
      if (!active) return
      setDevices(nextDevices)
      setError(null)
      selectReadyDevice(nextDevices)
    })
    return () => { active = false; unsubscribe() }
  }, [client])

  useEffect(() => {
    if (!selectedSerial) { setSnapshot(null); setSelectedWindowId(null); setSelectedActivityId(null); return }
    let active = true
    setRefreshing(true)
    setCaptureError(null)
    setSnapshot(null)
    client.getActivityWindowSnapshot(selectedSerial).then((nextSnapshot) => {
      if (!active) return
      setSnapshot(nextSnapshot)
      setSelectedWindowId(nextSnapshot.windows[0]?.id ?? null)
      setSelectedActivityId(nextSnapshot.activities[0]?.id ?? null)
      setRefreshing(false)
    }).catch((requestError: Error) => {
      if (!active) return
      setSnapshot({ capturedAtEpochMs: 0, windows: [], activities: [] })
      setCaptureError(requestError.message)
      setRefreshing(false)
    })
    return () => { active = false }
  }, [client, selectedSerial, refreshKey])

  const windows = snapshot?.windows ?? []
  const activities = snapshot?.activities ?? []
  const selectedWindow = windows.find((window) => window.id === selectedWindowId) ?? null
  const selectedActivity = activities.find((activity) => activity.id === selectedActivityId) ?? null
  const filteredWindows = windows.filter((window) => {
    const needle = query.trim().toLowerCase()
    const matchesSearch = !needle || [window.title, window.packageName ?? '', String(window.displayId)].some((value) => value.toLowerCase().includes(needle))
    const matchesVisibility = visibilityFilter === 'all' || (visibilityFilter === 'visible' && window.visible) || (visibilityFilter === 'hidden' && !window.visible)
    return matchesSearch && matchesVisibility
  })
  const showActivity = (id: string | null) => { if (!id) return; setSelectedActivityId(id); setPage('activities') }
  const showWindow = (id: string) => { setSelectedWindowId(id); setPage('windows') }

  return (
    <main className="app-shell">
      <header><h1>DroidScope</h1><span>Window and Activity Inspector</span></header>
      {devices === null && !error && <p>Connecting to Local Core…</p>}
      {error && devices === null && <p>Local Core request failed: {error}</p>}
      {devices?.length === 0 && <p>No Android devices found.</p>}
      {devices && devices.length > 0 && <div className="device-bar"><label>Device <select value={selectedSerial ?? ''} onChange={(event) => setSelectedSerial(event.target.value || null)}>{devices.map((device) => <option key={device.serial} value={device.serial}>{device.serial} — {stateLabel[device.state]}</option>)}</select></label><button className="refresh-button" onClick={() => setRefreshKey((value) => value + 1)} disabled={!selectedSerial || refreshing}>{refreshing ? 'Refreshing…' : 'Refresh Snapshot'}</button>{captureError && <span className="window-error">Snapshot capture failed: {captureError}</span>}</div>}
      {snapshot && <><nav aria-label="Inspector pages" className="inspector-nav"><button className={page === 'windows' ? 'active' : ''} onClick={() => setPage('windows')}>Windows page</button><button className={page === 'activities' ? 'active' : ''} onClick={() => setPage('activities')}>Activities page</button></nav>
        {page === 'windows' ? <section className="inspector"><div className="window-list"><input aria-label="Search windows" placeholder="Search windows" value={query} onChange={(event) => setQuery(event.target.value)} /><div className="visibility-filter" aria-label="Window visibility filter"><button className={visibilityFilter === 'all' ? 'active' : ''} onClick={() => setVisibilityFilter('all')}>All</button><button className={visibilityFilter === 'visible' ? 'active' : ''} onClick={() => setVisibilityFilter('visible')}>Visible</button><button className={visibilityFilter === 'hidden' ? 'active' : ''} onClick={() => setVisibilityFilter('hidden')}>Hidden</button></div><h2 aria-label="Windows">Windows <small>{filteredWindows.length}</small></h2>{filteredWindows.map((window) => <button className={selectedWindow === window ? 'window-row selected' : 'window-row'} key={window.id} onClick={() => setSelectedWindowId(window.id)}><strong>{window.title}</strong><span>{window.packageName ?? 'Unknown package'} · Display {window.displayId}</span><em className={window.focused ? 'state-focused' : window.visible ? 'state-visible' : 'state-hidden'}>{window.focused ? '◆ FOCUSED' : window.visible ? '● VISIBLE' : '○ HIDDEN'}</em></button>)}{filteredWindows.length === 0 && <p>No matching windows.</p>}</div><aside className="window-detail"><h2>Overview</h2>{selectedWindow ? <><h3>{selectedWindow.title}</h3><dl><dt>Package</dt><dd>{selectedWindow.packageName ?? '—'}</dd><dt>Component</dt><dd>{selectedWindow.componentName ?? '—'}</dd><dt>User</dt><dd>{selectedWindow.userId}</dd><dt>PID</dt><dd>{selectedWindow.pid < 0 ? '—' : selectedWindow.pid}</dd><dt>UID</dt><dd>{selectedWindow.uid < 0 ? '—' : selectedWindow.uid}</dd><dt>Display</dt><dd>{selectedWindow.displayId}</dd><dt>Focused</dt><dd>{String(selectedWindow.focused)}</dd><dt>Visible</dt><dd>{String(selectedWindow.visible)}</dd><dt>Has Surface</dt><dd>{String(selectedWindow.hasSurface)}</dd></dl>{selectedWindow.relatedActivityId && <button onClick={() => showActivity(selectedWindow.relatedActivityId)}>Activity: {selectedWindow.componentName ?? selectedWindow.relatedActivityId}</button>}<h2>Raw</h2><pre>{selectedWindow.rawBlock}</pre></> : <p>Select a window to inspect its details.</p>}</aside></section> : <section className="inspector"><div className="window-list"><h2 aria-label="Activities">Activities <small>{activities.length}</small></h2>{activities.map((activity) => <button className={selectedActivity === activity ? 'window-row selected' : 'window-row'} key={activity.id} onClick={() => setSelectedActivityId(activity.id)}><strong>{activityLabel(activity)}</strong><span>{activity.packageName ?? 'Unknown package'} · User {activity.userId}</span><em>{activity.state}</em></button>)}{activities.length === 0 && <p>No activities found.</p>}</div><aside className="window-detail"><h2>Overview</h2>{selectedActivity ? <><h3>{activityLabel(selectedActivity)}</h3><dl><dt>Package</dt><dd>{selectedActivity.packageName ?? '—'}</dd><dt>Component</dt><dd>{selectedActivity.componentName ?? '—'}</dd><dt>User</dt><dd>{selectedActivity.userId}</dd><dt>PID</dt><dd>{selectedActivity.pid < 0 ? '—' : selectedActivity.pid}</dd><dt>State</dt><dd>{selectedActivity.state}</dd></dl>{selectedActivity.relatedWindowIds.map((windowId) => { const window = windows.find((item) => item.id === windowId); return window ? <button key={windowId} onClick={() => showWindow(windowId)}>Window: {window.title}</button> : null })}<h2>Raw</h2><pre>{selectedActivity.rawBlock}</pre></> : <p>Select an activity to inspect its details.</p>}</aside></section>}
      </>}
    </main>
  )
}
