<template>
  <div class="page-card">
    <h2 class="page-title">职责定义 v1</h2>
    <p class="page-subtitle">管理系统角色职责定义及菜单分配</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
      <el-form-item label="职责名称">
        <el-input v-model="searchForm.roleName" placeholder="请输入" clearable />
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
      <el-table-column prop="roleName" label="职责名称" min-width="160" />
      <el-table-column prop="roleCode" label="职责编码" width="140" />
      <el-table-column prop="roleType" label="职责类型" width="100" align="center">
        <template #default="{ row }">
          {{ roleTypeLabel(row.roleType) }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-if="row.roleType !== 'SYSTEM'">删除</el-button>
          <el-button link type="primary" @click="handleAssignMenu(row)">分配菜单</el-button>
          <el-button link type="primary" @click="handleManageChildren(row)" v-if="row.roleType === 'UNIT'">维护单位职责</el-button>
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
      :title="isEdit ? '编辑职责' : '新增职责'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="职责名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入职责名称" />
        </el-form-item>
        <el-form-item label="职责编码" prop="roleCode">
          <el-input v-model="formData.roleCode" placeholder="请输入职责编码" />
        </el-form-item>
        <el-form-item label="职责类型">
          <el-select v-model="formData.roleType" placeholder="请选择职责类型" :disabled="formData.roleType === 'SYSTEM'">
            <el-option label="单位" value="UNIT" />
            <el-option label="部门" value="DEPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="4" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配菜单弹框 -->
    <el-dialog
      v-model="menuDialogVisible"
      title="分配菜单"
      width="500px"
      destroy-on-close
    >
      <div style="margin-bottom: 10px;">
        <el-button size="small" @click="handleCheckAll">全部选择</el-button>
        <el-button size="small" @click="handleUncheckAll">全部取消</el-button>
      </div>
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
        @check="handleMenuCheck"
      >
        <template #default="{ node, data }">
          <span class="custom-tree-node">
            <span>{{ node.label }}</span>
            <el-input-number
              v-if="isMenuChecked(data.id)"
              v-model="menuSortCodes[data.id]"
              :min="0"
              :max="9999"
              size="small"
              controls-position="right"
              style="width: 90px; margin-left: 12px;"
              placeholder="排序"
              @click.stop
            />
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveMenus">确定</el-button>
      </template>
    </el-dialog>

    <!-- 维护单位职责弹框 -->
    <el-dialog v-model="childDialogVisible" title="维护单位职责" width="500px" destroy-on-close>
      <div style="display: flex; gap: 10px; margin-bottom: 20px;">
        <el-select v-model="selectedChildRoleId" placeholder="选择部门职责" style="flex: 1;">
          <el-option v-for="role in deptRoleList" :key="role.id" :label="role.roleName" :value="role.id" />
        </el-select>
        <el-button type="primary" @click="handleAddChild">添加</el-button>
      </div>
      <el-table :data="childRoleList" border>
        <el-table-column prop="roleName" label="职责名称" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="danger" size="small" link @click="handleRemoveChild(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { roleApi, menuApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const menuTree = ref<any[]>([])

const queryParams = reactive({
  current: 1,
  size: 10,
  roleName: ''
})

const searchForm = reactive({
  roleName: ''
})

function loadData() {
  loading.value = true
  roleApi.list({ ...queryParams })
    .then((res: any) => {
      tableData.value = res.data?.records || []
      total.value = res.data?.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function loadMenuTree() {
  menuApi.tree().then((res: any) => {
    menuTree.value = res.data || []
  })
}

function handleSearch() {
  queryParams.current = 1
  queryParams.roleName = searchForm.roleName
  loadData()
}

function handleReset() {
  searchForm.roleName = ''
  queryParams.current = 1
  queryParams.roleName = ''
  loadData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  loadData()
}

// 编辑弹框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<any>(null)
const formData = reactive({
  id: undefined as number | undefined,
  roleName: '',
  roleCode: '',
  roleType: 'DEPT',
  description: ''
})

const formRules = {
  roleName: [{ required: true, message: '请输入职责名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入职责编码', trigger: 'blur' }]
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.roleName = ''
  formData.roleCode = ''
  formData.roleType = 'DEPT'
  formData.description = ''
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.roleName = row.roleName
  formData.roleCode = row.roleCode
  formData.roleType = row.roleType || 'DEPT'
  formData.description = row.description
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const api = isEdit.value ? roleApi.update : roleApi.create
    api({ ...formData }).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该职责？', '提示', { type: 'warning' }).then(() => {
    roleApi.delete(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  })
}

// 分配菜单
const menuDialogVisible = ref(false)
const menuTreeRef = ref<any>(null)
const currentRoleId = ref<number>(0)
const menuSortCodes = ref<Record<number, number>>({})

// 对菜单树按排序编码重新排序的函数
function sortMenuTree(tree: any[], sortCodeMap: Record<number, number>): any[] {
  return [...tree].sort((a, b) => {
    const sortA = sortCodeMap[a.id] || 0
    const sortB = sortCodeMap[b.id] || 0
    // 都为0，按菜单名排
    if (sortA === 0 && sortB === 0) return a.menuName.localeCompare(b.menuName)
    // sortA为0排后面
    if (sortA === 0) return 1
    // sortB为0排后面
    if (sortB === 0) return -1
    // 都不为0，按sortCode升序
    const cmp = sortA - sortB
    return cmp !== 0 ? cmp : a.menuName.localeCompare(b.menuName)
  }).map(item => ({
    ...item,
    children: item.children ? sortMenuTree(item.children, sortCodeMap) : []
  }))
}

function handleAssignMenu(row: any) {
  currentRoleId.value = row.id
  menuDialogVisible.value = true
  menuSortCodes.value = {}

  roleApi.getMenus(row.id).then((res: any) => {
    const menuItems = res.data || res || []

    // 构建 sortCode 映射
    const sortMap: Record<number, number> = {}
    if (Array.isArray(menuItems)) {
      menuItems.forEach((item: any) => {
        if (item.menuId) {
          menuSortCodes.value[item.menuId] = item.sortCode || 0
          sortMap[item.menuId] = item.sortCode || 0
        }
      })
    }

    // 对菜单树按排序编码重排序
    menuTree.value = sortMenuTree(menuTree.value, sortMap)

    // 获取菜单ID用于选中
    const menuIds = Array.isArray(menuItems)
      ? menuItems.map((item: any) => item.menuId || item)
      : []

    nextTick(() => {
      const leafKeys = filterLeafKeys(menuTree.value, menuIds)
      menuTreeRef.value?.setCheckedKeys(leafKeys)
    })
  })
}

// 递归获取所有节点ID
function getAllNodeKeys(nodes: any[]): number[] {
  let keys: number[] = []
  for (const node of nodes) {
    keys.push(node.id)
    if (node.children && node.children.length > 0) {
      keys = keys.concat(getAllNodeKeys(node.children))
    }
  }
  return keys
}

// 过滤出叶子节点的key（非父节点）
function filterLeafKeys(nodes: any[], keys: number[]): number[] {
  let leafKeys: number[] = []
  for (const node of nodes) {
    if (node.children && node.children.length > 0) {
      leafKeys = leafKeys.concat(filterLeafKeys(node.children, keys))
    } else {
      if (keys.includes(node.id)) {
        leafKeys.push(node.id)
      }
    }
  }
  return leafKeys
}

function handleCheckAll() {
  const allKeys = getAllNodeKeys(menuTree.value)
  menuTreeRef.value?.setCheckedKeys(allKeys)
  handleMenuCheck()
}

function handleUncheckAll() {
  menuTreeRef.value?.setCheckedKeys([])
  handleMenuCheck()
}

function handleSaveMenus() {
  const checkedKeys = menuTreeRef.value?.getCheckedKeys() || []
  const halfCheckedKeys = menuTreeRef.value?.getHalfCheckedKeys() || []
  const allMenuIds = [...checkedKeys, ...halfCheckedKeys]

  if (allMenuIds.length === 0) {
    ElMessage.warning('请先选择菜单')
    return
  }

  const roleMenuItems = allMenuIds.map((menuId: any) => ({
    menuId: Number(menuId),
    sortCode: menuSortCodes.value[menuId] || 0
  }))

  roleApi.assignMenus(currentRoleId.value, roleMenuItems).then(() => {
    ElMessage.success('菜单分配成功')
    menuDialogVisible.value = false
  })
}

function isMenuChecked(menuId: number): boolean {
  const checkedKeys = menuTreeRef.value?.getCheckedKeys() || []
  const halfCheckedKeys = menuTreeRef.value?.getHalfCheckedKeys() || []
  return checkedKeys.includes(menuId) || halfCheckedKeys.includes(menuId)
}

function handleMenuCheck() {
  const checkedKeys = menuTreeRef.value?.getCheckedKeys() || []
  const halfCheckedKeys = menuTreeRef.value?.getHalfCheckedKeys() || []
  const allKeys = [...checkedKeys, ...halfCheckedKeys]

  allKeys.forEach((id: any) => {
    if (menuSortCodes.value[id] === undefined) {
      menuSortCodes.value[id] = 0
    }
  })
}

onMounted(() => {
  loadData()
  loadMenuTree()
})

// 职责类型格式化
function roleTypeLabel(type: string) {
  const map: Record<string, string> = { SYSTEM: '系统', UNIT: '单位', DEPT: '部门' }
  return map[type] || type || ''
}

// 维护单位职责
const childDialogVisible = ref(false)
const currentParentRoleId = ref<number | null>(null)
const selectedChildRoleId = ref<number | null>(null)
const childRoleList = ref<any[]>([])
const deptRoleList = ref<any[]>([])

async function handleManageChildren(row: any) {
  currentParentRoleId.value = row.id
  childDialogVisible.value = true
  const res = await roleApi.listByType('DEPT')
  deptRoleList.value = (res as any).data || res || []
  await loadChildren()
}

async function loadChildren() {
  const res = await roleApi.getRoleChildren(currentParentRoleId.value!)
  childRoleList.value = (res as any).data || res || []
}

async function handleAddChild() {
  if (!selectedChildRoleId.value) {
    ElMessage.warning('请选择部门职责')
    return
  }
  await roleApi.addRoleChild(currentParentRoleId.value!, selectedChildRoleId.value)
  ElMessage.success('添加成功')
  selectedChildRoleId.value = null
  await loadChildren()
}

async function handleRemoveChild(row: any) {
  await roleApi.removeRoleChild(currentParentRoleId.value!, row.id)
  ElMessage.success('删除成功')
  await loadChildren()
}
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
.custom-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  padding-right: 8px;
}
</style>
