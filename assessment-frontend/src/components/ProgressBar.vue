<template>
  <div class="progress-bar-wrapper">
    <span v-if="label" class="progress-label">{{ label }}</span>
    <el-progress
      :percentage="percentage"
      :status="progressStatus"
      :color="customColors"
      :stroke-width="16"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  percentage: number
  label?: string
  status?: 'success' | 'warning' | 'exception'
}

const props = withDefaults(defineProps<Props>(), {
  percentage: 0
})

const progressStatus = computed(() => {
  if (props.status) return props.status
  if (props.percentage >= 100) return 'success'
  return undefined
})

const customColors = [
  { color: '#f56c6c', percentage: 30 },
  { color: '#e6a23c', percentage: 70 },
  { color: '#67c23a', percentage: 100 }
]
</script>

<style scoped lang="scss">
.progress-bar-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}
.progress-label {
  font-size: 13px;
  color: var(--text-regular);
  white-space: nowrap;
  min-width: 80px;
}
:deep(.el-progress) {
  flex: 1;
}
</style>
