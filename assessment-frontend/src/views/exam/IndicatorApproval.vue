<template>
  <div class="indicator-approval">
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
        <el-button link type="primary" @click="handleApprove(row)">审批</el-button>
        <el-button link type="info" @click="handleView(row)">查看</el-button>
      </template>
    </DataTable>

    <!-- 审批弹框 -->
    <el-dialog
      v-model="approvalDialogVisible"
      title="指标审批"
      width="900px"
      destroy-on-close
    >
      <div v-if="currentRow" class="approval-header">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="考核组">{{ currentRow.groupName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="考核部门">{{ currentRow.orgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ currentRow.submittedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentRow.submittedTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-table :data="indicatorTreeData" row-key="id" default-expand-all border class="indicator-tree-table">
        <el-table-column prop="categoryName" label="指标大类" width="140" />
        <el-table-column prop="subCategory" label="指标小类" width="140" />
        <el-table-column prop="content" label="考核内容" min-width="180" />
        <el-table-column prop="targetDesc" label="指标/目标" min-width="150" />
        <el-table-column prop="weightAnnual" label="权重(年度%)" width="100" />
        <el-table-column prop="weightMonthly" label="权重(月度%)" width="100" />
        <el-table-column prop="evaluationStandard" label="考核标准" min-width="180" />
      </el-table>

      <template #footer>
        <el-button @click="approvalDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="handleReject">退回</el-button>
        <el-button type="primary" @click="handlePass">通过</el-button>
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
import { ElMessage } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import { getApprovalList, approveIndicators, rejectIndicators, getIndicatorTree } from '@/api/indicator'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const rawData = ref<any[]>([])
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  orgId: undefined as number | undefined,
  approvalStatus: ''
})

const searchFields: SearchField[] = [
  { prop: 'examGroupId', label: '考核组', type: 'input', placeholder: '考核组ID' },
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
      { label: '被退回', value: 'REJECTED' }
    ]
  }
]

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
  REJECTED: { text: '被退回', type: 'danger' as const }
}

const roleCode = computed(() => userStore.userInfo?.roleCode || 'FIN_ADMIN')

function aggregateByGroup(records: any[]) {
  const map = new Map<string, any>()
  for (const item of records) {
    const key = `${item.examGroupId}-${item.orgId}`
    if (!map.has(key)) {
      map.set(key, {
        examGroupId: item.examGroupId,
        orgId: item.orgId,
        examGroupName: item.examGroupName,
        orgName: item.orgName,
        submittedBy: item.submittedBy,
        submittedTime: item.submittedTime,
        approvalStatus: item.approvalStatus
      })
    }
  }
  return Array.from(map.values())
}

function loadData() {
  loading.value = true
  getApprovalList({ ...queryParams, roleCode: roleCode.value })
    .then((res: any) => {
      rawData.value = res.data.records
      tableData.value = aggregateByGroup(rawData.value)
      total.value = tableData.value.length
    })
    .finally(() => {
      loading.value = false
    })
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
  queryParams.examGroupId = undefined
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
const indicatorTreeData = ref<any[]>([])

function handleApprove(row: any) {
  currentRow.value = row
  approvalDialogVisible.value = true
  // 获取指标树并展平为表格数据
  if (row.examGroupId && row.orgId) {
    getIndicatorTree(row.examGroupId, row.orgId).then((res: any) => {
      const flatList: any[] = []
      res.data.forEach((cat: any) => {
        cat.subCategories.forEach((sub: any) => {
          sub.items.forEach((item: any) => {
            flatList.push({
              id: item.id,
              categoryName: cat.categoryName,
              subCategory: sub.subCategory,
              content: item.content,
              targetDesc: item.targetDesc,
              weightAnnual: item.weightAnnual,
              weightMonthly: item.weightMonthly,
              evaluationStandard: item.evaluationStandard
            })
          })
        })
      })
      indicatorTreeData.value = flatList
    })
  }
}

function handleView(row: any) {
  handleApprove(row)
}

function handlePass() {
  if (!currentRow.value) return
  const indicatorIds = indicatorTreeData.value.map(item => item.id)
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
    const indicatorIds = indicatorTreeData.value.map(item => item.id)
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

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.indicator-approval {
  padding: 16px;
}
.approval-header {
  margin-bottom: 16px;
}
.indicator-tree-table {
  margin-top: 8px;
}
</style>
