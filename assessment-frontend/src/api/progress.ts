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

export interface UnfilledItem {
  indicatorName: string
  stage: string
  orgName: string
}

export function getProgressList(params: ProgressQuery) {
  return http.get<ExamProgressItem[]>('/progress/query', params)
}

export function getUnfilledItems(examGroupId: number, orgId: number) {
  return http.get<UnfilledItem[]>('/progress/unfilled', { examGroupId, orgId })
}
