import { http } from './request'
import type { Result, PageResult } from './request'
import type { Organization } from './types'

export function getOrganizationList(params: { current?: number; size?: number; keyword?: string }) {
  return http.get<PageResult<Organization>>('/organization/list', params)
}

export function getOrganizationDetail(id: number) {
  return http.get<Organization>('/organization/' + id)
}
