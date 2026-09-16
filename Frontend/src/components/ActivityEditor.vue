<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { CalendarPlus, LoaderCircle, Save, X } from 'lucide-vue-next'
import { createActivity, updateActivity } from '../services/api'
import type { ActivityMode, ActivityMutationPayload, FlashActivity, PickupPoint, Product } from '../types/api'

const props = defineProps<{ activity?: FlashActivity; products: Product[]; pickupPoints: PickupPoint[]; disabled?: boolean }>()
const emit = defineEmits<{ cancel: []; saved: [activity: FlashActivity] }>()

const form = reactive({
  name: '', productId: '', mode: 'FLASH_SALE' as ActivityMode,
  hasReservation: false, reservationStartAt: '', reservationEndAt: '',
  startAt: '', endAt: '', stock: '1', limitPerUser: '1', paymentTimeoutMinutes: '15',
  ruleDescription: '',
})
const submitting = ref(false)
const error = ref('')
const isEditing = computed(() => Boolean(props.activity))
const selectedProduct = computed(() => props.products.find((product) => String(product.id) === form.productId))
const selectedPickupPoint = computed(() => props.pickupPoints.find((point) => point.id === selectedProduct.value?.pickupPointId))

watch(() => props.activity, reset, { immediate: true })
watch(() => form.mode, (mode) => {
  if (mode === 'LOTTERY') form.hasReservation = true
})

function localInput(value?: string) {
  return value ? value.slice(0, 16) : ''
}

function reset() {
  const activity = props.activity
  Object.assign(form, {
    name: activity?.name || '',
    productId: activity ? String(activity.productId) : '',
    mode: activity?.mode || 'FLASH_SALE',
    hasReservation: Boolean(activity?.reservationStartAt || activity?.reservationEndAt),
    reservationStartAt: localInput(activity?.reservationStartAt),
    reservationEndAt: localInput(activity?.reservationEndAt),
    startAt: localInput(activity?.startAt),
    endAt: localInput(activity?.endAt),
    stock: activity ? String(activity.stock) : '1',
    limitPerUser: activity ? String(activity.limitPerUser) : '1',
    paymentTimeoutMinutes: activity ? String(activity.paymentTimeoutMinutes) : '15',
    ruleDescription: activity?.ruleDescription || '',
  })
  error.value = ''
}

async function submit() {
  if (submitting.value || props.disabled) return
  error.value = ''
  const stock = Number(form.stock)
  const limitPerUser = Number(form.limitPerUser)
  const paymentTimeoutMinutes = Number(form.paymentTimeoutMinutes)
  const start = new Date(form.startAt)
  const end = new Date(form.endAt)
  if (!form.name.trim() || !form.productId) {
    error.value = '请填写活动名称并选择商品。'
    return
  }
  if (!form.startAt || !form.endAt || start >= end) {
    error.value = '活动开始时间必须早于结束时间。'
    return
  }
  if (form.hasReservation) {
    const reservationStart = new Date(form.reservationStartAt)
    const reservationEnd = new Date(form.reservationEndAt)
    if (!form.reservationStartAt || !form.reservationEndAt || reservationStart >= reservationEnd || reservationEnd >= start) {
      error.value = '预约时间需完整填写，并在活动开始前结束。'
      return
    }
  }
  if (![stock, limitPerUser, paymentTimeoutMinutes].every(Number.isInteger)
      || stock < 1 || limitPerUser < 1 || paymentTimeoutMinutes < 1) {
    error.value = '库存、限购和支付时限必须是大于 0 的整数。'
    return
  }
  const payload: ActivityMutationPayload = {
    name: form.name.trim(), productId: Number(form.productId), mode: form.mode,
    reservationStartAt: form.hasReservation ? form.reservationStartAt : undefined,
    reservationEndAt: form.hasReservation ? form.reservationEndAt : undefined,
    startAt: form.startAt, endAt: form.endAt, stock, limitPerUser, paymentTimeoutMinutes,
    ruleDescription: form.ruleDescription.trim() || undefined,
  }
  submitting.value = true
  try {
    const activity = props.activity
      ? await updateActivity(props.activity.id, payload)
      : await createActivity(payload)
    emit('saved', activity)
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '活动保存失败，请检查配置后重试'
  } finally { submitting.value = false }
}
</script>

<template>
  <section class="activity-editor" aria-labelledby="activity-editor-title">
    <header>
      <div><h3 id="activity-editor-title">{{ isEditing ? '编辑活动' : '创建活动' }}</h3><p>保存后进入管理员审核</p></div>
      <button class="icon-button" type="button" title="关闭活动编辑" aria-label="关闭活动编辑" @click="emit('cancel')"><X :size="18" /></button>
    </header>
    <form @submit.prevent="submit">
      <div class="activity-editor-fields">
        <label class="activity-editor-name"><span>活动名称</span><input v-model.trim="form.name" maxlength="128" required /></label>
        <label><span>活动商品</span><select v-model="form.productId" required><option value="" disabled>选择在售商品</option><option v-for="product in products" :key="product.id" :value="String(product.id)">{{ product.name }} · 库存 {{ product.stock }}</option></select></label>
        <label><span>领取地点</span><input :value="selectedPickupPoint ? `${selectedPickupPoint.name} · ${selectedPickupPoint.address}` : '选择商品后自动确定'" disabled /></label>
        <label><span>活动模式</span><select v-model="form.mode"><option value="FLASH_SALE">普通抢购</option><option value="LOTTERY">预约抽签</option><option value="PRE_SALE">预售</option></select></label>
        <label class="activity-reservation-toggle"><input v-model="form.hasReservation" type="checkbox" :disabled="form.mode === 'LOTTERY'" /><span>开启预约阶段</span></label>
        <label v-if="form.hasReservation"><span>预约开始</span><input v-model="form.reservationStartAt" type="datetime-local" required /></label>
        <label v-if="form.hasReservation"><span>预约结束</span><input v-model="form.reservationEndAt" type="datetime-local" required /></label>
        <label><span>活动开始</span><input v-model="form.startAt" type="datetime-local" required /></label>
        <label><span>活动结束</span><input v-model="form.endAt" type="datetime-local" required /></label>
        <label><span>活动库存</span><input v-model="form.stock" type="number" min="1" step="1" inputmode="numeric" required /></label>
        <label><span>每人限购</span><input v-model="form.limitPerUser" type="number" min="1" step="1" inputmode="numeric" required /></label>
        <label><span>支付时限（分钟）</span><input v-model="form.paymentTimeoutMinutes" type="number" min="1" step="1" inputmode="numeric" required /></label>
        <label class="activity-editor-rules"><span>活动规则</span><textarea v-model.trim="form.ruleDescription" maxlength="2000" rows="5"></textarea></label>
      </div>
      <p v-if="error" class="inline-alert error" role="alert">{{ error }}</p>
      <div class="product-editor-actions">
        <button class="secondary-action" type="button" :disabled="submitting" @click="emit('cancel')">取消</button>
        <button class="primary-action" type="submit" :disabled="disabled || submitting"><LoaderCircle v-if="submitting" class="auth-loading-icon" :size="17" /><Save v-else-if="isEditing" :size="17" /><CalendarPlus v-else :size="17" />{{ submitting ? '保存中…' : isEditing ? '保存并重提' : '创建并送审' }}</button>
      </div>
    </form>
  </section>
</template>
