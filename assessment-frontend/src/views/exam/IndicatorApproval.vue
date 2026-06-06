<template>
  <div class="indicator-approval">
    <SearchForm
      :fields="searchFields"
      :model-value="searchFormModel"
      @search="handleSearch"
      @reset="handleReset"
    />

    <DataTable
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :total="total"
      :current-page="queryParams.current"
      :page-size="queryParams.size"
      @page-change="handlePageChange"
    >
      <template #submittedTime="{ row }">
        {{ formatDateTime(row.submittedTime) }}
      </template>
      <template #approvalStatus="{ row }">
        <StatusTag :status="row.approvalStatus" :status-map="approvalStatusMap" />
      </template>
      <template #operation="{ row }">
        <el-button v-if="canApprove(row)" link type="primary" @click="handleApprove(row)">审批</el-button>
        <el-button link type="info" @click="handleView(row)">查看</el-button>
      </template>
    </DataTable>

    <el-dialog
      v-model="approvalDialogVisible"
      :title="dialogMode === 'approve' ? '指标审批' : '鎸囨爣查看'"
      fullscreen
      destroy-on-close
      :close-on-click-modal="false"
    >
      <template #header>
        <div class="dialog-header">
          <div class="dialog-title-block">
            <span class="dialog-title">{{ dialogMode === 'approve' ? '指标审批' : '指标查看' }}</span>
            <span class="dialog-group-name">{{ currentRow?.examGroupName || '-' }}</span>
          </div>
        </div>
      </template>

      <div v-if="currentRow" class="approval-header">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="考核组">{{ currentRow.examGroupName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="考核部门">{{ currentRow.orgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ currentRow.submittedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(currentRow.submittedTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="approval-steps">
        <div
          v-for="(step, idx) in approvalSteps"
          :key="step"
          class="step-item"
          :class="{
            'step-done': getStepIndex(currentRow?.approvalStatus) > idx,
            'step-current': getStepIndex(currentRow?.approvalStatus) === idx,
            'step-pending': getStepIndex(currentRow?.approvalStatus) < idx
          }"
        >
          <span class="step-dot">{{ idx + 1 }}</span>
          <span class="step-text">{{ step }}</span>
          <span v-if="idx < approvalSteps.length - 1" class="step-arrow">→</span>
        </div>
      </div>

      <div class="category-cards">
        <div
          v-for="cat in categoryCards"
          :key="cat.name"
          class="category-card"
          :class="{ active: activeCategory === cat.name }"
          @click="activeCategory = cat.name"
        >
          <div class="card-top">
            <span class="card-name">{{ cat.name }}</span>
            <span v-if="cat.weightSum > 0" class="card-weight">{{ formatWeightPercent(cat.weightSum) }}</span>
          </div>
          <span class="card-stat">{{ cat.subCount }}小类 · {{ cat.itemCount }}指标</span>
        </div>
      </div>

      <div class="subcategory-section">
        <div class="section-header-row">
          <div class="section-title">小类列表 - {{ activeCategory || '-' }}</div>
          <div class="weight-summary">
            <span class="weight-item">
              <span class="weight-label">年度权重合计：</span>
              <span class="weight-value" :class="{ 'weight-ok': currentCategoryAnnualTotal === 100, 'weight-warn': currentCategoryAnnualTotal !== 100 }">
                {{ formatWeightPercent(currentCategoryAnnualTotal) }}
              </span>
            </span>
            <span class="weight-divider">|</span>
            <span class="weight-item">
              <span class="weight-label">月度权重合计：</span>
              <span class="weight-value" :class="{ 'weight-ok': currentCategoryMonthlyTotal === 100, 'weight-warn': currentCategoryMonthlyTotal !== 100 }">
                {{ formatWeightPercent(currentCategoryMonthlyTotal) }}
              </span>
            </span>
          </div>
        </div>
        <div class="subcategory-list">
          <div
            v-for="(sub, idx) in currentSubCategories"
            :key="sub.name"
            class="subcategory-item"
            :class="{ active: activeSubCategory === sub.name }"
            @click="activeSubCategory = sub.name"
          >
            <div class="sub-left">
              <span class="sub-index">{{ idx + 1 }}</span>
              <span class="sub-name">{{ sub.name }}</span>
              <span class="sub-desc" :title="sub.summary">{{ sub.summary }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="content-section">
        <div class="section-header">
          <span class="section-title">考核内容 - {{ activeSubCategory || '-' }}</span>
        </div>
        <el-table :data="sortedFilteredIndicators" border size="small" :max-height="tableMaxHeight">
          <el-table-column label="序号" width="70">
            <template #default="{ row }">{{ row.sortCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="content" label="考核内容" min-width="180" />
          <el-table-column prop="targetDesc" label="指标/目标" min-width="150" />
          <el-table-column label="权重(年度)" width="100" align="center">
            <template #default="{ row }">{{ formatWeightPercent(row.weightAnnual) }}</template>
          </el-table-column>
          <el-table-column label="权重(月度)" width="100" align="center">
            <template #default="{ row }">{{ formatWeightPercent(row.weightMonthly) }}</template>
          </el-table-column>
          <el-table-column label="考核标准" min-width="120" align="center">
            <template #default="{ row }">
              <el-tooltip
                :content="row.evaluationStandard"
                placement="top"
                :disabled="!row.evaluationStandard"
                popper-class="indicator-standard-tooltip"
              >
                <span class="view-link">查看</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="考核部门/分管领导" width="220">
            <template #default="{ row }">
              <span v-if="row.examTargetType === 'LEADER' && row.leaderNames?.length">
                {{ row.leaderNames.join('、') }}
              </span>
              <span v-else>{{ row.orgNames?.join('、') || row.orgName || currentRow?.orgName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" />
        </el-table>
        <div class="total-weight-summary">
          <span class="total-weight-title">全部权重合计</span>
          <span class="total-weight-item">
            <span class="total-weight-label">权重(年度)</span>
            <span class="total-weight-value">{{ formatWeightPercent(allIndicatorsAnnualTotal) }}</span>
          </span>
          <span class="total-weight-item">
            <span class="total-weight-label">权重(月度)</span>
            <span class="total-weight-value">{{ formatWeightPercent(allIndicatorsMonthlyTotal) }}</span>
          </span>
        </div>
      </div>

      <template #footer>
        <el-button @click="approvalDialogVisible = false">{{ dialogMode === 'approve' ? '取消' : '返回' }}</el-button>
        <template v-if="dialogMode === 'approve' && canApprove(currentRow) && String(route.query.mode || '') !== 'view'">
          <el-button type="danger" @click="handleReject">退回</el-button>
          <el-button type="primary" @click="handlePass">通过</el-button>
        </template>
        <el-button type="success" @click="handleDialogExport">导出</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectDialogVisible" title="退回说明" width="500px">
      <el-form
        ref="rejectFormRef"
        :model="rejectForm"
        :rules="{ rejectReason: [{ required: true, message: '请输入退回说明', trigger: 'blur' }] }"
      >
        <el-form-item label="退回说明" prop="rejectReason">
          <el-input v-model="rejectForm.rejectReason" type="textarea" :rows="4" placeholder="请输入退回说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import { getApprovalList, approveIndicators, rejectIndicators, getIndicatorList } from '@/api/indicator'
import { getExamGroupList } from '@/api/examGroup'
import { indicatorCategoryApi } from '@/api/admin'
import { getWorkflowTasks, workflowApprove, workflowReject, type WorkflowTask } from '@/api/workflow'
import { useUserStore } from '@/stores/user'
import type { ExamGroup } from '@/api/types'

const userStore = useUserStore()
const route = useRoute()
const loading = ref(false)
const rawData = ref<any[]>([])
const tableData = ref<any[]>([])
const total = ref(0)
const categorySortMap = ref<Map<string, number>>(new Map())
const examGroupOptions = ref<ExamGroup[]>([])

// 工作流平台待办任务（由工作流引擎驱动）
const workflowTasks = ref<WorkflowTask[]>([])
const searchFormModel = computed(() => ({
  examGroupId: queryParams.examGroupId || '',
  orgId: queryParams.orgId || '',
  approvalStatus: queryParams.approvalStatus || ''
}))

const queryParams = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  orgId: undefined as number | undefined,
  approvalStatus: ''
})

const searchFields = computed<SearchField[]>(() => [
  {
    prop: 'examGroupId',
    label: '考核组',
    type: 'select',
    placeholder: '请选择考核组',
    options: examGroupOptions.value.map(group => ({ label: group.groupName, value: group.id }))
  },
  { prop: 'orgId', label: '考核部门', type: 'input', placeholder: '部门ID' },
  {
    prop: 'approvalStatus',
    label: '审批状态',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '待部门负责人审批', value: 'PENDING_DEPT_LEADER' },
      { label: '待分管领导审批', value: 'PENDING_SUPERVISOR' },
      { label: '待财务处审批', value: 'PENDING_FINANCE' },
      { label: '审批通过', value: 'APPROVED' },
      { label: '已退回', value: 'REJECTED' }
    ]
  }
])

const columns: TableColumn[] = [
  { prop: 'examGroupName', label: '考核组名称', minWidth: 160 },
  { prop: 'orgName', label: '考核部门', minWidth: 140 },
  { prop: 'submittedBy', label: '提交人', width: 100 },
  { prop: 'submittedTime', label: '提交时间', width: 160 },
  { prop: 'approvalStatus', label: '审批状态', width: 140 },
  { prop: 'operation', label: '操作', width: 120 }
]

const approvalStatusMap = {
  DRAFT: { text: '草稿', type: 'info' as const },
  PENDING_DEPT_LEADER: { text: '待部门负责人审批', type: 'warning' as const },
  PENDING_SUPERVISOR: { text: '待分管领导审批', type: 'warning' as const },
  PENDING_FINANCE: { text: '待财务处审批', type: 'warning' as const },
  APPROVED: { text: '审批通过', type: 'success' as const },
  REJECTED: { text: '已退回', type: 'danger' as const }
}

const approvalSteps = ['草稿', '待部门负责人审批', '待分管领导审批', '待财务处审批', '审批通过']
const roleCode = computed(() => userStore.userInfo?.roleCode || 'FIN_ADMIN')

function canApprove(row: any) {
  if (!row?.approvalStatus) return false

  // 双重校验：
  // 1. QODER 后端的审批状态（指标数据层）
  // 2. 工作流平台的待办任务（审批引擎层）
  const hasBackendStatus =
    (roleCode.value === 'DEPT_LEADER' && row.approvalStatus === 'PENDING_DEPT_LEADER') ||
    (roleCode.value === 'SUPERVISOR' && row.approvalStatus === 'PENDING_SUPERVISOR') ||
    (roleCode.value === 'FIN_ADMIN' && row.approvalStatus === 'PENDING_FINANCE')

  if (!hasBackendStatus) return false

  // 检查工作流平台是否有对应的待办任务
  const examGroupId = Number(row.examGroupId)
  const orgId = Number(row.orgId)
  const hasWorkflowTask = workflowTasks.value.some(task => {
    const vars = task.variables || {}
    return (
      Number(vars.examGroupId) === examGroupId &&
      Number(vars.orgId) === orgId &&
      task.node_type === 'approval'
    )
  })

  // 如果工作流平台没有任务（可能工作流未启动或已推进），也允许通过
  // 工作流平台作为审批引擎，不阻断正常的审批操作
  return hasBackendStatus
}

function formatDateTime(value: any) {
  if (!value) return '-'
  const normalized = String(value).replace('T', ' ')
  return normalized.length >= 19 ? normalized.slice(0, 19) : normalized
}

function aggregateByGroup(records: any[]) {
  const map = new Map<string, any>()
  for (const item of records) {
    const key = `${item.examGroupId}-${item.orgId}`
    if (!map.has(key)) {
      const group = findExamGroup(item.examGroupId)
      map.set(key, {
        examGroupId: item.examGroupId,
        orgId: item.orgId,
        examGroupName: item.examGroupName || group?.groupName || '-',
        orgName: item.orgName || item.submitOrgName || item.deptName || '-',
        submittedBy: item.submittedBy || '-',
        submittedTime: formatDateTime(item.submittedTime),
        approvalStatus: item.approvalStatus
      })
    }
  }
  return Array.from(map.values())
}

function findExamGroup(examGroupId: number) {
  return examGroupOptions.value.find(group => group.id === examGroupId)
}

function loadData() {
  loading.value = true
  return Promise.all([
    // 从 QODER 后端加载指标审批列表
    getApprovalList({ ...queryParams, roleCode: roleCode.value }),
    // 从工作流平台加载待办任务（由工作流引擎驱动）
    getWorkflowTasks().catch(() => ({ data: [] } as any)),
  ])
    .then(([res, wfRes]) => {
      rawData.value = res.data.records || []
      tableData.value = aggregateByGroup(rawData.value)
      total.value = tableData.value.length

      // 保存工作流平台待办任务
      workflowTasks.value = (wfRes as any).data || []
    })
    .finally(() => {
      loading.value = false
    })
}

function tryOpenFromNotificationLink() {
  const mode = String(route.query.mode || '')
  const examGroupId = Number(route.query.examGroupId || 0)
  const orgId = Number(route.query.orgId || 0)
  if (!mode || !examGroupId || !orgId) return
  const target = tableData.value.find(
    (row: any) => Number(row.examGroupId) === examGroupId && Number(row.orgId) === orgId
  )
  const openMode: 'approve' | 'view' = mode === 'view' ? 'view' : 'approve'
  if (target) {
    if (!target.examGroupName && route.query.groupName) {
      target.examGroupName = String(route.query.groupName)
    }
    openIndicatorDialog(target, openMode)
    return
  }
  const fallbackGroup = examGroupOptions.value.find(g => Number(g.id) === examGroupId)
  openIndicatorDialog(
    {
      examGroupId,
      orgId,
      examGroupName: String(route.query.groupName || fallbackGroup?.groupName || '-'),
      orgName: String(route.query.orgName || '-')
    },
    openMode
  )
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.examGroupId = data.examGroupId ? Number(data.examGroupId) : undefined
  queryParams.orgId = data.orgId ? Number(data.orgId) : undefined
  queryParams.approvalStatus = data.approvalStatus || ''
  loadData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.examGroupId = getDefaultExamGroupId()
  queryParams.orgId = undefined
  queryParams.approvalStatus = ''
  loadData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  const grouped = aggregateByGroup(rawData.value)
  tableData.value = grouped.slice((page - 1) * size, page * size)
  total.value = grouped.length
}

const approvalDialogVisible = ref(false)
const currentRow = ref<any>(null)
const dialogMode = ref<'approve' | 'view'>('approve')
const indicatorList = ref<any[]>([])
const activeCategory = ref('')
const activeSubCategory = ref('')
const tableMaxHeight = 'calc(100vh - 430px)'
const previewMergeMap = ref<Map<string, { rowspan: number; colspan: number }>>(new Map())

const groupedByCategory = computed(() => {
  const map = new Map<string, any[]>()
  for (const item of indicatorList.value) {
    const category = item.categoryName || '默认大类'
    if (!map.has(category)) map.set(category, [])
    map.get(category)!.push(item)
  }
  return map
})

const categoryCards = computed(() => {
  return Array.from(groupedByCategory.value.entries())
    .map(([name, items]) => {
      const subSet = new Set(items.map(item => item.subCategory || '默认小类'))
      const weightSum = Math.round(items.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0) * 100) / 100
      return { name, subCount: subSet.size, itemCount: items.length, weightSum, sortCode: getCategorySort({ categoryName: name }) }
    })
    .sort((a, b) => a.sortCode - b.sortCode)
})

const currentSubCategories = computed(() => {
  const items = groupedByCategory.value.get(activeCategory.value) || []
  const subMap = new Map<string, { name: string; summary: string }>()
  for (const item of items) {
    const sub = item.subCategory || '默认小类'
    if (!subMap.has(sub)) {
      subMap.set(sub, { name: sub, summary: item.evaluationStandard || item.content || '' })
    }
  }
  return Array.from(subMap.values())
})

const filteredIndicators = computed(() => {
  return indicatorList.value.filter(item => {
    const category = item.categoryName || '默认大类'
    const sub = item.subCategory || '默认小类'
    return category === activeCategory.value && sub === activeSubCategory.value
  })
})

const sortedFilteredIndicators = computed(() => sortRows(filteredIndicators.value))

const currentCategoryAnnualTotal = computed(() => {
  const items = indicatorList.value.filter(item => (item.categoryName || '默认大类') === activeCategory.value)
  return roundWeight(items.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0))
})

const currentCategoryMonthlyTotal = computed(() => {
  const items = indicatorList.value.filter(item => (item.categoryName || '默认大类') === activeCategory.value)
  return roundWeight(items.reduce((sum, item) => sum + (Number(item.weightMonthly) || 0), 0))
})

const allIndicatorsAnnualTotal = computed(() => {
  return roundWeight(indicatorList.value.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0))
})

const allIndicatorsMonthlyTotal = computed(() => {
  return roundWeight(indicatorList.value.reduce((sum, item) => sum + (Number(item.weightMonthly) || 0), 0))
})

watch(activeCategory, () => {
  const subs = currentSubCategories.value
  activeSubCategory.value = subs.length > 0 ? subs[0].name : ''
})

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

function normalizeNames(value: any): string[] {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (typeof value === 'string') return value.split(/[、,，]/).map(item => item.trim()).filter(Boolean)
  return []
}

function roundWeight(value: number) {
  return Math.round(value * 100) / 100
}

function formatWeightPercent(value: any) {
  if (value === null || value === undefined || value === '') return '-'
  return `${value}%`
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

function sortRows(rows: any[]) {
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

function buildPreviewRows(items: any[]) {
  const validItems = items.filter(item => item.id || item.content)
  return sortRows(validItems).map(item => ({
    ...item,
    orgNames: item.orgNames?.length ? item.orgNames : (item.orgNameList?.length ? item.orgNameList : normalizeNames(item.orgName)),
    leaderNames: item.leaderNames?.length ? item.leaderNames : (item.leaderNameList?.length ? item.leaderNameList : normalizeNames(item.leaderName))
  }))
}

function getDisplayOrgName(record: any) {
  const fromList = record?.orgNameList?.find((name: string) => !!name)
  if (fromList) return fromList
  if (record?.orgName) return record.orgName
  return ''
}

function calculateMergeInfo(data: any[]) {
  previewMergeMap.value.clear()
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

function getExportTargetName(examGroupName?: string) {
  const dateText = new Date().toLocaleDateString().replace(/[\\/]/g, '-')
  return `业绩指标审批_${examGroupName || '导出'}_${dateText}.xls`
}

function escapeHtml(value: any) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function getAssessmentTargetText(item: any) {
  return item.examTargetType === 'LEADER'
    ? (item.leaderNames?.join('、') || item.leaderName || '')
    : (item.orgNames?.join('、') || item.orgName || currentRow.value?.orgName || '')
}

function buildExportHtml(rows: any[]) {
  const mergeMap = new Map<string, { rowspan: number; colspan: number }>()
  previewMergeMap.value.forEach((value, key) => mergeMap.set(key, value))

  const renderMergedCell = (columnIndex: number, rowIndex: number, content: any) => {
    const mergeInfo = mergeMap.get(`${columnIndex}-${rowIndex}`) || { rowspan: 1, colspan: 1 }
    if (mergeInfo.rowspan === 0 || mergeInfo.colspan === 0) return ''
    const spanAttrs = `${mergeInfo.rowspan > 1 ? ` rowspan="${mergeInfo.rowspan}"` : ''}${mergeInfo.colspan > 1 ? ` colspan="${mergeInfo.colspan}"` : ''}`
    return `<td${spanAttrs}>${escapeHtml(content || '-')}</td>`
  }

  const bodyRows = rows.map((item, index) => `
    <tr>
      ${renderMergedCell(0, index, item.categoryName)}
      ${renderMergedCell(1, index, item.subCategory)}
      <td>${escapeHtml(item.content || '-')}</td>
      <td>${escapeHtml(item.targetDesc || '-')}</td>
      <td>${escapeHtml(`${item.weightAnnual || 0}%`)}</td>
      <td>${escapeHtml(`${item.weightMonthly || 0}%`)}</td>
      ${renderMergedCell(6, index, item.evaluationStandard)}
      <td>${escapeHtml(getAssessmentTargetText(item) || '-')}</td>
      <td>${escapeHtml(item.remark || '-')}</td>
    </tr>
  `).join('')

  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8" />
  <style>
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #dcdfe6; padding: 8px; font-size: 12px; vertical-align: middle; }
    th { background: #f5f7fa; font-weight: 700; text-align: center; }
    td:nth-child(5), td:nth-child(6) { text-align: center; }
  </style>
</head>
<body>
  <table>
    <thead>
      <tr>
        <th>指标大类</th>
        <th>指标小类</th>
        <th>考核内容</th>
        <th>指标/目标</th>
        <th>权重(年度)</th>
        <th>权重(月度)</th>
        <th>考核标准</th>
        <th>考核部门/分管领导</th>
        <th>备注</th>
      </tr>
    </thead>
    <tbody>${bodyRows}</tbody>
  </table>
</body>
</html>`
}

function downloadExcelHtml(content: string, fileName: string) {
  const blob = new Blob(['\ufeff', content], { type: 'application/vnd.ms-excel;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function handleDialogExport() {
  const rows = buildPreviewRows(indicatorList.value)
  if (rows.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }
  calculateMergeInfo(rows)
  downloadExcelHtml(buildExportHtml(rows), getExportTargetName(currentRow.value?.examGroupName))
  ElMessage.success('导出成功')
}

function openIndicatorDialog(row: any, mode: 'approve' | 'view') {
  currentRow.value = row
  dialogMode.value = mode
  indicatorList.value = []
  activeCategory.value = ''
  activeSubCategory.value = ''
  approvalDialogVisible.value = true
  if (row.examGroupId && row.orgId) {
    getIndicatorList({ examGroupId: row.examGroupId, orgId: row.orgId, current: 1, size: 1000 }).then((res: any) => {
      const records = res.data?.records || []
      indicatorList.value = sortRows(records.map((item: any, index: number) => ({
        ...item,
        sortCode: item.sortCode || (index + 1),
        examTargetType: item.examTargetType || 'DEPARTMENT',
        orgNames: item.orgNameList?.length ? item.orgNameList : normalizeNames(item.orgName),
        leaderNames: item.leaderNameList?.length ? item.leaderNameList : normalizeNames(item.leaderName)
      })))
      if (!currentRow.value?.orgName) {
        const fallbackOrgName = records.map((r: any) => getDisplayOrgName(r)).find((name: string) => !!name)
        if (fallbackOrgName) {
          currentRow.value = { ...currentRow.value, orgName: fallbackOrgName }
        }
      }
      const firstCategory = categoryCards.value[0]?.name
      activeCategory.value = firstCategory || ''
      activeSubCategory.value = currentSubCategories.value[0]?.name || ''
    })
  }
}

function handleApprove(row: any) {
  if (String(route.query.mode || '') === 'view') return
  openIndicatorDialog(row, 'approve')
}

function handleView(row: any) {
  openIndicatorDialog(row, 'view')
}

function handlePass() {
  if (!currentRow.value) return
  const indicatorIds = indicatorList.value.map(item => item.id).filter(Boolean)

  // 从工作流平台查找当前行对应的待办任务
  const examGroupId = Number(currentRow.value.examGroupId)
  const orgId = Number(currentRow.value.orgId)
  const workflowTask = workflowTasks.value.find(task => {
    const vars = task.variables || {}
    return (
      Number(vars.examGroupId) === examGroupId &&
      Number(vars.orgId) === orgId &&
      task.node_type === 'approval'
    )
  })

  // 并行执行：
  // 1. QODER 后端：更新指标审批状态
  // 2. 工作流平台：推进审批节点（如果有工作流任务）
  Promise.all([
    approveIndicators({ indicatorIds, action: 'APPROVE', roleCode: roleCode.value }),
    workflowTask
      ? workflowApprove(workflowTask.node_instance_id, '同意')
          .then((wfRes: any) => {
            if (wfRes.data?.success === false) {
              console.warn('工作流平台审批失败:', wfRes.data?.message)
            }
          })
          .catch((err: any) => {
            console.warn('工作流平台调用失败（不阻断审批）:', err?.message)
          })
      : Promise.resolve(),
  ]).then(() => {
    ElMessage.success('审批通过')
    approvalDialogVisible.value = false
    loadData()
  })
}

const rejectDialogVisible = ref(false)
const rejectFormRef = ref<any>(null)
const rejectForm = reactive({ rejectReason: '' })

function handleReject() {
  rejectForm.rejectReason = ''
  rejectDialogVisible.value = true
}

function confirmReject() {
  rejectFormRef.value?.validate((valid: boolean) => {
    if (!valid) return
    if (!currentRow.value) return
    const indicatorIds = indicatorList.value.map(item => item.id).filter(Boolean)

    // 从工作流平台查找当前行对应的待办任务
    const examGroupId = Number(currentRow.value.examGroupId)
    const orgId = Number(currentRow.value.orgId)
    const workflowTask = workflowTasks.value.find(task => {
      const vars = task.variables || {}
      return (
        Number(vars.examGroupId) === examGroupId &&
        Number(vars.orgId) === orgId &&
        task.node_type === 'approval'
      )
    })

    Promise.all([
      rejectIndicators({
        indicatorIds,
        action: 'REJECT',
        rejectReason: rejectForm.rejectReason,
        roleCode: roleCode.value,
      }),
      workflowTask
        ? workflowReject(workflowTask.node_instance_id, rejectForm.rejectReason)
            .then((wfRes: any) => {
              if (wfRes.data?.success === false) {
                console.warn('工作流平台驳回失败:', wfRes.data?.message)
              }
            })
            .catch((err: any) => {
              console.warn('工作流平台调用失败（不阻断驳回）:', err?.message)
            })
        : Promise.resolve(),
    ]).then(() => {
      ElMessage.success('已退回')
      rejectDialogVisible.value = false
      approvalDialogVisible.value = false
      loadData()
    })
  })
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
  } catch (e) {}
}

function getDefaultExamGroupId() {
  const runningGroup = examGroupOptions.value.find(group =>
    group.examCategory === 'INDICATOR_SET' && group.status === 'IN_PROGRESS'
  )
  return runningGroup?.id
}

async function loadExamGroups() {
  try {
    const res: any = await getExamGroupList({
      current: 1,
      size: 1000,
      examCategory: 'INDICATOR_SET',
      status: 'IN_PROGRESS'
    })
    examGroupOptions.value = res.data?.records || []
    if (!queryParams.examGroupId) {
      queryParams.examGroupId = getDefaultExamGroupId()
    }
  } catch (e) {
    examGroupOptions.value = []
  }
}

onMounted(async () => {
  await Promise.all([loadExamGroups(), loadIndicatorCategories()])
  await loadData()
  tryOpenFromNotificationLink()
})
</script>

<style scoped lang="scss">
.indicator-approval {
  padding: 16px;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.dialog-title-block {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.dialog-group-name {
  color: #606266;
}

.approval-header {
  margin-bottom: 12px;
}

.approval-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
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

.step-current .step-text {
  color: #2563eb;
  font-weight: 700;
}

.step-done .step-text {
  color: #16a34a;
}

.step-done .step-dot {
  background: #f0fdf4;
  border-color: #86efac;
  color: #16a34a;
}

.step-current .step-dot {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #2563eb;
}

.step-pending .step-dot {
  color: #9ca3af;
}

.step-pending .step-text {
  color: #9ca3af;
}

.step-arrow {
  color: #c0c4cc;
  margin: 0 2px 0 4px;
}

.category-cards {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  overflow-x: auto;
}

.category-card {
  min-width: 160px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}

.category-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.card-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.card-name,
.card-weight {
  font-weight: 700;
  color: #303133;
}

.card-stat {
  font-size: 12px;
  color: #909399;
}

.subcategory-section {
  margin-bottom: 12px;
}

.section-header-row,
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-title {
  font-weight: 700;
  color: #303133;
}

.weight-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.weight-label {
  color: #606266;
}

.weight-value {
  font-weight: 700;
}

.weight-ok {
  color: #67c23a;
}

.weight-warn {
  color: #f56c6c;
}

.weight-divider {
  color: #dcdfe6;
}

.subcategory-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.subcategory-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
}

.subcategory-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.sub-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.sub-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #606266;
  font-size: 12px;
}

.sub-name {
  font-weight: 600;
  color: #303133;
}

.sub-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #909399;
}

.content-section {
  margin-top: 8px;
}

.view-link {
  color: #2d5aa0;
  cursor: pointer;
  font-size: 13px;
}

.view-link:hover {
  text-decoration: underline;
}

.total-weight-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 10px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-top: 0;
  background: #fafafa;
  font-size: 14px;
}

.total-weight-title,
.total-weight-value {
  font-weight: 700;
  color: #303133;
}

.total-weight-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.total-weight-label {
  color: #606266;
}
</style>

<style lang="scss">
.indicator-standard-tooltip {
  max-width: 520px !important;
  min-width: 320px !important;
  word-break: break-word;
  white-space: normal;
  text-align: left;
}
</style>

