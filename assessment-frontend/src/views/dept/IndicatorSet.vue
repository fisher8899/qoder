<template>
  <div class="indicator-set">
    <!-- 搜索区 -->
    <SearchForm
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 主表格 -->
    <DataTable
      :columns="columns"
      :data="pagedData"
      :loading="loading"
      :total="total"
      :current-page="queryParams.current"
      :page-size="queryParams.size"
      show-index
      @page-change="handlePageChange"
    >
      <template #examType="{ row }">
        {{ row.examType === 'MONTHLY' ? '月度考核' : row.examType === 'ANNUAL' ? '年度考核' : row.examType }}
      </template>
      <template #approvalStatus="{ row }">
        <StatusTag :status="row.approvalStatus || 'DRAFT'" :status-map="approvalStatusMap" />
      </template>
      <template #operation="{ row }">
        <el-button
          v-if="isEditable(row.approvalStatus)"
          link
          type="primary"
          @click="handleSet(row)"
        >设定目标</el-button>
        <el-button
          v-else
          link
          type="info"
          @click="handleView(row)"
        >查看</el-button>
      </template>
    </DataTable>

    <!-- 编辑弹框 -->
    <el-dialog
      v-model="editDialogVisible"
      :width="isFullscreen ? '100%' : '1100px'"
      :fullscreen="isFullscreen"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
          <span class="dialog-title">业绩指标目标设定 — {{ currentRow?.examGroupName || '' }}</span>
          <el-button
            :icon="isFullscreen ? 'Aim' : 'FullScreen'"
            circle
            size="small"
            @click="isFullscreen = !isFullscreen"
            style="margin-right: 30px;"
          />
        </div>
      </template>
      <!-- 审批流程步骤条 -->
      <div class="approval-steps">
        <div
          v-for="(step, idx) in approvalSteps"
          :key="idx"
          class="step-item"
          :class="{
            'step-done': getStepIndex(currentRow?.approvalStatus) > idx,
            'step-current': getStepIndex(currentRow?.approvalStatus) === idx
          }"
        >
          <span class="step-text">{{ step }}</span>
          <span v-if="idx < approvalSteps.length - 1" class="step-arrow">→</span>
        </div>
      </div>

      <!-- 指标大类卡片区 -->
      <div class="category-cards">
        <div
          v-for="cat in categoryCards"
          :key="cat.id"
          class="category-card"
          :class="{ active: activeCategory === cat.name }"
          @click="activeCategory = cat.name"
        >
          <div class="card-top">
            <span class="card-emoji">{{ cat.emoji }}</span>
            <span class="card-name">{{ cat.name }}</span>
            <span v-if="cat.weightSum > 0" class="card-weight">{{ cat.weightSum }}%</span>
          </div>
          <span class="card-stat">{{ cat.subCount }}小类·{{ cat.itemCount }}指标</span>
        </div>
      </div>

      <!-- 小类列表区 -->
      <div class="subcategory-section">
        <div class="section-header-row">
          <div class="section-title">小类列表 — {{ activeCategory }}</div>
          <div class="weight-summary">
            <span class="weight-item">
              <span class="weight-label">年度权重合计：</span>
              <span class="weight-value" :class="{ 'weight-ok': currentCategoryAnnualTotal === 100, 'weight-warn': currentCategoryAnnualTotal !== 100 }">
                {{ currentCategoryAnnualTotal }}%
              </span>
            </span>
            <span class="weight-divider">|</span>
            <span class="weight-item">
              <span class="weight-label">月度权重合计：</span>
              <span class="weight-value" :class="{ 'weight-ok': currentCategoryMonthlyTotal === 100, 'weight-warn': currentCategoryMonthlyTotal !== 100 }">
                {{ currentCategoryMonthlyTotal }}%
              </span>
            </span>
          </div>
        </div>
        <div class="subcategory-list">
          <div
            v-for="(sub, idx) in currentSubCategories"
            :key="sub.name"
            class="subcategory-item"
            :class="{ active: activeSubCategory === sub.name }"
            @click="activeSubCategory = sub.name"
          >
            <div class="sub-left">
              <span class="sub-index">{{ idx + 1 }}</span>
              <span class="sub-name">{{ sub.name }}</span>
              <span class="sub-desc" :title="sub.summary">{{ sub.summary }}</span>
            </div>
            <div class="sub-right">
              <el-button link type="primary" size="small" @click.stop="editSubCategory(sub)">编辑</el-button>
              <el-button link type="danger" size="small" @click.stop="deleteSubCategory(sub)">删除</el-button>
            </div>
          </div>
          <div class="add-subcategory" @click="addSubCategory">+ 新增小类</div>
        </div>
      </div>

      <!-- 考核内容表格区 -->
      <div class="content-section">
        <div class="section-header">
          <span class="section-title">考核内容 — {{ activeSubCategory }}</span>
          <div class="section-toolbar">
            <el-button type="primary" size="small" @click="addContent">+ 新增考核内容</el-button>
            <el-button size="small" @click="handleBatchImport">批量导入</el-button>
          </div>
        </div>
        <el-table :data="filteredIndicators" border size="small" :max-height="tableMaxHeight">
          <el-table-column type="index" label="序号" width="50" />
          <el-table-column label="考核内容" min-width="180">
            <template #default="{ row }">
              <el-input v-if="row.isEditing" v-model="row.content" placeholder="考核内容" />
              <span v-else>{{ row.content }}</span>
            </template>
          </el-table-column>
          <el-table-column label="指标/目标" min-width="150">
            <template #default="{ row }">
              <el-input v-if="row.isEditing" v-model="row.targetDesc" placeholder="指标/目标" />
              <span v-else>{{ row.targetDesc }}</span>
            </template>
          </el-table-column>
          <el-table-column label="权重(年度)" width="100">
            <template #default="{ row }">
              <div v-if="row.isEditing" style="display: flex; align-items: center; gap: 2px;">
                <el-input
                  v-model="row.weightAnnual"
                  placeholder="整数"
                  style="width: 65px"
                  @input="(val: string) => { row.weightAnnual = val.replace(/[^0-9]/g, '') }"
                />
                <span style="color: #666; font-size: 13px;">%</span>
              </div>
              <span v-else>{{ row.weightAnnual }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="权重(月度)" width="100">
            <template #default="{ row }">
              <div v-if="row.isEditing" style="display: flex; align-items: center; gap: 2px;">
                <el-input
                  v-model="row.weightMonthly"
                  placeholder="数值"
                  style="width: 65px"
                  @input="(val: string) => { row.weightMonthly = val.replace(/[^0-9.]/g, '').replace(/(\..*?)\./g, '$1') }"
                />
                <span style="color: #666; font-size: 13px;">%</span>
              </div>
              <span v-else>{{ row.weightMonthly }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="考核标准" min-width="100">
            <template #default="{ row }">
              <el-input v-if="row.isEditing" v-model="row.evaluationStandard" placeholder="考核标准" />
              <template v-else>
                <el-tooltip :content="row.evaluationStandard" placement="top" :disabled="!row.evaluationStandard">
                  <span class="view-link">查看</span>
                </el-tooltip>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="考核部门/分管领导" width="220">
            <template #default="{ row }">
              <!-- 非编辑状态 -->
              <template v-if="!row.isEditing">
                <span v-if="row.examTargetType === 'LEADER' && row.leaderNames?.length">
                  🏛️ {{ row.leaderNames.join('、') }}
                </span>
                <span v-else>
                  {{ row.orgNames?.join('、') || row.orgName || currentRow?.orgName || userStore.userInfo?.orgName || '' }}
                </span>
              </template>
              <!-- 编辑状态 -->
              <template v-else>
                <div class="exam-target-editor">
                  <el-radio-group
                    v-model="row.examTargetType"
                    size="small"
                    style="margin-bottom: 4px"
                  >
                    <el-radio value="DEPARTMENT">部门</el-radio>
                    <el-radio value="LEADER">分管领导</el-radio>
                  </el-radio-group>
                  <el-select
                    v-if="row.examTargetType === 'DEPARTMENT'"
                    v-model="row.orgIds"
                    placeholder="选择部门(可多选)"
                    size="small"
                    filterable
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    style="width: 100%"
                    @change="(vals: number[]) => onOrgMultiSelect(row, vals)"
                  >
                    <el-option
                      v-for="org in orgList"
                      :key="org.id"
                      :label="org.orgName"
                      :value="org.id"
                    />
                  </el-select>
                  <el-select
                    v-else
                    v-model="row.leaderIds"
                    placeholder="选择分管领导(可多选)"
                    size="small"
                    filterable
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    style="width: 100%"
                    @change="(vals: number[]) => onLeaderMultiSelect(row, vals)"
                  >
                    <el-option
                      v-for="leader in leaderList"
                      :key="leader.id"
                      :label="leader.leaderName"
                      :value="leader.id"
                    />
                  </el-select>
                </div>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="100">
            <template #default="{ row }">
              <el-input v-if="row.isEditing" v-model="row.remark" placeholder="备注" />
              <span v-else>{{ row.remark }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row, $index }">
              <template v-if="row.isEditing">
                <el-button link type="primary" size="small" @click="saveIndicatorRow(row)">保存</el-button>
                <el-button link type="info" size="small" @click="cancelEditRow(row, $index)">取消</el-button>
              </template>
              <template v-else>
                <el-button link type="primary" size="small" @click="editIndicatorRow(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="deleteIndicatorRow(row)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button @click="handleSaveDraft">保存草稿</el-button>
        <el-button type="primary" @click="handleSubmitApproval">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen, Aim } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import {
  getIndicatorList,
  createIndicator,
  updateIndicator,
  deleteIndicator,
  submitIndicatorForApproval
} from '@/api/indicator'
import type { IndicatorData } from '@/api/indicator'
import { getMyExamGroupTasks } from '@/api/examGroup'
import type { ExamGroupTaskVO } from '@/api/examGroup'
import { indicatorCategoryApi, organizationApi, leaderApi } from '@/api/admin'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const rawData = ref<any[]>([])
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  groupName: '',
  examType: '',
  approvalStatus: '',
  startDate: '',
  endDate: ''
})

const searchFields: SearchField[] = [
  { prop: 'groupName', label: '考核组名称', type: 'input', placeholder: '请输入考核组名称' },
  {
    prop: 'examType',
    label: '考核类型',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '月度考核', value: 'MONTHLY' },
      { label: '年度考核', value: 'ANNUAL' }
    ]
  },
  {
    prop: 'approvalStatus',
    label: '目标设定状态',
    type: 'select',
    placeholder: '请选择状态',
    options: [
      { label: '待设定/草稿', value: 'DRAFT' },
      { label: '待部门负责人审批', value: 'PENDING_DEPT_LEADER' },
      { label: '待分管领导审批', value: 'PENDING_SUPERVISOR' },
      { label: '待财务处审批', value: 'PENDING_FINANCE' },
      { label: '审批通过', value: 'APPROVED' },
      { label: '被退回', value: 'REJECTED' }
    ]
  },
  {
    prop: 'orgName',
    label: '考核部门',
    type: 'input',
    placeholder: ''
  } as any,
  { prop: 'startDate', label: '考核开始时间', type: 'date', placeholder: '选择开始时间' },
  { prop: 'endDate', label: '考核结束时间', type: 'date', placeholder: '选择结束时间' }
]

const columns: TableColumn[] = [
  { prop: 'examGroupName', label: '考核组名称', minWidth: 180 },
  { prop: 'examType', label: '考核类型', width: 100 },
  { prop: 'approvalStatus', label: '考核目标设定状态', width: 160 },
  { prop: 'startDate', label: '考核开始日期', width: 120 },
  { prop: 'endDate', label: '考核结束日期', width: 120 },
  { prop: 'operation', label: '操作', width: 100 }
]

const approvalStatusMap: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary'; class?: string }> = {
  DRAFT: { text: '待设定/草稿', type: 'warning', class: '' },
  PENDING_DEPT_LEADER: { text: '待部门负责人审批', type: 'info', class: 'tag-purple' },
  PENDING_SUPERVISOR: { text: '待分管领导审批', type: 'info', class: 'tag-purple' },
  PENDING_FINANCE: { text: '待财务处审批', type: 'info', class: 'tag-purple' },
  APPROVED: { text: '审批通过', type: 'success', class: '' },
  REJECTED: { text: '被退回', type: 'danger', class: '' }
}

// 审批步骤
const approvalSteps = ['草稿', '待部门负责人审批', '待分管领导审批', '待财务处审批', '审批通过']

function getStepIndex(status?: string): number {
  const map: Record<string, number> = {
    DRAFT: 0,
    PENDING_DEPT_LEADER: 1,
    PENDING_SUPERVISOR: 2,
    PENDING_FINANCE: 3,
    APPROVED: 4,
    REJECTED: 0
  }
  return map[status || 'DRAFT'] ?? 0
}

function isEditable(status?: string): boolean {
  return !status || status === 'DRAFT' || status === 'REJECTED'
}

// 分页
const pagedData = computed(() => {
  const start = (queryParams.current - 1) * queryParams.size
  return tableData.value.slice(start, start + queryParams.size)
})

function aggregateByGroup(records: any[]) {
  // 不再需要从指标数据聚合，仅作为兜底
  const map = new Map<string, any>()
  for (const item of records) {
    const key = `${item.examGroupId}-${item.orgId}`
    if (!map.has(key)) {
      map.set(key, {
        examGroupId: item.examGroupId,
        orgId: item.orgId,
        examGroupName: item.examGroupName,
        examType: item.examType,
        examCategory: item.examCategory,
        startDate: item.startDate,
        endDate: item.endDate,
        approvalStatus: item.approvalStatus,
        orgName: item.orgName
      })
    }
  }
  return Array.from(map.values())
}

function filterData() {
  let list = [...rawData.value]
  if (queryParams.groupName) {
    list = list.filter((item: any) => item.examGroupName?.includes(queryParams.groupName))
  }
  if (queryParams.examType) {
    list = list.filter((item: any) => item.examType === queryParams.examType)
  }
  if (queryParams.approvalStatus) {
    list = list.filter((item: any) => item.approvalStatus === queryParams.approvalStatus)
  }
  if (queryParams.startDate) {
    list = list.filter((item: any) => item.startDate >= queryParams.startDate)
  }
  if (queryParams.endDate) {
    list = list.filter((item: any) => item.endDate <= queryParams.endDate)
  }
  tableData.value = list
  total.value = list.length
}

function loadData() {
  loading.value = true
  getMyExamGroupTasks('INDICATOR_SET')
    .then((res: any) => {
      const tasks: ExamGroupTaskVO[] = res.data || []
      rawData.value = tasks.map((t: ExamGroupTaskVO) => ({
        examGroupId: t.examGroupId,
        orgId: t.orgId,
        examGroupName: t.examGroupName,
        examType: t.examType,
        examCategory: t.examCategory,
        startDate: t.startDate,
        endDate: t.endDate,
        approvalStatus: t.approvalStatus,
        orgName: t.orgName
      }))
      filterData()
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.groupName = data.groupName || ''
  queryParams.examType = data.examType || ''
  queryParams.approvalStatus = data.approvalStatus || ''
  queryParams.startDate = data.startDate || ''
  queryParams.endDate = data.endDate || ''
  filterData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.groupName = ''
  queryParams.examType = ''
  queryParams.approvalStatus = ''
  queryParams.startDate = ''
  queryParams.endDate = ''
  filterData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
}

// =========== 编辑弹框逻辑 ===========
const editDialogVisible = ref(false)
const isFullscreen = ref(false)
const currentRow = ref<any>(null)
const indicatorList = ref<any[]>([])
const activeCategory = ref('')
const activeSubCategory = ref('')

// 表格高度自适应
const tableMaxHeight = computed(() => isFullscreen.value ? 'calc(100vh - 380px)' : '500px')

// 大类名称 -> emoji 映射
const categoryEmojiMap: Record<string, string> = {
  '经营指标': '📊',
  '重点工作': '📋',
  '重点任务': '📌',
  '基础工作': '📄',
  '动态督办事项': '⏰',
  '党建考核': '🏛️',
  '控制指标': '🔒',
  '特殊贡献指标': '⭐',
  '否决项目': '❌'
}

// 大类卡片配置 - 动态加载
const dynamicCategories = ref<{ id: number; name: string; sortCode: number }[]>([])

// 组织列表与分管领导列表
const orgList = ref<any[]>([])
const leaderList = ref<any[]>([])

// 自定义小类（用户新增的）— 按大类隔离
const customSubCategories = ref<Record<string, string[]>>({})

// 按大类分组的指标数据
const groupedByCategory = computed(() => {
  const map = new Map<string, any[]>()
  for (const item of indicatorList.value) {
    const cat = item.categoryName || dynamicCategories.value[0]?.name || ''
    if (!map.has(cat)) map.set(cat, [])
    map.get(cat)!.push(item)
  }
  return map
})

// 大类卡片数据 - 基于动态加载
const categoryCards = computed(() => {
  const cards: { id: number; name: string; emoji: string; subCount: number; itemCount: number; weightSum: number }[] = []
  for (let i = 0; i < dynamicCategories.value.length; i++) {
    const def = dynamicCategories.value[i]
    const items = groupedByCategory.value.get(def.name) || []
    const subs = new Set(items.map(i => i.subCategory).filter(Boolean))
    const weightSum = items.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0)
    cards.push({
      id: def.id,
      name: def.name,
      emoji: categoryEmojiMap[def.name] || '\u{1F4C1}',
      subCount: subs.size,
      itemCount: items.length,
      weightSum
    })
  }
  return cards
})

// 加载指标大类
async function loadCategories() {
  const orgType = userStore.userInfo?.orgType
  try {
    const res: any = await indicatorCategoryApi.all(orgType || undefined)
    dynamicCategories.value = (res.data || []).map((item: any) => ({
      id: item.id,
      name: item.categoryName,
      sortCode: item.sortCode
    }))
    // 设置默认选中第一个大类
    if (dynamicCategories.value.length > 0) {
      activeCategory.value = dynamicCategories.value[0].name
    }
  } catch (e) {
    console.error('加载指标大类失败', e)
  }
}

// 加载组织列表
async function loadOrgList() {
  try {
    const res: any = await organizationApi.all()
    orgList.value = res.data || []
  } catch (e) {
    console.error('加载组织列表失败', e)
  }
}

// 加载分管领导列表
async function loadLeaderList() {
  try {
    const res: any = await leaderApi.list()
    leaderList.value = res.data?.records || res.data || []
  } catch (e) {
    console.error('加载分管领导列表失败', e)
  }
}

// 当前大类下的小类（纯计算，无副作用）
const currentSubCategories = computed(() => {
  const items = groupedByCategory.value.get(activeCategory.value) || []
  const subMap = new Map<string, { name: string; summary: string }>()

  // 从指标数据中提取小类
  for (const item of items) {
    const sub = item.subCategory || '默认小类'
    if (!subMap.has(sub)) {
      subMap.set(sub, { name: sub, summary: item.evaluationStandard || item.content || '' })
    }
  }

  // 包含当前大类下的自定义小类（没有指标数据的）
  for (const cs of (customSubCategories.value[activeCategory.value] || [])) {
    if (!subMap.has(cs)) {
      subMap.set(cs, { name: cs, summary: '' })
    }
  }

  return Array.from(subMap.values())
})

// 当前大类的年度权重合计
const currentCategoryAnnualTotal = computed(() => {
  const items = indicatorList.value.filter(item =>
    item.categoryName === activeCategory.value && (item.id || item.content)
  )
  return Math.round(items.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0) * 100) / 100
})

// 当前大类的月度权重合计
const currentCategoryMonthlyTotal = computed(() => {
  const items = indicatorList.value.filter(item =>
    item.categoryName === activeCategory.value && (item.id || item.content)
  )
  return Math.round(items.reduce((sum, item) => sum + (Number(item.weightMonthly) || 0), 0) * 100) / 100
})

// 当前小类下的指标（表格数据）
const filteredIndicators = computed(() => {
  return indicatorList.value.filter(item => {
    const cat = item.categoryName || activeCategory.value
    const sub = item.subCategory || '默认小类'
    return cat === activeCategory.value && sub === activeSubCategory.value
  })
})

// 当大类切换时重置小类选中
watch(activeCategory, () => {
  const subs = currentSubCategories.value
  activeSubCategory.value = subs.length > 0 ? subs[0].name : ''
})

function handleSet(row: any) {
  currentRow.value = row
  editDialogVisible.value = true
  customSubCategories.value = {}
  // 动态加载大类 + 指标数据 + 组织列表 + 领导列表
  loadCategories()
  loadOrgList()
  loadLeaderList()
  loadIndicators(row.examGroupId, row.orgId)
}

function handleView(row: any) {
  handleSet(row)
}

function loadIndicators(examGroupId: number, orgId: number) {
  getIndicatorList({ examGroupId, orgId, current: 1, size: 1000 }).then((res: any) => {
    indicatorList.value = res.data.records.map((item: any) => ({
      ...item,
      isEditing: false,
      isNew: false,
      remark: item.remark || '',
      examTargetType: item.examTargetType || 'DEPARTMENT',
      // 多选支持：优先使用后端返回的数组，否则解析逗号分隔字符串
      orgIds: item.orgIdList?.length ? item.orgIdList : parseIds(item.orgId),
      orgNames: item.orgNameList?.length ? item.orgNameList : parseNames(item.orgName),
      leaderIds: item.leaderIdList?.length ? item.leaderIdList : parseIds(item.leaderId),
      leaderNames: item.leaderNameList?.length ? item.leaderNameList : parseNames(item.leaderName)
    }))
    // 设置默认小类
    const subs = currentSubCategories.value
    activeSubCategory.value = subs.length > 0 ? subs[0].name : ''
  })
}

// 解析逗号分隔的ID字符串为数组
function parseIds(val: any): number[] {
  if (!val) return []
  if (Array.isArray(val)) return val.map(Number)
  return String(val).split(',').filter(Boolean).map(Number)
}

// 解析逗号分隔的名称字符串为数组
function parseNames(val: any): string[] {
  if (!val) return []
  if (Array.isArray(val)) return val
  return String(val).split(',').filter(Boolean)
}

// 刷新指标列表，但保留仍在编辑或新增中的行（避免保存一行后丢失其他未保存行）
function reloadIndicatorsPreservePending(examGroupId: number, orgId: number) {
  const pendingRows = indicatorList.value.filter(item => item.isNew || item.isEditing)
  getIndicatorList({ examGroupId, orgId, current: 1, size: 1000 }).then((res: any) => {
    indicatorList.value = [
      ...res.data.records.map((item: any) => ({
        ...item,
        isEditing: false,
        isNew: false,
        remark: item.remark || '',
        examTargetType: item.examTargetType || 'DEPARTMENT',
        // 多选支持：优先使用后端返回的数组
        orgIds: item.orgIdList?.length ? item.orgIdList : parseIds(item.orgId),
        orgNames: item.orgNameList?.length ? item.orgNameList : parseNames(item.orgName),
        leaderIds: item.leaderIdList?.length ? item.leaderIdList : parseIds(item.leaderId),
        leaderNames: item.leaderNameList?.length ? item.leaderNameList : parseNames(item.leaderName)
      })),
      ...pendingRows
    ]
  })
}

// 新增小类
function addSubCategory() {
  ElMessageBox.prompt('请输入小类名称', '新增小类', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '小类名称不能为空'
  }).then(({ value }) => {
    const catKey = activeCategory.value
    const catSubs = customSubCategories.value[catKey] || []
    if (catSubs.includes(value)) {
      ElMessage.warning('该小类已存在')
      return
    }
    if (!customSubCategories.value[catKey]) {
      customSubCategories.value[catKey] = []
    }
    customSubCategories.value[catKey].push(value)
    activeSubCategory.value = value
  }).catch(() => {})
}

// 编辑小类（改名）
function editSubCategory(sub: { name: string }) {
  ElMessageBox.prompt('请输入新的小类名称', '编辑小类', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: sub.name,
    inputPattern: /\S+/,
    inputErrorMessage: '小类名称不能为空'
  }).then(({ value }) => {
    const oldName = sub.name
    // 更新指标数据中的小类名
    for (const item of indicatorList.value) {
      if (item.subCategory === oldName) {
        item.subCategory = value
      }
    }
    // 更新当前大类下的自定义小类
    const catSubs = customSubCategories.value[activeCategory.value]
    if (catSubs) {
      const idx = catSubs.indexOf(oldName)
      if (idx >= 0) catSubs[idx] = value
    }
    // 更新选中
    if (activeSubCategory.value === oldName) activeSubCategory.value = value
  }).catch(() => {})
}

// 删除小类
function deleteSubCategory(sub: { name: string }) {
  const items = indicatorList.value.filter(
    i => (i.categoryName || activeCategory.value) === activeCategory.value && i.subCategory === sub.name
  )
  if (items.length > 0) {
    ElMessage.warning('该小类下还有指标数据，请先删除指标')
    return
  }
  const catSubs = customSubCategories.value[activeCategory.value]
  if (catSubs) {
    const idx = catSubs.indexOf(sub.name)
    if (idx >= 0) catSubs.splice(idx, 1)
  }
  // 切换选中
  const subs = currentSubCategories.value
  activeSubCategory.value = subs.length > 0 ? subs[0].name : ''
}

// 新增考核内容
function addContent() {
  indicatorList.value.push({
    id: undefined,
    examGroupId: currentRow.value?.examGroupId,
    orgId: currentRow.value?.orgId || userStore.userInfo?.orgId,
    orgName: currentRow.value?.orgName || userStore.userInfo?.orgName,
    categoryName: activeCategory.value,
    subCategory: activeSubCategory.value,
    content: '',
    targetDesc: '',
    weightAnnual: 0,
    weightMonthly: 0,
    evaluationStandard: '',
    remark: '',
    isEditing: true,
    isNew: true,
    examTargetType: 'DEPARTMENT',
    // 多选支持：初始化数组
    orgIds: [currentRow.value?.orgId || userStore.userInfo?.orgId],
    orgNames: [currentRow.value?.orgName || userStore.userInfo?.orgName || ''],
    leaderIds: [],
    leaderNames: []
  })
}

function editIndicatorRow(row: any) {
  row.isEditing = true
  // 初始化 examTargetType 默认值
  if (!row.examTargetType) {
    row.examTargetType = 'DEPARTMENT'
  }
  // 初始化多选数组（如果不存在）
  if (!row.orgIds) row.orgIds = row.orgId ? parseIds(row.orgId) : []
  if (!row.orgNames) row.orgNames = row.orgName ? parseNames(row.orgName) : []
  if (!row.leaderIds) row.leaderIds = row.leaderId ? parseIds(row.leaderId) : []
  if (!row.leaderNames) row.leaderNames = row.leaderName ? parseNames(row.leaderName) : []
}
  }
}

// 选择部门（单选，保留兼容）
function onOrgSelect(row: any, orgId: number) {
  const org = orgList.value.find((o: any) => o.id === orgId)
  if (org) {
    row.orgId = org.id
    row.orgName = org.orgName
    row.examTargetType = 'DEPARTMENT'
    row.leaderId = undefined
    row.leaderName = undefined
  }
}

// 选择部门（多选）
function onOrgMultiSelect(row: any, orgIds: number[]) {
  row.orgIds = orgIds
  row.orgNames = orgIds.map(id => {
    const org = orgList.value.find((o: any) => o.id === id)
    return org?.orgName || ''
  }).filter(Boolean)
  row.examTargetType = 'DEPARTMENT'
  row.leaderIds = []
  row.leaderNames = []
}

// 选择分管领导（单选，保留兼容）
function onLeaderSelect(row: any, leaderId: number) {
  const leader = leaderList.value.find((l: any) => l.id === leaderId)
  if (leader) {
    row.leaderId = leader.id
    row.leaderName = leader.leaderName
    row.examTargetType = 'LEADER'
    // 分管领导模式下 orgId 保持当前用户部门
    row.orgId = currentRow.value?.orgId || userStore.userInfo?.orgId
    row.orgName = currentRow.value?.orgName || userStore.userInfo?.orgName
  }
}

// 选择分管领导（多选）
function onLeaderMultiSelect(row: any, leaderIds: number[]) {
  row.leaderIds = leaderIds
  row.leaderNames = leaderIds.map(id => {
    const leader = leaderList.value.find((l: any) => l.id === id)
    return leader?.leaderName || ''
  }).filter(Boolean)
  row.examTargetType = 'LEADER'
  // 分管领导模式下保持当前用户部门
  row.orgIds = [currentRow.value?.orgId || userStore.userInfo?.orgId]
  row.orgNames = [currentRow.value?.orgName || userStore.userInfo?.orgName || '']
}

function saveIndicatorRow(row: any) {
  if (!row.content) {
    ElMessage.warning('请填写考核内容')
    return
  }

  const data: IndicatorData = {
    id: row.id,
    examGroupId: row.examGroupId,
    orgId: row.orgIds?.[0] || currentRow.value?.orgId || userStore.userInfo?.orgId,
    categoryName: row.categoryName,
    subCategory: row.subCategory,
    content: row.content,
    targetDesc: row.targetDesc,
    weightAnnual: row.weightAnnual,
    weightMonthly: row.weightMonthly,
    evaluationStandard: row.evaluationStandard,
    examTargetType: row.examTargetType || 'DEPARTMENT',
    // 多选支持：发送数组格式
    orgIds: row.orgIds || [],
    orgNames: row.orgNames || [],
    leaderIds: row.examTargetType === 'LEADER' ? (row.leaderIds || []) : [],
    leaderNames: row.examTargetType === 'LEADER' ? (row.leaderNames || []) : []
  }
  const isNewRecord = !!(row.isNew && !row.id)
  const api = isNewRecord ? createIndicator : updateIndicator
  api(data).then((res: any) => {
    ElMessage.success('保存成功')
    row.isEditing = false
    row.isNew = false
    if (isNewRecord && res.data) {
      row.id = res.data
    }
  })
}

function cancelEditRow(row: any, index: number) {
  if (row.isNew && !row.id) {
    indicatorList.value = indicatorList.value.filter(item => item !== row)
  } else {
    row.isEditing = false
    // 取消编辑已有行时，刷新保留其他未保存行
    reloadIndicatorsPreservePending(row.examGroupId, row.orgId)
  }
}

function deleteIndicatorRow(row: any) {
  if (!row.id) {
    indicatorList.value = indicatorList.value.filter(item => item !== row)
    return
  }
  ElMessageBox.confirm('确认删除该指标？', '提示', { type: 'warning' }).then(() => {
    deleteIndicator(row.id).then(() => {
      ElMessage.success('删除成功')
      // 删除后刷新，保留其他未保存行
      reloadIndicatorsPreservePending(row.examGroupId, row.orgId)
    })
  })
}

function handleBatchImport() {
  ElMessage.info('批量导入功能开发中')
}

async function handleSaveDraft() {
  // 保存所有新增和修改的指标
  const pending = indicatorList.value.filter(item => item.isNew || item.isEditing)
  if (pending.length === 0) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  // 先校验所有行是否填写了考核内容
  for (const row of pending) {
    if (!row.content) {
      ElMessage.warning('请先填写所有考核内容')
      return
    }
  }
  const promises = pending.map(row => {
    const data: IndicatorData = {
      id: row.id,
      examGroupId: row.examGroupId,
      orgId: row.orgIds?.[0] || currentRow.value?.orgId || userStore.userInfo?.orgId,
      categoryName: row.categoryName,
      subCategory: row.subCategory,
      content: row.content,
      targetDesc: row.targetDesc,
      weightAnnual: row.weightAnnual,
      weightMonthly: row.weightMonthly,
      evaluationStandard: row.evaluationStandard,
      examTargetType: row.examTargetType || 'DEPARTMENT',
      // 多选支持：发送数组格式
      orgIds: row.orgIds || [],
      orgNames: row.orgNames || [],
      leaderIds: row.examTargetType === 'LEADER' ? (row.leaderIds || []) : [],
      leaderNames: row.examTargetType === 'LEADER' ? (row.leaderNames || []) : []
    }
    const isNewRecord = !!(row.isNew && !row.id)
    const api = isNewRecord ? createIndicator : updateIndicator
    return api(data).then((res: any) => {
      row.isEditing = false
      row.isNew = false
      if (isNewRecord && res.data) {
        row.id = res.data
      }
    })
  })
  try {
    await Promise.all(promises)
    ElMessage.success('草稿已保存')
    loadIndicators(currentRow.value.examGroupId, currentRow.value.orgId)
  } catch (e) {
    ElMessage.error('部分指标保存失败')
    reloadIndicatorsPreservePending(currentRow.value.examGroupId, currentRow.value.orgId)
  }
}

function handleSubmitApproval() {
  const ids = indicatorList.value.filter(item => item.id).map(item => item.id)
  if (ids.length === 0) {
    ElMessage.warning('没有可提交的指标')
    return
  }

  // 权重合计校验（跨大类，所有有效指标）
  const validItems = indicatorList.value.filter(item => item.id || item.content)
  const totalAnnual = Math.round(validItems.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0) * 100) / 100
  const totalMonthly = Math.round(validItems.reduce((sum, item) => sum + (Number(item.weightMonthly) || 0), 0) * 100) / 100

  if (Math.abs(totalAnnual - 100) >= 0.01) {
    ElMessage.warning('年度权重合计必须等于100%才可提交')
    return
  }
  if (Math.abs(totalMonthly - 100) >= 0.01) {
    ElMessage.warning('月度权重合计必须等于100%才可提交')
    return
  }

  ElMessageBox.confirm('确认提交审批？', '提示').then(() => {
    submitIndicatorForApproval(ids).then(() => {
      ElMessage.success('提交成功')
      editDialogVisible.value = false
      loadData()
    })
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.indicator-set {
  padding: 16px;
}

/* 紫色状态标签 */
:deep(.tag-purple) {
  --el-tag-bg-color: #f9f0ff;
  --el-tag-border-color: #d3adf7;
  --el-tag-text-color: #722ed1;
  background-color: #f9f0ff;
  border-color: #d3adf7;
  color: #722ed1;
}

/* 审批流程步骤条 */
.approval-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #fafafa;
  border-radius: 6px;
}

.step-item {
  display: flex;
  align-items: center;
  .step-text {
    display: inline-block;
    padding: 4px 14px;
    border-radius: 4px;
    font-size: 13px;
    white-space: nowrap;
  }
  .step-arrow {
    margin: 0 6px;
    color: #d9d9d9;
    font-size: 16px;
  }
  &.step-done .step-text {
    background: #f6ffed;
    color: #52c41a;
  }
  &.step-current .step-text {
    background: #e6f4ff;
    color: #2d5aa0;
    font-weight: 600;
  }
  &:not(.step-done):not(.step-current) .step-text {
    background: #f5f5f5;
    color: #999;
  }
}

/* 大类卡片区 */
.category-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.category-card {
  width: calc(25% - 9px);
  border: 2px solid #e8e8e8;
  border-radius: 6px;
  padding: 12px 14px;
  cursor: pointer;
  background: #fff;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-sizing: border-box;

  &:hover {
    border-color: #2d5aa0;
    background: #f0f5ff;
  }
  &.active {
    border-color: #2d5aa0;
    background: #e6f4ff;
  }

  .card-top {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .card-emoji {
    font-size: 16px;
    line-height: 1;
  }
  .card-name {
    font-size: 13px;
    font-weight: 600;
    color: #333;
  }
  .card-weight {
    font-size: 12px;
    color: #2d5aa0;
    font-weight: 600;
    margin-left: auto;
  }
  .card-stat {
    font-size: 11px;
    color: #999;
  }
}

/* 小类列表区 */
.subcategory-section {
  margin-bottom: 14px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.subcategory-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.subcategory-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: #f0f5ff;
  }
  &.active {
    background: #f0f5ff;
    border-color: #91caff;
  }

  .sub-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    overflow: hidden;
  }
  .sub-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #e6f4ff;
    color: #2d5aa0;
    font-size: 11px;
    font-weight: 600;
    flex-shrink: 0;
  }
  .sub-name {
    font-size: 13px;
    font-weight: 500;
    color: #333;
    flex-shrink: 0;
  }
  .sub-desc {
    font-size: 11px;
    color: #888;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-left: 8px;
  }
  .sub-right {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
  }
}

.add-subcategory {
  padding: 8px 14px;
  color: #2d5aa0;
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;

  &:hover {
    background: #f0f5ff;
  }
}

/* 考核内容区 */
.content-section {
  margin-bottom: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-toolbar {
  display: flex;
  gap: 8px;
}

.view-link {
  color: #2d5aa0;
  cursor: pointer;
  font-size: 13px;
  &:hover {
    text-decoration: underline;
  }
}

/* 考核目标编辑区 */
.exam-target-editor {
  display: flex;
  flex-direction: column;
  gap: 2px;

  :deep(.el-radio-group) {
    flex-wrap: nowrap;
  }
  :deep(.el-radio) {
    margin-right: 8px;
  }
}

/* Dialog 标题 */
.dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* 权重汇总 */
.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;

  .section-title {
    margin-bottom: 0;
  }
}

.weight-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.weight-label {
  color: #666;
}

.weight-value {
  font-weight: 600;
  &.weight-ok {
    color: #52c41a;
  }
  &.weight-warn {
    color: #ff4d4f;
  }
}

.weight-divider {
  color: #ddd;
}

/* 全屏模式下的适配 */
:deep(.el-dialog.is-fullscreen) {
  .category-cards {
    flex-wrap: nowrap;
    overflow-x: auto;
  }
  .category-card {
    min-width: 160px;
    width: auto;
    flex-shrink: 0;
  }
  .subcategory-section {
    margin-bottom: 10px;
  }
}
</style>
