<template>
  <div class="page-card">
    <h2 class="page-title">系统用户管理 v1</h2>
    <p class="page-subtitle">管理系统用户账号、角色及启用状态</p>

    <!-- 搜索区 -->
    <el-form inline :model="searchForm" class="search-form">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="用户名/姓名" clearable />
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
      <el-form-item label="启用状态">
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
          <el-icon><Plus /></el-icon>新增用户
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table :data="tableData" stripe v-loading="loading" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="关联人员" width="120" />
      <!-- 角色名称列已隐藏，通过"分配权限"弹框管理 -->
      <el-table-column prop="orgName" label="所属组织" min-width="160" />
      <el-table-column prop="isEnabled" label="是否启用" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.isEnabled"
            :active-value="1"
            :inactive-value="0"
            @change="(val: number) => handleToggleEnabled(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录时间" width="160">
        <template #default="{ row }">
          {{ row.lastLoginTime || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="success" @click="handleAssignPermission(row)">分配权限</el-button>
          <el-button link type="warning" @click="handleResetPassword(row)">重置密码</el-button>
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

    <!-- 新增/编辑用户弹框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <!-- 新增时显示选择人员 -->
        <el-form-item v-if="!isEdit" label="选择人员" prop="empId">
          <el-select
            v-model="formData.empId"
            placeholder="请选择人员"
            style="width: 100%"
            filterable
            @change="handleEmpChange"
          >
            <el-option
              v-for="item in employeeOptions"
              :key="item.id"
              :label="`${item.employeeName}(${item.employeeNo}) - ${item.deptName || '—'}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <!-- 新增时显示用户名 -->
        <el-form-item v-if="!isEdit" label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="请输入用户名" />
        </el-form-item>

        <!-- 编辑时只读显示用户名 -->
        <el-form-item v-if="isEdit" label="用户名">
          <el-input v-model="formData.username" disabled />
        </el-form-item>

        <!-- 新增时显示密码 -->
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>

        <!-- 真实姓名 -->
        <el-form-item v-if="!isEdit" label="真实姓名">
          <el-input v-model="formData.realName" disabled />
        </el-form-item>
        <el-form-item v-if="isEdit" label="真实姓名">
          <el-input v-model="formData.realName" disabled />
        </el-form-item>

        <!-- 编辑时显示是否启用 -->
        <el-form-item v-if="isEdit" label="是否启用" prop="isEnabled">
          <el-switch v-model="formData.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 用户权限分配弹框 -->
    <el-dialog v-model="permDialogVisible" title="用户权限分配" width="900px" destroy-on-close>
      <div style="margin-bottom: 8px; color: var(--text-secondary);">
        当前用户：{{ currentPermUserName }}
      </div>

      <!-- 区域1: 新增权限 -->
      <div class="permission-add-section">
        <h4>新增权限</h4>
        <el-form :model="newPermForm" label-width="80px">
          <el-form-item label="角色">
            <el-select v-model="newPermForm.roleCode" placeholder="选择角色" style="width: 220px" @change="handleRoleChange">
              <el-option v-for="role in roleOptions" :key="role.roleCode"
                :label="role.roleName" :value="role.roleCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据范围">
            <el-radio-group v-model="newPermForm.dataScopeType" @change="handleDataScopeTypeChange">
              <el-radio value="ALL" :disabled="currentRoleType !== 'SYSTEM'">全部</el-radio>
              <el-radio value="UNIT" :disabled="currentRoleType === 'DEPT'">单位</el-radio>
              <el-radio value="ORG" :disabled="currentRoleType === 'UNIT'">考核组织</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="newPermForm.dataScopeType === 'UNIT'" label="选择单位">
            <el-select v-model="newPermForm.scopeId" placeholder="请选择单位" style="width: 250px" @change="handleUnitChange">
              <el-option v-for="unit in unitList" :key="unit.id" :label="unit.unitName" :value="unit.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="newPermForm.dataScopeType === 'ORG'" label="选择组织">
            <el-select v-model="newPermForm.scopeId" placeholder="请选择考核组织" style="width: 250px" @change="handleOrgChange">
              <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleAddPermission">确定</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域2: 已有权限列表 -->
      <div class="permission-list-section" style="margin-top: 20px;">
        <h4>已有权限列表</h4>
        <el-table :data="userPermissions" border size="small">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="roleName" label="角色名称" width="160" />
          <el-table-column label="数据范围" min-width="200">
            <template #default="{ row }">
              {{ formatDataScope(row) }}
            </template>
          </el-table-column>
          <el-table-column label="开始日期" width="150">
            <template #default="{ row }">
              <el-date-picker v-model="row.startDate" type="date" size="small"
                value-format="YYYY-MM-DD" style="width: 130px"
                @change="handleDateChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="失效日期" width="150">
            <template #default="{ row }">
              <el-date-picker v-model="row.endDate" type="date" size="small"
                value-format="YYYY-MM-DD" style="width: 130px"
                @change="handleDateChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleSavePermission(row)">保存</el-button>
              <el-button type="primary" link size="small" @click="handleEditPermission(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeletePermission(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 编辑权限弹框 -->
    <el-dialog v-model="editPermDialogVisible" title="编辑权限" width="500px" destroy-on-close>
      <el-form :model="editPermForm" label-width="100px">
        <el-form-item label="角色">
          <el-select v-model="editPermForm.roleCode" style="width: 100%">
            <el-option v-for="role in roleOptions" :key="role.roleCode"
              :label="role.roleName" :value="role.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-radio-group v-model="editPermForm.dataScopeType" @change="handleEditDataScopeTypeChange">
            <el-radio value="ALL" :disabled="editRoleType !== 'SYSTEM'">全部</el-radio>
            <el-radio value="UNIT" :disabled="editRoleType === 'DEPT'">单位</el-radio>
            <el-radio value="ORG" :disabled="editRoleType === 'UNIT'">考核组织</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editPermForm.dataScopeType === 'UNIT'" label="选择单位">
          <el-select v-model="editPermForm.scopeId" style="width: 100%" @change="handleEditUnitChange">
            <el-option v-for="unit in unitList" :key="unit.id" :label="unit.unitName" :value="unit.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editPermForm.dataScopeType === 'ORG'" label="选择组织">
          <el-select v-model="editPermForm.scopeId" style="width: 100%" @change="handleEditOrgChange">
            <el-option v-for="org in orgList" :key="org.id" :label="org.orgName" :value="org.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editPermDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEditPermission">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { sysUserApi, employeeApi, permissionApi, roleApi, examOrgApi, unitApi } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const employeeOptions = ref<any[]>([])

const roleMap: Record<string, string> = {
  ADMIN: '系统管理员',
  FIN_ADMIN: '计划财务处业绩考核管理员',
  DEPT_ADMIN: '部门绩效管理员',
  DEPT_LEADER: '部门负责人',
  SUPERVISOR: '分管领导'
}

const queryParams = reactive({
  current: 1,
  size: 10,
  keyword: '',
  roleCode: '',
  isEnabled: undefined as number | undefined
})

const searchForm = reactive({
  keyword: '',
  roleCode: '',
  isEnabled: undefined as number | undefined
})

function loadEmployees() {
  return employeeApi.all().then((res: any) => {
    employeeOptions.value = res.data || []
  })
}

function loadData() {
  loading.value = true
  sysUserApi.list({ ...queryParams })
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
  queryParams.roleCode = searchForm.roleCode
  queryParams.isEnabled = searchForm.isEnabled
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.roleCode = ''
  searchForm.isEnabled = undefined
  queryParams.current = 1
  queryParams.keyword = ''
  queryParams.roleCode = ''
  queryParams.isEnabled = undefined
  loadData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  loadData()
}

// ========== 新增/编辑用户弹框 ==========
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<any>(null)
const formData = reactive({
  id: undefined as number | undefined,
  empId: undefined as number | undefined,
  username: '',
  password: '',
  realName: '',
  orgId: undefined as number | undefined,
  orgName: '',
  unitId: undefined as number | undefined,
  isEnabled: 1
})

const formRules = {
  empId: [{ required: true, message: '请选择人员', trigger: 'change' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function handleAdd() {
  isEdit.value = false
  formData.id = undefined
  formData.empId = undefined
  formData.username = ''
  formData.password = ''
  formData.realName = ''
  formData.orgId = undefined
  formData.orgName = ''
  formData.unitId = undefined
  formData.isEnabled = 1
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.id = row.id
  formData.empId = row.empId
  formData.username = row.username
  formData.password = ''
  formData.realName = row.realName
  formData.orgId = row.orgId
  formData.orgName = row.orgName
  formData.unitId = row.unitId
  formData.isEnabled = row.isEnabled
  dialogVisible.value = true
}

function handleEmpChange(empId: number) {
  const emp = employeeOptions.value.find((e: any) => e.id === empId)
  if (emp) {
    formData.realName = emp.employeeName
    formData.orgId = emp.deptId
    formData.orgName = emp.deptName
    formData.unitId = emp.unitId
  }
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return
    const submitData: any = { ...formData }
    if (isEdit.value) {
      delete submitData.password
      delete submitData.empId
      sysUserApi.update(submitData).then(() => {
        ElMessage.success('编辑成功')
        dialogVisible.value = false
        loadData()
      })
    } else {
      // 新增时映射字段：empId -> employeeId
      submitData.employeeId = submitData.empId
      delete submitData.empId
      sysUserApi.create(submitData).then(() => {
        ElMessage.success('新增成功')
        dialogVisible.value = false
        loadData()
      })
    }
  })
}

function handleToggleEnabled(row: any, val: number) {
  sysUserApi.toggleEnabled(row.id).then(() => {
    ElMessage.success(val === 1 ? '已启用' : '已禁用')
    loadData()
  }).catch(() => {
    row.isEnabled = val === 1 ? 0 : 1
  })
}

function handleResetPassword(row: any) {
  ElMessageBox.confirm('确认重置该用户的密码？', '提示', { type: 'warning' }).then(() => {
    sysUserApi.resetPassword(row.id).then((res: any) => {
      const temporaryPassword = res.data || res
      ElMessageBox.alert(`临时密码：${temporaryPassword}`, '密码已重置', {
        confirmButtonText: '知道了'
      })
    })
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除该用户？', '提示', { type: 'warning' }).then(() => {
    sysUserApi.delete(row.id).then(() => {
      ElMessage.success('删除成功')
      loadData()
    })
  })
}

// ========== 用户权限分配弹框 ==========
const permDialogVisible = ref(false)
const currentPermUserId = ref<number>()
const currentPermUserName = ref('')
const userPermissions = ref<any[]>([])
const roleOptions = ref<any[]>([])

// 单位列表
const unitList = ref<Array<{id: number, unitName: string}>>([])
const loadUnitList = async () => {
  try {
    const res = await unitApi.list({})
    const records = res.data?.records || res.data || []
    unitList.value = records.filter((u: any) => u.isEnabled !== false && u.isEnabled !== 0)
  } catch {
    unitList.value = []
  }
}

// 组织列表
const orgList = ref<Array<{id: number, orgName: string}>>([])
const loadOrgList = async () => {
  try {
    const res = await examOrgApi.list()
    orgList.value = (res.data || []).filter((o: any) => o.isEnabled !== false && o.isEnabled !== 0)
  } catch {
    orgList.value = []
  }
}

// 新增权限表单
const newPermForm = reactive({
  roleCode: '',
  dataScopeType: 'UNIT' as 'ALL' | 'UNIT' | 'ORG',
  scopeId: null as number | null,
  scopeName: '' as string
})

// 编辑权限弹框
const editPermDialogVisible = ref(false)
const editPermForm = reactive({
  id: undefined as number | undefined,
  roleCode: '',
  dataScopeType: 'UNIT' as 'ALL' | 'UNIT' | 'ORG',
  scopeId: null as number | null,
  scopeName: '' as string
})

// 当前选择角色的 roleType（新增权限表单）
const currentRoleType = computed(() => {
  const role = roleOptions.value.find((r: any) => r.roleCode === newPermForm.roleCode)
  return role?.roleType || 'DEPT'
})

// 编辑权限表单中角色的 roleType
const editRoleType = computed(() => {
  const role = roleOptions.value.find((r: any) => r.roleCode === editPermForm.roleCode)
  return role?.roleType || 'DEPT'
})

// 角色变更时自动设置 dataScopeType
function handleRoleChange(roleCode: string) {
  const role = roleOptions.value.find((r: any) => r.roleCode === roleCode)
  const roleType = role?.roleType || 'DEPT'

  // 根据类型自动设置默认数据范围
  if (roleType === 'UNIT') {
    newPermForm.dataScopeType = 'UNIT'
  } else if (roleType === 'DEPT') {
    newPermForm.dataScopeType = 'ORG'
  } else if (roleType === 'SYSTEM') {
    newPermForm.dataScopeType = 'ALL'
  }

  // 清空已选范围
  newPermForm.scopeId = null
  newPermForm.scopeName = ''
}

// 数据范围类型变更时清空选择
const handleDataScopeTypeChange = () => {
  newPermForm.scopeId = null
  newPermForm.scopeName = ''
}

// 编辑表单数据范围类型变更
const handleEditDataScopeTypeChange = () => {
  editPermForm.scopeId = null
  editPermForm.scopeName = ''
}

// 选择单位时填充 scopeName
const handleUnitChange = (unitId: number) => {
  const unit = unitList.value.find(u => u.id === unitId)
  newPermForm.scopeName = unit ? unit.unitName : ''
}

// 选择组织时填充 scopeName
const handleOrgChange = (orgId: number) => {
  const org = orgList.value.find(o => o.id === orgId)
  newPermForm.scopeName = org ? org.orgName : ''
}

// 编辑时选择单位
const handleEditUnitChange = (unitId: number) => {
  const unit = unitList.value.find(u => u.id === unitId)
  editPermForm.scopeName = unit ? unit.unitName : ''
}

// 编辑时选择组织
const handleEditOrgChange = (orgId: number) => {
  const org = orgList.value.find(o => o.id === orgId)
  editPermForm.scopeName = org ? org.orgName : ''
}

// 格式化数据范围展示
const formatDataScope = (row: any) => {
  if (row.dataScope === 'ALL') return '全部'
  return row.scopeName || row.dataScope
}

// 打开权限分配弹框
async function handleAssignPermission(row: any) {
  currentPermUserId.value = row.id
  currentPermUserName.value = row.realName || row.username
  permDialogVisible.value = true

  // 重置新增权限表单
  newPermForm.roleCode = ''
  newPermForm.dataScopeType = 'UNIT'
  newPermForm.scopeId = null
  newPermForm.scopeName = ''

  // 加载角色列表
  try {
    const roleRes = await roleApi.getAvailable()
    roleOptions.value = roleRes.data || []
  } catch {
    roleOptions.value = []
  }

  // 加载单位列表和考核组织列表
  await Promise.all([loadUnitList(), loadOrgList()])

  // 加载用户已有权限
  await loadUserPermissions()
}

async function loadUserPermissions() {
  try {
    const res = await permissionApi.getUserPermissions(currentPermUserId.value!)
    userPermissions.value = (res.data || []).map((p: any) => ({
      ...p,
      roleName: roleOptions.value.find(r => r.roleCode === p.roleCode)?.roleName || p.roleCode
    }))
  } catch {
    userPermissions.value = []
  }
}

// 新增权限
async function handleAddPermission() {
  if (!newPermForm.roleCode) {
    ElMessage.warning('请选择角色')
    return
  }
  if (newPermForm.dataScopeType !== 'ALL' && !newPermForm.scopeId) {
    ElMessage.warning('请选择数据范围')
    return
  }

  // 唯一性校验
  const duplicate = userPermissions.value.find(p =>
    p.roleCode === newPermForm.roleCode &&
    p.dataScope === newPermForm.dataScopeType &&
    (p.scopeId || 0) === (newPermForm.dataScopeType === 'ALL' ? 0 : newPermForm.scopeId || 0)
  )
  if (duplicate) {
    ElMessage.warning('该角色的此数据范围已存在')
    return
  }

  try {
    await permissionApi.create({
      userId: currentPermUserId.value,
      userName: currentPermUserName.value,
      roleCode: newPermForm.roleCode,
      dataScope: newPermForm.dataScopeType,
      scopeId: newPermForm.dataScopeType === 'ALL' ? 0 : newPermForm.scopeId,
      scopeName: newPermForm.dataScopeType === 'ALL' ? '全部' : newPermForm.scopeName,
      startDate: new Date().toISOString().split('T')[0],
      endDate: null
    })
    ElMessage.success('权限添加成功')

    // 重置表单
    newPermForm.roleCode = ''
    newPermForm.dataScopeType = 'UNIT'
    newPermForm.scopeId = null
    newPermForm.scopeName = ''

    await loadUserPermissions()
  } catch {
    ElMessage.error('权限添加失败')
  }
}

// 日期变更保存
async function handleSavePermission(row: any) {
  try {
    await permissionApi.update({
      id: row.id,
      userId: row.userId,
      roleCode: row.roleCode,
      dataScope: row.dataScope,
      scopeId: row.scopeId,
      scopeName: row.scopeName,
      startDate: row.startDate || null,
      endDate: row.endDate || null
    })
    ElMessage.success('保存成功')
    await loadUserPermissions()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function handleDeletePermission(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该权限记录？', '提示', { type: 'warning' })
    await permissionApi.delete(row.id)
    ElMessage.success('删除成功')
    await loadUserPermissions()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

async function handleDateChange(row: any) {
  // 校验失效日期 > 开始日期
  if (row.endDate && row.startDate && row.endDate <= row.startDate) {
    ElMessage.warning('失效日期必须晚于开始日期')
    return
  }
  try {
    await permissionApi.update({
      id: row.id,
      userId: row.userId,
      userName: row.userName,
      roleCode: row.roleCode,
      dataScope: row.dataScope,
      scopeId: row.scopeId,
      scopeName: row.scopeName,
      startDate: row.startDate,
      endDate: row.endDate
    })
  } catch {
    ElMessage.error('日期保存失败')
  }
}

// 编辑权限
function handleEditPermission(row: any) {
  editPermForm.id = row.id
  editPermForm.roleCode = row.roleCode
  editPermForm.dataScopeType = row.dataScope || 'UNIT'
  editPermForm.scopeId = row.scopeId || null
  editPermForm.scopeName = row.scopeName || ''
  editPermDialogVisible.value = true
}

async function handleSaveEditPermission() {
  if (editPermForm.dataScopeType !== 'ALL' && !editPermForm.scopeId) {
    ElMessage.warning('请选择数据范围')
    return
  }

  const perm = userPermissions.value.find(p => p.id === editPermForm.id)
  try {
    await permissionApi.update({
      id: editPermForm.id,
      userId: currentPermUserId.value,
      userName: currentPermUserName.value,
      roleCode: editPermForm.roleCode,
      dataScope: editPermForm.dataScopeType,
      scopeId: editPermForm.dataScopeType === 'ALL' ? 0 : editPermForm.scopeId,
      scopeName: editPermForm.dataScopeType === 'ALL' ? '全部' : editPermForm.scopeName,
      startDate: perm?.startDate,
      endDate: perm?.endDate
    })

    ElMessage.success('权限修改成功')
    editPermDialogVisible.value = false
    await loadUserPermissions()
  } catch {
    ElMessage.error('权限修改失败')
  }
}

onMounted(() => {
  Promise.all([loadEmployees()]).then(() => loadData())
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
.permission-add-section {
  h4 {
    margin: 0 0 12px 0;
    font-size: 15px;
    color: var(--text-primary);
  }
}
.permission-list-section {
  h4 {
    margin: 0 0 12px 0;
    font-size: 15px;
    color: var(--text-primary);
  }
}
</style>

