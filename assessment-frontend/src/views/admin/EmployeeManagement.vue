<template>
  <div class="page-card">
    <h2 class="page-title">人员管理 v1</h2>
    <p class="page-subtitle">管理人员基本信息，支持从人事系统自动同步</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="姓名/编号" clearable />
      </el-form-item>
      <el-form-item label="所属部门">
        <el-select v-model="searchForm.deptId" placeholder="全部" clearable style="width: 180px">
          <el-option
            v-for="item in deptOptions"
            :key="item.id"
            :label="item.orgName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="在职状态">
        <el-select v-model="searchForm.isActive" placeholder="全部" clearable style="width: 120px">
          <el-option label="在职" :value="1" />
          <el-option label="离职" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
      <el-form-item style="margin-left: auto">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增人员
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table :data="tableData" stripe v-loading="loading" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="employeeNo" label="人员编号" width="120" />
      <el-table-column prop="employeeName" label="人员姓名" width="120" />
      <el-table-column prop="unitId" label="所属单位" width="140">
        <template #default="{ row }">
          {{ unitOptions.find((u: any) => u.id === row.unitId)?.unitName || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="deptName" label="部门名称" min-width="160" />
      <el-table-column prop="position" label="岗位" width="140" />
      <el-table-column prop="level" label="级别" width="100">
        <template #default="{ row }">
          {{ levelMap[row.level] || row.level || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="isActive" label="在职状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isActive === 1" type="success" size="small">在职</el-tag>
          <el-tag v-else type="danger" size="small">离职</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isInvalid" label="失效状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isInvalid === 1" type="info" size="small">已失效</el-tag>
          <el-tag v-else type="success" size="small">有效</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <span class="total-text">共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.size"
        :total="total"
        layout="prev, pager, next, jumper"
        @change="handlePageChange"
      />
    </div>

    <!-- 新增/编辑弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑人员' : '新增人员'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="人员编号" prop="employeeNo">
          <el-input v-model="formData.employeeNo" placeholder="请输入人员编号" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="人员姓名" prop="employeeName">
          <el-input v-model="formData.employeeName" placeholder="请输入人员姓名" />
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-select v-model="formData.deptId" placeholder="请选择所属部门" style="width: 100%" @change="handleDeptChange">
            <el-option
              v-for="item in deptOptions"
              :key="item.id"
              :label="item.orgName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属单位" prop="unitId">
          <el-select v-model="formData.unitId" placeholder="请选择所属单位" style="width: 100%">
            <el-option
              v-for="item in unitOptions"
              :key="item.id"
              :label="item.unitName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位" prop="position">
          <el-input v-model="formData.position" placeholder="请输入岗位" />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-select v-model="formData.level" placeholder="请选择级别" style="width: 100%">
            <el-option label="高层" value="高层" />
            <el-option label="中层" value="中层" />
            <el-option label="一般" value="一般" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否在职" prop="isActive">
          <el-switch v-model="formData.isActive" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="是否失效" prop="isInvalid">
          <el-switch v-model="formData.isInvalid" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <p class="page-tip">提示：人员数据后续将通过接口从人事系统自动同步</p>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { employeeApi, organizationApi, unitApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const deptOptions = ref<any[]>([])
const unitOptions = ref<any[]>([])

const levelMap: Record<string, string> = {
  '高层': '高层',
  '中层': '中层',
  '一般': '一般'
}

const queryParams = reactive({
  current: 1,
  size: 10,
  keyword: '',
  deptId: undefined as number | undefined,
  isActive: undefined as number | undefined
})

const searchForm = reactive({
  keyword: '',
  deptId: undefined as number | undefined,
  isActive: undefined as number | undefined
})

function loadDepts() {
  return organizationApi.all().then((res: any) => {
    deptOptions.value = res.data || []
  })
}

function loadUnits() {
  return unitApi.list({ current: 1, size: 1000 }).then((res: any) => {
    unitOptions.value = res.data?.records || []
  })
}

function loadData() {
  loading.value = true
  employeeApi.list({ ...queryParams })
    .then((res: any) => {
      tableData.value = res.data?.records || []
      total.value = res.data?.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch() {
  queryParams.current = 1
  queryParams.keyword = searchForm.keyword
  queryParams.deptId = searchForm.deptId
  queryParams.isActive = searchForm.isActive
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.deptId = undefined
  searchForm.isActive = undefined
  queryParams.current = 1
  queryParams.keyword = ''
  queryParams.deptId = undefined
  queryParams.isActive = undefined
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
const formData = reactive({
  id: undefined as number | undefined,
  employeeNo: '',
  employeeName: '',
  deptId: undefined as number | undefined,
  deptName: '',
  unitId: undefined as number | undefined,
  position: '',
  level: '',
  isActive: 1,
  isInvalid: 0
})

const formRules = {
  employeeNo: [{ required: true, message: '请输入人员编号', trigger: 'blur' }],
  employeeName: [{ required: true, message: '请输入人员姓名', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }]
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.employeeNo = ''
  formData.employeeName = ''
  formData.deptId = undefined
  formData.deptName = ''
  formData.unitId = undefined
  formData.position = ''
  formData.level = ''
  formData.isActive = 1
  formData.isInvalid = 0
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.employeeNo = row.employeeNo
  formData.employeeName = row.employeeName
  formData.deptId = row.deptId
  formData.deptName = row.deptName
  formData.unitId = row.unitId
  formData.position = row.position
  formData.level = row.level
  formData.isActive = row.isActive
  formData.isInvalid = row.isInvalid
  dialogVisible.value = true
}

function handleDeptChange(deptId: number) {
  const dept = deptOptions.value.find((d: any) => d.id === deptId)
  formData.deptName = dept ? dept.orgName : ''
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const api = isEdit.value ? employeeApi.update : employeeApi.create
    api({ ...formData }).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该人员？', '提示', { type: 'warning' }).then(() => {
    employeeApi.delete(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  })
}

onMounted(() => {
  Promise.all([loadDepts(), loadUnits()]).then(() => loadData())
})
</script>

<style scoped lang="scss">
.search-form {
  margin-bottom: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
.total-text {
  color: var(--text-secondary);
  font-size: 13px;
}
.page-tip {
  margin-top: 16px;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>

