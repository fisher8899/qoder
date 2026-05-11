import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const storedUserInfo = localStorage.getItem('userInfo')
  const initialUserInfo = storedUserInfo ? JSON.parse(storedUserInfo) as UserInfo : null
  const userInfo = ref<UserInfo | null>(initialUserInfo)
  const token = ref(localStorage.getItem('token') || '')
  const menus = ref<any[]>([])
  const allowedPaths = ref<string[]>([])

  // 数据范围相关
  const dataScope = ref(localStorage.getItem('dataScope') || '')
  const scopeId = ref(Number(localStorage.getItem('scopeId') || '0'))
  const scopeName = ref(localStorage.getItem('scopeName') || '')

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('token', t)
  }

  function setUserInfo(info: UserInfo) {
    // 登录成功后恢复上次选择的角色
    if (info.id && info.availableRoles && info.availableRoles.length > 0) {
      const lastRole = localStorage.getItem(`lastSelectedRole_${info.id}`)
      const lastScopeId = Number(localStorage.getItem(`lastSelectedScopeId_${info.id}`) || '0')
      if (lastRole) {
        const found = info.availableRoles.find(
          r => r.roleCode === lastRole && (r.scopeId || 0) === lastScopeId
        ) || info.availableRoles.find(r => r.roleCode === lastRole)
        if (found) {
          info.roleCode = found.roleCode
          info.roleName = found.roleName
          if (found.orgType) {
            info.orgType = found.orgType
          }
        }
      }
    }
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setMenus(m: any[]) {
    menus.value = m
  }

  function setAllowedPaths(paths: string[]) {
    allowedPaths.value = paths
  }

  function setDataScope(scope: string, id: number, name: string) {
    dataScope.value = scope
    scopeId.value = id
    scopeName.value = name
    localStorage.setItem('dataScope', scope)
    localStorage.setItem('scopeId', String(id))
    localStorage.setItem('scopeName', name)
  }

  function switchRole(roleCode: string) {
    if (!userInfo.value) return
    const role = userInfo.value.availableRoles?.find(r => r.roleCode === roleCode)
    if (!role) return
    userInfo.value.roleCode = role.roleCode
    userInfo.value.roleName = role.roleName
    // 同步更新 orgType
    if (role.orgType) {
      userInfo.value.orgType = role.orgType
    }
    // 同步更新数据范围
    setDataScope(role.dataScope || 'ALL', role.scopeId || 0, role.scopeName || '全部')
    localStorage.setItem(`lastSelectedRole_${userInfo.value.id}`, roleCode)
    localStorage.setItem(`lastSelectedScopeId_${userInfo.value.id}`, String(role.scopeId || 0))
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  function switchRoleByScope(roleCode: string, targetScopeId: number) {
    if (!userInfo.value) return
    // 通过 roleCode + scopeId 精确匹配角色
    const role = userInfo.value.availableRoles?.find(
      r => r.roleCode === roleCode && (r.scopeId || 0) === targetScopeId
    )
    if (!role) return
    userInfo.value.roleCode = role.roleCode
    userInfo.value.roleName = role.roleName
    // 同步更新 orgType
    if (role.orgType) {
      userInfo.value.orgType = role.orgType
    }
    // 同步更新数据范围
    setDataScope(role.dataScope || 'ALL', role.scopeId || 0, role.scopeName || '全部')
    localStorage.setItem(`lastSelectedRole_${userInfo.value.id}`, roleCode)
    localStorage.setItem(`lastSelectedScopeId_${userInfo.value.id}`, String(role.scopeId || 0))
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    menus.value = []
    allowedPaths.value = []
    dataScope.value = ''
    scopeId.value = 0
    scopeName.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('dataScope')
    localStorage.removeItem('scopeId')
    localStorage.removeItem('scopeName')
  }

  return { userInfo, token, menus, allowedPaths, dataScope, scopeId, scopeName, setToken, setUserInfo, setMenus, setAllowedPaths, setDataScope, switchRole, switchRoleByScope, logout }
})
