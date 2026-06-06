import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/DashboardPage.vue'), meta: { title: '首页' } },
      { path: 'admin/unit', name: 'UnitManagement', component: () => import('@/views/admin/UnitManagement.vue'), meta: { title: '单位管理', roles: ['ADMIN', 'FIN_ADMIN'] } },
      { path: 'admin/employee', name: 'EmployeeManagement', component: () => import('@/views/admin/EmployeeManagement.vue'), meta: { title: '人员管理', roles: ['ADMIN', 'FIN_ADMIN'] } },
      { path: 'admin/user', name: 'UserManagement', component: () => import('@/views/admin/UserManagement.vue'), meta: { title: '系统用户管理', roles: ['ADMIN', 'FIN_ADMIN'] } },
      { path: 'admin/leader', name: 'LeaderManagement', component: () => import('@/views/admin/LeaderManagement.vue'), meta: { title: '分管领导维护', roles: ['ADMIN', 'FIN_ADMIN'] } },
      { path: 'admin/permission', name: 'PermissionManagement', component: () => import('@/views/admin/PermissionManagement.vue'), meta: { title: '权限分配管理', roles: ['ADMIN', 'FIN_ADMIN'] } },
      { path: 'admin/organization', name: 'OrganizationManagement', component: () => import('@/views/admin/OrganizationManagement.vue'), meta: { title: '考核组织管理', roles: ['ADMIN'] } },
      { path: 'admin/indicator-category', name: 'IndicatorCategoryManagement', component: () => import('@/views/admin/IndicatorCategoryManagement.vue'), meta: { title: '指标大类管理', roles: ['ADMIN'] } },
      { path: 'admin/menu', name: 'MenuManagement', component: () => import('@/views/admin/MenuManagement.vue'), meta: { title: '功能/菜单定义', roles: ['ADMIN'] } },
      { path: 'admin/role', name: 'RoleManagement', component: () => import('@/views/admin/RoleManagement.vue'), meta: { title: '职责定义', roles: ['ADMIN'] } },
      { path: 'admin/data-sync', name: 'DataSync', component: () => import('@/views/admin/DataSyncManagement.vue'), meta: { title: '数据同步', roles: ['ADMIN'] } },
      { path: 'admin/db-browser', name: 'DatabaseBrowser', component: () => import('@/views/admin/DatabaseBrowser.vue'), meta: { title: '数据库查询', roles: ['ADMIN'] } },
      { path: 'exam/group', name: 'ExamGroupManagement', component: () => import('@/views/exam/ExamGroupManagement.vue'), meta: { title: '考核组管理', roles: ['FIN_ADMIN'] } },
      { path: 'exam/indicator-approval', name: 'IndicatorApproval', component: () => import('@/views/exam/IndicatorApproval.vue'), meta: { title: '业绩指标审批', roles: ['FIN_ADMIN'] } },
      { path: 'exam/monthly', name: 'MonthlyExam', component: () => import('@/views/exam/MonthlyExamManagement.vue'), meta: { title: '月度考核管理', roles: ['FIN_ADMIN'] } },
      { path: 'exam/review', name: 'ReviewEvaluation', component: () => import('@/views/exam/ReviewEvaluation.vue'), meta: { title: '复核评估', roles: ['FIN_ADMIN'] } },
      { path: 'exam/appeal', name: 'AppealManagement', component: () => import('@/views/exam/AppealManagement.vue'), meta: { title: '申诉管理', roles: ['FIN_ADMIN'] } },
      { path: 'exam/indicator-progress', name: 'IndicatorProgress', component: () => import('@/views/exam/IndicatorProgress.vue'), meta: { title: '指标设定进度查询', roles: ['FIN_ADMIN'] } },
      { path: 'exam/progress', name: 'ExamProgress', component: () => import('@/views/exam/ExamProgress.vue'), meta: { title: '考核进度查询', roles: ['FIN_ADMIN', 'DEPT_ADMIN'] } },
      { path: 'exam/result', name: 'ExamResult', component: () => import('@/views/exam/ExamResult.vue'), meta: { title: '考核结果查询', roles: ['FIN_ADMIN', 'DEPT_ADMIN', 'DEPT_LEADER'] } },
      { path: 'dept/indicator-set', name: 'IndicatorSet', component: () => import('@/views/dept/IndicatorSet.vue'), meta: { title: '业绩指标设定', roles: ['DEPT_ADMIN'] } },
      { path: 'dept/self-eval', name: 'SelfEvaluation', component: () => import('@/views/dept/SelfEvaluation.vue'), meta: { title: '月度考核自评', roles: ['DEPT_ADMIN'] } },
      { path: 'dept/peer-eval', name: 'PeerEvaluation', component: () => import('@/views/dept/PeerEvaluation.vue'), meta: { title: '部门他评打分', roles: ['DEPT_ADMIN'] } },
      { path: 'dept/appeal-feedback', name: 'AppealFeedback', component: () => import('@/views/dept/AppealFeedback.vue'), meta: { title: '申诉反馈', roles: ['DEPT_ADMIN', 'SUPERVISOR'] } },
      { path: 'dept/result', name: 'DeptResult', component: () => import('@/views/dept/DeptResult.vue'), meta: { title: '考核结果查询', roles: ['DEPT_ADMIN'] } },
      { path: 'leader/indicator-approve', name: 'LeaderIndicatorApprove', component: () => import('@/views/leader/IndicatorApprove.vue'), meta: { title: '指标审批', roles: ['DEPT_LEADER', 'SUPERVISOR'] } },
      { path: 'leader/result', name: 'LeaderResult', component: () => import('@/views/leader/LeaderResult.vue'), meta: { title: '考核结果查看', roles: ['DEPT_LEADER'] } },
      { path: 'supervisor/eval-score', name: 'SupervisorEvalScore', component: () => import('@/views/supervisor/EvalScore.vue'), meta: { title: '评估打分', roles: ['SUPERVISOR'] } },
      { path: 'supervisor/progress', name: 'SupervisorProgress', component: () => import('@/views/supervisor/SupervisorProgress.vue'), meta: { title: '考核进度查询', roles: ['SUPERVISOR'] } },
      { path: 'supervisor/appeal-reeval', name: 'SupervisorAppealReeval', component: () => import('@/views/supervisor/AppealReeval.vue'), meta: { title: '申诉重新评估', roles: ['SUPERVISOR'] } },
      { path: 'supervisor/history', name: 'SupervisorHistory', component: () => import('@/views/supervisor/HistoryQuery.vue'), meta: { title: '历史考核查询', roles: ['SUPERVISOR'] } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function extractPaths(menus: any[]): string[] {
  const paths: string[] = []
  for (const menu of menus || []) {
    if (menu.menuPath) {
      paths.push(menu.menuPath)
    }
    if (menu.children?.length) {
      paths.push(...extractPaths(menu.children))
    }
  }
  return paths
}

router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    next()
    return
  }
  if (!token) {
    next('/login')
    return
  }
  if (to.path === '/' || to.path === '/dashboard') {
    next()
    return
  }

  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  userStore.ensureActiveRole()

  if (userStore.hasPathAccess(to.path)) {
    next()
    return
  }

  try {
    const { menuApi } = await import('@/api/admin')
    const res = await menuApi.current()
    const data = res.data || res
    userStore.setAllowedPaths(extractPaths(data))
    if (userStore.hasPathAccess(to.path)) {
      next()
      return
    }
  } catch (e) {
    console.error('获取菜单失败', e)
    // Network error — force re-login since we can't verify permissions
    const userStore = useUserStore()
    await userStore.logout()
    next('/login')
    return
  }

  ElMessage.error('无权访问该页面')
  next('/dashboard')
})

export default router
