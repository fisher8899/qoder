<template>
  <div class="indicator-progress-page">
    <div class="page-header">
      <h2 class="page-title">指标设定进度查询</h2>
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
        <el-form-item label="考核组织名称">
          <el-input v-model="queryForm.orgName" placeholder="请输入组织名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="queryForm.approvalStatus" placeholder="请选择状态" clearable style="width: 180px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待部门负责人审批" value="PENDING_DEPT_LEADER" />
            <el-option label="待分管领导审批" value="PENDING_SUPERVISOR" />
            <el-option label="待财务处审批" value="PENDING_FINANCE" />
            <el-option label="审批通过" value="APPROVED" />
            <el-option label="被退回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="progressList" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="orgName" label="考核组织名称" min-width="200" />
        <el-table-column prop="approvalStatus" label="审批状态" width="160" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.approvalStatus)">
              {{ getStatusLabel(row.approvalStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getIndicatorProgress } from '@/api/indicator'
import type { IndicatorProgressItem } from '@/api/indicator'
import { getExamGroupList } from '@/api/examGroup'
import type { ExamGroup } from '@/api/types'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const progressList = ref<IndicatorProgressItem[]>([])
const examGroupOptions = ref<ExamGroup[]>([])

const queryForm = reactive({
  examGroupId: undefined as number | undefined,
  orgName: '',
  approvalStatus: ''
})

// 审批状态映射
const statusMap: Record<string, { label: string; type: string }> = {
  'DRAFT': { label: '草稿', type: 'info' },
  'PENDING_DEPT_LEADER': { label: '待部门负责人审批', type: 'warning' },
  'PENDING_SUPERVISOR': { label: '待分管领导审批', type: 'warning' },
  'PENDING_FINANCE': { label: '待财务处审批', type: 'warning' },
  'APPROVED': { label: '审批通过', type: 'success' },
  'REJECTED': { label: '被退回', type: 'danger' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}

function getStatusType(status: string) {
  return statusMap[status]?.type || 'info'
}

async function loadExamGroups() {
  try {
    const res = await getExamGroupList({ current: 1, size: 999, examCategory: 'INDICATOR_SET' })
    examGroupOptions.value = (res.data?.records || []) as ExamGroup[]
  } catch (e) {
    console.error('加载考核组失败', e)
  }
}

async function loadProgress() {
  loading.value = true
  try {
    const params: any = {}
    if (queryForm.examGroupId) params.examGroupId = queryForm.examGroupId
    if (queryForm.orgName) params.orgName = queryForm.orgName
    if (queryForm.approvalStatus) params.approvalStatus = queryForm.approvalStatus

    const res = await getIndicatorProgress(params)
    progressList.value = res.data || []
  } catch (e) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadProgress()
}

function handleReset() {
  queryForm.examGroupId = undefined
  queryForm.orgName = ''
  queryForm.approvalStatus = ''
  loadProgress()
}

function handleViewDetail(row: IndicatorProgressItem) {
  router.push({
    path: '/dept/indicator-set',
    query: { examGroupId: String(row.examGroupId), orgId: String(row.orgId) }
  })
}

onMounted(async () => {
  await loadExamGroups()
  // 接收跳转传递的参数
  const queryExamGroupId = route.query.examGroupId
  const queryGroupName = route.query.groupName
  if (queryExamGroupId) {
    queryForm.examGroupId = Number(queryExamGroupId)
  } else if (queryGroupName) {
    // 根据名称查找对应的 ID
    const found = examGroupOptions.value.find(g => g.groupName === queryGroupName)
    if (found) {
      queryForm.examGroupId = found.id
    }
  }
  // 自动执行查询
  loadProgress()
})
</script>

<style scoped lang="scss">
.indicator-progress-page {
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
.table-card {
  margin-bottom: 16px;
}
</style>
