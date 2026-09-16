import type { ApiResponse, ReportRecord, ReportStatus } from '../types/api'
import { http, unwrap } from './http'

export type ReviewContentType = 'PRODUCT' | 'ACTIVITY'

export interface ReviewItem {
  type: ReviewContentType
  id: number
  name: string
  status: string
  reviewStatus: string
  submittedBy?: string
  submittedAt: string
}

export interface ReviewLog {
  id: number
  contentType: ReviewContentType
  contentId: number
  reviewerId?: number
  reviewerUsername: string
  result: 'APPROVED' | 'REJECTED'
  reason?: string
  createdAt: string
}

export async function getPendingReviews(type?: ReviewContentType): Promise<ReviewItem[]> {
  return unwrap(await http.get<ApiResponse<ReviewItem[]>>('/admin/reviews', {
    params: { type, status: 'PENDING_REVIEW' },
  }))
}

export async function approveReview(type: ReviewContentType, id: number): Promise<unknown> {
  return unwrap(await http.post<ApiResponse<unknown>>(`/admin/reviews/${type}/${id}/approve`))
}

export async function rejectReview(type: ReviewContentType, id: number, reason: string): Promise<unknown> {
  return unwrap(await http.post<ApiResponse<unknown>>(`/admin/reviews/${type}/${id}/reject`, { reason }))
}

export async function getReviewLogs(type: ReviewContentType, id: number): Promise<ReviewLog[]> {
  return unwrap(await http.get<ApiResponse<ReviewLog[]>>(`/admin/reviews/${type}/${id}/logs`))
}

export async function getRecentReviewLogs(type?: ReviewContentType, limit = 100): Promise<ReviewLog[]> {
  return unwrap(await http.get<ApiResponse<ReviewLog[]>>('/admin/reviews/logs', {
    params: { type, limit },
  }))
}

export async function getReports(status?: ReportStatus): Promise<ReportRecord[]> {
  return unwrap(await http.get<ApiResponse<ReportRecord[]>>('/admin/reports', { params: { status } }))
}

export async function resolveReport(id: number, result: string): Promise<ReportRecord> {
  return unwrap(await http.post<ApiResponse<ReportRecord>>(`/admin/reports/${id}/resolve`, { result }))
}

export async function rejectReport(id: number, result: string): Promise<ReportRecord> {
  return unwrap(await http.post<ApiResponse<ReportRecord>>(`/admin/reports/${id}/reject`, { result }))
}
