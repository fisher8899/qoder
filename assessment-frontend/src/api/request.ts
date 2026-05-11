import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// 统一响应类型
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

export interface PageResult<T = any> {
  records: T[]
  total: number
  current: number
  size: number
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 添加数据范围相关 Header
    const userStore = useUserStore()
    if (userStore.userInfo?.roleCode) {
      config.headers['X-Role-Code'] = userStore.userInfo.roleCode
    }
    if (userStore.dataScope) {
      config.headers['X-Data-Scope'] = userStore.dataScope
    }
    if (userStore.scopeId !== undefined && userStore.scopeId !== 0) {
      config.headers['X-Scope-Id'] = String(userStore.scopeId)
    }
    if (userStore.scopeName) {
      config.headers['X-Scope-Name'] = encodeURIComponent(userStore.scopeName)
    }
    if (userStore.userInfo?.id) {
      config.headers['X-User-Id'] = String(userStore.userInfo.id)
    }

    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    // blob类型直接返回原始响应
    if (response.config.responseType === 'blob') {
      return response.data as any
    }
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res as any
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络错误'
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service

// 封装常用方法
export const http = {
  get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.get(url, { ...config, params })
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.post(url, data, config)
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.put(url, data, config)
  },
  delete<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.delete(url, { ...config, params })
  }
}
