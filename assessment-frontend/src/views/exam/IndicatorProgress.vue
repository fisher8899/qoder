<template>
  <div class="indicator-progress-page">
    <div class="page-header">
      <h2 class="page-title">指标设定进度查询</h2>
    </div>

    <el-card class="search-card" shadow="never">
      <div class="search-card-header">
        <span class="search-card-title">查询条件</span>
        <el-button link type="primary" @click="queryExpanded = !queryExpanded">
          {{ queryExpanded ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-form v-show="queryExpanded" :model="queryForm" inline>
        <el-form-item label="考核组名称">
          <el-select
            v-model="queryForm.examGroupId"
            placeholder="请选择考核组"
            clearable
            style="width: 320px"
            @change="handleExamGroupChange"
          >
            <el-option
              v-for="item in examGroupOptions"
              :key="item.id"
              :label="item.groupName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考核组织名称">
          <el-input
            v-model="queryForm.orgName"
            placeholder="请输入组织名称"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select
            v-model="queryForm.approvalStatus"
            placeholder="请选择审批状态"
            clearable
            style="width: 220px"
          >
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待部门负责人审批" value="PENDING_DEPT_LEADER" />
            <el-option label="待分管领导审批" value="PENDING_SUPERVISOR" />
            <el-option label="待财务处审批" value="PENDING_FINANCE" />
            <el-option label="审批通过" value="APPROVED" />
            <el-option label="已退回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="progressInfo.total > 0" class="progress-card" shadow="never">
      <div class="progress-summary">
        <span class="progress-summary-label">完成进度:</span>
        <ProgressBar
          :percentage="progressInfo.percentage"
          :label="`${progressInfo.approved}/${progressInfo.total}（${formatFixedPercent(progressInfo.percentage)}）`"
          style="flex: 1"
        />
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-card-content">
        <div class="table-wrapper">
          <el-table
            :data="pagedProgressList"
            v-loading="loading"
            stripe
            border
            height="100%"
          >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="orgName" label="考核组织名称" min-width="220" />
        <el-table-column prop="approvalStatus" label="审批状态" width="180" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.approvalStatus)">
              {{ getStatusLabel(row.approvalStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="指标数" width="100" align="center">
          <template #default="{ row }">
            {{ row.totalCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="已审批指标数" width="120" align="center">
          <template #default="{ row }">
            {{ row.approvedCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
          </el-table>
        </div>

        <div class="table-pagination">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            background
            layout="total, sizes, prev, pager, next"
            :total="progressList.length"
            :page-sizes="[5, 10, 20, 50]"
            @size-change="handlePageSizeChange"
            @current-change="handleCurrentPageChange"
          />
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="detailDialogVisible"
      title="业绩指标详情"
      fullscreen
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div v-if="detailContext.orgName" class="approval-header">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="考核组">{{ detailContext.groupName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="考核部门">{{ detailContext.orgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">
            <el-tag :type="getStatusType(detailContext.approvalStatus)">
              {{ getStatusLabel(detailContext.approvalStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交人">{{ detailContext.submittedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(detailContext.submittedTime) }}</el-descriptions-item>
          <el-descriptions-item label="最近操作时间">{{ formatDateTime(detailContext.approvedTime) }}</el-descriptions-item>
          <el-descriptions-item label="指标总数">{{ previewData.length }}</el-descriptions-item>
          <el-descriptions-item label="已审批指标数">{{ approvedIndicatorCount }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="approval-steps">
        <div
          v-for="(step, idx) in approvalSteps"
          :key="step"
          class="step-item"
          :class="{
            'step-done': getStepIndex(detailContext.approvalStatus) > idx,
            'step-current': getStepIndex(detailContext.approvalStatus) === idx,
            'step-pending': getStepIndex(detailContext.approvalStatus) < idx,
            'step-rejected': detailContext.approvalStatus === 'REJECTED' && idx === getStepIndex(detailContext.approvalStatus)
          }"
        >
          <span class="step-dot">{{ idx + 1 }}</span>
          <span class="step-text">{{ step }}</span>
          <el-button
            v-if="idx === approvalSteps.length - 1 && approvalRecords.length > 0"
            link
            type="primary"
            class="approval-record-link"
            @click.stop="recordDialogVisible = true"
          >
            审批记录
          </el-button>
          <span v-if="idx < approvalSteps.length - 1" class="step-arrow">→</span>
        </div>
      </div>

      <el-card class="indicator-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">业绩指标信息</span>
            <span class="weight-summary">
              权重合计(年度)：{{ formatSummaryPercent(weightAnnualTotal) }}
              &nbsp;&nbsp;&nbsp;&nbsp;
              权重合计(月度)：{{ formatSummaryPercent(weightMonthlyTotal) }}
            </span>
          </div>
        </template>
        <el-table
          v-loading="detailLoading"
          :data="previewData"
          border
          size="small"
          max-height="540"
          class="indicator-preview-table"
          :span-method="previewSpanMethod"
        >
          <el-table-column prop="categoryName" label="指标大类" width="120" align="center" />
          <el-table-column prop="subCategory" label="指标小类" width="120" align="center" />
          <el-table-column prop="content" label="考核内容" min-width="180" />
          <el-table-column prop="targetDesc" label="指标/目标" min-width="150" />
          <el-table-column prop="weightAnnual" label="权重(年度)" width="100" align="center">
            <template #default="{ row }">{{ formatPercent(row.weightAnnual) }}</template>
          </el-table-column>
          <el-table-column prop="weightMonthly" label="权重(月度)" width="100" align="center">
            <template #default="{ row }">{{ formatPercent(row.weightMonthly) }}</template>
          </el-table-column>
          <el-table-column prop="evaluationStandard" label="考核标准" min-width="200" align="center">
            <template #default="{ row }">
              <el-tooltip
                :content="row.evaluationStandard"
                placement="top"
                :disabled="!row.evaluationStandard"
                effect="light"
                popper-class="preview-tooltip"
              >
                <span class="preview-text">{{ row.evaluationStandard || '-' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="考核部门/分管领导" width="180">
            <template #default="{ row }">
              <span v-if="row.examTargetType === 'LEADER' && row.leaderNames?.length">
                {{ row.leaderNames.join('、') }}
              </span>
              <span v-else>{{ row.orgNames?.join('、') || row.orgName || detailContext.orgName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" width="120" />
        </el-table>
      </el-card>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="recordDialogVisible"
      title="审批记录"
      width="680px"
      append-to-body
      destroy-on-close
    >
      <el-timeline v-if="approvalRecords.length > 0">
        <el-timeline-item
          v-for="(item, idx) in approvalRecords"
          :key="idx"
          :type="item.type"
          :timestamp="formatDateTime(item.time)"
          placement="top"
        >
          <div class="record-action">{{ item.action }}</div>
          <div v-if="item.operator" class="record-operator">操作人：{{ item.operator }}</div>
          <div v-if="item.remark" class="record-remark">{{ item.remark }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无审批记录" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getIndicatorList, getIndicatorProgress } from '@/api/indicator'
import type { IndicatorProgressItem } from '@/api/indicator'
import { getExamGroupList } from '@/api/examGroup'
import { indicatorCategoryApi } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import type { ExamGroup } from '@/api/types'
import ProgressBar from '@/components/ProgressBar.vue'

type StatusMeta = {
  label: string
  type: 'success' | 'warning' | 'info' | 'danger' | 'primary'
}

interface ApprovalRecord {
  action: string
  operator?: string
  remark?: string
  time?: string | null
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const progressList = ref<IndicatorProgressItem[]>([])
const examGroupOptions = ref<ExamGroup[]>([])
const defaultExamGroupId = ref<number | undefined>()
const categorySortMap = ref<Map<string, number>>(new Map())
const queryExpanded = ref(true)

const queryForm = reactive({
  examGroupId: undefined as number | undefined,
  orgName: '',
  approvalStatus: ''
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 5
})

const progressInfo = reactive({
  approved: 0,
  total: 0,
  percentage: 0
})

const statusMap: Record<string, StatusMeta> = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_DEPT_LEADER: { label: '待部门负责人审批', type: 'warning' },
  PENDING_SUPERVISOR: { label: '待分管领导审批', type: 'warning' },
  PENDING_FINANCE: { label: '待财务处审批', type: 'warning' },
  APPROVED: { label: '审批通过', type: 'success' },
  REJECTED: { label: '已退回', type: 'danger' }
}

const approvalSteps = ['草稿', '部门负责人审批', '分管领导审批', '财务处审批', '审批通过']

const detailDialogVisible = ref(false)
const recordDialogVisible = ref(false)
const detailLoading = ref(false)
const previewData = ref<any[]>([])
const previewMergeMap = ref<Map<string, { rowspan: number; colspan: number }>>(new Map())
const detailContext = reactive({
  examGroupId: 0,
  orgId: 0,
  groupName: '',
  orgName: '',
  approvalStatus: '',
  submittedBy: '',
  submittedTime: '' as string | null,
  approvedBy: '',
  approvedTime: '' as string | null,
  rejectReason: ''
})

const approvedIndicatorCount = computed(
  () => previewData.value.filter(item => item.approvalStatus === 'APPROVED').length
)

const weightAnnualTotal = computed(() =>
  previewData.value.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0)
)

const weightMonthlyTotal = computed(() =>
  previewData.value.reduce((sum, item) => sum + (Number(item.weightMonthly) || 0), 0)
)

const pagedProgressList = computed(() => {
  const start = (pagination.currentPage - 1) * pagination.pageSize
  const end = start + pagination.pageSize
  return progressList.value.slice(start, end)
})

const approvalRecords = computed<ApprovalRecord[]>(() => {
  const records: ApprovalRecord[] = []
  const status = detailContext.approvalStatus
  if (!status || status === 'NOT_STARTED') {
    return records
  }
  if (detailContext.submittedTime) {
    records.push({
      action: '指标提交审批',
      operator: detailContext.submittedBy || '-',
      time: detailContext.submittedTime,
      type: 'primary'
    })
  }
  if (status === 'REJECTED') {
    records.push({
      action: '审批退回',
      operator: detailContext.approvedBy || '-',
      remark: detailContext.rejectReason ? `退回说明：${detailContext.rejectReason}` : '',
      time: detailContext.approvedTime,
      type: 'danger'
    })
  } else if (status === 'PENDING_SUPERVISOR') {
    records.push({
      action: '部门负责人审批通过',
      operator: detailContext.approvedBy || '-',
      time: detailContext.approvedTime,
      type: 'success'
    })
  } else if (status === 'PENDING_FINANCE') {
    records.push({
      action: '分管领导审批通过',
      operator: detailContext.approvedBy || '-',
      time: detailContext.approvedTime,
      type: 'success'
    })
  } else if (status === 'APPROVED') {
    records.push({
      action: '财务处审批通过（审批完成）',
      operator: detailContext.approvedBy || '-',
      time: detailContext.approvedTime,
      type: 'success'
    })
  }
  return records
})

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status || '-'
}

function getStatusType(status: string) {
  return statusMap[status]?.type || 'info'
}

function getStepIndex(status?: string): number {
  const map: Record<string, number> = {
    DRAFT: 0,
    PENDING_DEPT_LEADER: 1,
    PENDING_SUPERVISOR: 2,
    PENDING_FINANCE: 3,
    APPROVED: 4,
    REJECTED: 0
  }
  return map[status || 'DRAFT'] ?? 0
}

function formatDateTime(value: any) {
  if (!value) return '-'
  const normalized = String(value).replace('T', ' ')
  return normalized.length >= 19 ? normalized.slice(0, 19) : normalized
}

function formatPercent(value: any) {
  if (value === null || value === undefined || value === '') return '-'
  return `${value}%`
}

function formatSummaryPercent(value: number) {
  if (!Number.isFinite(value)) return '-'
  const rounded = Math.round((value + Number.EPSILON) * 100) / 100
  if (Math.abs(rounded - Math.round(rounded)) < 1e-9) {
    return `${Math.round(rounded)}%`
  }
  return `${rounded.toFixed(2).replace(/\.?0+$/, '')}%`
}

function formatFixedPercent(value: number) {
  if (!Number.isFinite(value)) return '-'
  return `${value.toFixed(2)}%`
}

function normalizeNames(value: any): string[] {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (typeof value === 'string') return value.split(/[、,，]/).map((item: string) => item.trim()).filter(Boolean)
  return []
}

function getNumberSort(value: any, fallback = 999) {
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

function getCategorySort(row: any) {
  const categoryIdKey = row.categoryId !== undefined && row.categoryId !== null ? `id:${row.categoryId}` : ''
  return categorySortMap.value.get(categoryIdKey)
    ?? categorySortMap.value.get(row.categoryName || '')
    ?? 999
}

function sortPreviewRows(rows: any[]) {
  return [...rows].sort((a, b) => {
    const categorySortA = getCategorySort(a)
    const categorySortB = getCategorySort(b)
    if (categorySortA !== categorySortB) return categorySortA - categorySortB
    if ((a.subCategory || '') !== (b.subCategory || '')) {
      return (a.subCategory || '').localeCompare(b.subCategory || '')
    }
    return getNumberSort(a.sortCode) - getNumberSort(b.sortCode)
  })
}

function calculateMergeInfo() {
  previewMergeMap.value.clear()
  const data = previewData.value
  if (data.length === 0) return

  let categoryStart = 0
  for (let i = 1; i <= data.length; i++) {
    if (i === data.length || data[i].categoryName !== data[categoryStart].categoryName) {
      const rowspan = i - categoryStart
      previewMergeMap.value.set(`0-${categoryStart}`, { rowspan, colspan: 1 })
      for (let j = categoryStart + 1; j < i; j++) {
        previewMergeMap.value.set(`0-${j}`, { rowspan: 0, colspan: 0 })
      }
      categoryStart = i
    }
  }

  let subCategoryStart = 0
  for (let i = 1; i <= data.length; i++) {
    const sameCategory = data[i]?.categoryName === data[subCategoryStart]?.categoryName
    const sameSubCategory = data[i]?.subCategory === data[subCategoryStart]?.subCategory
    if (i === data.length || !sameCategory || !sameSubCategory) {
      const rowspan = i - subCategoryStart
      previewMergeMap.value.set(`1-${subCategoryStart}`, { rowspan, colspan: 1 })
      for (let j = subCategoryStart + 1; j < i; j++) {
        previewMergeMap.value.set(`1-${j}`, { rowspan: 0, colspan: 0 })
      }
      subCategoryStart = i
    }
  }

  let evalStart = 0
  for (let i = 1; i <= data.length; i++) {
    const sameCategory = data[i]?.categoryName === data[evalStart]?.categoryName
    const sameSubCategory = data[i]?.subCategory === data[evalStart]?.subCategory
    const sameEval = data[i]?.evaluationStandard === data[evalStart]?.evaluationStandard
    if (i === data.length || !sameCategory || !sameSubCategory || !sameEval) {
      const rowspan = i - evalStart
      previewMergeMap.value.set(`6-${evalStart}`, { rowspan, colspan: 1 })
      for (let j = evalStart + 1; j < i; j++) {
        previewMergeMap.value.set(`6-${j}`, { rowspan: 0, colspan: 0 })
      }
      evalStart = i
    }
  }
}

function previewSpanMethod({ column, rowIndex }: { column: any; rowIndex: number }) {
  const columnIndex = column.property === 'categoryName' ? 0 :
                      column.property === 'subCategory' ? 1 :
                      column.property === 'evaluationStandard' ? 6 : -1
  if (columnIndex === -1) return [1, 1]
  return previewMergeMap.value.get(`${columnIndex}-${rowIndex}`) || [1, 1]
}

function resolveDefaultExamGroupId(groups: ExamGroup[]) {
  if (!groups.length) {
    return undefined
  }

  const currentYear = new Date().getFullYear()
  const scoredGroups = groups
    .map(group => {
      let score = 0

      if (group.status === 'IN_PROGRESS') {
        score += 100
      }
      if (group.groupName?.includes(`${currentYear}年度业绩指标`)) {
        score += 80
      } else if (group.groupName?.includes(`${currentYear}`)) {
        score += 40
      }
      if (group.startDate?.startsWith(`${currentYear}`)) {
        score += 20
      }
      if (group.endDate?.startsWith(`${currentYear}`)) {
        score += 10
      }

      return { group, score }
    })
    .sort((a, b) => {
      if (b.score !== a.score) {
        return b.score - a.score
      }
      return b.group.id - a.group.id
    })

  return scoredGroups[0]?.group.id
}

async function loadExamGroups() {
  try {
    const res = await getExamGroupList({
      current: 1,
      size: 999,
      examCategory: 'INDICATOR_SET'
    })
    examGroupOptions.value = (res.data?.records || []) as ExamGroup[]
    defaultExamGroupId.value = resolveDefaultExamGroupId(examGroupOptions.value)
  } catch (error) {
    console.error('加载考核组失败', error)
    ElMessage.error('加载考核组失败')
  }
}

async function loadIndicatorCategories() {
  try {
    const res: any = await indicatorCategoryApi.all(userStore.userInfo?.orgType || undefined)
    const map = new Map<string, number>()
    ;(res.data || []).forEach((item: any) => {
      const sortCode = item.sortCode || 999
      if (item.id !== undefined && item.id !== null) {
        map.set(`id:${item.id}`, sortCode)
      }
      if (item.categoryName) {
        map.set(item.categoryName, sortCode)
      }
    })
    categorySortMap.value = map
  } catch (e) {
    console.error('加载指标大类失败', e)
  }
}

async function loadProgress() {
  loading.value = true
  try {
    const params: Record<string, any> = {}
    if (queryForm.examGroupId) {
      params.examGroupId = queryForm.examGroupId
    }
    if (queryForm.orgName) {
      params.orgName = queryForm.orgName
    }
    if (queryForm.approvalStatus) {
      params.approvalStatus = queryForm.approvalStatus
    }
    const res = await getIndicatorProgress(params)
    progressList.value = res.data || []
    pagination.currentPage = 1
  } catch (error) {
    console.error('加载进度数据失败', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

async function loadProgressInfo() {
  if (!queryForm.examGroupId) {
    progressInfo.approved = 0
    progressInfo.total = 0
    progressInfo.percentage = 0
    return
  }

  try {
    const res = await getIndicatorProgress({ examGroupId: queryForm.examGroupId })
    const rows = res.data || []
    const total = rows.length
    const approved = rows.filter(item => item.approvalStatus === 'APPROVED').length
    progressInfo.total = total
    progressInfo.approved = approved
    progressInfo.percentage = total > 0
      ? Math.round((((approved / total) * 100) + Number.EPSILON) * 100) / 100
      : 0
  } catch (error) {
    console.error('加载进度汇总失败', error)
  }
}

async function refreshPageData() {
  await Promise.all([loadProgress(), loadProgressInfo()])
}

async function handleSearch() {
  await refreshPageData()
}

async function handleReset() {
  queryForm.examGroupId = defaultExamGroupId.value
  queryForm.orgName = ''
  queryForm.approvalStatus = ''
  await refreshPageData()
}

async function handleExamGroupChange() {
  await refreshPageData()
}

function handlePageSizeChange(size: number) {
  pagination.pageSize = size
  pagination.currentPage = 1
}

function handleCurrentPageChange(page: number) {
  pagination.currentPage = page
}

function resetDetailContext() {
  detailContext.examGroupId = 0
  detailContext.orgId = 0
  detailContext.groupName = ''
  detailContext.orgName = ''
  detailContext.approvalStatus = ''
  detailContext.submittedBy = ''
  detailContext.submittedTime = ''
  detailContext.approvedBy = ''
  detailContext.approvedTime = ''
  detailContext.rejectReason = ''
  recordDialogVisible.value = false
}

async function handleViewDetail(row: IndicatorProgressItem) {
  const matchedGroup = examGroupOptions.value.find(item => item.id === row.examGroupId)
  resetDetailContext()
  detailContext.examGroupId = row.examGroupId
  detailContext.orgId = row.orgId
  detailContext.groupName = matchedGroup?.groupName || row.groupName || ''
  detailContext.orgName = row.orgName || ''
  detailContext.approvalStatus = row.approvalStatus || ''
  previewData.value = []
  previewMergeMap.value.clear()
  detailDialogVisible.value = true

  if (!row.examGroupId || !row.orgId) {
    return
  }

  detailLoading.value = true
  try {
    const res: any = await getIndicatorList({
      examGroupId: row.examGroupId,
      orgId: row.orgId,
      current: 1,
      size: 1000
    })
    const records = (res.data?.records || []).map((item: any) => ({
      ...item,
      orgNames: item.orgNameList?.length ? item.orgNameList : normalizeNames(item.orgName),
      leaderNames: item.leaderNameList?.length ? item.leaderNameList : normalizeNames(item.leaderName)
    }))
    previewData.value = sortPreviewRows(records)
    calculateMergeInfo()

    if (records.length > 0) {
      const aggregated = aggregateApprovalInfo(records)
      detailContext.approvalStatus = aggregated.approvalStatus || detailContext.approvalStatus
      detailContext.submittedBy = aggregated.submittedBy
      detailContext.submittedTime = aggregated.submittedTime
      detailContext.approvedBy = aggregated.approvedBy
      detailContext.approvedTime = aggregated.approvedTime
      detailContext.rejectReason = aggregated.rejectReason
    }
  } catch (error) {
    console.error('加载指标详情失败', error)
    ElMessage.error('加载指标详情失败')
  } finally {
    detailLoading.value = false
  }
}

function aggregateApprovalInfo(records: any[]) {
  const statusPriority: Record<string, number> = {
    REJECTED: 5,
    APPROVED: 4,
    PENDING_FINANCE: 3,
    PENDING_SUPERVISOR: 2,
    PENDING_DEPT_LEADER: 1,
    DRAFT: 0
  }
  let dominantStatus = ''
  let dominantScore = -1
  let submittedBy = ''
  let submittedTime = ''
  let approvedBy = ''
  let approvedTime = ''
  let rejectReason = ''

  for (const item of records) {
    const score = statusPriority[item.approvalStatus] ?? -1
    if (score > dominantScore) {
      dominantScore = score
      dominantStatus = item.approvalStatus || ''
    }
    if (!submittedBy && item.submittedBy) submittedBy = item.submittedBy
    if (!submittedTime && item.submittedTime) submittedTime = item.submittedTime
    if (!approvedBy && item.approvedBy) approvedBy = item.approvedBy
    if (!approvedTime && item.approvedTime) approvedTime = item.approvedTime
    if (!rejectReason && item.rejectReason) rejectReason = item.rejectReason
  }

  return {
    approvalStatus: dominantStatus,
    submittedBy,
    submittedTime,
    approvedBy,
    approvedTime,
    rejectReason
  }
}

onMounted(async () => {
  await Promise.all([loadExamGroups(), loadIndicatorCategories()])

  const routeExamGroupId = route.query.examGroupId
  const routeGroupName = route.query.groupName

  if (routeExamGroupId) {
    queryForm.examGroupId = Number(routeExamGroupId)
  } else if (routeGroupName) {
    const matchedGroup = examGroupOptions.value.find(item => item.groupName === routeGroupName)
    if (matchedGroup) {
      queryForm.examGroupId = matchedGroup.id
    }
  }

  if (!queryForm.examGroupId) {
    queryForm.examGroupId = defaultExamGroupId.value
  }

  await refreshPageData()
})
</script>

<style scoped lang="scss">
.indicator-progress-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-header {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.page-title {
  font-size: 20px;
  font-weight: bold;
  color: var(--text-primary);
}

.search-card {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.search-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.search-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.progress-card {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.progress-summary {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-summary-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.table-card {
  flex: 1;
  min-height: 0;
  margin-bottom: 0;
}

.table-card :deep(.el-card__body) {
  height: 100%;
  padding: 0 0 12px;
  overflow: hidden;
}

.table-card-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
}

.table-card :deep(.el-table) {
  height: 100%;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px 0;
  flex-shrink: 0;
}

.approval-header {
  margin-bottom: 12px;
}

.approval-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow-x: auto;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  font-size: 13px;
}

.step-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid #d1d5db;
  background: #fff;
  color: #6b7280;
}

.step-text {
  color: #6b7280;
}

.step-arrow {
  color: #c0c4cc;
  margin: 0 2px 0 4px;
}

.approval-record-link {
  padding: 0;
  margin-left: 4px;
  font-size: 12px;
}

.step-done .step-dot {
  background: #f0fdf4;
  border-color: #86efac;
  color: #16a34a;
}

.step-done .step-text {
  color: #16a34a;
}

.step-current .step-dot {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #2563eb;
}

.step-current .step-text {
  color: #2563eb;
  font-weight: 600;
}

.step-pending .step-dot {
  color: #9ca3af;
}

.step-pending .step-text {
  color: #9ca3af;
}

.step-rejected .step-dot {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #dc2626;
}

.step-rejected .step-text {
  color: #dc2626;
  font-weight: 600;
}

.indicator-card {
  margin-bottom: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 700;
  color: #303133;
}

.weight-summary {
  font-size: 14px;
  color: #303133;
  font-weight: 400;
}

.record-action {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.record-operator {
  font-size: 13px;
  color: #606266;
  margin-top: 2px;
}

.record-remark {
  font-size: 13px;
  color: #f56c6c;
  margin-top: 4px;
}

.indicator-preview-table {
  margin-top: 4px;
}

.preview-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  color: #2d5aa0;
}
</style>

<style lang="scss">
.preview-tooltip {
  max-width: 800px !important;
  min-width: 400px !important;
  word-break: break-word;
  text-align: left;
}
</style>
