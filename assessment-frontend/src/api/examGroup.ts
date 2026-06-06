import { http } from './request'
import type { Result, PageResult } from './request'
import type { ExamGroup } from './types'

export interface ExamGroupQuery {
  current?: number
  size?: number
  groupName?: string
  examType?: string
  examCategory?: string
  status?: string
}

export interface ExamGroupCreateData {
  id?: number
  groupName: string
  examCategory: string
  examType: string
  startDate: string
  endDate: string
}

export function getExamGroupList(params: ExamGroupQuery) {
  return http.get<PageResult<ExamGroup>>('/exam-group/list', params)
}

export function getVisibleExamGroups(params: ExamGroupQuery) {
  return http.get<ExamGroup[]>('/exam-group/visible', params)
}

export function getExamGroupDetail(id: number) {
  return http.get<ExamGroup>(`/exam-group/${id}`)
}

export function createExamGroup(data: ExamGroupCreateData) {
  return http.post('/exam-group', data)
}

export function updateExamGroup(data: ExamGroupCreateData) {
  return http.put('/exam-group', data)
}

export function deleteExamGroup(id: number) {
  return http.delete(`/exam-group/${id}`)
}

export function getExamGroupMembers(id: number) {
  return http.get<any[]>(`/exam-group/${id}/members`)
}

export function addExamGroupMembers(id: number, orgIds: number[]) {
  return http.post(`/exam-group/${id}/members`, orgIds)
}

export function removeExamGroupMember(groupId: number, memberId: number) {
  return http.delete(`/exam-group/${groupId}/members/${memberId}`)
}

export function startExamGroup(id: number) {
  return http.post(`/exam-group/${id}/start`)
}

export function publishExamGroupIndicator(id: number) {
  return http.post(`/exam-group/${id}/publish-indicator`)
}

export function startExamGroupExam(id: number) {
  return http.post(`/exam-group/${id}/start-exam`)
}

export function startPeerEval(id: number) {
  return http.post(`/exam-group/${id}/start-peer-eval`)
}

export function prePublishExamGroup(id: number) {
  return http.post(`/exam-group/${id}/pre-publish`)
}

export function publishExamGroup(id: number) {
  return http.post(`/exam-group/${id}/publish`)
}

export function cancelPrePublishExamGroup(id: number) {
  return http.post(`/exam-group/${id}/cancel-pre-publish`)
}

export function getExamGroupProgress(id: number) {
  return http.get<any[]>(`/exam-group/${id}/progress`)
}

export function restartExamGroup(id: number) {
  return http.post(`/exam-group/${id}/restart`)
}

export interface ExamGroupTaskVO {
  examGroupId: number
  examGroupName: string
  examType: string
  examCategory: string
  startDate: string
  endDate: string
  status: string
  orgId: number
  orgName: string
  unitId?: number
  approvalStatus: string
}

export function getMyExamGroupTasks(examCategory?: string) {
  return http.get<ExamGroupTaskVO[]>('/exam-group/my-tasks', { examCategory })
}
