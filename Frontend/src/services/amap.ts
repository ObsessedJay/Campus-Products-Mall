export type AMapPosition = [number, number]

export interface AMapLngLat {
  getLng(): number
  getLat(): number
}

export interface AMapMarker {
  on(event: 'click', handler: () => void): void
  setzIndex(zIndex: number): void
}

export interface AMapMap {
  add(overlays: AMapMarker[]): void
  destroy(): void
  setZoomAndCenter(zoom: number, position: AMapPosition, immediately?: boolean, duration?: number): void
}

interface AMapGeocode {
  location: AMapLngLat
}

interface AMapGeocodeResult {
  info?: string
  geocodes?: AMapGeocode[]
}

export interface AMapGeocoder {
  getLocation(address: string, callback: (status: string, result: AMapGeocodeResult | string) => void): void
}

export interface AMapNamespace {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AMapMap
  Marker: new (options: Record<string, unknown>) => AMapMarker
  Pixel: new (x: number, y: number) => unknown
  Geocoder: new (options?: Record<string, unknown>) => AMapGeocoder
}

declare global {
  interface Window {
    AMap?: AMapNamespace
    _AMapSecurityConfig?: { securityJsCode: string }
  }
}

let pendingLoad: Promise<AMapNamespace> | null = null

export function loadAMap(key: string, securityCode?: string): Promise<AMapNamespace> {
  if (window.AMap) return Promise.resolve(window.AMap)
  if (pendingLoad) return pendingLoad

  pendingLoad = new Promise((resolve, reject) => {
    if (securityCode) window._AMapSecurityConfig = { securityJsCode: securityCode }

    const script = document.createElement('script')
    const timeout = window.setTimeout(() => {
      script.remove()
      pendingLoad = null
      reject(new Error('高德地图加载超时'))
    }, 12000)

    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&plugin=AMap.Geocoder`
    script.async = true
    script.dataset.campusAmap = 'true'
    script.onload = () => {
      window.clearTimeout(timeout)
      if (window.AMap) resolve(window.AMap)
      else {
        pendingLoad = null
        reject(new Error('高德地图初始化失败'))
      }
    }
    script.onerror = () => {
      window.clearTimeout(timeout)
      script.remove()
      pendingLoad = null
      reject(new Error('高德地图脚本加载失败'))
    }
    document.head.appendChild(script)
  })

  return pendingLoad
}
