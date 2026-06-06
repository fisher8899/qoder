<template>
  <div class="leader-eval-score">
    <!-- 顶部：考核组选择 -->
    <div class="page-header">
      <div class="header-row">
        <h2>评估打分</h2>
        <el-select
          v-model="selectedExamGroupId"
          placeholder="请选择考核组"
          style="width: 280px"
          @change="handleExamGroupChange"
        >
          <el-option
            v-for="item in examGroupOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
    </div>

    <!-- 主体：左右布局 -->
    <div class="main-container">
      <!-- 左侧部门导航 -->
      <div class="left-nav">
        <div class="nav-title">目标部门列表</div>
        <div
          v-for="dept in targetDepts"
          :key="dept.targetOrgId"
          class="nav-item"
          :class="{ active: selectedTargetOrgId === dept.targetOrgId }"
          @click="handleSelectDept(dept)"
        >
          <span class="dept-name">{{ dept.targetOrgName }}</span>
          <el-tag
            :type="dept.status === 'COMPLETED' ? 'success' : 'warning'"
            size="small"
          >
            {{ dept.status === 'COMPLETED' ? '已完成' : '待打分' }}
          </el-tag>
        </div>
        <el-empty v-if="!targetDepts.length && !navLoading" description="暂无部门" :image-size="60" />
        <div v-if="navLoading" class="nav-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
      </div>

      <!-- 右侧评分表单 -->
      <div class="right-content">
        <div
          class="indicator-card-list"
          v-loading="tableLoading"
        >
          <div
            v-for="(row, index) in indicatorList"
            :key="row.indicatorId || index"
            class="indicator-card"
          >
            <div class="card-upper">
              <div class="assessment-section">
                <div class="section-title">
                  <span class="serial-no">{{ index + 1 }}</span>
                  <span>考核内容</span>
                </div>
                <div class="content-title">{{ row.content || '-' }}</div>
                <div class="info-grid">
                  <div class="info-item wide">
                    <span class="info-label">指标/目标</span>
                    <span class="info-value">{{ row.targetDesc || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">权重</span>
                    <span class="info-value">年度{{ row.weightAnnual ?? '-' }} / 月度{{ row.weightMonthly ?? '-' }}</span>
                  </div>
                  <div class="info-item wide">
                    <span class="info-label">考核标准</span>
                    <span class="info-value prewrap">{{ row.evaluationStandard || '-' }}</span>
                  </div>
                </div>
              </div>

              <div class="self-section">
                <div class="section-title">自评信息</div>
                <div class="self-score-box">
                  <span class="score-label">自评得分</span>
                  <strong>{{ row.selfScore ?? '-' }}</strong>
                </div>
                <div class="self-completion">
                  <span class="info-label">完成情况</span>
                  <span class="info-value prewrap">{{ row.actualCompletion || '-' }}</span>
                </div>
                <div class="attachment-row">
                  <span class="info-label">附件</span>
                  <el-link
                    v-if="row.attachmentUrl"
                    type="primary"
                    :href="row.attachmentUrl"
                    target="_blank"
                    :underline="false"
                  >{{ row.attachmentName || '下载' }}</el-link>
                  <span v-else class="info-value">-</span>
                </div>
              </div>
            </div>

            <div class="card-lower">
              <div class="peer-score-panel">
                <div class="input-label">评估得分</div>
                <el-input-number
                  v-model="row.leaderScore"
                  :min="0"
                  :precision="2"
                  :step="0.1"
                  :controls="false"
                  size="large"
                  class="peer-score-input"
                  :disabled="isReadonly"
                />
              </div>
              <div class="peer-comment-panel">
                <div class="input-label">评估说明</div>
                <el-input
                  v-model="row.scoreComment"
                  type="textarea"
                  :maxlength="200"
                  show-word-limit
                  :rows="4"
                  resize="none"
                  placeholder="请输入评估说明"
                  :disabled="isReadonly"
                />
              </div>
            </div>
          </div>

          <el-empty
            v-if="!indicatorList.length && !tableLoading"
            description="请选择目标部门或暂无考核内容"
            :image-size="96"
          />
        </div>

        <!-- 底部操作区 -->
        <div class="footer-actions">
          <el-button
            type="primary"
            :disabled="isReadonly || !selectedTargetOrgId"
            :loading="submitLoading"
            @click="handleComplete"
          >
            完成
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  getLeaderEvalTasks,
  getLeaderEvalIndicators,
  saveLeaderEval,
  submitLeaderEval
} from '@/api/leaderEval'
import type { LeaderEvalTask, LeaderEvalIndicator } from '@/api/leaderEval'

// 考核组选项
const examGroupOptions = ref<{ label: string; value: number; status?: string }[]>([])
const selectedExamGroupId = ref<number | undefined>(undefined)

// 左侧部门列表
interface DeptItem {
  targetOrgId: number
  targetOrgName: string
  status: string
  indicatorCount: number
}
const targetDepts = ref<DeptItem[]>([])
const selectedTargetOrgId = ref<number | undefined>(undefined)
const navLoading = ref(false)

// 右侧表格
const allIndicators = ref<LeaderEvalIndicator[]>([])
const indicatorList = ref<LeaderEvalIndicator[]>([])
const tableLoading = ref(false)
const submitLoading = ref(false)

// 只读模式（全部已提交）
const isReadonly = computed(() => {
  if (!selectedTargetOrgId.value) return false
  const dept = targetDepts.value.find(d => d.targetOrgId === selectedTargetOrgId.value)
  return dept?.status === 'COMPLETED'
})

// 加载考核组列表
async function loadExamGroups() {
  try {
    const res = await getLeaderEvalTasks()
    const tasks: LeaderEvalTask[] = res.data || []
    examGroupOptions.value = tasks.map(t => ({
      label: t.groupName,
      value: t.examGroupId,
      status: t.status
    }))
    if (examGroupOptions.value.length > 0) {
      selectedExamGroupId.value = examGroupOptions.value[0].value
      loadAllIndicators()
    }
  } catch {
    ElMessage.error('加载考核组失败')
  }
}

// 考核组变更
function handleExamGroupChange() {
  selectedTargetOrgId.value = undefined
  indicatorList.value = []
  allIndicators.value = []
  targetDepts.value = []
  loadAllIndicators()
}

// 加载该考核组下所有指标，并按部门分组
async function loadAllIndicators() {
  if (!selectedExamGroupId.value) return
  navLoading.value = true
  tableLoading.value = true
  try {
    const res = await getLeaderEvalIndicators(selectedExamGroupId.value)
    allIndicators.value = (res.data || []) as LeaderEvalIndicator[]

    // 按部门分组
    const deptMap = new Map<number, DeptItem>()
    for (const ind of allIndicators.value) {
      if (!deptMap.has(ind.targetOrgId)) {
        deptMap.set(ind.targetOrgId, {
          targetOrgId: ind.targetOrgId,
          targetOrgName: ind.targetOrgName,
          status: 'COMPLETED',
          indicatorCount: 0
        })
      }
      const dept = deptMap.get(ind.targetOrgId)!
      dept.indicatorCount++
      if (ind.status !== 'SUBMITTED') {
        dept.status = 'PENDING'
      }
    }
    targetDepts.value = Array.from(deptMap.values())

    // 默认选中第一个部门
    if (targetDepts.value.length > 0) {
      handleSelectDept(targetDepts.value[0])
    }
  } catch {
    ElMessage.error('加载指标失败')
  } finally {
    navLoading.value = false
    tableLoading.value = false
  }
}

// 选中部门
function handleSelectDept(dept: DeptItem) {
  selectedTargetOrgId.value = dept.targetOrgId
  // 过滤出该部门的指标
  indicatorList.value = allIndicators.value
    .filter(ind => ind.targetOrgId === dept.targetOrgId)
    .map(item => ({ ...item }))
}

// 完成按钮
async function handleComplete() {
  if (!selectedExamGroupId.value || !selectedTargetOrgId.value) return

  const scoredRows = indicatorList.value.filter(
    row => row.leaderScore !== null && row.leaderScore !== undefined
  )
  if (scoredRows.length === 0) {
    ElMessage.warning('请至少填写一项评估得分')
    return
  }

  try {
    await ElMessageBox.confirm('提交后将无法修改，确认完成？', '确认提交', { type: 'warning' })
  } catch {
    return
  }

  submitLoading.value = true
  try {
    // 1. 保存所有已填分的行
    const savePromises = scoredRows.map(row => {
      return saveLeaderEval({
        examGroupId: selectedExamGroupId.value!,
        targetOrgId: selectedTargetOrgId.value!,
        indicatorId: row.indicatorId,
        leaderScore: row.leaderScore!,
        scoreComment: row.scoreComment
      })
    })
    await Promise.all(savePromises)

    // 2. 提交
    await submitLeaderEval(selectedExamGroupId.value, selectedTargetOrgId.value)
    ElMessage.success('提交成功')

    // 3. 刷新数据
    loadAllIndicators()
  } catch {
    ElMessage.error('提交失败，请重试')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadExamGroups()
})
</script>

<style scoped lang="scss">
.leader-eval-score {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 16px;
  .header-row {
    display: flex;
    align-items: center;
    gap: 16px;
    h2 {
      margin: 0;
      font-size: 20px;
      white-space: nowrap;
    }
  }
}

.main-container {
  display: flex;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 4px;
  overflow: hidden;
}

.left-nav {
  width: 240px;
  min-width: 240px;
  border-right: 1px solid var(--el-border-color-light, #e4e7ed);
  overflow-y: auto;
  background: var(--el-bg-color-page, #f5f7fa);

  .nav-title {
    padding: 12px 16px;
    font-weight: 600;
    font-size: 14px;
    color: var(--el-text-color-primary);
    border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  }

  .nav-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    cursor: pointer;
    transition: background-color 0.2s;
    border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);

    &:hover {
      background-color: var(--el-fill-color-light, #f0f2f5);
    }

    &.active {
      background-color: var(--el-color-primary-light-9, #ecf5ff);
      border-left: 3px solid var(--el-color-primary, #409eff);
      padding-left: 13px;
    }

    .dept-name {
      font-size: 14px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      flex: 1;
      margin-right: 8px;
    }
  }

  .nav-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 24px 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.right-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: auto;
  min-width: 0;
}

.indicator-card-list {
  flex: 1;
  min-height: 280px;
  max-height: calc(100vh - 232px);
  overflow-y: auto;
  padding-right: 4px;
}

.indicator-card {
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
  background: var(--el-bg-color, #fff);
  box-shadow: 0 1px 3px rgba(31, 45, 61, 0.04);
  overflow: hidden;

  & + .indicator-card {
    margin-top: 14px;
  }
}

.card-upper {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  padding: 16px;
  background: linear-gradient(180deg, #fafcff 0%, #fff 100%);
}

.assessment-section,
.self-section {
  min-width: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.serial-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  font-size: 13px;
  font-weight: 700;
}

.content-title {
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.info-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 10px 14px;
}

.info-item,
.self-completion,
.attachment-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.info-item.wide {
  grid-column: span 2;
}

.info-label,
.input-label,
.score-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.info-value {
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
}

.prewrap {
  white-space: pre-wrap;
}

.self-section {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 12px;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-lighter, #ebeef5);

  .section-title {
    grid-column: span 2;
    margin-bottom: 0;
  }
}

.self-score-box {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  min-height: 72px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light, #fafafa);

  strong {
    color: var(--el-color-success, #67c23a);
    font-size: 26px;
    line-height: 1.1;
  }
}

.self-completion {
  min-height: 72px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light, #fafafa);
}

.attachment-row {
  grid-column: span 2;
  flex-direction: row;
  align-items: center;
  min-height: 24px;
}

.card-lower {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  padding: 14px 16px 16px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  background: var(--el-fill-color-blank, #fff);
}

.peer-score-panel,
.peer-comment-panel {
  min-width: 0;
}

.input-label {
  margin-bottom: 6px;
  font-weight: 600;
}

.peer-score-input {
  width: 100%;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .card-upper,
  .card-lower {
    grid-template-columns: 1fr;
  }

  .self-section {
    padding-left: 0;
    padding-top: 14px;
    border-left: none;
    border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  }
}

@media (max-width: 860px) {
  .main-container {
    flex-direction: column;
  }

  .left-nav {
    width: 100%;
    min-width: 0;
    max-height: 220px;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  }

  .indicator-card-list {
    max-height: none;
  }

  .info-grid,
  .self-section {
    grid-template-columns: 1fr;
  }

  .info-item.wide,
  .self-section .section-title,
  .attachment-row {
    grid-column: auto;
  }
}
</style>
