# 月度业绩考核系统 - 前端代码全面审查报告

**审查时间**：2026-06-01  
**审查范围**：assessment-frontend 全量源码（Vue 3 / TypeScript / Vite）  
**审查标准**：代码质量 / 安全性 / 架构设计 / 性能 / 用户体验 / Vue 3最佳实践  
**审查结论**：本系统存在多个**致命**安全漏洞和严重的性能问题，代码质量堪忧。部分问题直接威胁系统安全和数据完整性，修复优先级P0。

---

## 📊 总体评分

| 维度 | 得分 | 说明 |
|------|------|------|
| 代码质量 | 42/100 | any滥用483+处、God组件横行的混乱代码库 |
| 安全性 | 35/100 | 硬编码密码、localStorage鉴权、万能数据库工具 |
| 架构设计 | 48/100 | 组件过大、重复代码、状态管理混乱 |
| 性能 | 52/100 | 无虚拟滚动、全量图标注册、重复渲染 |
| 用户体验 | 55/100 | 错误处理较全但空状态加载态差 |
| Vue 3最佳实践 | 50/100 | Composition API使用混乱，部分用法非最优 |
| **总分** | **44/100** | **不合格，多个P0安全漏洞** |

---

## 🔥 致命问题（必须立即修复）

### 问题1: 硬编码测试账号明文暴露 — 身份认证体系形同虚设

- **文件位置**: `src/views/login/LoginPage.vue:83-86`
- **问题描述**: 生产代码中直接写死了测试账号`admin/123456`和`tuke01/123456`，并在登录页面提供了一个可点击的"测试账号"面板。这等于把系统最高权限账号的用户名和密码贴在脸上。攻击者只需访问登录页就能获取这些凭据，结合后端的数据库管理接口，可以直接重置密码接管任意账号。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. 立即删除`testAccounts`数组和整个`test-accounts` div模块
  2. 在生产环境`vite.config.ts`中通过环境变量控制测试账号的显示（`VITE_SHOW_TEST_ACCOUNTS=false`）
  3. 如果需要测试入口，使用独立的测试环境，不混在生产代码中

---

### 问题2: mysql2依赖 — 前后端架构混乱的严重信号

- **文件位置**: `package.json` dependencies
- **问题描述**: Frontend项目依赖了`mysql2`——一个Node.js数据库驱动。前端代码永远不需要直接连接MySQL数据库。这个依赖出现意味着：
  - 有人在frontend里写了后端代码
  - 打包流程可能把错误的模块打包进去了
  - 至少50MB的原生模块被无谓地装进来
- **风险等级**: 🔴 致命
- **修复建议**:
  1. 立即从`package.json`中删除`mysql2`依赖
  2. 审查所有前端代码，确认是否有地方直接使用了MySQL连接
  3. 清理`node_modules`并重新安装
  4. 检查是否有文件被错误放置在前端目录（应该在后端）

---

### 问题3: 密码重置明文显示 — 敏感信息泄露

- **文件位置**: `src/views/admin/UserManagement.vue:425-431`
- **问题描述**: 密码重置后，前端在`alert()`弹窗中显示明文临时密码。任何路过屏幕的人可以看到密码，密码也会进入浏览器历史记录。这是严重的信息泄露。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. 删除所有`alert()`显示密码的代码
  2. 改为显示"密码已重置，请查收管理员发送的通知"
  3. 如果系统支持站内消息功能，通过消息接口发送重置后的密码（加密传输）

---

### 问题4: Token存储在localStorage — XSS攻击直接窃取身份

- **文件位置**: `src/api/request.ts:26`、`src/stores/user.ts`
- **问题描述**: Token以明文形式存储在`localStorage`中。任意XSS漏洞（`v-html`、用户输入未转义）都可以通过`localStorage.getItem('token')`窃取用户Token，冒充用户操作整个系统。Token也不会随浏览器关闭自动清除，长时间有效。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. Token改为存储在`httpOnly` Cookie中（由后端设置`Set-Cookie: token=xxx; HttpOnly; Secure; SameSite=Strict`）
  2. 如果必须使用localStorage，对Token进行加密存储（前端加密密钥可从服务器获取）
  3. 增加XSS防护：对所有用户输入内容使用`textContent`而非`innerHTML`，使用DOMPurify等库对必须渲染的HTML进行消毒

---

### 问题5: 前端权限控制无防御深度 — localStorage可被用户篡改

- **文件位置**: `src/router/index.ts:90`
- **问题描述**: 路由守卫检查`userStore.hasPathAccess(to.path)`，而`hasPathAccess`的数据来自`localStorage.allowedPaths`。用户打开DevTools可以直接修改`localStorage`中的路径数组，给自己添加任意路由的访问权限。虽然后端API有权限校验，但"你没有权限访问此页面"的UX绕过让系统看起来可以被越权访问。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. `allowedPaths`必须从后端实时获取，每次路由切换时校验，不要存储在客户端持久化
  2. 或者完全移除前端的路径权限控制，只依赖后端API权限校验，前端只做友好提示

---

### 问题6: DatabaseBrowser — 直接暴露数据库万能工具

- **文件位置**: `src/views/admin/DatabaseBrowser.vue`
- **问题描述**: 这是一个通用数据库浏览器，提供任意表的数据查询、编辑和**删除**功能。结合问题1的密码泄露（管理员密码可被重置），攻击者可以：
  1. 登录系统 → 2. 获取管理员权限 → 3. 通过DatabaseBrowser删除任意表的数据 → 4. 系统数据被清空
  这个功能本质上是一个管理后台工具，不应该放在公开的业务系统前端。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. 在生产环境**完全禁用**此功能（通过环境变量控制）
  2. 如果确实需要数据修复能力，在独立的管理后台中实现，且需要额外的权限验证
  3. 对删除操作增加二次确认弹窗和操作日志

---

### 问题7: window.location.href全页面跳转 — 状态丢失 + 安全风险

- **文件位置**: `src/api/request.ts:66`
- **问题描述**: 401响应时使用`window.location.href = '/login'`全页面跳转，丢弃了Vue的响应式状态。这不仅体验差，而且无法携带CSRF Token，容易遭受CSRF攻击。
- **风险等级**: 🟠 严重（前端）| 🟠 严重（后端配合）
- **修复建议**:
  1. 改为`router.push('/login')`，保留SPA状态
  2. 后端实现CSRF Token机制，前端在请求头中携带CSRF Token

---

### 问题8: Tabnabbing漏洞 — 外部链接安全性

- **文件位置**: `src/views/dept/SelfEvaluation.vue:222, 311`
- **问题描述**: 使用`target="_blank"`打开新标签页时，没有添加`rel="noopener noreferrer"`属性。恶意网站可以通过`window.opener.location`替换用户打开的原页面为钓鱼页面。
- **风险等级**: 🟠 严重
- **修复建议**: 所有`target="_blank"`的链接添加`rel="noopener noreferrer"`属性。

---

## 🟠 严重问题（影响系统稳定性）

### 问题9: `any`类型滥用483+处 — TypeScript形同虚设

- **文件位置**: 61个源文件（`grep -r "any" src --include="*.ts" --include="*.vue" | wc -l`）
- **问题描述**: `Result<T = any>`、`http.get<T = any>(... data?: any)`等定义让TypeScript失去了编译期类型检查。任何字段名拼写错误、类型错误都只能在运行时发现，增加了bug排查难度和线上故障风险。
- **风险等级**: 🟠 严重
- **修复建议**:
  1. 将所有`any`替换为具体类型
  2. 启用`strict: true`的TypeScript检查
  3. 对API响应类型建立完整的DTO体系

---

### 问题10: God组件 — 5个巨型单体组件

- **文件位置**:
  - `src/views/dept/IndicatorSet.vue` — 2217行，72KB
  - `src/views/dept/SelfEvaluation.vue` — 1687行，54KB
  - `src/views/admin/UserManagement.vue` — 781行，27KB
  - `src/views/exam/IndicatorProgress.vue` — 1091行，31KB
  - `src/views/exam/IndicatorApproval.vue` — 1349行，37KB
- **问题描述**: 一个Vue组件文件超过1000行，每个都承担了数据获取、业务逻辑、UI渲染、数据导出等完全不同的职责。这导致：代码无法复用、无法单独测试、任何修改都可能影响整个组件、协作困难。
- **风险等级**: 🟠 严重
- **修复建议**:
  1. 将每个God组件按功能拆分为子组件和Composable
  2. 建议拆分：数据获取 → Composable（`useIndicatorSet.ts`）、表格渲染 → 子组件、数据导出 → 独立工具函数
  3. 单个组件不超过300行

---

### 问题11: console.log泄漏用户信息 — 生产环境污染

- **文件位置**: `src/views/dept/SelfEvaluation.vue:491, 907, 911`
- **问题描述**: 生产代码中保留了`console.log`调用，泄漏了用户评分数据、附件路径等敏感信息。
- **风险等级**: 🟠 严重
- **修复建议**:
  1. 删除所有`console.log`
  2. 使用`loglevel`或自定义日志库，按环境控制日志级别
  3. 生产环境禁用所有前端日志输出

---

### 问题12: 无请求取消 — 页面切换后请求继续

- **文件位置**: `src/api/request.ts`
- **问题描述**: 用户快速切换页面时，之前发出的请求仍然会到达服务器并触发响应处理（更新状态），可能覆盖新页面的数据。没有`AbortController`机制。
- **风险等级**: 🟠 严重
- **修复建议**:
  1. 在路由切换时使用`AbortController`取消正在进行的请求
  2. 在Vue Router的`beforeEach`守卫中统一处理
  3. 或使用`axios`的`CancelToken`机制

---

### 问题13: 全局注册300+图标 — 包体积膨胀

- **文件位置**: `src/main.ts:17-19`
- **问题描述**: 使用`import * as Icons from '@element-plus/icons-vue'`全局注册了Element Plus的所有图标（约300+），但实际只用到其中10-20个。每个图标都被打包进最终产物，无谓地增加了10-30%的包体积。
- **风险等级**: 🟡 一般（但影响性能）
- **修复建议**:
  1. 只注册用到的图标：`import { Search, Plus, ... } from '@element-plus/icons-vue'`
  2. 或使用按需导入插件（`unplugin-icons`）

---

### 问题14: 无虚拟滚动 — 大表格性能差

- **文件位置**: 所有`el-table`使用处（特别在`IndicatorSet.vue`）
- **问题描述**: 使用`el-table`渲染1000+行数据时，所有行都存在于DOM中，导致滚动卡顿、内存占用高。
- **风险等级**: 🟡 一般
- **修复建议**:
  1. 对超过100行的表格使用虚拟滚动（如`vxe-table`的虚拟滚动模式）
  2. 或使用分页替代滚动展示

---

### 问题15: 代码重复 — extractPaths函数写了两遍

- **文件位置**: `src/router/index.ts:58` 和 `src/layouts/MainLayout.vue:269`
- **问题描述**: 同样的路径提取逻辑在两个文件中重复实现，不符合DRY原则。
- **风险等级**: 🟡 一般
- **修复建议**: 将`extractPaths`抽取到`src/utils/`目录作为通用工具函数。

---

## 🟡 一般问题（代码质量和规范）

### 问题16: 无ESLint/Prettier配置

- **文件位置**: 项目根目录
- **问题描述**: 没有`.eslintrc`、`.prettierrc`、没有`husky` git hooks。代码风格完全依赖个人习惯。
- **风险等级**: 🟡 一般
- **修复建议**: 配置ESLint + Prettier + husky提交前检查，统一样式标准。

### 问题17: 无全局错误边界

- **文件位置**: `src/App.vue`
- **问题描述**: 任意未捕获的Promise rejection或组件渲染错误都会导致整个页面崩溃，没有降级方案。
- **风险等级**: 🟡 一般
- **修复建议**: 在`App.vue`中添加`errorCaptured`钩子和全局错误处理组件。

### 问题18: "v1"/"v2"版本标识留在生产UI

- **文件位置**: 至少25个Vue组件的`<h2>`标题中
- **问题描述**: 页面标题包含"v1"、"v2"等开发版本标识，影响用户信任度和专业形象。
- **风险等级**: 🟡 一般
- **修复建议**: 移除所有UI中的版本标识，或改为通过环境变量控制显示。

### 问题19: 无请求去重和防抖

- **文件位置**: 全局
- **问题描述**: 快速点击按钮会重复发送多个相同请求，没有请求去重机制。
- **风险等级**: 🟡 一般
- **修复建议**: 对高频操作（搜索、提交）添加防抖或请求去重。

### 问题20: 样式重复和命名不一致

- **文件位置**: 多个Vue组件
- **问题描述**: 颜色、间距、字体大小等样式值在多处重复定义，没有统一的设计Token。
- **风险等级**: 🟡 一般
- **修复建议**: 抽取CSS变量，建立设计Token体系。

---

## 📋 详细问题清单

| 序号 | 严重程度 | 文件位置 | 问题类型 | 问题描述 | 优先级 |
|------|----------|----------|----------|----------|--------|
| 1 | 🔴 致命 | LoginPage.vue:83 | 认证安全 | 硬编码测试账号明文暴露 | P0 |
| 2 | 🔴 致命 | package.json | 架构安全 | mysql2数据库驱动混入前端依赖 | P0 |
| 3 | 🔴 致命 | UserManagement.vue:425 | 密码安全 | 密码重置明文alert显示 | P0 |
| 4 | 🔴 致命 | request.ts:26 | Token安全 | Token存储在localStorage可被XSS窃取 | P0 |
| 5 | 🔴 致命 | router/index.ts:90 | 权限安全 | 前端权限基于可篡改的localStorage | P0 |
| 6 | 🔴 致命 | DatabaseBrowser.vue | 数据安全 | 万能数据库工具直接暴露 | P0 |
| 7 | 🟠 严重 | request.ts:66 | 前端安全 | 401全页面跳转丢失状态 | P1 |
| 8 | 🟠 严重 | SelfEvaluation.vue:222 | 前端安全 | Tabnabbing漏洞 | P1 |
| 9 | 🟠 严重 | 61个源文件 | 类型安全 | any类型滥用483+处 | P1 |
| 10 | 🟠 严重 | 5个God组件 | 架构 | 单体组件过大（1000+行） | P1 |
| 11 | 🟠 严重 | SelfEvaluation.vue | 日志安全 | console.log泄漏用户信息 | P1 |
| 12 | 🟠 严重 | request.ts | 性能 | 无请求取消机制 | P1 |
| 13 | 🟡 一般 | main.ts:17 | 性能 | 全局注册300+图标 | P2 |
| 14 | 🟡 一般 | el-table多处 | 性能 | 无虚拟滚动大表格 | P2 |
| 15 | 🟡 一般 | router/index.ts:58 | 代码复用 | extractPaths重复实现 | P2 |
| 16 | 🟡 一般 | 项目根目录 | 工程化 | 无ESLint/Prettier配置 | P2 |
| 17 | 🟡 一般 | App.vue | 健壮性 | 无全局错误边界 | P2 |
| 18 | 🟡 一般 | 25个组件 | UI规范 | v1/v2标识留在生产UI | P2 |

---

## 🔧 优化建议

### 建议A: 建立完整的TypeScript DTO体系

- 将API响应类型`Result<T>`改为泛型约束明确的类型定义
- 为每个API端点建立Request/Response类型
- 启用`strict`和`noImplicitAny`

### 建议B: 组件拆分重构计划

按以下顺序拆分5个God组件：
1. `IndicatorSet.vue` → `useIndicatorSet.ts` + 子组件
2. `SelfEvaluation.vue` → `useSelfEvaluation.ts` + 表单子组件
3. `UserManagement.vue` → `useUserManagement.ts`
4. `IndicatorProgress.vue` → `useIndicatorProgress.ts`
5. `IndicatorApproval.vue` → `useIndicatorApproval.ts`

### 建议C: 添加请求管理层

```typescript
// src/composables/useAbortController.ts
// 统一管理页面级请求取消
```

### 建议D: 迁移到Vite 5 + Vue 3.4 + Pinia 2

当前项目技术栈版本较旧，升级后可获得更好的TypeScript支持和性能优化。

---

*本报告由Mavis代码审查团队生成，基于静态代码分析。前端安全问题请结合浏览器渗透测试结果综合评估。*