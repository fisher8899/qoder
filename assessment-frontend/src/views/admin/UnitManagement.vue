<template>
  <div class="page-card">
    <h2 class="page-title">单位管理 v1</h2>
    <p class="page-subtitle">管理参与考核的单位信息，按单位进行权限控制</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
      <el-form-item label="单位名称">
        <el-input v-model="searchForm.unitName" placeholder="请输入" clearable />
      </el-form-item>
      <el-form-item label="单位类型">
        <el-select v-model="searchForm.unitType" placeholder="全部" clearable style="width: 120px">
          <el-option label="公司" value="COMPANY" />
          <el-option label="分公司" value="BRANCH" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否启用">
        <el-select v-model="searchForm.isEnabled" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
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
          <el-icon><Plus /></el-icon>新增单位
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table :data="tableData" stripe v-loading="loading" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="unitName" label="单位名称" min-width="160" />
      <el-table-column prop="unitType" label="单位类型" width="100">
        <template #default="{ row }">
          {{ row.unitType === 'COMPANY' ? '公司' : '分公司' }}
        </template>
      </el-table-column>
      <el-table-column prop="unitCode" label="单位编码" width="120" />
      <el-table-column prop="createdTime" label="创建日期" width="160" />
      <el-table-column prop="createdBy" label="创建人" width="120" />
      <el-table-column prop="isEnabled" label="是否启用" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isEnabled === 1" type="success" size="small">启用</el-tag>
          <el-tag v-else type="info" size="small">禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="expireDate" label="失效日期" width="120">
        <template #default="{ row }">
          {{ row.expireDate || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button
            v-if="row.isEnabled === 1"
            link
            type="danger"
            @click="handleToggle(row)"
          >
            失效
          </el-button>
          <el-button
            v-else
            link
            type="success"
            @click="handleToggle(row)"
          >
            启用
          </el-button>
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
      :title="isEdit ? '编辑单位' : '新增单位'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="单位名称" prop="unitName">
          <el-input v-model="formData.unitName" placeholder="请输入单位名称" />
        </el-form-item>
        <el-form-item label="单位编码" prop="unitCode">
          <el-input v-model="formData.unitCode" placeholder="请输入单位编码" />
        </el-form-item>
        <el-form-item label="单位类型" prop="unitType">
          <el-select v-model="formData.unitType" placeholder="请选择单位类型" style="width: 100%">
            <el-option label="公司" value="COMPANY" />
            <el-option label="分公司" value="BRANCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否启用" prop="isEnabled">
          <el-switch v-model="formData.isEnabled" :active-value="1" :inactive-value="0" />
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
import { unitApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  unitName: '',
  unitType: '',
  isEnabled: undefined as number | undefined
})

const searchForm = reactive({
  unitName: '',
  unitType: '',
  isEnabled: undefined as number | undefined
})

function loadData() {
  loading.value = true
  unitApi.list({ ...queryParams })
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
  queryParams.unitName = searchForm.unitName
  queryParams.unitType = searchForm.unitType
  queryParams.isEnabled = searchForm.isEnabled
  loadData()
}

function handleReset() {
  searchForm.unitName = ''
  searchForm.unitType = ''
  searchForm.isEnabled = undefined
  queryParams.current = 1
  queryParams.unitName = ''
  queryParams.unitType = ''
  queryParams.isEnabled = undefined
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
  unitName: '',
  unitCode: '',
  unitType: '',
  isEnabled: 1
})

const formRules = {
  unitName: [{ required: true, message: '请输入单位名称', trigger: 'blur' }],
  unitCode: [{ required: true, message: '请输入单位编码', trigger: 'blur' }],
  unitType: [{ required: true, message: '请选择单位类型', trigger: 'change' }]
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.unitName = ''
  formData.unitCode = ''
  formData.unitType = ''
  formData.isEnabled = 1
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.unitName = row.unitName
  formData.unitCode = row.unitCode
  formData.unitType = row.unitType
  formData.isEnabled = row.isEnabled
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const api = isEdit.value ? unitApi.update : unitApi.create
    api({ ...formData }).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleToggle(row: any) {
  const action = row.isEnabled === 1 ? '失效' : '启用'
  ElMessageBox.confirm(`确认${action}该单位？`, '提示', { type: 'warning' }).then(() => {
    unitApi.toggle(row.id).then(() => {
      ElMessage.success(`${action}成功`)
      loadData()
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确定要删除该单位吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    unitApi.delete(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  }).catch(() => {
    // 用户取消，不做处理
  })
}

onMounted(() => {
  loadData()
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
