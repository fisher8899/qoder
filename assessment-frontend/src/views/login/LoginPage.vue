<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1 class="system-title">中煤鄂能化综合考评管理系统</h1>
        <p class="system-subtitle">月度业绩考核管理</p>
      </div>

      <el-form :model="form" class="login-form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input
            v-model="form.username"
            placeholder="用户名"
            prefix-icon="User"
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
      </el-form>

      <el-button
        type="primary"
        size="large"
        class="login-btn"
        :disabled="!form.username || !form.password"
        :loading="loading"
        @click="handleLogin"
      >
        登录
      </el-button>

      <div class="test-accounts">
        <p class="test-title">测试账号（密码均为 123456）：</p>
        <div class="account-list">
          <span
            v-for="account in testAccounts"
            :key="account.username"
            class="account-item"
            @click="fillAccount(account.username)"
          >
            {{ account.username }} - {{ account.roleName }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/auth'
import type { UserInfo } from '@/api/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const testAccounts = [
  { username: 'admin', roleName: '系统管理员' },
  { username: 'wangfang', roleName: '考核管理员' },
  { username: 'zhaogang', roleName: '部门绩效管理员' },
  { username: 'zhangjg', roleName: '部门负责人' },
  { username: 'wangjg', roleName: '分管领导/考核员' }
]

function fillAccount(username: string) {
  form.username = username
  form.password = '123456'
}

async function handleLogin() {
  if (!form.username || !form.password) return

  loading.value = true
  try {
    const res = await login({
      username: form.username,
      password: form.password
    })
    const data = res.data
    if (!data) {
      ElMessage.error('登录失败')
      return
    }
    const token = data.token as string
    const userInfo = data.userInfo as UserInfo

    userStore.setToken(token)
    userStore.setUserInfo(userInfo)
    userStore.setMenus([])

    // 登录后初始化数据范围
    if (userInfo.availableRoles && userInfo.availableRoles.length > 0) {
      const currentRole = userInfo.availableRoles.find(r => r.roleCode === userInfo.roleCode)
        || userInfo.availableRoles[0]
      userStore.setDataScope(
        currentRole.dataScope || 'ALL',
        currentRole.scopeId || 0,
        currentRole.scopeName || '全部'
      )
    }

    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '登录失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--sidebar-bg) 0%, var(--primary-dark) 100%);
}

.login-card {
  width: 480px;
  padding: 48px 40px;
  background: var(--card-bg);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.system-title {
  font-size: 24px;
  font-weight: bold;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.system-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
}

.login-form {
  margin-bottom: 20px;
}

.login-btn {
  width: 100%;
}

.test-accounts {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border-color, #e4e7ed);
}

.test-title {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 10px;
  text-align: center;
}

.account-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.account-item {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-color, #f5f7fa);
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}
</style>
