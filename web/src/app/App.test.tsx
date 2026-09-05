import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import type { DroidScopeClient } from '../local-client/DroidScopeClient'

function clientFor(devices: Awaited<ReturnType<DroidScopeClient['listDevices']>>): DroidScopeClient {
  return {
    getHealth: async () => ({ status: 'ok', platform: 'Windows', version: '0.1' }),
    listDevices: async () => devices,
    subscribeDevices: () => () => {},
  }
}

describe('App', () => {
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
    }
    render(<App client={client} />)
    expect(await screen.findByText('ADB is unavailable.')).toBeInTheDocument()
  })
})
