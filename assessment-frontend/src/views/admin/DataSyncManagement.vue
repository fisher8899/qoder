<template>
  <div class="page-card">
    <h2 class="page-title">数据同步 v1</h2>
    <p class="page-subtitle">查看数据同步历史记录并执行手动同步</p>

    <!-- 顶部操作区 -->
    <div class="toolbar">
      <el-button type="primary" @click="handleManualSync">
        <el-icon><Refresh /></el-icon>手动同步
      </el-button>
    </div>

    <!-- 同步历史表格 -->
    <el-table :data="tableData" stripe v-loading="loading" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="syncType" label="同步类型" width="160">
        <template #default="{ row }">
          {{ syncTypeMap[row.syncType] || row.syncType || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="同步时间" width="160" />
      <el-table-column prop="totalCount" label="总条数" width="90" align="center" />
      <el-table-column prop="addCount" label="新增条数" width="90" align="center" />
      <el-table-column prop="updateCount" label="更新条数" width="90" align="center" />
      <el-table-column prop="failCount" label="失败条数" width="90" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
          <el-tag v-else-if="row.status === 'FAILED'" type="danger" size="small">失败</el-tag>
          <el-tag v-else-if="row.status === 'IN_PROGRESS'" type="primary" size="small">进行中</el-tag>
          <el-tag v-else type="info" size="small">{{ row.status || '—' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <span class="total-text">共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.size"
        :total="total"
        layout="prev, pager, next, jumper"
        @change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { dataSyncApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const syncTypeMap: Record<string, string> = {
  USER: '用户同步',
  ORG: '组织同步',
  DEPT: '部门同步',
  INDICATOR: '指标同步'
}

const queryParams = reactive({
  current: 1,
  size: 10
})

function loadData() {
  loading.value = true
  dataSyncApi.history({ ...queryParams })
    .then((res: any) => {
      tableData.value = res.data?.records || []
      total.value = res.data?.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  loadData()
}

function handleManualSync() {
  ElMessageBox.confirm('确认执行手动数据同步？', '提示', { type: 'warning' }).then(() => {
    dataSyncApi.manual().then(() => {
      ElMessage.success('同步任务已启动')
      loadData()
    })
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.toolbar {
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
.total-text {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
