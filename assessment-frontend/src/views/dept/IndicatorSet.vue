<template>
  <div class="indicator-set">
    <!-- 搜索区 -->
    <SearchForm
      :fields="searchFields"
      v-model="searchFormModel"
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
        <el-button
          link
          type="success"
          @click="handleListExport(row)"
        >导出</el-button>
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
        <div style="display: flex; align-items: flex-start; justify-content: space-between; width: 100%; gap: 12px;">
          <div style="display: flex; align-items: flex-start; gap: 8px; flex: 1; min-width: 0; flex-wrap: wrap;">
            <span class="dialog-title">业绩指标设定</span>
            <el-select
              v-if="!isViewMode"
              v-model="currentRow.examGroupId"
              placeholder="选择考核组"
              size="small"
              filterable
              style="width: 200px"
              @change="handleExamGroupChange"
            >
              <el-option
                v-for="group in examGroupList"
                :key="group.id"
                :label="group.groupName"
                :value="group.id"
              />
            </el-select>
            <span v-else class="dialog-group-name">{{ currentRow?.examGroupName || '-' }}</span>
            <span v-if="showRejectReason" class="reject-reason-inline">
              退回说明：{{ currentRejectReason }}
            </span>
          </div>
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
            <div v-if="!isViewMode" class="sub-right">
              <el-button link type="primary" size="small" @click.stop="editSubCategory(sub)">编辑</el-button>
              <el-button link type="danger" size="small" @click.stop="deleteSubCategory(sub)">删除</el-button>
            </div>
          </div>
          <div v-if="!isViewMode" class="add-subcategory" @click="addSubCategory">+ 新增小类</div>
        </div>
      </div>

      <!-- 考核内容表格区 -->
      <div class="content-section">
        <div class="section-header">
          <span class="section-title">考核内容 — {{ activeSubCategory }}</span>
          <div v-if="!isViewMode" class="section-toolbar">
            <el-button type="primary" size="small" @click="addContent">+ 新增考核内容</el-button>
            <el-button size="small" @click="handleBatchImport">批量导入</el-button>
          </div>
        </div>
        <el-table :data="sortedFilteredIndicators" border size="small" :max-height="tableMaxHeight">
          <el-table-column label="序号" width="70">
            <template #default="{ row }">
              <el-input
                v-if="row.isEditing"
                v-model="row.sortCode"
                placeholder="序号"
                size="small"
                style="width: 60px"
              />
              <span v-else>{{ row.sortCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="考核内容" min-width="180">
            <template #default="{ row }">
              <div v-if="row.isEditing" class="input-with-clear">
                <el-input v-model="row.content" placeholder="考核内容" />
                <el-icon v-if="row.content" class="clear-icon" @click="row.content = ''"><CircleClose /></el-icon>
              </div>
              <span v-else>{{ row.content }}</span>
            </template>
          </el-table-column>
          <el-table-column label="指标/目标" min-width="150">
            <template #default="{ row }">
              <div v-if="row.isEditing" class="input-with-clear">
                <el-input v-model="row.targetDesc" placeholder="指标/目标" />
                <el-icon v-if="row.targetDesc" class="clear-icon" @click="row.targetDesc = ''"><CircleClose /></el-icon>
              </div>
              <span v-else>{{ row.targetDesc }}</span>
            </template>
          </el-table-column>
          <el-table-column label="权重(年度)" width="100">
            <template #default="{ row }">
              <div v-if="row.isEditing" style="display: flex; align-items: center; gap: 2px;">
                <el-input
                  v-model="row.weightAnnual"
                  placeholder="数值"
                  style="width: 65px"
                  @input="(val: string) => { row.weightAnnual = normalizePositiveDecimalInput(val) }"
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
                  @input="(val: string) => { row.weightMonthly = normalizePositiveDecimalInput(val) }"
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
                <el-tooltip
                  :content="row.evaluationStandard"
                  placement="top"
                  :disabled="!row.evaluationStandard"
                  popper-class="indicator-standard-tooltip"
                >
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
                      v-for="org in getSelectableOrgList(row)"
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
          <el-table-column v-if="!isViewMode" label="操作" width="120" fixed="right">
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
        <div class="total-weight-summary">
          <span class="total-weight-title">全部权重合计</span>
          <span class="total-weight-item">
            <span class="total-weight-label">权重(年度)</span>
            <span class="total-weight-value">{{ formatWeightPercent(allIndicatorsAnnualTotal) }}</span>
          </span>
          <span class="total-weight-item">
            <span class="total-weight-label">权重(月度)</span>
            <span class="total-weight-value">{{ formatWeightPercent(allIndicatorsMonthlyTotal) }}</span>
          </span>
        </div>
      </div>

      <template #footer>
        <template v-if="isViewMode">
          <el-button @click="editDialogVisible = false">返回</el-button>
          <el-button type="success" @click="handleDialogExport">导出</el-button>
        </template>
        <template v-else>
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button @click="handleSaveDraft">保存草稿</el-button>
          <el-button type="info" @click="handlePreview">提交预览</el-button>
          <el-button type="primary" @click="handleSubmitApproval">提交审批</el-button>
          <el-button type="success" @click="handleDialogExport">导出</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 提交预览弹框 -->
    <el-dialog
      v-model="previewDialogVisible"
      title="业绩指标设定预览"
      fullscreen
      destroy-on-close
    >
      <el-table :data="previewData" border size="small" max-height="540" :span-method="previewSpanMethod">
        <el-table-column prop="categoryName" label="指标类别" width="120" align="center" />
        <el-table-column prop="subCategory" label="指标小类" width="120" align="center" />
        <el-table-column prop="content" label="考核内容" min-width="180" />
        <el-table-column prop="targetDesc" label="指标/目标" min-width="150" />
        <el-table-column prop="weightAnnual" label="权重(年度)" width="100" align="center">
          <template #default="{ row }">{{ row.weightAnnual }}%</template>
        </el-table-column>
        <el-table-column prop="weightMonthly" label="权重(月度)" width="100" align="center">
          <template #default="{ row }">{{ row.weightMonthly }}%</template>
        </el-table-column>
        <el-table-column prop="evaluationStandard" label="考核标准" min-width="200" align="center">
          <template #default="{ row }">
            <el-tooltip
              :content="row.evaluationStandard"
              placement="top"
              :disabled="!row.evaluationStandard"
              effect="light"
              popper-class="preview-tooltip"
            >
              <span class="preview-text">{{ row.evaluationStandard }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="考核部门/分管领导" width="180">
          <template #default="{ row }">
            <span v-if="row.examTargetType === 'LEADER' && row.leaderNames?.length">
              🏛️ {{ row.leaderNames.join('、') }}
            </span>
            <span v-else>{{ row.orgNames?.join('、') || row.orgName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="120" />
      </el-table>
      <template #footer>
        <el-button @click="previewDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleExportExcel">导出Excel</el-button>
      </template>
    </el-dialog>

    <!-- 小类维护弹框 -->
    <el-dialog
      v-model="subcategoryDialogVisible"
      :title="subcategoryDialogMode === 'add' ? '新增小类' : '编辑小类'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="小类名称" required>
          <el-input v-model="subcategoryForm.name" placeholder="请输入小类名称" />
        </el-form-item>
        <el-form-item label="考核标准">
          <el-input
            v-model="subcategoryForm.evaluationStandard"
            type="textarea"
            :rows="5"
            placeholder="默认取当前大类考核标准，可修改"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subcategoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSubCategoryDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen, Aim, CircleClose } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import {
  getIndicatorList,
  getIndicatorSubCategories,
  createIndicatorSubCategory,
  updateIndicatorSubCategory,
  deleteIndicatorSubCategory,
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
  examGroupId: '',
  approvalStatus: '',
  startDate: '',
  endDate: ''
})

const searchFormModel = reactive<Record<string, any>>({
  examGroupId: '',
  approvalStatus: '',
  orgName: '',
  startDate: '',
  endDate: ''
})

const searchFields = ref<SearchField[]>([
  { prop: 'examGroupId', label: '考核组名称', type: 'select', placeholder: '请选择考核组', options: [] },
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
])

const columns: TableColumn[] = [
  { prop: 'examGroupName', label: '考核组名称', minWidth: 180 },
  { prop: 'approvalStatus', label: '考核目标设定状态', width: 160 },
  { prop: 'startDate', label: '考核开始日期', width: 120 },
  { prop: 'endDate', label: '考核结束日期', width: 120 },
  { prop: 'operation', label: '操作', width: 160 }
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
  if (queryParams.examGroupId) {
    list = list.filter((item: any) => String(item.examGroupId) === String(queryParams.examGroupId))
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
        unitId: t.unitId,
        examGroupName: t.examGroupName,
        examType: t.examType,
        examCategory: t.examCategory,
        startDate: t.startDate,
        endDate: t.endDate,
        approvalStatus: t.approvalStatus,
        orgName: t.orgName
      }))
      initExamGroupSearch(tasks)
      filterData()
    })
    .finally(() => {
      loading.value = false
    })
}

function initExamGroupSearch(tasks: ExamGroupTaskVO[]) {
  examGroupList.value = tasks.map((t: ExamGroupTaskVO) => ({
    id: t.examGroupId,
    groupName: t.examGroupName,
    examType: t.examType,
    orgId: t.orgId,
    unitId: t.unitId,
    orgName: t.orgName,
    status: t.status,
    examCategory: t.examCategory,
    startDate: t.startDate
  }))

  const examGroupOptions = tasks.map((t: ExamGroupTaskVO) => ({
    label: t.examGroupName,
    value: String(t.examGroupId)
  }))

  searchFields.value = searchFields.value.map(field => {
    if (field.prop === 'examGroupId') {
      return {
        ...field,
        options: examGroupOptions
      }
    }
    return field
  })

  const currentYear = new Date().getFullYear()
  const defaultTask = tasks.find((t: ExamGroupTaskVO) =>
    t.examCategory === 'INDICATOR_SET' &&
    t.status === 'IN_PROGRESS' &&
    t.startDate?.startsWith(String(currentYear))
  ) || tasks.find((t: ExamGroupTaskVO) =>
    t.examCategory === 'INDICATOR_SET' &&
    t.status === 'IN_PROGRESS'
  ) || tasks[0]

  if (defaultTask) {
    const defaultExamGroupId = String(defaultTask.examGroupId)
    queryParams.examGroupId = defaultExamGroupId
    searchFormModel.examGroupId = defaultExamGroupId
  } else {
    queryParams.examGroupId = ''
    searchFormModel.examGroupId = ''
  }
}

function getDefaultExamGroupId() {
  const currentYear = new Date().getFullYear()
  const defaultGroup = examGroupList.value.find((g: any) =>
    g.examCategory === 'INDICATOR_SET' &&
    g.status === 'IN_PROGRESS' &&
    g.startDate?.startsWith(String(currentYear))
  ) || examGroupList.value.find((g: any) =>
    g.examCategory === 'INDICATOR_SET' &&
    g.status === 'IN_PROGRESS'
  ) || examGroupList.value[0]

  return defaultGroup ? String(defaultGroup.id) : ''
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.examGroupId = data.examGroupId || ''
  queryParams.approvalStatus = data.approvalStatus || ''
  queryParams.startDate = data.startDate || ''
  queryParams.endDate = data.endDate || ''
  filterData()
}

function handleReset() {
  queryParams.current = 1
  const defaultExamGroupId = getDefaultExamGroupId()
  queryParams.examGroupId = defaultExamGroupId
  queryParams.approvalStatus = ''
  queryParams.startDate = ''
  queryParams.endDate = ''
  searchFormModel.examGroupId = defaultExamGroupId
  searchFormModel.approvalStatus = ''
  searchFormModel.orgName = ''
  searchFormModel.startDate = ''
  searchFormModel.endDate = ''
  filterData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
}

// =========== 编辑弹框逻辑 ===========
const editDialogVisible = ref(false)
const isFullscreen = ref(true)
const dialogMode = ref<'edit' | 'view'>('edit')
const isViewMode = computed(() => dialogMode.value === 'view')
const currentRow = ref<any>(null)
const indicatorList = ref<any[]>([])
const currentRejectReason = computed(() => {
  if (currentRow.value?.approvalStatus !== 'REJECTED') return ''
  return currentRow.value?.rejectReason || indicatorList.value.find(item => item.rejectReason)?.rejectReason || ''
})
const showRejectReason = computed(() => currentRow.value?.approvalStatus === 'REJECTED' && !!currentRejectReason.value)
const activeCategory = ref('')
const activeSubCategory = ref('')
const examGroupList = ref<any[]>([])  // 考核组列表

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

type DynamicCategory = {
  id: number
  name: string
  sortCode: number
  evaluationStandard: string
}

type SubCategoryMeta = {
  id?: number
  name: string
  evaluationStandard: string
  categoryId?: number
  categoryName?: string
  sortCode?: number
  isNew?: boolean
  isDirty?: boolean
}

// 大类卡片配置 - 动态加载
const dynamicCategories = ref<DynamicCategory[]>([])

// 组织列表与分管领导列表
const orgList = ref<any[]>([])
const leaderList = ref<any[]>([])

// 自定义小类（用户新增的）— 按大类隔离。仅前端暂存，考核标准会复制到新增考核内容。
const customSubCategories = ref<Record<string, SubCategoryMeta[]>>({})
const subcategoryDialogVisible = ref(false)
const subcategoryDialogMode = ref<'add' | 'edit'>('add')
const editingSubCategoryName = ref('')
const subcategoryForm = reactive<SubCategoryMeta>({
  name: '',
  evaluationStandard: ''
})

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
    for (const sub of (customSubCategories.value[def.name] || [])) {
      const normalized = normalizeSubCategoryMeta(sub)
      if (normalized.name) subs.add(normalized.name)
    }
    const weightSum = Math.round(items.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0) * 100) / 100
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
function getCurrentUnitId() {
  return currentRow.value?.unitId || userStore.userInfo?.unitId
}

async function loadCategories() {
  const orgType = userStore.userInfo?.orgType
  const unitId = getCurrentUnitId()
  try {
    const res: any = await indicatorCategoryApi.all(orgType || undefined, unitId || undefined)
    dynamicCategories.value = (res.data || []).map((item: any) => ({
      id: item.id,
      name: item.categoryName,
      sortCode: item.sortCode,
      evaluationStandard: item.evaluationStandard || ''
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
    const res: any = await organizationApi.all(getCurrentUnitId() || undefined)
    orgList.value = res.data || []
  } catch (e) {
    console.error('加载组织列表失败', e)
  }
}

// 加载分管领导列表
async function loadLeaderList() {
  try {
    const res: any = await leaderApi.getAll(getCurrentUnitId() || undefined)
    leaderList.value = res.data || []
  } catch (e) {
    console.error('加载分管领导列表失败', e)
  }
}

// 加载考核组列表（当前部门所属的所有考核组）
async function loadExamGroupList() {
  try {
    const res: any = await getMyExamGroupTasks('INDICATOR_SET')
    const tasks: ExamGroupTaskVO[] = res.data || []
    examGroupList.value = tasks.map((t: ExamGroupTaskVO) => ({
      id: t.examGroupId,
      groupName: t.examGroupName,
      examType: t.examType,
      orgId: t.orgId,
      unitId: t.unitId,
      orgName: t.orgName
    }))
  } catch (e) {
    console.error('加载考核组列表失败', e)
  }
}

// 切换考核组时重新加载指标数据
function handleExamGroupChange(newExamGroupId: number) {
  if (!currentRow.value) return
  const orgId = currentRow.value.orgId
  currentRow.value.examGroupId = newExamGroupId
  // 从考核组列表中找到对应名称
  const group = examGroupList.value.find((g: any) => g.id === newExamGroupId)
  if (group) {
    currentRow.value.examGroupName = group.groupName
    currentRow.value.unitId = group.unitId
  }
  Promise.all([
    loadCategories(),
    loadOrgList(),
    loadLeaderList()
  ])
  loadIndicators(newExamGroupId, orgId)
}

// 当前大类下的小类（纯计算，无副作用）
const currentSubCategories = computed(() => {
  const items = groupedByCategory.value.get(activeCategory.value) || []
  const subMap = new Map<string, { id?: number; name: string; summary: string; evaluationStandard: string }>()

  // 从指标数据中提取小类
  for (const item of items) {
    const sub = item.subCategory || '默认小类'
    if (!subMap.has(sub)) {
      subMap.set(sub, {
        id: item.subCategoryId,
        name: sub,
        summary: item.evaluationStandard || item.content || '',
        evaluationStandard: item.evaluationStandard || ''
      })
    }
  }

  // 包含当前大类下的自定义小类（没有指标数据的）
  for (const customSub of (customSubCategories.value[activeCategory.value] || [])) {
    const normalized = normalizeSubCategoryMeta(customSub)
    if (subMap.has(normalized.name)) {
      const existing = subMap.get(normalized.name)!
      subMap.set(normalized.name, {
        ...existing,
        id: existing.id || normalized.id,
        summary: existing.summary || normalized.evaluationStandard,
        evaluationStandard: existing.evaluationStandard || normalized.evaluationStandard
      })
    } else {
      subMap.set(normalized.name, {
        id: normalized.id,
        name: normalized.name,
        summary: normalized.evaluationStandard,
        evaluationStandard: normalized.evaluationStandard
      })
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
const allIndicatorsAnnualTotal = computed(() => {
  const items = indicatorList.value.filter(item => item.id || item.content)
  return Math.round(items.reduce((sum, item) => sum + (Number(item.weightAnnual) || 0), 0) * 100) / 100
})

const allIndicatorsMonthlyTotal = computed(() => {
  const items = indicatorList.value.filter(item => item.id || item.content)
  return Math.round(items.reduce((sum, item) => sum + (Number(item.weightMonthly) || 0), 0) * 100) / 100
})

function formatWeightPercent(value: number) {
  return `${value}%`
}

const filteredIndicators = computed(() => {
  return indicatorList.value.filter(item => {
    const cat = item.categoryName || activeCategory.value
    const sub = item.subCategory || '默认小类'
    return cat === activeCategory.value && sub === activeSubCategory.value
  })
})

// 按序号升序排列的指标列表
const sortedFilteredIndicators = computed(() => {
  return [...filteredIndicators.value].sort((a, b) => {
    const sortA = Number(a.sortCode) || 999
    const sortB = Number(b.sortCode) || 999
    return sortA - sortB
  })
})

// 当大类切换时重置小类选中
watch(activeCategory, () => {
  const subs = currentSubCategories.value
  activeSubCategory.value = subs.length > 0 ? subs[0].name : ''
})

function handleSet(row: any) {
  dialogMode.value = 'edit'
  currentRow.value = row
  editDialogVisible.value = true
  customSubCategories.value = {}
  // 动态加载大类 + 指标数据 + 组织列表 + 领导列表 + 考核组列表
  loadCategories()
  loadOrgList()
  loadLeaderList()
  loadExamGroupList()
  loadIndicators(row.examGroupId, row.orgId)
}

function handleView(row: any) {
  dialogMode.value = 'view'
  currentRow.value = row
  editDialogVisible.value = true
  customSubCategories.value = {}
  loadCategories()
  loadOrgList()
  loadLeaderList()
  loadExamGroupList()
  loadIndicators(row.examGroupId, row.orgId)
}

function loadIndicators(examGroupId: number, orgId: number) {
  Promise.all([
    getIndicatorList({ examGroupId, orgId, current: 1, size: 1000 }),
    getIndicatorSubCategories({ examGroupId, orgId })
  ]).then(([res, subRes]: any[]) => {
    const records = res.data.records || []
    customSubCategories.value = mapSubCategoryRecords(subRes.data || [])
    if (currentRow.value?.approvalStatus === 'REJECTED') {
      currentRow.value.rejectReason = records.find((item: any) => item.rejectReason)?.rejectReason || ''
    }
    indicatorList.value = records.map((item: any, index: number) => ({
      ...item,
      isEditing: false,
      isNew: false,
      remark: item.remark || '',
      examTargetType: item.examTargetType || 'DEPARTMENT',
      sortCode: item.sortCode || (index + 1),  // 确保有序号，默认用索引+1
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

function mapSubCategoryRecords(subRecords: any[]) {
  const customMap: Record<string, SubCategoryMeta[]> = {}
  for (const item of subRecords) {
    const catName = item.categoryName || ''
    if (!customMap[catName]) customMap[catName] = []
    customMap[catName].push({
      id: item.id,
      name: item.subCategoryName,
      evaluationStandard: item.evaluationStandard || '',
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      sortCode: item.sortCode
    })
  }
  return customMap
}

function mergePendingSubCategories(serverMap: Record<string, SubCategoryMeta[]>) {
  const mergedMap: Record<string, SubCategoryMeta[]> = { ...serverMap }
  for (const [categoryName, localSubs] of Object.entries(customSubCategories.value)) {
    const targetList = mergedMap[categoryName] ? [...mergedMap[categoryName]] : []
    for (const localSub of localSubs) {
      const normalized = normalizeSubCategoryMeta(localSub)
      const index = targetList.findIndex(item => normalizeSubCategoryMeta(item).name === normalized.name)
      if (index >= 0) {
        targetList[index] = {
          ...targetList[index],
          ...normalized
        }
      } else {
        targetList.push(normalized)
      }
    }
    mergedMap[categoryName] = targetList
  }
  return mergedMap
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

function trimText(value: any) {
  return typeof value === 'string' ? value.trim() : value
}

function normalizePositiveDecimalInput(value: any) {
  return String(value ?? '')
    .replace(/[^0-9.]/g, '')
    .replace(/(\..*?)\./g, '$1')
}

function validatePositiveWeight(value: any, label: string) {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue) || numericValue < 0) {
    ElMessage.warning(`${label}不能为负数`)
    return false
  }
  return true
}

function buildIndicatorPayload(row: any, ownerOrgId: number): IndicatorData {
  return {
    id: row.id,
    examGroupId: row.examGroupId,
    orgId: ownerOrgId,
    categoryId: row.categoryId,
    categoryName: trimText(row.categoryName),
    subCategoryId: row.subCategoryId,
    subCategory: trimText(row.subCategory),
    content: trimText(row.content),
    targetDesc: trimText(row.targetDesc),
    weightAnnual: row.weightAnnual,
    weightMonthly: row.weightMonthly,
    evaluationStandard: trimText(row.evaluationStandard),
    sortCode: row.sortCode || 1,
    examTargetType: row.examTargetType || 'DEPARTMENT',
    orgIds: row.orgIds || [],
    orgNames: (row.orgNames || []).map((name: any) => trimText(name)),
    leaderIds: row.examTargetType === 'LEADER' ? (row.leaderIds || []) : [],
    leaderNames: row.examTargetType === 'LEADER' ? (row.leaderNames || []).map((name: any) => trimText(name)) : []
  }
}

function getSelectableOrgList(row: any) {
  return orgList.value
}

function validateExamTarget(row: any, ownerOrgId: number) {
  return true
}

// 刷新指标列表，但保留仍在编辑或新增中的行（避免保存一行后丢失其他未保存行）
function reloadIndicatorsPreservePending(examGroupId: number, orgId: number) {
  const pendingRows = indicatorList.value.filter(item => item.isNew || item.isEditing)
  Promise.all([
    getIndicatorList({ examGroupId, orgId, current: 1, size: 1000 }),
    getIndicatorSubCategories({ examGroupId, orgId })
  ]).then(([res, subRes]: any[]) => {
    customSubCategories.value = mergePendingSubCategories(mapSubCategoryRecords(subRes.data || []))
    indicatorList.value = [
      ...res.data.records.map((item: any, index: number) => ({
        ...item,
        isEditing: false,
        isNew: false,
        remark: item.remark || '',
        examTargetType: item.examTargetType || 'DEPARTMENT',
        sortCode: item.sortCode || (index + 1),  // 确保有序号
        // 多选支持：优先使用后端返回的数组
        orgIds: item.orgIdList?.length ? item.orgIdList : parseIds(item.orgId),
        orgNames: item.orgNameList?.length ? item.orgNameList : parseNames(item.orgName),
        leaderIds: item.leaderIdList?.length ? item.leaderIdList : parseIds(item.leaderId),
        leaderNames: item.leaderNameList?.length ? item.leaderNameList : parseNames(item.leaderName)
      })),
      ...pendingRows
    ]
    const subs = currentSubCategories.value
    if (!subs.some(item => item.name === activeSubCategory.value)) {
      activeSubCategory.value = subs.length > 0 ? subs[0].name : ''
    }
  })
}

function normalizeSubCategoryMeta(value: any): SubCategoryMeta {
  if (typeof value === 'string') {
    return { name: value, evaluationStandard: '' }
  }
  return {
    id: value?.id,
    name: value?.name || '',
    evaluationStandard: value?.evaluationStandard || '',
    categoryId: value?.categoryId,
    categoryName: value?.categoryName,
    sortCode: value?.sortCode,
    isNew: value?.isNew,
    isDirty: value?.isDirty
  }
}

function getActiveCategoryStandard() {
  return dynamicCategories.value.find(category => category.name === activeCategory.value)?.evaluationStandard || ''
}

function getActiveCategoryId() {
  return dynamicCategories.value.find(category => category.name === activeCategory.value)?.id
}

function getCurrentSubCategoryStandard() {
  const sub = currentSubCategories.value.find(item => item.name === activeSubCategory.value)
  return sub?.evaluationStandard || ''
}

function getCurrentSubCategoryId() {
  const sub = currentSubCategories.value.find(item => item.name === activeSubCategory.value)
  return sub?.id
}

function findCustomSubCategoryIndex(categoryName: string, subName: string) {
  const list = customSubCategories.value[categoryName] || []
  return list.findIndex(item => normalizeSubCategoryMeta(item).name === subName)
}

// 新增小类
function addSubCategory() {
  subcategoryDialogMode.value = 'add'
  editingSubCategoryName.value = ''
  ;(subcategoryForm as any).id = undefined
  subcategoryForm.name = ''
  subcategoryForm.evaluationStandard = getActiveCategoryStandard()
  subcategoryDialogVisible.value = true
}

// 编辑小类（改名）
function editSubCategory(sub: { id?: number; name: string; evaluationStandard?: string }) {
  subcategoryDialogMode.value = 'edit'
  editingSubCategoryName.value = sub.name
  ;(subcategoryForm as any).id = sub.id
  subcategoryForm.name = sub.name
  subcategoryForm.evaluationStandard = sub.evaluationStandard || getCurrentSubCategoryStandard()
  subcategoryDialogVisible.value = true
}

async function submitSubCategoryDialog() {
  const name = subcategoryForm.name.trim()
  if (!name) {
    ElMessage.warning('小类名称不能为空')
    return
  }

  const catKey = activeCategory.value
  const oldName = editingSubCategoryName.value
  const duplicated = currentSubCategories.value.some(sub =>
    sub.name === name && !(subcategoryDialogMode.value === 'edit' && sub.name === oldName)
  )
  if (duplicated) {
    ElMessage.warning('该小类已存在')
    return
  }

  if (!customSubCategories.value[catKey]) {
    customSubCategories.value[catKey] = []
  }

  const meta: SubCategoryMeta = {
    id: (subcategoryForm as any).id,
    name,
    evaluationStandard: subcategoryForm.evaluationStandard,
    categoryId: getActiveCategoryId(),
    categoryName: catKey
  }

  const ownerOrgId = currentRow.value?.orgId ||
    (userStore.dataScope === 'ORG' ? userStore.scopeId : userStore.userInfo?.orgId)
  if (!currentRow.value?.examGroupId || !ownerOrgId) {
    ElMessage.warning('当前考核组或归属部门不能为空')
    return
  }

  const payload = {
    id: meta.id,
    examGroupId: currentRow.value.examGroupId,
    orgId: ownerOrgId,
    categoryId: meta.categoryId,
    categoryName: catKey,
    subCategoryName: name,
    evaluationStandard: meta.evaluationStandard,
    sortCode: meta.sortCode || (currentSubCategories.value.length + 1)
  }

  try {
    if (subcategoryDialogMode.value === 'add') {
      const res: any = await createIndicatorSubCategory(payload)
      meta.id = res.data
      meta.sortCode = payload.sortCode
      customSubCategories.value[catKey].push(meta)
      activeSubCategory.value = name
    } else {
      await updateIndicatorSubCategory(payload)
      meta.sortCode = payload.sortCode

      const idx = findCustomSubCategoryIndex(catKey, oldName)
      if (idx >= 0) {
        customSubCategories.value[catKey][idx] = meta
      } else {
        customSubCategories.value[catKey].push(meta)
      }

      if (activeSubCategory.value === oldName) activeSubCategory.value = name
    }

    for (const item of indicatorList.value) {
      if (item.categoryName === catKey && item.subCategory === oldName) {
        item.subCategory = name
        item.subCategoryId = meta.id
        if (!item.evaluationStandard && meta.evaluationStandard) {
          item.evaluationStandard = meta.evaluationStandard
        }
      }
    }

    subcategoryDialogVisible.value = false
    ;(subcategoryForm as any).id = undefined
    ElMessage.success('小类已保存')
  } catch (e) {
    ElMessage.error('小类保存失败')
  }
}

// 删除小类
async function deleteSubCategory(sub: { id?: number; name: string }) {
  const items = indicatorList.value.filter(
    i => (i.categoryName || activeCategory.value) === activeCategory.value && i.subCategory === sub.name
  )
  if (items.length > 0) {
    ElMessage.warning('该小类下还有指标数据，请先删除指标')
    return
  }
  const catSubs = customSubCategories.value[activeCategory.value]
  if (catSubs) {
    const idx = findCustomSubCategoryIndex(activeCategory.value, sub.name)
    if (idx >= 0) {
      try {
        const subId = normalizeSubCategoryMeta(catSubs[idx]).id || sub.id
        if (subId) {
          await deleteIndicatorSubCategory(subId)
        }
        catSubs.splice(idx, 1)
        ElMessage.success('小类已删除')
      } catch (e) {
        ElMessage.error('小类删除失败')
        return
      }
    }
  }
  // 切换选中
  const subs = currentSubCategories.value
  activeSubCategory.value = subs.length > 0 ? subs[0].name : ''
}

// 新增考核内容
function addContent() {
  // 计算当前小类下最大的序号，新记录序号 = 最大序号 + 1
  const currentItems = filteredIndicators.value
  const maxSortCode = currentItems.reduce((max, item) => {
    const sort = Number(item.sortCode) || 0
    return sort > max ? sort : max
  }, 0)

  // 获取正确的 orgId：优先使用 currentRow，其次使用数据范围的 scopeId
  const currentOrgId = currentRow.value?.orgId ||
    (userStore.dataScope === 'ORG' ? userStore.scopeId : userStore.userInfo?.orgId)
  const currentOrgName = currentRow.value?.orgName || userStore.scopeName || userStore.userInfo?.orgName || ''

  indicatorList.value.push({
    id: undefined,
    examGroupId: currentRow.value?.examGroupId,
    orgId: currentOrgId,
    orgName: currentOrgName,
    categoryId: getActiveCategoryId(),
    categoryName: activeCategory.value,
    subCategoryId: getCurrentSubCategoryId(),
    subCategory: activeSubCategory.value,
    content: '',
    targetDesc: '',
    weightAnnual: 0,
    weightMonthly: 0,
    evaluationStandard: getCurrentSubCategoryStandard(),
    remark: '',
    sortCode: maxSortCode + 1,  // 自动生成序号
    isEditing: true,
    isNew: true,
    examTargetType: 'DEPARTMENT',
    // 多选支持：初始化数组
    orgIds: [],
    orgNames: [],
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
  // 初始化序号（如果不存在）
  if (row.sortCode === undefined || row.sortCode === null) {
    row.sortCode = 1
  }
  // 初始化多选数组（如果不存在）
  if (!row.orgIds) row.orgIds = row.orgId ? parseIds(row.orgId) : []
  if (!row.orgNames) row.orgNames = row.orgName ? parseNames(row.orgName) : []
  if (!row.leaderIds) row.leaderIds = row.leaderId ? parseIds(row.leaderId) : []
  if (!row.leaderNames) row.leaderNames = row.leaderName ? parseNames(row.leaderName) : []
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
  if (!trimText(row.content)) {
    ElMessage.warning('请填写考核内容')
    return
  }
  if (!validatePositiveWeight(row.weightAnnual, '年度权重')) {
    return
  }
  if (!validatePositiveWeight(row.weightMonthly, '月度权重')) {
    return
  }

  // orgId 始终使用任务行的 orgId（数据归属），不从多选考核目标列表取
  const ownerOrgId = currentRow.value?.orgId ||
    (userStore.dataScope === 'ORG' ? userStore.scopeId : userStore.userInfo?.orgId)
  if (!ownerOrgId) {
    ElMessage.warning('当前归属部门不能为空')
    return
  }
  if (!validateExamTarget(row, ownerOrgId)) {
    return
  }

  const data = buildIndicatorPayload(row, ownerOrgId)
  const isNewRecord = !!(row.isNew && !row.id)
  const api = isNewRecord ? createIndicator : updateIndicator
  api(data).then((res: any) => {
    Object.assign(row, data)
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
    if (!trimText(row.content)) {
      ElMessage.warning('请先填写所有考核内容')
      return
    }
    if (!validatePositiveWeight(row.weightAnnual, '年度权重')) {
      return
    }
    if (!validatePositiveWeight(row.weightMonthly, '月度权重')) {
      return
    }
  }
  // orgId 始终使用任务行的 orgId（数据归属），不从多选考核目标列表取
  const ownerOrgId = currentRow.value?.orgId ||
    (userStore.dataScope === 'ORG' ? userStore.scopeId : userStore.userInfo?.orgId)
  if (!ownerOrgId) {
    ElMessage.warning('当前归属部门不能为空')
    return
  }
  if (pending.some(row => !validateExamTarget(row, ownerOrgId))) {
    return
  }

  const promises = pending.map(row => {
    const data = buildIndicatorPayload(row, ownerOrgId)
    const isNewRecord = !!(row.isNew && !row.id)
    const api = isNewRecord ? createIndicator : updateIndicator
    return api(data).then((res: any) => {
      Object.assign(row, data)
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

// =========== 提交预览逻辑 ===========
const previewDialogVisible = ref(false)
const previewData = ref<any[]>([])
const previewMergeMap = ref<Map<string, { rowspan: number; colspan: number }>>(new Map())

function buildPreviewRows(items: any[]) {
  const validItems = items.filter(item => item.id || item.content)
  return [...validItems].sort((a, b) => {
    const catA = dynamicCategories.value.find(c => c.name === a.categoryName)
    const catB = dynamicCategories.value.find(c => c.name === b.categoryName)
    const sortA = catA?.sortCode || 999
    const sortB = catB?.sortCode || 999
    if (sortA !== sortB) return sortA - sortB

    if (a.subCategory !== b.subCategory) {
      return (a.subCategory || '').localeCompare(b.subCategory || '')
    }

    return (Number(a.sortCode) || 999) - (Number(b.sortCode) || 999)
  }).map(item => ({
    ...item,
    orgNames: item.orgNames?.length ? item.orgNames : (item.orgNameList?.length ? item.orgNameList : parseNames(item.orgName)),
    leaderNames: item.leaderNames?.length ? item.leaderNames : (item.leaderNameList?.length ? item.leaderNameList : parseNames(item.leaderName))
  }))
}

function handlePreview() {
  previewData.value = buildPreviewRows(indicatorList.value)
  calculateMergeInfo(previewData.value)
  previewDialogVisible.value = true
}

function calculateMergeInfo(data = previewData.value) {
  previewMergeMap.value.clear()
  if (data.length === 0) return

  // 计算大类合并（columnIndex = 0）
  let categoryStart = 0
  for (let i = 1; i <= data.length; i++) {
    if (i === data.length || data[i].categoryName !== data[categoryStart].categoryName) {
      const rowspan = i - categoryStart
      previewMergeMap.value.set(`0-${categoryStart}`, { rowspan, colspan: 1 })
      for (let j = categoryStart + 1; j < i; j++) {
        previewMergeMap.value.set(`0-${j}`, { rowspan: 0, colspan: 0 })
      }
      categoryStart = i
    }
  }

  // 计算小类合并（columnIndex = 1）- 同一大类内相同小类合并
  let subCategoryStart = 0
  for (let i = 1; i <= data.length; i++) {
    const sameCategory = data[i]?.categoryName === data[subCategoryStart]?.categoryName
    const sameSubCategory = data[i]?.subCategory === data[subCategoryStart]?.subCategory
    if (i === data.length || !sameCategory || !sameSubCategory) {
      const rowspan = i - subCategoryStart
      previewMergeMap.value.set(`1-${subCategoryStart}`, { rowspan, colspan: 1 })
      for (let j = subCategoryStart + 1; j < i; j++) {
        previewMergeMap.value.set(`1-${j}`, { rowspan: 0, colspan: 0 })
      }
      subCategoryStart = i
    }
  }

  // 计算考核标准合并（columnIndex = 6）- 同一大类+小类内相同考核标准合并
  let evalStart = 0
  for (let i = 1; i <= data.length; i++) {
    const sameCategory = data[i]?.categoryName === data[evalStart]?.categoryName
    const sameSubCategory = data[i]?.subCategory === data[evalStart]?.subCategory
    const sameEval = data[i]?.evaluationStandard === data[evalStart]?.evaluationStandard
    if (i === data.length || !sameCategory || !sameSubCategory || !sameEval) {
      const rowspan = i - evalStart
      previewMergeMap.value.set(`6-${evalStart}`, { rowspan, colspan: 1 })
      for (let j = evalStart + 1; j < i; j++) {
        previewMergeMap.value.set(`6-${j}`, { rowspan: 0, colspan: 0 })
      }
      evalStart = i
    }
  }
}

// 单元格合并方法
function previewSpanMethod({ column, rowIndex }: { column: any; rowIndex: number }) {
  const columnIndex = column.property === 'categoryName' ? 0 :
                     column.property === 'subCategory' ? 1 :
                     column.property === 'evaluationStandard' ? 6 : -1

  if (columnIndex === -1) return [1, 1]

  const key = `${columnIndex}-${rowIndex}`
  const mergeInfo = previewMergeMap.value.get(key)
  return mergeInfo || [1, 1]
}

function getExportTargetName(examGroupName?: string) {
  const dateText = new Date().toLocaleDateString().replace(/[\\/]/g, '-')
  return `业绩指标设定_${examGroupName || '导出'}_${dateText}.xls`
}

function escapeHtml(value: any) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function getAssessmentTargetText(item: any) {
  return item.examTargetType === 'LEADER'
    ? (item.leaderNames?.join('、') || item.leaderName || '')
    : (item.orgNames?.join('、') || item.orgName || '')
}

function buildExportHtml(rows: any[]) {
  const mergeMap = new Map<string, { rowspan: number; colspan: number }>()
  previewMergeMap.value.forEach((value, key) => mergeMap.set(key, value))

  const renderMergedCell = (columnIndex: number, rowIndex: number, content: any) => {
    const mergeInfo = mergeMap.get(`${columnIndex}-${rowIndex}`) || { rowspan: 1, colspan: 1 }
    if (mergeInfo.rowspan === 0 || mergeInfo.colspan === 0) return ''
    const spanAttrs = `${mergeInfo.rowspan > 1 ? ` rowspan="${mergeInfo.rowspan}"` : ''}${mergeInfo.colspan > 1 ? ` colspan="${mergeInfo.colspan}"` : ''}`
    return `<td${spanAttrs}>${escapeHtml(content || '-')}</td>`
  }

  const bodyRows = rows.map((item, index) => `
    <tr>
      ${renderMergedCell(0, index, item.categoryName)}
      ${renderMergedCell(1, index, item.subCategory)}
      <td>${escapeHtml(item.content || '-')}</td>
      <td>${escapeHtml(item.targetDesc || '-')}</td>
      <td>${escapeHtml(`${item.weightAnnual || 0}%`)}</td>
      <td>${escapeHtml(`${item.weightMonthly || 0}%`)}</td>
      ${renderMergedCell(6, index, item.evaluationStandard)}
      <td>${escapeHtml(getAssessmentTargetText(item) || '-')}</td>
      <td>${escapeHtml(item.remark || '-')}</td>
    </tr>
  `).join('')

  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8" />
  <style>
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #dcdfe6; padding: 8px; font-size: 12px; vertical-align: middle; }
    th { background: #f5f7fa; font-weight: 700; text-align: center; }
    td:nth-child(5), td:nth-child(6) { text-align: center; }
  </style>
</head>
<body>
  <table>
    <thead>
      <tr>
        <th>指标类别</th>
        <th>指标小类</th>
        <th>考核内容</th>
        <th>指标/目标</th>
        <th>权重(年度)</th>
        <th>权重(月度)</th>
        <th>考核标准</th>
        <th>考核部门/分管领导</th>
        <th>备注</th>
      </tr>
    </thead>
    <tbody>${bodyRows}</tbody>
  </table>
</body>
</html>`
}

function downloadExcelHtml(content: string, fileName: string) {
  const blob = new Blob(['\ufeff', content], { type: 'application/vnd.ms-excel;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function exportIndicatorRows(rows: any[], examGroupName?: string) {
  if (rows.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }
  calculateMergeInfo(rows)
  const html = buildExportHtml(rows)
  downloadExcelHtml(html, getExportTargetName(examGroupName))
  ElMessage.success('导出成功')
}

function handleExportExcel() {
  exportIndicatorRows(previewData.value, currentRow.value?.examGroupName)
}

function handleDialogExport() {
  const rows = buildPreviewRows(indicatorList.value)
  exportIndicatorRows(rows, currentRow.value?.examGroupName)
}

async function handleListExport(row: any) {
  try {
    if (dynamicCategories.value.length === 0) {
      await loadCategories()
    }
    const res: any = await getIndicatorList({ examGroupId: row.examGroupId, orgId: row.orgId, current: 1, size: 1000 })
    const rows = buildPreviewRows(res.data?.records || [])
    exportIndicatorRows(rows, row.examGroupName)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.indicator-set {
  padding: 16px;
}

/* 输入框带清除图标 */
.input-with-clear {
  display: flex;
  align-items: center;
  gap: 4px;

  .el-input {
    flex: 1;
  }

  .clear-icon {
    font-size: 14px;
    color: #909399;
    cursor: pointer;
    transition: color 0.2s;

    &:hover {
      color: #f56c6c;
    }
  }
}

/* 预览文本样式 */
.preview-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  color: #2d5aa0;
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

.dialog-group-name {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  color: #606266;
}

.reject-reason-inline {
  flex: 1 1 100%;
  color: #f56c6c;
  font-size: 13px;
  line-height: 1.4;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
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
.total-weight-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 10px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-top: 0;
  background: #fafafa;
  font-size: 14px;
}

.total-weight-title {
  font-weight: 700;
  color: #303133;
}

.total-weight-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.total-weight-label {
  color: #606266;
}

.total-weight-value {
  font-weight: 700;
  color: #303133;
}

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

<!-- 全局样式：预览弹框 tooltip -->
<style lang="scss">
.indicator-standard-tooltip {
  max-width: 520px !important;
  min-width: 320px !important;
  word-break: break-word;
  white-space: normal;
  text-align: left;
}

.preview-tooltip {
  max-width: 800px !important;
  min-width: 400px !important;
  word-break: break-word;
  text-align: left;
}
</style>

