<template>
  <div class="leader-indicator-approve">
    <SearchForm
      :fields="searchFields"
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
      <template #approvalStatus="{ row }">
        <StatusTag :status="row.approvalStatus" :status-map="approvalStatusMap" />
      </template>
      <template #operation="{ row }">
        <el-button v-if="canApprove(row)" link type="primary" @click="handleApprove(row)">审批</el-button>
        <el-button link type="info" @click="handleView(row)">查看</el-button>
      </template>
    </DataTable>

    <!-- 审批弹框 -->
    <el-dialog
      v-model="approvalDialogVisible"
      :title="dialogMode === 'approve' ? '指标审批' : '指标查看'"
      fullscreen
      destroy-on-close
    >
      <div v-if="currentRow" class="approval-header">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="考核组">{{ currentRow.groupName || currentRow.examGroupName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="考核部门">{{ currentRow.orgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ currentRow.submittedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(currentRow.submittedTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <div v-if="currentRow" class="approval-steps">
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

      <el-table
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
            <span v-else>{{ row.orgNames?.join('、') || row.orgName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="120" />
      </el-table>

      <template #footer>
          <el-button @click="approvalDialogVisible = false">{{ dialogMode === 'approve' ? '取消' : '关闭' }}</el-button>
        <template v-if="dialogMode === 'approve' && canApprove(currentRow)">
          <el-button type="danger" @click="handleReject">退回</el-button>
          <el-button type="primary" @click="handlePass">通过</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 退回确认 -->
    <el-dialog v-model="rejectDialogVisible" title="退回说明" width="500px">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="{ rejectReason: [{ required: true, message: '请输入退回说明', trigger: 'blur' }] }">
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
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import { getApprovalList, approveIndicators, rejectIndicators, getIndicatorList } from '@/api/indicator'
import { getMyExamGroupTasks } from '@/api/examGroup'
import { indicatorCategoryApi } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import type { ExamGroupTaskVO } from '@/api/examGroup'

const userStore = useUserStore()
const route = useRoute()
const loading = ref(false)
const rawData = ref<any[]>([])
const tableData = ref<any[]>([])
const total = ref(0)
const examGroupOptions = ref<ExamGroupTaskVO[]>([])
const categorySortMap = ref<Map<string, number>>(new Map())

const queryParams = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  examType: '',
  startDate: '',
  orgId: undefined as number | undefined,
  approvalStatus: ''
})

const searchFields = computed<SearchField[]>(() => [
  {
    prop: 'examGroupId',
    label: '考核组名称',
    type: 'select',
    placeholder: '请选择考核组',
    options: examGroupOptions.value.map(g => ({ label: g.examGroupName, value: g.examGroupId }))
  },
  {
    prop: 'examType',
    label: '考核期间类型',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '月度考核', value: 'MONTHLY' },
      { label: '年度考核', value: 'ANNUAL' }
    ]
  },
  { prop: 'startDate', label: '考核开始时间', type: 'date' },
  { prop: 'orgId', label: '考核部门', type: 'input', placeholder: '部门ID' },
  {
    prop: 'approvalStatus',
    label: '审批状态',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '待部门负责人审批', value: 'PENDING_DEPT_LEADER' },
      { label: '已通过', value: 'APPROVED' },
      { label: '已退回', value: 'REJECTED' }
    ]
  }
])

const columns: TableColumn[] = [
  { prop: 'examGroupName', label: '考核组名称', minWidth: 180 },
  { prop: 'examType', label: '考核期间类型', width: 120 },
  { prop: 'startDate', label: '考核开始时间', width: 120 },
  { prop: 'endDate', label: '考核结束时间', width: 120 },
  { prop: 'submittedBy', label: '提交人', width: 100 },
  { prop: 'orgName', label: '考核部门', minWidth: 140 },
  { prop: 'approvalStatus', label: '审批状态', width: 150 },
  { prop: 'operation', label: '操作', width: 120 }
]

const approvalStatusMap = {
  PENDING_DEPT_LEADER: { text: '待部门负责人审批', type: 'warning' as const },
  PENDING_SUPERVISOR: { text: '待分管领导审批', type: 'warning' as const },
  PENDING_FINANCE: { text: '待财务处审批', type: 'warning' as const },
  APPROVED: { text: '已通过', type: 'success' as const },
  REJECTED: { text: '已退回', type: 'danger' as const }
}

const approvalSteps = ['草稿', '部门负责人审批', '分管领导审批', '财务处审批', '审批通过']

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

const roleCode = computed(() => userStore.userInfo?.roleCode || 'DEPT_LEADER')

function canApprove(row: any) {
  if (!row?.approvalStatus) return false
  return (
    (roleCode.value === 'DEPT_LEADER' && row.approvalStatus === 'PENDING_DEPT_LEADER') ||
    (roleCode.value === 'SUPERVISOR' && row.approvalStatus === 'PENDING_SUPERVISOR') ||
    (roleCode.value === 'FIN_ADMIN' && row.approvalStatus === 'PENDING_FINANCE')
  )
}

function formatDateTime(value: any) {
  if (!value) return '-'
  const text = String(value)
  const normalized = text.replace('T', ' ')
  return normalized.length >= 19 ? normalized.slice(0, 19) : normalized
}

function aggregateByGroup(records: any[]) {
  const map = new Map<string, any>()
  for (const item of records) {
    const key = `${item.examGroupId}-${item.orgId}`
    const task = findTask(item.examGroupId, item.orgId)
    if (!map.has(key)) {
      map.set(key, {
        examGroupId: item.examGroupId,
        orgId: item.orgId,
        examGroupName: item.examGroupName,
        orgName: item.orgName || task?.orgName || userStore.scopeName || userStore.userInfo?.orgName || '-',
        examType: item.examType,
        startDate: item.startDate,
        endDate: item.endDate,
        submittedBy: item.submittedBy || userStore.userInfo?.userName || '-',
        submittedTime: formatDateTime(item.submittedTime),
        approvalStatus: item.approvalStatus
      })
    }
  }
  return Array.from(map.values())
}

function findTask(examGroupId: number, orgId: number) {
  return examGroupOptions.value.find(task =>
    task.examGroupId === examGroupId && (!orgId || task.orgId === orgId)
  ) || examGroupOptions.value.find(task => task.examGroupId === examGroupId)
}

function loadData() {
  loading.value = true
  return getApprovalList({ ...queryParams, roleCode: roleCode.value })
    .then((res: any) => {
      rawData.value = res.data.records
      tableData.value = aggregateByGroup(rawData.value)
      total.value = tableData.value.length
    })
    .finally(() => {
      loading.value = false
    })
}

function tryOpenFromNotificationLink() {
  const mode = String(route.query.mode || '')
  const examGroupId = Number(route.query.examGroupId || 0)
  const orgId = Number(route.query.orgId || 0)
  if (mode !== 'approve' || !examGroupId || !orgId) return
  const target = tableData.value.find(
    (row: any) => Number(row.examGroupId) === examGroupId && Number(row.orgId) === orgId
  )
  if (target) {
    openIndicatorDialog(target, 'approve')
  }
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.examGroupId = data.examGroupId ? Number(data.examGroupId) : undefined
  queryParams.examType = data.examType || ''
  queryParams.startDate = data.startDate || ''
  queryParams.orgId = data.orgId ? Number(data.orgId) : undefined
  queryParams.approvalStatus = data.approvalStatus || ''
  loadData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.examGroupId = undefined
  queryParams.examType = ''
  queryParams.startDate = ''
  queryParams.orgId = undefined
  queryParams.approvalStatus = ''
  loadData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  const start = (page - 1) * size
  const end = start + size
  tableData.value = aggregateByGroup(rawData.value).slice(start, end)
  total.value = aggregateByGroup(rawData.value).length
}

// 审批弹框
const approvalDialogVisible = ref(false)
const currentRow = ref<any>(null)
const dialogMode = ref<'approve' | 'view'>('approve')
const previewData = ref<any[]>([])
const previewMergeMap = ref<Map<string, { rowspan: number; colspan: number }>>(new Map())

function normalizeNames(value: any): string[] {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (typeof value === 'string') return value.split(/[、,，]/).map(item => item.trim()).filter(Boolean)
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

    if (a.subCategory !== b.subCategory) {
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

function formatPercent(value: any) {
  if (value === null || value === undefined || value === '') return '-'
  return `${value}%`
}

function openIndicatorDialog(row: any, mode: 'approve' | 'view') {
  currentRow.value = row
  dialogMode.value = mode
  previewData.value = []
  previewMergeMap.value.clear()
  approvalDialogVisible.value = true
  if (row.examGroupId && row.orgId) {
    getIndicatorList({ examGroupId: row.examGroupId, orgId: row.orgId, current: 1, size: 1000 }).then((res: any) => {
      previewData.value = sortPreviewRows((res.data?.records || []).map((item: any) => ({
        ...item,
        orgNames: item.orgNameList?.length ? item.orgNameList : normalizeNames(item.orgName),
        leaderNames: item.leaderNameList?.length ? item.leaderNameList : normalizeNames(item.leaderName)
      })))
      calculateMergeInfo()
    })
  }
}

function handleApprove(row: any) {
  openIndicatorDialog(row, 'approve')
}

function handleView(row: any) {
  openIndicatorDialog(row, 'view')
}

function handlePass() {
  if (!currentRow.value) return
  const indicatorIds = previewData.value.map(item => item.id)
  approveIndicators({
    indicatorIds,
    action: 'APPROVE',
    roleCode: roleCode.value
  }).then(() => {
    ElMessage.success('审批通过')
    approvalDialogVisible.value = false
    loadData()
  })
}

// 退回
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
    const indicatorIds = previewData.value.map(item => item.id)
    rejectIndicators({
      indicatorIds,
      action: 'REJECT',
      rejectReason: rejectForm.rejectReason,
      roleCode: roleCode.value
    }).then(() => {
      ElMessage.success('已退回')
      rejectDialogVisible.value = false
      approvalDialogVisible.value = false
      loadData()
    })
  })
}

async function loadExamGroups() {
  try {
    const res = await getMyExamGroupTasks('INDICATOR_SET')
    const unique = new Map<number, ExamGroupTaskVO>()
    ;(res.data || []).forEach((task: ExamGroupTaskVO) => {
      if (task.examGroupId && task.examCategory === 'INDICATOR_SET') {
        unique.set(task.examGroupId, task)
      }
    })
    examGroupOptions.value = Array.from(unique.values())
  } catch (e) {}
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

onMounted(async () => {
  await Promise.all([loadExamGroups(), loadIndicatorCategories()])
  await loadData()
  tryOpenFromNotificationLink()
})
</script>

<style scoped lang="scss">
.leader-indicator-approve {
  padding: 16px;
}
.approval-header {
  margin-bottom: 12px;
}
.approval-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 12px;
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
  font-size: 13px;
  color: #6b7280;
}
.step-arrow {
  color: #c0c4cc;
  margin: 0 2px 0 4px;
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
.indicator-tree-table {
  margin-top: 8px;
}
.indicator-preview-table {
  margin-top: 8px;
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



