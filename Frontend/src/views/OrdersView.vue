<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Clock3, CreditCard, MapPin, MessageSquareText, QrCode, RotateCcw, Star, X } from 'lucide-vue-next'
import EmptyState from '../components/EmptyState.vue'
import StatusTag from '../components/StatusTag.vue'
import { cancelOrder, createProductReview, getOrder, getOrders, getPickupCode, payOrder, requestRefund, uploadReviewImage } from '../services/api'
import { createFlashSaleRealtime } from '../services/flashSaleRealtime'
import { useAuthStore } from '../stores/auth'
import type { OrderStatus, PickupVerification, TradeOrder, TradeOrderItem } from '../types/api'

const auth = useAuthStore()
const orders = ref<TradeOrder[]>([])
const selectedStatus = ref<OrderStatus | ''>('')
const loading = ref(true)
const error = ref('')
const feedback = ref('')
const busyOrderId = ref<number | null>(null)
const pickupCodes = ref<Record<number, PickupVerification>>({})
const reviewingOrder = ref<TradeOrder | null>(null)
const reviewItems = ref<TradeOrderItem[]>([])
const reviewProductId = ref<number>()
const reviewRating = ref(5)
const reviewContent = ref('')
const reviewFiles = ref<File[]>([])
const reviewLoading = ref(false)
const reviewedOrderIds = ref<number[]>([])
const paymentKeys = new Map<number, string>()
const now = ref(Date.now())
let ticker: number | undefined
const realtime = auth.user?.role === 'STUDENT'
  ? createFlashSaleRealtime(auth.user.token, {
      onEvent: (event) => {
        if (event.type === 'ORDER_STATUS') {
          upsertOrder({
            ...event.order,
            createdAt: event.order.createdAt || event.updatedAt,
            updatedAt: event.updatedAt,
          })
        }
      },
    })
  : undefined
const tabs: { label: string; value: OrderStatus | '' }[] = [
  { label: '全部', value: '' }, { label: '待支付', value: 'WAIT_PAYMENT' }, { label: '待核销', value: 'WAIT_VERIFICATION' }, { label: '已完成', value: 'COMPLETED' }, { label: '退款中', value: 'REFUNDING' }, { label: '已退款', value: 'REFUNDED' },
]
const visibleOrders = computed(() => selectedStatus.value ? orders.value.filter((item) => item.status === selectedStatus.value) : orders.value)
const formatDate = (value?: string) => value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : ''
const deadline = (value?: string) => {
  if (!value) return ''
  const seconds = Math.max(0, Math.floor((new Date(value).getTime() - now.value) / 1000))
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

const orderTime = (order: TradeOrder) => new Date(order.updatedAt || order.createdAt).getTime()

function upsertOrder(incoming: TradeOrder) {
  const current = orders.value.find((order) => order.id === incoming.id)
  const next = current && orderTime(current) > orderTime(incoming) ? current : incoming
  orders.value = [next, ...orders.value.filter((order) => order.id !== incoming.id)]
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
}

function mergeOrders(loadedOrders: TradeOrder[]) {
  const merged = new Map(loadedOrders.map((order) => [order.id, order]))
  for (const current of orders.value) {
    const loaded = merged.get(current.id)
    if (!loaded || orderTime(current) > orderTime(loaded)) merged.set(current.id, current)
  }
  orders.value = Array.from(merged.values())
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
}

async function load() {
  loading.value = true
  error.value = ''
  try { mergeOrders(await getOrders()) }
  catch (requestError) { error.value = requestError instanceof Error ? requestError.message : '订单加载失败' }
  finally { loading.value = false }
}

async function refresh() {
  void realtime?.connect()
  await load()
}

const requestErrorMessage = (requestError: unknown, fallback: string) => requestError instanceof Error ? requestError.message : fallback

async function pay(order: TradeOrder) {
  if (busyOrderId.value !== null) return
  busyOrderId.value = order.id
  feedback.value = ''
  try {
    let idempotencyKey = paymentKeys.get(order.id)
    if (!idempotencyKey) {
      idempotencyKey = `pay-${auth.user?.userId || 'user'}-${order.id}`
      paymentKeys.set(order.id, idempotencyKey)
    }
    await payOrder(order.id, idempotencyKey)
    feedback.value = `订单 ${order.orderNo} 已支付，提货码已生成。`
    await load()
  } catch (requestError) {
    feedback.value = requestErrorMessage(requestError, '支付失败，请稍后重试')
  } finally { busyOrderId.value = null }
}

async function cancel(order: TradeOrder) {
  if (busyOrderId.value !== null) return
  busyOrderId.value = order.id
  feedback.value = ''
  try {
    await cancelOrder(order.id)
    feedback.value = `订单 ${order.orderNo} 已取消，库存已释放。`
    await load()
  } catch (requestError) {
    feedback.value = requestErrorMessage(requestError, '取消失败，请稍后重试')
  } finally { busyOrderId.value = null }
}

async function showPickup(order: TradeOrder) {
  if (busyOrderId.value !== null) return
  busyOrderId.value = order.id
  feedback.value = ''
  try {
    pickupCodes.value[order.id] = await getPickupCode(order.id)
  } catch (requestError) {
    feedback.value = requestErrorMessage(requestError, '提货码查询失败，请稍后重试')
  } finally { busyOrderId.value = null }
}

async function refund(order: TradeOrder) {
  if (busyOrderId.value !== null) return
  const reason = window.prompt('请输入退款原因', '不再需要该商品')?.trim()
  if (!reason) return
  busyOrderId.value = order.id
  feedback.value = ''
  try {
    await requestRefund(order.id, reason)
    feedback.value = `订单 ${order.orderNo} 已提交退款申请，等待运营审核。`
    await load()
  } catch (requestError) {
    feedback.value = requestErrorMessage(requestError, '退款申请失败，请稍后重试')
  } finally { busyOrderId.value = null }
}

async function openReview(order: TradeOrder) {
  if (busyOrderId.value !== null) return
  reviewingOrder.value = order
  reviewLoading.value = true
  reviewContent.value = ''
  reviewFiles.value = []
  reviewRating.value = 5
  try {
    const detail = await getOrder(order.id)
    reviewItems.value = detail.items
    reviewProductId.value = detail.items[0]?.productId
  } catch (requestError) {
    feedback.value = requestErrorMessage(requestError, '订单商品加载失败，请稍后重试')
    reviewingOrder.value = null
  } finally { reviewLoading.value = false }
}

function closeReview() {
  if (reviewLoading.value) return
  reviewingOrder.value = null
}

async function submitReview() {
  if (!reviewingOrder.value || !reviewProductId.value || !reviewContent.value.trim()) return
  reviewLoading.value = true
  try {
    const uploadedImages = []
    for (const file of reviewFiles.value) uploadedImages.push(await uploadReviewImage(file))
    await createProductReview(reviewingOrder.value.id, {
      productId: reviewProductId.value,
      rating: reviewRating.value,
      content: reviewContent.value.trim(),
      imageUrls: uploadedImages.map((image) => image.url),
    })
    reviewedOrderIds.value.push(reviewingOrder.value.id)
    feedback.value = `订单 ${reviewingOrder.value.orderNo} 的评价已发布。`
    reviewingOrder.value = null
  } catch (requestError) {
    feedback.value = requestErrorMessage(requestError, '评价提交失败，请检查是否已经评价过')
  } finally { reviewLoading.value = false }
}

function selectReviewImages(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (files.some((file) => file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type))) {
    feedback.value = '评价图片仅支持 PNG、JPEG、GIF 或 WebP，单张不超过 5MB。'
    input.value = ''
    return
  }
  reviewFiles.value = files.slice(0, 3)
}

onMounted(() => {
  void load()
  void realtime?.connect()
  ticker = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => { window.clearInterval(ticker); realtime?.close() })
</script>

<template>
  <div class="orders-page page-wrap narrow-wrap">
    <header class="orders-header"><div><h1>我的凭证</h1><p>订单、支付倒计时和校园自提码都在这里。</p></div><button class="icon-button" type="button" title="刷新订单" aria-label="刷新订单" @click="refresh"><RotateCcw :size="20" /></button></header>
    <div class="segment-row order-tabs" aria-label="订单状态"><button v-for="tab in tabs" :key="tab.label" type="button" :class="{ active: selectedStatus === tab.value }" @click="selectedStatus = tab.value">{{ tab.label }}</button></div>
    <p v-if="feedback" class="action-feedback" role="status">{{ feedback }}</p>
    <div v-if="error" class="inline-alert error"><span>{{ error }}</span><button type="button" @click="refresh">重试</button></div>
    <div v-if="loading" class="orders-list"><div v-for="n in 3" :key="n" class="skeleton order-skeleton"></div></div>
    <TransitionGroup v-else-if="visibleOrders.length" name="list-post" tag="div" class="orders-list" appear>
      <article v-for="(order, index) in visibleOrders" :key="order.id" class="order-slip" :style="{ '--list-index': Math.min(index, 5) }">
        <div class="order-number"><span>ORDER</span><strong>{{ order.orderNo }}</strong><small>{{ formatDate(order.createdAt) }}</small></div>
        <div class="order-summary"><StatusTag :status="order.status" /><div><span>实付金额</span><strong>¥ {{ Number(order.totalAmount).toFixed(2) }}</strong></div><div class="order-pickup-point"><MapPin :size="17" /><span><small>自提点</small><strong>{{ order.pickupPointName }}</strong><small>{{ order.pickupPointAddress }}</small></span></div><div v-if="order.status === 'WAIT_PAYMENT'" class="deadline"><Clock3 :size="17" /><span>支付剩余</span><strong>{{ deadline(order.paymentDeadline) }}</strong></div><div v-else-if="order.status === 'WAIT_VERIFICATION'" class="deadline confirmed"><MapPin :size="17" /><span>等待到店领取</span></div></div>
        <div class="order-route"><span class="route-node done"><CreditCard :size="17" />下单</span><span class="route-line"></span><span class="route-node" :class="{ done: order.status !== 'WAIT_PAYMENT' }"><CreditCard :size="17" />支付</span><span class="route-line"></span><span class="route-node" :class="{ done: ['COMPLETED'].includes(order.status) }"><QrCode :size="17" />核销</span></div>
        <div class="order-actions">
          <button v-if="order.status === 'WAIT_PAYMENT'" class="primary-action small-action" type="button" :disabled="busyOrderId === order.id" @click="pay(order)"><CreditCard :size="18" />{{ busyOrderId === order.id ? '处理中…' : '立即支付' }}</button>
          <button v-if="order.status === 'WAIT_PAYMENT'" class="secondary-action" type="button" :disabled="busyOrderId === order.id" @click="cancel(order)">取消订单</button>
          <button v-if="order.status === 'WAIT_VERIFICATION' || order.status === 'COMPLETED'" class="primary-action small-action" type="button" :disabled="busyOrderId === order.id" @click="showPickup(order)"><QrCode :size="18" />{{ busyOrderId === order.id ? '查询中…' : '查看提货码' }}</button>
          <button v-if="order.status === 'WAIT_VERIFICATION'" class="secondary-action" type="button" :disabled="busyOrderId === order.id" @click="refund(order)">申请退款</button>
          <button v-if="order.status === 'COMPLETED' && !reviewedOrderIds.includes(order.id)" class="secondary-action" type="button" :disabled="busyOrderId === order.id" @click="openReview(order)"><MessageSquareText :size="17" />评价商品</button>
        </div>
        <div v-if="pickupCodes[order.id]" class="pickup-code" role="status"><QrCode :size="20" /><span>提货码</span><strong>{{ pickupCodes[order.id].pickupCode }}</strong><small>{{ order.pickupPointName }} · {{ order.pickupPointAddress }}</small><small v-if="pickupCodes[order.id].verifiedAt">已于 {{ formatDate(pickupCodes[order.id].verifiedAt) }} 核销</small><small v-else>请在上述自提点出示此码</small></div>
      </article>
    </TransitionGroup>
    <EmptyState v-else title="这里还没有凭证" description="预约或购买成功后，订单会按时间出现在这里。"><RouterLink class="primary-action" to="/">去看本周发行</RouterLink></EmptyState>
    <div v-if="reviewingOrder" class="modal-backdrop" role="presentation" @click.self="closeReview">
      <section class="confirm-modal review-compose" role="dialog" aria-modal="true" aria-labelledby="review-compose-title">
        <header><div><h2 id="review-compose-title">评价本次领取</h2><p>{{ reviewingOrder.orderNo }}</p></div><button class="icon-button" type="button" title="关闭" aria-label="关闭" :disabled="reviewLoading" @click="closeReview"><X :size="18" /></button></header>
        <label v-if="reviewItems.length > 1"><span>评价商品</span><select v-model="reviewProductId"><option v-for="item in reviewItems" :key="item.id" :value="item.productId">{{ item.productName }}{{ item.skuName ? ` · ${item.skuName}` : '' }}</option></select></label>
        <fieldset class="rating-picker"><legend>商品评分</legend><button v-for="score in 5" :key="score" type="button" :class="{ active: score <= reviewRating }" :aria-label="`${score} 星`" @click="reviewRating = score"><Star :size="24" :fill="score <= reviewRating ? 'currentColor' : 'none'" /></button></fieldset>
        <label><span>使用感受</span><textarea v-model="reviewContent" maxlength="1000" rows="5" placeholder="说说做工、包装或领取体验"></textarea><small>{{ reviewContent.length }} / 1000</small></label>
        <label class="review-file-field"><span>评价图片（可选，最多 3 张）</span><input type="file" accept="image/png,image/jpeg,image/gif,image/webp" multiple @change="selectReviewImages" /><small>{{ reviewFiles.length ? reviewFiles.map((file) => file.name).join('、') : '尚未选择图片' }}</small></label>
        <div class="confirm-actions"><button class="secondary-action" type="button" :disabled="reviewLoading" @click="closeReview">取消</button><button class="primary-action" type="button" :disabled="reviewLoading || !reviewContent.trim() || !reviewProductId" @click="submitReview">{{ reviewLoading ? '提交中…' : '发布评价' }}</button></div>
      </section>
    </div>
  </div>
</template>
