import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFlashSaleRealtime } from './flashSaleRealtime'

class MockWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 3
  static current?: MockWebSocket

  readonly url: string
  readonly protocols: string[]
  readyState = MockWebSocket.CONNECTING
  onopen?: () => void
  onmessage?: (message: { data: string }) => void
  onerror?: () => void
  onclose?: () => void

  constructor(url: string, protocols: string[]) {
    this.url = url
    this.protocols = protocols
    MockWebSocket.current = this
  }

  open() {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }

  emit(payload: unknown) {
    this.onmessage?.({ data: JSON.stringify(payload) })
  }

  fail() {
    this.onerror?.()
  }

  close() {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.()
  }
}

describe('flash-sale realtime client', () => {
  beforeEach(() => {
    vi.stubGlobal('window', {
      location: { origin: 'http://127.0.0.1:5175' },
      setTimeout,
      clearTimeout,
    })
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    MockWebSocket.current = undefined
  })

  it('resolves a matching terminal status event', async () => {
    const client = createFlashSaleRealtime('test.jwt.token')
    const connected = client.connect()
    MockWebSocket.current?.open()

    expect(await connected).toBe(true)
    expect(MockWebSocket.current?.protocols).toEqual([
      'campus-flash-sale',
      'bearer.test.jwt.token',
    ])

    const result = client.waitForFinal('request-1', 500)
    MockWebSocket.current?.emit({
      type: 'FLASH_SALE_STATUS',
      requestNo: 'request-1',
      status: 'SUCCEEDED',
      orderId: 88,
      updatedAt: '2026-08-21T18:00:00',
    })

    await expect(result).resolves.toMatchObject({ status: 'SUCCEEDED', orderId: 88 })
    client.close()
  })

  it('returns fallback when the connection fails', async () => {
    const states: string[] = []
    const client = createFlashSaleRealtime('test.jwt.token', {
      onState: (state) => states.push(state),
    })
    const connected = client.connect()
    MockWebSocket.current?.fail()

    await expect(connected).resolves.toBe(false)
    expect(states).toEqual(['connecting', 'fallback'])
    await expect(client.waitForFinal('request-2')).resolves.toBeUndefined()
  })

  it('forwards ready, order, and activity events to subscribers', async () => {
    const eventTypes: string[] = []
    const client = createFlashSaleRealtime('test.jwt.token', {
      onEvent: (event) => eventTypes.push(event.type),
    })
    const connected = client.connect()
    MockWebSocket.current?.open()
    await expect(connected).resolves.toBe(true)

    MockWebSocket.current?.emit({ type: 'READY', updatedAt: '2026-08-21T18:00:00' })
    MockWebSocket.current?.emit({
      type: 'ORDER_STATUS',
      order: { id: 91, orderNo: 'ORDER-91', status: 'PAID' },
      updatedAt: '2026-08-21T18:00:01',
    })
    MockWebSocket.current?.emit({
      type: 'ACTIVITY_STATUS',
      activity: { id: 92, name: 'Campus release', status: 'RUNNING' },
      updatedAt: '2026-08-21T18:00:02',
    })
    MockWebSocket.current?.emit({ type: 'UNKNOWN', updatedAt: '2026-08-21T18:00:03' })
    MockWebSocket.current?.emit({ type: 'ORDER_STATUS', order: null, updatedAt: '2026-08-21T18:00:04' })

    expect(eventTypes).toEqual(['READY', 'ORDER_STATUS', 'ACTIVITY_STATUS'])
    client.close()
  })
})
