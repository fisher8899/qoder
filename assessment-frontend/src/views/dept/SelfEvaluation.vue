<template>
  <div class="self-evaluation">
    <div class="page-header">
      <h2>月度考核自评 v1</h2>
      <p class="subtitle">完成本部门月度考核自评，提交后不可修改。</p>
    </div>

    <SearchForm
      v-model="searchFormModel"
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <DataTable
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :total="total"
      :current-page="queryParams.current"
      :page-size="queryParams.size"
      :show-pagination="true"
      @page-change="handlePageChange"
    >
      <template #examType="{ row }">
        {{ row.examType === 'MONTHLY' ? '月度考核' : row.examType === 'ANNUAL' ? '年度考核' : row.examType || '-' }}
      </template>
      <template #status="{ row }">
        <StatusTag :status="row.status" :status-map="statusMap" />
      </template>
      <template #progress="{ row }">
        <el-progress :percentage="row.progress" :status="row.progress === 100 ? 'success' : undefined" />
      </template>
      <template #operation="{ row }">
        <el-button link type="primary" @click="handleStart(row)">
          {{ row.status === 'SUBMITTED' ? '查看' : '开始自评' }}
        </el-button>
        <el-button
          v-if="row.status === 'SUBMITTED'"
          link
          type="warning"
          @click="handleWithdraw(row)"
        >
          撤回
        </el-button>
      </template>
    </DataTable>

    <el-dialog
      v-model="dialogVisible"
      :title="currentTask?.status === 'SUBMITTED' ? `查看自评 - ${currentTask?.groupName || ''}` : `月度考核自评 - ${currentTask?.groupName || ''}`"
      fullscreen
      class="self-eval-fullscreen-dialog"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="self-eval-fullscreen-content">
      <div v-if="currentTask" class="dialog-top">
        <div class="dialog-header">
          <span>考核组：{{ currentTask.groupName }}</span>
          <span>部门：{{ currentOrgName }}</span>
          <span v-if="currentTask.startDate && currentTask.endDate">
            考核周期：{{ currentTask.startDate }} 至 {{ currentTask.endDate }}
          </span>
        </div>

        <div class="stat-cards">
          <div class="stat-card">
            <div class="stat-label">指标总数量</div>
            <div class="stat-value">{{ indicatorList.length }}</div>
          </div>
          <div class="stat-card green">
            <div class="stat-label">已填写完成</div>
            <div class="stat-value">{{ totalFilledCount }}</div>
          </div>
          <div class="stat-card orange">
            <div class="stat-label">已打分指标</div>
            <div class="stat-value">{{ scoredCount }}</div>
          </div>
          <div class="stat-card red">
            <div class="stat-label">总得分</div>
            <div class="stat-value">{{ totalSelfResult }}</div>
          </div>
        </div>
        <div class="progress-bar-wrap">
          <el-progress :percentage="indicatorList.length ? Math.round((totalFilledCount / indicatorList.length) * 100) : 0" :stroke-width="10" />
          <span class="progress-text">{{ totalFilledCount }} / {{ indicatorList.length }} 项已完成</span>
        </div>
      </div>

      <el-empty v-if="!dialogLoading && indicatorList.length === 0" description="当前年度本部门暂无审批通过的业绩考核指标" />

      <div v-else class="self-eval-body" v-loading="dialogLoading">
        <!-- 大类卡片区 -->
        <div class="category-cards">
          <div
            v-for="(cat, idx) in categoryCards"
            :key="cat.id || cat.name"
            class="category-card"
            :class="{ active: activeCategory === cat.name }"
            @click="handleCategoryClick(cat.name)"
          >
            <div class="card-top">
              <span class="card-emoji">{{ cat.emoji }}</span>
              <span class="card-name">{{ cat.name }}</span>
              <span v-if="cat.filledCount > 0" class="card-badge">{{ cat.filledCount }}/{{ cat.totalCount }}</span>
            </div>
            <span class="card-stat">{{ cat.subCount }}小类·{{ cat.itemCount }}指标</span>
          </div>
        </div>

        <!-- 小类列表区（只有1个小类时不展示） -->
        <div v-if="showSubCategoryList" class="subcategory-section">
          <div class="section-header-row">
            <div class="section-title">小类列表 — {{ activeCategory }}</div>
          </div>
          <div class="subcategory-list">
            <div
              v-for="(sub, idx) in currentSubCategories"
              :key="sub.name"
              class="subcategory-item"
              :class="{ active: activeSubCategory === sub.name }"
              @click="handleSubCategoryClick(sub.name)"
            >
              <div class="sub-left">
                <span class="sub-index">{{ idx + 1 }}</span>
                <span class="sub-name">{{ sub.name }}</span>
                <span class="sub-desc" :title="sub.evaluationStandard">{{ sub.evaluationStandard || '暂无考核标准' }}</span>
              </div>
              <div class="sub-right">
                <span class="sub-progress" :class="{ complete: sub.filledCount === sub.totalCount }">
                  {{ sub.filledCount }}/{{ sub.totalCount }}
                </span>
              </div>
            </div>
            <div v-if="currentSubCategories.length === 0" class="sub-empty">该大类下暂无小类</div>
          </div>
        </div>

        <!-- 考核内容表格区 -->
        <div v-if="activeSubCategory && currentIndicators.length > 0" class="content-section">
          <div class="section-header-row">
            <div class="section-title">考核内容 — {{ activeSubCategory }}</div>
            <div class="section-toolbar">
              <span class="content-tip">完成情况和自评分均为必填项，附件非必填</span>
            </div>
          </div>
          <el-table :data="currentIndicators" border size="small" max-height="500" row-class-name="self-eval-row">
            <el-table-column type="index" label="序号" width="55" />
            <el-table-column prop="content" label="考核内容" min-width="150" show-overflow-tooltip />
            <el-table-column prop="targetDesc" label="指标/目标" min-width="120" show-overflow-tooltip />
            <el-table-column label="完成情况" min-width="200">
              <template #default="{ row }">
                <el-input
                  v-if="!isReadonly"
                  v-model="row.actualCompletion"
                  type="textarea"
                  :rows="2"
                  resize="none"
                  maxlength="2000"
                  show-word-limit
                  placeholder="请输入实际完成情况（必填）"
                  :class="{ 'input-error': row._completionError }"
                  @blur="handleFieldBlur(row, 'actualCompletion')"
                />
                <div v-else class="multiline-text">{{ row.actualCompletion || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="权重(年/月)" width="100" align="center">
              <template #default="{ row }">{{ formatWeight(row.weightAnnual) }} / {{ formatWeight(row.weightMonthly) }}</template>
            </el-table-column>
            <el-table-column label="考核标准" min-width="200">
              <template #default="{ row }">
                <el-tooltip
                  :content="cleanStd(row.evaluationStandard)"
                  placement="top"
                  :disabled="cleanStd(row.evaluationStandard) === '-' || !isTextOverflow(cleanStd(row.evaluationStandard), 200)"
                  popper-class="std-tooltip-popper"
                  :show-after="300"
                >
                  <div style="max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                    {{ cleanStd(row.evaluationStandard) }}
                  </div>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="自评得分" width="120" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-if="!isReadonly"
                  v-model="row.selfScore"
                  size="small"
                  :min="0"
                  :max="100"
                  :precision="2"
                  :controls="false"
                  placeholder="--"
                  style="width: 100px"
                  :class="{ 'input-error': row._scoreError }"
                  @blur="handleScoreBlur(row)"
                />
                <span v-else>{{ formatScore(row.selfScore) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="自评结果" width="120" align="center">
              <template #default="{ row }">
                <el-input
                  v-if="!isReadonly"
                  v-model="row.selfResult"
                  size="small"
                  style="width: 100px"
                  placeholder="--"
                  @blur="handleResultBlur(row)"
                />
                <span v-else class="result-text">{{ formatResult(row.selfResult) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="附件" min-width="140">
              <template #default="{ row }">
                <div class="attachment-cell">
                  <template v-if="row.attachmentName">
                    <a v-if="getAttachmentHref(row)" :href="getAttachmentHref(row)" target="_blank" class="file-link">{{ row.attachmentName }}</a>
                    <span v-else class="file-name">{{ row.attachmentName }}</span>
                    <el-button v-if="!isReadonly" link type="danger" size="small" @click="handleRemoveAttachment(row)">删除</el-button>
                  </template>
                  <el-upload
                    v-else-if="!isReadonly"
                    :action="uploadAction"
                    :headers="uploadHeaders"
                    :show-file-list="false"
                    :before-upload="beforeUpload"
                    :on-success="(res: any) => handleUploadSuccess(res, row)"
                    :on-error="handleUploadError"
                    accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.wps,.xls,.xlsx,.zip,.rar,.7z"
                  >
                    <el-button link type="primary" size="small">+ 上传</el-button>
                  </el-upload>
                  <span v-else>-</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 未选中小类时的提示 -->
        <div v-else-if="activeCategory" class="select-sub-tip">
          <el-icon><InfoFilled /></el-icon>
          请在左侧选择一个「小类」，查看对应的考核内容
        </div>
      </div>
    </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="!isReadonly" @click="handleSaveDraft">保存草稿</el-button>
        <el-button v-if="!isReadonly" type="info" @click="handlePreview">预览</el-button>
        <el-button v-if="!isReadonly" type="primary" @click="handleSubmit">提交自评</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹框 -->
    <el-dialog v-model="previewVisible" title="自评预览" fullscreen destroy-on-close>
      <div class="preview-header">
        <span>考核组：{{ currentTask?.groupName }}</span>
        <span>部门：{{ currentOrgName }}</span>
        <span v-if="currentTask?.startDate && currentTask?.endDate">周期：{{ currentTask.startDate }} 至 {{ currentTask.endDate }}</span>
      </div>
      <div class="preview-stats">
        <span>指标总数：{{ indicatorList.length }}</span>
        <span>已填写完成情况：{{ totalFilledCount }}</span>
        <span>已填写得分：{{ scoredCount }}</span>
        <span>自评结果合计：{{ totalSelfResult }}</span>
      </div>
      <el-table :data="previewTableData" border size="small" max-height="540" :span-method="previewSpanMethod">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="categoryName" label="指标大类" width="110" />
        <el-table-column prop="subCategory" label="指标小类" width="130" />
        <el-table-column prop="content" label="考核内容" min-width="160" show-overflow-tooltip />
        <el-table-column prop="targetDesc" label="指标/目标" min-width="130" show-overflow-tooltip />
        <el-table-column label="完成情况" min-width="180">
          <template #default="{ row }">
            <div class="multiline-text">{{ row.actualCompletion || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="考核标准" width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              :content="cleanStd(row.evaluationStandard)"
              placement="top"
              :disabled="cleanStd(row.evaluationStandard) === '-' || !isTextOverflow(cleanStd(row.evaluationStandard), 280)"
              popper-class="std-tooltip-popper"
              :show-after="300"
            >
              <div style="max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                {{ cleanStd(row.evaluationStandard) }}
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="权重(年/月)" width="110" align="center">
          <template #default="{ row }">{{ formatWeight(row.weightAnnual) }} / {{ formatWeight(row.weightMonthly) }}</template>
        </el-table-column>
        <el-table-column label="自评得分" width="90" align="center">
          <template #default="{ row }">{{ formatScore(row.selfScore) }}</template>
        </el-table-column>
        <el-table-column label="自评结果" width="100" align="center">
          <template #default="{ row }"><span class="result-text">{{ formatResult(row.selfResult) }}</span></template>
        </el-table-column>
        <el-table-column label="附件" width="100">
          <template #default="{ row }">
            <a v-if="row.attachmentName && getAttachmentHref(row)" :href="getAttachmentHref(row)" target="_blank" class="file-link">查看</a>
            <span v-else-if="row.attachmentName" class="file-name">{{ row.attachmentName }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button v-if="!isReadonly" type="primary" @click="handleSubmitFromPreview">确认提交</el-button>
      </template>
    </el-dialog>

    <div class="upload-hint">支持图片、DOC、DOCX、WPS、XLS、XLSX、PDF、压缩包，单个文件不超过20MB。</div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watchEffect } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import type { TableColumn } from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  deleteSelfEvalAttachment,
  getSelfEvalIndicators,
  getSelfEvalTaskList,
  saveSelfEval,
  submitSelfEval,
  withdrawSelfEval
} from '@/api/selfEval'
import { indicatorCategoryApi } from '@/api/admin'
import type { SelfEvalIndicator, SelfEvalSaveData, SelfEvalTask } from '@/api/selfEval'
import { useDataScope } from '@/composables/useDataScope'

type SelfEvalRow = SelfEvalIndicator & {
  attachmentDownloadUrl?: string
  _completionError?: boolean
  _scoreError?: boolean
}

const { effectiveOrgId, loadScopedExamGroups } = useDataScope()
const userStore = useUserStore()

const loading = ref(false)
const dialogLoading = ref(false)
const dialogVisible = ref(false)
const rawData = ref<SelfEvalTask[]>([])
const tableData = ref<SelfEvalTask[]>([])
const total = ref(0)
const currentTask = ref<SelfEvalTask | null>(null)
const indicatorList = ref<SelfEvalRow[]>([])
const categorySortMap = ref<Map<string | number, number>>(new Map())
const previewVisible = ref(false)

// 大类/小类选中状态
const activeCategory = ref('')
const activeSubCategory = ref('')

// 大类 emoji 映射
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

// 大类卡片数据
const categoryCards = computed(() => {
  const catMap = new Map<string, { name: string; id: number; subs: Set<string>; items: SelfEvalRow[] }>()
  indicatorList.value.forEach(item => {
    const catName = item.categoryName || '未分类'
    if (!catMap.has(catName)) {
      catMap.set(catName, { name: catName, id: item.categoryId || 0, subs: new Set(), items: [] })
    }
    const cat = catMap.get(catName)!
    if (item.subCategory) cat.subs.add(item.subCategory)
    cat.items.push(item)
  })
  // 按 sortCode 排序
  const sorted = Array.from(catMap.values()).sort((a, b) => {
    const sortA = categorySortMap.value.get(a.name) ?? 999
    const sortB = categorySortMap.value.get(b.name) ?? 999
    return sortA - sortB
  })
  return sorted.map(cat => ({
    id: cat.id,
    name: cat.name,
    emoji: categoryEmojiMap[cat.name] || '📋',
    subCount: cat.subs.size,
    itemCount: cat.items.length,
    totalCount: cat.items.length,
    filledCount: cat.items.filter(r => !!r.actualCompletion?.trim()).length
  }))
})

// 当前大类下的小类列表
const currentSubCategories = computed(() => {
  if (!activeCategory.value) return []
  const catItems = indicatorList.value.filter(i => i.categoryName === activeCategory.value)
  const subMap = new Map<string, SelfEvalRow[]>()
  catItems.forEach(item => {
    const sub = item.subCategory || '默认小类'
    if (!subMap.has(sub)) subMap.set(sub, [])
    subMap.get(sub)!.push(item)
  })
  return Array.from(subMap.entries()).map(([name, items]) => ({
    name,
    totalCount: items.length,
    filledCount: items.filter(r => !!r.actualCompletion?.trim()).length,
    evaluationStandard: items.find(i => i.evaluationStandard)?.evaluationStandard || ''
  }))
})

// 是否展示小类列表（只有1个小类时不展示）
const showSubCategoryList = computed(() => currentSubCategories.value.length > 1)

// 考核标准列合并（相同值合并）
// (stdSpanMethod 已移除，改用CSS视觉合并方案，避免列错位)

// 截断文本（用于显示前N个字符）
function truncateText(text: string | undefined, maxLen: number): string {
  if (!text) return '-'
  if (text.length <= maxLen) return text
  return text.slice(0, maxLen) + '...'
}

// 规范化考核标准：去掉引号/反斜杠/多余空格/不可见字符等差异，用于比较和显示
function normalizeStd(text: string | undefined | null): string {
  if (!text) return ''
  return text
    .replace(/["'\\`“”‘’「」『』]/g, '')  // 去掉各种引号和反斜杠
    .replace(/[\u200B-\u200D\uFEFF]/g, '')      // 去掉零宽字符
    .replace(/\s+/g, ' ')                         // 合并连续空白为单个空格
    .trim()
}

// 清理考核标准显示：空值显示'-'
function cleanStd(text: string | undefined | null): string {
  return normalizeStd(text) || '-'
}

// 判断文本是否超出指定宽度（用于决定是否显示 tooltip）
function isTextOverflow(text: string | undefined, maxWidth: number): boolean {
  if (!text) return false
  // 估算：中文字符约 14px，英文字符约 7px
  const estimatedWidth = Array.from(text).reduce((w, ch) => {
    return w + (ch.charCodeAt(0) > 127 ? 14 : 7)
  }, 0)
  return estimatedWidth > maxWidth
}

// 当前小类下的指标列表（相同考核标准合并显示）
const currentIndicators = computed(() => {
  if (!activeCategory.value || !activeSubCategory.value) return []
  const items = indicatorList.value.filter(i =>
    i.categoryName === activeCategory.value &&
    (i.subCategory || '默认小类') === activeSubCategory.value
  )
  if (items.length === 0) return []

  // 先标记每行的合并信息
  const result = items.map(item => ({
    ...item,
    _stdRowspan: 1 as number,
    _stdMergeStart: false as boolean,
    _stdMerged: false as boolean,
    _stdValue: normalizeStd(item.evaluationStandard)
  }))

  // 调试：输出每行考核标准的规范化值，方便排查差异
  if (result.length > 1) {
    const unique = new Set(result.map(r => r._stdValue))
    console.log('[stdMerge] items:', result.length, 'unique std values:', unique.size)
  }

  // 从后向前扫描，合并相同且非空的考核标准行
  let i = result.length - 1
  while (i >= 0) {
    const currentStd = result[i]._stdValue
    if (!currentStd) {
      i--
      continue
    }
    let j = i - 1
    while (j >= 0 && result[j]._stdValue === currentStd) {
      result[j]._stdMerged = true  // 被合并的行：内容不可见
      j--
    }
    const spanCount = i - j
    if (spanCount > 1) {
      result[j + 1]._stdMergeStart = true  // 合并组的第一行
      result[j + 1]._stdRowspan = spanCount
    }
    i = j
  }

  return result
})

// 预览表格数据：按大类排序（按sortCode），大类相同则按小类排序，再按考核内容排序
const previewTableData = computed(() => {
  if (!indicatorList.value.length) return []
  
  // 按大类sortCode排序，然后按小类排序，最后按考核内容排序
  const sorted = [...indicatorList.value].sort((a, b) => {
    // 获取大类排序码
    const catIdA = a.categoryId ?? 0
    const catIdB = b.categoryId ?? 0
    const sortCodeA = catIdA > 0 ? (categorySortMap.value.get(catIdA) ?? 999) : (categorySortMap.value.get(a.categoryName || '') ?? 999)
    const sortCodeB = catIdB > 0 ? (categorySortMap.value.get(catIdB) ?? 999) : (categorySortMap.value.get(b.categoryName || '') ?? 999)
    
    if (sortCodeA !== sortCodeB) return sortCodeA - sortCodeB
    
    // 大类相同，按小类排序
    const subA = a.subCategory || ''
    const subB = b.subCategory || ''
    if (subA !== subB) return subA.localeCompare(subB)
    
    // 小类相同，按考核内容排序
    return (a.content || '').localeCompare(b.content || '')
  })
  
  return sorted
})

// 预览表格行合并方法（按列名定位合并列）
function previewSpanMethod({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }) {
  const data = previewTableData.value
  if (!data.length) return { rowspan: 1, colspan: 1 }
  
  const currentRow = data[rowIndex]
  
  // 规范化考核标准（去掉引号等）
  function normalizeStd(text: string | undefined | null): string {
    if (!text) return ''
    return text.replace(/["'"]/g, '').trim()
  }
  
  // 表格列索引与列名对应关系（按 el-table-column 定义顺序）
  // 0: 序号, 1: 指标大类, 2: 指标小类, 3: 考核内容, 4: 指标/目标,
  // 5: 完成情况, 6: 考核标准, 7: 权重(年/月), 8: 自评得分, 9: 自评结果, 10: 附件
  
  // 【指标大类】列：相同大类名称合并
  if (columnIndex === 1) {
    const catName = currentRow.categoryName || ''
    let catStart = rowIndex
    while (catStart > 0 && (data[catStart - 1].categoryName || '') === catName) {
      catStart--
    }
    let catEnd = rowIndex
    while (catEnd < data.length - 1 && (data[catEnd + 1].categoryName || '') === catName) {
      catEnd++
    }
    const rowspan = catEnd - catStart + 1
    if (rowIndex === catStart) {
      return { rowspan, colspan: 1 }
    }
    return { rowspan: 0, colspan: 1 }
  }
  
  // 【指标小类】列：相同大类+相同小类合并
  if (columnIndex === 2) {
    const catName = currentRow.categoryName || ''
    const subName = currentRow.subCategory || ''
    let subStart = rowIndex
    while (subStart > 0 && 
           (data[subStart - 1].categoryName || '') === catName && 
           (data[subStart - 1].subCategory || '') === subName) {
      subStart--
    }
    let subEnd = rowIndex
    while (subEnd < data.length - 1 && 
           (data[subEnd + 1].categoryName || '') === catName && 
           (data[subEnd + 1].subCategory || '') === subName) {
      subEnd++
    }
    const rowspan = subEnd - subStart + 1
    if (rowIndex === subStart) {
      return { rowspan, colspan: 1 }
    }
    return { rowspan: 0, colspan: 1 }
  }
  
  // 【考核标准】列：相同考核标准合并（去引号比较）
  if (columnIndex === 6) {
    const currentStd = normalizeStd(currentRow.evaluationStandard)
    if (!currentStd) {
      return { rowspan: 1, colspan: 1 }
    }
    let stdStart = rowIndex
    while (stdStart > 0 && normalizeStd(data[stdStart - 1].evaluationStandard) === currentStd) {
      stdStart--
    }
    let stdEnd = rowIndex
    while (stdEnd < data.length - 1 && normalizeStd(data[stdEnd + 1].evaluationStandard) === currentStd) {
      stdEnd++
    }
    const rowspan = stdEnd - stdStart + 1
    if (rowIndex === stdStart) {
      return { rowspan, colspan: 1 }
    }
    return { rowspan: 0, colspan: 1 }
  }
  
  // 其他列不合并（包括：序号、考核内容、指标/目标、完成情况、权重(年/月)、自评得分、自评结果、附件）
  return { rowspan: 1, colspan: 1 }
}

// 大类卡片点击
function handleCategoryClick(catName: string) {
  activeCategory.value = catName
  // 保存到 localStorage，下次打开时恢复
  localStorage.setItem('self_eval_last_category', catName)
  // 自动选中该大类下第一个小类
  const catItems = indicatorList.value.filter(i => i.categoryName === catName)
  if (catItems.length > 0) {
    activeSubCategory.value = catItems[0].subCategory || '默认小类'
  } else {
    activeSubCategory.value = ''
  }
}

// 小类点击
function handleSubCategoryClick(subName: string) {
  activeSubCategory.value = subName
}

const queryParams = reactive({
  current: 1,
  size: 10,
  examGroupId: undefined as number | undefined,
  examType: '',
  status: ''
})

const defaultExamGroupId = ref<number | undefined>(undefined)
const searchFormModel = reactive({
  examGroupId: undefined as number | undefined,
  examType: '',
  status: ''
})

const searchFields: SearchField[] = [
  {
    prop: 'examGroupId',
    label: '考核组名称',
    type: 'select',
    placeholder: '请选择',
    options: []
  },
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
    prop: 'status',
    label: '自评状态',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '全部', value: '' },
      { label: '待提交', value: 'PENDING' },
      { label: '已提交', value: 'SUBMITTED' }
    ]
  }
]

const columns: TableColumn[] = [
  { prop: 'groupName', label: '考核组名称', minWidth: 180 },
  { prop: 'examType', label: '考核类型', width: 100, noRender: true },
  { prop: 'status', label: '月度考核自评状态', width: 140 },
  { prop: 'totalIndicators', label: '总指标数', width: 90 },
  { prop: 'evaluatedCount', label: '已评数', width: 80 },
  { prop: 'progress', label: '完成进度', width: 180 },
  { prop: 'operation', label: '操作', width: 160 }
]

const statusMap = {
  PENDING: { text: '待提交', type: 'warning' as const },
  DRAFT: { text: '草稿', type: 'info' as const },
  SUBMITTED: { text: '已提交', type: 'success' as const }
}

const rowStatusMap = {
  PENDING: { text: '待填写', type: 'info' as const },
  DRAFT: { text: '草稿', type: 'warning' as const },
  SUBMITTED: { text: '已提交', type: 'success' as const }
}

const isReadonly = computed(() => currentTask.value?.status === 'SUBMITTED')
const currentOrgId = computed(() => effectiveOrgId.value)
const currentOrgName = computed(() => userStore.scopeName || userStore.userInfo?.orgName || '-')
const uploadAction = '/api/evaluation/self/upload'
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const sectionColors = [
  { bg: '#f0f5ff' },
  { bg: '#fff7e6' },
  { bg: '#fff1f0' },
  { bg: '#f6ffed' },
  { bg: '#f9f0ff' },
  { bg: '#e6fffb' }
]
const sectionNumbers = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']

const sectionList = computed(() => {
  // 按大类排序码分组
  const catMap = new Map<number, { name: string; items: SelfEvalRow[]; sortCode: number }>()
  indicatorList.value.forEach(item => {
    const catId = item.categoryId ?? 0
    const sortCode = catId > 0
      ? (categorySortMap.value.get(catId) ?? 999)
      : (categorySortMap.value.get(item.categoryName || '') ?? 999)
    const catKey = sortCode * 100000 + catMap.size
    if (!catMap.has(catKey)) {
      catMap.set(catKey, { name: item.categoryName || '未分类', items: [], sortCode })
    }
    catMap.get(catKey)!.items.push(item)
  })
  const sorted = Array.from(catMap.entries()).sort((a, b) => a[0] - b[0])
  return sorted.map(([catKey, catData], catIdx) => {
    const subMap = new Map<string, SelfEvalRow[]>()
    catData.items.forEach(item => {
      const sub = item.subCategory || '未分类'
      if (!subMap.has(sub)) subMap.set(sub, [])
      subMap.get(sub)!.push(item)
    })
    const subs = Array.from(subMap.entries()).map(([subName, subItems]) => {
      const firstStd = subItems.find(i => i.evaluationStandard)?.evaluationStandard
      return {
        name: subName,
        items: subItems,
        totalCount: subItems.length,
        filledCount: subItems.filter(r => !!r.actualCompletion?.trim()).length,
        evaluationStandard: firstStd || '',
        bgColor: sectionColors[catIdx % sectionColors.length].bg
      }
    })
    return {
      name: catData.name,
      number: sectionNumbers[catIdx] || String(catIdx + 1),
      subs,
      totalCount: catData.items.length,
      filledCount: catData.items.filter(r => !!r.actualCompletion?.trim()).length
    }
  })
})

const scoredCount = computed(() =>
  indicatorList.value.filter(row => row.selfScore !== null && row.selfScore !== undefined).length
)

const totalFilledCount = computed(() =>
  indicatorList.value.filter(row => !!row.actualCompletion?.trim()).length
)

const totalSelfResult = computed(() => {
  // 先检查否决项：如果有否决得分>0，总得分为0
  const vetoRows = indicatorList.value.filter(r => r.categoryName === '否决项目')
  const hasVeto = vetoRows.some(r => Number(r.selfScore || 0) > 0)
  if (hasVeto) return '0.00'

  const totalValue = indicatorList.value.reduce((sum, row) => {
    return sum + Number(row.selfResult || 0)
  }, 0)
  return totalValue.toFixed(2)
})


function formatResult(val: number | null) {
  if (val === null || val === undefined) return '-'
  return Number(val).toFixed(2)
}

function formatScore(val: number | null) {
  if (val === null || val === undefined) return '-'
  return Number(val).toFixed(2)
}


function formatWeight(val: number | null) {
  if (val === null || val === undefined) return '-'
  return `${Number(val)}%`
}

// 生成权重比较的key（用于合并判断）
function formatWeightKey(weightAnnual: number | null, weightMonthly: number | null): string {
  return `${weightAnnual ?? ''}_${weightMonthly ?? ''}`
}

function calcResult(row: SelfEvalRow) {
  const score = row.selfScore
  const catName = row.categoryName || ''

  if (score === null || score === undefined || String(score).trim() === '') {
    row.selfResult = null
    return
  }
  const numScore = Number(score)
  if (isNaN(numScore)) {
    row.selfResult = null
    return
  }

  if (catName === '控制指标') {
    // 扣分项目：自评得分的负数
    row.selfResult = Number((-Math.abs(numScore)).toFixed(2))
  } else if (catName === '特殊贡献指标') {
    // 加分项目：自评得分本身
    row.selfResult = Number(numScore.toFixed(2))
  } else if (catName === '否决项目') {
    // 一票否决：得分>0则结果=其他全部得分的相反数，总得分归0
    if (numScore > 0) {
      const otherSum = indicatorList.value
        .filter(r => r.categoryName !== '否决项目')
        .reduce((s, r) => s + Number(r.selfResult || 0), 0)
      row.selfResult = Number((-otherSum).toFixed(2))
    } else {
      row.selfResult = Number('0.00')
    }
  } else {
    // 普通指标：得分 * 月权重 / 100
    const weight = row.weightMonthly
    if (weight !== null && weight !== undefined) {
      row.selfResult = Number(((numScore * Number(weight)) / 100).toFixed(2))
    } else {
      row.selfResult = Number(numScore.toFixed(2))
    }
  }
}

// 自动保存单行数据（失焦触发，500ms 防抖）
let autoSaveTimer: ReturnType<typeof setTimeout> | null = null

async function autoSaveRow(row: SelfEvalRow) {
  // 清除之前的定时器，实现防抖
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
    autoSaveTimer = null
  }

  autoSaveTimer = setTimeout(async () => {
    autoSaveTimer = null

    // 前置校验
    const orgId = currentOrgId.value
    if (!orgId) {
      console.warn('[autoSave] 缺少 orgId，跳过保存. effectiveOrgId=', effectiveOrgId.value, 'dataScope=', userStore.dataScope)
      return
    }
    if (!currentTask.value) {
      console.warn('[autoSave] currentTask 为空，跳过保存')
      return
    }

    // 构建保存数据
    const payload: any = {
      examGroupId: currentTask.value.examGroupId,
      orgId,
      indicatorId: row.indicatorId,
      actualCompletion: row.actualCompletion,
      selfScore: row.selfScore ?? undefined,
      selfResult: row.selfResult ?? undefined,
      attachmentUrl: row.attachmentUrl || undefined,
      attachmentName: row.attachmentName || undefined
    }
    if (row.selfEvalId) {
      payload.id = row.selfEvalId
    }

    console.log('[autoSave] 触发保存:', payload)

    try {
      const res = await saveSelfEval(payload)
      console.log('[autoSave] 保存成功，返回:', res.data)
      if (res.data) {
        row.selfEvalId = res.data
        // 同步更新 indicatorList 源数据
        const srcRow = indicatorList.value.find(r => r.indicatorId === row.indicatorId)
        if (srcRow) srcRow.selfEvalId = res.data
      }
      ElMessage.success('已自动保存')
    } catch (e: any) {
      console.error('[autoSave] 保存失败:', e)
      ElMessage.error('自动保存失败: ' + (e.message || ''))
    }
  }, 500)
}

/** 将表格行（currentIndicators 副本）的用户编辑字段同步回 indicatorList 源数据 */
function syncRowToSource(row: SelfEvalRow) {
  const srcRow = indicatorList.value.find(r => r.indicatorId === row.indicatorId)
  if (srcRow) {
    srcRow.actualCompletion = row.actualCompletion
    srcRow.selfScore = row.selfScore
    srcRow.selfResult = row.selfResult
    srcRow.attachmentUrl = row.attachmentUrl
    srcRow.attachmentName = row.attachmentName
  }
}

// 完成情况失焦自动保存
function handleFieldBlur(row: SelfEvalRow, field: 'actualCompletion') {
  syncRowToSource(row)
  autoSaveRow(row)
}

// 自评得分失焦时自动保存（先计算自评结果）
function handleScoreBlur(row: SelfEvalRow) {
  calcResult(row)
  syncRowToSource(row)
  autoSaveRow(row)
}

// 自评结果失焦时自动保存
function handleResultBlur(row: SelfEvalRow) {
  syncRowToSource(row)
  autoSaveRow(row)
}

// 得分变化时自动保存（保留兼容）
function handleScoreChange(row: SelfEvalRow) {
  handleScoreBlur(row)
}

function filterData() {
  let list = [...rawData.value]
  if (queryParams.examGroupId) {
    list = list.filter(item => item.examGroupId === queryParams.examGroupId)
  }
  if (queryParams.examType) {
    list = list.filter(item => item.examType === queryParams.examType)
  }
  if (queryParams.status) {
    list = list.filter(item => item.status === queryParams.status)
  }
  total.value = list.length
  const start = (queryParams.current - 1) * queryParams.size
  tableData.value = list.slice(start, start + queryParams.size)
}

async function loadExamGroupOptions() {
  const groups = await loadScopedExamGroups('PERFORMANCE')
  const options = groups.map(group => ({
    label: group.groupName,
    value: group.id
  }))
  const target = searchFields.find(field => field.prop === 'examGroupId')
  if (target) {
    target.options = options
    defaultExamGroupId.value = options[0]?.value
    // 默认选中最新月份的考核组（第一个）
    if (options.length > 0 && queryParams.examGroupId === undefined) {
      queryParams.examGroupId = options[0].value
    }
  }
  searchFormModel.examGroupId = queryParams.examGroupId ?? defaultExamGroupId.value
  searchFormModel.examType = queryParams.examType
  searchFormModel.status = queryParams.status
  filterData()
}

function loadData() {
  const orgId = currentOrgId.value
  if (!orgId) {
    ElMessage.warning('无法获取当前用户部门信息')
    return
  }
  loading.value = true
  getSelfEvalTaskList({ orgId })
    .then((res: any) => {
      rawData.value = res.data || []
      filterData()
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch(data: Record<string, any>) {
  queryParams.current = 1
  queryParams.examGroupId = data.examGroupId
  queryParams.examType = data.examType || ''
  queryParams.status = data.status || ''
  searchFormModel.examGroupId = data.examGroupId
  searchFormModel.examType = data.examType || ''
  searchFormModel.status = data.status || ''
  filterData()
}

function handleReset() {
  queryParams.current = 1
  queryParams.examGroupId = defaultExamGroupId.value
  queryParams.examType = ''
  queryParams.status = ''
  searchFormModel.examGroupId = defaultExamGroupId.value
  searchFormModel.examType = ''
  searchFormModel.status = ''
  filterData()
}

function handlePageChange(page: number, size: number) {
  queryParams.current = page
  queryParams.size = size
  filterData()
}

function handleStart(row: SelfEvalTask) {
  currentTask.value = row
  // 打开弹框前重置选中状态
  activeCategory.value = ''
  activeSubCategory.value = ''
  dialogVisible.value = true
  loadIndicators(row.examGroupId)
}

function loadIndicators(examGroupId: number) {
  const orgId = currentOrgId.value
  if (!orgId) {
    ElMessage.warning('无法获取当前用户部门信息')
    return
  }
  dialogLoading.value = true
  Promise.all([
    getSelfEvalIndicators(examGroupId, orgId),
    loadCategorySortMap()
  ]).then(([res]: any[]) => {
      indicatorList.value = (res.data || []).map((item: SelfEvalIndicator) => ({
        ...item
      }))
      // 自动选中大类：优先使用上次选择的，否则使用第一个
      if (indicatorList.value.length > 0) {
        const allCatNames = [...new Set(indicatorList.value.map(i => i.categoryName))]
        const lastCat = localStorage.getItem('self_eval_last_category')
        const targetCat = (lastCat && allCatNames.includes(lastCat)) ? lastCat : allCatNames[0]
        activeCategory.value = targetCat || ''
        const catItems = indicatorList.value.filter(i => i.categoryName === targetCat)
        // 自动选中该大类下第一个小类
        if (catItems.length > 0) {
          activeSubCategory.value = catItems[0].subCategory || '默认小类'
        }
      }
    })
    .catch(() => {
      indicatorList.value = []
    })
    .finally(() => {
      dialogLoading.value = false
    })
}

async function loadCategorySortMap() {
  try {
    const res = await indicatorCategoryApi.all()
    const map = new Map<string | number, number>()
    ;(res.data || []).forEach((cat: any) => {
      if (cat.id != null) map.set(cat.id, cat.sortCode ?? 999)
      if (cat.categoryName) map.set(cat.categoryName, cat.sortCode ?? 999)
    })
    categorySortMap.value = map
  } catch (e) {
    console.error('加载指标大类排序码失败', e)
  }
}

function beforeUpload(file: File) {
  const maxSize = 20 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过20MB')
    return false
  }
  const allowed = ['pdf', 'jpg', 'jpeg', 'png', 'doc', 'docx', 'wps', 'xls', 'xlsx', 'zip', 'rar', '7z']
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowed.includes(ext)) {
    ElMessage.error('不支持的文件类型')
    return false
  }
  return true
}

function getAttachmentHref(row: SelfEvalRow) {
  // 如果有预存的下载URL，直接使用
  if (row.attachmentDownloadUrl) return row.attachmentDownloadUrl
  // 如果有attachmentUrl但没有下载URL，构建下载链接
  if (row.attachmentUrl && row.selfEvalId) {
    return `/api/evaluation/self/download/${row.selfEvalId}`
  }
  return ''
}

function handleUploadSuccess(res: any, row: SelfEvalRow) {
  if (res.code === 200 && res.data) {
    row.attachmentUrl = res.data.url
    row.attachmentName = res.data.name
    row.attachmentDownloadUrl = ''
    ElMessage.success('上传成功')
    // 上传成功后立即保存，持久化附件信息到数据库
    autoSaveRow(row)
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function handleUploadError() {
  ElMessage.error('上传失败')
}

function buildSaveRows() {
  return indicatorList.value.filter(row => {
    return (
      !!row.actualCompletion?.trim()
      || row.selfScore !== null
      || row.selfScore !== undefined
      || !!row.attachmentUrl
      || !!row.attachmentName
      || !!row.selfEvalId
    )
  })
}

async function persistRows(orgId: number) {
  const rows = buildSaveRows()
  if (!rows.length) return
  await Promise.all(rows.map(async (row) => {
    const res = await saveSelfEval({
      id: row.selfEvalId || undefined,
      examGroupId: currentTask.value!.examGroupId,
      orgId,
      indicatorId: row.indicatorId,
      actualCompletion: row.actualCompletion,
      selfScore: row.selfScore ?? undefined,
      selfResult: row.selfResult ?? undefined,
      attachmentUrl: row.attachmentUrl || undefined,
      attachmentName: row.attachmentName || undefined
    })
    if (res.data) {
      row.selfEvalId = res.data
    }
  }))
}

async function handleRemoveAttachment(row: SelfEvalRow) {
  // 先调用后端删除文件
  if (row.selfEvalId) {
    await deleteSelfEvalAttachment(row.selfEvalId)
  }
  // 清空前端的附件字段
  row.attachmentUrl = ''
  row.attachmentName = ''
  row.attachmentDownloadUrl = ''
  // 重新保存，清除数据库中的附件信息
  autoSaveRow(row)
  ElMessage.success('附件已删除')
}

async function handleSaveDraft() {
  const orgId = currentOrgId.value
  if (!orgId || !currentTask.value) return
  try {
    await persistRows(orgId)
    ElMessage.success('草稿保存成功')
    await loadIndicators(currentTask.value.examGroupId)
    loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}

function validateBeforeSubmit() {
  if (!indicatorList.value.length) {
    ElMessage.warning('当前没有可提交的审批通过指标')
    return false
  }
  // 验证所有指标：完成情况必填、自评分必填
  let hasError = false
  const errors: string[] = []
  indicatorList.value.forEach((row, idx) => {
    // 清除之前的错误状态
    row._completionError = false
    row._scoreError = false
    // 检查完成情况
    if (!row.actualCompletion?.trim()) {
      row._completionError = true
      hasError = true
      errors.push(`第${idx + 1}条「${row.content?.slice(0, 20)}...」的完成情况未填写`)
    }
    // 检查自评分
    if (row.selfScore === null || row.selfScore === undefined) {
      row._scoreError = true
      hasError = true
      errors.push(`第${idx + 1}条「${row.content?.slice(0, 20)}...」的自评分未填写`)
    }
  })
  if (hasError) {
    const uniqueErrors = [...new Set(errors)].slice(0, 3)
    ElMessage.warning({
      message: uniqueErrors.join('；') + (errors.length > 3 ? `；等共${errors.length}条未填写完整` : ''),
      duration: 5000
    })
    // 自动滚动到顶部
    const scrollEl = document.querySelector('.self-eval-body')
    if (scrollEl) scrollEl.scrollTop = 0
    // 自动选中第一个有错误的大类/小类
    const firstError = indicatorList.value.find(r => r._completionError || r._scoreError)
    if (firstError) {
      activeCategory.value = firstError.categoryName || ''
      activeSubCategory.value = firstError.subCategory || ''
    }
    return false
  }
  return true
}

function handlePreview() {
  if (!validateBeforeSubmit()) return
  previewVisible.value = true
}

async function handleSubmitFromPreview() {
  const orgId = currentOrgId.value
  if (!orgId || !currentTask.value) return
  try {
    await persistRows(orgId)
    await ElMessageBox.confirm('提交后将无法修改，确认提交？', '确认提交', { type: 'warning' })
    await submitSelfEval(currentTask.value.examGroupId, orgId)
    ElMessage.success('提交成功')
    previewVisible.value = false
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('提交失败')
    }
  }
}

async function handleSubmit() {
  // 提交前先打开预览，让用户确认后再提交
  if (!validateBeforeSubmit()) return
  previewVisible.value = true
}

async function handleWithdraw(row: SelfEvalTask) {
  const orgId = effectiveOrgId.value
  if (!orgId) {
    ElMessage.warning('无法获取当前数据范围')
    return
  }
  try {
    await ElMessageBox.confirm('撤回后可重新编辑自评内容，确认撤回？', '确认撤回', {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  try {
    await withdrawSelfEval(row.examGroupId, orgId)
    ElMessage.success('撤回成功')
    loadData()
  } catch {
    // 错误已由 request 拦截器统一处理，此处无需重复弹窗
  }
}

onMounted(async () => {
  await loadExamGroupOptions()
  loadData()
})
</script>

<style scoped lang="scss">
.self-evaluation {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;

  h2 {
    margin: 0 0 4px 0;
    font-size: 20px;
  }

  .subtitle {
    margin: 0;
    color: var(--text-secondary);
    font-size: 14px;
  }
}

.dialog-top {
  margin-bottom: 16px;
}

.dialog-header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin-bottom: 12px;
  color: var(--text-primary);
  font-weight: 600;
}

/* 统计卡片 */
.stat-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.stat-card {
  flex: 1;
  padding: 8px 18px;
  background: linear-gradient(135deg, #f0f5ff, #e6f4ff);
  border-radius: 8px;
  border: 1px solid #d6e4ff;

  .stat-label { font-size: 12px; color: #888; }
  .stat-value { font-size: 22px; font-weight: 700; color: #2d5aa0; margin-top: 4px; }

  &.green {
    background: linear-gradient(135deg, #f6ffed, #d9f7be);
    border-color: #b7eb8f;
    .stat-value { color: #389e0d; }
  }
  &.orange {
    background: linear-gradient(135deg, #fff7e6, #ffe7ba);
    border-color: #ffd591;
    .stat-value { color: #d46b08; }
  }
  &.red {
    background: linear-gradient(135deg, #fff1f0, #ffccc7);
    border-color: #ffa39e;
    .stat-value { color: #cf1322; }
  }
}

/* 进度条 */
.progress-bar-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;

  .el-progress { flex: 1; }
  .progress-text { font-size: 13px; color: #555; white-space: nowrap; }
}

.self-eval-body {
  max-height: 65vh;
  overflow-y: auto;
  padding-right: 4px;
}

/* 大类卡片区 */
.category-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.category-card {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  min-width: 160px;
  border-radius: 8px;
  border: 2px solid #e8ecf4;
  background: #fafbfc;
  cursor: pointer;
  transition: all 0.2s;

  &:hover { border-color: #c0d4f0; background: #f0f5ff; }
  &.active { border-color: #2d5aa0; background: linear-gradient(135deg, #f0f5ff, #e6f0ff); }

  .card-top {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
  }
  .card-emoji { font-size: 18px; }
  .card-name { font-weight: 600; font-size: 14px; color: #333; }
  .card-badge {
    margin-left: auto;
    padding: 2px 6px;
    border-radius: 10px;
    background: #409eff;
    color: #fff;
    font-size: 11px;
  }
  .card-stat { font-size: 12px; color: #888; }
}

/* 小类列表区 */
.subcategory-section {
  margin-bottom: 20px;
  background: #fafbfc;
  border-radius: 8px;
  padding: 12px;
}

.section-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a3a6b;
}

.subcategory-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.subcategory-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 6px;
  border: 1px solid #e0e6ed;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 200px;

  &:hover { border-color: #409eff; }
  &.active {
    border-color: #409eff;
    background: linear-gradient(135deg, #ecf5ff, #d9edf7);
  }

  .sub-left { display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0; }
  .sub-index {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #409eff;
    color: #fff;
    font-size: 11px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .sub-name { font-weight: 600; font-size: 13px; color: #333; }
  .sub-desc {
    font-size: 11px;
    color: #888;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .sub-right { margin-left: auto; }
  .sub-progress {
    padding: 2px 8px;
    border-radius: 10px;
    background: #f5f5f5;
    font-size: 11px;
    color: #666;
    &.complete { background: #67c23a; color: #fff; }
  }
}

.sub-empty { color: #999; font-size: 13px; padding: 8px 0; }

/* 考核内容表格区 */
.content-section {
  margin-bottom: 20px;
}

.section-toolbar { display: flex; align-items: center; gap: 12px; }

.content-tip {
  font-size: 12px;
  color: #909399;
  padding: 4px 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.select-sub-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px;
  background: #f9f9f9;
  border-radius: 8px;
  color: #909399;
  font-size: 14px;
  text-align: center;
  justify-content: center;
}

/* 输入框错误样式 */
:deep(.input-error .el-textarea__inner) {
  border-color: #f56c6c !important;
  background: #fef0f0;
}

:deep(.input-error.el-input-number) {
  .el-input__wrapper { border-color: #f56c6c !important; box-shadow: 0 0 0 1px #f56c6c inset !important; }
}

/* 预览弹框 */
.preview-header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin-bottom: 12px;
  font-weight: 600;
  color: #333;
}

.preview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

/* 通用样式 */
.multiline-text {
  white-space: pre-wrap;
  line-height: 1.5;
  color: #303133;
}

/* 考核标准列文本截断 */
.std-cell {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;
}

.result-text {
  font-weight: 600;
  color: #1f4f99;
}

.attachment-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.file-link,
.file-name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.file-link {
  color: var(--el-color-primary);
  text-decoration: none;
}

.upload-hint {
  margin-top: 12px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .stat-cards { flex-wrap: wrap; }
  .stat-card { min-width: calc(50% - 8px); }
}

/* 全屏自评弹框样式 */
:deep(.self-eval-fullscreen-dialog) {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  margin: 0;
  max-width: 100%;
  width: 100vw !important;

  .el-dialog__header {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 10;
    background: #fff;
    border-bottom: 1px solid #e8ecf4;
    padding: 12px 20px;
    margin: 0;
  }

  .el-dialog__body {
    padding-top: 70px; /* 给固定 header 留出空间 */
    padding-bottom: 70px; /* 给固定 footer 留出空间 */
    max-height: 100vh;
    overflow-y: auto;
  }

  .el-dialog__footer {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 10;
    background: #fff;
    border-top: 1px solid #e8ecf4;
    padding: 12px 20px;
    margin: 0;
  }
}

.self-eval-fullscreen-content {
  padding: 0 16px 16px;
}

@media (max-width: 768px) {
  .stat-card { min-width: 100%; }
}
</style>

<!-- 非 scoped 样式：用于 Element Plus tooltip 弹出框 + 考核标准列合并 -->
<style>
.std-tooltip-popper {
  max-width: 230px !important;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}
</style>
