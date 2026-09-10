import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { App } from './App'
import type { ActivityWindowSnapshot, DroidScopeClient } from '../local-client/DroidScopeClient'

const emptySnapshot = (): ActivityWindowSnapshot => ({ capturedAtEpochMs: 0, windows: [], activities: [] })

function clientFor(devices: Awaited<ReturnType<DroidScopeClient['listDevices']>>): DroidScopeClient {
  return {
    getHealth: async () => ({ status: 'ok', platform: 'Windows', version: '0.1' }),
    listDevices: async () => devices,
    getWindows: async () => ({ focusedTarget: null, windows: [] }),
    getActivityWindowSnapshot: async () => emptySnapshot(),
    subscribeDevices: () => () => {},
  }
}

describe('App', () => {
  afterEach(() => cleanup())
  it('shows the local core connection state', () => {
    render(<App client={clientFor([])} />)

    expect(screen.getByText('DroidScope')).toBeInTheDocument()
    expect(screen.getByText('Connecting to Local Core…')).toBeInTheDocument()
  })

  it('shows ready, unauthorized, offline, and empty device states', async () => {
    render(<App client={clientFor([
      { serial: 'READY', state: 'device', ready: true },
      { serial: 'LOCKED', state: 'unauthorized', ready: false },
      { serial: 'GONE', state: 'offline', ready: false },
    ])} />)

    expect(await screen.findByText('READY — Ready')).toBeInTheDocument()
    expect(screen.getByText('LOCKED — Unauthorized')).toBeInTheDocument()
    expect(screen.getByText('GONE — Offline')).toBeInTheDocument()
  })

  it('shows an empty device state', async () => {
    render(<App client={clientFor([])} />)
    expect(await screen.findByText('No Android devices found.')).toBeInTheDocument()
  })

  it('shows an ADB unavailable error', async () => {
    const client: DroidScopeClient = {
      getHealth: async () => ({ status: 'ok', platform: 'Windows', version: '0.1' }),
      listDevices: async () => { throw new Error('DEVICE_LIST_FAILED:503') },
      subscribeDevices: () => () => {},
      getWindows: async () => ({ focusedTarget: null, windows: [] }),
      getActivityWindowSnapshot: async () => emptySnapshot(),
    }
    render(<App client={client} />)
    expect(await screen.findByText('Local Core request failed: DEVICE_LIST_FAILED:503')).toBeInTheDocument()
  })

  it('clears a previous ADB error after a later successful load', async () => {
    const failingClient: DroidScopeClient = {
      getHealth: async () => ({ status: 'ok', platform: 'Windows', version: '0.1' }),
      listDevices: async () => { throw new Error('DEVICE_LIST_FAILED:503') },
      subscribeDevices: () => () => {},
      getWindows: async () => ({ focusedTarget: null, windows: [] }),
      getActivityWindowSnapshot: async () => emptySnapshot(),
    }
    const { rerender } = render(<App client={failingClient} />)
    expect(await screen.findByText('Local Core request failed: DEVICE_LIST_FAILED:503')).toBeInTheDocument()

    rerender(<App client={clientFor([{ serial: 'READY', state: 'device', ready: true }])} />)
    expect(await screen.findByText('READY — Ready')).toBeInTheDocument()
    expect(screen.queryByText(/Local Core request failed/)).not.toBeInTheDocument()
  })

  it('refreshes the selected device snapshot', async () => {
    let calls = 0
    const client = clientFor([{ serial: 'READY', state: 'device', ready: true }])
    client.getActivityWindowSnapshot = async () => { calls += 1; return emptySnapshot() }
    render(<App client={client} />)
    const button = await screen.findByRole('button', { name: 'Refresh Snapshot' })
    await waitFor(() => expect(calls).toBe(1))
    fireEvent.click(button)
    await waitFor(() => expect(calls).toBe(2))
  })

  it('selects a device delivered after the initial load', async () => {
    let publish: ((devices: Awaited<ReturnType<DroidScopeClient['listDevices']>>) => void) | undefined
    const client: DroidScopeClient = {
      getHealth: async () => ({ status: 'ok', platform: 'Windows', version: '0.1' }),
      listDevices: async () => [],
      getWindows: async () => ({ focusedTarget: null, windows: [] }),
      getActivityWindowSnapshot: async () => emptySnapshot(),
      subscribeDevices: (onDevices) => { publish = onDevices; return () => {} },
    }
    render(<App client={client} />)
    await waitFor(() => expect(publish).toBeDefined())
    publish?.([{ serial: 'READY', state: 'device', ready: true }])
    expect(await screen.findByRole('button', { name: 'Refresh Snapshot' })).toBeEnabled()
  })

  it('distinguishes window states and filters visible and hidden windows', async () => {
    const client = clientFor([{ serial: 'READY', state: 'device', ready: true }])
    client.getActivityWindowSnapshot = async () => ({ capturedAtEpochMs: 0, activities: [], windows: [
      { id: 'window:0', order: 0, title: 'focused', packageName: null, componentName: null, userId: -1, displayId: 0, pid: -1, uid: -1, focused: true, visible: true, hasSurface: true, relatedActivityId: null, rawBlock: '' },
      { id: 'window:1', order: 1, title: 'visible', packageName: null, componentName: null, userId: -1, displayId: 0, pid: -1, uid: -1, focused: false, visible: true, hasSurface: true, relatedActivityId: null, rawBlock: '' },
      { id: 'window:2', order: 2, title: 'hidden', packageName: null, componentName: null, userId: -1, displayId: 0, pid: -1, uid: -1, focused: false, visible: false, hasSurface: false, relatedActivityId: null, rawBlock: '' },
    ] })
    render(<App client={client} />)

    expect(await screen.findByText('◆ FOCUSED')).toHaveClass('state-focused')
    expect(screen.getByText('● VISIBLE')).toHaveClass('state-visible')
    expect(screen.getByText('○ HIDDEN')).toHaveClass('state-hidden')

    fireEvent.click(screen.getByRole('button', { name: 'Visible' }))
    expect(screen.getByRole('button', { name: /visible/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /hidden/ })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Hidden' }))
    expect(screen.getByRole('button', { name: /hidden/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /visible/ })).not.toBeInTheDocument()
  })

  it('navigates between a related window and activity from two independent pages', async () => {
    const client = clientFor([{ serial: 'READY', state: 'device', ready: true }])
    client.getActivityWindowSnapshot = async () => ({
      capturedAtEpochMs: 1,
      windows: [{
        id: 'window:0', order: 0, title: 'com.demo/.MainActivity', packageName: 'com.demo',
        componentName: 'com.demo/.MainActivity', userId: 0, displayId: 0, pid: 1234, uid: 1000,
        focused: true, visible: true, hasSurface: true,
        relatedActivityId: 'u0:com.demo/.MainActivity', rawBlock: 'window',
      }],
      activities: [{
        id: 'u0:com.demo/.MainActivity', userId: 0, packageName: 'com.demo',
        componentName: 'com.demo/.MainActivity', pid: 1234, state: 'RESUMED',
        relatedWindowIds: ['window:0'], rawBlock: 'activity',
      }],
    })
    render(<App client={client} />)

    expect(await screen.findByRole('heading', { name: 'Windows' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Activity: com.demo\/\.MainActivity/ }))
    expect(screen.getByRole('heading', { name: 'Activities' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Window: com.demo/.MainActivity' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Window: com.demo\/\.MainActivity/ }))
    expect(screen.getByRole('heading', { name: 'Windows' })).toBeInTheDocument()
  })

  it('shows unlinked items without an association action', async () => {
    const client = clientFor([{ serial: 'READY', state: 'device', ready: true }])
    client.getActivityWindowSnapshot = async () => ({
      capturedAtEpochMs: 1,
      windows: [{
        id: 'window:0', order: 0, title: 'SurfaceView', packageName: null,
        componentName: null, userId: -1, displayId: 0, pid: -1, uid: -1,
        focused: false, visible: true, hasSurface: true, relatedActivityId: null, rawBlock: 'window',
      }],
      activities: [{
        id: 'u0:com.demo/.MainActivity', userId: 0, packageName: 'com.demo',
        componentName: 'com.demo/.MainActivity', pid: 1234, state: 'UNKNOWN',
        relatedWindowIds: [], rawBlock: 'activity',
      }],
    })
    render(<App client={client} />)

    expect(await screen.findByRole('button', { name: /SurfaceView/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Activity:/ })).not.toBeInTheDocument()
  })
})
