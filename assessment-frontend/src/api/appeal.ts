import { http } from './request'
import type { Result, PageResult } from './request'

export interface AppealQuery {
  examGroupId?: number
  appealOrgId?: number
  status?: string
  current?: number
  size?: number
}

export interface AppealItem {
  id: number
  examGroupId: number
  appealOrgId: number
  appealOrgName: string
  scorerOrgId: number
  scorerOrgName: string
  indicatorId?: number
  appealReason: string
  status: string
  originalScore?: number
  newScore?: number
  handledBy?: string
  handledTime?: string
  createdBy?: string
  createdTime: string
  updatedTime?: string
}

export interface AppealCreateData {
  examGroupId: number
  appealOrgId: number
  appealOrgName: string
  scorerOrgId: number
  scorerOrgName: string
  indicatorId?: number
  appealReason: string
  originalScore?: number
}

export interface AppealHandleData {
  appealId?: number
  newScore?: number
  handleComment?: string
}

export interface AppealAttachment {
  id: number
  appealId: number
  fileName: string
  fileUrl: string
  fileSize: number
  fileType: string
  createdTime: string
}

export interface AppealDetail {
  appeal: AppealItem
  attachments: AppealAttachment[]
}

export function getAppealList(params: AppealQuery) {
  return http.get<PageResult<AppealItem>>('/appeal/list', params)
}

export function getAppealDetail(id: number) {
  return http.get<AppealDetail>(`/appeal/${id}`)
}

export function createAppeal(data: AppealCreateData) {
  return http.post<AppealItem>('/appeal', data)
}

export function submitAppeal(id: number) {
  return http.post(`/appeal/${id}/submit`)
}

export function reassignAppeal(id: number) {
  return http.post(`/appeal/${id}/reassign`)
}

export function handleAppeal(id: number, data: AppealHandleData) {
  return http.post(`/appeal/${id}/handle`, data)
}

export function uploadAppealAttachment(file: File, appealId: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('appealId', String(appealId))
  return http.post<AppealAttachment>('/appeal/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getAppealAttachments(appealId: number) {
  return http.get<AppealAttachment[]>(`/appeal/${appealId}/attachments`)
}

export function deleteAppealAttachment(attachmentId: number) {
  return http.delete(`/appeal/attachment/${attachmentId}`)
}

export function getPendingReevalList(scorerOrgId: number) {
  return http.get<AppealItem[]>('/appeal/pending-reeval', { scorerOrgId })
}

export function reScoreAppeal(id: number, data: AppealHandleData) {
  return http.post(`/appeal/${id}/re-score`, data)
}

export function deleteAppeal(id: number) {
  return http.delete(`/appeal/${id}`)
}
