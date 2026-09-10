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

  it('loads the unified Activity Window snapshot', async () => {
    const payload = {
      capturedAtEpochMs: 123,
      windows: [{
        id: 'window:0',
        order: 0,
        title: 'com.demo/.MainActivity',
        packageName: 'com.demo',
        componentName: 'com.demo/.MainActivity',
        userId: 0,
        displayId: 0,
        pid: 1234,
        uid: 1000,
        focused: true,
        visible: true,
        hasSurface: true,
        relatedActivityId: 'u0:com.demo/.MainActivity',
        rawBlock: 'window',
      }],
      activities: [{
        id: 'u0:com.demo/.MainActivity',
        userId: 0,
        packageName: 'com.demo',
        componentName: 'com.demo/.MainActivity',
        pid: 1234,
        state: 'RESUMED',
        relatedWindowIds: ['window:0'],
        rawBlock: 'activity',
      }],
    }
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(payload)))
    vi.stubGlobal('fetch', fetch)

    const snapshot = await new HttpDroidScopeClient('token').getActivityWindowSnapshot('ABC123')

    expect(fetch).toHaveBeenCalledWith('/api/v1/activity-window-snapshot?serial=ABC123', {
      headers: { 'X-DroidScope-Session': 'token' },
    })
    expect(snapshot.activities[0].relatedWindowIds).toEqual(['window:0'])
  })

  it('loads the unified Inspector snapshot with surfaces', async () => {
    const payload = {
      capturedAtEpochMs: 123,
      serial: 'ABC123',
      windows: [],
      activities: [],
      surfaces: [{ id: 5635, name: 'Layer#5635', canonicalName: 'Layer', type: 'Layer', parentId: 92, childIds: [], layerStack: 0, z: 0, hasBuffer: true, metadata: { '1': 'bb270000' }, relationKind: 'EXACT_METADATA', relatedWindowId: 'window:0' }],
      relations: { surfaceWindow: [] },
    }
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(payload)))
    vi.stubGlobal('fetch', fetch)

    const snapshot = await new HttpDroidScopeClient('token').getInspectorSnapshot('ABC123')

    expect(fetch).toHaveBeenCalledWith('/api/v1/inspector-snapshot?serial=ABC123', {
      headers: { 'X-DroidScope-Session': 'token' },
    })
    expect(snapshot.surfaces[0].id).toBe(5635)
  })
})
