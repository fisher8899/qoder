import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { getMyExamGroupTasks } from '@/api/examGroup'
import { getOrganizationList } from '@/api/organization'
import type { ExamGroupTaskVO } from '@/api/examGroup'
import type { ExamGroup } from '@/api/types'
import type { Organization } from '@/api/types'

/**
 * 数据范围工具函数
 *
 * 部门级职责 (DEPT_ADMIN / DEPT_LEADER / SUPERVISOR): dataScope = 'ORG', 看本部门数据
 * 单位级职责 (FIN_ADMIN): dataScope = 'UNIT', 看单位+下属全部考核组织数据
 * 系统管理员 (ADMIN): dataScope = 'ALL', 看全部数据
 *
 * 后端 DataScopeFilter 通过 HTTP 请求头 (X-Data-Scope, X-Scope-Id, X-Role-Code) 自动过滤,
 * 前端调用 getExamGroupList / getOrganizationList 等接口时无需额外传参即可自动按范围过滤。
 * 本 composable 提供 effectiveOrgId 用于需要显式传 orgId 的接口参数。
 */
export function useDataScope() {
  const userStore = useUserStore()

  /**
   * 当前用户的生效 orgId
   * - ORG 范围: 取 scopeId (即该部门的 orgId)
   * - UNIT / ALL 范围: 取 userInfo.orgId (用户自身所属组织)
   */
  const effectiveOrgId = computed(() => {
    if (userStore.dataScope === 'ORG') {
      return userStore.scopeId || userStore.userInfo?.orgId
    }
    return userStore.userInfo?.orgId
  })

  /**
   * 加载当前数据范围下的考核组列表
   * 使用 /exam-group/my-tasks 接口，按当前用户 orgId 匹配考核组成员
   * 同时过滤 examCategory（PERFORMANCE = 月度考核）
   */
  async function loadScopedExamGroups(examCategory?: string, size = 999): Promise<ExamGroup[]> {
    try {
      const res = await getMyExamGroupTasks(examCategory)
      const tasks = (res.data || []) as ExamGroupTaskVO[]
      // 转换为 ExamGroup 格式供下拉选择使用
      return tasks.slice(0, size).map(task => ({
        id: task.examGroupId,
        groupName: task.examGroupName,
        examType: task.examType,
        examCategory: task.examCategory,
        startDate: task.startDate,
        endDate: task.endDate,
        status: task.status,
        unitId: task.unitId
      })) as unknown as ExamGroup[]
    } catch (e) {
      console.error('加载考核组列表失败', e)
      return []
    }
  }

  /**
   * 加载当前数据范围下的组织列表
   * 后端 DataScopeFilter 会自动按 UNIT/ORG 范围过滤 unitId
   */
  async function loadScopedOrganizations(size = 999): Promise<Organization[]> {
    try {
      const res = await getOrganizationList({ current: 1, size })
      return (res.data?.records || []) as Organization[]
    } catch (e) {
      return []
    }
  }

  return {
    effectiveOrgId,
    loadScopedExamGroups,
    loadScopedOrganizations
  }
}
