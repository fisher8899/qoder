// 分页请求参数
export interface PageQuery {
  current: number
  size: number
  [key: string]: any
}

// 单位
export interface Unit {
  id: number
  unitName: string
  unitCode: string
  unitType: string
  isEnabled: number
  createdBy: string
  createdTime: string
  expireDate: string
}

// 考核组织
export interface Organization {
  id: number
  orgName: string
  orgCode: string
  unitId: number
  orgType: string
  sortCode: number
  deptAdminName: string
  deptLeaderName: string
  supervisorName: string
  assessorName: string
}

// 考核组
export interface ExamGroup {
  id: number
  groupName: string
  examCategory: string
  examType: string
  startDate: string
  endDate: string
  progress: number
  status: string
  currentStep: string
}

// 指标大类
export interface IndicatorCategory {
  id: number
  categoryName: string
  categoryCode: string
  sortCode: number
  applicableScope: string
  weight: number
  evaluationStandard: string
}

// 用户信息
export interface UserInfo {
  id: number
  userName: string
  roleCode: string
  roleName: string
  orgId: number
  orgName: string
  unitId: number
  // 新增人员关联信息
  employeeId?: number
  employeeNo?: string
  position?: string
  level?: string
  deptId?: number
  deptName?: string
  orgType?: string
  availableRoles?: Array<{
    roleCode: string
    roleName: string
    dataScope: string  // "ALL" / "UNIT" / "ORG"
    scopeId: number    // 范围ID
    scopeName: string  // 范围名称
    orgType?: string   // 组织类型
  }>
}
