import { http } from './request'
import type { Result } from './request'

export interface SelfEvalTask {
  examGroupId: number
  groupName: string
  examType: string
  startDate: string
  endDate: string
  status: string
  totalIndicators: number
  evaluatedCount: number
  progress: number
}

export interface SelfEvalIndicator {
  indicatorId: number
  categoryName: string
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
  status: string
}

export interface SelfEvalSaveData {
  id?: number
  examGroupId: number
  orgId: number
  indicatorId: number
  actualCompletion?: string
  selfScore?: number
  attachmentUrl?: string
  attachmentName?: string
}

export interface SelfEvalQuery {
  examGroupId?: number
  orgId: number
  status?: string
}

export function getSelfEvalTaskList(params: SelfEvalQuery) {
  return http.get<SelfEvalTask[]>('/evaluation/self/task', params)
}

export function getSelfEvalIndicators(examGroupId: number, orgId: number) {
  return http.get<SelfEvalIndicator[]>('/evaluation/self/indicators', { examGroupId, orgId })
}

export function saveSelfEval(data: SelfEvalSaveData) {
  return http.post('/evaluation/self/save', data)
}

export function submitSelfEval(examGroupId: number, orgId: number) {
  return http.post('/evaluation/self/submit', null, { params: { examGroupId, orgId } })
}

export function uploadSelfEvalAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<{ url: string; name: string }>('/evaluation/self/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function downloadSelfEvalFile(fileName: string) {
  return http.get(`/evaluation/self/download/${fileName}`)
}
