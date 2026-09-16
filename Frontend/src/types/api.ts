export type UserRole = 'STUDENT' | 'MERCHANT' | 'OPERATOR' | 'ADMIN'
// 这些联合类型与后端枚举对应，用于限制状态标签和页面分支的合法值。
export type ProductSaleType = 'NORMAL' | 'FLASH_SALE' | 'PRE_SALE' | 'LOTTERY'
export type ProductStatus = 'DRAFT' | 'PENDING_REVIEW' | 'ON_SALE' | 'OFF_SALE' | 'REJECTED'
export type CategoryStatus = 'ACTIVE' | 'INACTIVE'
export type ActivityMode = 'FLASH_SALE' | 'LOTTERY' | 'PRE_SALE'
export type ActivityStatus = 'UNPUBLISHED' | 'PENDING_REVIEW' | 'REJECTED' | 'RESERVING' | 'PENDING' | 'RUNNING' | 'ENDED' | 'TERMINATED'
export type OrderStatus = 'WAIT_PAYMENT' | 'PAID' | 'WAIT_VERIFICATION' | 'COMPLETED' | 'CANCELLED' | 'REFUNDING' | 'REFUNDED'
export type PaymentStatus = 'SUCCESS' | 'FAILED' | 'REFUNDED'
export type RefundStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type FlashSaleRequestStatus = 'ACCEPTING' | 'COMPENSATING' | 'PENDING' | 'SUCCEEDED' | 'FAILED'
export type ReservationStatus = 'PENDING' | 'QUALIFIED' | 'NOT_QUALIFIED' | 'CANCELLED'

export interface ApiResponse<T> {
  // Spring Boot 所有接口统一使用该响应包装。
  success: boolean
  code: string
  message: string
  data: T
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface AuthUser {
  token: string
  userId: number
  email: string
  nickname: string
  role: UserRole
}

export interface CaptchaChallenge {
  captchaId: string
  imageData: string
  expiresInSeconds: number
}

export interface EmailCodeReceipt {
  expiresInSeconds: number
  retryAfterSeconds: number
}

export interface PickupPoint {
  id: number
  name: string
  campus: string
  address: string
  longitude?: number
  latitude?: number
  openingHours?: string
  contactPhone?: string
  status: 'ACTIVE' | 'INACTIVE'
}

export interface UserProfile {
  id: number
  email?: string
  nickname: string
  school: string
  role: UserRole
}

export interface Product {
  id: number
  categoryId?: number
  pickupPointId: number
  name: string
  subtitle?: string
  description?: string
  coverUrl?: string
  saleType: ProductSaleType
  status: ProductStatus
  price: number
  stock: number
  soldCount: number
  limitPerUser: number
  createdAt: string
  updatedAt?: string
}

export interface ProductSku {
  id: number
  productId: number
  skuCode: string
  name: string
  price: number
  stock: number
  enabled: boolean
  sortOrder: number
  createdAt: string
  updatedAt?: string
}

export interface ProductSkuMutationPayload {
  skuCode: string
  name: string
  price: number
  stock: number
  enabled: boolean
  sortOrder: number
}

export interface ProductMutationPayload {
  categoryId?: number
  pickupPointId: number
  name: string
  subtitle?: string
  description?: string
  saleType: ProductSaleType
  price: number
  stock: number
  limitPerUser: number
}

export interface ProductImage {
  id: number
  productId: number
  objectName: string
  displayName?: string
  url: string
  contentType: string
  sizeBytes: number
  sortOrder: number
  createdBy?: number
  createdAt: string
}

export interface Category {
  id: number
  name: string
  sortOrder: number
  status: CategoryStatus
  createdAt: string
}

export interface CategoryMutationPayload {
  name: string
  sortOrder: number
  status?: CategoryStatus
}

export interface FlashActivity {
  id: number
  name: string
  productId: number
  pickupPointId: number
  mode: ActivityMode
  status: ActivityStatus
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
  reservationStartAt?: string
  reservationEndAt?: string
  startAt: string
  endAt: string
  stock: number
  limitPerUser: number
  paymentTimeoutMinutes: number
  ruleDescription?: string
  terminateReason?: string
  createdAt: string
}

export interface ActivityMutationPayload {
  name: string
  productId: number
  mode: ActivityMode
  reservationStartAt?: string
  reservationEndAt?: string
  startAt: string
  endAt: string
  stock: number
  limitPerUser: number
  paymentTimeoutMinutes: number
  ruleDescription?: string
}

export interface Reservation {
  id: number
  activityId: number
  userId: number
  reservationNo: string
  status: ReservationStatus
  lotteryBatchId?: number
  drawRank?: number
  createdAt: string
}

export interface ReservationRosterItem {
  id: number
  activityId: number
  activityName: string
  userId: number
  email?: string
  nickname?: string
  reservationNo: string
  status: ReservationStatus
  lotteryBatchNo?: string
  drawRank?: number
  createdAt: string
  updatedAt: string
}

export interface LotteryBatch {
  id: number
  activityId: number
  batchNo: string
  randomSeed: number
  totalReservations: number
  winnerCount: number
  drawnBy?: number
  drawnAt: string
}

export interface LotteryDrawResult {
  batch: LotteryBatch
  reservations: Reservation[]
}

export interface TradeOrder {
  id: number
  orderNo: string
  requestNo: string
  userId: number
  activityId?: number
  pickupPointId: number
  pickupPointName: string
  pickupPointAddress: string
  status: OrderStatus
  totalAmount: number
  paymentDeadline?: string
  createdAt: string
  updatedAt?: string
}

export interface FlashSaleRequest {
  id: number
  requestNo: string
  activityId: number
  productId: number
  userId: number
  quantity: number
  status: FlashSaleRequestStatus
  orderId?: number
  failureCode?: string
  failureMessage?: string
  createdAt: string
  updatedAt?: string
}

export interface RealtimeReadyEvent {
  type: 'READY'
  updatedAt: string
}

export interface FlashSaleStatusEvent {
  type: 'FLASH_SALE_STATUS'
  requestNo: string
  status: FlashSaleRequestStatus
  orderId?: number
  failureCode?: string
  failureMessage?: string
  updatedAt: string
}

export interface OrderStatusEvent {
  type: 'ORDER_STATUS'
  order: TradeOrder
  updatedAt: string
}

export interface ActivityStatusEvent {
  type: 'ACTIVITY_STATUS'
  activity: FlashActivity
  updatedAt: string
}

export type StudentRealtimeEvent = RealtimeReadyEvent | FlashSaleStatusEvent | OrderStatusEvent | ActivityStatusEvent

export interface FlashSaleCompensation {
  id: number
  requestId: number
  requestNo: string
  operatedBy: number
  reason: string
  previousFailureCode?: string
  previousFailureMessage?: string
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED'
  failureMessage?: string
  createdAt: string
  completedAt?: string
}

export interface InventorySnapshot {
  activityId: number
  activityName: string
  databaseAvailableStock: number
  redisAvailableStock?: number
  pendingQuantity: number
  paidQuantity: number
  expectedRedisStock: number
  redisAvailable: boolean
  consistent: boolean
  checkedAt: string
}

export interface InventoryReconciliation {
  id: number
  activityId: number
  databaseStock: number
  redisStockBefore?: number
  redisStockAfter: number
  pendingQuantity: number
  differenceBefore?: number
  reason: string
  adjustedBy: number
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED'
  failureMessage?: string
  createdAt: string
}

export interface TradeOrderItem {
  id: number
  orderId: number
  productId: number
  skuId?: number
  productName: string
  skuCode?: string
  skuName?: string
  unitPrice: number
  quantity: number
  lineAmount: number
}

export interface OrderDetail {
  order: TradeOrder
  items: TradeOrderItem[]
  payment?: PaymentRecord
  pickupVerification?: PickupVerification
  refund?: RefundRecord
}

export interface ProductReview {
  id: number
  productId: number
  nickname: string
  rating: number
  content: string
  imageUrls: string[]
  createdAt: string
}

export type ReportTargetType = 'PRODUCT' | 'REVIEW'
export type ReportStatus = 'PENDING' | 'RESOLVED' | 'REJECTED'

export interface ReportRecord {
  id: number
  reporterId: number
  reporterNickname?: string
  targetType: ReportTargetType
  targetId: number
  targetSummary?: string
  reason: string
  status: ReportStatus
  handledBy?: number
  handlerNickname?: string
  handleResult?: string
  handledAt?: string
  createdAt: string
}

export type NotificationType = 'ACTIVITY_REMINDER' | 'LOTTERY_RESULT' | 'PAYMENT_SUCCESS' | 'PICKUP_VERIFIED'

export interface UserMessage {
  id: number
  type: NotificationType
  title: string
  content: string
  link?: string
  readAt?: string
  createdAt: string
}

export interface PaymentRecord {
  id: number
  orderId: number
  paymentNo: string
  idempotencyKey: string
  amount: number
  status: PaymentStatus
  paidAt?: string
  createdAt: string
}

export interface PickupVerification {
  id: number
  orderId: number
  pickupCode: string
  verifiedBy?: number
  verifiedAt?: string
  createdAt: string
}

export interface RefundRecord {
  id: number
  orderId: number
  refundNo: string
  reason: string
  status: RefundStatus
  processedBy?: number
  processedAt?: string
  processReason?: string
  createdAt: string
  updatedAt?: string
}

export interface ProductQuery {
  keyword?: string
  categoryId?: number
  saleType?: ProductSaleType
  minPrice?: number
  maxPrice?: number
  sort?: 'latest' | 'sales' | 'priceAsc' | 'priceDesc'
  page?: number
  size?: number
}
