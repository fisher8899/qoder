import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/api/types'
import { switchRole as switchRoleApi, logout as logoutApi } from '@/api/auth'

type AvailableRole = NonNullable<UserInfo['availableRoles']>[number]

const STORAGE_KEYS = {
  userInfo: 'userInfo',
  token: 'token',
  dataScope: 'dataScope',
  scopeId: 'scopeId',
  scopeName: 'scopeName',
  allowedPaths: 'allowedPaths',
  activeRoleCode: 'activeRoleCode',
  activeScopeId: 'activeScopeId',
  activeRoleName: 'activeRoleName'
} as const

function readJson<T>(key: string, fallback: T): T {
  const raw = localStorage.getItem(key)
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export const useUserStore = defineStore('user', () => {
  const initialUserInfo = readJson<UserInfo | null>(STORAGE_KEYS.userInfo, null)
  const userInfo = ref<UserInfo | null>(initialUserInfo)
  
  // 职责列表按职责名称第一个数字排序，无数字的排最后
  const sortedAvailableRoles = computed(() => {
    const roles = userInfo.value?.availableRoles || []
    return [...roles].sort((a, b) => {
      const extractNumber = (name: string) => {
        const match = name.match(/^(\d+)/)
        return match ? parseInt(match[1], 10) : Infinity
      }
      const numA = extractNumber(a.roleName || '')
      const numB = extractNumber(b.roleName || '')
      return numA - numB
    })
  })
  const token = ref(localStorage.getItem(STORAGE_KEYS.token) || '')
  const menus = ref<any[]>([])
  const allowedPaths = ref<string[]>(readJson<string[]>(STORAGE_KEYS.allowedPaths, []))

  const dataScope = ref(localStorage.getItem(STORAGE_KEYS.dataScope) || '')
  const scopeId = ref(Number(localStorage.getItem(STORAGE_KEYS.scopeId) || '0'))
  const scopeName = ref(localStorage.getItem(STORAGE_KEYS.scopeName) || '')

  const activeRoleCode = ref(localStorage.getItem(STORAGE_KEYS.activeRoleCode) || initialUserInfo?.roleCode || '')
  const activeScopeId = ref(Number(localStorage.getItem(STORAGE_KEYS.activeScopeId) || '0'))
  const activeRoleName = ref(localStorage.getItem(STORAGE_KEYS.activeRoleName) || initialUserInfo?.roleName || '')

  function setToken(value: string) {
    token.value = value
    localStorage.setItem(STORAGE_KEYS.token, value)
  }

  function setMenus(value: any[]) {
    menus.value = value
  }

  function setAllowedPaths(paths: string[]) {
    allowedPaths.value = Array.from(new Set((paths || []).filter(Boolean)))
    localStorage.setItem(STORAGE_KEYS.allowedPaths, JSON.stringify(allowedPaths.value))
  }

  function clearPermissionCache() {
    menus.value = []
    allowedPaths.value = []
    localStorage.removeItem(STORAGE_KEYS.allowedPaths)
  }

  function setDataScope(scope: string, id: number, name: string) {
    dataScope.value = scope
    scopeId.value = id
    scopeName.value = name
    localStorage.setItem(STORAGE_KEYS.dataScope, scope)
    localStorage.setItem(STORAGE_KEYS.scopeId, String(id))
    localStorage.setItem(STORAGE_KEYS.scopeName, name)
  }

  function rememberRoleSelection(role: AvailableRole) {
    activeRoleCode.value = role.roleCode
    activeScopeId.value = role.scopeId || 0
    activeRoleName.value = role.roleName
    localStorage.setItem(STORAGE_KEYS.activeRoleCode, activeRoleCode.value)
    localStorage.setItem(STORAGE_KEYS.activeScopeId, String(activeScopeId.value))
    localStorage.setItem(STORAGE_KEYS.activeRoleName, activeRoleName.value)
  }

  function findAvailableRole(roleCode?: string | null, targetScopeId?: number | null): AvailableRole | null {
    const roles = userInfo.value?.availableRoles || []
    if (!roles.length) return null

    if (roleCode != null && targetScopeId != null) {
      const exact = roles.find(role => role.roleCode === roleCode && (role.scopeId || 0) === targetScopeId)
      if (exact) return exact
    }

    if (roleCode != null) {
      const sameRole = roles.find(role => role.roleCode === roleCode)
      if (sameRole) return sameRole
    }

    if (targetScopeId != null) {
      const sameScope = roles.find(role => (role.scopeId || 0) === targetScopeId)
      if (sameScope) return sameScope
    }

    return roles[0]
  }

  function applyRoleSnapshot(role: AvailableRole) {
    if (!userInfo.value) return

    userInfo.value.roleCode = role.roleCode
    userInfo.value.roleName = role.roleName
    if (role.orgType) {
      userInfo.value.orgType = role.orgType
    }

    setDataScope(role.dataScope || 'ALL', role.scopeId || 0, role.scopeName || '全部')
    rememberRoleSelection(role)
    localStorage.setItem(STORAGE_KEYS.userInfo, JSON.stringify(userInfo.value))
  }

  function ensureActiveRole(force = false, restoreRoleCode?: string, restoreScopeId?: number) {
    if (!userInfo.value?.availableRoles?.length) {
      return null
    }

    // 优先使用传入的恢复参数，其次使用当前存储的活动角色
    const candidateCode = restoreRoleCode || activeRoleCode.value || userInfo.value.roleCode
    const candidateScope = restoreScopeId ?? activeScopeId.value

    const candidate = findAvailableRole(candidateCode, candidateScope)

    if (!candidate) {
      return null
    }

    const changed =
      force ||
      userInfo.value.roleCode !== candidate.roleCode ||
      userInfo.value.roleName !== candidate.roleName ||
      dataScope.value !== (candidate.dataScope || 'ALL') ||
      scopeId.value !== (candidate.scopeId || 0) ||
      scopeName.value !== (candidate.scopeName || '全部')

    if (changed) {
      applyRoleSnapshot(candidate)
    } else {
      rememberRoleSelection(candidate)
    }

    return candidate
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    localStorage.setItem(STORAGE_KEYS.userInfo, JSON.stringify(info))

    // 在清除前读取上一次选择的角色，用于恢复
    const prevRoleCode = activeRoleCode.value || localStorage.getItem(STORAGE_KEYS.activeRoleCode) || ''
    const prevScopeId = activeScopeId.value || Number(localStorage.getItem(STORAGE_KEYS.activeScopeId) || '0')

    activeRoleCode.value = ''
    activeScopeId.value = 0
    activeRoleName.value = ''
    localStorage.removeItem(STORAGE_KEYS.activeRoleCode)
    localStorage.removeItem(STORAGE_KEYS.activeScopeId)
    localStorage.removeItem(STORAGE_KEYS.activeRoleName)

    // 尝试恢复上次选择的角色，避免登录后丢失角色上下文
    ensureActiveRole(true, prevRoleCode, prevScopeId)
  }

  async function switchRole(roleCode: string) {
    const role = findAvailableRole(roleCode, null)
    if (!role) return false
    
    try {
      const res = await switchRoleApi(role.roleCode, role.scopeId || 0)
      if (res.data?.token) {
        setToken(res.data.token)
      }
      applyRoleSnapshot(role)
      clearPermissionCache()
      return true
    } catch (error) {
      console.error('切换角色失败:', error)
      return false
    }
  }

  async function switchRoleByScope(roleCode: string, targetScopeId: number) {
    const role = findAvailableRole(roleCode, targetScopeId)
    if (!role) return false

    try {
      const res = await switchRoleApi(roleCode, targetScopeId)
      if (res.data?.token) {
        setToken(res.data.token)
      }
      applyRoleSnapshot(role)
      clearPermissionCache()
      return true
    } catch (error) {
      console.error('切换角色失败:', error)
      return false
    }
  }

  function hasPathAccess(path: string) {
    if (!path || path === '/' || path === '/dashboard') {
      return true
    }
    return allowedPaths.value.includes(path)
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // Ignore network errors during logout
    }
    token.value = ''
    userInfo.value = null
    clearPermissionCache()
    dataScope.value = ''
    scopeId.value = 0
    scopeName.value = ''
    activeRoleCode.value = ''
    activeScopeId.value = 0
    activeRoleName.value = ''

    localStorage.removeItem(STORAGE_KEYS.token)
    localStorage.removeItem(STORAGE_KEYS.userInfo)
    localStorage.removeItem(STORAGE_KEYS.dataScope)
    localStorage.removeItem(STORAGE_KEYS.scopeId)
    localStorage.removeItem(STORAGE_KEYS.scopeName)
    localStorage.removeItem(STORAGE_KEYS.activeRoleCode)
    localStorage.removeItem(STORAGE_KEYS.activeScopeId)
    localStorage.removeItem(STORAGE_KEYS.activeRoleName)
  }

  return {
    userInfo,
    sortedAvailableRoles,
    token,
    menus,
    allowedPaths,
    dataScope,
    scopeId,
    scopeName,
    activeRoleCode,
    activeScopeId,
    activeRoleName,
    setToken,
    setUserInfo,
    setMenus,
    setAllowedPaths,
    setDataScope,
    ensureActiveRole,
    clearPermissionCache,
    switchRole,
    switchRoleByScope,
    hasPathAccess,
    logout
  }
})
