<template>
  <div class="main-layout">
    <!-- 左侧导航栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div v-if="!sidebarCollapsed">
          <div class="logo-text">月度业绩考核</div>
          <div class="role-text">{{ userRoleName }}</div>
        </div>
        <el-icon v-else size="24" color="#fff"><Menu /></el-icon>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
        background-color="transparent"
        text-color="var(--sidebar-text)"
        active-text-color="var(--sidebar-active-text)"
        class="sidebar-menu"
      >
        <template v-for="group in menuGroups" :key="group.title">
          <el-sub-menu v-if="group.children && group.children.length" :index="group.title">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item
              v-for="item in group.children"
              :key="item.path"
              :index="item.path"
              :route="{ path: item.path }"
            >
              <span>{{ item.title }}</span>
              <el-badge v-if="item.badge && item.badge > 0" :value="item.badge" class="menu-badge" />
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="group.path" :route="{ path: group.path }">
            <el-icon><component :is="group.icon" /></el-icon>
            <template #title>
              <span>{{ group.title }}</span>
            </template>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>

    <!-- 主容器 -->
    <div class="main-container">
      <!-- 顶部导航栏 -->
      <header class="top-header">
        <div class="header-left">
          <el-button text :icon="sidebarCollapsed ? Expand : Fold" @click="toggleSidebar" />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta?.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <ThemeSwitcher />
          <!-- 角色切换 -->
          <div v-if="userStore.userInfo?.availableRoles && userStore.userInfo.availableRoles.length > 0" class="role-switcher">
            <el-tag v-if="userStore.userInfo.availableRoles.length === 1" size="small" type="info">
              {{ formatRoleLabel(userStore.sortedAvailableRoles[0]) }}
            </el-tag>
            <el-select
              v-else
              :model-value="currentRoleKey"
              size="small"
              class="role-select"
              @change="handleRoleChange"
            >
              <el-option
                v-for="(role, index) in userStore.sortedAvailableRoles"
                :key="`${role.roleCode}_${role.scopeId || 0}_${index}`"
                :label="formatRoleLabel(role)"
                :value="`${role.roleCode}__${role.scopeId || 0}`"
              />
            </el-select>
          </div>
          <el-dropdown @command="handleUserCommand">
            <span class="user-info">
              <el-avatar :size="28" :icon="UserFilled" />
              <span class="username">{{ userName }}</span>
              <span class="userrole">({{ userRoleName }})</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 主内容区 -->
      <main class="content-area">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Menu, Fold, Expand, UserFilled, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { getPeerEvalTaskList } from '@/api/peerEval'
import { getPendingReevalList } from '@/api/appeal'
import { menuApi } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const userName = computed(() => userStore.userInfo?.userName || '未知用户')
const userRoleName = computed(() => userStore.userInfo?.roleName || '')
const activeMenu = computed(() => route.path)

function toggleSidebar() {
  appStore.toggleSidebar()
}

// 当前角色的复合key（roleCode__scopeId），用于select的model-value
const currentRoleKey = computed(() => {
  const roleCode = userStore.userInfo?.roleCode
  const scopeId = userStore.scopeId || 0
  return `${roleCode}__${scopeId}`
})

// 单位名称缩写映射（展示用，不影响数据）
function shortenScopeName(name: string): string {
  if (!name) return name
  const map: Record<string, string> = {
    '中煤鄂能化能源化工有限公司': '鄂能化',
  }
  return map[name] || name
}

function formatRoleLabel(role: { roleCode: string; roleName: string; dataScope?: string; scopeId?: number; scopeName?: string }) {
  if (!role.dataScope || role.dataScope === 'ALL') {
    return `${role.roleName}-全部`
  }
  // UNIT 或 ORG 时显示范围名称（使用缩写）
  const displayName = shortenScopeName(role.scopeName || '')
  return `${role.roleName}-${displayName}`
}

async function handleRoleChange(compositeKey: string) {
  // compositeKey 格式：roleCode__scopeId
  const [roleCode, scopeIdStr] = compositeKey.split('__')
  const scopeId = Number(scopeIdStr) || 0
  const switched = await userStore.switchRoleByScope(roleCode, scopeId)
  if (!switched) {
    return
  }

  if (route.path !== '/dashboard') {
    await router.replace('/dashboard')
  }
  window.location.reload()
}

function handleUserCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

const pendingEvalCount = ref(0)
const pendingAppealCount = ref(0)

function getCurrentRoleCode() {
  return userStore.userInfo?.roleCode || ''
}

function loadPendingEvalCount() {
  const roleCode = getCurrentRoleCode()
  if (roleCode !== 'DEPT_ADMIN') {
    pendingEvalCount.value = 0
    return
  }
  // 使用当前角色作用域 scopeId，而非用户原始 orgId，防止角色切换后不匹配
  const orgId = userStore.scopeId || userStore.userInfo?.orgId
  if (!orgId) return
  getPeerEvalTaskList({ evaluatorOrgId: orgId })
    .then((res: any) => {
      const tasks = res.data || []
      pendingEvalCount.value = tasks.filter((t: any) => t.status === 'PENDING').length
    })
    .catch(() => {})
}

function loadPendingAppealCount() {
  const roleCode = getCurrentRoleCode()
  if (roleCode !== 'SUPERVISOR') {
    pendingAppealCount.value = 0
    return
  }
  const orgId = userStore.userInfo?.orgId
  if (!orgId) return
  getPendingReevalList(orgId)
    .then((res: any) => {
      pendingAppealCount.value = (res.data || []).length
    })
    .catch(() => {})
}

// 菜单code与图标映射
const iconMap: Record<string, string> = {
  'SYS_SETTING': 'Setting',
  'EXAM_CONFIG': 'SetUp',
  'SYS_OPS': 'Tools',
  'EXAM_MGMT': 'Collection',
  'RESULT_MGMT': 'DataAnalysis',
  'DEPT_EXAM': 'EditPen',
  'DEPT_FEEDBACK': 'ChatDotSquare',
  'APPROVAL_MGMT': 'Stamp',
  'LEADER_RESULT': 'View',
  'SUPERVISOR_EVAL': 'Star',
  'SUPERVISOR_APPEAL': 'Warning',
  'SUPERVISOR_STAT': 'TrendCharts',
}

const transformMenuTree = (menus: any[]): any[] => {
  if (!menus || menus.length === 0) return []

  return menus.map(menu => {
    const item: any = {
      title: menu.menuName,
      path: menu.menuPath || undefined,
      icon: menu.menuIcon || iconMap[menu.menuCode] || undefined,
    }
    if (menu.children && menu.children.length > 0) {
      item.children = transformMenuTree(menu.children)
    }
    return item
  })
}

// 动态菜单数据
const menuGroups = ref<any[]>([])

const loadMenus = async () => {
  const role = userStore.userInfo?.roleCode
  if (!role) {
    menuGroups.value = []
    return
  }

  try {
    const res = await menuApi.current()
    const data = res.data || res
    const menus: any[] = [
      { title: '首页', path: '/dashboard', icon: 'HomeFilled' }
    ]
    menus.push(...transformMenuTree(data))
    menuGroups.value = menus

    // 提取所有菜单路径并缓存到 store，供路由守卫权限检查使用
    const extractPaths = (items: any[]): string[] => {
      const paths: string[] = []
      for (const item of items) {
        if (item.menuPath) paths.push(item.menuPath)
        if (item.children?.length) paths.push(...extractPaths(item.children))
      }
      return paths
    }
    userStore.setAllowedPaths(extractPaths(data))
  } catch (e) {
    console.error('加载菜单失败', e)
    menuGroups.value = [{ title: '首页', path: '/dashboard', icon: 'HomeFilled' }]
  }
}

onMounted(() => {
  // 初始化数据范围：如果 store 中没有则从当前角色获取
  if (!userStore.dataScope && userStore.userInfo?.availableRoles?.length) {
    const currentRoleCode = userStore.userInfo.roleCode
    const currentRole = userStore.userInfo.availableRoles.find(r => r.roleCode === currentRoleCode)
      || userStore.userInfo.availableRoles[0]
    userStore.setDataScope(
      currentRole.dataScope || 'ALL',
      currentRole.scopeId || 0,
      currentRole.scopeName || '全部'
    )
  }
  loadPendingEvalCount()
  loadPendingAppealCount()
  loadMenus()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/layout.scss';

.sidebar-menu {
  border-right: none;
  flex: 1;

  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    background-color: rgba(255, 255, 255, 0.05);
  }

  :deep(.el-menu-item.is-active) {
    background-color: var(--sidebar-active-bg);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.role-switcher {
  margin-right: 4px;
}

.role-select {
  width: 220px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;

  &:hover {
    background: var(--content-bg);
  }
}

.username {
  font-size: 14px;
  color: var(--text-primary);
}

.userrole {
  font-size: 12px;
  color: var(--text-secondary);
}

.menu-badge {
  margin-left: 8px;
  :deep(.el-badge__content) {
    border: none;
  }
}
</style>
