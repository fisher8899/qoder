import { http } from './request'
import type { Result } from './request'
import type { ExamGroup } from './types'

export interface MonthlyExamStatus {
  selfEvalRate: number
  peerEvalRate: number
  deptDetails: {
    orgId: number
    orgName: string
    selfEvalStatus: string
    peerEvalCompleted: number
    peerEvalTotal: number
    peerEvalProgress: number
  }[]
}

export interface DeptProgress {
  orgId: number
  orgName: string
  selfEvalStatus: string
  selfEvalProgress: number
  peerEvalProgress: number
  overallStatus: string
}

export function getMonthlyExamList() {
  return http.get<ExamGroup[]>('/monthly-exam/list')
}

export function getMonthlyExamStatus(examGroupId: number) {
  return http.get<MonthlyExamStatus>(`/monthly-exam/${examGroupId}/status`)
}

export function getMonthlyExamDeptProgress(examGroupId: number) {
  return http.get<DeptProgress[]>(`/monthly-exam/${examGroupId}/dept-progress`)
}
