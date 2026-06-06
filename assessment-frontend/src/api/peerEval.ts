import { http } from './request'
import type { Result } from './request'

export interface PeerEvalTask {
  examGroupId: number
  groupName: string
  examType: string
  status: string
  totalIndicators: number
  evaluatedCount: number
  progress: number
}

export interface PeerEvalTargetDept {
  targetOrgId: number
  targetOrgName: string
  totalIndicators: number
  evaluatedCount: number
  progress: number
  status: string
}

export interface PeerEvalIndicator {
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
  peerEvalId: number | null
  peerScore: number | null
  scoreComment: string
  status: string
}

export interface PeerEvalByIndicator {
  indicatorId: number
  categoryId?: number | null
  categoryName: string
  sortCode?: number | null
  subCategory: string
  content: string
  totalDepts: number
  scoredCount: number
  deptScores: {
    targetOrgId: number
    targetOrgName: string
    peerScore: number | null
    scoreComment: string
    status: string
  }[]
}

export interface PeerEvalSaveData {
  id?: number
  examGroupId: number
  evaluatorOrgId: number
  targetOrgId: number
  indicatorId: number
  peerScore?: number
  scoreComment?: string
}

export interface PeerEvalQuery {
  examGroupId?: number
  evaluatorOrgId: number
  targetOrgId?: number
  status?: string
}

export function getPeerEvalTaskList(params: PeerEvalQuery) {
  return http.get<PeerEvalTask[]>('/evaluation/peer/task', params)
}

export function getPeerEvalTargets(examGroupId: number, evaluatorOrgId: number) {
  return http.get<PeerEvalTargetDept[]>('/evaluation/peer/targets', { examGroupId, evaluatorOrgId })
}

export function getPeerEvalByDept(examGroupId: number, evaluatorOrgId: number, targetOrgId: number) {
  return http.get<PeerEvalIndicator[]>('/evaluation/peer/by-dept', { examGroupId, evaluatorOrgId, targetOrgId })
}

export function getPeerEvalByIndicator(examGroupId: number, evaluatorOrgId: number, categoryId?: number) {
  return http.get<PeerEvalByIndicator[]>('/evaluation/peer/by-indicator', { examGroupId, evaluatorOrgId, categoryId })
}

export function savePeerEval(data: PeerEvalSaveData) {
  return http.post('/evaluation/peer/save', data)
}

export function submitPeerEval(examGroupId: number, evaluatorOrgId: number, targetOrgId: number) {
  return http.post('/evaluation/peer/submit', null, { params: { examGroupId, evaluatorOrgId, targetOrgId } })
}

export function getPeerEvalStatistics(examGroupId: number) {
  return http.get<any[]>('/evaluation/peer/statistics', { examGroupId })
}
