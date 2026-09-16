import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { UserRole } from '../types/api'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: UserRole[]
  }
}

// 页面按路由懒加载，避免首次打开时一次性加载所有工作区代码。
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'discover', component: () => import('../views/DiscoverView.vue') },
    { path: '/catalog', name: 'catalog', component: () => import('../views/CatalogView.vue') },
    { path: '/products/:id', name: 'product', component: () => import('../views/ProductDetailView.vue') },
    { path: '/activities/:id', name: 'activity', component: () => import('../views/ActivityDetailView.vue') },
    { path: '/orders', name: 'orders', component: () => import('../views/OrdersView.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
    { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/operator', name: 'operator', component: () => import('../views/OperatorView.vue'), meta: { requiresAuth: true, roles: ['MERCHANT', 'OPERATOR', 'ADMIN'] } },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  // 路由守卫执行时再次恢复，保证直接输入受保护地址也能识别本地登录状态。
  if (!auth.user) auth.restore()
  // 未登录用户先去登录页，redirect 参数用于登录后回到原目标页面。
  if (to.meta.requiresAuth && !auth.user) return { name: 'login', query: { redirect: to.fullPath } }
  // 角色限制只负责前端导航体验，真正的权限判断仍由 Spring Security 完成。
  if (to.meta.roles && auth.user && !to.meta.roles.includes(auth.user.role)) return { name: 'discover', query: { denied: '1' } }
  if (to.name === 'login' && auth.user) {
    if (auth.user.role === 'ADMIN') return { name: 'admin' }
    if (auth.user.role === 'MERCHANT' || auth.user.role === 'OPERATOR') return { name: 'operator' }
    return { name: 'discover' }
  }
})

export default router
