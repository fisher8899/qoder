<template>
  <div class="eval-score">
    <div class="page-header">
      <h2>评估打分 v1</h2>
      <p class="subtitle">对其他部门的考核指标进行评估打分</p>
    </div>

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
      <template #status="{ row }">
        <StatusTag :status="row.status" :status-map="statusMap" />
      </template>
      <template #operation="{ row }">
        <el-button link type="primary" @click="handleEval(row)">
          {{ row.status === 'COMPLETED' ? '查看' : '评估反馈' }}
        </el-button>
      </template>
    </DataTable>

    <!-- 评估反馈弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="currentTarget?.status === 'COMPLETED' ? '查看评估' : '评估反馈'"
      width="85%"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="dialog-header" v-if="currentTarget">
        <span>考核组：{{ currentTask?.groupName }}</span>
        <span>目标部门：{{ currentTarget.targetOrgName }}</span>
      </div>

      <el-table :data="indicatorList" border size="small" v-loading="dialogLoading" max-height="500">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column label="指标大类" prop="categoryName" width="120" />
        <el-table-column label="指标小类" prop="subCategory" width="120" />
        <el-table-column label="考核内容" prop="content" min-width="160" show-overflow-tooltip />
        <el-table-column label="指标/目标" prop="targetDesc" min-width="140" show-overflow-tooltip />
        <el-table-column label="考核标准" prop="evaluationStandard" min-width="160" show-overflow-tooltip />
        <el-table-column label="权重(年度)" prop="weightAnnual" width="90" />
        <el-table-column label="权重(月度)" prop="weightMonthly" width="90" />
        <el-table-column label="得分" width="110">
          <template #default="{ row }">
            <el-input-number
              v-if="!isReadonly"
              v-model="row.peerScore"
              :min="0"
              :max="100"
              :precision="2"
              controls-position="right"
              size="small"
              style="width: 90px"
            />
            <span v-else>{{ row.peerScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="打分说明" min-width="160">
          <template #default="{ row }">
            <el-input v-if="!isReadonly" v-model="row.scoreComment" placeholder="请输入" size="small" />
            <span v-else>{{ row.scoreComment }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :status="row.status || 'PENDING'" :status-map="rowStatusMap" />
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="!isReadonly" type="primary" @click="handleSave">保存</el-button>
        <el-button v-if="!isReadonly" type="success" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import {
  getPeerEvalTaskList,
  getPeerEvalTargets,
  getPeerEvalByDept,
  savePeerEval,
  submitPeerEval
} from '@/api/peerEval'
import type { PeerEvalTask, PeerEvalTargetDept, PeerEvalIndicator, PeerEvalSaveData } from '@/api/peerEval'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const dialogLoading = ref(false)
const rawData = ref<any[]>([])
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  examType: '',
  targetOrgId: undefined as number | undefined,
  status: ''
})

const searchFields: SearchField[] = [
  {
    prop: 'examGroupId',
    label: '考核组名称',
    type: 'select',
    placeholder: '请选择',
    options: []
  },
  {
    prop: 'examType',
    label: '考核类型',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '月度考核', value: 'MONTHLY' },
      { label: '年度考核', value: 'ANNUAL' }
    ]
  },
  {
    prop: 'targetOrgId',
    label: '考核部门',
    type: 'select',
    placeholder: '请选择',
    options: []
  },
  {
    prop: 'status',
    label: '考核状态',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '全部', value: '' },
      { label: '待评估', value: 'PENDING' },
      { label: '已完成', value: 'COMPLETED' }
    ]
  }
]

const columns: TableColumn[] = [
  { prop: 'groupName', label: '考核组名称', minWidth: 180 },
  { prop: 'examType', label: '考核类型', width: 100 },
  { prop: 'status', label: '考核评估状态', width: 120 },
  { prop: 'startDate', label: '考核开始日期', width: 120 },
  { prop: 'endDate', label: '考核结束日期', width: 120 },
  { prop: 'operation', label: '操作', width: 120 }
]

const statusMap = {
  PENDING: { text: '待评估', type: 'warning' as const },
  COMPLETED: { text: '已完成', type: 'success' as const }
}

const rowStatusMap = {
  PENDING: { text: '待填写', type: 'info' as const },
  DRAFT: { text: '草稿', type: 'warning' as const },
  SUBMITTED: { text: '已提交', type: 'success' as const }
}

const dialogVisible = ref(false)
const currentTask = ref<PeerEvalTask | null>(null)
const currentTarget = ref<PeerEvalTargetDept | null>(null)
const indicatorList = ref<any[]>([])

const isReadonly = computed(() => currentTarget.value?.status === 'COMPLETED')

function filterData() {
  let list = [...rawData.value]
  if (queryParams.examGroupId) {
    list = list.filter((item: any) => item.examGroupId === queryParams.examGroupId)
  }
  if (queryParams.examType) {
    list = list.filter((item: any) => item.examType === queryParams.examType)
  }
  if (queryParams.targetOrgId) {
    list = list.filter((item: any) => item.targetOrgId === queryParams.targetOrgId)
  }
  if (queryParams.status) {
    list = list.filter((item: any) => item.status === queryParams.status)
  }
  total.value = list.length
  const start = (queryParams.current - 1) * queryParams.size
  tableData.value = list.slice(start, start + queryParams.size)
}

function loadData() {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId) {
    ElMessage.warning('无法获取当前用户部门信息')
    return
  }
  loading.value = true
  getPeerEvalTaskList({ evaluatorOrgId })
    .then((res: any) => {
      const tasks: PeerEvalTask[] = res.data || []
      const rows: any[] = []
      let pendingCount = 0
      for (const task of tasks) {
        // 获取每个任务的目标部门
        // 这里简化处理，将任务本身作为行展示
        rows.push({
          examGroupId: task.examGroupId,
          groupName: task.groupName,
          examType: task.examType,
          status: task.status,
          startDate: '',
          endDate: ''
        })
        if (task.status === 'PENDING') pendingCount++
      }
      rawData.value = rows
      filterData()
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.examGroupId = data.examGroupId
  queryParams.examType = data.examType || ''
  queryParams.targetOrgId = data.targetOrgId
  queryParams.status = data.status || ''
  filterData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.examGroupId = undefined
  queryParams.examType = ''
  queryParams.targetOrgId = undefined
  queryParams.status = ''
  filterData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  filterData()
}

function handleEval(row: any) {
  currentTask.value = { examGroupId: row.examGroupId, groupName: row.groupName, examType: row.examType, status: row.status, totalIndicators: 0, evaluatedCount: 0, progress: 0 }
  // 加载该考核组的目标部门列表，选择第一个进行评估
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId) return
  dialogLoading.value = true
  getPeerEvalTargets(row.examGroupId, evaluatorOrgId)
    .then((res: any) => {
      const targets: PeerEvalTargetDept[] = res.data || []
      if (targets.length > 0) {
        currentTarget.value = targets[0]
        dialogVisible.value = true
        loadIndicators(row.examGroupId, evaluatorOrgId, targets[0].targetOrgId)
      } else {
        ElMessage.info('暂无需要评估的部门')
      }
    })
    .finally(() => {
      dialogLoading.value = false
    })
}

function loadIndicators(examGroupId: number, evaluatorOrgId: number, targetOrgId: number) {
  dialogLoading.value = true
  getPeerEvalByDept(examGroupId, evaluatorOrgId, targetOrgId)
    .then((res: any) => {
      indicatorList.value = (res.data || []).map((item: PeerEvalIndicator) => ({ ...item }))
    })
    .finally(() => {
      dialogLoading.value = false
    })
}

function handleSave() {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId || !currentTask.value || !currentTarget.value) return

  const promises = indicatorList.value
    .filter((row: any) => row.peerScore !== null && row.peerScore !== undefined)
    .map((row: any) => {
      const data: PeerEvalSaveData = {
        id: row.peerEvalId || undefined,
        examGroupId: currentTask.value!.examGroupId,
        evaluatorOrgId,
        targetOrgId: currentTarget.value!.targetOrgId,
        indicatorId: row.indicatorId,
        peerScore: row.peerScore,
        scoreComment: row.scoreComment
      }
      return savePeerEval(data)
    })

  Promise.all(promises).then(() => {
    ElMessage.success('保存成功')
    loadIndicators(currentTask.value!.examGroupId, evaluatorOrgId, currentTarget.value!.targetOrgId)
    loadData()
  }).catch(() => {
    ElMessage.error('保存失败')
  })
}

function handleSubmit() {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId || !currentTask.value || !currentTarget.value) return

  const promises = indicatorList.value
    .filter((row: any) => row.peerScore !== null && row.peerScore !== undefined)
    .map((row: any) => {
      const data: PeerEvalSaveData = {
        id: row.peerEvalId || undefined,
        examGroupId: currentTask.value!.examGroupId,
        evaluatorOrgId,
        targetOrgId: currentTarget.value!.targetOrgId,
        indicatorId: row.indicatorId,
        peerScore: row.peerScore,
        scoreComment: row.scoreComment
      }
      return savePeerEval(data)
    })

  Promise.all(promises).then(() => {
    ElMessageBox.confirm('提交后将无法修改，确认提交？', '确认提交', { type: 'warning' }).then(() => {
      submitPeerEval(currentTask.value!.examGroupId, evaluatorOrgId, currentTarget.value!.targetOrgId).then(() => {
        ElMessage.success('提交成功')
        dialogVisible.value = false
        loadData()
      })
    })
  }).catch(() => {
    ElMessage.error('保存失败')
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.eval-score {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
  h2 {
    margin: 0 0 4px 0;
    font-size: 20px;
  }
  .subtitle {
    margin: 0;
    color: var(--text-secondary);
    font-size: 14px;
  }
}
.dialog-header {
  margin-bottom: 12px;
  display: flex;
  gap: 24px;
  font-weight: bold;
  color: var(--text-primary);
}
</style>
