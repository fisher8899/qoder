<template>
  <div class="monthly-exam">
    <div class="page-header">
      <h2>月度考核管理 v1</h2>
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
      <template #selfEvalRate="{ row }">
        <el-progress :percentage="row.selfEvalRate" :status="row.selfEvalRate === 100 ? 'success' : undefined" />
      </template>
      <template #peerEvalRate="{ row }">
        <el-progress :percentage="row.peerEvalRate" :status="row.peerEvalRate === 100 ? 'success' : undefined" />
      </template>
      <template #status="{ row }">
        <StatusTag :status="row.status" :status-map="statusMap" />
      </template>
      <template #operation="{ row }">
        <el-button v-if="row.status === 'PRE_PUBLISHED'" link type="warning" @click="handleCancelPrePublish(row)">
          取消预发布
        </el-button>
        <el-button v-if="row.status === 'PRE_PUBLISHED'" link type="success" @click="handlePublish(row)">
          绩效发布
        </el-button>
        <el-button link type="primary" @click="handleViewProgress(row)">查看进度</el-button>
      </template>
    </DataTable>

    <!-- 查看进度弹框 -->
    <el-dialog
      v-model="progressDialogVisible"
      title="各部门完成情况"
      width="800px"
      destroy-on-close
    >
      <el-table :data="progressList" border size="small" v-loading="progressLoading">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column label="部门名称" prop="orgName" min-width="160" />
        <el-table-column label="自评状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.selfEvalStatus === '已完成' ? 'COMPLETED' : 'PENDING'" :status-map="progressStatusMap" />
          </template>
        </el-table-column>
        <el-table-column label="他评完成率" width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.peerEvalProgress" :status="row.peerEvalProgress === 100 ? 'success' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="整体状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.overallStatus === '已完成' ? 'COMPLETED' : 'PENDING'" :status-map="progressStatusMap" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import {
  getMonthlyExamList,
  getMonthlyExamDeptProgress
} from '@/api/monthlyExam'
import type { ExamGroup } from '@/api/types'
import type { DeptProgress } from '@/api/monthlyExam'
import { cancelPrePublishExamGroup, publishExamGroup } from '@/api/examGroup'

const loading = ref(false)
const progressLoading = ref(false)
const rawData = ref<any[]>([])
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  groupName: '',
  status: ''
})

const searchFields: SearchField[] = [
  {
    prop: 'groupName',
    label: '考核组名称',
    type: 'input',
    placeholder: '请输入考核组名称'
  },
  {
    prop: 'status',
    label: '考核状态',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '全部', value: '' },
      { label: '进行中', value: 'IN_PROGRESS' },
      { label: '预发布', value: 'PRE_PUBLISHED' },
      { label: '已发布', value: 'PUBLISHED' }
    ]
  }
]

const columns: TableColumn[] = [
  { prop: 'groupName', label: '考核组名称', minWidth: 180 },
  { prop: 'examType', label: '考核月份', width: 120 },
  { prop: 'selfEvalRate', label: '自评完成率', width: 180 },
  { prop: 'peerEvalRate', label: '他评完成率', width: 180 },
  { prop: 'status', label: '考核状态', width: 120 },
  { prop: 'operation', label: '操作', width: 200 }
]

const statusMap = {
  NOT_STARTED: { text: '待启动', type: 'info' as const },
  IN_PROGRESS: { text: '进行中', type: 'warning' as const },
  COMPLETED: { text: '已完成', type: 'success' as const },
  PRE_PUBLISHED: { text: '预发布', type: 'primary' as const },
  PUBLISHED: { text: '已发布', type: 'success' as const }
}

const progressStatusMap = {
  COMPLETED: { text: '已完成', type: 'success' as const },
  PENDING: { text: '待完成', type: 'warning' as const }
}

const progressDialogVisible = ref(false)
const progressList = ref<DeptProgress[]>([])
const currentRow = ref<any>(null)

function filterData() {
  let list = [...rawData.value]
  if (queryParams.groupName) {
    list = list.filter((item: any) => item.groupName?.includes(queryParams.groupName))
  }
  if (queryParams.status) {
    list = list.filter((item: any) => item.status === queryParams.status)
  }
  total.value = list.length
  const start = (queryParams.current - 1) * queryParams.size
  tableData.value = list.slice(start, start + queryParams.size)
}

function loadData() {
  loading.value = true
  getMonthlyExamList()
    .then((res: any) => {
      const groups: ExamGroup[] = res.data || []
      const rows = groups.map(g => ({
        examGroupId: g.id,
        groupName: g.groupName,
        examType: g.examType,
        startDate: g.startDate,
        endDate: g.endDate,
        status: g.status,
        selfEvalRate: 0,
        peerEvalRate: 0
      }))
      rawData.value = rows
      // 加载每个考核组的进度
      loadProgressForAll(rows)
      filterData()
    })
    .finally(() => {
      loading.value = false
    })
}

function loadProgressForAll(rows: any[]) {
  for (const row of rows) {
    getMonthlyExamDeptProgress(row.examGroupId).then((res: any) => {
      const depts: DeptProgress[] = res.data || []
      if (depts.length > 0) {
        const selfDone = depts.filter(d => d.selfEvalStatus === '已完成').length
        const peerDone = depts.filter(d => d.overallStatus === '已完成').length
        row.selfEvalRate = Math.round((selfDone / depts.length) * 100)
        row.peerEvalRate = Math.round((peerDone / depts.length) * 100)
      }
    })
  }
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.groupName = data.groupName || ''
  queryParams.status = data.status || ''
  filterData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.groupName = ''
  queryParams.status = ''
  filterData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  filterData()
}

function handleCancelPrePublish(row: any) {
  cancelPrePublishExamGroup(row.examGroupId).then(() => {
    ElMessage.success('已取消预发布')
    loadData()
  })
}

function handlePublish(row: any) {
  publishExamGroup(row.examGroupId).then(() => {
    ElMessage.success('绩效发布成功')
    loadData()
  })
}

function handleViewProgress(row: any) {
  currentRow.value = row
  progressDialogVisible.value = true
  progressLoading.value = true
  getMonthlyExamDeptProgress(row.examGroupId)
    .then((res: any) => {
      progressList.value = res.data || []
    })
    .finally(() => {
      progressLoading.value = false
    })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.monthly-exam {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
  h2 {
    margin: 0 0 4px 0;
    font-size: 20px;
  }
}
</style>
