<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Activity, Boxes, CalendarPlus, CheckCircle2, CircleStop, ClipboardCheck, Database, Dices, Download, History, Images, Layers3, ListChecks, LoaderCircle, PackagePlus, Pencil, Power, PowerOff, QrCode, RefreshCw, RotateCcw, Send, Tags, Trash2, TriangleAlert, Wrench } from 'lucide-vue-next'
import ActivityEditor from '../components/ActivityEditor.vue'
import ProductCategoryManager from '../components/ProductCategoryManager.vue'
import ProductEditor from '../components/ProductEditor.vue'
import ProductImageManager from '../components/ProductImageManager.vue'
import ProductSkuManager from '../components/ProductSkuManager.vue'
import StatusTag from '../components/StatusTag.vue'
import { deleteProduct, exportActivityReservationRoster, exportManagedOrders, getActivityInventory, getActivityReservationRoster, getFailedFlashSaleRequests, getFlashSaleCompensations, getInventoryReconciliations, getManagedActivities, getManagedCategories, getManagedProducts, getPickupPoints, publishActivity, putProductOnSale, reconcileActivityInventory, retryFailedFlashSaleRequest, runActivityLottery, takeProductOffSale, terminateActivity, verifyPickup } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { Category, FlashActivity, FlashSaleCompensation, FlashSaleRequest, InventoryReconciliation, InventorySnapshot, OrderStatus, PickupPoint, Product, ReservationRosterItem, ReservationStatus } from '../types/api'

const auth = useAuthStore()
const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const activities = ref<FlashActivity[]>([])
const pickupPoints = ref<PickupPoint[]>([])
const tab = ref<'overview' | 'products' | 'activities' | 'inventory' | 'compensation' | 'verification'>('overview')
const loading = ref(true)
const verificationCode = ref('')
const verificationBusy = ref(false)
const verificationFeedback = ref('')
const verificationError = ref('')
const orderExportActivityId = ref<number | ''>('')
const orderExportPickupPointId = ref<number | ''>('')
const orderExportStatus = ref<OrderStatus | ''>('')
const orderExportVerificationStatus = ref<'VERIFIED' | 'PENDING' | ''>('')
const orderExporting = ref(false)
const orderExportError = ref('')
const loadError = ref('')
const lotteryOpenId = ref<number | null>(null)
const lotteryBusyId = ref<number | null>(null)
const lotteryDrafts = reactive<Record<number, { winnerCount: string; seed: string }>>({})
const lotteryFeedback = reactive<Record<number, string>>({})
const lotteryErrors = reactive<Record<number, string>>({})
const rosterOpenId = ref<number>()
const rosterItems = ref<ReservationRosterItem[]>([])
const rosterStatus = ref<ReservationStatus | ''>('')
const rosterLoading = ref(false)
const rosterExporting = ref(false)
const rosterError = ref('')
const inventoryActivityId = ref<number>()
const inventorySnapshot = ref<InventorySnapshot>()
const inventoryReconciliations = ref<InventoryReconciliation[]>([])
const inventoryLoading = ref(false)
const inventoryBusy = ref(false)
const inventoryReason = ref('')
const inventoryError = ref('')
const inventoryFeedback = ref('')
const failedRequests = ref<FlashSaleRequest[]>([])
const compensationActivityId = ref<number | ''>('')
const compensationOpenRequest = ref<string>()
const compensationHistory = reactive<Record<string, FlashSaleCompensation[]>>({})
const compensationReasons = reactive<Record<string, string>>({})
const compensationLoading = ref(false)
const compensationHistoryLoading = ref<string>()
const compensationBusy = ref<string>()
const compensationError = ref('')
const compensationFeedback = ref('')
const selectedProductId = ref<number>()
const selectedProductTool = ref<'images' | 'skus'>()
const editingProductId = ref<number>()
const productEditorMode = ref<'create' | 'edit'>()
const categoryManagerOpen = ref(false)
const productActionBusyId = ref<number>()
const productFeedback = ref('')
const activityEditorMode = ref<'create' | 'edit'>()
const editingActivityId = ref<number>()
const activityActionBusyId = ref<number>()
const terminatingActivityId = ref<number>()
const terminationReasons = reactive<Record<number, string>>({})
const activityFeedback = ref('')
const openActivities = computed(() => activities.value.filter((item) => !['ENDED', 'TERMINATED'].includes(item.status)).length)
const onSaleProducts = computed(() => products.value.filter((item) => item.status === 'ON_SALE'))
const lowStock = computed(() => onSaleProducts.value.filter((item) => item.stock < 50).length)
const selectedProduct = computed(() => products.value.find((item) => item.id === selectedProductId.value))
const editingProduct = computed(() => products.value.find((item) => item.id === editingProductId.value))
const editingActivity = computed(() => activities.value.find((item) => item.id === editingActivityId.value))
const activeProductId = computed(() => selectedProductId.value || editingProductId.value)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [productPage, activityData, categoryData, pickupPointData] = await Promise.all([
      getManagedProducts({ size: 100 }),
      getManagedActivities(),
      getManagedCategories(),
      getPickupPoints(),
    ])
    products.value = productPage.items
    activities.value = activityData
    categories.value = categoryData
    pickupPoints.value = pickupPointData
    if (!inventoryActivityId.value && activityData.length) {
      inventoryActivityId.value = activityData.find((item) => item.status === 'RUNNING')?.id || activityData[0].id
    }
    activityData.forEach((item) => {
      if (item.mode === 'LOTTERY' && !lotteryDrafts[item.id]) {
        lotteryDrafts[item.id] = { winnerCount: String(item.stock), seed: '' }
      }
    })
  } catch (requestError) {
    loadError.value = requestError instanceof Error ? requestError.message : '工作台数据加载失败'
  }
  finally { loading.value = false }
}

async function refreshProducts() {
  const productPage = await getManagedProducts({ size: 100 })
  products.value = productPage.items
}

async function refreshActivities() {
  activities.value = await getManagedActivities()
}

function manageImages(product: Product) {
  categoryManagerOpen.value = false
  productEditorMode.value = undefined
  editingProductId.value = undefined
  selectedProductId.value = product.id
  selectedProductTool.value = 'images'
}

function manageSkus(product: Product) {
  categoryManagerOpen.value = false
  productEditorMode.value = undefined
  editingProductId.value = undefined
  selectedProductId.value = product.id
  selectedProductTool.value = 'skus'
}

function createNewProduct() {
  categoryManagerOpen.value = false
  selectedProductId.value = undefined
  selectedProductTool.value = undefined
  editingProductId.value = undefined
  productEditorMode.value = 'create'
}

function editProduct(product: Product) {
  categoryManagerOpen.value = false
  selectedProductId.value = undefined
  selectedProductTool.value = undefined
  editingProductId.value = product.id
  productEditorMode.value = 'edit'
}

function closeProductEditor() {
  productEditorMode.value = undefined
  editingProductId.value = undefined
}

async function handleProductSaved(product: Product) {
  await refreshProducts()
  closeProductEditor()
  selectedProductId.value = product.id
  selectedProductTool.value = 'skus'
}

async function handleImagesChanged() {
  loadError.value = ''
  try {
    await refreshProducts()
  } catch (requestError) {
    loadError.value = requestError instanceof Error ? requestError.message : '商品封面刷新失败'
  }
}

async function handleSkusChanged() {
  loadError.value = ''
  try { await refreshProducts() }
  catch (requestError) { loadError.value = requestError instanceof Error ? requestError.message : '商品库存刷新失败' }
}

function manageCategories() {
  selectedProductId.value = undefined
  selectedProductTool.value = undefined
  editingProductId.value = undefined
  productEditorMode.value = undefined
  categoryManagerOpen.value = true
}

function handleCategoriesChanged(updated: Category[]) {
  categories.value = updated
}

async function changeProductSaleState(product: Product) {
  if (productActionBusyId.value) return
  productActionBusyId.value = product.id
  loadError.value = ''
  productFeedback.value = ''
  try {
    const updated = product.status === 'ON_SALE'
      ? await takeProductOffSale(product.id)
      : await putProductOnSale(product.id)
    products.value = products.value.map((item) => item.id === updated.id ? updated : item)
    productFeedback.value = updated.status === 'ON_SALE' ? `${updated.name} 已重新上架。` : `${updated.name} 已下架。`
  } catch (requestError) {
    loadError.value = requestError instanceof Error ? requestError.message : '商品状态更新失败'
  } finally { productActionBusyId.value = undefined }
}

async function removeProduct(product: Product) {
  if (productActionBusyId.value) return
  if (!window.confirm(`确认删除“${product.name}”？已有订单或活动的商品不会被删除。`)) return
  productActionBusyId.value = product.id
  loadError.value = ''
  productFeedback.value = ''
  try {
    await deleteProduct(product.id)
    products.value = products.value.filter((item) => item.id !== product.id)
    if (activeProductId.value === product.id) {
      selectedProductId.value = undefined
      editingProductId.value = undefined
      productEditorMode.value = undefined
    }
    productFeedback.value = `${product.name} 已删除。`
  } catch (requestError) {
    loadError.value = requestError instanceof Error ? requestError.message : '商品删除失败'
  } finally { productActionBusyId.value = undefined }
}

function createNewActivity() {
  editingActivityId.value = undefined
  activityEditorMode.value = 'create'
  terminatingActivityId.value = undefined
}

function editActivity(activity: FlashActivity) {
  editingActivityId.value = activity.id
  activityEditorMode.value = 'edit'
  terminatingActivityId.value = undefined
}

function closeActivityEditor() {
  activityEditorMode.value = undefined
  editingActivityId.value = undefined
}

async function handleActivitySaved() {
  await refreshActivities()
  closeActivityEditor()
  activityFeedback.value = '活动已保存并进入审核队列。'
}

async function publishManagedActivity(activity: FlashActivity) {
  if (activityActionBusyId.value) return
  activityActionBusyId.value = activity.id
  loadError.value = ''
  activityFeedback.value = ''
  try {
    const updated = await publishActivity(activity.id)
    activities.value = activities.value.map((item) => item.id === updated.id ? updated : item)
    activityFeedback.value = `${updated.name} 已发布。`
  } catch (requestError) {
    loadError.value = requestError instanceof Error ? requestError.message : '活动发布失败'
  } finally { activityActionBusyId.value = undefined }
}

function openTermination(activity: FlashActivity) {
  terminatingActivityId.value = terminatingActivityId.value === activity.id ? undefined : activity.id
  activityEditorMode.value = undefined
  editingActivityId.value = undefined
  if (!terminationReasons[activity.id]) terminationReasons[activity.id] = ''
}

async function terminateManagedActivity(activity: FlashActivity) {
  const reason = terminationReasons[activity.id]?.trim()
  if (!reason || activityActionBusyId.value) return
  activityActionBusyId.value = activity.id
  loadError.value = ''
  activityFeedback.value = ''
  try {
    const updated = await terminateActivity(activity.id, reason)
    activities.value = activities.value.map((item) => item.id === updated.id ? updated : item)
    terminatingActivityId.value = undefined
    activityFeedback.value = `${updated.name} 已终止。`
  } catch (requestError) {
    loadError.value = requestError instanceof Error ? requestError.message : '活动终止失败'
  } finally { activityActionBusyId.value = undefined }
}

async function openInventory() {
  tab.value = 'inventory'
  if (!inventoryActivityId.value && activities.value.length) inventoryActivityId.value = activities.value[0].id
  await loadInventory()
}

async function loadInventory() {
  const activityId = inventoryActivityId.value
  inventoryError.value = ''
  inventoryFeedback.value = ''
  inventorySnapshot.value = undefined
  inventoryReconciliations.value = []
  if (!activityId) return
  inventoryLoading.value = true
  try {
    const [snapshot, reconciliations] = await Promise.all([
      getActivityInventory(activityId),
      getInventoryReconciliations(activityId),
    ])
    inventorySnapshot.value = snapshot
    inventoryReconciliations.value = reconciliations
  } catch (requestError) {
    inventoryError.value = requestError instanceof Error ? requestError.message : '库存状态加载失败'
  } finally { inventoryLoading.value = false }
}

async function reconcileInventory() {
  const reason = inventoryReason.value.trim()
  if (!inventoryActivityId.value || inventoryBusy.value) return
  inventoryError.value = ''
  inventoryFeedback.value = ''
  if (!reason) {
    inventoryError.value = '请填写本次库存校准原因。'
    return
  }
  inventoryBusy.value = true
  try {
    const result = await reconcileActivityInventory(inventoryActivityId.value, reason)
    inventoryFeedback.value = `校准记录 #${result.id} 已完成。`
    inventoryReason.value = ''
    const [snapshot, reconciliations] = await Promise.all([
      getActivityInventory(inventoryActivityId.value),
      getInventoryReconciliations(inventoryActivityId.value),
    ])
    inventorySnapshot.value = snapshot
    inventoryReconciliations.value = reconciliations
  } catch (requestError) {
    inventoryError.value = requestError instanceof Error ? requestError.message : '库存校准失败，请检查 Redis 状态后重试'
  } finally { inventoryBusy.value = false }
}

async function openCompensation() {
  tab.value = 'compensation'
  await loadFailedRequests()
}

async function loadFailedRequests() {
  compensationError.value = ''
  compensationFeedback.value = ''
  compensationLoading.value = true
  try {
    failedRequests.value = await getFailedFlashSaleRequests({
      activityId: compensationActivityId.value || undefined,
    })
  } catch (requestError) {
    compensationError.value = requestError instanceof Error ? requestError.message : '失败请求加载失败'
  } finally { compensationLoading.value = false }
}

async function toggleCompensation(request: FlashSaleRequest) {
  if (compensationOpenRequest.value === request.requestNo) {
    compensationOpenRequest.value = undefined
    return
  }
  compensationOpenRequest.value = request.requestNo
  compensationError.value = ''
  if (compensationHistory[request.requestNo]) return
  compensationHistoryLoading.value = request.requestNo
  try {
    compensationHistory[request.requestNo] = await getFlashSaleCompensations(request.requestNo)
  } catch (requestError) {
    compensationError.value = requestError instanceof Error ? requestError.message : '补偿记录加载失败'
  } finally { compensationHistoryLoading.value = undefined }
}

async function compensate(request: FlashSaleRequest) {
  const reason = compensationReasons[request.requestNo]?.trim()
  compensationError.value = ''
  compensationFeedback.value = ''
  if (!reason) {
    compensationError.value = '请填写本次人工补偿原因。'
    return
  }
  if (compensationBusy.value) return
  compensationBusy.value = request.requestNo
  try {
    const audit = await retryFailedFlashSaleRequest(request.requestNo, reason)
    compensationReasons[request.requestNo] = ''
    compensationOpenRequest.value = undefined
    delete compensationHistory[request.requestNo]
    await loadFailedRequests()
    compensationFeedback.value = `请求 ${request.requestNo} 已重新进入处理队列，补偿记录 #${audit.id} 将跟踪最终结果。`
  } catch (requestError) {
    compensationError.value = requestError instanceof Error ? requestError.message : '人工补偿失败，请核对活动与库存状态'
    try {
      compensationHistory[request.requestNo] = await getFlashSaleCompensations(request.requestNo)
    } catch { /* 主错误已展示。 */ }
  } finally { compensationBusy.value = undefined }
}

const formatAuditTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
}).format(new Date(value))

function toggleLottery(activity: FlashActivity) {
  if (!lotteryDrafts[activity.id]) lotteryDrafts[activity.id] = { winnerCount: String(activity.stock), seed: '' }
  lotteryOpenId.value = lotteryOpenId.value === activity.id ? null : activity.id
  lotteryErrors[activity.id] = ''
}

async function drawLottery(activity: FlashActivity) {
  const draft = lotteryDrafts[activity.id]
  if (!draft || lotteryBusyId.value !== null) return
  const winnerCount = Number(draft.winnerCount)
  const seed = draft.seed.trim() ? Number(draft.seed) : undefined
  lotteryErrors[activity.id] = ''
  lotteryFeedback[activity.id] = ''
  if (!Number.isInteger(winnerCount) || winnerCount < 1 || winnerCount > activity.stock) {
    lotteryErrors[activity.id] = `资格数需为 1 至 ${activity.stock} 的整数。`
    return
  }
  if (seed !== undefined && !Number.isSafeInteger(seed)) {
    lotteryErrors[activity.id] = '固定种子必须是安全整数。'
    return
  }
  lotteryBusyId.value = activity.id
  try {
    const result = await runActivityLottery(activity.id, { winnerCount, seed })
    lotteryFeedback[activity.id] = `${result.batch.batchNo} · ${result.batch.winnerCount}/${result.batch.totalReservations} 人获得资格`
    await load()
  } catch (requestError) {
    lotteryErrors[activity.id] = requestError instanceof Error ? requestError.message : '抽签执行失败，请稍后重试'
  } finally { lotteryBusyId.value = null }
}

async function toggleRoster(activity: FlashActivity) {
  if (rosterOpenId.value === activity.id) {
    rosterOpenId.value = undefined
    return
  }
  rosterOpenId.value = activity.id
  rosterStatus.value = ''
  await loadRoster()
}

async function loadRoster() {
  if (!rosterOpenId.value || rosterLoading.value) return
  rosterLoading.value = true
  rosterError.value = ''
  try {
    rosterItems.value = await getActivityReservationRoster(
      rosterOpenId.value,
      rosterStatus.value || undefined,
    )
  } catch (requestError) {
    rosterItems.value = []
    rosterError.value = requestError instanceof Error ? requestError.message : '预约名单加载失败，请稍后重试'
  } finally {
    rosterLoading.value = false
  }
}

async function downloadRoster(activity: FlashActivity) {
  if (rosterExporting.value) return
  rosterExporting.value = true
  rosterError.value = ''
  try {
    const workbook = await exportActivityReservationRoster(activity.id, rosterStatus.value || undefined)
    const url = URL.createObjectURL(workbook)
    const link = document.createElement('a')
    link.href = url
    link.download = `activity-${activity.id}-reservations.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (requestError) {
    rosterError.value = requestError instanceof Error ? requestError.message : '名单下载失败，请稍后重试'
  } finally {
    rosterExporting.value = false
  }
}

const reservationStatusLabel = (status: ReservationStatus) => ({
  PENDING: '待抽签', QUALIFIED: '获得资格', NOT_QUALIFIED: '未中签', CANCELLED: '已取消',
})[status]

async function verify() {
  const code = verificationCode.value.trim()
  verificationFeedback.value = ''
  verificationError.value = ''
  if (auth.user?.role !== 'MERCHANT' && auth.user?.role !== 'OPERATOR' && auth.user?.role !== 'ADMIN') {
    verificationError.value = '当前账号没有运营核销权限。'
    return
  }
  if (!code || verificationBusy.value) {
    if (!code) verificationError.value = '请输入提货码'
    return
  }
  verificationBusy.value = true
  try {
    const order = await verifyPickup(code)
    verificationFeedback.value = `核销成功：订单 ${order.orderNo} 已完成。`
    verificationCode.value = ''
  } catch (requestError) {
    verificationError.value = requestError instanceof Error ? requestError.message : '核销失败，请检查提货码后重试'
  } finally { verificationBusy.value = false }
}

async function downloadOrderExport() {
  if (orderExporting.value) return
  orderExportError.value = ''
  orderExporting.value = true
  try {
    const workbook = await exportManagedOrders({
      activityId: orderExportActivityId.value || undefined,
      status: orderExportStatus.value || undefined,
      pickupPointId: orderExportPickupPointId.value || undefined,
      verificationStatus: orderExportVerificationStatus.value || undefined,
    })
    const url = URL.createObjectURL(workbook)
    const link = document.createElement('a')
    link.href = url
    link.download = `orders-and-verifications-${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (requestError) {
    orderExportError.value = requestError instanceof Error ? requestError.message : '订单报表生成失败，请稍后重试'
  } finally {
    orderExporting.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="workspace-page page-wrap">
    <header class="workspace-header"><div><h1>运营工作台</h1><p>商品、活动、库存、失败补偿与校园自提核销。</p></div><button class="secondary-action" type="button" @click="load"><RefreshCw :size="17" />刷新状态</button></header>
    <nav class="workspace-tabs" aria-label="运营功能"><button :class="{ active: tab === 'overview' }" @click="tab = 'overview'"><Activity :size="18" />概览</button><button :class="{ active: tab === 'products' }" @click="tab = 'products'"><Boxes :size="18" />商品</button><button :class="{ active: tab === 'activities' }" @click="tab = 'activities'"><ClipboardCheck :size="18" />活动</button><button :class="{ active: tab === 'inventory' }" @click="openInventory"><Database :size="18" />库存</button><button :class="{ active: tab === 'compensation' }" @click="openCompensation"><RotateCcw :size="18" />补偿</button><button :class="{ active: tab === 'verification' }" @click="tab = 'verification'"><QrCode :size="18" />核销</button></nav>
    <p v-if="loadError" class="inline-alert error" role="alert">{{ loadError }} <button type="button" @click="load">重试</button></p>

    <section v-if="tab === 'overview'" class="workspace-overview">
      <div class="workspace-metrics"><div><span>在售商品</span><strong>{{ loading ? '--' : onSaleProducts.length }}</strong><small>运营商品目录</small></div><div><span>进行中活动</span><strong>{{ loading ? '--' : openActivities }}</strong><small>含预约与待开始</small></div><div><span>低库存提醒</span><strong>{{ loading ? '--' : lowStock }}</strong><small>阈值：50 件</small></div></div>
      <div class="ops-columns"><section><h2>当前发行</h2><div class="ops-list"><div v-for="activityItem in activities.slice(0, 5)" :key="activityItem.id"><span><strong>{{ activityItem.name }}</strong><small>库存 {{ activityItem.stock }} · 限购 {{ activityItem.limitPerUser }}</small></span><StatusTag :status="activityItem.status" /></div></div></section><section class="integration-status"><h2>接口接通状态</h2><ul><li class="ready"><PackagePlus :size="18" /><span><strong>商品与分类</strong><small>创建、公开列表、详情已实现</small></span></li><li class="ready"><ClipboardCheck :size="18" /><span><strong>活动与预约</strong><small>创建、发布、终止、预约已实现</small></span></li><li class="ready"><CheckCircle2 :size="18" /><span><strong>库存校准</strong><small>差异检查、缓存重建与调整留痕已实现</small></span></li></ul></section></div>
    </section>

    <section v-else-if="tab === 'products'" class="workspace-table-section product-workspace">
      <header><div><h2>商品目录</h2><p>查看全部状态商品，维护资料、库存与公开封面。</p></div><span class="product-header-actions"><button class="secondary-action" type="button" :aria-pressed="categoryManagerOpen" @click="manageCategories"><Tags :size="18" />分类管理</button><button class="primary-action" type="button" @click="createNewProduct"><PackagePlus :size="18" />新建商品</button></span></header>
      <p v-if="productFeedback" class="action-feedback" role="status">{{ productFeedback }}</p>
      <div class="product-management-layout" :class="{ 'has-selection': selectedProduct || productEditorMode || categoryManagerOpen }">
        <div class="data-table product-management-table">
          <div class="table-row table-head"><span>商品</span><span>方式</span><span>库存</span><span>状态</span><span>操作</span></div>
          <div v-for="product in products" :key="product.id" class="table-row" :class="{ selected: activeProductId === product.id }">
            <span><strong>{{ product.name }}</strong><small>¥ {{ Number(product.price).toFixed(2) }} · {{ pickupPoints.find((point) => point.id === product.pickupPointId)?.name || '待补领取点' }}</small></span>
            <StatusTag :status="product.saleType" compact />
            <span class="tabular">{{ product.stock }}</span>
            <StatusTag :status="product.status" compact />
            <span class="product-row-actions">
              <button class="icon-button" type="button" title="编辑商品" :aria-label="`编辑 ${product.name}`" :aria-pressed="editingProductId === product.id" @click="editProduct(product)"><Pencil :size="16" /></button>
              <button class="icon-button" type="button" title="管理规格" :aria-label="`管理 ${product.name} 的规格`" :aria-pressed="selectedProductId === product.id && selectedProductTool === 'skus'" @click="manageSkus(product)"><Layers3 :size="16" /></button>
              <button class="icon-button" type="button" title="管理图片" :aria-label="`管理 ${product.name} 的图片`" :aria-pressed="selectedProductId === product.id && selectedProductTool === 'images'" @click="manageImages(product)"><Images :size="16" /></button>
              <button v-if="product.status === 'ON_SALE' || product.status === 'OFF_SALE'" class="icon-button" type="button" :title="product.status === 'ON_SALE' ? '下架商品' : '重新上架'" :aria-label="`${product.status === 'ON_SALE' ? '下架' : '重新上架'} ${product.name}`" :disabled="productActionBusyId === product.id" @click="changeProductSaleState(product)"><LoaderCircle v-if="productActionBusyId === product.id" class="auth-loading-icon" :size="16" /><PowerOff v-else-if="product.status === 'ON_SALE'" :size="16" /><Power v-else :size="16" /></button>
              <button class="icon-button danger" type="button" title="删除商品" :aria-label="`删除 ${product.name}`" :disabled="product.status === 'ON_SALE' || productActionBusyId === product.id" @click="removeProduct(product)"><Trash2 :size="16" /></button>
            </span>
          </div>
          <div v-if="!loading && !products.length" class="product-table-empty"><strong>暂无商品</strong><span>新建商品后可在这里维护图库。</span></div>
        </div>
        <ProductEditor v-if="productEditorMode" :product="productEditorMode === 'edit' ? editingProduct : undefined" :categories="categories" :pickup-points="pickupPoints" @cancel="closeProductEditor" @saved="handleProductSaved" />
        <ProductCategoryManager v-else-if="categoryManagerOpen" :categories="categories" @close="categoryManagerOpen = false" @changed="handleCategoriesChanged" />
        <ProductSkuManager v-else-if="selectedProduct && selectedProductTool === 'skus'" :product="selectedProduct" @close="selectedProductId = undefined" @changed="handleSkusChanged" />
        <ProductImageManager v-else-if="selectedProduct" :product="selectedProduct" @close="selectedProductId = undefined" @changed="handleImagesChanged" />
        <aside v-else class="product-image-prompt"><Layers3 :size="30" /><strong>选择商品继续维护</strong><span>编辑资料、销售规格与库存，或上传图片并调整公开封面。</span></aside>
      </div>
    </section>
    <section v-else-if="tab === 'activities'" class="workspace-table-section activity-workspace">
      <header><div><h2>活动排期</h2><p>活动配置、审核状态、发布与终止统一管理。</p></div><button class="primary-action" type="button" @click="createNewActivity"><CalendarPlus :size="18" />创建活动</button></header>
      <p v-if="activityFeedback" class="action-feedback" role="status">{{ activityFeedback }}</p>
      <div class="activity-management-layout">
        <div class="data-table activity-management-table">
          <div class="table-row table-head"><span>活动</span><span>模式</span><span>库存</span><span>状态与操作</span></div>
          <div v-for="activityItem in activities" :key="activityItem.id" class="activity-management-item" :class="{ selected: editingActivityId === activityItem.id }">
            <div class="table-row">
              <span><strong>{{ activityItem.name }}</strong><small>商品 #{{ activityItem.productId }} · {{ pickupPoints.find((point) => point.id === products.find((product) => product.id === activityItem.productId)?.pickupPointId)?.name || '商品领取点' }} · {{ activityItem.reviewStatus === 'APPROVED' ? '审核通过' : activityItem.reviewStatus === 'REJECTED' ? '审核驳回' : '等待审核' }}</small></span>
              <StatusTag :status="activityItem.mode" compact />
              <span class="tabular">{{ activityItem.stock }}</span>
              <span class="activity-row-state">
                <StatusTag :status="activityItem.status" compact />
                <span class="activity-row-actions">
                  <button v-if="['UNPUBLISHED', 'PENDING_REVIEW', 'REJECTED'].includes(activityItem.status)" class="icon-button" type="button" title="编辑活动" :aria-label="`编辑 ${activityItem.name}`" @click="editActivity(activityItem)"><Pencil :size="15" /></button>
                  <button v-if="activityItem.status === 'UNPUBLISHED' && activityItem.reviewStatus === 'APPROVED'" class="icon-button" type="button" title="发布活动" :aria-label="`发布 ${activityItem.name}`" :disabled="activityActionBusyId === activityItem.id" @click="publishManagedActivity(activityItem)"><LoaderCircle v-if="activityActionBusyId === activityItem.id" class="auth-loading-icon" :size="15" /><Send v-else :size="15" /></button>
                  <button v-if="activityItem.mode === 'LOTTERY' && ['RESERVING', 'PENDING'].includes(activityItem.status)" class="icon-button" type="button" title="执行抽签" :aria-label="`执行 ${activityItem.name} 抽签`" @click="toggleLottery(activityItem)"><Dices :size="15" /></button>
                  <button class="icon-button" type="button" title="查看预约名单" :aria-label="`查看 ${activityItem.name} 预约名单`" :aria-pressed="rosterOpenId === activityItem.id" @click="toggleRoster(activityItem)"><ListChecks :size="15" /></button>
                  <button v-if="!['ENDED', 'TERMINATED'].includes(activityItem.status)" class="icon-button danger" type="button" title="终止活动" :aria-label="`终止 ${activityItem.name}`" @click="openTermination(activityItem)"><CircleStop :size="15" /></button>
                </span>
              </span>
            </div>
            <form v-if="terminatingActivityId === activityItem.id" class="activity-termination" @submit.prevent="terminateManagedActivity(activityItem)">
              <label><span>终止原因</span><input v-model.trim="terminationReasons[activityItem.id]" maxlength="500" required placeholder="填写不可逆终止原因" /></label>
              <button class="secondary-action" type="button" @click="terminatingActivityId = undefined">取消</button>
              <button class="primary-action danger-action" type="submit" :disabled="!terminationReasons[activityItem.id]?.trim() || activityActionBusyId === activityItem.id"><LoaderCircle v-if="activityActionBusyId === activityItem.id" class="auth-loading-icon" :size="16" /><CircleStop v-else :size="16" />确认终止</button>
            </form>
          <form v-if="lotteryOpenId === activityItem.id" class="lottery-runner" @submit.prevent="drawLottery(activityItem)">
            <label><span>资格数量</span><input v-model="lotteryDrafts[activityItem.id].winnerCount" type="number" min="1" :max="activityItem.stock" required /></label>
            <label><span>固定种子</span><input v-model.trim="lotteryDrafts[activityItem.id].seed" inputmode="numeric" placeholder="留空则自动生成" /></label>
            <button class="primary-action" type="submit" :disabled="lotteryBusyId !== null"><LoaderCircle v-if="lotteryBusyId === activityItem.id" class="auth-loading-icon" :size="17" /><Dices v-else :size="17" />{{ lotteryBusyId === activityItem.id ? '执行中…' : '执行抽签' }}</button>
            <p v-if="lotteryErrors[activityItem.id]" class="inline-alert error" role="alert">{{ lotteryErrors[activityItem.id] }}</p>
            <p v-if="lotteryFeedback[activityItem.id]" class="action-feedback" role="status">{{ lotteryFeedback[activityItem.id] }}</p>
          </form>
          <section v-if="rosterOpenId === activityItem.id" class="reservation-roster" aria-label="活动预约名单">
            <header>
              <div><strong>预约与资格名单</strong><small>{{ rosterLoading ? '正在读取…' : `${rosterItems.length} 条记录` }}</small></div>
              <span class="reservation-roster-actions">
                <label><span class="sr-only">资格状态</span><select v-model="rosterStatus" :disabled="rosterLoading || rosterExporting" @change="loadRoster"><option value="">全部状态</option><option value="PENDING">待抽签</option><option value="QUALIFIED">获得资格</option><option value="NOT_QUALIFIED">未中签</option><option value="CANCELLED">已取消</option></select></label>
                <button class="secondary-action" type="button" :disabled="rosterLoading || rosterExporting" @click="downloadRoster(activityItem)"><LoaderCircle v-if="rosterExporting" class="auth-loading-icon" :size="16" /><Download v-else :size="16" />{{ rosterExporting ? '生成中…' : '下载 Excel' }}</button>
              </span>
            </header>
            <p v-if="rosterError" class="inline-alert error" role="alert">{{ rosterError }} <button type="button" @click="loadRoster">重试</button></p>
            <div v-if="rosterLoading" class="reservation-roster-loading" aria-label="正在加载预约名单"><div class="skeleton"></div><div class="skeleton"></div></div>
            <div v-else-if="rosterItems.length" class="reservation-roster-scroll">
              <div class="reservation-roster-table">
                <div class="reservation-roster-row reservation-roster-head"><span>学生</span><span>预约编号</span><span>资格</span><span>抽签</span></div>
                <div v-for="item in rosterItems" :key="item.id" class="reservation-roster-row">
                  <span><strong>{{ item.nickname || `学生 #${item.userId}` }}</strong><small>{{ item.email || '未登记邮箱' }}</small></span>
                  <span><strong>{{ item.reservationNo }}</strong><small>{{ formatAuditTime(item.createdAt) }}</small></span>
                  <span class="reservation-result" :data-status="item.status">{{ reservationStatusLabel(item.status) }}</span>
                  <span><strong>{{ item.drawRank ? `第 ${item.drawRank} 名` : '--' }}</strong><small>{{ item.lotteryBatchNo || '尚无抽签批次' }}</small></span>
                </div>
              </div>
            </div>
            <p v-else-if="!rosterError" class="reservation-roster-empty">当前筛选条件下没有预约记录。</p>
          </section>
          </div>
          <div v-if="!loading && !activities.length" class="product-table-empty"><strong>暂无活动</strong><span>创建首个活动后可在这里完成审核、发布和终止。</span></div>
        </div>
        <ActivityEditor v-if="activityEditorMode" :activity="activityEditorMode === 'edit' ? editingActivity : undefined" :products="onSaleProducts" :pickup-points="pickupPoints" @cancel="closeActivityEditor" @saved="handleActivitySaved" />
        <aside v-else class="product-image-prompt"><CalendarPlus :size="30" /><strong>选择活动继续维护</strong><span>配置发售时间、预约阶段、活动库存、限购和支付时限。</span></aside>
      </div>
    </section>
    <section v-else-if="tab === 'inventory'" class="inventory-workspace">
      <header class="inventory-header">
        <div><h2>活动库存账本</h2><p>数据库为业务存量基准，排队请求计入 Redis 预扣。</p></div>
        <label for="inventory-activity"><span>选择活动</span><select id="inventory-activity" v-model.number="inventoryActivityId" :disabled="inventoryLoading || inventoryBusy" @change="loadInventory"><option v-for="activityItem in activities" :key="activityItem.id" :value="activityItem.id">{{ activityItem.name }}</option></select></label>
      </header>
      <div v-if="inventoryLoading" class="inventory-loading" aria-label="正在加载库存状态"><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div></div>
      <p v-else-if="inventoryError && !inventorySnapshot" class="inline-alert error" role="alert">{{ inventoryError }} <button type="button" @click="loadInventory">重试</button></p>
      <template v-else-if="inventorySnapshot">
        <div class="inventory-health" :data-state="inventorySnapshot.consistent ? 'consistent' : 'attention'" role="status">
          <CheckCircle2 v-if="inventorySnapshot.consistent" :size="20" />
          <TriangleAlert v-else :size="20" />
          <span><strong>{{ inventorySnapshot.consistent ? '库存数据一致' : inventorySnapshot.redisAvailable ? '检测到库存差异' : 'Redis 库存未初始化' }}</strong><small>期望 Redis 可用库存 {{ inventorySnapshot.expectedRedisStock }}</small></span>
          <button class="icon-button" type="button" title="刷新库存" :disabled="inventoryLoading || inventoryBusy" @click="loadInventory"><RefreshCw :size="17" /><span class="sr-only">刷新库存</span></button>
        </div>
        <div class="inventory-ledger">
          <div><span>数据库库存</span><strong>{{ inventorySnapshot.databaseAvailableStock }}</strong><small>已落单后的可用量</small></div>
          <div><span>Redis 可用</span><strong>{{ inventorySnapshot.redisAvailableStock ?? '--' }}</strong><small>{{ inventorySnapshot.redisAvailable ? '抢购入口实时值' : '当前没有缓存值' }}</small></div>
          <div><span>排队预扣</span><strong>{{ inventorySnapshot.pendingQuantity }}</strong><small>尚未完成落单</small></div>
          <div><span>已支付</span><strong>{{ inventorySnapshot.paidQuantity }}</strong><small>待核销、已完成及退款中</small></div>
        </div>
        <form class="inventory-reconcile" @submit.prevent="reconcileInventory">
          <div><Wrench :size="24" /><span><strong>重建 Redis 库存</strong><small>按当前订单与排队请求恢复活动库存和用户限购计数。</small></span></div>
          <label><span>校准原因</span><input v-model.trim="inventoryReason" maxlength="500" :disabled="inventoryBusy" placeholder="例如：Redis 重启后恢复活动缓存" /></label>
          <button class="primary-action" type="submit" :disabled="inventoryBusy || !inventoryReason.trim()"><LoaderCircle v-if="inventoryBusy" class="auth-loading-icon" :size="17" /><Wrench v-else :size="17" />{{ inventoryBusy ? '校准中…' : '执行校准' }}</button>
        </form>
        <p v-if="inventoryError" class="inline-alert error" role="alert">{{ inventoryError }}</p>
        <p v-if="inventoryFeedback" class="action-feedback" role="status">{{ inventoryFeedback }}</p>
        <section class="inventory-history">
          <header><h3>最近调整记录</h3><span>{{ inventoryReconciliations.length }} 条</span></header>
          <div v-if="inventoryReconciliations.length" class="inventory-history-table">
            <div class="inventory-history-row inventory-history-head"><span>时间与原因</span><span>调整前</span><span>调整后</span><span>结果</span></div>
            <div v-for="record in inventoryReconciliations" :key="record.id" class="inventory-history-row">
              <span><strong>{{ record.reason }}</strong><small>{{ formatAuditTime(record.createdAt) }} · 操作人 #{{ record.adjustedBy }}</small></span>
              <span class="tabular">{{ record.redisStockBefore ?? '--' }}</span><span class="tabular">{{ record.redisStockAfter }}</span>
              <span class="inventory-audit-status" :data-status="record.status">{{ record.status === 'SUCCEEDED' ? '成功' : record.status === 'FAILED' ? '失败' : '执行中' }}</span>
            </div>
          </div>
          <p v-else class="inventory-empty">当前活动还没有库存调整记录。</p>
        </section>
      </template>
      <p v-else class="inventory-empty">暂无可校准的活动。</p>
    </section>
    <section v-else-if="tab === 'compensation'" class="compensation-workspace">
      <header class="compensation-header">
        <div><h2>失败请求补偿</h2><p>重新校验资格与库存后入队，操作原因和最终结果全程留痕。</p></div>
        <label><span>活动筛选</span><select v-model="compensationActivityId" :disabled="compensationLoading || !!compensationBusy" @change="loadFailedRequests"><option value="">全部活动</option><option v-for="activityItem in activities" :key="activityItem.id" :value="activityItem.id">{{ activityItem.name }}</option></select></label>
      </header>
      <p v-if="compensationError" class="inline-alert error" role="alert">{{ compensationError }} <button v-if="!compensationBusy" type="button" @click="loadFailedRequests">刷新队列</button></p>
      <p v-if="compensationFeedback" class="action-feedback" role="status">{{ compensationFeedback }}</p>
      <div v-if="compensationLoading" class="compensation-loading" aria-label="正在加载失败请求"><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div></div>
      <div v-else-if="failedRequests.length" class="compensation-ledger">
        <div class="compensation-row compensation-head"><span>请求与活动</span><span>用户 / 数量</span><span>失败原因</span><span>操作</span></div>
        <article v-for="request in failedRequests" :key="request.requestNo" class="compensation-item">
          <div class="compensation-row">
            <span><strong>{{ request.requestNo }}</strong><small>活动 #{{ request.activityId }} · 商品 #{{ request.productId }} · {{ formatAuditTime(request.updatedAt || request.createdAt) }}</small></span>
            <span><strong>#{{ request.userId }}</strong><small>{{ request.quantity }} 件</small></span>
            <span><strong>{{ request.failureCode || 'UNKNOWN' }}</strong><small>{{ request.failureMessage || '未记录失败详情' }}</small></span>
            <button class="table-command" type="button" :disabled="!!compensationBusy" @click="toggleCompensation(request)"><History :size="15" />{{ compensationOpenRequest === request.requestNo ? '收起' : '处理' }}</button>
          </div>
          <div v-if="compensationOpenRequest === request.requestNo" class="compensation-detail">
            <section>
              <h3>补偿记录</h3>
              <p v-if="compensationHistoryLoading === request.requestNo" class="compensation-history-empty">正在加载记录…</p>
              <div v-else-if="compensationHistory[request.requestNo]?.length" class="compensation-history">
                <div v-for="record in compensationHistory[request.requestNo]" :key="record.id"><span><strong>{{ record.reason }}</strong><small>{{ formatAuditTime(record.createdAt) }} · 操作人 #{{ record.operatedBy }}</small></span><span class="inventory-audit-status" :data-status="record.status">{{ record.status === 'SUCCEEDED' ? '成功' : record.status === 'FAILED' ? '失败' : '处理中' }}</span><small v-if="record.failureMessage">{{ record.failureMessage }}</small></div>
              </div>
              <p v-else class="compensation-history-empty">此前没有人工补偿记录。</p>
            </section>
            <form @submit.prevent="compensate(request)">
              <label><span>补偿原因</span><textarea v-model.trim="compensationReasons[request.requestNo]" maxlength="500" :disabled="!!compensationBusy" placeholder="例如：消息服务恢复，已核对该请求未生成订单"></textarea></label>
              <button class="primary-action" type="submit" :disabled="!!compensationBusy || !compensationReasons[request.requestNo]?.trim()"><LoaderCircle v-if="compensationBusy === request.requestNo" class="auth-loading-icon" :size="17" /><RotateCcw v-else :size="17" />{{ compensationBusy === request.requestNo ? '重新入队中…' : '确认补偿' }}</button>
            </form>
          </div>
        </article>
      </div>
      <p v-else class="compensation-empty">当前筛选范围内没有待补偿的失败请求。</p>
    </section>
    <template v-else>
      <section class="verification-station"><div><QrCode :size="42" /><h2>自提核销台</h2><p>输入一次性提货码，服务端会校验订单归属、状态和是否重复核销。</p><label><span>提货码</span><input v-model="verificationCode" inputmode="numeric" autocomplete="off" placeholder="例如 482913" @keyup.enter="verify" /></label><p v-if="verificationError" class="inline-alert error" role="alert">{{ verificationError }}</p><p v-if="verificationFeedback" class="action-feedback" role="status">{{ verificationFeedback }}</p><button class="primary-action wide-action" type="button" :disabled="verificationBusy || !verificationCode.trim()" @click="verify"><QrCode :size="18" />{{ verificationBusy ? '核销中…' : '查询并核销' }}</button></div><aside><h3>核销边界</h3><ul><li>只能核销授权自提点订单</li><li>重复核销会被服务端拒绝</li><li>退款中订单不可核销</li><li>成功后订单不可逆转为待核销</li></ul></aside></section>
      <section class="order-export-panel" aria-labelledby="order-export-title">
        <header><div><h2 id="order-export-title">订单与核销报表</h2><p>按活动、订单状态、自提点和核销结果生成运营 XLSX。</p></div><Download :size="28" /></header>
        <form @submit.prevent="downloadOrderExport">
          <label><span>活动</span><select v-model="orderExportActivityId" :disabled="orderExporting"><option value="">全部活动</option><option v-for="activityItem in activities" :key="activityItem.id" :value="activityItem.id">{{ activityItem.name }}</option></select></label>
          <label><span>订单状态</span><select v-model="orderExportStatus" :disabled="orderExporting"><option value="">全部状态</option><option value="WAIT_PAYMENT">待支付</option><option value="WAIT_VERIFICATION">待核销</option><option value="COMPLETED">已完成</option><option value="CANCELLED">已取消</option><option value="REFUNDING">退款中</option><option value="REFUNDED">已退款</option></select></label>
          <label><span>自提点</span><select v-model="orderExportPickupPointId" :disabled="orderExporting"><option value="">全部自提点</option><option v-for="point in pickupPoints" :key="point.id" :value="point.id">{{ point.name }}</option></select></label>
          <label><span>核销结果</span><select v-model="orderExportVerificationStatus" :disabled="orderExporting"><option value="">全部结果</option><option value="VERIFIED">已核销</option><option value="PENDING">未核销</option></select></label>
          <button class="primary-action" type="submit" :disabled="orderExporting"><LoaderCircle v-if="orderExporting" class="auth-loading-icon" :size="17" /><Download v-else :size="17" />{{ orderExporting ? '正在生成…' : '下载订单报表' }}</button>
        </form>
        <p v-if="orderExportError" class="inline-alert error" role="alert">{{ orderExportError }} <button v-if="!orderExporting" type="button" @click="downloadOrderExport">重试</button></p>
      </section>
    </template>
  </div>
</template>
