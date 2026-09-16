import { afterEach, describe, expect, it, vi } from 'vitest'
import { approveReview, getPendingReviews, getRecentReviewLogs, getReviewLogs, rejectReview, type ReviewItem, type ReviewLog } from './admin'
import { http } from './http'
import type { ApiResponse } from '../types/api'

const review: ReviewItem = {
  type: 'PRODUCT',
  id: 21,
  name: '校园纪念册',
  status: 'PENDING_REVIEW',
  reviewStatus: 'PENDING_REVIEW',
  submittedBy: 'operator@example.com',
  submittedAt: '2026-08-24T10:00:00',
}

const log: ReviewLog = {
  id: 31,
  contentType: 'PRODUCT',
  contentId: review.id,
  reviewerUsername: 'admin@example.com',
  result: 'APPROVED',
  createdAt: '2026-08-24T10:30:00',
}

describe('review management API', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads pending content and bounded recent logs', async () => {
    const getSpy = vi.spyOn(http, 'get')
      .mockResolvedValueOnce({
        data: { success: true, code: 'OK', message: 'success', data: [review] } satisfies ApiResponse<ReviewItem[]>,
      })
      .mockResolvedValueOnce({
        data: { success: true, code: 'OK', message: 'success', data: [log] } satisfies ApiResponse<ReviewLog[]>,
      })

    await expect(getPendingReviews('PRODUCT')).resolves.toEqual([review])
    await expect(getRecentReviewLogs('PRODUCT', 50)).resolves.toEqual([log])
    expect(getSpy).toHaveBeenNthCalledWith(1, '/admin/reviews', { params: { type: 'PRODUCT', status: 'PENDING_REVIEW' } })
    expect(getSpy).toHaveBeenNthCalledWith(2, '/admin/reviews/logs', { params: { type: 'PRODUCT', limit: 50 } })
  })

  it('uses content-scoped logs and explicit review actions', async () => {
    const getSpy = vi.spyOn(http, 'get').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: [log] } satisfies ApiResponse<ReviewLog[]>,
    })
    const postSpy = vi.spyOn(http, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: review } satisfies ApiResponse<ReviewItem>,
    })

    await getReviewLogs(review.type, review.id)
    await approveReview(review.type, review.id)
    await rejectReview(review.type, review.id, '图片信息不完整')

    expect(getSpy).toHaveBeenCalledWith('/admin/reviews/PRODUCT/21/logs')
    expect(postSpy).toHaveBeenNthCalledWith(1, '/admin/reviews/PRODUCT/21/approve')
    expect(postSpy).toHaveBeenNthCalledWith(2, '/admin/reviews/PRODUCT/21/reject', { reason: '图片信息不完整' })
  })
})
