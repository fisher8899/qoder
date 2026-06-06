import { http } from './request'

export interface LoginData {
  username: string
  password: string
}

export function login(data: LoginData) {
  return http.post<{ token: string; userInfo: any }>('/auth/login', data)
}

export function getUserInfo() {
  return http.get<any>('/auth/user-info')
}

export function logout() {
  return http.post('/auth/logout')
}

export function switchRole(roleCode: string, scopeId: number) {
  return http.post<{
    token: string
    roleCode: string
    roleName: string
    dataScope: string
    scopeId: number
    scopeName: string
  }>('/auth/switch-role', { roleCode, scopeId })
}
