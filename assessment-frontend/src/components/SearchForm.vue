<template>
  <el-form :model="formData" inline class="search-form">
    <el-form-item
      v-for="field in fields"
      :key="field.prop"
      :label="field.label"
    >
      <el-input
        v-if="field.type === 'input'"
        v-model="formData[field.prop]"
        :placeholder="field.placeholder || '请输入'"
        clearable
      />
      <el-select
        v-else-if="field.type === 'select'"
        v-model="formData[field.prop]"
        :placeholder="field.placeholder || '请选择'"
        clearable
        style="width: 180px"
      >
        <el-option
          v-for="opt in field.options"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-date-picker
        v-else-if="field.type === 'date'"
        v-model="formData[field.prop]"
        type="date"
        :placeholder="field.placeholder || '选择日期'"
        value-format="YYYY-MM-DD"
      />
      <el-date-picker
        v-else-if="field.type === 'daterange'"
        v-model="formData[field.prop]"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

export interface SearchField {
  prop: string
  label: string
  type: 'input' | 'select' | 'date' | 'daterange'
  placeholder?: string
  options?: { label: string; value: any }[]
}

interface Props {
  fields: SearchField[]
  modelValue?: Record<string, any>
}

const props = withDefaults(defineProps<Props>(), {
  fields: () => [],
  modelValue: () => ({})
})

const emit = defineEmits<{
  (e: 'search', data: Record<string, any>): void
  (e: 'reset'): void
  (e: 'update:modelValue', data: Record<string, any>): void
}>()

const formData = reactive<Record<string, any>>({})

// 初始化字段
watch(() => props.fields, (fields) => {
  fields.forEach(f => {
    if (!(f.prop in formData)) {
      formData[f.prop] = props.modelValue[f.prop] || ''
    }
  })
}, { immediate: true })

function handleSearch() {
  emit('search', { ...formData })
  emit('update:modelValue', { ...formData })
}

function handleReset() {
  props.fields.forEach(f => {
    formData[f.prop] = ''
  })
  emit('reset')
  emit('search', { ...formData })
  emit('update:modelValue', { ...formData })
}
</script>

<style scoped lang="scss">
.search-form {
  padding: 16px;
  background: var(--card-bg);
  border-radius: 4px;
  margin-bottom: 16px;
}
</style>
