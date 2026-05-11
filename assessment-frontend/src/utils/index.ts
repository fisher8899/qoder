/**
 * 通用工具函数
 */

// 格式化日期
export function formatDate(date: string | Date, fmt = 'yyyy-MM-dd'): string {
  if (!date) return ''
  const d = typeof date === 'string' ? new Date(date) : date
  const o: Record<string, number> = {
    'M+': d.getMonth() + 1,
    'd+': d.getDate(),
    'h+': d.getHours(),
    'm+': d.getMinutes(),
    's+': d.getSeconds(),
    'q+': Math.floor((d.getMonth() + 3) / 3),
    'S': d.getMilliseconds()
  }
  if (/(y+)/.test(fmt)) {
    fmt = fmt.replace(RegExp.$1, (d.getFullYear() + '').substr(4 - RegExp.$1.length))
  }
  for (const k in o) {
    if (new RegExp('(' + k + ')').test(fmt)) {
      fmt = fmt.replace(RegExp.$1, RegExp.$1.length === 1 ? (o[k] + '') : (('00' + o[k]).substr(('' + o[k]).length)))
    }
  }
  return fmt
}

// 深拷贝
export function deepClone<T>(obj: T): T {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj.getTime()) as unknown as T
  if (obj instanceof Array) return obj.map(item => deepClone(item)) as unknown as T
  if (typeof obj === 'object') {
    const cloned = {} as T
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        ;(cloned as any)[key] = deepClone((obj as any)[key])
      }
    }
    return cloned
  }
  return obj
}

// 防抖
export function debounce<T extends (...args: any[]) => any>(fn: T, delay = 300): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  return function (this: any, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

// 节流
export function throttle<T extends (...args: any[]) => any>(fn: T, delay = 300): (...args: Parameters<T>) => void {
  let last = 0
  return function (this: any, ...args: Parameters<T>) {
    const now = Date.now()
    if (now - last >= delay) {
      last = now
      fn.apply(this, args)
    }
  }
}
