<template>
  <div class="exam-progress-page">
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
              v-for="item in orgOptions"
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
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleViewDetail(scope.row)">查看</el-button>
            <el-button link type="primary" @click="handleViewUnfilled(scope.row)">查看未完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 评价详情全屏弹框 -->
    <el-dialog v-model="detailVisible" title="评价详情" fullscreen destroy-on-close>
      <div class="preview-header">
        <span>考核组：{{ detailGroupName }}</span>
        <span>部门：{{ detailOrgName }}</span>
      </div>
      <div class="preview-stats">
        <span>指标总数：{{ detailIndicatorList.length }}</span>
        <span>已填写完成情况：{{ detailFilledCount }}</span>
        <span>已填写得分：{{ detailScoredCount }}</span>
        <span>自评结果合计：{{ detailTotalSelfResult }}</span>
        <span v-if="showScoreReviewColumns">考核得分合计：{{ detailTotalExamScore }}</span>
      </div>
      <el-table :data="detailTableData" v-loading="detailLoading" border size="small" max-height="540" :span-method="detailSpanMethod">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="categoryName" label="指标大类" width="110" />
        <el-table-column prop="subCategory" label="指标小类" width="130" />
        <el-table-column prop="content" label="考核内容" min-width="160" show-overflow-tooltip />
        <el-table-column prop="targetDesc" label="指标/目标" min-width="130" show-overflow-tooltip />
        <el-table-column label="完成情况" min-width="180">
          <template #default="{ row }">
            <div style="white-space: pre-wrap;">{{ row.actualCompletion || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="考核标准" width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              :content="cleanStd(row.evaluationStandard)"
              placement="top"
              :disabled="cleanStd(row.evaluationStandard) === '-' || !isTextOverflow(cleanStd(row.evaluationStandard), 280)"
              popper-class="std-tooltip-popper"
              :show-after="300"
            >
              <div style="max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                {{ cleanStd(row.evaluationStandard) }}
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="权重(年/月)" width="110" align="center">
          <template #default="{ row }">{{ formatWeight(row.weightAnnual) }} / {{ formatWeight(row.weightMonthly) }}</template>
        </el-table-column>
        <el-table-column label="自评得分" width="90" align="center">
          <template #default="{ row }">{{ formatScore(row.selfScore) }}</template>
        </el-table-column>
        <el-table-column v-if="showScoreReviewColumns" label="考核得分" width="100" align="center">
          <template #default="{ row }">{{ formatScore(row.peerResult) }}</template>
        </el-table-column>
        <el-table-column v-if="showScoreReviewColumns" label="考核评语" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.peerComment || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="showScoreReviewColumns" label="管理员调整得分" width="130" align="center">
          <template #default="{ row }">{{ formatScore(row.adminScore) }}</template>
        </el-table-column>
        <el-table-column v-if="showScoreReviewColumns" label="调整说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.adjustComment || '-' }}</template>
        </el-table-column>
        <el-table-column label="自评结果" width="100" align="center">
          <template #default="{ row }"><span class="result-text">{{ formatResult(row.selfResult) }}</span></template>
        </el-table-column>
        <el-table-column label="附件" width="100">
          <template #default="{ row }">
            <a v-if="row.attachmentName && getAttachmentHref(row)" :href="getAttachmentHref(row)" target="_blank" class="file-link">查看</a>
            <span v-else-if="row.attachmentName" class="file-name">{{ row.attachmentName }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 未完成明细弹框 -->
    <el-dialog v-model="dialogVisible" title="未完成评价指标明细" width="680px">
      <el-table :data="unfilledList" v-loading="dialogLoading" stripe border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="indicatorName" label="指标名称" min-width="200" />
        <el-table-column prop="stage" label="阶段" width="100" align="center">
          <template #default="scope">
            <el-tag size="small" :type="stageTagType(scope.row.stage)">{{ scope.row.stage }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="待评价部门" width="180">
          <template #default="{ row }">{{ row.evaluateTargetName || row.orgName || '-' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { getProgressList, getProgressDetail, getUnfilledItems } from '@/api/progress'
import { getExamGroupList } from '@/api/examGroup'
import { getOrganizationList } from '@/api/organization'
import { useUserStore } from '@/stores/user'
import type { ExamProgressDetailItem, ExamProgressItem, UnfilledItem } from '@/api/progress'
import type { ExamGroup } from '@/api/types'
import type { Organization } from '@/api/types'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const progressList = ref<ExamProgressItem[]>([])
const unfilledList = ref<UnfilledItem[]>([])
const examGroupOptions = ref<ExamGroup[]>([])
const orgOptions = ref<Organization[]>([])

// 评价详情弹框状态
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailOrgName = ref('')
const detailIndicatorList = ref<ExamProgressDetailItem[]>([])

const queryForm = reactive({
  examGroupId: undefined as number | undefined,
  orgId: undefined as number | undefined,
  period: ''
})

const defaultExamGroupId = ref<number | undefined>(undefined)

const progressColors = [
  { color: '#f56c6c', percentage: 30 },
  { color: '#e6a23c', percentage: 70 },
  { color: '#67c23a', percentage: 100 }
]

const reviewStatusMap = {
  '已复核': { text: '已复核', type: 'success' as const },
  '待复核': { text: '待复核', type: 'warning' as const }
}

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
    const res = await getExamGroupList({ current: 1, size: 999, examCategory: 'PERFORMANCE' })
    examGroupOptions.value = (res.data?.records || []) as ExamGroup[]
    defaultExamGroupId.value = examGroupOptions.value[0]?.id
    const routeExamGroupId = Number(route.query.examGroupId)
    const selectedExamGroupId = examGroupOptions.value.some(group => group.id === routeExamGroupId)
      ? routeExamGroupId
      : defaultExamGroupId.value
    // 从考核组管理页进入时定位指定考核组，否则默认选中最新月份的考核组。
    if (selectedExamGroupId && queryForm.examGroupId === undefined) {
      queryForm.examGroupId = selectedExamGroupId
      await handleSearch()
    }
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
  queryForm.examGroupId = defaultExamGroupId.value
  queryForm.orgId = undefined
  queryForm.period = ''
  handleSearch()
}

// ===== 评价详情弹框逻辑 =====

const detailGroupName = computed(() => {
  const group = examGroupOptions.value.find(g => g.id === queryForm.examGroupId)
  return group?.groupName || ''
})

const detailTableData = computed(() => {
  if (!detailIndicatorList.value.length) return []
  const sorted = [...detailIndicatorList.value].sort((a, b) => {
    const categorySortA = a.sortCode ?? Number.MAX_SAFE_INTEGER
    const categorySortB = b.sortCode ?? Number.MAX_SAFE_INTEGER
    if (categorySortA !== categorySortB) return categorySortA - categorySortB
    const catA = a.categoryName || ''
    const catB = b.categoryName || ''
    if (catA !== catB) return catA.localeCompare(catB)
    const subA = a.subCategory || ''
    const subB = b.subCategory || ''
    if (subA !== subB) return subA.localeCompare(subB)
    return (a.content || '').localeCompare(b.content || '')
  })
  return sorted
})

const currentExamGroup = computed(() =>
  examGroupOptions.value.find(group => group.id === queryForm.examGroupId)
)

const showScoreReviewColumns = computed(() => {
  const roleCode = userStore.activeRoleCode || userStore.userInfo?.roleCode || ''
  if (roleCode === 'DEPT_ADMIN') {
    return currentExamGroup.value?.status === 'PRE_PUBLISHED'
  }
  return ['DEPT_LEADER', 'SUPERVISOR', 'FIN_ADMIN'].includes(roleCode)
})

const detailFilledCount = computed(() =>
  detailIndicatorList.value.filter(row => !!row.actualCompletion?.trim()).length
)

const detailScoredCount = computed(() =>
  detailIndicatorList.value.filter(row => row.selfScore !== null && row.selfScore !== undefined).length
)

const detailTotalSelfResult = computed(() => {
  const vetoRows = detailIndicatorList.value.filter(r => r.categoryName === '否决项目')
  const hasVeto = vetoRows.some(r => Number(r.selfScore || 0) > 0)
  if (hasVeto) return '0.00'
  const totalValue = detailIndicatorList.value.reduce((sum, row) => {
    return sum + Number(row.selfResult || 0)
  }, 0)
  return totalValue.toFixed(2)
})

const detailTotalExamScore = computed(() => {
  const totalValue = detailIndicatorList.value.reduce((sum, row) => {
    return sum + Number(row.peerResult || 0)
  }, 0)
  return totalValue.toFixed(2)
})

function normalizeStd(text: string | undefined | null): string {
  if (!text) return ''
  return text
    .replace(/["'\\`\u201c\u201d\u2018\u2019\u300c\u300d\u300e\u300f]/g, '')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function cleanStd(text: string | undefined | null): string {
  return normalizeStd(text) || '-'
}

function isTextOverflow(text: string | undefined, maxWidth: number): boolean {
  if (!text) return false
  const estimatedWidth = Array.from(text).reduce((w, ch) => {
    return w + (ch.charCodeAt(0) > 127 ? 14 : 7)
  }, 0)
  return estimatedWidth > maxWidth
}

function formatWeight(val: number | null) {
  if (val === null || val === undefined) return '-'
  return `${Number(val)}%`
}

function formatScore(val: number | null) {
  if (val === null || val === undefined) return '-'
  return Number(val).toFixed(2)
}

function formatResult(val: number | null) {
  if (val === null || val === undefined) return '-'
  return Number(val).toFixed(2)
}

function getAttachmentHref(row: ExamProgressDetailItem) {
  if (row.attachmentDownloadUrl) return row.attachmentDownloadUrl
  if (row.attachmentUrl && row.selfEvalId) {
    return `/api/evaluation/self/download/${row.selfEvalId}`
  }
  return ''
}

function detailSpanMethod({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }) {
  const data = detailTableData.value
  if (!data.length) return { rowspan: 1, colspan: 1 }
  const currentRow = data[rowIndex]

  const getMergeValue = (row: ExamProgressDetailItem) => {
    if (columnIndex === 1) return row.categoryName || ''
    if (columnIndex === 2) return row.subCategory || ''
    if (columnIndex === 6) return normalizeStd(row.evaluationStandard)
    return ''
  }

  if ([1, 2, 6].includes(columnIndex)) {
    const currentValue = getMergeValue(currentRow)
    if (!currentValue) return { rowspan: 1, colspan: 1 }
    let start = rowIndex
    while (start > 0 && getMergeValue(data[start - 1]) === currentValue) start--
    let end = rowIndex
    while (end < data.length - 1 && getMergeValue(data[end + 1]) === currentValue) end++
    const rowspan = end - start + 1
    if (rowIndex === start) return { rowspan, colspan: 1 }
    return { rowspan: 0, colspan: 1 }
  }
  return { rowspan: 1, colspan: 1 }
}

async function handleViewDetail(row: ExamProgressItem) {
  if (!queryForm.examGroupId) {
    ElMessage.warning('请先选择考核组再查看')
    return
  }
  detailVisible.value = true
  detailLoading.value = true
  detailOrgName.value = row.orgName
  try {
    const res = await getProgressDetail(queryForm.examGroupId, row.orgId)
    detailIndicatorList.value = res.data || []
  } catch (error) {
    ElMessage.error('加载考核数据失败')
  } finally {
    detailLoading.value = false
  }
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
.exam-progress-page {
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
.preview-header {
  display: flex;
  gap: 24px;
  font-size: 14px;
  margin-bottom: 12px;
  color: var(--text-primary);
  font-weight: 500;
}
.preview-stats {
  display: flex;
  gap: 24px;
  font-size: 13px;
  margin-bottom: 16px;
  color: var(--text-secondary);
}
.file-link {
  color: #409eff;
  text-decoration: none;
  &:hover {
    text-decoration: underline;
  }
}
.file-name {
  color: var(--text-secondary);
  font-size: 12px;
}
.result-text {
  font-weight: 600;
}
</style>
