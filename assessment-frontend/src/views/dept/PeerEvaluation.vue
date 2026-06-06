<template>
  <div class="peer-evaluation">
    <!-- 顶部：考核组选择 -->
    <div class="page-header">
      <div class="header-row">
        <h2>部门他评打分</h2>
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
      <el-alert
        v-if="groupStatus === '已发布'"
        title="当前考核组已发布，评分数据为只读状态"
        type="warning"
        show-icon
        :closable="false"
        style="margin-top: 12px"
      />
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
                  >下载</el-link>
                  <span v-else class="info-value">-</span>
                </div>
              </div>
            </div>

            <div class="card-lower">
              <div class="peer-score-panel">
                <div class="input-label">
                  <span class="required-mark">*</span> 他评得分
                </div>
                <div class="input-hint">按实际得分打分，不按百分制打分</div>
                <el-input-number
                  v-model="row.peerScore"
                  :min="0"
                  :precision="2"
                  :step="0.1"
                  :controls="false"
                  size="large"
                  class="peer-score-input"
                  :disabled="isReadonly"
                  placeholder="请输入得分"
                />
              </div>
              <div class="peer-comment-panel">
                <div class="input-label">他评说明</div>
                <el-input
                  v-model="row.scoreComment"
                  type="textarea"
                  :maxlength="200"
                  show-word-limit
                  :rows="4"
                  resize="none"
                  placeholder="请输入他评说明"
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
  getPeerEvalTargets,
  getPeerEvalByDept,
  savePeerEval,
  submitPeerEval
} from '@/api/peerEval'
import type { PeerEvalTargetDept, PeerEvalSaveData } from '@/api/peerEval'
import { useUserStore } from '@/stores/user'
import { useDataScope } from '@/composables/useDataScope'

const userStore = useUserStore()
const { loadScopedExamGroups } = useDataScope()

// 考核组选项
const examGroupOptions = ref<{ label: string; value: number; status?: string }[]>([])
const selectedExamGroupId = ref<number | undefined>(undefined)
const groupStatus = ref('')

// 左侧部门列表
const targetDepts = ref<PeerEvalTargetDept[]>([])
const selectedTargetOrgId = ref<number | undefined>(undefined)
const navLoading = ref(false)

// 右侧表格
const indicatorList = ref<any[]>([])
const tableLoading = ref(false)
const submitLoading = ref(false)

// 只读模式（考核组已发布）
const isReadonly = computed(() => groupStatus.value === '已发布')

function getCurrentEvaluatorOrgId(): number | undefined {
  if (userStore.dataScope === 'ORG' && userStore.scopeId) {
    return userStore.scopeId
  }
  return userStore.scopeId || userStore.userInfo?.orgId
}

// 加载考核组列表
async function loadExamGroupOptions() {
  const groups = await loadScopedExamGroups('PERFORMANCE')
  examGroupOptions.value = groups.map((group: any) => ({
    label: group.groupName,
    value: group.id,
    status: group.status
  }))
  // 自动选中第一个
  if (examGroupOptions.value.length > 0) {
    selectedExamGroupId.value = examGroupOptions.value[0].value
    groupStatus.value = examGroupOptions.value[0].status || ''
    loadTargetDepts()
  }
}

// 考核组变更
function handleExamGroupChange(val: number) {
  const selected = examGroupOptions.value.find(item => item.value === val)
  groupStatus.value = selected?.status || ''
  selectedTargetOrgId.value = undefined
  indicatorList.value = []
  loadTargetDepts()
}

// 加载部门列表
function loadTargetDepts() {
  const evaluatorOrgId = getCurrentEvaluatorOrgId()
  if (!evaluatorOrgId || !selectedExamGroupId.value) return

  navLoading.value = true
  getPeerEvalTargets(selectedExamGroupId.value, evaluatorOrgId)
    .then((res: any) => {
      targetDepts.value = res.data || []
      // 如果后端返回 groupStatus
      if (res.data && res.data.length > 0 && res.data[0].groupStatus) {
        groupStatus.value = res.data[0].groupStatus
      }
      // 默认选中第一个部门
      if (targetDepts.value.length > 0) {
        handleSelectDept(targetDepts.value[0])
      }
    })
    .finally(() => {
      navLoading.value = false
    })
}

// 选中部门
function handleSelectDept(dept: PeerEvalTargetDept) {
  selectedTargetOrgId.value = dept.targetOrgId
  loadDeptIndicators(dept.targetOrgId)
}

// 加载指标数据
function loadDeptIndicators(targetOrgId: number) {
  const evaluatorOrgId = getCurrentEvaluatorOrgId()
  if (!evaluatorOrgId || !selectedExamGroupId.value) return

  tableLoading.value = true
  getPeerEvalByDept(selectedExamGroupId.value, evaluatorOrgId, targetOrgId)
    .then((res: any) => {
      indicatorList.value = (res.data || []).map((item: any) => ({ ...item }))
    })
    .finally(() => {
      tableLoading.value = false
    })
}

// 完成按钮
async function handleComplete() {
  const evaluatorOrgId = getCurrentEvaluatorOrgId()
  if (!evaluatorOrgId || !selectedExamGroupId.value || !selectedTargetOrgId.value) return

  const unscoredRows = indicatorList.value.filter(
    (row: any) => row.peerScore === null || row.peerScore === undefined
  )
  if (unscoredRows.length > 0) {
    ElMessage.warning(`还有 ${unscoredRows.length} 项指标未填写他评得分，请全部填写后再提交`)
    return
  }

  const scoredRows = indicatorList.value

  try {
    await ElMessageBox.confirm('提交后将无法修改，确认完成？', '确认提交', { type: 'warning' })
  } catch {
    return
  }

  submitLoading.value = true
  try {
    // 1. 保存所有已填分的行
    const savePromises = scoredRows.map((row: any) => {
      const data: PeerEvalSaveData = {
        id: row.peerEvalId || undefined,
        examGroupId: selectedExamGroupId.value!,
        evaluatorOrgId,
        targetOrgId: selectedTargetOrgId.value!,
        indicatorId: row.indicatorId,
        peerScore: row.peerScore,
        scoreComment: row.scoreComment
      }
      return savePeerEval(data)
    })
    await Promise.all(savePromises)

    // 2. 提交
    await submitPeerEval(selectedExamGroupId.value, evaluatorOrgId, selectedTargetOrgId.value)
    ElMessage.success('提交成功')

    // 3. 刷新左侧部门状态
    loadTargetDepts()
  } catch {
    ElMessage.error('提交失败，请重试')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadExamGroupOptions()
})
</script>

<style scoped lang="scss">
.peer-evaluation {
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
  margin-bottom: 2px;
  font-weight: 600;

  .required-mark {
    color: var(--el-color-danger, #f56c6c);
    margin-right: 2px;
  }
}

.input-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-bottom: 6px;
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
