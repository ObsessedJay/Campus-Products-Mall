<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ArrowUpDown, ImageOff, ImagePlus, LoaderCircle, Save, Trash2, Upload, X } from 'lucide-vue-next'
import {
  deleteProductImage,
  getProductImages,
  updateProductImageSort,
  uploadProductImage,
} from '../services/api'
import type { Product, ProductImage } from '../types/api'

const props = defineProps<{ product: Product; disabled?: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const images = ref<ProductImage[]>([])
const loading = ref(false)
const uploading = ref(false)
const busyImageId = ref<number>()
const deleteImageId = ref<number>()
const selectedFile = ref<File>()
const imageName = ref('')
const fileInput = ref<HTMLInputElement>()
const error = ref('')
const feedback = ref('')
const sortDrafts = reactive<Record<number, number>>({})
const atLimit = computed(() => images.value.length >= 8)

watch(() => props.product.id, loadImages, { immediate: true })

async function loadImages() {
  loading.value = true
  error.value = ''
  feedback.value = ''
  deleteImageId.value = undefined
  selectedFile.value = undefined
  imageName.value = ''
  try {
    images.value = await getProductImages(props.product.id)
    images.value.forEach((image) => { sortDrafts[image.id] = image.sortOrder })
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '商品图片加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openFilePicker() {
  fileInput.value?.click()
}

function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  error.value = ''
  feedback.value = ''
  if (!file) return
  if (!['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type)) {
    error.value = '请选择 PNG、JPEG、GIF 或 WebP 图片。'
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    error.value = '图片不能超过 5 MB，请压缩后重新选择。'
    return
  }
  selectedFile.value = file
  imageName.value = file.name.replace(/\.[^.]+$/, '').slice(0, 100)
}

async function upload() {
  if (!selectedFile.value || uploading.value || props.disabled || atLimit.value) return
  error.value = ''
  feedback.value = ''
  uploading.value = true
  try {
    await uploadProductImage(props.product.id, selectedFile.value, imageName.value)
    selectedFile.value = undefined
    imageName.value = ''
    feedback.value = '图片已加入商品图库。'
    await reloadAfterChange()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '图片上传失败，请检查文件后重试'
  } finally {
    uploading.value = false
  }
}

async function saveSort(image: ProductImage) {
  const sortOrder = Number(sortDrafts[image.id])
  error.value = ''
  feedback.value = ''
  if (!Number.isInteger(sortOrder) || sortOrder < 0) {
    error.value = '展示顺序必须是大于或等于 0 的整数。'
    return
  }
  if (sortOrder === image.sortOrder || busyImageId.value) return
  busyImageId.value = image.id
  try {
    await updateProductImageSort(props.product.id, image.id, sortOrder)
    feedback.value = '展示顺序已保存。'
    await reloadAfterChange()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '排序保存失败，请稍后重试'
  } finally {
    busyImageId.value = undefined
  }
}

async function remove(image: ProductImage) {
  if (busyImageId.value || props.disabled) return
  error.value = ''
  feedback.value = ''
  busyImageId.value = image.id
  try {
    await deleteProductImage(props.product.id, image.id)
    deleteImageId.value = undefined
    feedback.value = image.url === props.product.coverUrl ? '封面已删除，系统已顺延下一张图片。' : '图片已删除。'
    await reloadAfterChange()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '图片删除失败，请稍后重试'
  } finally {
    busyImageId.value = undefined
  }
}

async function reloadAfterChange() {
  images.value = await getProductImages(props.product.id)
  images.value.forEach((image) => { sortDrafts[image.id] = image.sortOrder })
  emit('changed')
}

function formatBytes(bytes: number) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(bytes / 1024)} KB`
}
</script>

<template>
  <section class="product-image-manager" aria-labelledby="product-image-title">
    <header>
      <div>
        <h3 id="product-image-title">{{ product.name }}</h3>
        <p>商品图库 · {{ images.length }}/8 张</p>
      </div>
      <button class="icon-button" type="button" title="关闭图片管理" aria-label="关闭图片管理" @click="emit('close')"><X :size="18" /></button>
    </header>

    <div class="product-image-upload">
      <input ref="fileInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/gif,image/webp" :disabled="disabled || atLimit" @change="selectFile" />
      <button class="secondary-action" type="button" :disabled="disabled || atLimit || uploading" @click="openFilePicker"><ImagePlus :size="17" />{{ atLimit ? '已达 8 张上限' : '选择图片' }}</button>
      <label class="product-image-name">
        <span>图片名称</span>
        <input v-model="imageName" type="text" maxlength="100" :disabled="!selectedFile || disabled || atLimit || uploading" />
      </label>
      <span :title="selectedFile?.name">{{ selectedFile?.name || 'PNG / JPEG / GIF / WebP，最大 5 MB' }}</span>
      <button class="primary-action" type="button" :disabled="!selectedFile || disabled || atLimit || uploading" @click="upload">
        <LoaderCircle v-if="uploading" class="auth-loading-icon" :size="17" /><Upload v-else :size="17" />{{ uploading ? '上传中…' : '上传' }}
      </button>
    </div>

    <p v-if="error" class="inline-alert error" role="alert">{{ error }} <button type="button" @click="loadImages">重新加载</button></p>
    <p v-if="feedback" class="action-feedback" role="status">{{ feedback }}</p>

    <div v-if="loading" class="product-image-loading" aria-label="正在加载商品图片"><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div></div>
    <div v-else-if="!images.length" class="product-image-empty">
      <ImageOff :size="30" />
      <strong>图库还是空的</strong>
      <span>上传第一张图片后，它会自动成为商品封面。</span>
    </div>
    <div v-else class="product-image-list">
      <article v-for="image in images" :key="image.id" class="product-image-row" :class="{ 'is-cover': image.url === product.coverUrl }">
        <figure><img :src="image.url" :alt="`${product.name} 商品图片`" /></figure>
        <div class="product-image-meta">
          <strong>{{ image.displayName || `图片 #${image.id}` }}</strong>
          <span>{{ image.url === product.coverUrl ? '当前封面 · ' : '' }}{{ image.contentType.replace('image/', '').toUpperCase() }} · {{ formatBytes(image.sizeBytes) }}</span>
        </div>
        <label class="product-image-sort">
          <span><ArrowUpDown :size="14" />顺序</span>
          <input v-model.number="sortDrafts[image.id]" type="number" min="0" step="1" :disabled="disabled || busyImageId === image.id" />
        </label>
        <div class="product-image-actions">
          <button class="icon-button" type="button" title="保存展示顺序" :aria-label="`保存图片 ${image.id} 的展示顺序`" :disabled="disabled || busyImageId !== undefined || sortDrafts[image.id] === image.sortOrder" @click="saveSort(image)"><Save :size="17" /></button>
          <button class="icon-button danger" type="button" title="删除图片" :aria-label="`删除图片 ${image.id}`" :disabled="disabled || busyImageId !== undefined" @click="deleteImageId = image.id"><Trash2 :size="17" /></button>
        </div>
        <div v-if="deleteImageId === image.id" class="product-image-delete-confirm" role="alertdialog" aria-label="确认删除商品图片">
          <span>{{ image.url === product.coverUrl ? '删除封面后将自动顺延下一张。' : '删除后无法从图库恢复。' }}</span>
          <button class="secondary-action" type="button" @click="deleteImageId = undefined">取消</button>
          <button class="primary-action" type="button" :disabled="busyImageId !== undefined" @click="remove(image)"><LoaderCircle v-if="busyImageId === image.id" class="auth-loading-icon" :size="16" /><Trash2 v-else :size="16" />确认删除</button>
        </div>
      </article>
    </div>
  </section>
</template>
