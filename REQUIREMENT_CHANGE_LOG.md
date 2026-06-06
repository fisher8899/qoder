# 需求变更台账

用途：记录“新增了哪些需求、调整了哪些需求、为什么改、怎么实现”。  
维护要求：只要本次任务包含新增需求或需求调整，就必须新增记录。

---

## 记录模板

### REQ-XXX: [需求标题]

- 日期：YYYY-MM-DD
- 类型：新增需求 / 需求调整 / 体验优化 / 技术配套
- 状态：待实现 / 实现中 / 已完成 / 已验证
- 来源：用户提出 / 业务调整 / 联调反馈
- 背景：
- 原需求：
- 新需求或调整后要求：
- 实现方案：
- 涉及文件：
- 验证方式：
- 备注：

---

## 已记录需求

### REQ-001: 审批页支持通知直达并自动打开对应审批对象

- 日期：2026-05-13
- 类型：需求调整
- 状态：已完成
- 来源：业务联调反馈
- 背景：通知跳转只进入审批列表，无法直接进入对应业务对象。
- 原需求：点击通知后进入审批页面。
- 新需求或调整后要求：点击通知后，应直接定位到对应考核组和部门的审批弹窗。
- 实现方案：
  - 后端通知链接附带 `examGroupId`、`orgId`、`mode=approve`
  - 前端审批页读取路由参数后自动打开目标弹窗
- 涉及文件：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizIndicatorDefinitionServiceImpl.java`
  - `assessment-frontend/src/views/exam/IndicatorApproval.vue`
  - `assessment-frontend/src/views/leader/IndicatorApprove.vue`
- 验证方式：通知跳转手工联调验证

### REQ-002: 指标设定进度详情改为复用审批详情页的只读模式

- 日期：2026-05-13
- 类型：需求调整
- 状态：已完成
- 来源：业务联调反馈
- 背景：原详情页跳错页面且权限模型不对。
- 原需求：财务管理员可以查看指标设定详情。
- 新需求或调整后要求：详情页应与审批详情展示一致，但必须只读，不显示审批操作按钮。
- 实现方案：
  - 统一跳转到 `exam/indicator-approval`
  - 使用 `mode=view` 区分只读模式
  - 补齐详情页上下文字段
- 涉及文件：
  - `assessment-frontend/src/views/exam/IndicatorProgress.vue`
  - `assessment-frontend/src/views/exam/IndicatorApproval.vue`
- 验证方式：页面手工验证

---

### REQ-2026-05-27-001: 测试阶段保留测试账号快捷登录与数据库查询

- 日期：2026-05-27
- 类型：需求调整
- 状态：已实现
- 来源：用户提出
- 背景：当前仍处于测试阶段，需要快速登录和清理垃圾数据，不能按正式生产安全基线提前移除相关入口。
- 原需求：安全审核中建议去掉登录页默认测试账号展示，并默认关闭数据库查询入口。
- 调整后要求：
  - 登录页继续展示默认测试账号用户名和密码，并支持点击自动填充。
  - dev 环境继续启用“系统运维/数据库查询”，用于测试阶段垃圾数据清理。
  - 在用户确认需要清理前，后续扫描和修复不得再把这两项作为当前阶段必须删除的问题。
- 实现方案：
  - `LoginPage.vue` 恢复“测试账号”区域，内置 `admin / 123456` 和 `tuke01 / 123456`。
  - `application-dev.yml` 将 `app.db-admin.enabled` 默认恢复为 `true`，同时保留 `APP_DB_ADMIN_ENABLED=false` 覆盖开关。
- 涉及文件：
  - `assessment-frontend/src/views/login/LoginPage.vue`
  - `assessment-backend/src/main/resources/application-dev.yml`
- 验证方式：
  - `npm run build`
  - `mvn test`
  - 前后端启动后登录并访问数据库查询页面
- 备注：该保留项仅适用于测试阶段；生产环境仍应关闭测试账号展示和数据库查询入口。

### REQ-003: 审批流程节点可视化

- 日期：2026-05-13
- 类型：体验优化
- 状态：已完成
- 来源：业务体验优化
- 背景：审批页面无法直观看出当前所处审批阶段。
- 原需求：能完成审批动作即可。
- 新需求或调整后要求：明确展示“已审批 / 当前审批 / 未审批”三种节点状态。
- 实现方案：
  - 增加流程节点状态映射
  - 为不同状态增加专属样式
- 涉及文件：
  - `assessment-frontend/src/views/exam/IndicatorApproval.vue`
  - `assessment-frontend/src/views/leader/IndicatorApprove.vue`
- 验证方式：页面手工验证

### REQ-004: 建立项目进度管理与可追溯台账体系

- 日期：2026-05-14
- 类型：技术配套
- 状态：已完成
- 来源：用户提出
- 背景：需要在每次任务完成后，能清楚知道已完成内容、下一个任务，以及 Bug 修复和需求调整的追溯记录；重启机器后也要能快速接手。
- 原需求：仅有零散进度文档。
- 新需求或调整后要求：
  - 每次任务完成后及时更新进度文档
  - 记录“已经完成什么”“下一个任务是什么”
  - Bug 修复和需求变更分开记录
  - 重启后能快速知道当前开发进度和下一步工作
- 实现方案：
  - 建立总览、进度、Bug、需求、交接、规范六类文档
  - 规定每次任务结束必须同步更新
  - 在 `SESSION_HANDOFF.md` 中提供重启后的快速入口
- 涉及文件：
  - `PROJECT_PROGRESS.md`
  - `progress.md`
  - `BUG_FIX_LOG.md`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `SESSION_HANDOFF.md`
  - `docs/project-management.md`
  - `CLAUDE.md`
- 验证方式：文档结构检查与交接路径检查

### REQ-005: 从项目负责人视角补齐项目控制面文档

- 日期：2026-05-14
- 类型：技术配套
- 状态：已完成
- 来源：用户提出
- 背景：仅有进度、Bug、需求和交接文档还不够，缺少真正用于控制任务、交付、风险、测试和发布的管理文档。
- 原需求：已有基础文档体系，但缺少项目负责人控制面。
- 新需求或调整后要求：
  - 增加任务看板
  - 增加交付计划
  - 增加风险台账
  - 增加测试策略
  - 增加发布清单
- 实现方案：
  - 建立 5 份控制面文档，形成“任务推进 + 风险控制 + 交付收口”完整治理链路
  - 将后续研发纳入该文档体系管理
- 涉及文件：
  - `TASK_BOARD.md`
  - `DELIVERY_PLAN.md`
  - `RISK_REGISTER.md`
  - `TEST_STRATEGY.md`
  - `RELEASE_CHECKLIST.md`
  - `PROJECT_PROGRESS.md`
  - `progress.md`
- 验证方式：文档存在性检查、内容结构检查、控制面覆盖检查

### REQ-006: 设计项目负责人项目管理看板 HTML

- 日期：2026-05-14
- 类型：新增需求
- 状态：已完成
- 来源：用户提出
- 背景：需要在当前目录下提供一个面向项目负责人的可视化项目管理看板，能直观看到项目阶段、总体进度、流程节点、任务、规范、文档和功能入口，并支持钻取查看。
- 原需求：已有项目管理文档，但缺少统一的可视化驾驶舱页面。
- 新需求或调整后要求：
  - 在当前目录下提供一个 HTML 看板
  - 站在项目负责人视角展示当前项目阶段、总体进度和关键管理动作
  - 支持先看整体，再看流程节点状态、具体任务、文档和功能入口
  - 文档支持直接打开查看
  - 对应功能页面支持切换到原型或相关入口查看
- 实现方案：
  - 新增单文件看板 `project-management-dashboard.html`
  - 采用侧边导航 + 总览卡片 + 流程节点钻取 + 文档入口 + 功能入口设计
  - 将当前项目管理规则、冲刺清单、任务和风险信息结构化到页面中
- 涉及文件：
  - `project-management-dashboard.html`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `PROJECT_PROGRESS.md`
  - `progress.md`
- 验证方式：本地打开 HTML，检查页面导航、节点切换、文档链接和功能入口链接

### REQ-007: 项目管理看板增强为第二版执行驾驶舱

- 日期：2026-05-14
- 类型：需求调整
- 状态：已完成
- 来源：用户提出
- 背景：第一版已具备总览、流程、文档和功能入口，但还需要更贴近项目负责人日常管理动作的执行视图。
- 原需求：提供可视化项目管理看板。
- 新需求或调整后要求：
  - 增加“今日事项 / 本周事项 / 阻断事项”管理区
  - 增加任务筛选能力，便于按状态、优先级、类型查看
  - 增加节点状态颜色联动，使阶段和业务节点风险更直观
  - 保持文档入口和功能入口不变，继续支持钻取
- 实现方案：
  - 升级 `project-management-dashboard.html` 为第二版
  - 增加“执行驾驶舱”分页
  - 使用前端内置任务数据源渲染筛选后的任务卡片
  - 增加阶段节点与业务节点的状态色设计和动态详情切换
- 涉及文件：
  - `project-management-dashboard.html`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `PROJECT_PROGRESS.md`
  - `progress.md`
- 验证方式：本地打开 HTML，验证导航切换、任务筛选、节点点击联动、文档与功能入口跳转

### REQ-008: 优化项目总览驾驶舱与文档中心中文化展示

- 日期：2026-05-14
- 类型：需求调整
- 状态：已完成
- 来源：用户提出
- 背景：项目总览需要更像驾驶舱，直观看到整体流程节点、当前节点和下一节点；文档中心需要中文名称，并且点击后不要裸看 Markdown，而是以设计化页面展示文档内容。
- 原需求：已有项目看板和文档入口，但总览驾驶舱视角不够强，文档查看体验偏原始。
- 新需求或调整后要求：
  - 项目总览从驾驶舱视角展示整体流程节点、当前节点、下一节点和阻断点
  - 文档名称在页面上全部显示为中文
  - 文档中心点击后进入设计化文档查看页，而不是直接打开 `.md`
  - 文档查看页支持美观展示内容，并保留原文入口
- 实现方案：
  - 升级 `project-management-dashboard.html` 为第三版驾驶舱总览
  - 新增 `project-doc-viewer.html` 作为项目文档设计化查看器
  - 将文档中心全部切换到中文名称和查看器链接
- 涉及文件：
  - `project-management-dashboard.html`
  - `project-doc-viewer.html`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `PROJECT_PROGRESS.md`
  - `progress.md`
- 验证方式：本地打开看板，验证总览驾驶舱、中文文档名称、文档查看器跳转和原文入口

### REQ-009: 文档查看器升级为优先自动读取本地 Markdown

- 日期：2026-05-14
- 类型：需求调整
- 状态：已完成
- 来源：用户提出
- 背景：文档查看器已经具备中文化和设计化展示，但仍以预置摘要为主，需要进一步增强为优先自动读取真实 Markdown 内容。
- 原需求：文档中心点击后进入设计化文档查看页。
- 新需求或调整后要求：
  - 优先自动读取对应本地 Markdown 文件内容
  - 自动将 Markdown 转换为更适合阅读的 HTML 展示
  - 若浏览器因本地文件策略无法直接读取，则自动降级为结构化摘要视图
  - 保留原文入口
- 实现方案：
  - 升级 `project-doc-viewer.html`
  - 增加本地 Markdown `fetch` 读取逻辑
  - 增加轻量 Markdown 渲染逻辑
  - 增加失败回退机制
- 涉及文件：
  - `project-doc-viewer.html`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `PROJECT_PROGRESS.md`
  - `progress.md`
- 验证方式：本地打开文档查看器，验证自动读取、设计化渲染、失败降级和原文链接

### REQ-010: 项目看板升级为优先自动读取本地治理文档数据

- 日期：2026-05-14
- 类型：需求调整
- 状态：已完成
- 来源：用户提出
- 背景：看板和文档查看器已经具备较强展示能力，但项目总览、任务和风险数据仍主要依赖手工维护的页面内静态数据，需要进一步接入真实治理文档。
- 原需求：继续增强项目负责人看板。
- 新需求或调整后要求：
  - 看板优先自动读取 `PROJECT_PROGRESS.md`
  - 看板优先自动读取 `TASK_BOARD.md`
  - 看板优先自动读取 `RISK_REGISTER.md`
  - 将项目总览、任务和风险优先切换为真实文档数据
  - 读取失败时保留静态兜底
- 实现方案：
  - 升级 `project-management-dashboard.html` 为第五版
  - 增加本地 Markdown `fetch` 读取逻辑
  - 增加轻量解析逻辑，提取进度、任务、风险信息
  - 增加自动读取状态提示和失败回退机制
- 涉及文件：
  - `project-management-dashboard.html`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `PROJECT_PROGRESS.md`
  - `progress.md`
- 验证方式：本地打开看板，验证自动读取状态、任务区与风险区动态更新、读取失败回退

### REQ-011: 在本地配置 6 个项目专用 Agent Profile

- 日期：2026-05-14
- 类型：新增需求
- 状态：已完成
- 来源：用户提出
- 背景：后续需要开启多 Agent 协作，不同 Agent 分别负责项目管理、前端、后端、测试、代码审查、发布等事务，需要先在本地落地高质量可复用的 profile 配置。
- 原需求：希望了解如何设计 Agent profile，让 Agent 具备更强能力和更快解决问题的速度。
- 新需求或调整后要求：
  - 在本地正式配置 6 个 Agent Profile
  - 每个 Agent 要有清晰职责、边界、默认读取文档、产出格式、验收标准和升级规则
  - 配置应贴合本项目实际目录、治理规则和交付方式
- 实现方案：
  - 在 `.claude/agents/` 目录下创建 6 个 Agent profile 文件
  - 新增总入口 `README.md`
  - 将项目治理规则固化进各 Agent 的“特殊规则”和产出要求
- 涉及文件：
  - `.claude/agents/README.md`
  - `.claude/agents/project-manager-agent.md`
  - `.claude/agents/frontend-agent.md`
  - `.claude/agents/backend-agent.md`
  - `.claude/agents/test-agent.md`
  - `.claude/agents/code-review-agent.md`
  - `.claude/agents/release-agent.md`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `PROJECT_PROGRESS.md`
  - `progress.md`
- 验证方式：检查本地目录存在性、Profile 结构完整性、职责边界和项目文档规则覆盖情况

### REQ-012: 菜单类别驱动职责菜单与数据范围统一模型

- 日期：2026-05-15
- 类型：需求调整 / 安全增强 / 技术配套
- 状态：已完成并通过构建验证
- 来源：用户提出
- 背景：
  - 现有系统已有职责类型和数据范围字段，但菜单没有类别属性。
  - 定义单位职责、部门职责时，菜单可选范围缺少统一约束。
  - 仅靠前端角色或菜单配置容易出现职责菜单与数据范围不一致的问题。
- 原需求：
  - 系统管理员、单位管理员、部门管理员等角色各自配置不同菜单。
  - 数据范围通过用户权限中的 `data_scope/scope_id` 控制。
- 新需求或调整后要求：
  - 功能菜单增加类别属性。
  - 菜单类别支持多选：
    - `SYSTEM`：全部/系统管理员类
    - `UNIT`：单位类
    - `DEPT`：部门类
  - 系统职责默认全部菜单可选。
  - 单位职责只能选择单位类菜单。
  - 部门职责只能选择部门类菜单。
  - 系统职责数据范围为全部，单位职责数据范围为单位，部门职责数据范围为部门/组织。
- 漏洞分析：
  - 模型本身可行，但如果只在前端过滤菜单会被绕过。
  - 因此后端保存职责菜单时必须校验菜单类别。
  - 用户权限分配也必须由后端按职责类型归一数据范围，不能信任前端传入。
- 实现方案：
  - `sys_menu` 增加 `menu_category` 字段。
  - 新增迁移 `V3__menu_category_scope.sql`，添加字段并回填存量菜单类别。
  - 后端菜单服务支持按职责类型返回可分配菜单。
  - 后端职责服务保存菜单时强制校验职责类型和菜单类别。
  - 后端权限分配按职责类型强制数据范围。
  - 前端菜单定义页增加菜单类别多选。
  - 前端职责定义页按职责类型加载可选菜单。
  - 主布局菜单加载改为 `/menu/current`。
- 涉及文件：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/SysMenuService.java`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysMenuController.java`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysRoleServiceImpl.java`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserPermissionController.java`
  - `assessment-backend/src/main/resources/db/schema.sql`
  - `assessment-backend/src/main/resources/db/data.sql`
  - `assessment-backend/src/main/resources/db/migration/V3__menu_category_scope.sql`
  - `assessment-frontend/src/api/admin.ts`
  - `assessment-frontend/src/layouts/MainLayout.vue`
  - `assessment-frontend/src/views/admin/MenuManagement.vue`
  - `assessment-frontend/src/views/admin/RoleManagement.vue`
  - `docs/menu-category-scope-refactor-2026-05-15.md`
- 验证方式：
  - 后端：`mvn -q -DskipTests package` 通过，执行前需切换 `JAVA_HOME` 到 JDK 17。
  - 前端：`npm run build` 通过。
- 备注：
  - 真实数据库仍需启动后确认 Flyway V3 执行成功。
  - 下一步需做五类职责菜单与数据范围回归。
