import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'

describe('http authentication refresh', () => {
  const storage = new Map<string, string>()

  beforeEach(() => {
    storage.clear()
    storage.set('campus-auth-token', 'expired-token')
    storage.set('campus-auth-user', JSON.stringify({
      token: 'expired-token', userId: 7, email: 'student@example.com', nickname: '同学', role: 'STUDENT',
    }))
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    })
    vi.stubGlobal('window', { dispatchEvent: vi.fn() })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('refreshes once and retries the original request with the new access token', async () => {
    const refreshedUser = {
      token: 'fresh-token', userId: 7, email: 'student@example.com', nickname: '同学', role: 'STUDENT' as const,
    }
    const refreshSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: refreshedUser },
    })
    const adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
      if (config._authRetry) {
        return { data: { success: true, code: 'OK', message: 'success', data: { value: 1 } }, status: 200,
          statusText: 'OK', headers: {}, config }
      }
      throw new AxiosError('unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
        data: { success: false, code: 'UNAUTHORIZED', message: 'unauthorized', data: null }, status: 401,
        statusText: 'Unauthorized', headers: {}, config,
      })
    }

    const response = await http.get('/protected', { adapter })

    expect(response.data.data.value).toBe(1)
    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(storage.get('campus-auth-token')).toBe('fresh-token')
    expect((JSON.parse(storage.get('campus-auth-user')!) as { token: string }).token).toBe('fresh-token')
  })
})
