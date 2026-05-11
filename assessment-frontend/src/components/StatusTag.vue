<template>
  <el-tag
    :type="tagType"
    :class="customClass"
    size="small"
    effect="plain"
  >
    {{ displayText }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface StatusMapItem {
  text: string
  type?: 'success' | 'warning' | 'danger' | 'info' | 'primary'
  class?: string
}

interface Props {
  status: string | number
  statusMap: Record<string | number, StatusMapItem>
}

const props = defineProps<Props>()

const config = computed(() => {
  return props.statusMap[props.status] || { text: String(props.status), type: 'info' }
})

const displayText = computed(() => config.value.text)
const tagType = computed(() => config.value.type || 'info')
const customClass = computed(() => config.value.class || '')
</script>

<style scoped>
</style>
