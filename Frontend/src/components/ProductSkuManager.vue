<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Boxes, LoaderCircle, Pencil, Plus, Save, Trash2, X } from 'lucide-vue-next'
import { createProductSku, deleteProductSku, getManagedProductSkus, updateProductSku } from '../services/api'
import type { Product, ProductSku, ProductSkuMutationPayload } from '../types/api'

const props = defineProps<{ product: Product; disabled?: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const skus = ref<ProductSku[]>([])
const loading = ref(false)
const busy = ref(false)
const editingId = ref<number | 'new'>()
const deletingId = ref<number>()
const addButton = ref<HTMLButtonElement>()
const reloadButton = ref<HTMLButtonElement>()
let deleteTrigger: HTMLButtonElement | undefined
const error = ref('')
const feedback = ref('')
const loadFailed = ref(false)
const form = reactive({ skuCode: '', name: '', price: '', stock: '0', enabled: true, sortOrder: '0' })
const totalStock = computed(() => skus.value.filter((sku) => sku.enabled).reduce((sum, sku) => sum + sku.stock, 0))

watch(() => props.product.id, load, { immediate: true })

async function load() {
  loading.value = true
  error.value = ''
  feedback.value = ''
  loadFailed.value = false
  editingId.value = undefined
  deletingId.value = undefined
  try { skus.value = await getManagedProductSkus(props.product.id) }
  catch (requestError) {
    loadFailed.value = true
    error.value = requestError instanceof Error ? requestError.message : '商品规格加载失败'
  }
  finally { loading.value = false }
}

function beginCreate() {
  editingId.value = 'new'
  Object.assign(form, { skuCode: '', name: '', price: String(props.product.price), stock: '0', enabled: true, sortOrder: String(skus.value.length) })
  error.value = ''
}

function beginEdit(sku: ProductSku) {
  editingId.value = sku.id
  Object.assign(form, { skuCode: sku.skuCode, name: sku.name, price: String(sku.price), stock: String(sku.stock), enabled: sku.enabled, sortOrder: String(sku.sortOrder) })
  error.value = ''
}

async function save() {
  if (busy.value || props.disabled || editingId.value === undefined) return
  loadFailed.value = false
  error.value = ''
  const price = Number(form.price)
  const stock = Number(form.stock)
  const sortOrder = Number(form.sortOrder)
  if (!form.skuCode.trim() || !form.name.trim()) return void (error.value = '请填写规格编码和规格名称。')
  if (!Number.isFinite(price) || price < 0) return void (error.value = '规格价格不能小于 0。')
  if (!Number.isInteger(stock) || stock < 0 || !Number.isInteger(sortOrder) || sortOrder < 0) return void (error.value = '库存和顺序必须是非负整数。')
  const payload: ProductSkuMutationPayload = { skuCode: form.skuCode.trim(), name: form.name.trim(), price, stock, enabled: form.enabled, sortOrder }
  busy.value = true
  error.value = ''
  feedback.value = ''
  try {
    if (editingId.value === 'new') await createProductSku(props.product.id, payload)
    else await updateProductSku(props.product.id, editingId.value, payload)
    feedback.value = editingId.value === 'new' ? '规格已新增，商品进入待审核。' : '规格修改已保存。'
    editingId.value = undefined
    emit('changed')
    try {
      skus.value = await getManagedProductSkus(props.product.id)
      loadFailed.value = false
    } catch {
      feedback.value = ''
      loadFailed.value = true
      error.value = '规格已保存，但列表刷新失败，请重新加载。'
      void nextTick(() => reloadButton.value?.focus())
    }
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '规格保存失败'
  }
  finally { busy.value = false }
}

function askDelete(sku: ProductSku, event: MouseEvent) {
  deleteTrigger = event.currentTarget as HTMLButtonElement
  deletingId.value = sku.id
  void nextTick(() => document.getElementById(`sku-delete-confirm-${sku.id}`)?.focus())
}

function cancelDelete() {
  deletingId.value = undefined
  void nextTick(() => deleteTrigger?.focus())
}

async function remove(sku: ProductSku) {
  if (busy.value || props.disabled) return
  busy.value = true
  loadFailed.value = false
  error.value = ''
  feedback.value = ''
  try {
    await deleteProductSku(props.product.id, sku.id)
    deletingId.value = undefined
    feedback.value = '规格已删除，历史订单仍保留规格快照。'
    emit('changed')
    try {
      skus.value = await getManagedProductSkus(props.product.id)
      loadFailed.value = false
      void nextTick(() => addButton.value?.focus())
    } catch {
      feedback.value = ''
      loadFailed.value = true
      error.value = '规格已删除，但列表刷新失败，请重新加载。'
      void nextTick(() => reloadButton.value?.focus())
    }
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '规格删除失败'
    void nextTick(() => deleteTrigger?.focus())
  }
  finally { busy.value = false }
}
</script>

<template>
  <section class="product-sku-manager" aria-labelledby="product-sku-title">
    <header>
      <div><h3 id="product-sku-title">{{ product.name }}</h3><p>销售规格 · {{ skus.length }} 项 · 可售 {{ totalStock }}</p></div>
      <span class="sku-header-actions"><button ref="addButton" class="secondary-action" type="button" :disabled="disabled || busy" @click="beginCreate"><Plus :size="16" />新增</button><button class="icon-button" type="button" title="关闭规格管理" aria-label="关闭规格管理" @click="emit('close')"><X :size="18" /></button></span>
    </header>

    <form v-if="editingId !== undefined" class="sku-editor" @submit.prevent="save">
      <div class="sku-editor-grid">
        <label><span>规格编码</span><input v-model.trim="form.skuCode" maxlength="64" placeholder="NAVY-M" required /></label>
        <label><span>规格名称</span><input v-model.trim="form.name" maxlength="128" placeholder="海军蓝 / M" required /></label>
        <label><span>价格（元）</span><input v-model="form.price" type="number" min="0" step="0.01" required /></label>
        <label><span>库存</span><input v-model="form.stock" type="number" min="0" step="1" required /></label>
        <label><span>展示顺序</span><input v-model="form.sortOrder" type="number" min="0" step="1" required /></label>
        <label class="sku-enabled"><input v-model="form.enabled" type="checkbox" /><span>允许学生选择</span></label>
      </div>
      <div class="sku-editor-actions"><button class="secondary-action" type="button" :disabled="busy" @click="editingId = undefined">取消</button><button class="primary-action" type="submit" :disabled="disabled || busy"><LoaderCircle v-if="busy" class="auth-loading-icon" :size="16" /><Save v-else :size="16" />{{ busy ? '保存中…' : '保存规格' }}</button></div>
    </form>

    <p v-if="error" class="inline-alert error" role="alert">{{ error }} <button v-if="loadFailed" ref="reloadButton" type="button" @click="load">重新加载</button></p>
    <p v-if="feedback" class="action-feedback" role="status">{{ feedback }}</p>
    <div v-if="loading" class="sku-loading"><div class="skeleton"></div><div class="skeleton"></div></div>
    <div v-else-if="loadFailed" class="sku-load-gap" aria-hidden="true"></div>
    <div v-else-if="!skus.length" class="product-image-empty"><Boxes :size="30" /><strong>还没有销售规格</strong><span>新增颜色、尺寸或套装组合后，商品库存将自动汇总。</span></div>
    <div v-else class="sku-list">
      <article v-for="sku in skus" :key="sku.id" class="sku-row" :class="{ disabled: !sku.enabled }">
        <span class="sku-identity"><strong>{{ sku.name }}</strong><small>{{ sku.skuCode }} · 顺序 {{ sku.sortOrder }}</small></span>
        <span class="sku-price"><small>售价</small><strong>¥{{ Number(sku.price).toFixed(2) }}</strong></span>
        <span class="sku-stock"><small>库存</small><strong>{{ sku.stock }}</strong></span>
        <span class="sku-state">{{ sku.enabled ? '启用' : '停用' }}</span>
        <span class="product-row-actions"><button class="icon-button" type="button" title="编辑规格" :aria-label="`编辑规格 ${sku.name}`" :disabled="disabled || busy" @click="beginEdit(sku)"><Pencil :size="16" /></button><button class="icon-button danger" type="button" title="删除规格" :aria-label="`删除规格 ${sku.name}`" :disabled="disabled || busy" @click="askDelete(sku, $event)"><Trash2 :size="16" /></button></span>
        <div v-if="deletingId === sku.id" :id="`sku-delete-confirm-${sku.id}`" class="product-image-delete-confirm" role="alertdialog" :aria-label="`确认删除规格 ${sku.name}`" tabindex="-1" @keydown.esc="cancelDelete"><span>删除后不可恢复，历史订单仍保留名称和编码快照。</span><button class="secondary-action" type="button" @click="cancelDelete">取消</button><button class="primary-action" type="button" :disabled="busy" @click="remove(sku)"><Trash2 :size="15" />确认删除</button></div>
      </article>
    </div>
  </section>
</template>
