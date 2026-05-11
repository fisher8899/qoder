<template>
  <div class="exam-group-management">
    <!-- 搜索区 -->
    <SearchForm
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>新增考核组
      </el-button>
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
      <template #examCategory="{ row }">
        {{ examCategoryMap[row.examCategory] || row.examCategory }}
      </template>
      <template #progress="{ row }">
        <el-progress :percentage="row.progress || 0" :stroke-width="12" />
      </template>
      <template #status="{ row }">
        <StatusTag :status="row.status" :status-map="examStatusMap" />
      </template>
      <template #operation="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
      </template>
      <template #bizOperation="{ row }">
        <div class="biz-btns">
          <el-button
            v-for="btn in getBizButtons(row)"
            :key="btn.key"
            :type="btn.type"
            :disabled="btn.disabled"
            size="small"
            @click="btn.handler"
          >
            {{ btn.label }}
          </el-button>
        </div>
      </template>
    </DataTable>

    <!-- 新增/编辑弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑考核组' : '新增考核组'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="考核组名称" prop="groupName">
          <el-input v-model="formData.groupName" placeholder="请输入考核组名称" />
        </el-form-item>
        <el-form-item label="考核类别" prop="examCategory">
          <el-select v-model="formData.examCategory" placeholder="请选择考核类别" style="width: 100%" @change="handleCategoryChange">
            <el-option label="业绩指标设定" value="INDICATOR_SET" />
            <el-option label="绩效考核" value="PERFORMANCE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.examCategory !== 'INDICATOR_SET'" label="考核类型" prop="examType">
          <el-select v-model="formData.examType" placeholder="请选择考核类型" style="width: 100%">
            <el-option label="月度考核" value="MONTHLY" />
            <el-option label="年度考核" value="ANNUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="formData.startDate" type="date" placeholder="选择开始日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="formData.endDate" type="date" placeholder="选择结束日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 成员维护弹框（穿梭式布局） -->
    <el-dialog
      v-model="memberDialogVisible"
      title="成员维护"
      width="900px"
      destroy-on-close
    >
      <div class="member-dialog-body">
        <!-- 左侧：候选成员 -->
        <div class="candidate-panel">
          <div class="panel-title">候选成员 <span class="panel-count">({{ candidateMembers.length }})</span></div>
          <el-table ref="candidateTableRef" :data="candidateMembers" size="small" border height="400" @selection-change="handleCandidateSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="orgName" label="组织名称" />
            <el-table-column prop="orgType" label="类型" width="100">
              <template #default="{ row }">
                {{ row.orgType === 'FUNCTIONAL' ? '职能部门' : '分公司' }}
              </template>
            </el-table-column>
          </el-table>
          <div class="panel-actions">
            <el-button type="primary" size="small" :disabled="candidateSelection.length === 0" @click="addSelectedMembers">
              添加选中 →
            </el-button>
          </div>
        </div>
        <!-- 右侧：已选成员 -->
        <div class="selected-panel">
          <div class="panel-title">已选成员 <span class="panel-count">({{ selectedMembers.length }})</span></div>
          <el-table :data="selectedMembers" size="small" border height="400">
            <el-table-column prop="orgName" label="组织名称" />
            <el-table-column prop="orgType" label="类型" width="100">
              <template #default="{ row }">
                {{ row.orgType === 'FUNCTIONAL' ? '职能部门' : '分公司' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="removeSelectedMember(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import {
  getExamGroupList,
  createExamGroup,
  updateExamGroup,
  deleteExamGroup,
  startExamGroup,
  restartExamGroup,
  publishExamGroupIndicator,
  startExamGroupExam,
  startPeerEval,
  prePublishExamGroup,
  publishExamGroup,
  cancelPrePublishExamGroup,
  getExamGroupMembers,
  addExamGroupMembers,
  removeExamGroupMember,
  getExamGroupProgress
} from '@/api/examGroup'
import type { ExamGroupCreateData } from '@/api/examGroup'
import { getOrganizationList } from '@/api/organization'

const router = useRouter()

const examCategoryMap: Record<string, string> = {
  'INDICATOR_SET': '业绩指标设定',
  'PERFORMANCE': '业绩考核'
}

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  groupName: '',
  examType: '',
  status: ''
})

const searchFields: SearchField[] = [
  { prop: 'groupName', label: '考核组名称', type: 'input', placeholder: '请输入考核组名称' },
  {
    prop: 'examType',
    label: '考核类型',
    type: 'select',
    placeholder: '请选择考核类型',
    options: [
      { label: '月度考核', value: 'MONTHLY' },
      { label: '年度考核', value: 'ANNUAL' }
    ]
  },
  {
    prop: 'status',
    label: '考核状态',
    type: 'select',
    placeholder: '请选择考核状态',
    options: [
      { label: '待启动', value: 'NOT_STARTED' },
      { label: '进行中', value: 'IN_PROGRESS' },
      { label: '已完成', value: 'COMPLETED' },
      { label: '预发布', value: 'PRE_PUBLISHED' },
      { label: '已发布', value: 'PUBLISHED' }
    ]
  }
]

const columns: TableColumn[] = [
  { prop: 'groupName', label: '考核组名称', minWidth: 180, fixed: 'left' },
  { prop: 'examCategory', label: '考核类别', width: 120 },
  { prop: 'examType', label: '考核类型', width: 100 },
  { prop: 'startDate', label: '考核开始日期', width: 120 },
  { prop: 'endDate', label: '考核结束日期', width: 120 },
  { prop: 'progress', label: '完成进度', width: 180 },
  { prop: 'status', label: '状态', width: 100 },
  { prop: 'operation', label: '操作', width: 120 },
  { prop: 'bizOperation', label: '业务操作', width: 400, fixed: 'right' }
]

const examStatusMap = {
  NOT_STARTED: { text: '待启动', type: 'info' as const },
  IN_PROGRESS: { text: '进行中', type: 'primary' as const },
  COMPLETED: { text: '已完成', type: 'success' as const },
  PRE_PUBLISHED: { text: '预发布', type: 'warning' as const },
  PUBLISHED: { text: '已发布', type: 'success' as const }
}

function loadData() {
  loading.value = true
  getExamGroupList({ ...queryParams })
    .then((res: any) => {
      tableData.value = res.data.records
      total.value = res.data.total
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.groupName = data.groupName || ''
  queryParams.examType = data.examType || ''
  queryParams.status = data.status || ''
  loadData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.groupName = ''
  queryParams.examType = ''
  queryParams.status = ''
  loadData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  loadData()
}

// 弹框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<any>(null)
const formData = reactive<ExamGroupCreateData>({
  groupName: '',
  examCategory: '',
  examType: '',
  startDate: '',
  endDate: ''
})

const formRules = {
  groupName: [{ required: true, message: '请输入考核组名称', trigger: 'blur' }],
  examCategory: [{ required: true, message: '请选择考核类别', trigger: 'change' }],
  examType: [{ required: true, message: '请选择考核类型', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

function handleAdd() {
  isEdit.value = false
  formData.groupName = ''
  formData.examCategory = ''
  formData.examType = ''
  formData.startDate = ''
  formData.endDate = ''
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.groupName = row.groupName
  formData.examCategory = row.examCategory
  formData.examType = row.examType
  formData.startDate = row.startDate
  formData.endDate = row.endDate
  dialogVisible.value = true
}

function handleCategoryChange(val: string) {
  if (val === 'INDICATOR_SET') {
    formData.examType = ''
  }
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const submitData = { ...formData }
    if (submitData.examCategory === 'INDICATOR_SET') {
      submitData.examType = ''
    }
    const api = isEdit.value ? updateExamGroup : createExamGroup
    api(submitData).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该考核组？', '提示', { type: 'warning' }).then(() => {
    deleteExamGroup(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  })
}

// 业务操作按钮
function getBizButtons(row: any) {
  const step = row.currentStep || '成员维护'
  const btns: any[] = []

  const addBtn = (key: string, label: string, type: string, disabled: boolean, handler: () => void) => {
    btns.push({ key, label, type, disabled, handler })
  }

  // 1.成员维护
  const isMemberMaintained = step !== '成员维护'
  addBtn('member', '1.成员维护', isMemberMaintained ? 'info' : 'primary', false, () => openMemberDialog(row))

  // 2.启动
  const isStarted = step !== '成员维护' && step !== '待启动'
  addBtn('start', '2.启动', isStarted ? 'info' : 'primary', isStarted, () => {
    ElMessageBox.confirm('确认启动该考核组？', '提示').then(() => {
      startExamGroup(row.id).then(() => { ElMessage.success('启动成功'); loadData() })
    })
  })

  // 2.1 重新启动（仅已启动/IN_PROGRESS状态显示，与启动按钮互斥）
  if (row.status === 'IN_PROGRESS') {
    addBtn('restart', '重新启动', 'warning', false, () => {
      ElMessageBox.confirm('确认重新启动？将补发未发送的通知。', '提示').then(() => {
        restartExamGroup(row.id).then(() => { ElMessage.success('重新启动成功，已补发未发送的通知'); loadData() })
      })
    })
  }

  // 3.发布考核指标
  if (isStarted) {
    const isIndicatorPublished = step !== '已启动'
    addBtn('publishIndicator', '发布考核指标', isIndicatorPublished ? 'info' : 'primary', step !== '已启动', () => {
      publishExamGroupIndicator(row.id).then(() => { ElMessage.success('发布考核指标成功'); loadData() })
    })
  }

  // 4.启动考核（业绩指标设定类别不展示）
  if (row.examCategory !== 'INDICATOR_SET' && (step === '指标已发布' || step === '考核中' || step === '他评中' || step === '预发布' || step === '已发布')) {
    const isExamStarted = step !== '指标已发布'
    addBtn('startExam', '3.启动考核', isExamStarted ? 'info' : 'primary', step !== '指标已发布', () => {
      startExamGroupExam(row.id).then(() => { ElMessage.success('启动考核成功'); loadData() })
    })
  }

  // 5.启动他评
  if (isStarted && step !== '成员维护' && step !== '已启动' && step !== '指标已发布') {
    const isPeerStarted = step !== '考核中'
    addBtn('startPeer', '4.启动他评', isPeerStarted ? 'info' : 'primary', step !== '考核中', () => {
      startPeerEval(row.id).then(() => { ElMessage.success('启动他评成功'); loadData() })
    })
  }

  // 6.绩效预发布
  if (step === '他评中' || step === '预发布' || step === '已发布') {
    const isPrePublished = step === '预发布' || step === '已发布'
    addBtn('prePublish', '5.绩效预发布', isPrePublished ? 'warning' : 'primary', step !== '他评中', () => {
      prePublishExamGroup(row.id).then(() => { ElMessage.success('预发布成功'); loadData() })
    })
  }

  // 7.绩效发布
  if (step === '预发布' || step === '已发布') {
    const isPublished = step === '已发布'
    addBtn('publish', '6.绩效发布', isPublished ? 'success' : 'success', step !== '预发布', () => {
      ElMessageBox.confirm('确认正式发布？发布后不可撤回。', '提示').then(() => {
        publishExamGroup(row.id).then(() => { ElMessage.success('发布成功'); loadData() })
      })
    })
  }

  // 8.查看进度
  if (isStarted) {
    addBtn('progress', '查看进度', 'primary', false, () => {
      router.push({ path: '/exam/indicator-progress', query: { examGroupId: String(row.id), groupName: row.groupName } })
    })
  }

  return btns
}

// 成员维护弹框
const memberDialogVisible = ref(false)
const currentGroupId = ref<number>(0)
const selectedMembers = ref<any[]>([])
const allOrgs = ref<any[]>([])
const candidateSelection = ref<any[]>([])
const candidateTableRef = ref<any>(null)

// 候选成员 = 全部可选成员 - 已选成员
// selectedMembers 中 orgId 为组织ID，allOrgs 中 id 为组织ID
const candidateMembers = computed(() => {
  const selectedOrgIds = new Set(selectedMembers.value.map((m: any) => m.orgId))
  return allOrgs.value.filter((m: any) => !selectedOrgIds.has(m.id))
})

function openMemberDialog(row: any) {
  currentGroupId.value = row.id
  memberDialogVisible.value = true
  candidateSelection.value = []
  loadMembers()
  loadAllOrgs()
}

function loadMembers() {
  getExamGroupMembers(currentGroupId.value).then((res: any) => {
    selectedMembers.value = res.data || []
  })
}

function loadAllOrgs() {
  getOrganizationList({ current: 1, size: 1000 }).then((res: any) => {
    allOrgs.value = res.data?.records || []
  })
}

function handleCandidateSelectionChange(selection: any[]) {
  candidateSelection.value = selection
}

function addSelectedMembers() {
  if (candidateSelection.value.length === 0) {
    ElMessage.warning('请先选择候选成员')
    return
  }
  const orgIds = candidateSelection.value.map((item: any) => item.id)
  addExamGroupMembers(currentGroupId.value, orgIds).then(() => {
    ElMessage.success('添加成功')
    candidateSelection.value = []
    loadMembers()
  })
}

function removeSelectedMember(row: any) {
  removeExamGroupMember(currentGroupId.value, row.id).then(() => {
    ElMessage.success('移除成功')
    loadMembers()
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.exam-group-management {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  justify-content: flex-end;
}
.biz-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.member-dialog-body {
  display: flex;
  gap: 20px;
}
.candidate-panel,
.selected-panel {
  flex: 1;
  min-width: 0;
}
.panel-title {
  font-weight: bold;
  margin-bottom: 10px;
  font-size: 14px;
  .panel-count {
    color: #909399;
    font-weight: normal;
  }
}
.panel-actions {
  margin-top: 10px;
  text-align: center;
}
</style>
