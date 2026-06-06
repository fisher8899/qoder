<template>
  <div class="exam-result-page">
    <div class="page-header">
      <h2 class="page-title">考核结果查询 v1</h2>
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
              v-for="item in orgOptions"
              :key="item.id"
              :label="item.orgName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="指标大类">
          <el-select v-model="queryForm.categoryId" placeholder="请选择大类" clearable style="width: 180px">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考核月份">
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
      <div class="export-btns">
        <el-button type="success" :icon="Download" @click="handleExportDetail">导出明细表</el-button>
        <el-button type="warning" :icon="Download" @click="handleExportSummary">导出汇总表</el-button>
      </div>
    </el-card>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="明细视图" name="detail">
        <DataTable
          :columns="detailColumns"
          :data="detailList"
          :loading="detailLoading"
          :total="detailTotal"
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
      </el-tab-pane>

      <el-tab-pane label="汇总视图" name="summary">
        <el-table :data="summaryList" v-loading="summaryLoading" stripe border>
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="orgName" label="部门名称" min-width="160" />
          <el-table-column
            v-for="cat in dynamicCategories"
            :key="cat"
            :prop="`categoryScores.${cat}`"
            :label="cat + '得分'"
            width="140"
            align="center"
          >
            <template #default="scope">
              {{ scope.row.categoryScores?.[cat] || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总分" width="120" align="center">
            <template #default="scope">
              <strong>{{ scope.row.totalScore }}</strong>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { getResultList, getResultSummary, exportDetailExcel, exportSummaryExcel } from '@/api/result'
import { getExamGroupList } from '@/api/examGroup'
import { getOrganizationList } from '@/api/organization'
import { indicatorCategoryApi } from '@/api/admin'
import type { ResultDetailItem, ResultSummaryItem } from '@/api/result'
import type { ExamGroup, Organization } from '@/api/types'

const activeTab = ref('detail')
const detailLoading = ref(false)
const summaryLoading = ref(false)
const detailList = ref<ResultDetailItem[]>([])
const detailTotal = ref(0)
const summaryList = ref<ResultSummaryItem[]>([])
const examGroupOptions = ref<ExamGroup[]>([])
const orgOptions = ref<Organization[]>([])
const categoryOptions = ref<any[]>([])

const queryForm = reactive({
  examGroupId: undefined as number | undefined,
  orgId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  scoreMonth: '',
  current: 1,
  size: 10
})

const detailColumns = [
  { prop: 'orgName', label: '部门', minWidth: 140 },
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

const dynamicCategories = computed(() => {
  const set = new Set<string>()
  summaryList.value.forEach(item => {
    if (item.categoryScores) {
      Object.keys(item.categoryScores).forEach(k => set.add(k))
    }
  })
  return Array.from(set)
})

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
    // 默认选中最新月份的考核组（第一个）
    if (examGroupOptions.value.length > 0 && queryForm.examGroupId === undefined) {
      queryForm.examGroupId = examGroupOptions.value[0].id
      handleSearch()
    }
  } catch (e) {}
}

async function loadOrgs() {
  try {
    const res = await getOrganizationList({ current: 1, size: 999 })
    orgOptions.value = (res.data?.records || []) as Organization[]
  } catch (e) {}
}

async function loadCategories() {
  try {
    const res = await indicatorCategoryApi.list({ current: 1, size: 999 })
    categoryOptions.value = (res.data?.records || []) as any[]
  } catch (e) {}
}

async function handleSearch() {
  if (!queryForm.examGroupId) {
    ElMessage.warning('请选择考核组')
    return
  }
  if (activeTab.value === 'detail') {
    await loadDetail()
  } else {
    await loadSummary()
  }
}

async function loadDetail() {
  detailLoading.value = true
  try {
    const res = await getResultList({
      examGroupId: queryForm.examGroupId!,
      orgId: queryForm.orgId,
      categoryId: queryForm.categoryId,
      scoreMonth: queryForm.scoreMonth || undefined,
      current: queryForm.current,
      size: queryForm.size
    })
    detailList.value = (res.data?.records || []) as ResultDetailItem[]
    detailTotal.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    const res = await getResultSummary(queryForm.examGroupId!)
    summaryList.value = res.data || []
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    summaryLoading.value = false
  }
}

function handlePageChange(page: number, size: number) {
  queryForm.current = page
  queryForm.size = size
  loadDetail()
}

function handleReset() {
  queryForm.examGroupId = undefined
  queryForm.orgId = undefined
  queryForm.categoryId = undefined
  queryForm.scoreMonth = ''
  queryForm.current = 1
  queryForm.size = 10
  detailList.value = []
  summaryList.value = []
}

async function handleExportDetail() {
  if (!queryForm.examGroupId) {
    ElMessage.warning('请选择考核组')
    return
  }
  try {
    const res = await exportDetailExcel(queryForm.examGroupId, queryForm.orgId)
    downloadBlob(res as any, '考核明细表.xlsx')
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

async function handleExportSummary() {
  if (!queryForm.examGroupId) {
    ElMessage.warning('请选择考核组')
    return
  }
  try {
    const res = await exportSummaryExcel(queryForm.examGroupId)
    downloadBlob(res as any, '考核汇总表.xlsx')
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

function downloadBlob(data: Blob, filename: string) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

onMounted(() => {
  loadExamGroups()
  loadOrgs()
  loadCategories()
})
</script>

<style scoped lang="scss">
.exam-result-page {
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
.export-btns {
  margin-top: 12px;
  text-align: right;
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
