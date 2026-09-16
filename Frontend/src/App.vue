<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import MobileNav from './components/MobileNav.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const route = useRoute()
// 登录页使用独立布局，不显示学生端顶部和移动端导航。
const immersiveRoute = computed(() => route.name === 'login')

// 应用首次启动时从 localStorage 恢复上次登录的用户。
onMounted(() => auth.restore())
</script>

<template>
  <!-- surface contract 5c4e38e9: campus noticeboard, task-first, paper/ink/state-ticket grammar -->
  <div class="app-shell" :class="{ 'with-app-nav': !immersiveRoute }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <AppHeader v-if="!immersiveRoute" />
    <main id="main-content" class="app-main">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
    <MobileNav v-if="!immersiveRoute" />
  </div>
</template>
