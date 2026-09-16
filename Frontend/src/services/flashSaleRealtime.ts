import type { FlashSaleStatusEvent, StudentRealtimeEvent } from '../types/api'

export type RealtimeConnectionState = 'idle' | 'connecting' | 'live' | 'fallback'

interface RealtimeCallbacks {
  onEvent?: (event: StudentRealtimeEvent) => void
  onState?: (state: RealtimeConnectionState) => void
}

interface PendingWaiter {
  resolve: (event?: FlashSaleStatusEvent) => void
  timer: number
}

const terminalStatuses = new Set(['SUCCEEDED', 'FAILED'])

function isRealtimeEvent(value: unknown): value is StudentRealtimeEvent {
  if (!value || typeof value !== 'object') return false
  const event = value as Record<string, unknown>
  if (typeof event.updatedAt !== 'string') return false
  if (event.type === 'READY') return true
  if (event.type === 'FLASH_SALE_STATUS') {
    return typeof event.requestNo === 'string' && typeof event.status === 'string'
  }
  if (event.type === 'ORDER_STATUS') {
    return Boolean(event.order && typeof event.order === 'object'
      && typeof (event.order as Record<string, unknown>).id === 'number')
  }
  if (event.type === 'ACTIVITY_STATUS') {
    return Boolean(event.activity && typeof event.activity === 'object'
      && typeof (event.activity as Record<string, unknown>).id === 'number')
  }
  return false
}

function socketUrl() {
  const configured = import.meta.env.VITE_WS_BASE_URL as string | undefined
  const base = configured || window.location.origin
  const url = new URL('/ws/flash-sale', base)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

export function createFlashSaleRealtime(token: string, callbacks: RealtimeCallbacks = {}) {
  let socket: WebSocket | undefined
  let connectPromise: Promise<boolean> | undefined
  let closed = false
  const latestEvents = new Map<string, FlashSaleStatusEvent>()
  const waiters = new Map<string, Set<PendingWaiter>>()

  function setState(state: RealtimeConnectionState) {
    callbacks.onState?.(state)
  }

  function settleWaiters(requestNo: string, event?: FlashSaleStatusEvent) {
    const pending = waiters.get(requestNo)
    if (!pending) return
    pending.forEach((waiter) => {
      window.clearTimeout(waiter.timer)
      waiter.resolve(event)
    })
    waiters.delete(requestNo)
  }

  function settleAll() {
    Array.from(waiters.keys()).forEach((requestNo) => settleWaiters(requestNo))
  }

  function connect(): Promise<boolean> {
    if (socket?.readyState === WebSocket.OPEN) return Promise.resolve(true)
    if (connectPromise) return connectPromise
    closed = false
    setState('connecting')
    connectPromise = new Promise((resolve) => {
      let settled = false
      const finish = (connected: boolean) => {
        if (settled) return
        settled = true
        window.clearTimeout(timeout)
        resolve(connected)
      }
      const timeout = window.setTimeout(() => {
        setState('fallback')
        finish(false)
      }, 1_800)

      try {
        socket = new WebSocket(socketUrl(), ['campus-flash-sale', `bearer.${token}`])
        socket.onopen = () => {
          setState('live')
          finish(true)
        }
        socket.onmessage = (message) => {
          try {
            const event: unknown = JSON.parse(String(message.data))
            if (!isRealtimeEvent(event)) return
            callbacks.onEvent?.(event)
            if (event.type !== 'FLASH_SALE_STATUS') return
            latestEvents.set(event.requestNo, event)
            if (terminalStatuses.has(event.status)) settleWaiters(event.requestNo, event)
          } catch { /* Ignore malformed server frames and retain HTTP fallback. */ }
        }
        socket.onerror = () => {
          setState('fallback')
          finish(false)
        }
        socket.onclose = () => {
          socket = undefined
          connectPromise = undefined
          settleAll()
          setState(closed ? 'idle' : 'fallback')
          finish(false)
        }
      } catch {
        setState('fallback')
        finish(false)
      }
    })
    return connectPromise
  }

  function waitForFinal(requestNo: string, timeoutMs = 8_000): Promise<FlashSaleStatusEvent | undefined> {
    const latest = latestEvents.get(requestNo)
    if (latest?.status && terminalStatuses.has(latest.status)) return Promise.resolve(latest)
    if (socket?.readyState !== WebSocket.OPEN) return Promise.resolve(undefined)
    return new Promise((resolve) => {
      const waiter: PendingWaiter = {
        resolve,
        timer: window.setTimeout(() => {
          waiters.get(requestNo)?.delete(waiter)
          if (waiters.get(requestNo)?.size === 0) waiters.delete(requestNo)
          resolve(undefined)
        }, timeoutMs),
      }
      if (!waiters.has(requestNo)) waiters.set(requestNo, new Set())
      waiters.get(requestNo)?.add(waiter)
    })
  }

  function close() {
    closed = true
    settleAll()
    socket?.close(1000, 'page closed')
    socket = undefined
    connectPromise = undefined
    setState('idle')
  }

  return { connect, waitForFinal, close }
}
