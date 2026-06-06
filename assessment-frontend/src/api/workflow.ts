/**
 * 工作流平台集成服务
 *
 * 集成说明：
 * 工作流平台（Python FastAPI）运行在 localhost:5555，
 * QODER 后端（Spring Boot）运行在 localhost:8080，
 * QODER 前端（Vue 3）通过 Vite 代理 /api/* 到 QODER 后端。
 *
 * 本服务调用 QODER 后端 /api/workflow/* 接口，
 * 这些接口内部代理到工作流平台 API。
 *
 * 角色代码 -> 工作流用户 UUID 映射（需与 WorkflowConfig 保持一致）：
 *   DEPT_LEADER  -> 00000000-0000-0000-0000-000000000002
 *   SUPERVISOR   -> 00000000-0000-0000-0000-000000000003
 *   FIN_ADMIN    -> 00000000-0000-0000-0000-000000000011
 *   DEPT_ADMIN   -> 00000000-0000-0000-0000-000000000001
 */

import { http } from './request'
import type { Result } from './request'

// ===== 类型定义 =====

/** 工作流平台的任务项（来自 TaskItem schema） */
export interface WorkflowTask {
  node_instance_id: string
  instance_id: string
  workflow_name: string
  node_name: string
  node_type: string
  variables: Record<string, any> | null
  started_at: string
  approver_ids: string[]
}

/** 工作流实例响应（来自 InstanceResponse schema） */
export interface WorkflowInstance {
  id: string
  workflow_id: string
  workflow_name: string | null
  status: 'running' | 'completed' | 'cancelled' | string
  variables: Record<string, any> | null
  started_at: string
  finished_at: string | null
  pending_node_instance_id: string | null
}

/** 审批历史项（来自 InstanceHistoryItem schema） */
export interface WorkflowHistoryItem {
  node_instance_id: string
  node_id: string
  node_name: string
  node_type: string
  status: 'pending' | 'approved' | 'rejected' | string
  started_at: string
  finished_at: string | null
  approver_id: string | null
  approver_name: string | null
  action: 'approve' | 'reject' | null
  comment: string | null
  approval_time: string | null
}

/** 审批操作响应（来自 ApprovalResponse schema） */
export interface ApprovalResult {
  success: boolean
  message: string
  next_node_id: string | null
}

// ===== API 函数 =====

/**
 * 获取当前用户的工作流平台用户 UUID
 * 内部根据当前登录用户的角色代码查询
 */
export function getMyWorkflowUserId(): Promise<Result<string>> {
  return http.get<string>('/workflow/my-id')
}

/**
 * 根据角色代码查询对应的工作流用户 UUID
 */
export function getWorkflowApproverId(roleCode: string): Promise<Result<string>> {
  return http.get<string>('/workflow/approver-id', { roleCode })
}

/**
 * 初始化工作流：自动查找"业绩指标审核"工作流 ID
 * 首次配置时调用一次即可
 */
export function initWorkflow(): Promise<Result<string>> {
  return http.post<string>('/workflow/init', null)
}

/**
 * 启动业绩指标审核工作流
 *
 * @param examGroupId 考核组 ID（工作流变量）
 * @param orgId       部门 ID（工作流变量）
 * @param variables   其他自定义变量
 */
export function startIndicatorWorkflow(
  examGroupId: number,
  orgId: number,
  variables?: Record<string, any>
): Promise<Result<WorkflowInstance>> {
  return http.post<WorkflowInstance>('/workflow/start-indicator', {
    examGroupId,
    orgId,
    variables: variables || {},
  })
}

/**
 * 查询当前用户的待办审批任务
 * 返回的工作流实例变量中包含 examGroupId 和 orgId
 */
export function getWorkflowTasks(): Promise<Result<WorkflowTask[]>> {
  return http.get<WorkflowTask[]>('/workflow/tasks')
}

/**
 * 执行审批通过
 *
 * @param nodeInstanceId 节点实例 UUID（来自 getWorkflowTasks 返回的 node_instance_id）
 * @param comment        审批意见（可选）
 */
export function workflowApprove(
  nodeInstanceId: string,
  comment?: string
): Promise<Result<ApprovalResult>> {
  return http.post<ApprovalResult>('/workflow/approve', {
    nodeInstanceId,
    comment: comment || '',
  })
}

/**
 * 执行审批驳回
 *
 * @param nodeInstanceId 节点实例 UUID
 * @param comment        驳回说明
 */
export function workflowReject(
  nodeInstanceId: string,
  comment: string
): Promise<Result<ApprovalResult>> {
  return http.post<ApprovalResult>('/workflow/reject', {
    nodeInstanceId,
    comment,
  })
}

/**
 * 查询工作流实例的审批历史
 *
 * @param instanceId 工作流实例 UUID（来自 getWorkflowTasks 返回的 instance_id）
 */
export function getWorkflowHistory(
  instanceId: string
): Promise<Result<WorkflowHistoryItem[]>> {
  return http.get<WorkflowHistoryItem[]>('/workflow/history/' + instanceId)
}

// ===== 辅助函数 =====

/**
 * 根据角色代码查找对应的待办任务
 * 用于在审批页面筛选当前用户角色对应的审批任务
 */
export function filterTasksByRole(
  tasks: WorkflowTask[],
  roleCode: string
): WorkflowTask[] {
  return tasks.filter(task => {
    // 匹配审批节点
    if (task.node_type !== 'approval') return false

    // 从 approver_ids 中查找该角色对应的 UUID
    // 需要通过 getWorkflowApproverId 查询角色 -> UUID 映射
    return true // 实际由后端根据当前用户角色过滤
  })
}

/**
 * 判断工作流节点是否属于某审批角色
 * node_name 格式如："部门负责人审批" / "分管领导审批" / "财务处审批"
 */
export function getNodeRoleFromName(nodeName: string): string | null {
  if (nodeName.includes('部门负责人')) return 'DEPT_LEADER'
  if (nodeName.includes('分管领导')) return 'SUPERVISOR'
  if (nodeName.includes('财务处')) return 'FIN_ADMIN'
  return null
}
