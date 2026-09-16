<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CalendarDays, LogOut, Search, ShieldCheck, ShoppingBag, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date())
const isMerchant = computed(() => auth.user?.role === 'MERCHANT' || auth.user?.role === 'OPERATOR')
const roleEntry = computed(() => auth.user?.role === 'ADMIN' ? '/admin' : isMerchant.value ? '/operator' : '/profile')

async function logout() {
  await auth.logout()
  await router.push('/')
}
</script>

<template>
  <header class="site-header">
    <RouterLink class="brand" to="/" aria-label="校集首页">
      <span class="brand-mark">校</span>
      <span class="brand-name">校集</span>
      <span class="brand-note">校园限定发行所</span>
    </RouterLink>

    <nav class="desktop-nav" aria-label="主导航">
      <RouterLink to="/" :class="{ active: route.name === 'discover' }">本周发行</RouterLink>
      <RouterLink to="/catalog" :class="{ active: route.name === 'catalog' || route.name === 'product' }">文创目录</RouterLink>
      <RouterLink v-if="auth.user?.role === 'STUDENT'" to="/orders" :class="{ active: route.name === 'orders' }">我的凭证</RouterLink>
      <RouterLink v-if="auth.user?.role === 'STUDENT'" to="/profile" :class="{ active: route.name === 'profile' }">个人资料</RouterLink>
      <RouterLink v-if="isMerchant" to="/operator" :class="{ active: route.name === 'operator' }">商家工作台</RouterLink>
      <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/admin" :class="{ active: route.name === 'admin' }">审核工作台</RouterLink>
    </nav>

    <div class="header-actions">
      <span class="date-chip"><CalendarDays :size="16" aria-hidden="true" />{{ dateLabel }}</span>
      <RouterLink class="icon-button" to="/catalog" title="搜索文创" aria-label="搜索文创"><Search :size="20" /></RouterLink>
      <template v-if="auth.user">
        <RouterLink class="user-ticket" :to="roleEntry">
          <ShieldCheck v-if="auth.user.role !== 'STUDENT'" :size="17" />
          <UserRound v-else :size="17" />
          <span>{{ auth.user.nickname }}</span>
        </RouterLink>
        <button class="icon-button" type="button" title="退出登录" aria-label="退出登录" @click="logout"><LogOut :size="20" /></button>
      </template>
      <RouterLink v-else class="login-link" to="/login"><ShoppingBag :size="17" />登录</RouterLink>
    </div>
  </header>
</template>
