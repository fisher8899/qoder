import { http } from './request'
import type { Result, PageResult } from './request'

export interface IndicatorQuery {
  current?: number
  size?: number
  examGroupId?: number
  orgId?: number
  categoryId?: number
  approvalStatus?: string
}

export interface IndicatorData {
  id?: number
  examGroupId: number
  orgId: number
  categoryId?: number
  categoryName: string
  subCategoryId?: number
  subCategory: string
  content: string
  targetDesc: string
  weightAnnual: number
  weightMonthly: number
  evaluationStandard: string
  sortCode?: number
  examTargetType?: 'DEPARTMENT' | 'LEADER'
  leaderId?: number
  leaderName?: string
  orgName?: string
  // 多选支持字段（数组格式）
  orgIds?: number[]
  orgNames?: string[]
  leaderIds?: number[]
  leaderNames?: string[]
}

export interface IndicatorSubCategoryData {
  id?: number
  examGroupId: number
  orgId: number
  orgName?: string
  categoryId?: number
  categoryName: string
  subCategoryName: string
  evaluationStandard?: string
  sortCode?: number
}

export interface IndicatorTreeNode {
  categoryName: string
  subCategories: {
    subCategory: string
    items: {
      id: number
      content: string
      targetDesc: string
      weightAnnual: number
      weightMonthly: number
      evaluationStandard: string
    }[]
  }[]
}

export interface IndicatorApprovalData {
  indicatorIds: number[]
  action: string
  rejectReason?: string
  roleCode: string
}

export function getIndicatorList(params: IndicatorQuery) {
  return http.get<PageResult<any>>('/indicator/list', params)
}

export function getIndicatorDetail(id: number) {
  return http.get<any>(`/indicator/${id}`)
}

export function getIndicatorTree(examGroupId: number, orgId: number) {
  return http.get<IndicatorTreeNode[]>(`/indicator/tree/${examGroupId}/${orgId}`)
}

export function getIndicatorSubCategories(params: {
  examGroupId: number
  orgId: number
  categoryId?: number
  categoryName?: string
}) {
  return http.get<any[]>('/indicator/sub-category/list', params)
}

export function createIndicatorSubCategory(data: IndicatorSubCategoryData) {
  return http.post<number>('/indicator/sub-category', data)
}

export function updateIndicatorSubCategory(data: IndicatorSubCategoryData) {
  return http.put('/indicator/sub-category', data)
}

export function deleteIndicatorSubCategory(id: number) {
  return http.delete(`/indicator/sub-category/${id}`)
}

export function createIndicator(data: IndicatorData) {
  return http.post('/indicator', data)
}

export function updateIndicator(data: IndicatorData) {
  return http.put('/indicator', data)
}

export function deleteIndicator(id: number) {
  return http.delete(`/indicator/${id}`)
}

export function submitIndicatorForApproval(indicatorIds: number[]) {
  return http.post('/indicator/submit', indicatorIds)
}

export function getApprovalList(params: IndicatorQuery & { roleCode: string }) {
  return http.get<PageResult<any>>('/indicator/approval/list', params)
}

export function approveIndicators(data: IndicatorApprovalData) {
  return http.post('/indicator/approve', data)
}

export function rejectIndicators(data: IndicatorApprovalData) {
  return http.post('/indicator/reject', data)
}

// 指标设定进度查询
export interface IndicatorProgressQuery {
  examGroupId?: number
  orgName?: string
  approvalStatus?: string
}

export interface IndicatorProgressItem {
  orgId: number
  orgName: string
  examGroupId: number
  approvalStatus: string
  groupName: string
  totalCount: number
  approvedCount: number
}

export function getIndicatorProgress(params: IndicatorProgressQuery) {
  return http.get<IndicatorProgressItem[]>('/indicator/progress/list', params)
}
