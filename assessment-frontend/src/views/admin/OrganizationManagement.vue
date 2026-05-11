<template>
  <div class="page-card">
    <h2 class="page-title">考核组织管理 v1</h2>
    <p class="page-subtitle">管理参与考核的组织部门信息</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
      <el-form-item label="组织名称">
        <el-input v-model="searchForm.orgName" placeholder="请输入" clearable />
      </el-form-item>
      <el-form-item label="组织类别">
        <el-select v-model="searchForm.orgType" placeholder="全部" clearable style="width: 140px">
          <el-option label="职能部门" value="FUNCTIONAL" />
          <el-option label="分公司" value="BRANCH" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属单位">
        <el-select v-model="searchForm.unitId" placeholder="全部" clearable style="width: 180px">
          <el-option
            v-for="item in unitOptions"
            :key="item.id"
            :label="item.unitName"
            :value="item.id"
          />
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
          <el-icon><Plus /></el-icon>新增
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table :data="tableData" stripe v-loading="loading" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="orgName" label="组织名称" min-width="160" />
      <el-table-column prop="orgCode" label="组织编码" width="120" />
      <el-table-column prop="unitName" label="所属单位" width="160" />
      <el-table-column prop="orgType" label="组织类别" width="100">
        <template #default="{ row }">
          {{ row.orgType === 'FUNCTIONAL' ? '职能部门' : '分公司' }}
        </template>
      </el-table-column>
      <el-table-column prop="sortCode" label="排序" width="70" align="center" />
      <el-table-column prop="deptAdminName" label="部门绩效管理员" width="130" />
      <el-table-column prop="deptLeaderName" label="部门负责人" width="120" />
      <el-table-column prop="supervisorName" label="分管领导" width="120" />
      <el-table-column prop="assessorName" label="考核员" width="120" />
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
      :title="isEdit ? '编辑考核组织' : '新增考核组织'"
      width="550px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="组织名称" prop="orgName">
          <el-input v-model="formData.orgName" placeholder="请输入组织名称" />
        </el-form-item>
        <el-form-item label="组织编码" prop="orgCode">
          <el-input v-model="formData.orgCode" placeholder="请输入组织编码" />
        </el-form-item>
        <el-form-item label="所属单位" prop="unitId">
          <el-select v-model="formData.unitId" placeholder="请选择所属单位" style="width: 100%" @change="handleUnitChange">
            <el-option
              v-for="item in unitOptions"
              :key="item.id"
              :label="item.unitName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组织类别" prop="orgType">
          <el-select v-model="formData.orgType" placeholder="请选择组织类别" style="width: 100%">
            <el-option label="职能部门" value="FUNCTIONAL" />
            <el-option label="分公司" value="BRANCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortCode">
          <el-input-number v-model="formData.sortCode" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="部门绩效管理员" prop="deptAdminId">
          <el-select
            v-model="formData.deptAdminId"
            placeholder="请选择部门绩效管理员"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in orgEmployeeOptions"
              :key="item.id"
              :label="`${item.employeeName}(${item.employeeCode}) - ${item.deptName || ''}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门负责人" prop="deptLeaderId">
          <el-select
            v-model="formData.deptLeaderId"
            placeholder="请选择部门负责人"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in orgEmployeeOptions"
              :key="item.id"
              :label="`${item.employeeName}(${item.employeeCode}) - ${item.deptName || ''}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分管领导" prop="supervisorId">
          <el-select
            v-model="formData.supervisorId"
            placeholder="请选择分管领导"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in leaderOptions"
              :key="item.id"
              :label="`${item.leaderName}（${item.leaderLevel || ''}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考核员" prop="assessorId">
          <el-select
            v-model="formData.assessorId"
            placeholder="请选择考核员"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in orgEmployeeOptions"
              :key="item.id"
              :label="`${item.employeeName}(${item.employeeCode}) - ${item.deptName || ''}`"
              :value="item.id"
            />
          </el-select>
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
import { organizationApi, unitApi, leaderApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const unitOptions = ref<any[]>([])

const queryParams = reactive({
  current: 1,
  size: 10,
  orgName: '',
  orgType: '',
  unitId: undefined as number | undefined
})

const searchForm = reactive({
  orgName: '',
  orgType: '',
  unitId: undefined as number | undefined
})

function loadUnits() {
  return unitApi.list({ current: 1, size: 1000 }).then((res: any) => {
    unitOptions.value = res.data?.records || []
  })
}

function loadData() {
  loading.value = true
  organizationApi.list({ ...queryParams })
    .then((res: any) => {
      const records = res.data?.records || []
      tableData.value = records.map((item: any) => ({
        ...item,
        unitName: unitOptions.value.find((u: any) => u.id === item.unitId)?.unitName || '-'
      }))
      total.value = res.data?.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch() {
  queryParams.current = 1
  queryParams.orgName = searchForm.orgName
  queryParams.orgType = searchForm.orgType
  queryParams.unitId = searchForm.unitId
  loadData()
}

function handleReset() {
  searchForm.orgName = ''
  searchForm.orgType = ''
  searchForm.unitId = undefined
  queryParams.current = 1
  queryParams.orgName = ''
  queryParams.orgType = ''
  queryParams.unitId = undefined
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
const orgEmployeeOptions = ref<any[]>([])
const leaderOptions = ref<any[]>([])

const formData = reactive({
  id: undefined as number | undefined,
  orgName: '',
  orgCode: '',
  unitId: undefined as number | undefined,
  orgType: '',
  sortCode: 0,
  deptAdminId: undefined as number | undefined,
  deptLeaderId: undefined as number | undefined,
  supervisorId: undefined as number | undefined,
  assessorId: undefined as number | undefined,
  deptAdminName: '',
  deptLeaderName: '',
  supervisorName: '',
  assessorName: ''
})

const formRules = {
  orgName: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  orgCode: [{ required: true, message: '请输入组织编码', trigger: 'blur' }],
  unitId: [{ required: true, message: '请选择所属单位', trigger: 'change' }],
  orgType: [{ required: true, message: '请选择组织类别', trigger: 'change' }]
}

function loadOrgEmployees(unitId?: number) {
  organizationApi.getEmployees({ unitId }).then((res: any) => {
    orgEmployeeOptions.value = res.data || []
  })
}

function loadLeaders(unitId?: number) {
  leaderApi.getAll(unitId).then((res: any) => {
    leaderOptions.value = res.data || []
  })
}

function handleUnitChange(unitId: number) {
  formData.deptAdminId = undefined
  formData.deptLeaderId = undefined
  formData.supervisorId = undefined
  formData.assessorId = undefined
  loadOrgEmployees(unitId)
  loadLeaders(unitId)
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.orgName = ''
  formData.orgCode = ''
  formData.unitId = undefined
  formData.orgType = ''
  formData.sortCode = 0
  formData.deptAdminId = undefined
  formData.deptLeaderId = undefined
  formData.supervisorId = undefined
  formData.assessorId = undefined
  formData.deptAdminName = ''
  formData.deptLeaderName = ''
  formData.supervisorName = ''
  formData.assessorName = ''
  orgEmployeeOptions.value = []
  leaderOptions.value = []
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.orgName = row.orgName
  formData.orgCode = row.orgCode
  formData.unitId = row.unitId
  formData.orgType = row.orgType
  formData.sortCode = row.sortCode
  formData.deptAdminId = row.deptAdminId
  formData.deptLeaderId = row.deptLeaderId
  formData.supervisorId = row.supervisorId
  formData.assessorId = row.assessorId
  formData.deptAdminName = row.deptAdminName
  formData.deptLeaderName = row.deptLeaderName
  formData.supervisorName = row.supervisorName
  formData.assessorName = row.assessorName
  // 加载人员列表以支持回显
  if (row.unitId) {
    loadOrgEmployees(row.unitId)
    loadLeaders(row.unitId)
  }
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const api = isEdit.value ? organizationApi.update : organizationApi.create
    api({ ...formData }).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该考核组织？', '提示', { type: 'warning' }).then(() => {
    organizationApi.delete(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  })
}

onMounted(() => {
  loadUnits().then(() => loadData())
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
