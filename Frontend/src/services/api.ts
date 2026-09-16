import type {
  ApiResponse,
  ActivityMutationPayload,
  AuthUser,
  Category,
  CategoryMutationPayload,
  CaptchaChallenge,
  EmailCodeReceipt,
  FlashActivity,
  FlashSaleRequest,
  FlashSaleCompensation,
  LotteryDrawResult,
  InventoryReconciliation,
  InventorySnapshot,
  PageResponse,
  Product,
  ProductImage,
  ProductSku,
  ProductSkuMutationPayload,
  ProductMutationPayload,
  ProductReview,
  ProductQuery,
  OrderDetail,
  OrderStatus,
  PaymentRecord,
  PickupPoint,
  PickupVerification,
  RefundRecord,
  Reservation,
  ReservationRosterItem,
  ReservationStatus,
  TradeOrder,
  UserProfile,
  UserRole,
  UserMessage,
  ReportRecord,
  ReportTargetType,
} from '../types/api'
import { http, unwrap } from './http'

export async function getProducts(query: ProductQuery = {}): Promise<PageResponse<Product>> {
  return unwrap(await http.get<ApiResponse<PageResponse<Product>>>('/products', { params: query }))
}

export async function getProduct(id: number): Promise<Product> {
  return unwrap(await http.get<ApiResponse<Product>>(`/products/${id}`))
}

export async function getManagedProducts(query: {
  keyword?: string
  status?: Product['status']
  page?: number
  size?: number
} = {}): Promise<PageResponse<Product>> {
  return unwrap(await http.get<ApiResponse<PageResponse<Product>>>('/admin/products', { params: query }))
}

export async function getProductImages(productId: number): Promise<ProductImage[]> {
  return unwrap(await http.get<ApiResponse<ProductImage[]>>(`/admin/products/${productId}/images`))
}

export async function uploadProductImage(
  productId: number,
  file: File,
  displayName?: string,
  sortOrder?: number,
): Promise<ProductImage> {
  const body = new FormData()
  body.append('file', file)
  if (displayName?.trim()) body.append('displayName', displayName.trim())
  if (sortOrder !== undefined) body.append('sortOrder', String(sortOrder))
  return unwrap(await http.post<ApiResponse<ProductImage>>(`/admin/products/${productId}/images`, body, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }))
}

export async function updateProductImageSort(
  productId: number,
  imageId: number,
  sortOrder: number,
): Promise<ProductImage> {
  return unwrap(await http.put<ApiResponse<ProductImage>>(
    `/admin/products/${productId}/images/${imageId}/sort`,
    { sortOrder },
  ))
}

export async function deleteProductImage(productId: number, imageId: number): Promise<void> {
  await http.delete<ApiResponse<null>>(`/admin/products/${productId}/images/${imageId}`)
}

export async function createProduct(payload: ProductMutationPayload): Promise<Product> {
  return unwrap(await http.post<ApiResponse<Product>>('/products', payload))
}

export async function updateProduct(productId: number, payload: ProductMutationPayload): Promise<Product> {
  return unwrap(await http.put<ApiResponse<Product>>(`/admin/products/${productId}`, payload))
}

export async function takeProductOffSale(productId: number): Promise<Product> {
  return unwrap(await http.post<ApiResponse<Product>>(`/admin/products/${productId}/off-sale`))
}

export async function putProductOnSale(productId: number): Promise<Product> {
  return unwrap(await http.post<ApiResponse<Product>>(`/admin/products/${productId}/on-sale`))
}

export async function deleteProduct(productId: number): Promise<void> {
  await http.delete<ApiResponse<null>>(`/admin/products/${productId}`)
}

export async function getProductSkus(productId: number): Promise<ProductSku[]> {
  return unwrap(await http.get<ApiResponse<ProductSku[]>>(`/products/${productId}/skus`))
}

export async function getManagedProductSkus(productId: number): Promise<ProductSku[]> {
  return unwrap(await http.get<ApiResponse<ProductSku[]>>(`/admin/products/${productId}/skus`))
}

export async function createProductSku(
  productId: number,
  payload: ProductSkuMutationPayload,
): Promise<ProductSku> {
  return unwrap(await http.post<ApiResponse<ProductSku>>(`/admin/products/${productId}/skus`, payload))
}

export async function updateProductSku(
  productId: number,
  skuId: number,
  payload: ProductSkuMutationPayload,
): Promise<ProductSku> {
  return unwrap(await http.put<ApiResponse<ProductSku>>(`/admin/products/${productId}/skus/${skuId}`, payload))
}

export async function deleteProductSku(productId: number, skuId: number): Promise<void> {
  await http.delete<ApiResponse<null>>(`/admin/products/${productId}/skus/${skuId}`)
}

export async function getCategories(): Promise<Category[]> {
  return unwrap(await http.get<ApiResponse<Category[]>>('/categories'))
}

export async function getManagedCategories(): Promise<Category[]> {
  return unwrap(await http.get<ApiResponse<Category[]>>('/admin/categories'))
}

export async function createCategory(payload: Omit<CategoryMutationPayload, 'status'>): Promise<Category> {
  return unwrap(await http.post<ApiResponse<Category>>('/admin/categories', payload))
}

export async function updateCategory(categoryId: number, payload: CategoryMutationPayload): Promise<Category> {
  return unwrap(await http.put<ApiResponse<Category>>(`/admin/categories/${categoryId}`, payload))
}

export async function deleteCategory(categoryId: number): Promise<void> {
  await http.delete<ApiResponse<null>>(`/admin/categories/${categoryId}`)
}

export async function getActivities(): Promise<FlashActivity[]> {
  return unwrap(await http.get<ApiResponse<FlashActivity[]>>('/activities'))
}

export async function getManagedActivities(): Promise<FlashActivity[]> {
  return unwrap(await http.get<ApiResponse<FlashActivity[]>>('/admin/activities'))
}

export async function createActivity(payload: ActivityMutationPayload): Promise<FlashActivity> {
  return unwrap(await http.post<ApiResponse<FlashActivity>>('/activities', payload))
}

export async function updateActivity(id: number, payload: ActivityMutationPayload): Promise<FlashActivity> {
  return unwrap(await http.put<ApiResponse<FlashActivity>>(`/admin/activities/${id}`, payload))
}

export async function publishActivity(id: number): Promise<FlashActivity> {
  return unwrap(await http.post<ApiResponse<FlashActivity>>(`/activities/${id}/publish`))
}

export async function terminateActivity(id: number, reason: string): Promise<FlashActivity> {
  return unwrap(await http.post<ApiResponse<FlashActivity>>(`/activities/${id}/terminate`, { reason }))
}

export async function getActivity(id: number): Promise<FlashActivity> {
  return unwrap(await http.get<ApiResponse<FlashActivity>>(`/activities/${id}`))
}

export async function reserveActivity(id: number): Promise<Reservation> {
  // 预约是写操作，不设置本地 fallback；后端负责登录、幂等、库存和资格校验。
  return unwrap(await http.post<ApiResponse<Reservation>>(`/activities/${id}/reservations`))
}

export async function getActivityReservation(id: number): Promise<Reservation> {
  return unwrap(await http.get<ApiResponse<Reservation>>(`/activities/${id}/reservation`))
}

export async function runActivityLottery(
  id: number,
  payload: { seed?: number; winnerCount?: number },
): Promise<LotteryDrawResult> {
  return unwrap(await http.post<ApiResponse<LotteryDrawResult>>(`/activities/${id}/lottery`, payload))
}

export async function getActivityReservationRoster(
  id: number,
  status?: ReservationStatus,
): Promise<ReservationRosterItem[]> {
  return unwrap(await http.get<ApiResponse<ReservationRosterItem[]>>(
    `/admin/activities/${id}/reservations`,
    { params: { status } },
  ))
}

export async function exportActivityReservationRoster(
  id: number,
  status?: ReservationStatus,
): Promise<Blob> {
  const response = await http.get<Blob>(`/admin/activities/${id}/reservations/export`, {
    params: { status },
    responseType: 'blob',
  })
  return response.data
}

export async function getOrders(status?: string): Promise<TradeOrder[]> {
  return unwrap(await http.get<ApiResponse<TradeOrder[]>>('/orders', { params: { status } }))
}

export async function exportManagedOrders(filters: {
  activityId?: number
  status?: OrderStatus
  pickupPointId?: number
  verificationStatus?: 'VERIFIED' | 'PENDING'
} = {}): Promise<Blob> {
  const response = await http.get<Blob>('/admin/orders/export', {
    params: filters,
    responseType: 'blob',
  })
  return response.data
}

export async function getOrder(id: number): Promise<OrderDetail> {
  return unwrap(await http.get<ApiResponse<OrderDetail>>(`/orders/${id}`))
}

export async function getProductReviews(productId: number): Promise<ProductReview[]> {
  return unwrap(await http.get<ApiResponse<ProductReview[]>>(`/products/${productId}/reviews`))
}

export async function createProductReview(orderId: number, payload: {
  productId: number
  rating: number
  content: string
  imageUrls?: string[]
}): Promise<ProductReview> {
  return unwrap(await http.post<ApiResponse<ProductReview>>(`/orders/${orderId}/reviews`, payload))
}

export async function uploadReviewImage(file: File): Promise<{ objectName: string; url: string; contentType: string; size: number }> {
  const body = new FormData()
  body.append('file', file)
  return unwrap(await http.post<ApiResponse<{ objectName: string; url: string; contentType: string; size: number }>>(
    '/files/images', body, { headers: { 'Content-Type': 'multipart/form-data' } },
  ))
}

export async function createReport(payload: {
  targetType: ReportTargetType
  targetId: number
  reason: string
}): Promise<ReportRecord> {
  return unwrap(await http.post<ApiResponse<ReportRecord>>('/reports', payload))
}

export async function createOrder(payload: { requestNo: string; productId: number; quantity: number; activityId?: number; skuId?: number }): Promise<TradeOrder> {
  return unwrap(await http.post<ApiResponse<TradeOrder>>('/orders', payload))
}

export async function createFlashSaleRequest(payload: {
  requestNo: string
  productId: number
  quantity: number
  activityId: number
  skuId?: number
}): Promise<FlashSaleRequest> {
  return unwrap(await http.post<ApiResponse<FlashSaleRequest>>('/orders/flash-sale', payload))
}

export async function getFlashSaleRequest(requestNo: string): Promise<FlashSaleRequest> {
  return unwrap(await http.get<ApiResponse<FlashSaleRequest>>(`/orders/flash-sale/${encodeURIComponent(requestNo)}`))
}

export async function getActivityInventory(activityId: number): Promise<InventorySnapshot> {
  return unwrap(await http.get<ApiResponse<InventorySnapshot>>(`/admin/activities/${activityId}/inventory`))
}

export async function getInventoryReconciliations(activityId: number): Promise<InventoryReconciliation[]> {
  return unwrap(await http.get<ApiResponse<InventoryReconciliation[]>>(`/admin/activities/${activityId}/inventory/reconciliations`))
}

export async function reconcileActivityInventory(activityId: number, reason: string): Promise<InventoryReconciliation> {
  return unwrap(await http.post<ApiResponse<InventoryReconciliation>>(
    `/admin/activities/${activityId}/inventory/reconcile`,
    { reason },
  ))
}

export async function getFailedFlashSaleRequests(filters: {
  activityId?: number
  failureCode?: string
} = {}): Promise<FlashSaleRequest[]> {
  return unwrap(await http.get<ApiResponse<FlashSaleRequest[]>>('/admin/flash-sale/failed-requests', {
    params: filters,
  }))
}

export async function getFlashSaleCompensations(requestNo: string): Promise<FlashSaleCompensation[]> {
  return unwrap(await http.get<ApiResponse<FlashSaleCompensation[]>>(
    `/admin/flash-sale/failed-requests/${encodeURIComponent(requestNo)}/compensations`,
  ))
}

export async function retryFailedFlashSaleRequest(
  requestNo: string,
  reason: string,
): Promise<FlashSaleCompensation> {
  return unwrap(await http.post<ApiResponse<FlashSaleCompensation>>(
    `/admin/flash-sale/failed-requests/${encodeURIComponent(requestNo)}/retry`,
    { reason },
  ))
}

export async function payOrder(id: number, idempotencyKey: string): Promise<PaymentRecord> {
  return unwrap(await http.post<ApiResponse<PaymentRecord>>(`/orders/${id}/pay`, { idempotencyKey }))
}

export async function cancelOrder(id: number): Promise<TradeOrder> {
  return unwrap(await http.post<ApiResponse<TradeOrder>>(`/orders/${id}/cancel`))
}

export async function getPickupCode(id: number): Promise<PickupVerification> {
  return unwrap(await http.get<ApiResponse<PickupVerification>>(`/orders/${id}/pickup`))
}

export async function requestRefund(id: number, reason: string): Promise<RefundRecord> {
  return unwrap(await http.post<ApiResponse<RefundRecord>>(`/orders/${id}/refund`, { reason }))
}

export async function approveRefund(id: number, reason: string): Promise<RefundRecord> {
  return unwrap(await http.post<ApiResponse<RefundRecord>>(`/orders/${id}/refund/approve`, { reason }))
}

export async function rejectRefund(id: number, reason: string): Promise<RefundRecord> {
  return unwrap(await http.post<ApiResponse<RefundRecord>>(`/orders/${id}/refund/reject`, { reason }))
}

export async function verifyPickup(pickupCode: string): Promise<TradeOrder> {
  return unwrap(await http.post<ApiResponse<TradeOrder>>('/orders/verification', { pickupCode }))
}

export async function getCaptcha(previousCaptchaId?: string): Promise<CaptchaChallenge> {
  return unwrap(await http.get<ApiResponse<CaptchaChallenge>>('/auth/captcha', {
    params: previousCaptchaId ? { previousCaptchaId } : undefined,
  }))
}

export async function sendRegistrationEmailCode(payload: {
  email: string
  captchaId: string
  captchaCode: string
}): Promise<EmailCodeReceipt> {
  return unwrap(await http.post<ApiResponse<EmailCodeReceipt>>('/auth/email-codes', payload))
}

export async function sendMerchantRegistrationEmailCode(payload: {
  email: string
  captchaId: string
  captchaCode: string
}): Promise<EmailCodeReceipt> {
  return unwrap(await http.post<ApiResponse<EmailCodeReceipt>>('/auth/merchants/email-codes', payload))
}

export async function sendPasswordResetEmailCode(payload: {
  email: string
  captchaId: string
  captchaCode: string
  portalRole: 'STUDENT' | 'MERCHANT' | 'ADMIN'
}): Promise<EmailCodeReceipt> {
  return unwrap(await http.post<ApiResponse<EmailCodeReceipt>>('/auth/password-reset/email-code', payload))
}

export async function resetPassword(payload: {
  email: string
  emailCode: string
  newPassword: string
  portalRole: 'STUDENT' | 'MERCHANT' | 'ADMIN'
}): Promise<void> {
  await http.post<ApiResponse<null>>('/auth/password-reset', payload)
}

export async function logout(): Promise<void> {
  await http.post<ApiResponse<null>>('/auth/logout')
}

export async function login(email: string, password: string, captchaId: string, captchaCode: string, portalRole: 'STUDENT' | 'MERCHANT' | 'ADMIN'): Promise<AuthUser> {
  const result = unwrap(await http.post<ApiResponse<AuthUser>>('/auth/login', { email, password, captchaId, captchaCode, portalRole }))
  return { ...result, role: result.role as UserRole }
}

export async function registerMerchant(payload: {
  email: string
  password: string
  confirmPassword: string
  displayName: string
  merchantName: string
  emailCode: string
  logo?: File
}): Promise<AuthUser> {
  const { logo, ...registration } = payload
  const requestBody: typeof registration | FormData = logo ? new FormData() : registration
  if (logo && requestBody instanceof FormData) {
    requestBody.append('data', new Blob([JSON.stringify(registration)], { type: 'application/json' }))
    requestBody.append('logo', logo)
  }
  const result = unwrap(await http.post<ApiResponse<AuthUser>>('/auth/merchants/register', requestBody))
  return { ...result, role: result.role as UserRole }
}

export async function register(payload: {
  email: string
  password: string
  confirmPassword: string
  nickname: string
  emailCode: string
}): Promise<AuthUser> {
  const result = unwrap(await http.post<ApiResponse<AuthUser>>('/auth/register', payload))
  return { ...result, role: result.role as UserRole }
}

export async function getUserProfile(): Promise<UserProfile> {
  return unwrap(await http.get<ApiResponse<UserProfile>>('/users/me'))
}

export async function updateUserProfile(payload: {
  nickname: string
}): Promise<UserProfile> {
  return unwrap(await http.put<ApiResponse<UserProfile>>('/users/me', payload))
}

export async function getMessages(unreadOnly = false): Promise<UserMessage[]> {
  return unwrap(await http.get<ApiResponse<UserMessage[]>>('/users/me/messages', { params: { unreadOnly } }))
}

export async function getUnreadMessageCount(): Promise<number> {
  const result = unwrap(await http.get<ApiResponse<{ count: number }>>('/users/me/messages/unread-count'))
  return result.count
}

export async function markMessageRead(id: number): Promise<void> {
  await http.post<ApiResponse<null>>(`/users/me/messages/${id}/read`)
}

export async function markAllMessagesRead(): Promise<number> {
  const result = unwrap(await http.post<ApiResponse<{ updated: number }>>('/users/me/messages/read-all'))
  return result.updated
}

export async function getPickupPoints(): Promise<PickupPoint[]> {
  return unwrap(await http.get<ApiResponse<PickupPoint[]>>('/pickup-points'))
}
