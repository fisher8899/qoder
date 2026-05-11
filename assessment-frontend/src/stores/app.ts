import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeType = 'default' | 'baidu' | 'feishu' | 'tech' | 'state'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const currentTheme = ref<ThemeType>('default')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setTheme(theme: ThemeType) {
    currentTheme.value = theme
    document.documentElement.setAttribute('data-theme', theme)
  }

  return { sidebarCollapsed, currentTheme, toggleSidebar, setTheme }
})
