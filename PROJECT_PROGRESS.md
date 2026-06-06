# 项目总览看板

更新时间：2026-06-01
维护要求：每次完成任务后必须更新

## 1. 当前开发总览

| 模块 | 当前状态 | 进度 | 说明 |
|---|---|---:|---|
| 核心业务流程 | 开发中 | 88% | 自评、他评、复核、申诉、结果查询主链路已具备基础实现 |
| 审批与通知链路 | 已完成阶段性修复 | 90% | 近期已修复通知跳转、审批查看和组织名回填问题 |
| 指标设定模块 | BUG 修复中 | 85% | BUG-006（行内编辑保存 500）已定位并修复，待重启验证 |
| 前端页面与交互 | 持续开发中 | 82% | 主要页面已成型，仍有局部体验与编码问题 |
| 环境与启动 | 可运行 | 80% | 前后端已可启动，但需注意 JDK 17 和本机环境一致性 |
| 安全与权限 | 代码审核完成 | 60% | CODE_REVIEW 发现 6 个严重 + 9 个高危，SEC-001~005 须当天止血 |
| 集成测试与发布准备 | 未完成 | 40% | 构建已通过，仍缺少系统化回归、空库验证、权限回归和发布前检查 |

## 2. 今天刚完成什么

### 2026-06-01

- **BUG-008 修复**：考核进度查询自评完成率始终为 0%
  - 根因：`ExamProgressServiceImpl.queryProgress` 直接从月度 PERFORMANCE 考核组查指标作为分母（且无审批状态过滤），实际指标在关联的 INDICATOR_SET 考核组中。分母=0 → 完成率=0%。
  - 修复：新增 `resolveIndicatorSourceGroupIds()` 解析关联指标组，分母改为查 APPROVED 指标数；同步修复 `queryUnfilledItems`。
  - 涉及文件：`ExamProgressServiceImpl.java`
  - 验证：`mvn compile` 通过；待重启后端以"鄂能化公司业绩考核管理员-鄂能化"登录验证。

### 2026-05-26

- **BUG-007 修复**：部门绩效管理员在"指标设定进度查询"点击"详情"无权限
  - 根因：`IndicatorProgress.vue` 的"详情"按钮使用 `router.push('/exam/indicator-approval')` 跨页跳转，而该路由只授权给 `FIN_ADMIN`，`DEPT_ADMIN` 的 `allowedPaths` 不含该路径，被全局守卫 `hasPathAccess` 拦截并提示"无权访问该页面"
  - 修复：将"详情"按钮改为就地全屏弹框，复用 `/api/indicator/list`（无角色限制，按 ORG 数据范围自动收窄），结构对齐部门负责人审批弹框（描述区 + 5 段审批进度条 + 合并行的指标表），并新增"审批记录"时间线（提交、按当前状态推断的最近一次审批通过/退回 + `rejectReason`）
  - 涉及文件：`assessment-frontend/src/views/exam/IndicatorProgress.vue`
  - 已更新 `BUG_FIX_LOG.md`（BUG-007）、`progress.md`
  - 验证：`vue-tsc --noEmit` 通过；待以 `tuke01` 登录回归

### 2026-05-22

- **BUG-006 修复**：指标行内编辑保存全部 500
  - 根因：`BizIndicatorOrgMapper` 和 `BizIndicatorLeaderMapper` 的 `deleteByIndicatorId` 用 `@Select` 注解执行 UPDATE 语句，MySQL Connector/J 8.x 拒绝 executeQuery() 执行 DML → SQLException → 事务回滚 → 500
  - 修复：`@Select` → `@Update`，涉及 2 个 Mapper 文件
  - 已更新 `BUG_FIX_LOG.md`（BUG-006）
  - 待重启后端验证

- **全面代码审核输出**：`CODE_REVIEW_REPORT.md`
  - 总评：62/100，共 28 个问题（🔴×6 / 🟠×9 / 🟡×8 / 🟢×5）
  - 最严重：SEC-001 管理员后门、SEC-002/003 IDOR 越权、SEC-004/005 CORS/密钥泄露
  - 报告含：Top 10 优先修复清单、一天/一周修复清单、每个问题的具体解决方案

- **业务流程时序图**：`docs/indicator-flow-diagram.html`
  - 6 张完整时序图：行内编辑保存、新增指标、提交审批、审批流程、小类管理、页面加载
  - 已标注 BUG-006 的触发点和修复点

### 2026-05-15

- 完成菜单类别与数据范围统一模型改造：
  - `sys_menu` 新增 `menu_category`
  - 菜单类别支持 `SYSTEM/UNIT/DEPT` 多选
  - 职责分配菜单按职责类型过滤
  - 后端保存职责菜单时强制校验菜单类别
  - 用户权限分配时按职责类型强制数据范围：`SYSTEM -> ALL`、`UNIT -> UNIT`、`DEPT -> ORG`
- 新增数据库迁移：
  - `assessment-backend/src/main/resources/db/migration/V3__menu_category_scope.sql`
- 前端页面同步：
  - 功能/菜单定义页新增菜单类别多选
  - 职责定义页按职责类型加载可选菜单
  - 主菜单加载改为 `/menu/current`
- 新增专项归档文档：
  - `docs/menu-category-scope-refactor-2026-05-15.md`
- 验证结果：
  - 后端 `mvn -q -DskipTests package` 通过（需先切换 JDK 17）
  - 前端 `npm run build` 通过

### 2026-05-14

- 完成项目进度管理文档体系升级
- 新增可追溯文档：
  - `BUG_FIX_LOG.md`
  - `REQUIREMENT_CHANGE_LOG.md`
  - `SESSION_HANDOFF.md`
- 完成项目控制面文档补齐：
  - `TASK_BOARD.md`
  - `DELIVERY_PLAN.md`
  - `RISK_REGISTER.md`
  - `TEST_STRATEGY.md`
  - `RELEASE_CHECKLIST.md`
- 更新文档：
  - `progress.md`
  - `CLAUDE.md`
- 新增项目负责人测试上线冲刺清单：
  - `PM_TEST_RELEASE_SPRINT_CHECKLIST_2026-05-14.md`
- 新增项目负责人可视化看板：
  - `project-management-dashboard.html`
- 已升级项目负责人可视化看板第二版：
  - 新增执行驾驶舱、任务筛选和节点状态联动
- 已升级项目负责人可视化看板第三版：
  - 项目总览改为驾驶舱流程视角
  - 文档中心改为中文名称 + 设计化文档查看器
- 新增项目文档查看器：
  - `project-doc-viewer.html`
- 已升级项目文档查看器第二版：
  - 优先自动读取本地 Markdown
  - 失败时自动回退为结构化摘要视图
- 已升级项目管理看板第五版：
  - 优先自动读取项目总览、任务看板、风险台账
  - 读取失败时自动回退为静态兜底数据
- 已在本地配置项目专用 Agent Profiles：
  - 位于 `.claude/agents/`
  - 覆盖项目管理、前端、后端、测试、代码审查、发布 6 类角色

## 3. 当前正在做什么

- 正在进行：BUG-006 修复完成，待重启后端验证；代码审核推进中
- 当前原则：
  - 优先验证 BUG-006 修复（行内编辑保存）
  - 验证通过后按 CODE_REVIEW_REPORT.md 推进 SEC-001~005 止血
  - 每次任务结束必须记录进度
  - Bug 修复和需求调整必须单独入账

## 4. 下一个任务是什么

- **紧急**：重启后端，以 tuke01 验证 BUG-006 修复（行内编辑保存）
- **当天必修**：按 CODE_REVIEW_REPORT.md 修复 SEC-001 ~ SEC-005（管理员后门、IDOR 越权、CORS/密钥泄露）
- **一周内**：N+1 查询优化、前端安全加固
- 后续：按 TASK_BOARD.md、DELIVERY_PLAN.md 推进交付收口

## 5. 当前待办列表

| 编号 | 任务 | 类型 | 优先级 | 状态 | 备注 |
|---|---|---|---|---|---|
| T-016 | BUG-006：指标行内编辑保存全部 500 | Bug Fix | P0 | 已修复待验证 | `@Select`→`@Update`，涉及 2 个 Mapper |
| T-017 | 输出全面代码审核报告 | Audit | P0 | 已完成 | `CODE_REVIEW_REPORT.md`，28 个问题 |
| T-018 | 输出业绩指标设定业务流程时序图 | Doc | P0 | 已完成 | `docs/indicator-flow-diagram.html`，6 张图 |
| T-019 | 按审核报告修复 SEC-001~005 | Security | P0 | 待开始 | 管理员后门、IDOR、CORS、密钥泄露 |
| T-020 | BUG-008：考核进度查询自评完成率 0% | Bug Fix | P0 | 已修复待验证 | 分母查错组+未过滤审批状态 |
| T-001 | 继续完成当前业务需求 | Feature | P0 | 进行中 | 具体内容按会话推进 |
| T-002 | 保持文档同步更新 | Process | P0 | 进行中 | 每次任务结束后执行 |
| T-015 | 菜单权限模型角色回归 | Test | P0 | 待验证 | 验证 ADMIN/UNIT/DEPT 职责菜单与数据范围 |
| T-003 | 按审核报告推进 P0/P1 问题 | Tech Debt | P1 | 待开始 | 参考 `CODE_REVIEW_REPORT.md` |

## 6. 当前已知风险

- **安全红线**：CODE_REVIEW_REPORT 中 SEC-001（管理员后门）至 SEC-005（密钥硬编码）均为严重级别，未修复前有生产事故风险
- 本机 Maven 默认 Java 环境是 JDK 8，执行后端构建前必须使用 `mvn17.cmd`
- 工作区存在未提交改动，后续变更需避免误覆盖
- 代码与文档存在局部乱码，影响长期维护
- 权限模型、数据范围、状态机和评分闭环仍是上线前红线风险

## 7. 重启后如何快速接手

重启或重新进入项目后，先看：

1. `SESSION_HANDOFF.md`
2. `PROJECT_PROGRESS.md`
3. `progress.md`
4. `BUG_FIX_LOG.md`（重点看 BUG-006）
5. `CODE_REVIEW_REPORT.md`（重点看 SEC-001~005）
6. `docs/indicator-flow-diagram.html`（业务流程全貌）

这样可以最快知道：

- 当前做到哪
- 最近刚完成什么
- 下一步要完成什么
- 安全问题还剩哪些没修
