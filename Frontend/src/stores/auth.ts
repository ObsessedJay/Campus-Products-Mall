import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as api from '../services/api'
import type { AuthUser } from '../types/api'

const USER_KEY = 'campus-auth-user'
const TOKEN_KEY = 'campus-auth-token'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const loading = ref(false)
  const isAuthenticated = computed(() => Boolean(user.value))

  if (typeof window !== 'undefined') {
    window.addEventListener('campus-auth-expired', clearSession)
  }

  function persist(nextUser: AuthUser) {
    // 同时保存用户资料和 Token，刷新页面后可以恢复完整的前端会话。
    user.value = nextUser
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    localStorage.setItem(TOKEN_KEY, nextUser.token)
  }

  function restore() {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return
    try {
      // localStorage 只存 JSON，解析失败时主动清理，避免污染后续路由判断。
      persist(JSON.parse(raw) as AuthUser)
    } catch {
      clearSession()
    }
  }

  async function signIn(email: string, password: string, captchaId: string, captchaCode: string, portalRole: 'STUDENT' | 'MERCHANT' | 'ADMIN') {
    loading.value = true
    try {
      persist(await api.login(email, password, captchaId, captchaCode, portalRole))
    } finally {
      loading.value = false
    }
  }

  async function signUpMerchant(payload: Parameters<typeof api.registerMerchant>[0]) {
    loading.value = true
    try {
      persist(await api.registerMerchant(payload))
    } finally {
      loading.value = false
    }
  }

  async function signUp(payload: Parameters<typeof api.register>[0]) {
    loading.value = true
    try {
      persist(await api.register(payload))
    } finally {
      loading.value = false
    }
  }

  function updateNickname(nickname: string) {
    if (!user.value) return
    persist({ ...user.value, nickname })
  }

  function clearSession() {
    user.value = null
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(TOKEN_KEY)
  }

  async function logout() {
    try {
      if (user.value) await api.logout()
    } finally {
      clearSession()
    }
  }

  return { user, loading, isAuthenticated, restore, signIn, signUp, signUpMerchant, updateNickname, logout }
})
