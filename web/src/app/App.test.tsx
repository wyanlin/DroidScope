import '@testing-library/jest-dom/vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { App } from './App'
import type { DroidScopeClient } from '../local-client/DroidScopeClient'

function clientFor(devices: Awaited<ReturnType<DroidScopeClient['listDevices']>>): DroidScopeClient {
  return {
    getHealth: async () => ({ status: 'ok', platform: 'Windows', version: '0.1' }),
    listDevices: async () => devices,
    getWindows: async () => ({ focusedTarget: null, windows: [] }),
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
    }
    render(<App client={client} />)
    expect(await screen.findByText('ADB is unavailable.')).toBeInTheDocument()
  })

  it('clears a previous ADB error after a later successful load', async () => {
    const failingClient: DroidScopeClient = {
      getHealth: async () => ({ status: 'ok', platform: 'Windows', version: '0.1' }),
      listDevices: async () => { throw new Error('DEVICE_LIST_FAILED:503') },
      subscribeDevices: () => () => {},
      getWindows: async () => ({ focusedTarget: null, windows: [] }),
    }
    const { rerender } = render(<App client={failingClient} />)
    expect(await screen.findByText('ADB is unavailable.')).toBeInTheDocument()

    rerender(<App client={clientFor([{ serial: 'READY', state: 'device', ready: true }])} />)
    expect(await screen.findByText('READY — Ready')).toBeInTheDocument()
    expect(screen.queryByText('ADB is unavailable.')).not.toBeInTheDocument()
  })

  it('refreshes the selected device snapshot', async () => {
    let calls = 0
    const client = clientFor([{ serial: 'READY', state: 'device', ready: true }])
    client.getWindows = async () => { calls += 1; return { focusedTarget: null, windows: [] } }
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
      subscribeDevices: (onDevices) => { publish = onDevices; return () => {} },
    }
    render(<App client={client} />)
    await waitFor(() => expect(publish).toBeDefined())
    publish?.([{ serial: 'READY', state: 'device', ready: true }])
    expect(await screen.findByRole('button', { name: 'Refresh Snapshot' })).toBeEnabled()
  })
})
