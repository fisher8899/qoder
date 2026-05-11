<template>
  <div class="appeal-reeval">
    <div class="page-header">
      <h2>申诉重新评估 v1</h2>
      <p class="subtitle">对接收的申诉进行重新评估打分</p>
    </div>

    <!-- 数据表格 -->
    <DataTable
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :total="total"
      :current-page="queryParams.current"
      :page-size="queryParams.size"
      @page-change="handlePageChange"
    >
      <template #appealReason="{ row }">
        <el-tooltip :content="row.appealReason" placement="top" show-after="500">
          <span class="ellipsis">{{ truncate(row.appealReason, 30) }}</span>
        </el-tooltip>
      </template>
      <template #status="{ row }">
        <StatusTag :status="row.status" :status-map="appealStatusMap" />
      </template>
      <template #operation="{ row }">
        <el-button v-if="row.status === 'PENDING_REEVAL'" link type="primary" @click="handleReScore(row)">
          重新打分
        </el-button>
        <el-button link type="primary" @click="handleView(row)">查看</el-button>
      </template>
    </DataTable>

    <!-- 重新打分弹框 -->
    <el-dialog
      v-model="reScoreDialogVisible"
      title="重新评估打分"
      width="600px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="申诉说明">
          <div class="readonly-text">{{ currentAppeal.appealReason }}</div>
        </el-form-item>
        <el-form-item label="原始得分">
          <div class="readonly-text">{{ currentAppeal.originalScore }}</div>
        </el-form-item>
        <el-form-item label="附件列表">
          <div v-if="currentAppeal.attachments && currentAppeal.attachments.length > 0">
            <div v-for="att in currentAppeal.attachments" :key="att.id" class="attachment-item">
              <el-link type="primary" @click="downloadFile(att)">{{ att.fileName }}</el-link>
            </div>
          </div>
          <span v-else>—</span>
        </el-form-item>
        <el-form-item label="新打分" required>
          <el-input-number
            v-model="reScoreForm.newScore"
            :min="0"
            :max="100"
            :precision="2"
            :controls="false"
            style="width: 200px"
            placeholder="请输入新打分"
          />
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input
            v-model="reScoreForm.handleComment"
            type="textarea"
            :rows="3"
            placeholder="请输入处理说明"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reScoreDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitReScore">提交</el-button>
      </template>
    </el-dialog>

    <!-- 查看弹框 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="申诉详情"
      width="600px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="考核组">
          <span>{{ currentAppeal.examGroupName }}</span>
        </el-form-item>
        <el-form-item label="申诉部门">
          <span>{{ currentAppeal.appealOrgName }}</span>
        </el-form-item>
        <el-form-item label="原始得分">
          <span>{{ currentAppeal.originalScore }}</span>
        </el-form-item>
        <el-form-item label="新得分">
          <span>{{ currentAppeal.newScore !== null ? currentAppeal.newScore : '—' }}</span>
        </el-form-item>
        <el-form-item label="申诉说明">
          <span>{{ currentAppeal.appealReason }}</span>
        </el-form-item>
        <el-form-item label="附件">
          <div v-if="currentAppeal.attachments && currentAppeal.attachments.length > 0">
            <div v-for="att in currentAppeal.attachments" :key="att.id" class="attachment-item">
              <el-link type="primary" @click="downloadFile(att)">{{ att.fileName }}</el-link>
            </div>
          </div>
          <span v-else>—</span>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import { getPendingReevalList, reScoreAppeal, getAppealDetail } from '@/api/appeal'
import type { AppealItem, AppealAttachment } from '@/api/appeal'
import { getExamGroupList } from '@/api/examGroup'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<AppealItem[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10
})

const columns: TableColumn[] = [
  { prop: 'examGroupName', label: '考核组名称', minWidth: 160 },
  { prop: 'appealOrgName', label: '申诉部门', minWidth: 120 },
  { prop: 'originalScore', label: '原始得分', width: 100, align: 'center' },
  { prop: 'appealReason', label: '申诉说明', minWidth: 200 },
  { prop: 'status', label: '状态', width: 120, align: 'center' },
  { prop: 'operation', label: '操作', width: 150, align: 'center' }
]

const appealStatusMap = {
  DRAFT: { text: '草稿', type: 'info' as const },
  PENDING_REEVAL: { text: '待重新评估', type: 'warning' as const },
  HANDLED: { text: '已处理', type: 'success' as const }
}

const examGroupMap = ref<Record<number, string>>({})

function loadExamGroups() {
  getExamGroupList({ current: 1, size: 1000 }).then((res: any) => {
    const map: Record<number, string> = {}
    ;(res.data?.records || []).forEach((g: any) => {
      map[g.id] = g.groupName
    })
    examGroupMap.value = map
  })
}

function loadData() {
  const orgId = userStore.userInfo?.orgId
  if (!orgId) {
    tableData.value = []
    return
  }
  loading.value = true
  getPendingReevalList(orgId)
    .then((res: any) => {
      const list = (res.data || []).map((item: AppealItem) => ({
        ...item,
        examGroupName: examGroupMap.value[item.examGroupId] || ''
      }))
      tableData.value = list
      total.value = list.length
    })
    .finally(() => {
      loading.value = false
    })
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  loadData()
}

function truncate(text: string, len: number) {
  if (!text) return ''
  return text.length > len ? text.substring(0, len) + '...' : text
}

const reScoreDialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentAppeal = reactive<{
  id: number
  examGroupName: string
  appealOrgName: string
  originalScore: number | undefined
  newScore: number | undefined
  appealReason: string
  status: string
  attachments: AppealAttachment[]
}>({
  id: 0,
  examGroupName: '',
  appealOrgName: '',
  originalScore: undefined,
  newScore: undefined,
  appealReason: '',
  status: '',
  attachments: []
})

const reScoreForm = reactive({
  newScore: undefined as number | undefined,
  handleComment: ''
})

function handleReScore(row: AppealItem) {
  currentAppeal.id = row.id
  currentAppeal.examGroupName = examGroupMap.value[row.examGroupId] || ''
  currentAppeal.appealOrgName = row.appealOrgName
  currentAppeal.originalScore = row.originalScore
  currentAppeal.newScore = row.newScore
  currentAppeal.appealReason = row.appealReason
  currentAppeal.status = row.status
  currentAppeal.attachments = []
  reScoreForm.newScore = undefined
  reScoreForm.handleComment = ''

  getAppealDetail(row.id).then((res: any) => {
    currentAppeal.attachments = res.data?.attachments || []
  })

  reScoreDialogVisible.value = true
}

function handleView(row: AppealItem) {
  currentAppeal.id = row.id
  currentAppeal.examGroupName = examGroupMap.value[row.examGroupId] || ''
  currentAppeal.appealOrgName = row.appealOrgName
  currentAppeal.originalScore = row.originalScore
  currentAppeal.newScore = row.newScore
  currentAppeal.appealReason = row.appealReason
  currentAppeal.status = row.status
  currentAppeal.attachments = []

  getAppealDetail(row.id).then((res: any) => {
    currentAppeal.attachments = res.data?.attachments || []
    viewDialogVisible.value = true
  })
}

function handleSubmitReScore() {
  if (reScoreForm.newScore === undefined || reScoreForm.newScore === null) {
    ElMessage.warning('请输入新打分')
    return
  }
  reScoreAppeal(currentAppeal.id, {
    newScore: reScoreForm.newScore,
    handleComment: reScoreForm.handleComment
  }).then(() => {
    ElMessage.success('重新打分提交成功')
    reScoreDialogVisible.value = false
    loadData()
  })
}

function downloadFile(att: AppealAttachment) {
  // 简化实现
  ElMessage.info('下载功能：' + att.fileName)
}

onMounted(() => {
  loadExamGroups()
  setTimeout(() => loadData(), 300)
})
</script>

<style scoped lang="scss">
.appeal-reeval {
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

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.readonly-text {
  padding: 8px 0;
  color: var(--text-primary);
  line-height: 1.5;
}

.attachment-item {
  padding: 4px 0;
}
</style>
