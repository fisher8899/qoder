<template>
  <div class="appeal-management">
    <div class="page-header">
      <h2>申诉管理 v1</h2>
      <p class="subtitle">处理部门考核结果申诉</p>
    </div>

    <!-- 搜索区 -->
    <SearchForm
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>创建申诉
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
      <template #newScore="{ row }">
        {{ row.newScore !== null && row.newScore !== undefined ? row.newScore : '—' }}
      </template>
      <template #status="{ row }">
        <StatusTag :status="row.status" :status-map="appealStatusMap" />
      </template>
      <template #operation="{ row }">
        <template v-if="row.status === 'DRAFT'">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="handleSubmit(row)">提交</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
        <template v-else-if="row.status === 'PENDING_REEVAL'">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
          <el-button link type="warning" @click="handleReassign(row)">退回</el-button>
        </template>
        <template v-else-if="row.status === 'HANDLED'">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
        </template>
      </template>
    </DataTable>

    <!-- 创建/编辑申诉弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑申诉' : '创建申诉'"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="考核组" prop="examGroupId">
          <el-select v-model="formData.examGroupId" placeholder="请选择考核组" style="width: 100%" @change="onExamGroupChange">
            <el-option
              v-for="item in examGroupOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="申诉部门" prop="appealOrgId">
          <el-select v-model="formData.appealOrgId" placeholder="请选择申诉部门" style="width: 100%" @change="onAppealOrgChange">
            <el-option
              v-for="item in orgOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="打分部门" prop="scorerOrgId">
          <el-select v-model="formData.scorerOrgId" placeholder="请选择打分部门" style="width: 100%" @change="onScorerOrgChange">
            <el-option
              v-for="item in scorerOrgOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="相关指标" prop="indicatorId">
          <el-select v-model="formData.indicatorId" placeholder="请选择相关指标" style="width: 100%">
            <el-option
              v-for="item in indicatorOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原始得分">
          <el-input-number v-model="formData.originalScore" :controls="false" style="width: 100%" disabled />
        </el-form-item>
        <el-form-item label="申诉说明" prop="appealReason">
          <el-input
            v-model="formData.appealReason"
            type="textarea"
            :rows="4"
            placeholder="请输入申诉说明，最多500字"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="申诉材料">
          <el-upload
            v-model:file-list="fileList"
            action="#"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            multiple
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">每个文件不超过20MB</div>
            </template>
          </el-upload>
          <div v-if="uploadedAttachments.length > 0" class="attachment-list">
            <div v-for="att in uploadedAttachments" :key="att.id" class="attachment-item">
              <span>{{ att.fileName }}</span>
              <el-button link type="danger" size="small" @click="removeAttachment(att.id)">删除</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button @click="handleSaveDraft">保存草稿</el-button>
        <el-button type="primary" @click="handleSubmitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>

    <!-- 查看申诉弹框 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="申诉详情"
      width="600px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="考核组">
          <span>{{ viewData.examGroupName }}</span>
        </el-form-item>
        <el-form-item label="申诉部门">
          <span>{{ viewData.appealOrgName }}</span>
        </el-form-item>
        <el-form-item label="打分部门">
          <span>{{ viewData.scorerOrgName }}</span>
        </el-form-item>
        <el-form-item label="原始得分">
          <span>{{ viewData.originalScore }}</span>
        </el-form-item>
        <el-form-item label="新得分">
          <span>{{ viewData.newScore !== null ? viewData.newScore : '—' }}</span>
        </el-form-item>
        <el-form-item label="申诉状态">
          <StatusTag :status="viewData.status" :status-map="appealStatusMap" />
        </el-form-item>
        <el-form-item label="申诉说明">
          <span>{{ viewData.appealReason }}</span>
        </el-form-item>
        <el-form-item label="附件">
          <div v-if="viewData.attachments && viewData.attachments.length > 0">
            <div v-for="att in viewData.attachments" :key="att.id" class="attachment-item">
              <span>{{ att.fileName }}</span>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import { getExamGroupList } from '@/api/examGroup'
import { getExamGroupMembers } from '@/api/examGroup'
import { getOrganizationList } from '@/api/organization'
import { getIndicatorList } from '@/api/indicator'
import {
  getAppealList,
  createAppeal,
  submitAppeal,
  reassignAppeal,
  deleteAppeal,
  getAppealDetail,
  uploadAppealAttachment,
  deleteAppealAttachment
} from '@/api/appeal'
import type { AppealItem, AppealAttachment } from '@/api/appeal'

const loading = ref(false)
const tableData = ref<AppealItem[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  appealOrgId: undefined as number | undefined,
  status: ''
})

const searchFields = ref<SearchField[]>([
  {
    prop: 'examGroupId',
    label: '考核组名称',
    type: 'select',
    placeholder: '请选择考核组',
    options: []
  },
  {
    prop: 'appealOrgId',
    label: '申诉部门',
    type: 'select',
    placeholder: '请选择申诉部门',
    options: []
  },
  {
    prop: 'status',
    label: '申诉状态',
    type: 'select',
    placeholder: '请选择申诉状态',
    options: [
      { label: '全部', value: '' },
      { label: '草稿', value: 'DRAFT' },
      { label: '待重新评估', value: 'PENDING_REEVAL' },
      { label: '已处理', value: 'HANDLED' }
    ]
  }
])

const columns: TableColumn[] = [
  { prop: 'examGroupName', label: '考核组名称', minWidth: 160 },
  { prop: 'appealOrgName', label: '申诉部门', minWidth: 120 },
  { prop: 'scorerOrgName', label: '打分部门', minWidth: 120 },
  { prop: 'originalScore', label: '原始得分', width: 100, align: 'center' },
  { prop: 'newScore', label: '新得分', width: 100, align: 'center' },
  { prop: 'status', label: '申诉状态', width: 120, align: 'center' },
  { prop: 'createdTime', label: '创建时间', width: 160 },
  { prop: 'operation', label: '操作', width: 200, align: 'center' }
]

const appealStatusMap = {
  DRAFT: { text: '草稿', type: 'info' as const },
  PENDING_REEVAL: { text: '待重新评估', type: 'warning' as const },
  HANDLED: { text: '已处理', type: 'success' as const }
}

const examGroupOptions = ref<any[]>([])
const examGroupMap = ref<Record<number, string>>({})
const orgOptions = ref<any[]>([])
const scorerOrgOptions = ref<any[]>([])
const indicatorOptions = ref<any[]>([])

function loadExamGroups() {
  getExamGroupList({ current: 1, size: 1000 }).then((res: any) => {
    examGroupOptions.value = (res.data?.records || []).map((g: any) => ({
      label: g.groupName,
      value: g.id
    }))
    examGroupMap.value = {}
    ;(res.data?.records || []).forEach((g: any) => {
      examGroupMap.value[g.id] = g.groupName
    })
    searchFields.value[0].options = examGroupOptions.value
  })
}

function loadOrgs() {
  getOrganizationList({ current: 1, size: 1000 }).then((res: any) => {
    orgOptions.value = (res.data?.records || []).map((o: any) => ({
      label: o.orgName,
      value: o.id
    }))
    searchFields.value[1].options = orgOptions.value
  })
}

function loadData() {
  loading.value = true
  getAppealList({ ...queryParams })
    .then((res: any) => {
      tableData.value = (res.data?.records || []).map((item: AppealItem) => ({
        ...item,
        examGroupName: examGroupMap.value[item.examGroupId] || ''
      }))
      total.value = res.data?.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.examGroupId = data.examGroupId
  queryParams.appealOrgId = data.appealOrgId
  queryParams.status = data.status || ''
  loadData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.examGroupId = undefined
  queryParams.appealOrgId = undefined
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
const viewDialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<any>(null)
const formData = reactive({
  id: undefined as number | undefined,
  examGroupId: undefined as number | undefined,
  appealOrgId: undefined as number | undefined,
  appealOrgName: '',
  scorerOrgId: undefined as number | undefined,
  scorerOrgName: '',
  indicatorId: undefined as number | undefined,
  appealReason: '',
  originalScore: undefined as number | undefined
})

const formRules = {
  examGroupId: [{ required: true, message: '请选择考核组', trigger: 'change' }],
  appealOrgId: [{ required: true, message: '请选择申诉部门', trigger: 'change' }],
  scorerOrgId: [{ required: true, message: '请选择打分部门', trigger: 'change' }],
  appealReason: [{ required: true, message: '请输入申诉说明', trigger: 'blur' }]
}

const fileList = ref<any[]>([])
const uploadedAttachments = ref<AppealAttachment[]>([])

function handleCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: AppealItem) {
  isEdit.value = true
  resetForm()
  formData.id = row.id
  formData.examGroupId = row.examGroupId
  formData.appealOrgId = row.appealOrgId
  formData.appealOrgName = row.appealOrgName
  formData.scorerOrgId = row.scorerOrgId
  formData.scorerOrgName = row.scorerOrgName
  formData.indicatorId = row.indicatorId
  formData.appealReason = row.appealReason
  formData.originalScore = row.originalScore
  dialogVisible.value = true
}

function resetForm() {
  formData.id = undefined
  formData.examGroupId = undefined
  formData.appealOrgId = undefined
  formData.appealOrgName = ''
  formData.scorerOrgId = undefined
  formData.scorerOrgName = ''
  formData.indicatorId = undefined
  formData.appealReason = ''
  formData.originalScore = undefined
  fileList.value = []
  uploadedAttachments.value = []
  scorerOrgOptions.value = []
  indicatorOptions.value = []
}

function onExamGroupChange(val: number) {
  if (!val) {
    scorerOrgOptions.value = []
    indicatorOptions.value = []
    return
  }
  getExamGroupMembers(val).then((res: any) => {
    const members = res.data || []
    scorerOrgOptions.value = members.map((m: any) => ({
      label: m.orgName,
      value: m.orgId
    }))
  })
  getIndicatorList({ examGroupId: val, current: 1, size: 1000 }).then((res: any) => {
    indicatorOptions.value = (res.data?.records || []).map((ind: any) => ({
      label: `${ind.categoryName} - ${ind.subCategory} - ${ind.content}`,
      value: ind.id
    }))
  })
}

function onAppealOrgChange(val: number) {
  const org = orgOptions.value.find(o => o.value === val)
  if (org) {
    formData.appealOrgName = org.label
  }
}

function onScorerOrgChange(val: number) {
  const org = scorerOrgOptions.value.find(o => o.value === val)
  if (org) {
    formData.scorerOrgName = org.label
  }
}

function handleFileChange(file: any) {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('单个文件不能超过20MB')
    const idx = fileList.value.indexOf(file)
    if (idx > -1) fileList.value.splice(idx, 1)
    return
  }
}

function handleFileRemove() {
  // el-upload自动处理
}

function removeAttachment(id: number) {
  deleteAppealAttachment(id).then(() => {
    uploadedAttachments.value = uploadedAttachments.value.filter(a => a.id !== id)
    ElMessage.success('删除成功')
  })
}

async function uploadFiles(appealId: number) {
  for (const file of fileList.value) {
    if (file.raw) {
      try {
        const res: any = await uploadAppealAttachment(file.raw, appealId)
        if (res.data) {
          uploadedAttachments.value.push(res.data)
        }
      } catch (e) {
        // ignore
      }
    }
  }
}

function handleSaveDraft() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const data = {
      examGroupId: formData.examGroupId!,
      appealOrgId: formData.appealOrgId!,
      appealOrgName: formData.appealOrgName,
      scorerOrgId: formData.scorerOrgId!,
      scorerOrgName: formData.scorerOrgName,
      indicatorId: formData.indicatorId,
      appealReason: formData.appealReason,
      originalScore: formData.originalScore
    }
    createAppeal(data).then(async (res: any) => {
      const appeal = res.data
      if (appeal?.id) {
        await uploadFiles(appeal.id)
      }
      ElMessage.success('保存草稿成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleSubmitAppeal() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const data = {
      examGroupId: formData.examGroupId!,
      appealOrgId: formData.appealOrgId!,
      appealOrgName: formData.appealOrgName,
      scorerOrgId: formData.scorerOrgId!,
      scorerOrgName: formData.scorerOrgName,
      indicatorId: formData.indicatorId,
      appealReason: formData.appealReason,
      originalScore: formData.originalScore
    }
    createAppeal(data).then(async (res: any) => {
      const appeal = res.data
      if (appeal?.id) {
        await uploadFiles(appeal.id)
        await submitAppeal(appeal.id)
      }
      ElMessage.success('提交申诉成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleSubmit(row: AppealItem) {
  ElMessageBox.confirm('确认提交该申诉？', '提示').then(() => {
    submitAppeal(row.id).then(() => {
      ElMessage.success('提交成功')
      loadData()
    })
  })
}

function handleDelete(row: AppealItem) {
  ElMessageBox.confirm('确认删除该申诉？', '提示', { type: 'warning' }).then(() => {
    deleteAppeal(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  })
}

function handleReassign(row: AppealItem) {
  ElMessageBox.confirm('确认退回该申诉给打分部门重新评估？', '提示').then(() => {
    reassignAppeal(row.id).then(() => {
      ElMessage.success('退回成功')
      loadData()
    })
  })
}

const viewData = reactive({
  examGroupName: '',
  appealOrgName: '',
  scorerOrgName: '',
  originalScore: undefined as number | undefined,
  newScore: undefined as number | undefined,
  status: '',
  appealReason: '',
  attachments: [] as AppealAttachment[]
})

function handleView(row: AppealItem) {
  getAppealDetail(row.id).then((res: any) => {
    const data = res.data
    const appeal = data.appeal || {}
    viewData.examGroupName = examGroupOptions.value.find(g => g.value === appeal.examGroupId)?.label || ''
    viewData.appealOrgName = appeal.appealOrgName
    viewData.scorerOrgName = appeal.scorerOrgName
    viewData.originalScore = appeal.originalScore
    viewData.newScore = appeal.newScore
    viewData.status = appeal.status
    viewData.appealReason = appeal.appealReason
    viewData.attachments = data.attachments || []
    viewDialogVisible.value = true
  })
}

onMounted(() => {
  loadExamGroups()
  loadOrgs()
  loadData()
})
</script>

<style scoped lang="scss">
.appeal-management {
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

.toolbar {
  margin-bottom: 16px;
  display: flex;
  justify-content: flex-end;
}

.attachment-list {
  margin-top: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}
</style>
