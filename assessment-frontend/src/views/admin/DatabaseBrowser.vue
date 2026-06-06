<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2 class="page-title">数据库查询</h2>
        <p class="page-subtitle">浏览、编辑和删除考核系统业务表数据。</p>
      </div>
    </div>

    <div class="toolbar">
      <el-select
        v-model="queryForm.tableName"
        filterable
        placeholder="请选择数据表"
        style="width: 360px"
      >
        <el-option
          v-for="item in tableOptions"
          :key="item.tableName"
          :label="formatTableLabel(item)"
          :value="item.tableName"
        />
      </el-select>
      <el-button type="primary" :disabled="!queryForm.tableName" @click="handleQuery">查询</el-button>
    </div>

    <div v-if="selectedTableLabel" class="table-meta">
      <span>当前表：{{ selectedTableLabel }}</span>
      <span>总记录：{{ total }}</span>
    </div>

    <el-table v-loading="loading" :data="tableData" stripe style="width: 100%">
      <el-table-column
        v-for="column in columns"
        :key="column.columnName"
        :prop="column.columnName"
        :label="getColumnLabel(column)"
        min-width="150"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ formatCell(row[column.columnName]) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <span class="total-text">共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="queryForm.current"
        v-model:page-size="queryForm.size"
        :page-sizes="[10]"
        :total="total"
        layout="prev, pager, next, jumper"
        @current-change="loadRows"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="编辑数据" width="760px" destroy-on-close>
      <el-form label-width="160px" class="edit-form">
        <el-form-item
          v-for="column in editableColumns"
          :key="column.columnName"
          :label="getColumnLabel(column)"
        >
          <el-input
            v-if="isLongText(column)"
            v-model="editValues[column.columnName]"
            type="textarea"
            :rows="3"
          />
          <el-input-number
            v-else-if="isNumberColumn(column)"
            v-model="editValues[column.columnName]"
            :controls="false"
            style="width: 100%"
          />
          <el-switch
            v-else-if="isBooleanColumn(column)"
            v-model="editValues[column.columnName]"
            :active-value="1"
            :inactive-value="0"
          />
          <el-input v-else v-model="editValues[column.columnName]" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dbAdminApi } from '@/api/admin'

interface TableOption {
  tableName: string
  tableComment?: string
}

interface ColumnMeta {
  columnName: string
  columnType: string
  columnComment?: string
  primaryKey: boolean
  editable: boolean
}

const loading = ref(false)
const tableOptions = ref<TableOption[]>([])
const columns = ref<ColumnMeta[]>([])
const tableData = ref<Record<string, any>[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const currentRowId = ref<number | null>(null)
const editValues = reactive<Record<string, any>>({})

const queryForm = reactive({
  tableName: '',
  current: 1,
  size: 10
})

const selectedTableLabel = computed(() => {
  const found = tableOptions.value.find(item => item.tableName === queryForm.tableName)
  return found ? formatTableLabel(found) : ''
})

const editableColumns = computed(() => columns.value.filter(item => item.editable))

function formatTableLabel(item: TableOption) {
  return item.tableComment ? `${item.tableName} - ${item.tableComment}` : item.tableName
}

function getColumnLabel(column: ColumnMeta) {
  return column.columnComment || column.columnName
}

function formatCell(value: any) {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function isNumberColumn(column: ColumnMeta) {
  return ['int', 'bigint', 'decimal', 'double', 'float', 'tinyint'].includes(column.columnType)
}

function isBooleanColumn(column: ColumnMeta) {
  return column.columnType === 'tinyint' && /is_|deleted|enabled|active|invalid|read/.test(column.columnName)
}

function isLongText(column: ColumnMeta) {
  return ['text', 'longtext'].includes(column.columnType)
}

async function loadTableOptions() {
  const res: any = await dbAdminApi.tables()
  tableOptions.value = res.data || []
}

async function loadRows() {
  if (!queryForm.tableName) {
    columns.value = []
    tableData.value = []
    total.value = 0
    return
  }

  loading.value = true
  try {
    const res: any = await dbAdminApi.rows({ ...queryForm })
    const data = res.data || {}
    columns.value = data.columns || []
    tableData.value = data.page?.records || []
    total.value = data.page?.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryForm.current = 1
  loadRows()
}

function handleEdit(row: Record<string, any>) {
  currentRowId.value = Number(row.id)
  editableColumns.value.forEach(column => {
    editValues[column.columnName] = row[column.columnName]
  })
  dialogVisible.value = true
}

async function submitEdit() {
  if (!queryForm.tableName || !currentRowId.value) {
    return
  }

  const values: Record<string, any> = {}
  editableColumns.value.forEach(column => {
    values[column.columnName] = editValues[column.columnName]
  })

  await dbAdminApi.updateRow({
    tableName: queryForm.tableName,
    id: currentRowId.value,
    values
  })
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadRows()
}

function handleDelete(row: Record<string, any>) {
  ElMessageBox.confirm(`确认删除表 ${queryForm.tableName} 中 ID=${row.id} 的记录？`, '提示', {
    type: 'warning'
  }).then(async () => {
    await dbAdminApi.deleteRow(queryForm.tableName, Number(row.id))
    ElMessage.success('删除成功')
    await loadRows()
  })
}

onMounted(async () => {
  await loadTableOptions()
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.table-meta {
  display: flex;
  gap: 20px;
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 12px;
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

.edit-form {
  max-height: 60vh;
  overflow: auto;
  padding-right: 8px;
}
</style>
