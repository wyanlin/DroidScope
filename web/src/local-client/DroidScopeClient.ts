export type DeviceState = 'device' | 'unauthorized' | 'offline' | 'unknown'

export interface DeviceSummary {
  serial: string
  state: DeviceState
  ready: boolean
}

export interface CoreHealth {
  status: 'ok'
  platform: string
  version: string
}

export interface WindowInfo {
  id: string
  order: number
  title: string
  packageName: string | null
  componentName: string | null
  userId: number
  displayId: number
  pid: number
  uid: number
  windowType?: number
  focused: boolean
  visible: boolean
  hasSurface: boolean
  relatedActivityId: string | null
  rawBlock: string
}

export interface WindowSnapshot {
  focusedTarget: string | null
  windows: WindowInfo[]
}

export type ActivityState = 'RESUMED' | 'PAUSED' | 'UNKNOWN'

export interface ActivityInfo {
  id: string
  userId: number
  packageName: string | null
  componentName: string | null
  pid: number
  state: ActivityState
  relatedWindowIds: string[]
  rawBlock: string
}

export interface ActivityWindowSnapshot {
  capturedAtEpochMs: number
  windows: WindowInfo[]
  activities: ActivityInfo[]
}

export type SurfaceRelationKind = 'EXACT_METADATA' | 'UNLINKED' | 'AMBIGUOUS'

export interface SurfaceLayerInfo {
  id: number
  name: string | null
  canonicalName: string | null
  type: string | null
  parentId: number | null
  childIds: number[]
  layerStack: number | null
  z: number | null
  bounds: { left: number; top: number; right: number; bottom: number } | null
  screenBounds: { left: number; top: number; right: number; bottom: number } | null
  hasBuffer: boolean
  activeBuffer: { width: number; height: number; stride: number; format: number } | null
  currentFrame: number | null
  inputWindowInfo: { layoutParamsType: number | null; frame: number[] | null } | null
  metadata: Record<string, string>
  relationKind: SurfaceRelationKind
  relatedWindowId: string | null
}

export interface SurfaceWindowRelation {
  windowId: string | null
  surfaceId: number | null
  kind: SurfaceRelationKind
  candidateCount: number
}

export interface InspectorSnapshot extends ActivityWindowSnapshot {
  serial: string
  surfaces: SurfaceLayerInfo[]
  relations: { surfaceWindow: SurfaceWindowRelation[] }
}

export interface DroidScopeClient {
  getHealth(): Promise<CoreHealth>
  listDevices(): Promise<DeviceSummary[]>
  getWindows(serial: string): Promise<WindowSnapshot>
  getActivityWindowSnapshot(serial: string): Promise<ActivityWindowSnapshot>
  getInspectorSnapshot?: (serial: string) => Promise<InspectorSnapshot>
  subscribeDevices(onDevices: (devices: DeviceSummary[]) => void): () => void
}
