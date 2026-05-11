<template>
  <div class="supervisor-progress-page">
    <div class="page-header">
      <h2 class="page-title">考核进度查询 v1</h2>
    </div>

    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryForm" inline>
        <el-form-item label="考核组名称">
          <el-select v-model="queryForm.examGroupId" placeholder="请选择考核组" clearable style="width: 220px">
            <el-option
              v-for="item in examGroupOptions"
              :key="item.id"
              :label="item.groupName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考核部门">
          <el-select v-model="queryForm.orgId" placeholder="请选择部门" clearable style="width: 180px">
            <el-option
              v-for="item in supervisedOrgOptions"
              :key="item.id"
              :label="item.orgName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考核月份">
          <el-date-picker
            v-model="queryForm.period"
            type="month"
            placeholder="选择月份"
            value-format="YYYY-MM"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 汇总卡片 -->
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="summary-item">
            <div class="summary-label">总部门数</div>
            <div class="summary-value">{{ summary.total }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="summary-item">
            <div class="summary-label">已完成</div>
            <div class="summary-value" style="color: #67c23a">{{ summary.completed }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="summary-item">
            <div class="summary-label">进行中</div>
            <div class="summary-value" style="color: #e6a23c">{{ summary.inProgress }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="summary-item">
            <div class="summary-label">完成率</div>
            <div class="summary-value" style="color: #409eff">{{ summary.completionRate }}%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 进度表格 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="progressList" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="orgName" label="部门名称" min-width="160" />
        <el-table-column label="自评完成率" width="180">
          <template #default="scope">
            <el-progress :percentage="Number(scope.row.selfEvalRate) || 0" :color="progressColors" />
          </template>
        </el-table-column>
        <el-table-column label="他评完成率" width="180">
          <template #default="scope">
            <el-progress :percentage="Number(scope.row.peerEvalRate) || 0" :color="progressColors" />
          </template>
        </el-table-column>
        <el-table-column label="复核状态" width="100" align="center">
          <template #default="scope">
            <StatusTag :status="scope.row.reviewStatus" :status-map="reviewStatusMap" />
          </template>
        </el-table-column>
        <el-table-column label="整体进度" width="180">
          <template #default="scope">
            <el-progress :percentage="Number(scope.row.overallProgress) || 0" :color="progressColors" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleViewUnfilled(scope.row)">查看未完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 未完成明细弹框 -->
    <el-dialog v-model="dialogVisible" title="未完成指标明细" width="600px">
      <el-table :data="unfilledList" v-loading="dialogLoading" stripe border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="indicatorName" label="指标名称" min-width="200" />
        <el-table-column prop="stage" label="阶段" width="100" align="center">
          <template #default="scope">
            <el-tag size="small" :type="stageTagType(scope.row.stage)">{{ scope.row.stage }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="负责部门" width="140" />
      </el-table>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { getProgressList, getUnfilledItems } from '@/api/progress'
import { getExamGroupList } from '@/api/examGroup'
import { getOrganizationList } from '@/api/organization'
import { useUserStore } from '@/stores/user'
import type { ExamProgressItem, UnfilledItem } from '@/api/progress'
import type { ExamGroup, Organization } from '@/api/types'

const userStore = useUserStore()
const loading = ref(false)
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const progressList = ref<ExamProgressItem[]>([])
const unfilledList = ref<UnfilledItem[]>([])
const examGroupOptions = ref<ExamGroup[]>([])
const orgOptions = ref<Organization[]>([])

const queryForm = reactive({
  examGroupId: undefined as number | undefined,
  orgId: undefined as number | undefined,
  period: ''
})

const progressColors = [
  { color: '#f56c6c', percentage: 30 },
  { color: '#e6a23c', percentage: 70 },
  { color: '#67c23a', percentage: 100 }
]

const reviewStatusMap = {
  '已复核': { text: '已复核', type: 'success' as const },
  '待复核': { text: '待复核', type: 'warning' as const }
}

// 只显示分管的部门（简化：从所有组织中筛选，实际应从领导关系表获取）
const supervisedOrgOptions = computed(() => {
  const currentOrgId = userStore.userInfo?.orgId
  if (!currentOrgId) return orgOptions.value
  // 简化：分管领导能看到所有部门，实际业务中应限制为分管部门
  return orgOptions.value
})

const summary = computed(() => {
  const total = progressList.value.length
  const completed = progressList.value.filter(i => Number(i.overallProgress) >= 100).length
  const inProgress = total - completed
  const rate = total > 0 ? Math.round((completed / total) * 100) : 0
  return { total, completed, inProgress, completionRate: rate }
})

function stageTagType(stage: string) {
  if (stage === '自评') return 'warning'
  if (stage === '他评') return 'primary'
  return 'danger'
}

async function loadExamGroups() {
  try {
    const res = await getExamGroupList({ current: 1, size: 999 })
    examGroupOptions.value = (res.data?.records || []) as ExamGroup[]
  } catch (e) {}
}

async function loadOrgs() {
  try {
    const res = await getOrganizationList({ current: 1, size: 999 })
    orgOptions.value = (res.data?.records || []) as Organization[]
  } catch (e) {}
}

async function handleSearch() {
  if (!queryForm.examGroupId) {
    ElMessage.warning('请选择考核组')
    return
  }
  loading.value = true
  try {
    const res = await getProgressList({
      examGroupId: queryForm.examGroupId,
      orgId: queryForm.orgId,
      period: queryForm.period || undefined
    })
    progressList.value = res.data || []
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryForm.examGroupId = undefined
  queryForm.orgId = undefined
  queryForm.period = ''
  progressList.value = []
}

async function handleViewUnfilled(row: ExamProgressItem) {
  if (!queryForm.examGroupId) return
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const res = await getUnfilledItems(queryForm.examGroupId, row.orgId)
    unfilledList.value = res.data || []
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    dialogLoading.value = false
  }
}

onMounted(() => {
  loadExamGroups()
  loadOrgs()
})
</script>

<style scoped lang="scss">
.supervisor-progress-page {
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
.summary-row {
  margin-bottom: 16px;
}
.summary-item {
  text-align: center;
}
.summary-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.summary-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--text-primary);
}
.table-card {
  margin-bottom: 16px;
}
</style>
