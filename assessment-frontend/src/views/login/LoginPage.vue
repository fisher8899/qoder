<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1 class="system-title">综合考评管理系统</h1>
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

      <div class="test-accounts">
        <div class="test-accounts-title">测试账号</div>
        <button
          v-for="account in testAccounts"
          :key="account.username"
          type="button"
          class="test-account"
          @click="fillAccount(account)"
        >
          <span>{{ account.label }}</span>
          <strong>{{ account.username }} / {{ account.password }}</strong>
        </button>
      </div>

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

interface TestAccount {
  label: string
  username: string
  password: string
}

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const testAccounts: TestAccount[] = [
  { label: '系统管理员-全部', username: 'admin', password: '123456' },
  { label: '图克分公司管理员', username: 'tuke01', password: '123456' }
]

function fillAccount(account: TestAccount) {
  form.username = account.username
  form.password = account.password
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

    // 数据范围已由 setUserInfo -> ensureActiveRole 正确设置，
    // 不再用基础角色覆盖，避免切换角色后重新登录时范围回退到 ALL

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
  margin-bottom: 16px;
}

.test-accounts {
  margin-bottom: 20px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-color);
}

.test-accounts-title {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.test-account {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.test-account:hover {
  background: var(--hover-bg);
}

.test-account strong {
  font-size: 13px;
  white-space: nowrap;
}

.login-btn {
  width: 100%;
}
</style>
