<template>
  <div class="self-evaluation">
    <div class="page-header">
      <h2>月度考核自评 v1</h2>
      <p class="subtitle">完成部门月度考核自评打分</p>
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
      :show-pagination="true"
      @page-change="handlePageChange"
    >
      <template #status="{ row }">
        <StatusTag :status="row.status" :status-map="statusMap" />
      </template>
      <template #progress="{ row }">
        <el-progress :percentage="row.progress" :status="row.progress === 100 ? 'success' : undefined" />
      </template>
      <template #operation="{ row }">
        <el-button link type="primary" @click="handleStart(row)">
          {{ row.status === 'SUBMITTED' ? '查看' : '开始自评' }}
        </el-button>
      </template>
    </DataTable>

    <!-- 自评弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="currentTask?.status === 'SUBMITTED' ? '查看自评' : '开始自评'"
      width="90%"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="dialog-header" v-if="currentTask">
        <span>考核组：{{ currentTask.groupName }}</span>
        <span>部门：{{ userStore.userInfo?.orgName }}</span>
        <span v-if="currentTask.startDate && currentTask.endDate">
          考核月份：{{ currentTask.startDate }} 至 {{ currentTask.endDate }}
        </span>
      </div>

      <el-table :data="indicatorList" border size="small" v-loading="dialogLoading" max-height="500">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column label="指标大类" prop="categoryName" width="120" />
        <el-table-column label="指标小类" prop="subCategory" width="120" />
        <el-table-column label="考核内容" prop="content" min-width="160" show-overflow-tooltip />
        <el-table-column label="指标/目标" prop="targetDesc" min-width="140" show-overflow-tooltip />
        <el-table-column label="权重(年度)" prop="weightAnnual" width="90" />
        <el-table-column label="权重(月度)" prop="weightMonthly" width="90" />
        <el-table-column label="考核标准" prop="evaluationStandard" min-width="160" show-overflow-tooltip />
        <el-table-column label="实际完成" width="160">
          <template #default="{ row }">
            <el-input
              v-if="!isReadonly"
              v-model="row.actualCompletion"
              type="textarea"
              :rows="2"
              placeholder="请输入"
              size="small"
            />
            <span v-else>{{ row.actualCompletion }}</span>
          </template>
        </el-table-column>
        <el-table-column label="自评得分" width="110">
          <template #default="{ row }">
            <el-input-number
              v-if="!isReadonly"
              v-model="row.selfScore"
              :min="0"
              :max="100"
              :precision="2"
              controls-position="right"
              size="small"
              style="width: 90px"
              @change="calcResult(row)"
            />
            <span v-else>{{ row.selfScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="自评结果" width="100">
          <template #default="{ row }">
            {{ formatResult(row.selfResult) }}
          </template>
        </el-table-column>
        <el-table-column label="附件" width="140">
          <template #default="{ row }">
            <template v-if="!isReadonly">
              <el-upload
                :action="uploadAction"
                :headers="uploadHeaders"
                :show-file-list="false"
                :before-upload="beforeUpload"
                :on-success="(res: any) => handleUploadSuccess(res, row)"
                :on-error="handleUploadError"
                accept=".pdf,.jpg,.jpeg,.png,.zip,.rar,.7z"
              >
                <el-button link type="primary" size="small">上传</el-button>
              </el-upload>
            </template>
            <a
              v-if="row.attachmentUrl"
              :href="row.attachmentUrl"
              target="_blank"
              class="file-link"
            >
              {{ row.attachmentName || '查看附件' }}
            </a>
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
        <el-button v-if="!isReadonly" type="primary" @click="handleSaveDraft">保存草稿</el-button>
        <el-button v-if="!isReadonly" type="success" @click="handleSubmit">提交自评</el-button>
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
  getSelfEvalTaskList,
  getSelfEvalIndicators,
  saveSelfEval,
  submitSelfEval
} from '@/api/selfEval'
import type { SelfEvalTask, SelfEvalIndicator, SelfEvalSaveData } from '@/api/selfEval'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const dialogLoading = ref(false)
const rawData = ref<SelfEvalTask[]>([])
const tableData = ref<SelfEvalTask[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  examType: '',
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
    prop: 'status',
    label: '自评状态',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '全部', value: '' },
      { label: '待提交', value: 'PENDING' },
      { label: '已提交', value: 'SUBMITTED' }
    ]
  }
]

const columns: TableColumn[] = [
  { prop: 'groupName', label: '考核组名称', minWidth: 180 },
  { prop: 'examType', label: '考核类型', width: 100 },
  { prop: 'status', label: '月度考核自评状态', width: 140 },
  { prop: 'totalIndicators', label: '总指标数', width: 90 },
  { prop: 'evaluatedCount', label: '已评数', width: 80 },
  { prop: 'progress', label: '完成进度', width: 180 },
  { prop: 'operation', label: '操作', width: 100 }
]

const statusMap = {
  PENDING: { text: '待提交', type: 'warning' as const },
  SUBMITTED: { text: '已提交', type: 'success' as const }
}

const rowStatusMap = {
  PENDING: { text: '待填写', type: 'info' as const },
  DRAFT: { text: '草稿', type: 'warning' as const },
  SUBMITTED: { text: '已提交', type: 'success' as const }
}

const dialogVisible = ref(false)
const currentTask = ref<SelfEvalTask | null>(null)
const indicatorList = ref<any[]>([])

const isReadonly = computed(() => currentTask.value?.status === 'SUBMITTED')

const uploadAction = '/api/evaluation/self/upload'
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

function formatResult(val: number | null) {
  if (val === null || val === undefined) return '-'
  return Number(val).toFixed(4)
}

function calcResult(row: any) {
  const score = row.selfScore
  const weight = row.weightMonthly
  if (score !== null && score !== undefined && weight !== null && weight !== undefined) {
    row.selfResult = (Number(score) * Number(weight)) / 100
  } else {
    row.selfResult = null
  }
}

function filterData() {
  let list = [...rawData.value]
  if (queryParams.examGroupId) {
    list = list.filter((item: SelfEvalTask) => item.examGroupId === queryParams.examGroupId)
  }
  if (queryParams.examType) {
    list = list.filter((item: SelfEvalTask) => item.examType === queryParams.examType)
  }
  if (queryParams.status) {
    list = list.filter((item: SelfEvalTask) => item.status === queryParams.status)
  }
  total.value = list.length
  const start = (queryParams.current - 1) * queryParams.size
  tableData.value = list.slice(start, start + queryParams.size)
}

function loadData() {
  const orgId = userStore.userInfo?.orgId
  if (!orgId) {
    ElMessage.warning('无法获取当前用户部门信息')
    return
  }
  loading.value = true
  getSelfEvalTaskList({ orgId })
    .then((res: any) => {
      rawData.value = res.data || []
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
  queryParams.status = data.status || ''
  filterData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.examGroupId = undefined
  queryParams.examType = ''
  queryParams.status = ''
  filterData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  filterData()
}

function handleStart(row: SelfEvalTask) {
  currentTask.value = row
  dialogVisible.value = true
  loadIndicators(row.examGroupId)
}

function loadIndicators(examGroupId: number) {
  const orgId = userStore.userInfo?.orgId
  if (!orgId) return
  dialogLoading.value = true
  getSelfEvalIndicators(examGroupId, orgId)
    .then((res: any) => {
      indicatorList.value = (res.data || []).map((item: SelfEvalIndicator) => ({ ...item }))
    })
    .finally(() => {
      dialogLoading.value = false
    })
}

function beforeUpload(file: File) {
  const maxSize = 20 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过20MB')
    return false
  }
  const allowed = ['pdf', 'jpg', 'jpeg', 'png', 'zip', 'rar', '7z']
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowed.includes(ext)) {
    ElMessage.error('不支持的文件类型')
    return false
  }
  return true
}

function handleUploadSuccess(res: any, row: any) {
  if (res.code === 200 && res.data) {
    row.attachmentUrl = res.data.url
    row.attachmentName = res.data.name
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function handleUploadError() {
  ElMessage.error('上传失败')
}

function handleSaveDraft() {
  const orgId = userStore.userInfo?.orgId
  if (!orgId || !currentTask.value) return

  const promises = indicatorList.value
    .filter((row: any) => row.selfScore !== null && row.selfScore !== undefined)
    .map((row: any) => {
      const data: SelfEvalSaveData = {
        id: row.selfEvalId || undefined,
        examGroupId: currentTask.value!.examGroupId,
        orgId,
        indicatorId: row.indicatorId,
        actualCompletion: row.actualCompletion,
        selfScore: row.selfScore,
        attachmentUrl: row.attachmentUrl,
        attachmentName: row.attachmentName
      }
      return saveSelfEval(data)
    })

  Promise.all(promises).then(() => {
    ElMessage.success('草稿保存成功')
    loadIndicators(currentTask.value!.examGroupId)
    loadData()
  }).catch(() => {
    ElMessage.error('保存失败')
  })
}

function handleSubmit() {
  const orgId = userStore.userInfo?.orgId
  if (!orgId || !currentTask.value) return

  // 先保存所有已填写的内容
  const promises = indicatorList.value
    .filter((row: any) => row.selfScore !== null && row.selfScore !== undefined)
    .map((row: any) => {
      const data: SelfEvalSaveData = {
        id: row.selfEvalId || undefined,
        examGroupId: currentTask.value!.examGroupId,
        orgId,
        indicatorId: row.indicatorId,
        actualCompletion: row.actualCompletion,
        selfScore: row.selfScore,
        attachmentUrl: row.attachmentUrl,
        attachmentName: row.attachmentName
      }
      return saveSelfEval(data)
    })

  Promise.all(promises).then(() => {
    ElMessageBox.confirm('提交后将无法修改，确认提交？', '确认提交', { type: 'warning' }).then(() => {
      submitSelfEval(currentTask.value!.examGroupId, orgId).then(() => {
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
.self-evaluation {
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
.file-link {
  color: var(--el-color-primary);
  text-decoration: none;
  font-size: 12px;
  margin-left: 4px;
}
</style>
