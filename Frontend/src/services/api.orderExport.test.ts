import { afterEach, describe, expect, it, vi } from 'vitest'
import { exportManagedOrders } from './api'
import { http } from './http'

describe('order export API', () => {
  afterEach(() => vi.restoreAllMocks())

  it('requests an xlsx export with operational filters', async () => {
    const blob = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const getSpy = vi.spyOn(http, 'get').mockResolvedValue({ data: blob })

    await expect(exportManagedOrders({
      activityId: 201,
      status: 'COMPLETED',
      pickupPointId: 3,
      verificationStatus: 'VERIFIED',
    })).resolves.toBe(blob)
    expect(getSpy).toHaveBeenCalledWith('/admin/orders/export', {
      params: {
        activityId: 201,
        status: 'COMPLETED',
        pickupPointId: 3,
        verificationStatus: 'VERIFIED',
      },
      responseType: 'blob',
    })
  })
})
