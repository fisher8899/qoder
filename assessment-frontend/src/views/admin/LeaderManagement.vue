<template>
  <div class="page-card">
    <h2 class="page-title">分管领导维护 v1</h2>
    <p class="page-subtitle">维护各单位的分管领导信息</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
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
      <el-form-item label="领导姓名">
        <el-input v-model="searchForm.leaderName" placeholder="请输入" clearable />
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
      <el-table-column prop="unitName" label="所属单位" min-width="160" />
      <el-table-column prop="leaderName" label="领导姓名" width="120" />
      <el-table-column prop="leaderLevel" label="职级" width="120" />
      <el-table-column prop="effectiveDate" label="生效日期" width="120" />
      <el-table-column prop="expireDate" label="失效日期" width="120">
        <template #default="{ row }">
          {{ row.expireDate || '—' }}
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
      :title="isEdit ? '编辑分管领导' : '新增分管领导'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
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
        <el-form-item label="领导姓名" prop="employeeId">
          <el-select
            v-model="formData.employeeId"
            placeholder="请选择领导"
            filterable
            style="width: 100%"
            @change="handleEmployeeChange"
          >
            <el-option
              v-for="item in employeeOptions"
              :key="item.id"
              :label="`${item.employeeName}(${item.employeeCode}) - ${item.position || ''}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="职级" prop="leaderLevel">
          <el-input v-model="formData.leaderLevel" placeholder="自动填充或手动输入" />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker
            v-model="formData.effectiveDate"
            type="date"
            placeholder="选择生效日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="失效日期" prop="expireDate">
          <el-date-picker
            v-model="formData.expireDate"
            type="date"
            placeholder="选择失效日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
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
import { leaderApi, unitApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const unitOptions = ref<any[]>([])

const queryParams = reactive({
  current: 1,
  size: 10,
  unitId: undefined as number | undefined,
  leaderName: ''
})

const searchForm = reactive({
  unitId: undefined as number | undefined,
  leaderName: ''
})

function loadUnits() {
  return unitApi.list({ current: 1, size: 1000 }).then((res: any) => {
    unitOptions.value = res.data?.records || []
  })
}

function loadData() {
  loading.value = true
  leaderApi.list({ ...queryParams })
    .then((res: any) => {
      const records = res.data?.records || []
      // 关联单位名称
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
  queryParams.unitId = searchForm.unitId
  queryParams.leaderName = searchForm.leaderName
  loadData()
}

function handleReset() {
  searchForm.unitId = undefined
  searchForm.leaderName = ''
  queryParams.current = 1
  queryParams.unitId = undefined
  queryParams.leaderName = ''
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
const employeeOptions = ref<any[]>([])

const formData = reactive({
  id: undefined as number | undefined,
  unitId: undefined as number | undefined,
  employeeId: undefined as number | undefined,
  leaderName: '',
  leaderLevel: '',
  effectiveDate: '',
  expireDate: ''
})

const formRules = {
  unitId: [{ required: true, message: '请选择所属单位', trigger: 'change' }],
  employeeId: [{ required: true, message: '请选择领导', trigger: 'change' }],
  leaderLevel: [{ required: true, message: '请输入职级', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
}

function loadEmployees(unitId?: number) {
  leaderApi.getEmployees(unitId).then((res: any) => {
    employeeOptions.value = res.data || []
  })
}

function handleUnitChange(unitId: number) {
  formData.employeeId = undefined
  formData.leaderName = ''
  formData.leaderLevel = ''
  loadEmployees(unitId)
}

function handleEmployeeChange(employeeId: number) {
  const emp = employeeOptions.value.find((e: any) => e.id === employeeId)
  if (emp) {
    formData.leaderName = emp.employeeName
    if (emp.rank) {
      formData.leaderLevel = emp.rank
    }
  }
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.unitId = undefined
  formData.employeeId = undefined
  formData.leaderName = ''
  formData.leaderLevel = ''
  formData.effectiveDate = ''
  formData.expireDate = ''
  employeeOptions.value = []
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.unitId = row.unitId
  formData.employeeId = row.employeeId
  formData.leaderName = row.leaderName
  formData.leaderLevel = row.leaderLevel
  formData.effectiveDate = row.effectiveDate
  formData.expireDate = row.expireDate
  // 加载该单位下的人员列表以支持回显
  if (row.unitId) {
    loadEmployees(row.unitId)
  }
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const api = isEdit.value ? leaderApi.update : leaderApi.create
    api({ ...formData }).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该分管领导？', '提示', { type: 'warning' }).then(() => {
    leaderApi.delete(row.id).then(() => {
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
