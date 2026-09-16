<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ClipboardCheck, House, LayoutDashboard, PackageSearch, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
// 不同角色的第三个入口指向各自工作区，学生则进入订单凭证页。
const workspace = computed(() => auth.user?.role === 'ADMIN'
  ? '/admin'
  : auth.user?.role === 'MERCHANT' || auth.user?.role === 'OPERATOR'
    ? '/operator'
    : '/orders')
const workspaceLabel = computed(() => auth.user?.role === 'STUDENT' ? '订单' : auth.user ? '工作台' : '登录')
</script>

<template>
  <nav class="mobile-nav" aria-label="移动端导航">
    <RouterLink to="/" :class="{ active: route.name === 'discover' }"><House :size="21" /><span>发行</span></RouterLink>
    <RouterLink to="/catalog" :class="{ active: route.name === 'catalog' || route.name === 'product' }"><PackageSearch :size="21" /><span>目录</span></RouterLink>
    <RouterLink v-if="auth.user" :to="workspace" :class="{ active: ['orders', 'operator', 'admin'].includes(String(route.name)) }">
      <LayoutDashboard v-if="auth.user.role !== 'STUDENT'" :size="21" />
      <ClipboardCheck v-else :size="21" />
      <span>{{ workspaceLabel }}</span>
    </RouterLink>
    <RouterLink v-if="auth.user?.role === 'STUDENT'" to="/profile" :class="{ active: route.name === 'profile' }"><UserRound :size="21" /><span>我的</span></RouterLink>
    <RouterLink v-else to="/login" :class="{ active: route.name === 'login' }"><UserRound :size="21" /><span>登录</span></RouterLink>
  </nav>
</template>
