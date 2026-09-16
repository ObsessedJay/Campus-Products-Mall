import { afterEach, describe, expect, it, vi } from 'vitest'
import { getMessages, getUnreadMessageCount, markAllMessagesRead, markMessageRead } from './api'
import { http } from './http'
import type { ApiResponse, UserMessage } from '../types/api'

const message: UserMessage = {
  id: 11,
  type: 'PAYMENT_SUCCESS',
  title: '支付成功，等待领取',
  content: '订单已支付。',
  link: '/orders?orderId=31',
  createdAt: '2026-08-25T14:00:00',
}

describe('notification API', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads messages and unread count', async () => {
    const getSpy = vi.spyOn(http, 'get')
      .mockResolvedValueOnce({
        data: { success: true, code: 'OK', message: 'success', data: [message] } satisfies ApiResponse<UserMessage[]>,
      })
      .mockResolvedValueOnce({
        data: { success: true, code: 'OK', message: 'success', data: { count: 1 } } satisfies ApiResponse<{ count: number }>,
      })

    await expect(getMessages(true)).resolves.toEqual([message])
    await expect(getUnreadMessageCount()).resolves.toBe(1)
    expect(getSpy).toHaveBeenNthCalledWith(1, '/users/me/messages', { params: { unreadOnly: true } })
    expect(getSpy).toHaveBeenNthCalledWith(2, '/users/me/messages/unread-count')
  })

  it('marks one or all messages as read', async () => {
    const postSpy = vi.spyOn(http, 'post')
      .mockResolvedValueOnce({ data: { success: true, code: 'OK', message: 'success', data: null } })
      .mockResolvedValueOnce({
        data: { success: true, code: 'OK', message: 'success', data: { updated: 2 } } satisfies ApiResponse<{ updated: number }>,
      })

    await expect(markMessageRead(11)).resolves.toBeUndefined()
    await expect(markAllMessagesRead()).resolves.toBe(2)
    expect(postSpy).toHaveBeenNthCalledWith(1, '/users/me/messages/11/read')
    expect(postSpy).toHaveBeenNthCalledWith(2, '/users/me/messages/read-all')
  })
})
