import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse, AuthUser } from '../types/api'

declare module 'axios' {
  interface InternalAxiosRequestConfig {
    _authRetry?: boolean
  }
}

const USER_KEY = 'campus-auth-user'
const TOKEN_KEY = 'campus-auth-token'
let refreshRequest: Promise<AuthUser> | null = null

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code = 'REQUEST_FAILED',
    public readonly status?: number,
  ) {
    super(message)
  }
}

// 使用相对路径，开发环境由 Vite 代理，生产环境由 Nginx 或网关转发到 Spring Boot。
export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 8000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('campus-auth-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    // 后端即使返回 HTTP 200，也可能用 success=false 表示业务失败。
    if (body && body.success === false) {
      return Promise.reject(new ApiError(body.message, body.code, response.status))
    }
    return response
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const config = error.config as InternalAxiosRequestConfig | undefined
    const token = localStorage.getItem(TOKEN_KEY)
    const canRefresh = error.response?.status === 401 && config && !config._authRetry
      && token && config.url !== '/auth/refresh'

    if (canRefresh) {
      config._authRetry = true
      try {
        const user = await refreshSession()
        config.headers.Authorization = `Bearer ${user.token}`
        return http(config)
      } catch {
        clearLocalSession()
        window.dispatchEvent(new Event('campus-auth-expired'))
        return Promise.reject(new ApiError('登录状态已过期，请重新登录', 'SESSION_EXPIRED', 401))
      }
    }

    return Promise.reject(toApiError(error))
  },
)

/** 使用 HttpOnly Cookie 轮换刷新会话，并合并同一时刻的并发刷新请求。 */
async function refreshSession(): Promise<AuthUser> {
  if (!refreshRequest) {
    refreshRequest = axios.post<ApiResponse<AuthUser>>('/api/v1/auth/refresh', undefined, {
      timeout: 8000,
      withCredentials: true,
      headers: { 'Content-Type': 'application/json' },
    }).then((response) => {
      const user = response.data.data
      persistRefreshedUser(user)
      return user
    }).finally(() => {
      refreshRequest = null
    })
  }
  return refreshRequest
}

/** 更新访问令牌以及持久化用户快照，刷新令牌始终由 HttpOnly Cookie 承载。 */
function persistRefreshedUser(user: AuthUser) {
  localStorage.setItem(TOKEN_KEY, user.token)
  const raw = localStorage.getItem(USER_KEY)
  let previous: AuthUser | undefined
  try {
    previous = raw ? JSON.parse(raw) as AuthUser : undefined
  } catch {
    previous = undefined
  }
  localStorage.setItem(USER_KEY, JSON.stringify({ ...previous, ...user }))
}

/** 清除已经无法续期的本地认证快照。 */
function clearLocalSession() {
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(TOKEN_KEY)
}

/** 将 Axios 错误转换成页面统一使用的业务错误。 */
function toApiError(error: AxiosError<ApiResponse<unknown>>) {
  const message = error.response?.data?.message
    || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '暂时无法连接服务')
  return new ApiError(message, error.response?.data?.code, error.response?.status)
}

// 统一拆开 Spring Boot 的 ApiResponse<T> 包装，业务层只处理 data。
export const unwrap = <T>(response: { data: ApiResponse<T> }) => response.data.data
