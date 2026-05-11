<template>
  <div class="page-card">
    <h2 class="page-title">指标大类管理 v1</h2>
    <p class="page-subtitle">管理业绩考核指标的大类定义</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
      <el-form-item label="大类名称">
        <el-input v-model="searchForm.categoryName" placeholder="请输入" clearable />
      </el-form-item>
      <el-form-item label="适用范围">
        <el-select v-model="searchForm.applicableScope" placeholder="全部" clearable style="width: 140px">
          <el-option label="职能部门" value="FUNCTIONAL" />
          <el-option label="分公司" value="BRANCH" />
          <el-option label="通用" value="COMMON" />
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
      <el-table-column prop="categoryName" label="大类名称" min-width="140" />
      <el-table-column prop="categoryCode" label="大类编码" width="120" />
      <el-table-column prop="sortCode" label="排序" width="70" align="center" />
      <el-table-column prop="applicableScope" label="适用范围" width="100">
        <template #default="{ row }">
          {{ scopeMap[row.applicableScope] || row.applicableScope || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="weight" label="权重(%)" width="90" align="center">
        <template #default="{ row }">
          {{ row.weight != null ? row.weight + '%' : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="evaluationStandard" label="评价标准" min-width="200" show-overflow-tooltip />
      <el-table-column prop="isEnabled" label="是否启用" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isEnabled === 1" type="success" size="small">启用</el-tag>
          <el-tag v-else type="info" size="small">禁用</el-tag>
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
      :title="isEdit ? '编辑指标大类' : '新增指标大类'"
      width="550px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="大类名称" prop="categoryName">
          <el-input v-model="formData.categoryName" placeholder="请输入大类名称" />
        </el-form-item>
        <el-form-item label="大类编码" prop="categoryCode">
          <el-input v-model="formData.categoryCode" placeholder="请输入大类编码" />
        </el-form-item>
        <el-form-item label="排序" prop="sortCode">
          <el-input-number v-model="formData.sortCode" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="适用范围" prop="applicableScope">
          <el-select v-model="formData.applicableScope" placeholder="请选择适用范围" style="width: 100%">
            <el-option label="职能部门" value="FUNCTIONAL" />
            <el-option label="分公司" value="BRANCH" />
            <el-option label="通用" value="COMMON" />
          </el-select>
        </el-form-item>
        <el-form-item label="权重(%)" prop="weight">
          <el-input-number v-model="formData.weight" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="评价标准" prop="evaluationStandard">
          <el-input v-model="formData.evaluationStandard" type="textarea" :rows="4" placeholder="请输入评价标准" />
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
import { indicatorCategoryApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const scopeMap: Record<string, string> = {
  FUNCTIONAL: '职能部门',
  BRANCH: '分公司',
  COMMON: '通用'
}

const queryParams = reactive({
  current: 1,
  size: 10,
  categoryName: '',
  applicableScope: ''
})

const searchForm = reactive({
  categoryName: '',
  applicableScope: ''
})

function loadData() {
  loading.value = true
  indicatorCategoryApi.list({ ...queryParams })
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
  queryParams.categoryName = searchForm.categoryName
  queryParams.applicableScope = searchForm.applicableScope
  loadData()
}

function handleReset() {
  searchForm.categoryName = ''
  searchForm.applicableScope = ''
  queryParams.current = 1
  queryParams.categoryName = ''
  queryParams.applicableScope = ''
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
  categoryName: '',
  categoryCode: '',
  sortCode: 0,
  applicableScope: '',
  weight: undefined as number | undefined,
  evaluationStandard: '',
  isEnabled: 1
})

const formRules = {
  categoryName: [{ required: true, message: '请输入大类名称', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请输入大类编码', trigger: 'blur' }],
  applicableScope: [{ required: true, message: '请选择适用范围', trigger: 'change' }]
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.categoryName = ''
  formData.categoryCode = ''
  formData.sortCode = 0
  formData.applicableScope = ''
  formData.weight = undefined
  formData.evaluationStandard = ''
  formData.isEnabled = 1
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.categoryName = row.categoryName
  formData.categoryCode = row.categoryCode
  formData.sortCode = row.sortCode
  formData.applicableScope = row.applicableScope
  formData.weight = row.weight
  formData.evaluationStandard = row.evaluationStandard
  formData.isEnabled = row.isEnabled
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const api = isEdit.value ? indicatorCategoryApi.update : indicatorCategoryApi.create
    api({ ...formData }).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该指标大类？', '提示', { type: 'warning' }).then(() => {
    indicatorCategoryApi.delete(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
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
