<template>
  <div class="peer-evaluation">
    <div class="page-header">
      <h2>部门他评打分 v1</h2>
      <p class="subtitle">对其他部门的考核指标进行评估打分</p>
    </div>

    <SearchForm
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
      <el-tab-pane label="按部门" name="dept">
        <DataTable
          :columns="deptColumns"
          :data="deptTableData"
          :loading="loading"
          :total="deptTotal"
          :current-page="deptQuery.current"
          :page-size="deptQuery.size"
          @page-change="handleDeptPageChange"
        >
          <template #status="{ row }">
            <StatusTag :status="row.status" :status-map="statusMap" />
          </template>
          <template #progress="{ row }">
            <el-progress :percentage="row.progress" :status="row.progress === 100 ? 'success' : undefined" />
          </template>
          <template #operation="{ row }">
            <el-button link type="primary" @click="handleDeptEval(row)">评估反馈</el-button>
          </template>
        </DataTable>
      </el-tab-pane>

      <el-tab-pane label="按指标" name="indicator">
        <DataTable
          :columns="indicatorColumns"
          :data="indicatorTableData"
          :loading="indicatorLoading"
          :total="indicatorTotal"
          :current-page="indicatorQuery.current"
          :page-size="indicatorQuery.size"
          @page-change="handleIndicatorPageChange"
        >
          <template #operation="{ row }">
            <el-button link type="primary" @click="handleIndicatorEval(row)">评估反馈</el-button>
          </template>
        </DataTable>
      </el-tab-pane>
    </el-tabs>

    <!-- 按部门评估弹框 -->
    <el-dialog
      v-model="deptDialogVisible"
      title="评估反馈"
      width="85%"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="dialog-header" v-if="currentTargetDept">
        <span>目标部门：{{ currentTargetDept.targetOrgName }}</span>
      </div>

      <el-table :data="deptIndicatorList" border size="small" v-loading="dialogLoading" max-height="500">
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
              v-model="row.peerScore"
              :min="0"
              :max="100"
              :precision="2"
              controls-position="right"
              size="small"
              style="width: 90px"
            />
          </template>
        </el-table-column>
        <el-table-column label="打分说明" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.scoreComment" placeholder="请输入" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :status="row.status || 'PENDING'" :status-map="rowStatusMap" />
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="deptDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleSaveDeptEval">保存</el-button>
        <el-button type="success" @click="handleSubmitDeptEval">提交</el-button>
      </template>
    </el-dialog>

    <!-- 按指标评估弹框 -->
    <el-dialog
      v-model="indicatorDialogVisible"
      title="评估反馈"
      width="85%"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="dialog-header" v-if="currentIndicator">
        <span>指标：{{ currentIndicator.content }}</span>
        <span>大类：{{ currentIndicator.categoryName }} / {{ currentIndicator.subCategory }}</span>
      </div>

      <el-table :data="indicatorDeptList" border size="small" v-loading="dialogLoading" max-height="500">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column label="目标部门" prop="targetOrgName" width="150" />
        <el-table-column label="得分" width="110">
          <template #default="{ row }">
            <el-input-number
              v-model="row.peerScore"
              :min="0"
              :max="100"
              :precision="2"
              controls-position="right"
              size="small"
              style="width: 90px"
            />
          </template>
        </el-table-column>
        <el-table-column label="打分说明" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.scoreComment" placeholder="请输入" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :status="row.status || 'PENDING'" :status-map="rowStatusMap" />
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="indicatorDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleSaveIndicatorEval">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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
  getPeerEvalByIndicator,
  savePeerEval,
  submitPeerEval
} from '@/api/peerEval'
import type { PeerEvalTask, PeerEvalTargetDept, PeerEvalIndicator, PeerEvalByIndicator, PeerEvalSaveData } from '@/api/peerEval'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const activeTab = ref('dept')
const loading = ref(false)
const indicatorLoading = ref(false)
const dialogLoading = ref(false)

// 按部门数据
const rawDeptData = ref<PeerEvalTargetDept[]>([])
const deptTableData = ref<PeerEvalTargetDept[]>([])
const deptTotal = ref(0)
const currentTask = ref<PeerEvalTask | null>(null)

const deptQuery = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  examType: '',
  status: ''
})

// 按指标数据
const rawIndicatorData = ref<PeerEvalByIndicator[]>([])
const indicatorTableData = ref<PeerEvalByIndicator[]>([])
const indicatorTotal = ref(0)

const indicatorQuery = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  categoryId: undefined as number | undefined
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
    prop: 'status',
    label: '打分状态',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '全部', value: '' },
      { label: '待打分', value: 'PENDING' },
      { label: '已完成', value: 'COMPLETED' }
    ]
  }
]

const deptColumns: TableColumn[] = [
  { prop: 'targetOrgName', label: '目标部门名称', minWidth: 180 },
  { prop: 'totalIndicators', label: '总指标数', width: 90 },
  { prop: 'evaluatedCount', label: '已打分数', width: 90 },
  { prop: 'progress', label: '完成进度', width: 180 },
  { prop: 'status', label: '状态', width: 100 },
  { prop: 'operation', label: '操作', width: 100 }
]

const indicatorColumns: TableColumn[] = [
  { prop: 'categoryName', label: '指标大类', width: 120 },
  { prop: 'subCategory', label: '指标小类', width: 120 },
  { prop: 'content', label: '考核内容', minWidth: 200 },
  { prop: 'totalDepts', label: '评估部门数', width: 100 },
  { prop: 'scoredCount', label: '已打分数', width: 90 },
  { prop: 'operation', label: '操作', width: 100 }
]

const statusMap = {
  PENDING: { text: '待打分', type: 'warning' as const },
  COMPLETED: { text: '已完成', type: 'success' as const }
}

const rowStatusMap = {
  PENDING: { text: '待填写', type: 'info' as const },
  DRAFT: { text: '草稿', type: 'warning' as const },
  SUBMITTED: { text: '已提交', type: 'success' as const }
}

// 弹框数据
const deptDialogVisible = ref(false)
const currentTargetDept = ref<PeerEvalTargetDept | null>(null)
const deptIndicatorList = ref<any[]>([])

const indicatorDialogVisible = ref(false)
const currentIndicator = ref<PeerEvalByIndicator | null>(null)
const indicatorDeptList = ref<any[]>([])

function filterDeptData() {
  let list = [...rawDeptData.value]
  if (deptQuery.status) {
    list = list.filter((item: PeerEvalTargetDept) => item.status === deptQuery.status)
  }
  deptTotal.value = list.length
  const start = (deptQuery.current - 1) * deptQuery.size
  deptTableData.value = list.slice(start, start + deptQuery.size)
}

function filterIndicatorData() {
  let list = [...rawIndicatorData.value]
  deptTotal.value = list.length
  const start = (indicatorQuery.current - 1) * indicatorQuery.size
  indicatorTableData.value = list.slice(start, start + indicatorQuery.size)
  indicatorTotal.value = list.length
}

function loadTaskList() {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId) {
    ElMessage.warning('无法获取当前用户部门信息')
    return
  }
  loading.value = true
  getPeerEvalTaskList({ evaluatorOrgId })
    .then((res: any) => {
      const tasks: PeerEvalTask[] = res.data || []
      if (tasks.length > 0) {
        currentTask.value = tasks[0]
        loadDeptTargets(tasks[0].examGroupId, evaluatorOrgId)
        loadIndicatorData(tasks[0].examGroupId, evaluatorOrgId)
      }
    })
    .finally(() => {
      loading.value = false
    })
}

function loadDeptTargets(examGroupId: number, evaluatorOrgId: number) {
  loading.value = true
  getPeerEvalTargets(examGroupId, evaluatorOrgId)
    .then((res: any) => {
      rawDeptData.value = res.data || []
      filterDeptData()
    })
    .finally(() => {
      loading.value = false
    })
}

function loadIndicatorData(examGroupId: number, evaluatorOrgId: number) {
  indicatorLoading.value = true
  getPeerEvalByIndicator(examGroupId, evaluatorOrgId)
    .then((res: any) => {
      rawIndicatorData.value = res.data || []
      filterIndicatorData()
    })
    .finally(() => {
      indicatorLoading.value = false
    })
}

function handleSearch(data: Record<string, any>) {
  deptQuery.current = 1
  deptQuery.examGroupId = data.examGroupId
  deptQuery.examType = data.examType || ''
  deptQuery.status = data.status || ''
  filterDeptData()
}

function handleReset() {
  deptQuery.current = 1
  deptQuery.examGroupId = undefined
  deptQuery.examType = ''
  deptQuery.status = ''
  filterDeptData()
}

function handleTabChange() {
  if (activeTab.value === 'dept') {
    filterDeptData()
  } else {
    filterIndicatorData()
  }
}

function handleDeptPageChange(page: number, size: number) {
  deptQuery.current = page
  deptQuery.size = size
  filterDeptData()
}

function handleIndicatorPageChange(page: number, size: number) {
  indicatorQuery.current = page
  indicatorQuery.size = size
  filterIndicatorData()
}

function handleDeptEval(row: PeerEvalTargetDept) {
  currentTargetDept.value = row
  deptDialogVisible.value = true
  loadDeptIndicators(row.targetOrgId)
}

function loadDeptIndicators(targetOrgId: number) {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId || !currentTask.value) return
  dialogLoading.value = true
  getPeerEvalByDept(currentTask.value.examGroupId, evaluatorOrgId, targetOrgId)
    .then((res: any) => {
      deptIndicatorList.value = (res.data || []).map((item: PeerEvalIndicator) => ({ ...item }))
    })
    .finally(() => {
      dialogLoading.value = false
    })
}

function handleIndicatorEval(row: PeerEvalByIndicator) {
  currentIndicator.value = row
  indicatorDialogVisible.value = true
  indicatorDeptList.value = (row.deptScores || []).map((item: any) => ({ ...item }))
}

function handleSaveDeptEval() {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId || !currentTask.value) return

  const promises = deptIndicatorList.value
    .filter((row: any) => row.peerScore !== null && row.peerScore !== undefined)
    .map((row: any) => {
      const data: PeerEvalSaveData = {
        id: row.peerEvalId || undefined,
        examGroupId: currentTask.value!.examGroupId,
        evaluatorOrgId,
        targetOrgId: currentTargetDept.value!.targetOrgId,
        indicatorId: row.indicatorId,
        peerScore: row.peerScore,
        scoreComment: row.scoreComment
      }
      return savePeerEval(data)
    })

  Promise.all(promises).then(() => {
    ElMessage.success('保存成功')
    loadDeptIndicators(currentTargetDept.value!.targetOrgId)
    if (currentTask.value) {
      loadDeptTargets(currentTask.value.examGroupId, evaluatorOrgId)
    }
  }).catch(() => {
    ElMessage.error('保存失败')
  })
}

function handleSubmitDeptEval() {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId || !currentTask.value || !currentTargetDept.value) return

  const promises = deptIndicatorList.value
    .filter((row: any) => row.peerScore !== null && row.peerScore !== undefined)
    .map((row: any) => {
      const data: PeerEvalSaveData = {
        id: row.peerEvalId || undefined,
        examGroupId: currentTask.value!.examGroupId,
        evaluatorOrgId,
        targetOrgId: currentTargetDept.value!.targetOrgId,
        indicatorId: row.indicatorId,
        peerScore: row.peerScore,
        scoreComment: row.scoreComment
      }
      return savePeerEval(data)
    })

  Promise.all(promises).then(() => {
    ElMessageBox.confirm('提交后将无法修改，确认提交？', '确认提交', { type: 'warning' }).then(() => {
      submitPeerEval(currentTask.value!.examGroupId, evaluatorOrgId, currentTargetDept.value!.targetOrgId).then(() => {
        ElMessage.success('提交成功')
        deptDialogVisible.value = false
        loadDeptTargets(currentTask.value!.examGroupId, evaluatorOrgId)
      })
    })
  }).catch(() => {
    ElMessage.error('保存失败')
  })
}

function handleSaveIndicatorEval() {
  const evaluatorOrgId = userStore.userInfo?.orgId
  if (!evaluatorOrgId || !currentTask.value || !currentIndicator.value) return

  const promises = indicatorDeptList.value
    .filter((row: any) => row.peerScore !== null && row.peerScore !== undefined)
    .map((row: any) => {
      const data: PeerEvalSaveData = {
        examGroupId: currentTask.value!.examGroupId,
        evaluatorOrgId,
        targetOrgId: row.targetOrgId,
        indicatorId: currentIndicator.value!.indicatorId,
        peerScore: row.peerScore,
        scoreComment: row.scoreComment
      }
      return savePeerEval(data)
    })

  Promise.all(promises).then(() => {
    ElMessage.success('保存成功')
    indicatorDialogVisible.value = false
    loadIndicatorData(currentTask.value!.examGroupId, evaluatorOrgId)
  }).catch(() => {
    ElMessage.error('保存失败')
  })
}

onMounted(() => {
  loadTaskList()
})
</script>

<style scoped lang="scss">
.peer-evaluation {
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
