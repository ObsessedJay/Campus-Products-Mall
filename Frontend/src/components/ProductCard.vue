<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowUpRight, ImageOff } from 'lucide-vue-next'
import type { Product } from '../types/api'
import StatusTag from './StatusTag.vue'

const props = defineProps<{ product: Product; index?: number }>()
const imageFailed = ref(false)
// 价格统一在展示层格式化，原始金额仍以接口返回值为准。
const price = computed(() => Number(props.product.price).toFixed(2))
</script>

<template>
  <RouterLink class="product-card" :to="`/products/${product.id}`" :aria-label="`查看 ${product.name}`">
    <div class="product-media">
      <!-- 图片加载失败时显示明确占位，不让断图破坏商品卡布局。 -->
      <img v-if="product.coverUrl && !imageFailed" :src="product.coverUrl" :alt="product.name" loading="lazy" @error="imageFailed = true" />
      <div v-else class="media-fallback"><ImageOff :size="30" /><span>图片待补充</span></div>
      <span class="product-index">{{ String((index || 0) + 1).padStart(2, '0') }}</span>
      <StatusTag :status="product.saleType" compact />
    </div>
    <div class="product-copy">
      <div>
        <h3>{{ product.name }}</h3>
        <p>{{ product.subtitle || '校园限定文创' }}</p>
      </div>
      <ArrowUpRight :size="21" aria-hidden="true" />
    </div>
    <div class="product-meta">
      <strong><small>¥</small>{{ price }}</strong>
      <span>库存 {{ product.stock }}</span>
      <span>限购 {{ product.limitPerUser }}</span>
    </div>
  </RouterLink>
</template>
