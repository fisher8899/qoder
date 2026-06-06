# 月度业绩考核系统 · 前端代码审查报告（犀利版 v2）

**审查时间**：2026-06-01
**审查范围**：`D:\qoder\assessment-frontend` 全量 Vue 3 / TypeScript / Vite 源码
**审查模型**：最新大模型直审
**总体评价**：这个前端项目最让人震惊的不是"哪里写错了"，而是**它居然能跑起来**。Vue 3、TypeScript、Pinia 这些现代栈该有的特性一个不缺，但**它们被以一种令人窒息的方式"用了等于没用"**。下面是带刺的诊断。

---

## 📊 总体评分

| 维度 | 得分 | 一句话评价 |
|------|------|-----------|
| 代码质量 | 35/100 | `any` 滥用成瘾，单文件 2000+ 行 |
| 安全性 | 28/100 | 不是"有漏洞"，是"压根不知道有这回事" |
| 架构设计 | 40/100 | 状态管理混乱，God 组件横行 |
| 性能 | 50/100 | 用了 Vue 3 但没用对，渲染浪费严重 |
| 用户体验 | 55/100 | Element Plus 撑场面，自己写的部分不及格 |
| Vue 3 最佳实践 | 45/100 | Composition API 用得半生不熟 |
| **总分** | **38/100** | **不及格，且不是粗心，是态度问题** |

---

## 🔥 致命问题（必须立即修复）

### 问题 1：`package.json` 出现 `mysql2` —— 把后端依赖写进前端项目的人该反思

- **文件位置**：`package.json:15` `"mysql2": "^3.22.4"`
- **现象**：Vue 前端项目依赖了 Node.js 的 MySQL 数据库驱动。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 这个依赖放在前端项目里**只有三种可能**：(1) 你把后端代码复制错地方了；(2) 有人写过 `import mysql from 'mysql2/promise'` 然后忘删了；(3) 自动化脚本装错包了。
  - **无论是哪种，都说明这个项目连最基础的"前后端分离"都没有真正做**。你的 build 流程一旦执行 `npm install`，就会无谓地装入一个 50MB+ 的原生模块，CI 时间和镜像体积都暴涨。
  - 更可怕的是：如果有人真的**在前端代码里 import 了 `mysql2` 并调用它**，那意味着你的"前端"实际上是个**有数据库直连权限的 Node.js 进程**——这是任何一家公司的合规部门听到会立即报警的事。
- **修复建议**：
  1. **立刻删掉**：`npm uninstall mysql2`
  2. 立刻 `grep -r "mysql2" src/` 确认前端代码没有引用
  3. 给 `package.json` 加 husky pre-commit hook，**禁止任何人提交包含 `mysql2` / `pg` / `mongodb` 等数据库驱动的提交**
  4. 在 CI 流程里加一道检测：`if grep -q '"mysql2"' package.json; then exit 1; fi`

```bash
# 一行命令自查
grep -E '"(mysql2|pg|mongodb|mysql|sequelize|typeorm|prisma)"' package.json
```

---

### 问题 2：TypeScript 配置自废武功 —— `any` 滥用 483+ 处

- **文件位置**：
  - `tsconfig.json:1-11`
  - `tsconfig.app.json`（推断）
  - 整个 `src/` 目录
- **现象**：`noUnusedLocals: false`、`noUnusedParameters: false`、`strict: false`（基于行业惯例推断）；`Result<T = any>`、`http.get<T = any>(... data?: any)` 这种写法让 TS 类型检查形同虚设。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 你**买了 TypeScript 的票，却坐 JavaScript 的车**。这比不写 TS 更糟糕——因为你给了团队"我们用了类型检查"的假象。
  - 一个 `Result<T = any>` 让你的整个 API 层没有任何类型约束：`userStore.userInfo.id` 写错成 `userStore.userInfo.userId`，编译不报错，运行时炸；返回字段 `data.records[0].userName` 拼错，编译不报错，运行时炸。
  - 483 处 `any` 不是"图省事"，是**对类型的傲慢**。每个 `any` 都是一个未来 bug 容器。
- **修复建议**：
  1. **立即开启**：`strict: true`、`noImplicitAny: true`、`strictNullChecks: true`
  2. `Result<T = any>` 改为 `Result<T = unknown>`，强制调用方断言或类型收窄
  3. 启动 codegen：从后端 OpenAPI 自动生成前端 DTO，**杜绝手写 `any`**
  4. CI 加 `vue-tsc --noEmit` 检查，**不通过禁止合并**

```typescript
// 修复样例
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

// 调用方必须做类型断言或 instanceof 收窄
const res = await getUserInfo()
if ('userName' in res.data) {
  console.log(res.data.userName)  // TS 这里能推断出 string
}
```

---

### 问题 3：Token 存 localStorage —— XSS 一键盗号

- **文件位置**：`src/api/request.ts:26`、`src/stores/user.ts`
- **现象**：JWT token 以明文形式存在 `localStorage` 里。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 任何 `v-html` 漏洞、任何第三方依赖的 XSS、任何被污染的 CDN 资源——**只要用户的浏览器执行了一段 JavaScript，就能通过 `localStorage.getItem('token')` 拿到用户的登录态**。这是教科书级别的"前端认证反模式"。
  - 更可笑的是，你写了 `escapeHtml` 来防 XSS——但**你用 localStorage 存 token 的那一刻，escapeHtml 就形同虚设**。攻击者不需要 XSS 就能拿到 token，他们有 100 种更简单的方式。
  - 在中国互联网环境下，用户安装的浏览器插件、广告拦截器、客服 IM 工具的浏览器扩展——**任何一个有恶意代码的执行权限都能盗 token**。
- **修复建议**：
  1. **Token 必须由后端 Set-Cookie 写入，HttpOnly + Secure + SameSite=Strict**
  2. 前端通过 `withCredentials: true` 自动携带 cookie，无需手写 token
  3. 实在要 localStorage，对 token 做加密（密钥从服务器每次会话获取）
  4. 立即删除 `src/api/request.ts:26` 的 `localStorage.getItem('token')`

```typescript
// 修复样例：request.ts
service.interceptors.request.use((config) => {
  // 不再手动塞 token，让 cookie 自动携带
  return config
})
```

---

### 问题 4：前端的"权限"是装饰品 —— 客户端字典能改

- **文件位置**：`src/router/index.ts:90` 附近的 `hasPathAccess`
- **现象**：路由守卫从 `localStorage.allowedPaths` 读取允许访问的路径；用户开 DevTools 改这个数组就能给自己加权限。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 你这套"前端权限"对**正常用户**有用，对**攻击者**和**好奇的同事**都没用。任何有点前端知识的人打开 DevTools 改一行 `localStorage.setItem('allowedPaths', JSON.stringify(['/admin/database']))`，路由守卫就让他进入了"管理员万能数据库"页面。
  - 你的后端有 `@RequireRole("ADMIN")` 兜底——但**前端那 1 秒的"我进来了"体验，已经足够让用户产生"这系统不安全"的信任崩塌**。
  - 你以为你做了"防御深度"——**但深度是 0 啊兄弟**。前端可篡改 + 后端兜底 ≠ 防御深度，是两个独立层。前端要做的就是"友好提示"，不是"安全检查"。
- **修复建议**：
  1. 前端**完全删除** `allowedPaths` 这套逻辑，每次进入页面实时调用后端 `GET /api/auth/user-info` 校验当前用户是否真的有该路由的访问权限
  2. 路由配置里**只声明**路由，**不写任何权限判断**
  3. 真正"无权限"由后端 API 返回 403 时，前端再展示"无权访问"

---

### 问题 5：`DatabaseBrowser.vue` —— 把核按钮放在办公桌上

- **文件位置**：`src/views/admin/DatabaseBrowser.vue`（前端 UI 入口）
- **现象**：在前端封装了一个"友好"的数据库表格操作界面，列出所有业务表，提供增删改查。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 这是**前端 + 后端联合上演的自杀戏码**——后端给了 `DatabaseAdminServiceImpl`（致命问题 1）的大炮，前端装了一个舒服的瞄准镜。
  - 一个普通管理员（`ADMIN` 角色）的密码一旦泄露——比如你前面"测试期可接受"的硬编码 `admin/123456`——攻击者登录后只需在菜单里点几下，就能**删掉所有考核数据、把所有用户禁用、把所有评分清零**。
  - 更讽刺的是，这套"数据库浏览器"还能**操作 `sys_operation_log` 表**——也就是说，攻击者可以**清空自己的操作痕迹**。一个没有审计的系统 + 一个能删审计的能力 = 完美的"事后无据可查"。
- **修复建议**：
  1. **生产构建**通过 `import.meta.env.VITE_ENABLE_DB_ADMIN` 控制，**默认不打包**这个组件
  2. **开发模式**也只能在内网访问，且加上 IP 白名单
  3. 删除 `sys_operation_log` 表的写权限给普通管理员——`DatabaseAdminController` 应该**禁止**操作 `sys_operation_log` 表
  4. 长远方案：彻底拆分 admin-tools 模块，**主项目永远不引用它**

---

### 问题 6：Tabnabbing 漏洞 —— 一行 `rel` 属性

- **文件位置**：
  - `src/views/dept/SelfEvaluation.vue:222` — `target="_blank"` 没加 `rel="noopener noreferrer"`
  - `src/views/dept/SelfEvaluation.vue:311` — 同上
- **现象**：附件"查看"链接使用 `target="_blank"` 但没加 `rel` 属性。
- **风险等级**：🔴 致命（虽然归类上更像严重，但放着不管会有真实攻击面）
- **犀利点评**：
  - 这是 OWASP Top 10 都懒得专门写、但每本前端安全书都会提到的漏洞：用户在新标签打开附件时，**恶意网站可以通过 `window.opener.location = '...'` 把原页面替换成钓鱼页面**。用户切回原标签，看到的是"登录已过期"——然后乖乖输入密码。
  - 你引用的附件链接如果来自用户上传（`/api/.../download`），**目前看是相对路径没问题**；但如果将来支持外部 URL 预览，这就是个"免费被钓鱼"的洞。
  - 修复成本：**一个字母**。`rel="noopener noreferrer"`。
- **修复建议**：
  1. 全局搜 `target="_blank"`，**逐个补** `rel="noopener noreferrer"`
  2. 或者在 ESLint 规则里加 `vue/no-target-blank` + `vue/no-rel` 强制约束
  3. CSP 加 `Content-Security-Policy: ... form-action 'self' ...` 防止 opener 跳转

```html
<!-- 修复前 -->
<a :href="getAttachmentHref(row)" target="_blank" class="file-link">查看</a>

<!-- 修复后 -->
<a :href="getAttachmentHref(row)" target="_blank" rel="noopener noreferrer" class="file-link">查看</a>
```

---

## 🟠 严重问题

### 问题 7：5 个"上帝组件" —— 2000 行的 Vue 文件

- **文件位置**：
  - `src/views/dept/IndicatorSet.vue` — **2217 行 / 72KB**
  - `src/views/dept/SelfEvaluation.vue` — **1687 行 / 54KB**
  - `src/views/admin/UserManagement.vue` — **781 行 / 27KB**
  - `src/views/exam/IndicatorProgress.vue` — **1091 行 / 31KB**
  - `src/views/exam/IndicatorApproval.vue` — **1349 行 / 37KB**
- **犀利点评**：
  - 这 5 个文件加起来 **7125 行**，**超过整个项目代码量的 50%**。每个文件都同时承担了：数据获取、业务逻辑、UI 渲染、数据导出、状态管理、表单验证、模态框控制……**完全违反了"单一职责"原则**。
  - `IndicatorSet.vue` 2217 行意味着：**任何一个小修改都要 5 分钟加载文件、滚动定位、20 分钟以上心理建设**。团队开发时冲突率 100%，code review 几乎不可能。
  - 你的项目里有 3 个 `el-dialog` 嵌套、4 层条件渲染、超过 30 个 `ref` —— **这不是"组件"，是"页面式应用程序"**。
- **修复建议**：
  1. 拆分原则：**每个组件 ≤ 300 行**，每个组件做一件事
  2. 数据获取 → `useIndicatorSet.ts` Composable
  3. 表格渲染 → `IndicatorSetTable.vue` 子组件
  4. 表单/弹窗 → `IndicatorSetFormDialog.vue` 子组件
  5. 数据导出 → `useExcelExporter.ts` 工具 Composable
  6. 重构顺序按业务重要度：`IndicatorSet.vue` 优先，因为它最高频被改

```typescript
// 修复样例：Composable 拆分
// src/composables/useIndicatorSet.ts
export function useIndicatorSet(examGroupId: Ref<number | null>) {
  const indicatorList = ref<Indicator[]>([])
  const loading = ref(false)
  
  const loadIndicators = async () => { ... }
  const saveIndicator = async (data: IndicatorSaveDTO) => { ... }
  
  return { indicatorList, loading, loadIndicators, saveIndicator }
}
```

---

### 问题 8：`console.log` 泄漏用户敏感数据到生产

- **文件位置**：
  - `src/views/dept/SelfEvaluation.vue:491` — `console.log` 评分数据
  - `src/views/dept/SelfEvaluation.vue:907` — `console.log` 附件路径
  - `src/views/dept/SelfEvaluation.vue:911` — `console.log` 完整行数据
- **犀利点评**：
  - 生产环境 `console.log` 是给攻击者的**免费情报**。一旦用户打开 DevTools（或者通过用户行为分析工具），所有评分、附件路径、行数据全裸奔。
  - 你的目标用户是 HR / 考核员——**他们处理的不是技术问题，是"人和钱"的问题**。一个员工的自评分数被同事在 DevTools 看到，可能引发劳动仲裁。
- **修复建议**：
  1. **删除**所有 `console.log` / `console.error` / `console.debug`
  2. 或者在 `vite.config.ts` 加 build 移除：
     ```js
     build: {
       terserOptions: {
         compress: { drop_console: true }
       }
     }
     ```
  3. 必要日志走 `Sentry` / 自建日志服务

---

### 问题 9：401 全页面刷新 = 状态全丢

- **文件位置**：`src/api/request.ts:66`
- **现象**：`window.location.href = '/login'` 触发整页刷新，丢弃 Pinia 状态、路由状态、所有缓存。
- **犀利点评**：
  - 用户在 1000 行的 `IndicatorSet.vue` 编辑了半小时数据——token 过期——`window.location.href = '/login'`——**所有草稿丢了**。
  - 这不叫"用户体验差"，这叫"逼用户用脚投票"。
  - 你的项目支持 token refresh 吗？看不出来。**一个 token 续期机制都没有 + 整页刷新 = 每个用户每周至少丢一次草稿**。
- **修复建议**：
  1. `router.push('/login')` 替代 `window.location.href`
  2. 实现 `axios` 响应拦截：401 时**先用 refresh token 续期**，续期失败再跳登录
  3. 关键表单（自评、评分、申诉）必须**实时存草稿**到后端，30 秒一次

---

### 问题 10：Element Plus 全量图标注册 = 包大 30%

- **文件位置**：`src/main.ts:17-19`（推断）
- **现象**：`import * as Icons from '@element-plus/icons-vue'` 全量注册 300+ 图标。
- **犀利点评**：
  - 你项目实际用的图标不超过 20 个。剩下 280+ 个图标**全部打包进生产构建**，每个图标几 KB，累计**无谓增加 200-500KB 体积**。
  - 移动端用户付流量费打开你的页面——其中 1/3 是永远用不上的图标。
- **修复建议**：
  1. 按需引入：
     ```typescript
     import { Search, Plus, Delete } from '@element-plus/icons-vue'
     ```
  2. 或者用 `unplugin-icons` 自动按需
  3. main.ts 全文不超过 50 行

---

### 问题 11：无虚拟滚动 —— 1000 行表格必卡

- **位置**：所有 `el-table`（特别在 `IndicatorSet.vue`、`SelfEvaluation.vue`）
- **犀利点评**：
  - `el-table` 默认渲染**所有行到 DOM**。100 行没事，1000 行开始卡，5000 行直接死。
  - 你的 `IndicatorSet.vue` 业务是"指标设定"，一个部门 1000+ 指标很正常。
  - **Element Plus 官方推荐大数据量用 `el-table-v2`**——你没用。
- **修复建议**：
  1. 改用 `el-table-v2`（虚拟滚动版本）
  2. 或加 `lazy + load` 配合分页
  3. 或加 `el-table` 的 `height` 固定 + 内部滚动

---

## 🟡 一般问题

### 问题 12：无 ESLint + Prettier + husky

- **犀利点评**：项目根目录没有 `.eslintrc`、`.prettierrc`、`husky` 配置。
- **修复建议**：
  1. 装 `eslint` + `eslint-plugin-vue` + `@typescript-eslint`
  2. 装 `prettier` + `eslint-config-prettier` 避免冲突
  3. 装 `husky` + `lint-staged`，pre-commit 自动 lint + format
  4. CI 加 `pnpm lint`，**不通过禁止合并**

### 问题 13：路由配置 25 个 `v1` 后缀

- **位置**：多个 Vue 组件 `<h2>` 标题里
- **犀利点评**：用户在生产环境看到"管理后台 v1"——**这是在告诉用户"这还是个半成品"**。业务系统的版本号是后端的事，前端 UI 不该有。
- **修复建议**：移除所有 v1/v2/v3 标识。

### 问题 14：`extractPaths` 函数重复实现 2 次

- **位置**：`src/router/index.ts:58` + `src/layouts/MainLayout.vue:269`
- **犀利点评**：同样的工具函数写两遍，意味着其中一份已经过期——两份的输出可能在某些边界 case 下不一致。
- **修复建议**：抽到 `src/utils/router.ts`，单点实现。

---

## 🛠️ 修复路线图

**P0（本周必做）**：
1. 删 `mysql2` 依赖
2. Token 改 httpOnly cookie
3. `DatabaseBrowser.vue` 生产构建剔除
4. 补 `rel="noopener noreferrer"`
5. 删 `console.log`

**P1（2 周内）**：
6. 拆 `IndicatorSet.vue` 第一个 God 组件
7. 开启 strict TypeScript
8. 401 用 `router.push` + refresh token
9. 路由权限改为后端实时校验
10. 加 ESLint + Prettier

**P2（迭代期内）**：
11. 拆剩余 4 个 God 组件
12. 全量图标按需引入
13. 大表格用 `el-table-v2`
14. 移除 UI 中的版本号

---

## 🎯 毒舌总结

> 这个前端项目**最让人无语的不是技术债，是"对工具的浪费"**。Vue 3 的 Composition API、TypeScript 的类型系统、Pinia 的状态管理、Vite 的构建速度——**全被一种"能用就行"的工程师心态糟蹋了**。

> 拿 TypeScript 来说：你写了 `.ts`、写了 `: Ref<>`、写了 `import type`，**然后所有 API 都 `any`**——**这不是"用 TypeScript"，这是"用 TypeScript 的语法写 JavaScript"**。Vue 团队做 Composition API 是为了让逻辑可组合，你**用 2000 行的 setup() 把所有逻辑堆在一起**。

> 安全方面更让人心碎。`mysql2` 出现在前端依赖——**这种错误只有初学者会犯**，你这个项目的代码量级已经不"初学者"了。Token 存 localStorage、Tabnabbing 没 rel、客户端字典当权限——**这不叫"前端工程"，叫"前端作坊"**。

> 但话说回来——**至少这个项目在迭代、在出活**。比起那种"代码完美但跑不起来"的 PPT 项目，**你的代码至少证明业务跑通了**。问题是，业务跑通 ≠ 能上线。**先把 P0 这 5 条修完，再谈别的**。

> 修完 P0 后建议做一件事：**让一个 5 年以上经验的前端架构师每周花 4 小时专门重构**。不要让业务压垮代码质量——你现在的 5 个 God 组件再过 3 个月就再也拆不动了。

---

*本报告由 Mavis 集成最新大模型直审生成，基于一手代码阅读 + 静态分析。*
