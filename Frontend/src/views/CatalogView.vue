<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ArrowDownWideNarrow, RotateCcw, Search, SlidersHorizontal, Tags } from 'lucide-vue-next'
import EmptyState from '../components/EmptyState.vue'
import ProductCard from '../components/ProductCard.vue'
import { getCategories, getProducts } from '../services/api'
import type { Category, Product, ProductSaleType } from '../types/api'

const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const loading = ref(true)
const error = ref('')
const filters = reactive<{ keyword: string; categoryId?: number; saleType?: ProductSaleType; sort: 'latest' | 'sales' | 'priceAsc' | 'priceDesc' }>({ keyword: '', sort: 'latest' })
const saleTypes: { label: string; value?: ProductSaleType }[] = [
  { label: '全部' }, { label: '常规', value: 'NORMAL' }, { label: '抢购', value: 'FLASH_SALE' }, { label: '抽签', value: 'LOTTERY' }, { label: '预售', value: 'PRE_SALE' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 筛选条件直接映射为后端 ProductQuery，分类数据与商品列表并行请求。
    const [page, categoryData] = await Promise.all([getProducts({ ...filters, size: 40 }), getCategories()])
    products.value = page.items
    categories.value = categoryData
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '目录加载失败'
  } finally {
    loading.value = false
  }
}

function reset() {
  // 重置后重新请求，确保真实后端分页/排序结果也同步更新。
  filters.keyword = ''
  filters.categoryId = undefined
  filters.saleType = undefined
  filters.sort = 'latest'
  load()
}

onMounted(load)
</script>

<template>
  <div class="catalog-page page-wrap narrow-wrap">
    <header class="catalog-header">
      <div><h1>文创目录</h1><p>按发售方式、分类与关键词找到你要参加的校园限定。</p></div>
      <span class="catalog-count" aria-live="polite">{{ loading ? '--' : products.length }} 件在售</span>
    </header>

    <!-- 搜索、分类、排序和发售方式收拢为同一块筛选工作台。 -->
    <section class="catalog-controls" aria-label="目录筛选">
      <form class="filter-bar" :aria-busy="loading" @submit.prevent="load">
        <label class="search-field">
          <Search :size="19" aria-hidden="true" />
          <span class="sr-only">搜索商品</span>
          <input v-model="filters.keyword" type="search" placeholder="搜索名称、卖点或标签" />
          <button class="catalog-search-action" type="submit" :disabled="loading">搜索</button>
        </label>
        <label class="filter-select"><span><Tags :size="14" aria-hidden="true" />分类</span><select v-model="filters.categoryId" :disabled="loading" @change="load"><option :value="undefined">全部分类</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></label>
        <label class="filter-select"><span><ArrowDownWideNarrow :size="14" aria-hidden="true" />排序</span><select v-model="filters.sort" :disabled="loading" @change="load"><option value="latest">最新发布</option><option value="sales">销量优先</option><option value="priceAsc">价格从低到高</option><option value="priceDesc">价格从高到低</option></select></label>
        <button class="icon-button filter-reset" type="button" title="重置筛选" aria-label="重置筛选" :disabled="loading" @click="reset"><RotateCcw :size="19" /><span>重置筛选</span></button>
      </form>

      <div class="segment-row" aria-label="发售方式">
        <span class="segment-label"><SlidersHorizontal :size="18" aria-hidden="true" /><span>发售方式</span></span>
        <button v-for="type in saleTypes" :key="type.label" class="sale-type-button" type="button" :class="{ active: filters.saleType === type.value }" :aria-pressed="filters.saleType === type.value" :disabled="loading" @click="filters.saleType = type.value; load()">
          <!-- 发售方式使用蓝白线段按钮，选中后保持蓝底白字。 -->
          <span class="sale-type-button__top-key" aria-hidden="true"></span>
          <span class="sale-type-button__text">{{ type.label }}</span>
          <span class="sale-type-button__bottom-key sale-type-button__bottom-key--wide" aria-hidden="true"></span>
          <span class="sale-type-button__bottom-key sale-type-button__bottom-key--short" aria-hidden="true"></span>
        </button>
      </div>
    </section>

    <div v-if="error" class="inline-alert error"><span>{{ error }}</span><button type="button" @click="load">重试</button></div>
    <div v-if="loading" class="product-grid catalog-grid" aria-label="正在加载商品"><div v-for="n in 8" :key="n" class="skeleton product-skeleton"></div></div>
    <TransitionGroup v-else-if="products.length" name="list-post" tag="div" class="product-grid catalog-grid" appear><ProductCard v-for="(product, index) in products" :key="product.id" :product="product" :index="index" :style="{ '--list-index': Math.min(index, 5) }" /></TransitionGroup>
    <EmptyState v-else title="没有匹配的文创" description="换一个关键词或清除部分筛选条件。"><button class="secondary-action" type="button" @click="reset">清除筛选</button></EmptyState>
  </div>
</template>
