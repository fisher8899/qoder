<template>
  <div class="leader-result-page">
    <div class="page-header">
      <h2 class="page-title">考核结果查看 v1</h2>
    </div>

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
        <el-form-item label="月份">
          <el-date-picker
            v-model="queryForm.scoreMonth"
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

    <el-card shadow="never">
      <DataTable
        :columns="columns"
        :data="detailList"
        :loading="loading"
        :total="total"
        :current-page="queryForm.current"
        :page-size="queryForm.size"
        @page-change="handlePageChange"
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import DataTable from '@/components/DataTable.vue'
import { getDetailByOrg } from '@/api/result'
import { getExamGroupList } from '@/api/examGroup'
import { useUserStore } from '@/stores/user'
import type { ResultDetailItem } from '@/api/result'
import type { ExamGroup } from '@/api/types'

const userStore = useUserStore()
const loading = ref(false)
const detailList = ref<ResultDetailItem[]>([])
const total = ref(0)
const examGroupOptions = ref<ExamGroup[]>([])

const queryForm = reactive({
  examGroupId: undefined as number | undefined,
  scoreMonth: '',
  current: 1,
  size: 10
})

const columns = [
  { prop: 'categoryName', label: '指标大类', width: 120 },
  { prop: 'subCategory', label: '指标小类', width: 120 },
  { prop: 'content', label: '考核内容', minWidth: 200 },
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

async function loadExamGroups() {
  try {
    const res = await getExamGroupList({ current: 1, size: 999, examCategory: 'PERFORMANCE' })
    examGroupOptions.value = (res.data?.records || []) as ExamGroup[]
  } catch (e) {}
}

const allDetailList = ref<ResultDetailItem[]>([])

async function handleSearch() {
  const orgId = userStore.userInfo?.orgId
  if (!orgId) {
    ElMessage.warning('无法获取当前用户部门信息')
    return
  }
  if (!queryForm.examGroupId) {
    ElMessage.warning('请选择考核组')
    return
  }
  loading.value = true
  try {
    const res = await getDetailByOrg(queryForm.examGroupId, orgId)
    allDetailList.value = res.data || []
    total.value = allDetailList.value.length
    applyPage()
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

function applyPage() {
  const from = (queryForm.current - 1) * queryForm.size
  const to = from + queryForm.size
  detailList.value = allDetailList.value.slice(from, to)
}

function handlePageChange(page: number, size: number) {
  queryForm.current = page
  queryForm.size = size
  applyPage()
}

function handleReset() {
  queryForm.examGroupId = undefined
  queryForm.scoreMonth = ''
  queryForm.current = 1
  queryForm.size = 10
  detailList.value = []
}

onMounted(() => {
  loadExamGroups()
})
</script>

<style scoped lang="scss">
.leader-result-page {
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
