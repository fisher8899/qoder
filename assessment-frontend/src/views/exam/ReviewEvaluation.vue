<template>
  <div class="review-evaluation">
    <div class="page-header">
      <h2>复核评估 v1</h2>
      <p class="subtitle">复核修正各部门月度考核打分</p>
    </div>

    <!-- 搜索区 -->
    <SearchForm
      :fields="searchFields"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="tableData"
      stripe
      border
      style="width: 100%"
      class="review-table"
    >
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="orgName" label="考核部门" min-width="120" />
      <el-table-column prop="categoryName" label="指标大类" min-width="120" />
      <el-table-column prop="subCategory" label="指标小类" min-width="120" />
      <el-table-column prop="content" label="考核内容" min-width="200" show-overflow-tooltip />
      <el-table-column prop="weightMonthly" label="权重(月度)(%)" width="120" align="center">
        <template #default="{ row }">
          {{ row.weightMonthly }}
        </template>
      </el-table-column>
      <el-table-column prop="deptScore" label="部门打分" width="100" align="center">
        <template #default="{ row }">
          {{ row.deptScore !== null && row.deptScore !== undefined ? row.deptScore : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="adminScore" label="管理员打分" width="140" align="center">
        <template #default="{ row }">
          <el-input-number
            v-model="row.adminScore"
            :min="0"
            :max="100"
            :precision="2"
            :controls="false"
            style="width: 100px"
            @change="() => calcFinalScore(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="finalScore" label="最终得分" width="100" align="center">
        <template #default="{ row }">
          <span :class="getScoreClass(row.finalScore)">
            {{ row.finalScore !== null && row.finalScore !== undefined ? row.finalScore : '—' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="scoreComment" label="打分说明" min-width="180">
        <template #default="{ row }">
          <el-input
            v-model="row.scoreComment"
            placeholder="请输入打分说明"
            maxlength="500"
            show-word-limit
          />
        </template>
      </el-table-column>
    </el-table>

    <!-- 底部操作区 -->
    <div class="bottom-actions">
      <el-button type="primary" @click="handleBatchSave">
        批量保存
      </el-button>
      <el-button type="success" @click="handleSubmit">
        提交复核
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import { getExamGroupList } from '@/api/examGroup'
import { getExamGroupMembers } from '@/api/examGroup'
import { indicatorCategoryApi } from '@/api/admin'
import { getReviewList, batchSaveReviewScore, submitReview } from '@/api/review'
import type { ReviewItem, ReviewScoreSaveData } from '@/api/review'

const loading = ref(false)
const tableData = ref<ReviewItem[]>([])

const queryParams = reactive({
  examGroupId: undefined as number | undefined,
  orgId: undefined as number | undefined,
  categoryId: undefined as number | undefined
})

const examGroupOptions = ref<any[]>([])
const orgOptions = ref<any[]>([])
const categoryOptions = ref<any[]>([])

const searchFields = ref<SearchField[]>([
  {
    prop: 'examGroupId',
    label: '考核组名称',
    type: 'select',
    placeholder: '请选择考核组',
    options: []
  },
  {
    prop: 'orgId',
    label: '考核部门',
    type: 'select',
    placeholder: '请选择考核部门',
    options: []
  },
  {
    prop: 'categoryId',
    label: '指标大类',
    type: 'select',
    placeholder: '请选择指标大类',
    options: []
  }
])

function loadExamGroups() {
  getExamGroupList({ current: 1, size: 1000, examType: 'MONTHLY' }).then((res: any) => {
    examGroupOptions.value = (res.data?.records || []).map((g: any) => ({
      label: g.groupName,
      value: g.id
    }))
    searchFields.value[0].options = examGroupOptions.value
  })
}

function loadCategories() {
  indicatorCategoryApi.all().then((res: any) => {
    categoryOptions.value = (res.data || []).map((c: any) => ({
      label: c.categoryName,
      value: c.id
    }))
    searchFields.value[2].options = categoryOptions.value
  })
}

function loadOrgsByExamGroup(examGroupId: number) {
  getExamGroupMembers(examGroupId).then((res: any) => {
    orgOptions.value = (res.data || []).map((m: any) => ({
      label: m.orgName,
      value: m.orgId
    }))
    searchFields.value[1].options = orgOptions.value
  })
}

watch(() => queryParams.examGroupId, (val) => {
  if (val) {
    loadOrgsByExamGroup(val)
  } else {
    orgOptions.value = []
    searchFields.value[1].options = []
  }
})

function loadData() {
  if (!queryParams.examGroupId) {
    tableData.value = []
    return
  }
  loading.value = true
  getReviewList({ ...queryParams })
    .then((res: any) => {
      tableData.value = (res.data || []).map((item: any) => ({
        ...item,
        adminScore: item.adminScore ?? null,
        scoreComment: item.scoreComment || ''
      }))
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch(data: Record<string, any>) {
  queryParams.examGroupId = data.examGroupId
  queryParams.orgId = data.orgId
  queryParams.categoryId = data.categoryId
  loadData()
}

function handleReset() {
  queryParams.examGroupId = undefined
  queryParams.orgId = undefined
  queryParams.categoryId = undefined
  searchFields.value[1].options = []
  tableData.value = []
  loadData()
}

function calcFinalScore(row: ReviewItem) {
  const adminScore = row.adminScore
  const deptScore = row.deptScore

  if (adminScore == null && deptScore == null) {
    row.finalScore = null
    return
  }

  let base = adminScore
  if (base == null) {
    base = deptScore
  } else if (deptScore != null && deptScore > base) {
    base = deptScore
  }

  row.finalScore = base != null ? base : null
}

function getScoreClass(score: number | null) {
  if (score == null) return ''
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-medium'
  return 'score-low'
}

function handleBatchSave() {
  if (!queryParams.examGroupId) {
    ElMessage.warning('请先选择考核组')
    return
  }
  if (tableData.value.length === 0) {
    ElMessage.warning('没有可保存的数据')
    return
  }

  const items: ReviewScoreSaveData[] = tableData.value.map(row => ({
    id: row.id,
    examGroupId: row.examGroupId,
    orgId: row.orgId,
    indicatorId: row.indicatorId,
    adminScore: row.adminScore ?? undefined,
    scoreComment: row.scoreComment || undefined
  }))

  batchSaveReviewScore({
    examGroupId: queryParams.examGroupId,
    items
  }).then(() => {
    ElMessage.success('批量保存成功')
    loadData()
  })
}

function handleSubmit() {
  if (!queryParams.examGroupId) {
    ElMessage.warning('请先选择考核组')
    return
  }
  ElMessageBox.confirm('确认提交复核？提交后将标记复核完成并计算最终得分。', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    submitReview(queryParams.examGroupId!).then(() => {
      ElMessage.success('提交复核成功')
      loadData()
    })
  })
}

onMounted(() => {
  loadExamGroups()
  loadCategories()
})
</script>

<style scoped lang="scss">
.review-evaluation {
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

.review-table {
  margin-bottom: 16px;
}

.bottom-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 16px;
  background: var(--card-bg);
  border-radius: 4px;
}

.score-high {
  color: #67c23a;
  font-weight: bold;
}

.score-medium {
  color: #e6a23c;
  font-weight: bold;
}

.score-low {
  color: #f56c6c;
  font-weight: bold;
}
</style>
