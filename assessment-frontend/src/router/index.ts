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
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardPage.vue'),
        meta: { title: '首页' }
      },
      // ===== 系统管理员路由 =====
      {
        path: 'admin/unit',
        name: 'UnitManagement',
        component: () => import('@/views/admin/UnitManagement.vue'),
        meta: { title: '单位管理', roles: ['ADMIN', 'FIN_ADMIN'] }
      },
      {
        path: 'admin/employee',
        name: 'EmployeeManagement',
        component: () => import('@/views/admin/EmployeeManagement.vue'),
        meta: { title: '人员管理', roles: ['ADMIN', 'FIN_ADMIN'] }
      },
      {
        path: 'admin/user',
        name: 'UserManagement',
        component: () => import('@/views/admin/UserManagement.vue'),
        meta: { title: '系统用户管理', roles: ['ADMIN', 'FIN_ADMIN'] }
      },
      {
        path: 'admin/leader',
        name: 'LeaderManagement',
        component: () => import('@/views/admin/LeaderManagement.vue'),
        meta: { title: '分管领导维护', roles: ['ADMIN', 'FIN_ADMIN'] }
      },
      {
        path: 'admin/permission',
        name: 'PermissionManagement',
        component: () => import('@/views/admin/PermissionManagement.vue'),
        meta: { title: '权限分配管理', roles: ['ADMIN', 'FIN_ADMIN'] }
      },
      {
        path: 'admin/organization',
        name: 'OrganizationManagement',
        component: () => import('@/views/admin/OrganizationManagement.vue'),
        meta: { title: '考核组织管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/indicator-category',
        name: 'IndicatorCategoryManagement',
        component: () => import('@/views/admin/IndicatorCategoryManagement.vue'),
        meta: { title: '指标大类管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/menu',
        name: 'MenuManagement',
        component: () => import('@/views/admin/MenuManagement.vue'),
        meta: { title: '功能/菜单定义', roles: ['ADMIN'] }
      },
      {
        path: 'admin/role',
        name: 'RoleManagement',
        component: () => import('@/views/admin/RoleManagement.vue'),
        meta: { title: '职责定义', roles: ['ADMIN'] }
      },
      {
        path: 'admin/data-sync',
        name: 'DataSync',
        component: () => import('@/views/admin/DataSyncManagement.vue'),
        meta: { title: '数据同步', roles: ['ADMIN'] }
      },
      // ===== 考核管理员路由 =====
      {
        path: 'exam/group',
        name: 'ExamGroupManagement',
        component: () => import('@/views/exam/ExamGroupManagement.vue'),
        meta: { title: '考核组管理', roles: ['FIN_ADMIN'] }
      },
      {
        path: 'exam/indicator-approval',
        name: 'IndicatorApproval',
        component: () => import('@/views/exam/IndicatorApproval.vue'),
        meta: { title: '业绩指标审批', roles: ['FIN_ADMIN'] }
      },
      {
        path: 'exam/monthly',
        name: 'MonthlyExam',
        component: () => import('@/views/exam/MonthlyExamManagement.vue'),
        meta: { title: '月度考核管理', roles: ['FIN_ADMIN'] }
      },
      {
        path: 'exam/review',
        name: 'ReviewEvaluation',
        component: () => import('@/views/exam/ReviewEvaluation.vue'),
        meta: { title: '复核评估', roles: ['FIN_ADMIN'] }
      },
      {
        path: 'exam/appeal',
        name: 'AppealManagement',
        component: () => import('@/views/exam/AppealManagement.vue'),
        meta: { title: '申诉管理', roles: ['FIN_ADMIN'] }
      },
      {
        path: 'exam/indicator-progress',
        name: 'IndicatorProgress',
        component: () => import('@/views/exam/IndicatorProgress.vue'),
        meta: { title: '指标设定进度查询', roles: ['FIN_ADMIN'] }
      },
      {
        path: 'exam/progress',
        name: 'ExamProgress',
        component: () => import('@/views/exam/ExamProgress.vue'),
        meta: { title: '考核进度查询', roles: ['FIN_ADMIN', 'DEPT_ADMIN'] }
      },
      {
        path: 'exam/result',
        name: 'ExamResult',
        component: () => import('@/views/exam/ExamResult.vue'),
        meta: { title: '考核结果查询', roles: ['FIN_ADMIN', 'DEPT_ADMIN', 'DEPT_LEADER'] }
      },
      // ===== 部门绩效管理员路由 =====
      {
        path: 'dept/indicator-set',
        name: 'IndicatorSet',
        component: () => import('@/views/dept/IndicatorSet.vue'),
        meta: { title: '业绩指标设定', roles: ['DEPT_ADMIN'] }
      },
      {
        path: 'dept/self-eval',
        name: 'SelfEvaluation',
        component: () => import('@/views/dept/SelfEvaluation.vue'),
        meta: { title: '月度考核自评', roles: ['DEPT_ADMIN'] }
      },
      {
        path: 'dept/peer-eval',
        name: 'PeerEvaluation',
        component: () => import('@/views/dept/PeerEvaluation.vue'),
        meta: { title: '部门他评打分', roles: ['DEPT_ADMIN'] }
      },
      {
        path: 'dept/appeal-feedback',
        name: 'AppealFeedback',
        component: () => import('@/views/dept/AppealFeedback.vue'),
        meta: { title: '申诉反馈', roles: ['DEPT_ADMIN', 'SUPERVISOR'] }
      },
      {
        path: 'dept/result',
        name: 'DeptResult',
        component: () => import('@/views/dept/DeptResult.vue'),
        meta: { title: '考核结果查询', roles: ['DEPT_ADMIN'] }
      },
      // ===== 部门负责人路由 =====
      {
        path: 'leader/indicator-approve',
        name: 'LeaderIndicatorApprove',
        component: () => import('@/views/leader/IndicatorApprove.vue'),
        meta: { title: '指标审批', roles: ['DEPT_LEADER'] }
      },
      {
        path: 'leader/result',
        name: 'LeaderResult',
        component: () => import('@/views/leader/LeaderResult.vue'),
        meta: { title: '考核结果查看', roles: ['DEPT_LEADER'] }
      },
      // ===== 分管领导路由 =====
      {
        path: 'supervisor/eval-score',
        name: 'SupervisorEvalScore',
        component: () => import('@/views/supervisor/EvalScore.vue'),
        meta: { title: '评估打分', roles: ['SUPERVISOR'] }
      },
      {
        path: 'supervisor/progress',
        name: 'SupervisorProgress',
        component: () => import('@/views/supervisor/SupervisorProgress.vue'),
        meta: { title: '考核进度查询', roles: ['SUPERVISOR'] }
      },
      {
        path: 'supervisor/appeal-reeval',
        name: 'SupervisorAppealReeval',
        component: () => import('@/views/supervisor/AppealReeval.vue'),
        meta: { title: '申诉重新评估', roles: ['SUPERVISOR'] }
      },
      {
        path: 'supervisor/history',
        name: 'SupervisorHistory',
        component: () => import('@/views/supervisor/HistoryQuery.vue'),
        meta: { title: '历史考核查询', roles: ['SUPERVISOR'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('token')

  // 登录页无需检查
  if (to.path === '/login') {
    next()
    return
  }

  // 无 token 跳转登录
  if (!token) {
    next('/login')
    return
  }

  // 首页/dashboard 所有角色都能访问
  if (to.path === '/' || to.path === '/dashboard') {
    next()
    return
  }

  // 获取 userStore
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  const roleCode = userStore.userInfo?.roleCode

  // 检查路由 meta.roles（兼容兆底）
  const roles = to.meta?.roles as string[] | undefined
  if (roles && roles.length > 0 && roleCode && roles.includes(roleCode)) {
    next()
    return
  }

  // 检查动态菜单权限（store 缓存）
  if (userStore.allowedPaths && userStore.allowedPaths.length > 0) {
    if (userStore.allowedPaths.includes(to.path)) {
      next()
      return
    }
  } else if (roleCode) {
    // allowedPaths 为空（可能首次加载），尝试从 API 获取
    try {
      const { menuApi } = await import('@/api/admin')
      const res = await menuApi.getByRole(roleCode)
      const data = res.data || res
      const extractPaths = (menus: any[]): string[] => {
        const paths: string[] = []
        for (const menu of menus) {
          if (menu.menuPath) paths.push(menu.menuPath)
          if (menu.children?.length) paths.push(...extractPaths(menu.children))
        }
        return paths
      }
      const paths = extractPaths(data)
      userStore.setAllowedPaths(paths)
      if (paths.includes(to.path)) {
        next()
        return
      }
    } catch (e) {
      console.error('权限检查失败', e)
    }
  }

  // 无权限
  ElMessage.error('无权访问该页面')
  next('/dashboard')
})

export default router
