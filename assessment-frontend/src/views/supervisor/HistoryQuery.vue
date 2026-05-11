<template>
  <div class="history-query-page">
    <div class="page-header">
      <h2 class="page-title">历史考核查询 v1</h2>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :model="queryForm" inline>
        <el-form-item label="年份">
          <el-date-picker
            v-model="queryForm.year"
            type="year"
            placeholder="选择年份"
            value-format="YYYY"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="考核部门">
          <el-select v-model="queryForm.orgId" placeholder="请选择部门" clearable style="width: 180px">
            <el-option
              v-for="item in orgOptions"
              :key="item.id"
              :label="item.orgName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="historyList" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="groupName" label="考核组名称" min-width="180" />
        <el-table-column prop="examCategory" label="考核类型" width="120" />
        <el-table-column prop="startDate" label="考核月份" width="120">
          <template #default="scope">
            {{ scope.row.startDate ? scope.row.startDate.substring(0, 7) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="考核状态" width="100" align="center">
          <template #default="scope">
            <StatusTag :status="scope.row.status" :status-map="statusMap" />
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="100" align="center">
          <template #default="scope">
            <strong>{{ scope.row.totalScore }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleViewDetail(scope.row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情弹框 -->
    <el-dialog v-model="dialogVisible" title="考核明细得分" width="900px">
      <DataTable
        :columns="detailColumns"
        :data="detailList"
        :loading="detailLoading"
        :show-pagination="false"
      >
        <template #selfScore="{ row }">
          <span :class="scoreClass(row.selfScore)">{{ row.selfScore }}</span>
        </template>
        <template #peerScore="{ row }">
          <span :class="scoreClass(row.peerScore)">{{ row.peerScore }}</span>
        </template>
        <template #adminScore="{ row }">
          <span :class="scoreClass(row.adminScore)">{{ row.adminScore }}</span>
        </template>
        <template #finalScore="{ row }">
          <span :class="scoreClass(row.finalScore)">{{ row.finalScore }}</span>
        </template>
        <template #weightedScore="{ row }">
          <span :class="scoreClass(row.weightedScore)">{{ row.weightedScore }}</span>
        </template>
      </DataTable>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import DataTable from '@/components/DataTable.vue'
import { getHistoryList, getDetailByOrg } from '@/api/result'
import { getOrganizationList } from '@/api/organization'
import type { HistoryExamItem, ResultDetailItem } from '@/api/result'
import type { Organization } from '@/api/types'

const loading = ref(false)
const detailLoading = ref(false)
const dialogVisible = ref(false)
const historyList = ref<HistoryExamItem[]>([])
const detailList = ref<ResultDetailItem[]>([])
const orgOptions = ref<Organization[]>([])

const queryForm = reactive({
  year: '',
  orgId: undefined as number | undefined
})

const statusMap = {
  'PUBLISHED': { text: '已发布', type: 'success' as const },
  'COMPLETED': { text: '已完成', type: 'info' as const },
  'PRE_PUBLISHED': { text: '预发布', type: 'warning' as const }
}

const detailColumns = [
  { prop: 'categoryName', label: '指标大类', width: 120 },
  { prop: 'subCategory', label: '指标小类', width: 120 },
  { prop: 'content', label: '考核内容', minWidth: 180 },
  { prop: 'weightMonthly', label: '权重(月度%)', width: 110, align: 'center' as const },
  { prop: 'selfScore', label: '自评得分', width: 100, align: 'center' as const },
  { prop: 'peerScore', label: '他评得分', width: 100, align: 'center' as const },
  { prop: 'adminScore', label: '管理员打分', width: 110, align: 'center' as const },
  { prop: 'finalScore', label: '最终得分', width: 100, align: 'center' as const },
  { prop: 'weightedScore', label: '加权得分', width: 100, align: 'center' as const }
]

function scoreClass(score: number) {
  if (score == null) return ''
  const s = Number(score)
  if (s >= 80) return 'score-high'
  if (s >= 60) return 'score-mid'
  return 'score-low'
}

async function loadOrgs() {
  try {
    const res = await getOrganizationList({ current: 1, size: 999 })
    orgOptions.value = (res.data?.records || []) as Organization[]
  } catch (e) {}
}

async function handleSearch() {
  if (!queryForm.orgId) {
    ElMessage.warning('请选择考核部门')
    return
  }
  loading.value = true
  try {
    const res = await getHistoryList(queryForm.orgId, queryForm.year || undefined)
    historyList.value = res.data || []
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryForm.year = ''
  queryForm.orgId = undefined
  historyList.value = []
}

async function handleViewDetail(row: HistoryExamItem) {
  if (!queryForm.orgId) return
  dialogVisible.value = true
  detailLoading.value = true
  try {
    const res = await getDetailByOrg(row.examGroupId, queryForm.orgId)
    detailList.value = res.data || []
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadOrgs()
})
</script>

<style scoped lang="scss">
.history-query-page {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: bold;
  color: var(--text-primary);
}
.search-card {
  margin-bottom: 16px;
}
.score-high {
  color: #67c23a;
  font-weight: bold;
}
.score-mid {
  color: #e6a23c;
  font-weight: bold;
}
.score-low {
  color: #f56c6c;
  font-weight: bold;
}
</style>
