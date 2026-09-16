import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createActivity,
  exportActivityReservationRoster,
  getActivityReservationRoster,
  getManagedActivities,
  publishActivity,
  terminateActivity,
  updateActivity,
} from './api'
import { http } from './http'
import type {
  ActivityMutationPayload,
  ApiResponse,
  FlashActivity,
  ReservationRosterItem,
} from '../types/api'

const activity: FlashActivity = {
  id: 201,
  name: '校园限定抢购',
  productId: 101,
  pickupPointId: 1,
  mode: 'FLASH_SALE',
  status: 'UNPUBLISHED',
  reviewStatus: 'PENDING',
  startAt: '2026-08-25T10:00:00',
  endAt: '2026-08-25T12:00:00',
  stock: 20,
  limitPerUser: 1,
  paymentTimeoutMinutes: 15,
  createdAt: '2026-08-24T10:00:00',
}

const payload: ActivityMutationPayload = {
  name: activity.name,
  productId: activity.productId,
  mode: activity.mode,
  startAt: activity.startAt,
  endAt: activity.endAt,
  stock: activity.stock,
  limitPerUser: activity.limitPerUser,
  paymentTimeoutMinutes: activity.paymentTimeoutMinutes,
}

describe('activity management API', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads all activity states through the protected endpoint', async () => {
    const getSpy = vi.spyOn(http, 'get').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: [activity] } satisfies ApiResponse<FlashActivity[]>,
    })

    await expect(getManagedActivities()).resolves.toEqual([activity])
    expect(getSpy).toHaveBeenCalledWith('/admin/activities')
  })

  it('creates and updates activity configuration', async () => {
    const postSpy = vi.spyOn(http, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: activity } satisfies ApiResponse<FlashActivity>,
    })
    const putSpy = vi.spyOn(http, 'put').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: activity } satisfies ApiResponse<FlashActivity>,
    })

    await createActivity(payload)
    await updateActivity(activity.id, payload)
    expect(postSpy).toHaveBeenCalledWith('/activities', payload)
    expect(putSpy).toHaveBeenCalledWith(`/admin/activities/${activity.id}`, payload)
  })

  it('publishes and terminates activity with an explicit reason', async () => {
    const postSpy = vi.spyOn(http, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: activity } satisfies ApiResponse<FlashActivity>,
    })

    await publishActivity(activity.id)
    await terminateActivity(activity.id, '排期调整')
    expect(postSpy).toHaveBeenNthCalledWith(1, `/activities/${activity.id}/publish`)
    expect(postSpy).toHaveBeenNthCalledWith(2, `/activities/${activity.id}/terminate`, { reason: '排期调整' })
  })

  it('loads the reservation roster with an optional status filter', async () => {
    const roster: ReservationRosterItem[] = [
      {
        id: 1,
        activityId: activity.id,
        activityName: activity.name,
        userId: 301,
        email: 'student@example.com',
        nickname: 'Student',
        reservationNo: 'RSV-001',
        status: 'QUALIFIED',
        lotteryBatchNo: 'BATCH-001',
        drawRank: 1,
        createdAt: '2026-08-24T10:00:00',
        updatedAt: '2026-08-24T10:01:00',
      },
    ]
    const getSpy = vi.spyOn(http, 'get').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: roster } satisfies ApiResponse<ReservationRosterItem[]>,
    })

    await expect(getActivityReservationRoster(activity.id, 'QUALIFIED')).resolves.toEqual(roster)
    expect(getSpy).toHaveBeenCalledWith(`/admin/activities/${activity.id}/reservations`, {
      params: { status: 'QUALIFIED' },
    })
  })

  it('requests reservation roster exports as an xlsx blob', async () => {
    const blob = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const getSpy = vi.spyOn(http, 'get').mockResolvedValue({ data: blob })

    await expect(exportActivityReservationRoster(activity.id, 'NOT_QUALIFIED')).resolves.toBe(blob)
    expect(getSpy).toHaveBeenCalledWith(`/admin/activities/${activity.id}/reservations/export`, {
      params: { status: 'NOT_QUALIFIED' },
      responseType: 'blob',
    })
  })
})
