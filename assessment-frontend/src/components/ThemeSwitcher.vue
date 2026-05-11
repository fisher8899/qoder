<template>
  <el-dropdown trigger="click" @command="handleThemeChange">
    <el-button circle :icon="Brush" title="切换主题" />
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="theme in themes"
          :key="theme.value"
          :command="theme.value"
          :class="{ active: currentTheme === theme.value }"
        >
          <span class="theme-dot" :style="{ background: theme.color }"></span>
          {{ theme.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Brush } from '@element-plus/icons-vue'
import { useAppStore, type ThemeType } from '@/stores/app'

const appStore = useAppStore()
const currentTheme = computed(() => appStore.currentTheme)

const themes = [
  { label: '默认蓝', value: 'default' as ThemeType, color: '#2d5aa0' },
  { label: '百度蓝', value: 'baidu' as ThemeType, color: '#2932e1' },
  { label: '飞书蓝', value: 'feishu' as ThemeType, color: '#3370ff' },
  { label: '科技青', value: 'tech' as ThemeType, color: '#00d4ff' },
  { label: '央企红', value: 'state' as ThemeType, color: '#c41e3a' }
]

function handleThemeChange(theme: ThemeType) {
  appStore.setTheme(theme)
}
</script>

<style scoped lang="scss">
.theme-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 8px;
  vertical-align: middle;
}
:deep(.el-dropdown-menu__item.active) {
  color: var(--primary-color);
  font-weight: bold;
}
</style>
