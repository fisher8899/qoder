import { http } from './request'
import type { Result } from './request'

export interface ReviewQuery {
  examGroupId?: number
  orgId?: number
  categoryId?: number
}

export interface ReviewItem {
  id?: number
  examGroupId: number
  orgId: number
  orgName: string
  indicatorId: number
  categoryName: string
  subCategory: string
  content: string
  weightMonthly: number
  deptScore: number | null
  adminScore: number | null
  finalScore: number | null
  scoreComment: string
}

export interface ReviewScoreSaveData {
  id?: number
  examGroupId: number
  orgId: number
  indicatorId: number
  adminScore?: number
  scoreComment?: string
}

export interface ReviewScoreBatchData {
  examGroupId: number
  items: ReviewScoreSaveData[]
}

export interface ReviewSummary {
  orgId: number
  orgName: string
  totalIndicators: number
  reviewedCount: number
  progress: number
  status: string
}

export function getReviewList(params: ReviewQuery) {
  return http.get<ReviewItem[]>('/review/list', params)
}

export function getReviewSummary(examGroupId: number) {
  return http.get<ReviewSummary[]>(`/review/${examGroupId}/summary`)
}

export function saveReviewScore(data: ReviewScoreSaveData) {
  return http.post('/review/save', data)
}

export function batchSaveReviewScore(data: ReviewScoreBatchData) {
  return http.post('/review/batch-save', data)
}

export function submitReview(examGroupId: number) {
  return http.post('/review/submit', null, { params: { examGroupId } })
}
