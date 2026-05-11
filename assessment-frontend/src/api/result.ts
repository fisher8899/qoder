import { http } from './request'
import type { Result, PageResult } from './request'

export interface ResultQuery {
  examGroupId: number
  orgId?: number
  categoryId?: number
  scoreMonth?: string
  current?: number
  size?: number
}

export interface ResultDetailItem {
  orgId: number
  orgName: string
  categoryName: string
  subCategory: string
  content: string
  targetDesc: string
  weightAnnual: number
  weightMonthly: number
  evaluationStandard: string
  selfScore: number
  peerScore: number
  adminScore: number
  finalScore: number
  weightedScore: number
}

export interface ResultSummaryItem {
  orgId: number
  orgName: string
  categoryScores: Record<string, number>
  totalScore: number
}

export interface HistoryExamItem {
  examGroupId: number
  groupName: string
  examCategory: string
  examType: string
  startDate: string
  endDate: string
  status: string
  currentStep: string
  totalScore: number
}

export function getResultList(params: ResultQuery) {
  return http.get<PageResult<ResultDetailItem>>('/result/query', params)
}

export function getResultSummary(examGroupId: number) {
  return http.get<ResultSummaryItem[]>('/result/summary', { examGroupId })
}

export function exportDetailExcel(examGroupId: number, orgId?: number) {
  const params: any = { examGroupId }
  if (orgId !== undefined) params.orgId = orgId
  return http.get('/result/export/detail', params, { responseType: 'blob' })
}

export function exportSummaryExcel(examGroupId: number) {
  return http.get('/result/export/summary', { examGroupId }, { responseType: 'blob' })
}

export function getHistoryList(orgId: number, year?: string) {
  return http.get<HistoryExamItem[]>('/result/history', { orgId, year })
}

export function getDetailByOrg(examGroupId: number, orgId: number) {
  return http.get<ResultDetailItem[]>('/result/detail-by-org', { examGroupId, orgId })
}
