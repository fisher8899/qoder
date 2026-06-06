import { http } from './request'
import type { Result } from './request'

export interface ProgressQuery {
  examGroupId: number
  orgId?: number
  period?: string
}

export interface ExamProgressItem {
  orgId: number
  orgName: string
  selfEvalRate: number
  peerEvalRate: number
  reviewStatus: string
  overallProgress: number
}

export interface ExamProgressDetailItem {
  indicatorId: number
  categoryId?: number | null
  categoryName: string
  sortCode?: number | null
  subCategory: string
  content: string
  targetDesc: string
  weightAnnual: number
  weightMonthly: number
  evaluationStandard: string
  selfEvalId: number | null
  actualCompletion: string
  selfScore: number | null
  selfResult: number | null
  attachmentUrl: string
  attachmentName: string
  attachmentDownloadUrl?: string
  status: string
  peerResult: number | null
  peerComment: string
  adminScore: number | null
  adjustComment: string
}

export interface UnfilledItem {
  indicatorName: string
  stage: string
  orgName: string
  evaluateTargetName?: string
}

export function getProgressList(params: ProgressQuery) {
  return http.get<ExamProgressItem[]>('/progress/query', params)
}

export function getProgressDetail(examGroupId: number, orgId: number) {
  return http.get<ExamProgressDetailItem[]>('/progress/detail', { examGroupId, orgId })
}

export function getUnfilledItems(examGroupId: number, orgId: number) {
  return http.get<UnfilledItem[]>('/progress/unfilled', { examGroupId, orgId })
}
