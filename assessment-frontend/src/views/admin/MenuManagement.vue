<template>
  <div class="page-card">
    <h2 class="page-title">功能/菜单定义 v1</h2>
    <p class="page-subtitle">定义系统功能菜单结构</p>

    <!-- 顶部操作 -->
    <div class="toolbar">
      <el-button type="primary" @click="handleAddRoot">
        <el-icon><Plus /></el-icon>新增根菜单
      </el-button>
    </div>

    <!-- 菜单树 -->
    <el-tree
      :data="menuTree"
      :props="{ label: 'menuName', children: 'children' }"
      node-key="id"
      default-expand-all
      highlight-current
      class="menu-tree"
    >
      <template #default="{ node, data }">
        <div class="tree-node-content">
          <span class="node-label">{{ data.menuName }}</span>
          <span class="node-meta">编码: {{ data.menuCode }}</span>
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

    <!-- 新增/编辑弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="菜单编码" prop="menuCode">
          <el-input v-model="formData.menuCode" placeholder="请输入菜单编码" />
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { menuApi } from '@/api/admin'

const loading = ref(false)
const menuTree = ref<any[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const isAddRoot = ref(false)
const currentParent = ref<any>(null)
const formRef = ref<any>(null)

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑菜单'
  if (isAddRoot.value) return '新增根菜单'
  return '新增子菜单'
})

const formData = reactive({
  id: undefined as number | undefined,
  menuName: '',
  menuCode: '',
  parentId: undefined as number | undefined,
  menuPath: '',
  menuIcon: '',
  sortCode: 0,
  isEnabled: 1
})

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuCode: [{ required: true, message: '请输入菜单编码', trigger: 'blur' }]
}

// 用于 tree-select 的数据，编辑时排除当前节点及其子节点
const menuTreeForSelect = computed(() => {
  if (!isEdit.value || !formData.id) return menuTree.value
  return filterTree(menuTree.value, formData.id)
})

function filterTree(tree: any[], excludeId: number): any[] {
  return tree
    .filter(node => node.id !== excludeId)
    .map(node => ({
      ...node,
      children: node.children ? filterTree(node.children, excludeId) : []
    }))
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
  isEdit.value = false
  isAddRoot.value = true
  currentParent.value = null
  formData.id = undefined
  formData.menuName = ''
  formData.menuCode = ''
  formData.parentId = undefined
  formData.menuPath = ''
  formData.menuIcon = ''
  formData.sortCode = 0
  formData.isEnabled = 1
  dialogVisible.value = true
}

function handleAddChild(parent: any) {
  isEdit.value = false
  isAddRoot.value = false
  currentParent.value = parent
  formData.id = undefined
  formData.menuName = ''
  formData.menuCode = ''
  formData.parentId = parent.id
  formData.menuPath = ''
  formData.menuIcon = ''
  formData.sortCode = 0
  formData.isEnabled = 1
  dialogVisible.value = true
}

function handleEdit(data: any) {
  isEdit.value = true
  isAddRoot.value = false
  currentParent.value = null
  formData.id = data.id
  formData.menuName = data.menuName
  formData.menuCode = data.menuCode
  formData.parentId = data.parentId
  formData.menuPath = data.menuPath
  formData.menuIcon = data.menuIcon
  formData.sortCode = data.sortCode
  formData.isEnabled = data.isEnabled
  dialogVisible.value = true
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const api = isEdit.value ? menuApi.update : menuApi.create
    api({ ...formData }).then(() => {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    })
  })
}

function handleDelete(data: any) {
  const hasChildren = data.children && data.children.length > 0
  const msg = hasChildren ? '该菜单存在子菜单，确认一并删除？' : '确认删除该菜单？'
  ElMessageBox.confirm(msg, '提示', { type: 'warning' }).then(() => {
    menuApi.delete(data.id).then(() => {
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
