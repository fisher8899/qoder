// 包含所有系统管理模块的API调用
import { http } from './request'
import type { Result, PageResult } from './request'

// 单位管理
export const unitApi = {
  list: (params: any) => http.get<PageResult>('/unit/list', params),
  getById: (id: number) => http.get('/unit/' + id),
  create: (data: any) => http.post('/unit', data),
  update: (data: any) => http.put('/unit', data),
  toggle: (id: number) => http.put('/unit/' + id + '/toggle'),
  delete: (id: number) => http.delete('/unit/' + id),
}

// 分管领导
export const leaderApi = {
  list: (params?: any) => http.get<PageResult>('/leader/list', params),
  getAll: (unitId?: number) => http.get('/leader/all', unitId != null ? { unitId } : undefined),
  getById: (id: number) => http.get('/leader/' + id),
  create: (data: any) => http.post('/leader', data),
  update: (data: any) => http.put('/leader', data),
  delete: (id: number) => http.delete('/leader/' + id),
  getEmployees: (unitId?: number) => http.get('/leader/employees', { params: { unitId } }),
}

// 用户
export const userApi = {
  list: () => http.get<any[]>('/permission/users'),
}

// 权限分配
export const permissionApi = {
  list: (params: any) => http.get<PageResult>('/permission/list', params),
  getByUserId: (userId: number) => http.get('/permission/' + userId),
  getUserPermissions: (userId: number) => http.get<any[]>('/permission/user/' + userId),
  create: (data: any) => http.post('/permission', data),
  update: (data: any) => http.put('/permission', data),
  delete: (id: number) => http.delete('/permission/' + id),
}

// 考核组织
export const organizationApi = {
  list: (params: any) => http.get<PageResult>('/organization/list', params),
  all: () => http.get('/organization/all'),
  getById: (id: number) => http.get('/organization/' + id),
  create: (data: any) => http.post('/organization', data),
  update: (data: any) => http.put('/organization', data),
  delete: (id: number) => http.delete('/organization/' + id),
  getEmployees: (params?: { unitId?: number; deptId?: number }) => http.get('/organization/employees', params),
}

// 指标大类
export const indicatorCategoryApi = {
  list: (params: any) => http.get<PageResult>('/indicator-category/list', params),
  all: (applicableScope?: string) => http.get('/indicator-category/all', applicableScope ? { applicableScope } : undefined),
  getById: (id: number) => http.get('/indicator-category/' + id),
  create: (data: any) => http.post('/indicator-category', data),
  update: (data: any) => http.put('/indicator-category', data),
  delete: (id: number) => http.delete('/indicator-category/' + id),
}

// 菜单
export const menuApi = {
  tree: () => http.get('/menu/tree'),
  getByRole: (roleCode: string) => http.get(`/menu/by-role/${roleCode}`),
  getById: (id: number) => http.get('/menu/' + id),
  create: (data: any) => http.post('/menu', data),
  update: (data: any) => http.put('/menu', data),
  delete: (id: number) => http.delete('/menu/' + id),
}

// 职责
export const roleApi = {
  list: (params: any) => http.get<PageResult>('/role/list', params),
  allList: () => http.get<any[]>('/role/all'),
  getAvailable: () => http.get<any[]>('/role/available'),
  getById: (id: number) => http.get('/role/' + id),
  create: (data: any) => http.post('/role', data),
  update: (data: any) => http.put('/role', data),
  delete: (id: number) => http.delete('/role/' + id),
  assignMenus: (id: number, roleMenuItems: Array<{ menuId: number; sortCode: number }>) => http.post('/role/' + id + '/menus', roleMenuItems),
  getMenus: (id: number) => http.get<Array<{ menuId: number; sortCode: number }>>('/role/' + id + '/menus'),
  getRoleChildren: (roleId: number) => http.get(`/role/${roleId}/children`),
  addRoleChild: (roleId: number, childRoleId: number) => http.post(`/role/${roleId}/children`, { childRoleId }),
  removeRoleChild: (roleId: number, childId: number) => http.delete(`/role/${roleId}/children/${childId}`),
  listByType: (type: string) => http.get('/role/list-by-type', { type }),
}

// 数据同步
export const dataSyncApi = {
  history: (params: any) => http.get<PageResult>('/data-sync/history', params),
  manual: () => http.post('/data-sync/manual'),
  getById: (id: number) => http.get('/data-sync/' + id),
}

// 数据字典
export const dictApi = {
  getByType: (dictType: string) => http.get('/dict/' + dictType),
}

// 人员管理
export const employeeApi = {
  list: (params: any) => http.get<PageResult>('/employee/list', params),
  all: () => http.get('/employee/all'),
  getById: (id: number) => http.get('/employee/' + id),
  create: (data: any) => http.post('/employee', data),
  update: (data: any) => http.put('/employee', data),
  delete: (id: number) => http.delete('/employee/' + id),
}

// 考核组织API
export const examOrgApi = {
  list: () => http.get<any[]>('/organization/all'),
}

// 通知
export const notificationApi = {
  getUnread: () => http.get<any[]>('/notification/unread'),
  getRead: () => http.get<any[]>('/notification/read'),
  markRead: (id: number) => http.put('/notification/' + id + '/mark-read'),
  getUnreadCount: () => http.get<number>('/notification/unread-count'),
}

// 系统用户管理
export const sysUserApi = {
  list: (params: any) => http.get<PageResult>('/sys-user/list', params),
  getById: (id: number) => http.get('/sys-user/' + id),
  create: (data: any) => http.post('/sys-user', data),
  update: (data: any) => http.put('/sys-user', data),
  toggleEnabled: (id: number) => http.put('/sys-user/' + id + '/toggle-enabled'),
  resetPassword: (id: number) => http.put('/sys-user/' + id + '/reset-password'),
  delete: (id: number) => http.delete('/sys-user/' + id),
  getRoles: (userId: number) => http.get<string[]>('/sys-user/' + userId + '/roles'),
  saveRoles: (userId: number, roles: string[]) => http.post('/sys-user/' + userId + '/roles', roles),
}
