<template>
  <div class="page-card">
    <h2 class="page-title">功能/菜单定义 v1</h2>
    <p class="page-subtitle">定义系统功能菜单结构</p>

    <div class="toolbar">
      <el-button type="primary" @click="handleAddRoot">
        <el-icon><Plus /></el-icon>新增根菜单
      </el-button>
    </div>

    <el-tree
      :data="menuTree"
      :props="{ label: 'menuName', children: 'children' }"
      node-key="id"
      default-expand-all
      highlight-current
      class="menu-tree"
    >
      <template #default="{ data }">
        <div class="tree-node-content">
          <span class="node-label">{{ data.menuName }}</span>
          <span class="node-meta">编码: {{ data.menuCode }}</span>
          <span class="node-meta">类别: {{ formatMenuCategory(data.menuCategory) }}</span>
          <span class="node-meta">路径: {{ data.menuPath || '—' }}</span>
          <span class="node-actions">
            <el-button link type="primary" size="small" @click.stop="handleAddChild(data)">
              新增子菜单
            </el-button>
            <el-button link type="primary" size="small" @click.stop="handleEdit(data)">
              编辑
            </el-button>
            <el-button link type="danger" size="small" @click.stop="handleDelete(data)">
              删除
            </el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="菜单编码" prop="menuCode">
          <el-input v-model="formData.menuCode" placeholder="请输入菜单编码" />
        </el-form-item>
        <el-form-item label="菜单类别" prop="menuCategoryValues">
          <el-checkbox-group v-model="formData.menuCategoryValues">
            <el-checkbox value="SYSTEM">系统类</el-checkbox>
            <el-checkbox value="UNIT">单位类</el-checkbox>
            <el-checkbox value="DEPT">部门类</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="menuTreeForSelect"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            placeholder="请选择上级菜单（空则为根菜单）"
            clearable
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="路由路径" prop="menuPath">
          <el-input v-model="formData.menuPath" placeholder="请输入路由路径" />
        </el-form-item>
        <el-form-item label="图标" prop="menuIcon">
          <el-input v-model="formData.menuIcon" placeholder="请输入图标名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sortCode">
          <el-input-number v-model="formData.sortCode" :min="0" style="width: 100%" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { menuApi } from '@/api/admin'

type MenuNode = {
  id: number
  menuName: string
  menuCode: string
  menuCategory?: string
  parentId?: number
  menuPath?: string
  menuIcon?: string
  sortCode?: number
  isEnabled?: number
  children?: MenuNode[]
}

const menuTree = ref<MenuNode[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const isAddRoot = ref(false)
const formRef = ref()

const formData = reactive({
  id: undefined as number | undefined,
  menuName: '',
  menuCode: '',
  menuCategoryValues: ['SYSTEM'] as string[],
  parentId: undefined as number | undefined,
  menuPath: '',
  menuIcon: '',
  sortCode: 0,
  isEnabled: 1
})

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuCode: [{ required: true, message: '请输入菜单编码', trigger: 'blur' }],
  menuCategoryValues: [{ required: true, type: 'array', min: 1, message: '请选择菜单类别', trigger: 'change' }]
}

const menuCategoryLabelMap: Record<string, string> = {
  SYSTEM: '系统类',
  UNIT: '单位类',
  DEPT: '部门类'
}

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑菜单'
  if (isAddRoot.value) return '新增根菜单'
  return '新增子菜单'
})

const menuTreeForSelect = computed(() => {
  if (!isEdit.value || !formData.id) {
    return menuTree.value
  }
  return filterTree(menuTree.value, formData.id)
})

function parseMenuCategory(category?: string): string[] {
  if (!category) {
    return ['SYSTEM']
  }
  return category
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

function formatMenuCategory(category?: string): string {
  return parseMenuCategory(category)
    .map(item => menuCategoryLabelMap[item] || item)
    .join('、')
}

function filterTree(tree: MenuNode[], excludeId: number): MenuNode[] {
  return tree
    .filter(node => node.id !== excludeId)
    .map(node => ({
      ...node,
      children: node.children ? filterTree(node.children, excludeId) : []
    }))
}

function resetForm() {
  formData.id = undefined
  formData.menuName = ''
  formData.menuCode = ''
  formData.menuCategoryValues = ['SYSTEM']
  formData.parentId = undefined
  formData.menuPath = ''
  formData.menuIcon = ''
  formData.sortCode = 0
  formData.isEnabled = 1
}

function loadData() {
  loading.value = true
  menuApi.tree()
    .then((res: any) => {
      menuTree.value = res.data || []
    })
    .finally(() => {
      loading.value = false
    })
}

function handleAddRoot() {
  resetForm()
  isEdit.value = false
  isAddRoot.value = true
  dialogVisible.value = true
}

function handleAddChild(parent: MenuNode) {
  resetForm()
  isEdit.value = false
  isAddRoot.value = false
  formData.parentId = parent.id
  formData.menuCategoryValues = parseMenuCategory(parent.menuCategory)
  dialogVisible.value = true
}

function handleEdit(menu: MenuNode) {
  isEdit.value = true
  isAddRoot.value = false
  formData.id = menu.id
  formData.menuName = menu.menuName
  formData.menuCode = menu.menuCode
  formData.menuCategoryValues = parseMenuCategory(menu.menuCategory)
  formData.parentId = menu.parentId
  formData.menuPath = menu.menuPath || ''
  formData.menuIcon = menu.menuIcon || ''
  formData.sortCode = menu.sortCode || 0
  formData.isEnabled = menu.isEnabled ?? 1
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return

    const payload = {
      id: formData.id,
      menuName: formData.menuName,
      menuCode: formData.menuCode,
      menuCategory: formData.menuCategoryValues.join(','),
      parentId: formData.parentId,
      menuPath: formData.menuPath,
      menuIcon: formData.menuIcon,
      sortCode: formData.sortCode,
      isEnabled: formData.isEnabled
    }

    const api = isEdit.value ? menuApi.update : menuApi.create
    api(payload).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(menu: MenuNode) {
  const hasChildren = !!menu.children?.length
  const message = hasChildren ? '该菜单存在子菜单，确认一并删除？' : '确认删除该菜单？'
  ElMessageBox.confirm(message, '提示', { type: 'warning' }).then(() => {
    menuApi.delete(menu.id).then(() => {
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
.toolbar {
  margin-bottom: 16px;
}

.menu-tree {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 12px;
}

.tree-node-content {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 12px;
}

.node-label {
  font-weight: 500;
  min-width: 120px;
}

.node-meta {
  color: var(--text-secondary);
  font-size: 12px;
}

.node-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}
</style>
