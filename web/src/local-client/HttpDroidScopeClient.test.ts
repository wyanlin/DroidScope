import { afterEach, describe, expect, it, vi } from 'vitest'
import { HttpDroidScopeClient } from './HttpDroidScopeClient'

describe('HttpDroidScopeClient', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads health without a session header', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'ok', platform: 'Windows', version: '0.1' })))
    vi.stubGlobal('fetch', fetch)

    await expect(new HttpDroidScopeClient('token').getHealth()).resolves.toMatchObject({ status: 'ok' })
    expect(fetch).toHaveBeenCalledWith('/api/v1/health')
  })

  it('loads devices with the session header', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ devices: [] })))
    vi.stubGlobal('fetch', fetch)

    await expect(new HttpDroidScopeClient('token').listDevices()).resolves.toEqual([])
    expect(fetch).toHaveBeenCalledWith('/api/v1/devices', { headers: { 'X-DroidScope-Session': 'token' } })
  })
})
