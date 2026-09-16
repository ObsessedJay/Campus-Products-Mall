<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { LoaderCircle, MapPinned, RefreshCw, TriangleAlert } from 'lucide-vue-next'
import { loadAMap } from '../services/amap'
import type { AMapMap, AMapMarker, AMapNamespace, AMapPosition } from '../services/amap'
import type { PickupPoint } from '../types/api'

const props = defineProps<{
  points: PickupPoint[]
  selectedPointId?: number
  school?: string
}>()

const emit = defineEmits<{ select: [pointId: number] }>()

type MapState = 'idle' | 'loading' | 'ready' | 'missing-key' | 'empty' | 'error'

const mapElement = ref<HTMLElement | null>(null)
const state = ref<MapState>('idle')
const errorMessage = ref('')
const selectedPoint = computed(() => props.points.find((point) => point.id === props.selectedPointId))
const amapKey = import.meta.env.VITE_AMAP_KEY?.trim() || ''
const amapSecurityCode = import.meta.env.VITE_AMAP_SECURITY_CODE?.trim() || ''

let amap: AMapNamespace | null = null
let map: AMapMap | null = null
let renderVersion = 0
let mounted = false
const markers = new Map<number, { marker: AMapMarker; element: HTMLButtonElement; position: AMapPosition }>()

function storedPosition(point: PickupPoint): AMapPosition | null {
  const longitude = Number(point.longitude)
  const latitude = Number(point.latitude)
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
  if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) return null
  return [longitude, latitude]
}

function geocodePoint(api: AMapNamespace, point: PickupPoint): Promise<AMapPosition | null> {
  const stored = storedPosition(point)
  if (stored) return Promise.resolve(stored)

  const query = [props.school, point.campus, point.address].filter(Boolean).join(' ')
  if (!query) return Promise.resolve(null)

  return new Promise((resolve) => {
    const geocoder = new api.Geocoder({ city: '全国', radius: 1000 })
    geocoder.getLocation(query, (status, result) => {
      if (status !== 'complete' || typeof result === 'string') {
        resolve(null)
        return
      }
      const location = result.geocodes?.[0]?.location
      resolve(location ? [location.getLng(), location.getLat()] : null)
    })
  })
}

function destroyMap() {
  map?.destroy()
  map = null
  markers.clear()
}

function syncSelection(shouldPan = true) {
  markers.forEach(({ marker, element, position }, pointId) => {
    const active = pointId === props.selectedPointId
    element.classList.toggle('selected', active)
    element.setAttribute('aria-pressed', String(active))
    marker.setzIndex(active ? 120 : 100)
    if (active && shouldPan) map?.setZoomAndCenter(15, position, false, 240)
  })
}

async function renderMap() {
  const version = ++renderVersion
  destroyMap()
  errorMessage.value = ''

  if (!props.points.length) {
    state.value = 'empty'
    return
  }
  if (!amapKey) {
    state.value = 'missing-key'
    return
  }

  state.value = 'loading'
  await nextTick()
  if (!mapElement.value) return

  try {
    amap = await loadAMap(amapKey, amapSecurityCode)
    const positions = await Promise.all(props.points.map((point) => geocodePoint(amap!, point)))
    if (version !== renderVersion || !mapElement.value) return

    const locatedPoints = props.points.flatMap((point, index) => {
      const position = positions[index]
      return position ? [{ point, position }] : []
    })
    if (!locatedPoints.length) {
      state.value = 'empty'
      return
    }

    const center: AMapPosition = [
      locatedPoints.reduce((sum, item) => sum + item.position[0], 0) / locatedPoints.length,
      locatedPoints.reduce((sum, item) => sum + item.position[1], 0) / locatedPoints.length,
    ]
    map = new amap.Map(mapElement.value, {
      center,
      zoom: 15,
      viewMode: '2D',
      resizeEnable: true,
      showLabel: true,
    })

    locatedPoints.forEach(({ point, position }, index) => {
      const element = document.createElement('button')
      element.type = 'button'
      element.className = 'pickup-map-marker'
      element.textContent = String(index + 1)
      element.title = point.name
      element.setAttribute('aria-label', `选择领取点：${point.name}`)

      const marker = new amap!.Marker({
        position,
        content: element,
        offset: new amap!.Pixel(-18, -38),
        title: point.name,
        zIndex: 100,
      })
      element.addEventListener('click', () => emit('select', point.id))
      markers.set(point.id, { marker, element, position })
    })

    const overlays = [...markers.values()].map(({ marker }) => marker)
    map.add(overlays)
    syncSelection(false)
    state.value = 'ready'
  } catch (error) {
    if (version !== renderVersion) return
    destroyMap()
    state.value = 'error'
    errorMessage.value = error instanceof Error ? error.message : '地图服务暂时不可用'
  }
}

watch(() => props.selectedPointId, () => syncSelection())
watch(
  () => [props.points, props.school],
  () => { if (mounted) renderMap() },
  { deep: true },
)
onMounted(() => {
  mounted = true
  renderMap()
})
onBeforeUnmount(() => {
  mounted = false
  renderVersion += 1
  destroyMap()
})
</script>

<template>
  <section class="pickup-map-panel" aria-labelledby="pickup-map-title">
    <header>
      <h3 id="pickup-map-title"><MapPinned :size="17" />领取点地图</h3>
      <span>高德地图</span>
    </header>

    <div class="pickup-map-frame">
      <div ref="mapElement" class="pickup-map-canvas" :aria-hidden="state !== 'ready'"></div>

      <div v-if="state === 'loading'" class="pickup-map-state" role="status">
        <LoaderCircle class="auth-loading-icon" :size="24" />
        <strong>正在定位领取点</strong>
      </div>
      <div v-else-if="state === 'missing-key'" class="pickup-map-state">
        <MapPinned :size="25" />
        <strong>地图服务尚未配置</strong>
        <small>领取点列表仍可正常选择。</small>
      </div>
      <div v-else-if="state === 'empty'" class="pickup-map-state">
        <MapPinned :size="25" />
        <strong>暂时无法定位领取点</strong>
        <small>请按文字地址确认领取位置。</small>
        <button type="button" @click="renderMap"><RefreshCw :size="15" />重新定位</button>
      </div>
      <div v-else-if="state === 'error'" class="pickup-map-state error" role="alert">
        <TriangleAlert :size="25" />
        <strong>{{ errorMessage }}</strong>
        <small>领取点列表仍可正常选择。</small>
        <button type="button" @click="renderMap"><RefreshCw :size="15" />重新加载</button>
      </div>
    </div>

    <footer v-if="selectedPoint">
      <MapPinned :size="15" />
      <span><strong>{{ selectedPoint.name }}</strong>{{ selectedPoint.campus }} · {{ selectedPoint.address }}</span>
    </footer>
    <footer v-else class="muted"><MapPinned :size="15" /><span>尚未选择默认领取点</span></footer>
  </section>
</template>
