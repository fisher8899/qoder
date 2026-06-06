import { http } from './request'
import type { Result } from './request'

export interface LeaderEvalTask {
  examGroupId: number
  groupName: string
  examType: string
  status: string
  totalIndicators: number
  evaluatedCount: number
  progress: number
  startDate: string
  endDate: string
}

export interface LeaderEvalIndicator {
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
  targetOrgId: number
  targetOrgName: string
  actualCompletion: string
  selfScore: number | null
  attachmentUrl: string
  attachmentName: string
  evalId: number | null
  leaderScore: number | null
  scoreComment: string
  status: string
}

export interface LeaderEvalSaveData {
  examGroupId: number
  targetOrgId: number
  indicatorId: number
  leaderScore: number
  scoreComment?: string
}

export function getLeaderEvalTasks() {
  return http.get<LeaderEvalTask[]>('/evaluation/leader/tasks')
}

export function getLeaderEvalIndicators(examGroupId: number) {
  return http.get<LeaderEvalIndicator[]>('/evaluation/leader/indicators', { examGroupId })
}

export function saveLeaderEval(data: LeaderEvalSaveData) {
  return http.post('/evaluation/leader/save', data)
}

export function submitLeaderEval(examGroupId: number, targetOrgId: number) {
  return http.post('/evaluation/leader/submit', null, { params: { examGroupId, targetOrgId } })
}
