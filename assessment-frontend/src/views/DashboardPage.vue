<template>
  <div class="dashboard-container">
    <div class="page-header">
      <h2>工作台</h2>
    </div>

    <el-card class="notification-card">
      <template #header>
        <div class="card-header">
          <span>通知中心</span>
          <el-badge v-if="unreadCount > 0" :value="unreadCount" class="unread-badge" />
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="未读通知" name="unread">
          <div v-if="unreadList.length === 0" class="empty-state">
            <el-empty description="暂无未读通知" />
          </div>
          <div v-else class="notification-list">
            <div v-for="item in unreadList" :key="item.id" class="notification-item unread">
              <div class="notification-content">
                <div class="notification-title">{{ item.title }}</div>
                <div class="notification-time">{{ formatTime(item.createdTime) }}</div>
              </div>
              <div class="notification-action">
                <el-button type="primary" size="small" link @click="handleNotificationClick(item)">
                  {{ item.linkText || '查看' }}
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="已读通知" name="read">
          <div v-if="readList.length === 0" class="empty-state">
            <el-empty description="暂无已读通知" />
          </div>
          <div v-else class="notification-list">
            <div v-for="item in readList" :key="item.id" class="notification-item read">
              <div class="notification-content">
                <div class="notification-title">{{ item.title }}</div>
                <div class="notification-time">{{ formatTime(item.createdTime) }}</div>
              </div>
              <div class="notification-action">
                <el-button v-if="item.linkUrl" size="small" link @click="navigateTo(item)">
                  {{ item.linkText || '查看' }}
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { notificationApi } from '@/api/admin'
import { ElMessage } from 'element-plus'

const router = useRouter()
const activeTab = ref('unread')
const unreadList = ref<any[]>([])
const readList = ref<any[]>([])
const unreadCount = ref(0)

onMounted(() => {
  loadUnreadNotifications()
  loadUnreadCount()
})

async function loadUnreadNotifications() {
  try {
    const res = await notificationApi.getUnread()
    unreadList.value = res.data || []
  } catch (e) {
    console.error('加载未读通知失败', e)
  }
}

async function loadReadNotifications() {
  try {
    const res = await notificationApi.getRead()
    readList.value = res.data || []
  } catch (e) {
    console.error('加载已读通知失败', e)
  }
}

async function loadUnreadCount() {
  try {
    const res = await notificationApi.getUnreadCount()
    unreadCount.value = res.data || 0
  } catch (e) {
    console.error('加载未读计数失败', e)
  }
}

function handleTabChange(tab: string | number) {
  if (tab === 'read' && readList.value.length === 0) {
    loadReadNotifications()
  }
}

async function handleNotificationClick(item: any) {
  try {
    await notificationApi.markRead(item.id)
    unreadList.value = unreadList.value.filter(n => n.id !== item.id)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    if (item.linkUrl) {
      router.push(item.linkUrl)
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function navigateTo(item: any) {
  if (item.linkUrl) {
    router.push(item.linkUrl)
  }
}

function formatTime(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}
.page-header {
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
.notification-card {
  max-width: 800px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: bold;
}
.notification-list {
  max-height: 500px;
  overflow-y: auto;
}
.notification-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  transition: background-color 0.2s;
}
.notification-item:hover {
  background-color: #f5f7fa;
}
.notification-item:last-child {
  border-bottom: none;
}
.notification-item.unread .notification-title {
  font-weight: 600;
}
.notification-item.read .notification-title {
  color: #909399;
}
.notification-title {
  font-size: 14px;
  line-height: 1.5;
}
.notification-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.notification-content {
  flex: 1;
}
.notification-action {
  margin-left: 16px;
  flex-shrink: 0;
}
.empty-state {
  padding: 40px 0;
}
</style>
