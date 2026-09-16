<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Award, ArrowLeft, CalendarClock, Check, CircleX, Hourglass, LoaderCircle, MapPin, Radio, SearchCheck, TicketCheck } from 'lucide-vue-next'
import RollingCounter from '../components/RollingCounter.vue'
import StatusTag from '../components/StatusTag.vue'
import { createFlashSaleRequest, getActivity, getActivityReservation, getFlashSaleRequest, getProduct, getProductSkus, reserveActivity } from '../services/api'
import { ApiError } from '../services/http'
import { createFlashSaleRealtime, type RealtimeConnectionState } from '../services/flashSaleRealtime'
import { useAuthStore } from '../stores/auth'
import type { FlashActivity, FlashSaleRequest, Product, ProductSku, Reservation } from '../types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activity = ref<FlashActivity>()
const product = ref<Product>()
const skus = ref<ProductSku[]>([])
const selectedSkuId = ref<number>()
const reservation = ref<Reservation>()
const loading = ref(true)
const actionBusy = ref(false)
const feedback = ref('')
const feedbackIsError = ref(false)
const orderRequestNo = ref('')
const realtimeState = ref<RealtimeConnectionState>('idle')
const queueStage = ref<'CONNECTING' | 'QUEUED' | 'COMPENSATING' | 'FALLBACK' | ''>('')
const now = ref(Date.now())
let ticker: number | undefined
let lastActivityRefreshAt = 0
let refreshingActivity = false
const realtime = auth.user?.role === 'STUDENT'
  ? createFlashSaleRealtime(auth.user.token, {
      onState: (state) => { realtimeState.value = state },
      onEvent: (event) => {
        if (event.type === 'ACTIVITY_STATUS') {
          if (event.activity.id === Number(route.params.id)) activity.value = event.activity
          return
        }
        if (event.type !== 'FLASH_SALE_STATUS') return
        if (event.requestNo !== orderRequestNo.value) return
        if (event.status === 'COMPENSATING') queueStage.value = 'COMPENSATING'
        else if (event.status === 'PENDING') queueStage.value = 'QUEUED'
      },
    })
  : undefined
const isReservationOpen = computed(() => {
  if (!activity.value || activity.value.status !== 'RESERVING') return false
  const start = activity.value.reservationStartAt ? new Date(activity.value.reservationStartAt).getTime() : 0
  const end = activity.value.reservationEndAt ? new Date(activity.value.reservationEndAt).getTime() : 0
  return (!start || now.value >= start) && Boolean(end && now.value < end)
})
const isSaleOpen = computed(() => {
  if (!activity.value || activity.value.status !== 'RUNNING') return false
  const start = new Date(activity.value.startAt).getTime()
  const end = new Date(activity.value.endAt).getTime()
  return now.value >= start && now.value < end
})
const selectedSku = computed(() => skus.value.find((sku) => sku.id === selectedSkuId.value))
const skuUnavailable = computed(() => skus.value.length > 0 && (!selectedSku.value || selectedSku.value.stock <= 0))
const clockTitle = computed(() => isReservationOpen.value ? '预约关闭倒计时' : isSaleOpen.value ? '发行结束倒计时' : '开售倒计时')
const countLabel = computed(() => {
  if (!activity.value) return '--:--:--'
  const targetValue = isReservationOpen.value
    ? activity.value.reservationEndAt || activity.value.startAt
    : isSaleOpen.value ? activity.value.endAt : activity.value.startAt
  const target = new Date(targetValue).getTime()
  const diff = Math.max(0, target - now.value)
  const h = Math.floor(diff / 3_600_000)
  const m = Math.floor((diff % 3_600_000) / 60_000)
  const s = Math.floor((diff % 60_000) / 1000)
  return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':')
})
const qualification = computed(() => {
  switch (reservation.value?.status) {
    case 'PENDING': return { title: '等待抽签', copy: `预约号 ${reservation.value.reservationNo}`, badge: '待抽签', tone: 'pending', icon: Hourglass }
    case 'QUALIFIED': return { title: '已获得购买资格', copy: reservation.value.drawRank ? `本批次抽签序位 ${reservation.value.drawRank}` : '资格已由服务端确认', badge: '已中签', tone: 'qualified', icon: Award }
    case 'NOT_QUALIFIED': return { title: '本次未中签', copy: reservation.value.drawRank ? `本批次抽签序位 ${reservation.value.drawRank}` : '可继续关注后续发行', badge: '未中签', tone: 'missed', icon: CircleX }
    case 'CANCELLED': return { title: '预约已取消', copy: '本场活动不可使用该预约', badge: '已取消', tone: 'missed', icon: CircleX }
    default: return { title: '尚未预约', copy: isReservationOpen.value ? '当前预约窗口开放' : '当前没有可用资格', badge: '未预约', tone: 'none', icon: TicketCheck }
  }
})
const actionLabel = computed(() => {
  if (!auth.user) return '登录后参与'
  if (isReservationOpen.value) return reservation.value ? '已完成预约' : '预约这场发行'
  if (isSaleOpen.value) {
    if (activity.value?.mode === 'LOTTERY' && reservation.value?.status !== 'QUALIFIED') return '当前没有购买资格'
    if (skuUnavailable.value) return '请选择有库存的规格'
    return actionBusy.value ? '正在排队处理…' : '立即购买'
  }
  if (reservation.value?.status === 'PENDING') return '等待抽签结果'
  if (reservation.value?.status === 'QUALIFIED') return '资格已确认，等待开售'
  if (reservation.value?.status === 'NOT_QUALIFIED') return '本次未中签'
  return '尚未开放'
})
const actionDisabled = computed(() => {
  if (actionBusy.value) return true
  if (!auth.user) return !(isReservationOpen.value || isSaleOpen.value)
  if (isReservationOpen.value) return Boolean(reservation.value)
  if (isSaleOpen.value) return skuUnavailable.value || (activity.value?.mode === 'LOTTERY' && reservation.value?.status !== 'QUALIFIED')
  return true
})
const queueStatus = computed(() => {
  if (queueStage.value === 'CONNECTING') return { title: '正在建立实时通道', copy: '即将提交本次抢购请求', icon: Radio }
  if (queueStage.value === 'COMPENSATING') return { title: '请求正在重新处理', copy: '运营补偿已入队，请保持页面开启', icon: Radio }
  if (queueStage.value === 'FALLBACK') return { title: '正在查询最终结果', copy: '实时通道不可用，已自动切换查询确认', icon: SearchCheck }
  return { title: '请求已进入处理队列', copy: realtimeState.value === 'live' ? '结果将实时返回' : '正在确认处理结果', icon: Radio }
})
const formatTime = (value?: string) => value ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '未设置'

async function load() {
  loading.value = true
  feedback.value = ''
  feedbackIsError.value = false
  lastActivityRefreshAt = Date.now()
  try {
    // 活动详情返回 productId，再补充一次商品详情用于海报和商品名称展示。
    const activityData = await getActivity(Number(route.params.id))
    const [productData, skuData] = await Promise.all([
      getProduct(activityData.productId),
      getProductSkus(activityData.productId),
    ])
    activity.value = activityData
    product.value = productData
    skus.value = skuData
    selectedSkuId.value = skuData.find((sku) => sku.stock > 0)?.id ?? skuData[0]?.id
    if (auth.user?.role === 'STUDENT') {
      try { reservation.value = await getActivityReservation(activityData.id) }
      catch (error) {
        if (!(error instanceof ApiError) || error.code !== 'RESERVATION_NOT_FOUND') throw error
      }
    }
  } catch (requestError) {
    feedbackIsError.value = true
    feedback.value = requestError instanceof Error ? requestError.message : '活动加载失败'
  }
  finally { loading.value = false }
}

async function refreshActivityState() {
  if (refreshingActivity) return
  refreshingActivity = true
  lastActivityRefreshAt = Date.now()
  try { activity.value = await getActivity(Number(route.params.id)) }
  catch { /* Keep the last known activity while the realtime fallback recovers. */ }
  finally { refreshingActivity = false }
}

async function reserve() {
  if (!activity.value) return
  // 登录后回到当前活动页，避免用户丢失刚才查看的活动。
  if (!auth.user) return void router.push({ name: 'login', query: { redirect: route.fullPath } })
  // 预约编号由后端生成，前端不自行生成成功状态。
  actionBusy.value = true
  feedback.value = ''
  feedbackIsError.value = false
  try {
    reservation.value = await reserveActivity(activity.value.id)
    feedback.value = `预约成功，编号 ${reservation.value.reservationNo}`
  } catch (requestError) {
    feedbackIsError.value = true
    feedback.value = requestError instanceof Error ? requestError.message : '预约失败'
  } finally { actionBusy.value = false }
}

async function purchase() {
  if (!activity.value || !product.value || actionBusy.value) return
  if (!orderRequestNo.value) orderRequestNo.value = `act-${activity.value.id}-${crypto.randomUUID()}`
  actionBusy.value = true
  feedback.value = ''
  feedbackIsError.value = false
  queueStage.value = 'CONNECTING'
  try {
    const realtimeConnected = await realtime?.connect() || false
    let request = await createFlashSaleRequest({ requestNo: orderRequestNo.value, productId: product.value.id, quantity: 1, activityId: activity.value.id, skuId: selectedSkuId.value })
    queueStage.value = request.status === 'COMPENSATING' ? 'COMPENSATING' : 'QUEUED'
    if (realtimeConnected && ['ACCEPTING', 'COMPENSATING', 'PENDING'].includes(request.status)) {
      const event = await realtime?.waitForFinal(orderRequestNo.value)
      if (event?.status) request = { ...request, ...event, requestNo: event.requestNo || request.requestNo } as FlashSaleRequest
    }
    if (['ACCEPTING', 'COMPENSATING', 'PENDING'].includes(request.status)) queueStage.value = 'FALLBACK'
    for (let attempt = 0; ['ACCEPTING', 'COMPENSATING', 'PENDING'].includes(request.status) && attempt < 20; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 500))
      request = await getFlashSaleRequest(orderRequestNo.value)
    }
    if (request.status === 'FAILED') throw new Error(request.failureMessage || '抢购请求处理失败，请稍后重试')
    if (['ACCEPTING', 'COMPENSATING', 'PENDING'].includes(request.status)) {
      feedback.value = '请求仍在排队处理中，请稍后再次查看。'
      return
    }
    orderRequestNo.value = ''
    await router.push({ name: 'orders' })
  } catch (requestError) {
    feedbackIsError.value = true
    feedback.value = requestError instanceof Error ? requestError.message : '订单创建失败，请稍后重试'
  } finally { actionBusy.value = false; queueStage.value = '' }
}

function primaryAction() {
  if (!activity.value) return
  if (!auth.user) return void router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (isReservationOpen.value) return void reserve()
  if (isSaleOpen.value) return void purchase()
}

// 计时器只刷新展示；实时通道不可用时，活动状态仍以服务端回查结果为准。
onMounted(() => {
  void load()
  void realtime?.connect()
  ticker = window.setInterval(() => {
    now.value = Date.now()
    if (realtimeState.value !== 'live' && now.value - lastActivityRefreshAt >= 10_000) {
      void refreshActivityState()
    }
  }, 1000)
})
onBeforeUnmount(() => { window.clearInterval(ticker); realtime?.close() })
</script>

<template>
  <div class="activity-page page-wrap narrow-wrap">
    <button class="back-link" type="button" @click="router.back()"><ArrowLeft :size="18" />返回本周发行</button>
    <div v-if="loading" class="skeleton activity-skeleton"></div>
    <p v-else-if="!activity" class="inline-alert error" role="alert">{{ feedback || '活动加载失败' }} <button type="button" @click="load">重试</button></p>
    <article v-else-if="activity" class="activity-ticket">
      <div class="activity-visual">
        <img v-if="product?.coverUrl" :src="product.coverUrl" :alt="product.name" />
        <div class="activity-overlay"></div>
        <StatusTag :status="activity.status" />
        <div><p>校园限定发行</p><h1>{{ activity.name }}</h1><span>{{ product?.name }}</span></div>
      </div>
      <div class="activity-body">
        <div class="activity-clock"><span>{{ clockTitle }}</span><strong><RollingCounter :value="countLabel" :aria-label="`剩余时间 ${countLabel}`" /></strong><small>北京时间</small></div>
        <dl class="activity-facts">
          <div><CalendarClock :size="20" /><dt>预约窗口</dt><dd>{{ formatTime(activity.reservationStartAt) }} - {{ formatTime(activity.reservationEndAt) }}</dd></div>
          <div><TicketCheck :size="20" /><dt>发售时间</dt><dd>{{ formatTime(activity.startAt) }} - {{ formatTime(activity.endAt) }}</dd></div>
          <div><MapPin :size="20" /><dt>领取与限购</dt><dd>校园自提 · 每人限购 {{ activity.limitPerUser }} 件</dd></div>
        </dl>
        <section v-if="activity.mode === 'LOTTERY' && auth.user?.role === 'STUDENT'" class="qualification-strip" :data-tone="qualification.tone">
          <component :is="qualification.icon" :size="24" />
          <span><strong>{{ qualification.title }}</strong><small>{{ qualification.copy }}</small></span>
          <span class="qualification-badge">{{ qualification.badge }}</span>
        </section>
        <fieldset v-if="skus.length" class="product-sku-picker activity-sku-picker">
          <legend>本场可选规格</legend>
          <div><button v-for="sku in skus" :key="sku.id" type="button" :class="{ selected: selectedSkuId === sku.id }" :disabled="sku.stock <= 0 || actionBusy" :aria-pressed="selectedSkuId === sku.id" @click="selectedSkuId = sku.id"><span>{{ sku.name }}</span><small>{{ sku.stock > 0 ? `余 ${sku.stock} · ¥${Number(sku.price).toFixed(2)}` : '已售罄' }}</small></button></div>
        </fieldset>
        <section class="rules-block"><h2>活动规则</h2><p>{{ activity.ruleDescription || '运营人员尚未补充活动规则。' }}</p><ul><li><Check :size="16" />重复预约返回已有结果，不产生重复记录</li><li><Check :size="16" />支付超时后订单关闭并释放库存</li><li><Check :size="16" />最终资格、库存和限购以服务端校验为准</li></ul></section>
        <button class="primary-action wide-action" type="button" :disabled="actionDisabled" @click="primaryAction"><LoaderCircle v-if="actionBusy" class="auth-loading-icon" :size="18" />{{ actionLabel }}</button>
        <div v-if="actionBusy && queueStage" class="queue-progress" :data-mode="queueStage === 'FALLBACK' ? 'fallback' : 'live'" role="status">
          <component :is="queueStatus.icon" :size="19" />
          <span><strong>{{ queueStatus.title }}</strong><small>{{ queueStatus.copy }}</small></span>
          <i aria-hidden="true"></i>
        </div>
        <p v-if="feedback" :class="feedbackIsError ? 'inline-alert error' : 'action-feedback'" :role="feedbackIsError ? 'alert' : 'status'">{{ feedback }}</p>
      </div>
    </article>
  </div>
</template>
