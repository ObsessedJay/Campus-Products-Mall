<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Flag, Heart, ImageOff, MapPin, MessageSquareText, Minus, Plus, ShieldCheck, ShoppingBag, Star } from 'lucide-vue-next'
import StatusTag from '../components/StatusTag.vue'
import { createOrder, createReport, getProduct, getProductReviews, getProductSkus } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { Product, ProductReview, ProductSku, ReportTargetType } from '../types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const product = ref<Product>()
const skus = ref<ProductSku[]>([])
const selectedSkuId = ref<number>()
const quantity = ref(1)
const loading = ref(true)
const error = ref('')
const feedback = ref('')
const submitting = ref(false)
const imageFailed = ref(false)
const reviews = ref<ProductReview[]>([])
const reviewsLoading = ref(true)
const reviewsError = ref('')
const selectedSku = computed(() => skus.value.find((sku) => sku.id === selectedSkuId.value))
const currentPrice = computed(() => Number(selectedSku.value?.price ?? product.value?.price ?? 0))
const availableStock = computed(() => selectedSku.value?.stock ?? product.value?.stock ?? 0)
const maxQuantity = computed(() => Math.max(1, Math.min(product.value?.limitPerUser ?? 1, availableStock.value)))
const total = computed(() => currentPrice.value * quantity.value)

async function load() {
  loading.value = true
  // 路由参数是字符串，调用 API 前转换为后端需要的数字 ID。
  try {
    const id = Number(route.params.id)
    const [productData, skuData] = await Promise.all([getProduct(id), getProductSkus(id)])
    product.value = productData
    skus.value = skuData
    selectedSkuId.value = skuData.find((sku) => sku.stock > 0)?.id ?? skuData[0]?.id
  }
  catch (requestError) { error.value = requestError instanceof Error ? requestError.message : '商品加载失败' }
  finally { loading.value = false }
  await loadReviews()
}

async function loadReviews() {
  reviewsLoading.value = true
  reviewsError.value = ''
  try { reviews.value = await getProductReviews(Number(route.params.id)) }
  catch (requestError) { reviewsError.value = requestError instanceof Error ? requestError.message : '评价加载失败' }
  finally { reviewsLoading.value = false }
}

async function reportTarget(targetType: ReportTargetType, targetId: number) {
  if (!auth.user) return void router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (auth.user.role !== 'STUDENT') {
    feedback.value = '仅学生账号可以提交举报。'
    return
  }
  const reason = window.prompt('请说明举报原因')?.trim()
  if (!reason) return
  try {
    await createReport({ targetType, targetId, reason })
    feedback.value = '举报已提交，管理员会在治理工作台处理。'
  } catch (requestError) {
    feedback.value = requestError instanceof Error ? requestError.message : '举报提交失败'
  }
}

function chooseQuantity(delta: number) {
  if (!product.value) return
  // 前端先限制数量改善体验，后端创建订单时仍必须再次校验库存和限购。
  quantity.value = Math.min(maxQuantity.value, Math.max(1, quantity.value + delta))
}

function chooseSku(sku: ProductSku) {
  selectedSkuId.value = sku.id
  quantity.value = Math.min(quantity.value, Math.max(1, sku.stock))
  feedback.value = ''
}

async function startOrder() {
  if (!auth.user) return void router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (auth.user.role !== 'STUDENT') {
    feedback.value = '当前账号没有学生购买权限，请切换学生账号。'
    return
  }
  if (!product.value || submitting.value) return
  submitting.value = true
  feedback.value = ''
  try {
    const requestNo = `web-${auth.user.userId}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const order = await createOrder({ requestNo, productId: product.value.id, quantity: quantity.value, skuId: selectedSkuId.value })
    await router.push({ name: 'orders', query: { orderId: String(order.id) } })
  } catch (requestError) {
    feedback.value = requestError instanceof Error ? requestError.message : '订单创建失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="detail-page page-wrap narrow-wrap">
    <button class="back-link" type="button" @click="router.back()"><ArrowLeft :size="18" />返回</button>
    <div v-if="loading" class="detail-layout"><div class="skeleton detail-media"></div><div class="skeleton detail-copy"></div></div>
    <div v-else-if="error" class="inline-alert error"><span>{{ error }}</span><button type="button" @click="load">重试</button></div>
    <article v-else-if="product" class="detail-layout">
      <div class="detail-media product-detail-media">
        <img v-if="product.coverUrl && !imageFailed" :src="product.coverUrl" :alt="product.name" @error="imageFailed = true" />
        <div v-else class="media-fallback"><ImageOff :size="42" /><span>商品图片待补充</span></div>
        <span class="detail-number">PRODUCT / {{ String(product.id).padStart(4, '0') }}</span>
      </div>
      <div class="detail-copy">
        <div class="detail-tags"><StatusTag :status="product.saleType" /><StatusTag :status="product.status" /></div>
        <h1>{{ product.name }}</h1>
        <p class="detail-subtitle">{{ product.subtitle }}</p>
        <p class="detail-description">{{ product.description || '商品详情由运营人员补充。' }}</p>
        <dl class="product-ledger">
          <div><dt>{{ selectedSku ? '规格售价' : '发售价' }}</dt><dd>¥ {{ currentPrice.toFixed(2) }}</dd></div>
          <div><dt>可售库存</dt><dd>{{ availableStock }}</dd></div>
          <div><dt>每人限购</dt><dd>{{ product.limitPerUser }} 件</dd></div>
          <div><dt>已售</dt><dd>{{ product.soldCount }}</dd></div>
        </dl>
        <fieldset v-if="skus.length" class="product-sku-picker">
          <legend>选择规格</legend>
          <div>
            <button v-for="sku in skus" :key="sku.id" type="button" :class="{ selected: selectedSkuId === sku.id }" :disabled="sku.stock <= 0" :aria-pressed="selectedSkuId === sku.id" @click="chooseSku(sku)">
              <span>{{ sku.name }}</span><small>{{ sku.stock > 0 ? `余 ${sku.stock} · ¥${Number(sku.price).toFixed(2)}` : '已售罄' }}</small>
            </button>
          </div>
        </fieldset>
        <div class="pickup-rule"><MapPin :size="22" /><div><strong>校园自提</strong><p>支付后生成一次性提货码，请在订单指定自提点核销领取。</p></div></div>
        <div class="purchase-row">
          <div class="stepper" aria-label="购买数量"><button type="button" aria-label="减少数量" :disabled="quantity <= 1" @click="chooseQuantity(-1)"><Minus :size="17" /></button><output>{{ quantity }}</output><button type="button" aria-label="增加数量" :disabled="quantity >= maxQuantity" @click="chooseQuantity(1)"><Plus :size="17" /></button></div>
          <!-- 外层负责显示提示，按钮内部只裁切文字和购物袋的上下切换轨道。 -->
          <div class="purchase-button-wrap">
            <button
              class="primary-action purchase-button"
              type="button"
              :disabled="availableStock <= 0 || submitting || (skus.length > 0 && !selectedSku)"
              aria-describedby="purchase-button-tooltip"
              @click="startOrder"
            >
              <span class="purchase-button__track">
                <span class="purchase-button__text">{{ submitting ? '提交中…' : availableStock > 0 ? '确认购买' : '暂时售罄' }}</span>
                <span class="purchase-button__icon" aria-hidden="true"><ShoppingBag :size="20" /></span>
              </span>
            </button>
            <span id="purchase-button-tooltip" class="purchase-button__tooltip" role="tooltip">
              {{ availableStock > 0 ? `合计 ¥${total.toFixed(2)}` : '库存不足' }}
            </span>
          </div>
          <button class="icon-button favorite-button" type="button" title="收藏商品" aria-label="收藏商品"><Heart :size="20" /></button>
        </div>
        <p v-if="feedback" class="action-feedback" role="status">{{ feedback }}</p>
        <p class="security-note"><ShieldCheck :size="17" />库存、限购与订单归属以服务端校验结果为准。</p>
      </div>
    </article>
    <section v-if="product" class="product-reviews" :aria-busy="reviewsLoading">
      <header><div><h2>同学们的领取反馈</h2><p>仅展示已完成订单发布且当前可见的评价。</p></div><button class="secondary-action" type="button" @click="reportTarget('PRODUCT', product.id)"><Flag :size="16" />举报商品</button></header>
      <div v-if="reviewsLoading" class="review-feed"><div v-for="n in 2" :key="n" class="skeleton review-feed-skeleton"></div></div>
      <div v-else-if="reviewsError" class="inline-alert error"><span>{{ reviewsError }}</span><button type="button" @click="loadReviews">重试</button></div>
      <div v-else-if="reviews.length" class="review-feed">
        <article v-for="review in reviews" :key="review.id" class="student-review">
          <div class="student-review-head"><strong>{{ review.nickname }}</strong><span class="review-stars" :aria-label="`${review.rating} 星评价`"><Star v-for="score in 5" :key="score" :size="16" :fill="score <= review.rating ? 'currentColor' : 'none'" /></span><time>{{ new Date(review.createdAt).toLocaleDateString('zh-CN') }}</time></div>
          <p>{{ review.content }}</p>
          <div v-if="review.imageUrls.length" class="review-images"><img v-for="url in review.imageUrls" :key="url" :src="url" alt="评价图片" /></div>
          <button class="review-report" type="button" @click="reportTarget('REVIEW', review.id)"><Flag :size="14" />举报此评价</button>
        </article>
      </div>
      <div v-else class="review-empty"><MessageSquareText :size="22" /><span>还没有同学发布评价</span></div>
    </section>
  </div>
</template>
