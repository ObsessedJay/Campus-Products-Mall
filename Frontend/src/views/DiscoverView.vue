<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, BellRing, CheckCircle2, Clock3, MapPin, Search, TicketCheck } from 'lucide-vue-next'
import EmptyState from '../components/EmptyState.vue'
import ProductCard from '../components/ProductCard.vue'
import RollingCounter from '../components/RollingCounter.vue'
import StatusTag from '../components/StatusTag.vue'
import { getActivities, getProducts, reserveActivity } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { FlashActivity, Product } from '../types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activities = ref<FlashActivity[]>([])
const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
const actionMessage = ref('')
const now = ref(Date.now())
const selectedActivityId = ref<number>()
let ticker: number | undefined

// 首页优先展示正在预约、待开始或进行中的活动，没有时再退回第一场活动。
const defaultFeatured = computed(() => activities.value.find((item) => ['RESERVING', 'PENDING', 'RUNNING'].includes(item.status)) || activities.value[0])
// 悬停或聚焦后保留最后选中的活动，移出列表时主海报不恢复默认内容。
const featured = computed(() => activities.value.find((item) => item.id === selectedActivityId.value) || defaultFeatured.value)
const featuredProduct = computed(() => products.value.find((item) => item.id === featured.value?.productId) || products.value[0])
const countdown = computed(() => {
  if (!featured.value) return '--:--:--'
  // 预约阶段倒计时指向预约结束，否则指向正式开售时间。
  const target = new Date(featured.value.status === 'RESERVING' ? featured.value.reservationEndAt || featured.value.startAt : featured.value.startAt).getTime()
  const diff = Math.max(0, target - now.value)
  const hours = Math.floor(diff / 3_600_000)
  const minutes = Math.floor((diff % 3_600_000) / 60_000)
  const seconds = Math.floor((diff % 60_000) / 1000)
  return [hours, minutes, seconds].map((part) => String(part).padStart(2, '0')).join(':')
})

const formatWeek = (value: string) => new Intl.DateTimeFormat('zh-CN', { weekday: 'short' }).format(new Date(value)).replace('周', '')
const formatDay = (value: string) => new Intl.DateTimeFormat('zh-CN', { day: '2-digit' }).format(new Date(value))
const formatTime = (value: string) => new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 活动和商品互相独立，并行加载可以缩短首页等待时间。
    const [activityData, productData] = await Promise.all([getActivities(), getProducts({ size: 8, sort: 'latest' })])
    activities.value = activityData
    products.value = productData.items
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function reserve() {
  if (!featured.value) return
  if (!auth.user) {
    // 未登录时保留目标活动地址，登录成功后可继续预约流程。
    router.push({ name: 'login', query: { redirect: `/activities/${featured.value.id}` } })
    return
  }
  try {
    const result = await reserveActivity(featured.value.id)
    actionMessage.value = `预约成功，预约编号 ${result.reservationNo}`
  } catch (requestError) {
    actionMessage.value = requestError instanceof Error ? requestError.message : '预约未完成，请稍后重试'
  }
}

onMounted(() => {
  // 倒计时由前端每秒刷新显示，活动状态和资格仍以服务端校验为准。
  load()
  ticker = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => window.clearInterval(ticker))
</script>

<template>
  <div class="discover-page page-wrap">
    <div v-if="route.query.denied" class="inline-alert">当前账号无权进入该工作台，已返回学生端。</div>
    <div v-if="error" class="inline-alert error"><span>{{ error }}</span><button type="button" @click="load">重新加载</button></div>

    <section v-if="loading" class="noticeboard loading-board" aria-label="正在加载">
      <div class="skeleton tall"></div><div class="skeleton hero"></div><div class="skeleton tall"></div>
    </section>

    <section v-else-if="featured" class="noticeboard" aria-label="本周校园文创发行">
      <aside class="week-rail">
        <div class="rail-heading">
          <h1>本周发行</h1>
          <span>{{ activities.length }} 场</span>
        </div>
        <TransitionGroup name="list-post" tag="ol" appear>
          <li
            v-for="(activity, index) in activities.slice(0, 5)"
            :key="activity.id"
            :class="{ current: activity.id === featured.id }"
            :style="{ '--list-index': Math.min(index, 5) }"
            @mouseenter="selectedActivityId = activity.id"
            @focusin="selectedActivityId = activity.id"
          >
            <RouterLink :to="`/activities/${activity.id}`">
              <time :datetime="activity.startAt"><b>{{ formatDay(activity.startAt) }}</b><span>{{ formatWeek(activity.startAt) }}</span></time>
              <span class="rail-copy"><strong>{{ activity.name }}</strong><small>{{ formatTime(activity.startAt) }}</small></span>
              <StatusTag :status="activity.status" compact />
            </RouterLink>
          </li>
        </TransitionGroup>
        <RouterLink class="text-link" to="/catalog">查看全部文创 <ArrowRight :size="17" /></RouterLink>
      </aside>

      <article class="feature-poster">
        <!-- key 变化会触发海报切换动画，使列表与主视觉之间的关系更清晰。 -->
        <Transition name="poster-swap">
          <img v-if="featuredProduct?.coverUrl" :key="featuredProduct.id" :src="featuredProduct.coverUrl" :alt="featuredProduct.name" />
        </Transition>
        <div class="poster-wash"></div>
        <div class="poster-topline">
          <StatusTag :status="featured.status" />
          <span>NO. {{ String(featured.id).padStart(4, '0') }}</span>
        </div>
        <div class="poster-copy">
          <p>{{ featured.mode === 'LOTTERY' ? '预约抽签 · 校内自提' : featured.mode === 'PRE_SALE' ? '校园预售 · 到店领取' : '限时抢购 · 数量有限' }}</p>
          <h2>{{ featured.name }}</h2>
          <div class="poster-facts">
            <span><Clock3 :size="18" />{{ formatTime(featured.startAt) }} 开售</span>
            <span><TicketCheck :size="18" />限购 {{ featured.limitPerUser }} 件</span>
            <span><MapPin :size="18" />校园自提</span>
          </div>
          <RouterLink class="poster-link" :to="`/activities/${featured.id}`">查看发行详情 <ArrowRight :size="20" /></RouterLink>
        </div>
        <div class="poster-stamp">校内<br />限定</div>
      </article>

      <aside class="action-board">
        <div class="countdown-block">
          <span>{{ featured.status === 'RESERVING' ? '预约窗口关闭' : '距离开售' }}</span>
          <strong><RollingCounter :value="countdown" :aria-label="`剩余时间 ${countdown}`" /></strong>
          <small>北京时间</small>
        </div>
        <div class="personal-note">
          <BellRing :size="21" aria-hidden="true" />
          <div><strong>{{ auth.user ? `${auth.user.nickname}，别错过` : '先登录，再拿资格' }}</strong><p>预约、支付倒计时与提货码会集中在“我的凭证”。</p></div>
        </div>
        <button class="primary-action" type="button" :disabled="featured.status === 'ENDED'" @click="reserve">
          {{ featured.status === 'RESERVING' ? '预约这场发行' : featured.status === 'RUNNING' ? '进入购买' : '开售提醒我' }}
          <ArrowRight :size="20" />
        </button>
        <p v-if="actionMessage" class="action-feedback" role="status">{{ actionMessage }}</p>
        <dl class="action-ledger">
          <div><dt>活动库存</dt><dd>{{ featured.stock }}</dd></div>
          <div><dt>支付时限</dt><dd>{{ featured.paymentTimeoutMinutes }} 分钟</dd></div>
          <div><dt>领取方式</dt><dd>校内核销</dd></div>
        </dl>
      </aside>
    </section>

    <EmptyState
      v-else
      title="本周发行正在排期"
      description="当前还没有已发布的校园文创活动，请稍后回来查看。"
    >
      <RouterLink class="primary-action" to="/catalog">先逛文创目录</RouterLink>
    </EmptyState>

    <section v-if="products.length" class="catalog-strip">
      <header class="section-heading">
        <div><h2>刚刚贴上公告栏</h2><p>近期上新的校园文创与活动信息。</p></div>
        <RouterLink class="text-link" to="/catalog">浏览目录 <ArrowRight :size="17" /></RouterLink>
      </header>
      <TransitionGroup name="list-post" tag="div" class="product-grid compact-grid" appear>
        <ProductCard v-for="(product, index) in products.slice(0, 4)" :key="product.id" :product="product" :index="index" :style="{ '--list-index': Math.min(index, 5) }" />
      </TransitionGroup>
    </section>

    <section class="pickup-band">
      <div><CheckCircle2 :size="29" /><h2>线上拿资格，校内凭码领取</h2></div>
      <p>订单支付后生成一次性提货码；运营人员核销后，订单状态立即完成。</p>
      <RouterLink :to="auth.user ? '/orders' : '/login'">查看我的领取凭证 <ArrowRight :size="18" /></RouterLink>
    </section>
  </div>
</template>
