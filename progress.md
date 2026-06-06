# 项目进度记录

最后更新：2026-05-26  
当前维护人：Claude

## 当前状态

- 项目阶段：考核进度查询 BUG 修复 + 菜单权限模型整改
- 总体进度：约 86%
- 当前重点：考核进度查询自评完成率 0% 问题修复
- 当前分支状态：工作区存在未提交改动

## 当前任务

- 任务名称：菜单类别与数据范围统一模型改造
- 状态：已完成
- 完成日期：2026-05-15
- 产出：
  - `sys_menu.menu_category` 菜单类别字段
  - 菜单类别支持 `SYSTEM/UNIT/DEPT` 多选
  - 职责分配菜单按职责类型过滤
  - 后端保存职责菜单时强制校验菜单类别
  - 用户权限分配按职责类型强制数据范围
  - 新增专项归档：`docs/menu-category-scope-refactor-2026-05-15.md`

## 已完成任务记录

### 2026-06-01

- [x] 修复 BUG-008：考核进度查询自评完成率始终为 0%
  - 根因：`ExamProgressServiceImpl.queryProgress` 直接从月度 PERFORMANCE 考核组查指标作为分母，且未过滤审批状态。实际指标在关联的 INDICATOR_SET 考核组中，且应只计 APPROVED 状态。导致分母=0，完成率恒为 0%。
  - 修复：注入 `BizExamGroupMapper`，新增 `resolveIndicatorSourceGroupIds()` 方法，分母改为从指标设定组查 APPROVED 指标数。同步修复 `queryUnfilledItems`。
  - 涉及文件：`ExamProgressServiceImpl.java`
  - 验证：`mvn compile` 通过；待以"鄂能化公司业绩考核管理员-鄂能化"登录验证。

### 2026-05-26

- [x] 修复 BUG-007：部门绩效管理员在"指标设定进度查询"点击详情提示无权限
  - 根因：`IndicatorProgress.vue` 的"详情"按钮 `router.push('/exam/indicator-approval')`，该路由仅授权 `FIN_ADMIN`，`DEPT_ADMIN` 的 `allowedPaths` 不含该路径，被全局守卫拦截。
  - 修复：将"详情"改为就地弹框（fullscreen Dialog），复用 `/api/indicator/list`（按 ORG 数据范围自动过滤）拉取数据；弹框沿用部门负责人审批弹框结构（描述区 + 5 段审批进度条 + 合并行的指标表），并新增"审批记录"时间线（提交、按当前状态推断的最近一次审批通过/退回，含 `rejectReason`）。
  - 涉及文件：`assessment-frontend/src/views/exam/IndicatorProgress.vue`
  - 验证：`vue-tsc --noEmit` 通过；待以 `tuke01` 登录回归详情弹框。

### 2026-05-15

- [x] 完成菜单类别与数据范围统一模型改造
  - 目的：用统一模型管理系统、单位、部门三类菜单，并让职责菜单配置和数据范围控制一致
  - 结果：前后端、数据库迁移、初始化数据和后端校验均已落地
  - 验证：后端 `mvn -q -DskipTests package` 通过，前端 `npm run build` 通过
  - 归档：`docs/menu-category-scope-refactor-2026-05-15.md`

### 2026-05-14

- [x] 完成 P0/P1 安全与功能问题修复（共 9 项）
  - 目的：修复代码审查发现的严重安全隐患和功能缺陷
  - 结果：后端编译通过，变更已记录到 CHANGELOG.md
  - 验证：`mvn compile` 成功

- [x] 完成项目进度管理文档体系设计与落地
  - 目的：支持每次任务完成后及时记录“已完成内容 + 下一个任务”
  - 结果：建立总览、进度、Bug、需求、交接、任务、交付、风险、测试、发布、规范等治理文档
  - 验证：文档文件已创建并形成可追溯结构

- [x] 输出项目负责人测试上线冲刺清单
  - 目的：为项目负责人提供可执行的测试收口、上线推进和日常检查基线
  - 结果：新增 `PM_TEST_RELEASE_SPRINT_CHECKLIST_2026-05-14.md`
  - 验证：清单已覆盖阶段判断、重点事务、3 天/1 周/收口/上线前检查项

- [x] 输出项目负责人项目管理看板 HTML
  - 目的：将项目阶段、总体进度、流程节点、任务、规范、文档和功能入口可视化集中展示
  - 结果：新增 `project-management-dashboard.html`
  - 验证：页面支持总览查看、流程节点切换、文档打开和原型入口跳转

- [x] 升级项目负责人项目管理看板第二版
  - 目的：让看板从“总览页”升级为可执行的项目负责人驾驶舱
  - 结果：增加执行驾驶舱、任务筛选、今日事项、本周事项、阻断事项和节点状态联动
  - 验证：页面支持筛选任务、点击节点查看详情、按分页切换不同控制视图

- [x] 升级项目负责人项目管理看板第三版
  - 目的：强化项目总览的驾驶舱视角，并优化文档查看体验
  - 结果：总览改为流程驾驶舱，文档中心改为中文名称，并新增设计化文档查看器 `project-doc-viewer.html`
  - 验证：页面支持查看当前节点、下一节点、阻断点，并可从文档中心进入设计化文档页

- [x] 升级项目文档查看器第二版
  - 目的：让文档查看器优先展示真实 Markdown 内容，而不是只看预置摘要
  - 结果：增加本地 Markdown 自动读取、轻量渲染和失败降级逻辑
  - 验证：页面在可读取时展示设计化 Markdown，在不可读取时回退到结构化摘要并保留原文入口

- [x] 升级项目管理看板第五版
  - 目的：让项目总览、任务和风险优先使用真实治理文档数据，减少手工维护
  - 结果：增加对 `PROJECT_PROGRESS.md`、`TASK_BOARD.md`、`RISK_REGISTER.md` 的自动读取与轻量解析，并保留静态兜底
  - 验证：页面显示自动读取状态，任务区与风险区可使用本地文档数据驱动

- [x] 在本地配置 6 个项目专用 Agent Profiles
  - 目的：为后续多 Agent 协作建立可复用、边界清晰、面向高质量交付的本地配置
  - 结果：在 `.claude/agents/` 下新增 6 个角色 Profile 和总入口 README
  - 验证：配置文件已落地，包含职责、边界、默认文档、产出格式、验收标准和升级规则

### 2026-05-13

- [x] 修复指标设定进度查询中组织名称为空的问题
- [x] 修复指标审批/查看页面的只读查看链路
- [x] 修复通知跳转定位审批对象的问题
- [x] 优化审批流程节点显示
- [x] 修复 `SelfEvaluation.vue` 编译问题

### 2026-05-11

- [x] 输出代码审查报告
- [x] 修复前端构建报错

## 下一个任务

1. 启动后端，确认 Flyway `V3__menu_category_scope.sql` 已在真实数据库执行成功。
2. 启动前端，完成角色菜单与数据范围回归。
3. 重点验证：
   - `ADMIN` 可配置全部菜单，数据范围为全部
   - 单位职责只可选单位类菜单，数据范围为单位
   - 部门职责只可选部门类菜单，数据范围为组织/部门
4. 继续当前业务需求开发。
5. 后续任务开始按照以下控制面文档推进：
   - `TASK_BOARD.md`
   - `DELIVERY_PLAN.md`
   - `RISK_REGISTER.md`
   - `TEST_STRATEGY.md`
   - `RELEASE_CHECKLIST.md`
   - `PM_TEST_RELEASE_SPRINT_CHECKLIST_2026-05-14.md`
   - `project-management-dashboard.html`
6. 每完成一个开发任务，同步更新以下文档：
   - `PROJECT_PROGRESS.md`
   - `BUG_FIX_LOG.md`，如果本次包含 Bug 修复
   - `REQUIREMENT_CHANGE_LOG.md`，如果本次包含新增需求或需求调整
7. 业务开发完成后，再按代码审核报告推进质量修复。
8. 后续若继续增强看板，优先考虑把本地治理文档数据自动接入看板。
9. 后续多 Agent 协作时，优先复用 `.claude/agents/` 中的本地 Profiles。

## 阻塞与风险

- 后端本机 Maven 默认 Java 环境是 JDK 8；构建前必须切换 `JAVA_HOME` 到 JDK 17
- 前后端源码和文档仍存在部分中文乱码
- 当前工作区不是干净基线，提交前需要注意变更边界
- 菜单类别迁移需要在真实数据库启动后确认执行结果

## 快速查看建议

机器重启后，按以下顺序看文档：

1. `SESSION_HANDOFF.md`
2. `PROJECT_PROGRESS.md`
3. `progress.md`
4. `BUG_FIX_LOG.md`
5. `REQUIREMENT_CHANGE_LOG.md`
