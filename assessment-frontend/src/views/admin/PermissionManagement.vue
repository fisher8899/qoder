<template>
  <div class="page-card">
    <h2 class="page-title">权限分配管理 v1</h2>
    <p class="page-subtitle">管理系统用户的权限分配</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
      <el-form-item label="用户姓名">
        <el-input v-model="searchForm.userName" placeholder="请输入" clearable />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="searchForm.roleCode" placeholder="全部" clearable style="width: 160px">
          <el-option label="系统管理员" value="ADMIN" />
          <el-option label="计划财务处业绩考核管理员" value="FIN_ADMIN" />
          <el-option label="部门绩效管理员" value="DEPT_ADMIN" />
          <el-option label="部门负责人" value="DEPT_LEADER" />
          <el-option label="分管领导" value="SUPERVISOR" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
      <el-form-item v-if="false" style="margin-left: auto">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table :data="tableData" stripe v-loading="loading" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="userName" label="用户姓名" width="120" />
      <el-table-column prop="username" label="系统登录用户名" width="150" />
      <el-table-column prop="unitScope" label="单位权限" min-width="160" />
      <el-table-column prop="examType" label="考核类型" width="120">
        <template #default="{ row }">
          {{ examTypeMap[row.examType] || row.examType || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="roleCode" label="分配角色" width="140">
        <template #default="{ row }">
          {{ roleMap[row.roleCode] || row.roleCode || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="dataScope" label="数据范围" min-width="160">
        <template #default="{ row }">
          {{ formatDataScope(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="startDate" label="生效日期" width="120" />
      <el-table-column prop="endDate" label="失效日期" width="120">
        <template #default="{ row }">
          {{ row.endDate || '永久' }}
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
      :title="isEdit ? '编辑权限分配' : '新增权限分配'"
      width="550px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="选择用户" prop="userId">
          <el-select v-model="formData.userId" placeholder="请选择用户" style="width: 100%" @change="handleUserChange">
            <el-option
              v-for="item in userOptions"
              :key="item.id"
              :label="`${item.realName}(${item.username})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="单位权限" prop="unitScope">
          <el-select v-model="formData.unitScopeList" multiple placeholder="请选择单位权限" style="width: 100%">
            <el-option
              v-for="item in unitOptions"
              :key="item.id"
              :label="item.unitName"
              :value="item.unitName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考核类型" prop="examType">
          <el-select v-model="formData.examType" placeholder="请选择考核类型" style="width: 100%">
            <el-option label="月度考核" value="MONTHLY" />
            <el-option label="年度考核" value="ANNUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="分配角色" prop="roleCode">
          <el-select v-model="formData.roleCode" placeholder="请选择角色" style="width: 100%">
            <el-option label="系统管理员" value="ADMIN" />
            <el-option label="计划财务处业绩考核管理员" value="FIN_ADMIN" />
            <el-option label="部门绩效管理员" value="DEPT_ADMIN" />
            <el-option label="部门负责人" value="DEPT_LEADER" />
            <el-option label="分管领导" value="SUPERVISOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="dataScopeType" placeholder="请选择数据范围" style="width: 100%" @change="handleDataScopeTypeChange">
            <el-option label="全部" value="ALL" />
            <el-option label="指定组织" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dataScopeType === 'CUSTOM'" label="组织名称" prop="dataScopeCustom">
          <el-select v-model="formData.dataScopeList" multiple filterable allow-create placeholder="请输入或选择组织名称" style="width: 100%">
            <el-option
              v-for="item in orgOptions"
              :key="item.id"
              :label="item.orgName"
              :value="item.orgName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期" prop="startDate">
          <el-date-picker v-model="formData.startDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择生效日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日期" prop="endDate">
          <el-date-picker v-model="formData.endDate" type="date" value-format="YYYY-MM-DD" placeholder="留空表示永久有效" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { permissionApi, unitApi, userApi, organizationApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const unitOptions = ref<any[]>([])
const userOptions = ref<any[]>([])

const roleMap: Record<string, string> = {
  ADMIN: '系统管理员',
  FIN_ADMIN: '计划财务处业绩考核管理员',
  DEPT_ADMIN: '部门绩效管理员',
  DEPT_LEADER: '部门负责人',
  SUPERVISOR: '分管领导'
}

const examTypeMap: Record<string, string> = {
  MONTHLY: '月度考核',
  ANNUAL: '年度考核'
}

const orgOptions = ref<any[]>([])
const dataScopeType = ref('ALL')

function formatDataScope(row: any) {
  if (!row.dataScope) return '—'
  if (row.dataScope === 'ALL') return '全部'
  if (row.scopeName) return row.scopeName
  return row.dataScope
}

const queryParams = reactive({
  current: 1,
  size: 10,
  userName: '',
  roleCode: ''
})

const searchForm = reactive({
  userName: '',
  roleCode: ''
})

function loadUnits() {
  return unitApi.list({ current: 1, size: 1000 }).then((res: any) => {
    unitOptions.value = res.data?.records || []
  })
}

function loadData() {
  loading.value = true
  permissionApi.list({ ...queryParams })
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
  queryParams.userName = searchForm.userName
  queryParams.roleCode = searchForm.roleCode
  loadData()
}

function handleReset() {
  searchForm.userName = ''
  searchForm.roleCode = ''
  queryParams.current = 1
  queryParams.userName = ''
  queryParams.roleCode = ''
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
  userId: undefined as number | undefined,
  userName: '',
  unitScopeList: [] as string[],
  examType: '',
  roleCode: '',
  dataScope: '',
  dataScopeList: [] as string[],
  startDate: '',
  endDate: ''
})

const formRules = {
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }],
  roleCode: [{ required: true, message: '请选择分配角色', trigger: 'change' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

function handleDataScopeTypeChange(val: string) {
  if (val === 'ALL') {
    formData.dataScope = 'ALL'
    formData.dataScopeList = []
  } else {
    formData.dataScope = ''
  }
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.userId = undefined
  formData.userName = ''
  formData.unitScopeList = []
  formData.examType = ''
  formData.roleCode = ''
  formData.dataScope = ''
  formData.dataScopeList = []
  formData.startDate = new Date().toISOString().slice(0, 10)
  formData.endDate = ''
  dataScopeType.value = 'ALL'
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.userId = row.userId
  formData.userName = row.userName
  formData.unitScopeList = row.unitScope ? row.unitScope.split(',') : []
  formData.examType = row.examType
  formData.roleCode = row.roleCode
  formData.dataScope = row.dataScope
  formData.startDate = row.startDate || ''
  formData.endDate = row.endDate || ''
  if (row.dataScope === 'ALL') {
    dataScopeType.value = 'ALL'
    formData.dataScopeList = []
  } else {
    dataScopeType.value = 'CUSTOM'
    formData.dataScopeList = row.dataScope ? row.dataScope.split(',') : []
  }
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const submitData = {
      ...formData,
      unitScope: formData.unitScopeList.join(','),
      dataScope: dataScopeType.value === 'ALL' ? 'ALL' : formData.dataScopeList.join(','),
      endDate: formData.endDate || null
    }
    const api = isEdit.value ? permissionApi.update : permissionApi.create
    api(submitData).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleUserChange(userId: number) {
  const user = userOptions.value.find((u: any) => u.id === userId)
  formData.userName = user ? user.realName : ''
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该权限分配？', '提示', { type: 'warning' }).then(() => {
    permissionApi.delete(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  })
}

function loadUsers() {
  return userApi.list().then((res: any) => {
    userOptions.value = res.data || []
  })
}

function loadOrgs() {
  return organizationApi.list({ current: 1, size: 1000 }).then((res: any) => {
    orgOptions.value = res.data?.records || []
  })
}

onMounted(() => {
  Promise.all([loadUnits(), loadUsers(), loadOrgs()]).then(() => loadData())
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
</style>
