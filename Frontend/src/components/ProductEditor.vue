<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { LoaderCircle, PackagePlus, Save, X } from 'lucide-vue-next'
import { createProduct, getManagedProductSkus, updateProduct } from '../services/api'
import type { Category, PickupPoint, Product, ProductMutationPayload, ProductSaleType } from '../types/api'

const props = defineProps<{ product?: Product; categories: Category[]; pickupPoints: PickupPoint[]; disabled?: boolean }>()
const emit = defineEmits<{ cancel: []; saved: [product: Product] }>()

const form = reactive({
  categoryId: '',
  pickupPointId: '',
  name: '',
  subtitle: '',
  description: '',
  saleType: 'NORMAL' as ProductSaleType,
  price: '',
  stock: '0',
  limitPerUser: '1',
})
const submitting = ref(false)
const skuManaged = ref(false)
const error = ref('')
const isEditing = computed(() => Boolean(props.product))

watch(() => props.product, reset, { immediate: true })
watch(() => props.product?.id, async (productId) => {
  skuManaged.value = false
  if (!productId || props.disabled) return
  try { skuManaged.value = (await getManagedProductSkus(productId)).length > 0 }
  catch { skuManaged.value = false }
}, { immediate: true })

function reset() {
  form.categoryId = props.product?.categoryId ? String(props.product.categoryId) : ''
  form.pickupPointId = props.product?.pickupPointId ? String(props.product.pickupPointId) : ''
  form.name = props.product?.name || ''
  form.subtitle = props.product?.subtitle || ''
  form.description = props.product?.description || ''
  form.saleType = props.product?.saleType || 'NORMAL'
  form.price = props.product ? String(props.product.price) : ''
  form.stock = props.product ? String(props.product.stock) : '0'
  form.limitPerUser = props.product ? String(props.product.limitPerUser) : '1'
  error.value = ''
}

async function submit() {
  if (submitting.value || props.disabled) return
  error.value = ''
  const price = Number(form.price)
  const stock = Number(form.stock)
  const limitPerUser = Number(form.limitPerUser)
  if (!form.name.trim()) {
    error.value = '请填写商品名称。'
    return
  }
  if (!form.pickupPointId) {
    error.value = '请选择商品领取点。'
    return
  }
  if (!Number.isFinite(price) || price <= 0) {
    error.value = '商品价格必须大于 0。'
    return
  }
  if (!Number.isInteger(stock) || stock < 0) {
    error.value = '库存必须是大于或等于 0 的整数。'
    return
  }
  if (!Number.isInteger(limitPerUser) || limitPerUser < 1) {
    error.value = '每人限购必须是大于或等于 1 的整数。'
    return
  }
  const payload: ProductMutationPayload = {
    categoryId: form.categoryId ? Number(form.categoryId) : undefined,
    pickupPointId: Number(form.pickupPointId),
    name: form.name.trim(),
    subtitle: form.subtitle.trim() || undefined,
    description: form.description.trim() || undefined,
    saleType: form.saleType,
    price,
    stock,
    limitPerUser,
  }
  submitting.value = true
  try {
    const product = props.product
      ? await updateProduct(props.product.id, payload)
      : await createProduct(payload)
    emit('saved', product)
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '商品保存失败，请检查表单后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="product-editor" aria-labelledby="product-editor-title">
    <header>
      <div><h3 id="product-editor-title">{{ isEditing ? '编辑商品' : '新建商品' }}</h3><p>{{ isEditing ? product?.name : '提交后进入内容审核' }}</p></div>
      <button class="icon-button" type="button" title="关闭商品编辑" aria-label="关闭商品编辑" @click="emit('cancel')"><X :size="18" /></button>
    </header>
    <form @submit.prevent="submit">
      <div class="product-editor-fields">
        <label class="product-editor-name"><span>商品名称</span><input v-model.trim="form.name" maxlength="128" required placeholder="例如：毕业季纪念衫" /></label>
        <label><span>商品分类</span><select v-model="form.categoryId"><option value="">暂不分类</option><option v-for="category in categories.filter((item) => item.status === 'ACTIVE')" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select></label>
        <label><span>领取地点</span><select v-model="form.pickupPointId" required><option value="" disabled>{{ pickupPoints.length ? '选择启用领取点' : '暂无启用领取点' }}</option><option v-for="point in pickupPoints" :key="point.id" :value="String(point.id)">{{ point.name }} · {{ point.campus }}</option></select></label>
        <label><span>发售方式</span><select v-model="form.saleType"><option value="NORMAL">普通发售</option><option value="FLASH_SALE">限时抢购</option><option value="PRE_SALE">预售</option><option value="LOTTERY">抽签发售</option></select></label>
        <label class="product-editor-subtitle"><span>商品副标题</span><input v-model.trim="form.subtitle" maxlength="255" placeholder="一句话说明商品特点" /></label>
        <label><span>价格（元）</span><input v-model="form.price" type="number" min="0.01" step="0.01" inputmode="decimal" required /></label>
        <label><span>{{ skuManaged ? '规格汇总库存' : '可用库存' }}</span><input v-model="form.stock" type="number" min="0" step="1" inputmode="numeric" :disabled="skuManaged" required /></label>
        <label><span>每人限购</span><input v-model="form.limitPerUser" type="number" min="1" step="1" inputmode="numeric" required /></label>
        <label class="product-editor-description"><span>商品描述</span><textarea v-model.trim="form.description" maxlength="2000" rows="5" placeholder="补充材质、尺寸、包装或领取注意事项"></textarea></label>
      </div>
      <p v-if="isEditing" class="product-review-note">{{ skuManaged ? '库存由销售规格汇总；内容变更将重新进入审核。' : '内容变更将重新进入审核；仅调整库存或限购时保留当前状态。' }}</p>
      <p v-if="error" class="inline-alert error" role="alert">{{ error }}</p>
      <div class="product-editor-actions">
        <button class="secondary-action" type="button" :disabled="submitting" @click="emit('cancel')">取消</button>
        <button class="primary-action" type="submit" :disabled="disabled || submitting">
          <LoaderCircle v-if="submitting" class="auth-loading-icon" :size="17" /><Save v-else-if="isEditing" :size="17" /><PackagePlus v-else :size="17" />{{ submitting ? '保存中…' : isEditing ? '保存修改' : '创建商品' }}
        </button>
      </div>
    </form>
  </section>
</template>
