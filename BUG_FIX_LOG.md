# Bug 修复台账

用途：记录“修复了哪些 Bug、原因是什么、方案是什么、如何验证”。  
维护要求：只要本次任务包含 Bug 修复，就必须新增一条记录。

---

## Bug 记录模板

### BUG-XXX: [Bug 标题]

- 日期：YYYY-MM-DD
- 状态：待修复 / 修复中 / 已修复 / 已验证
- 优先级：P0 / P1 / P2
- 来源：用户反馈 / 自测发现 / 代码审查 / 联调发现
- 影响范围：
- 问题现象：
- 根因分析：
- 修复方案：
- 涉及文件：
- 验证方式：
- 备注：

---

## 已记录问题

### BUG-001: 自评页面编译失败，缺少 `userStore`

- 日期：2026-05-13
- 状态：已修复
- 优先级：P0
- 来源：构建失败
- 影响范围：前端构建
- 问题现象：`SelfEvaluation.vue` 中使用了 `userStore`，但脚本中未定义，导致 `vue-tsc` 报错。
- 根因分析：模板与脚本依赖未同步。
- 修复方案：引入 `useUserStore` 并创建 `const userStore = useUserStore()`。
- 涉及文件：
  - `assessment-frontend/src/views/dept/SelfEvaluation.vue`
- 验证方式：
  - 前端构建通过

### BUG-002: 财务管理员查看指标审批详情时“考核部门”可能为空

- 日期：2026-05-13
- 状态：已修复
- 优先级：P1
- 来源：业务联调
- 影响范围：财务审批页面
- 问题现象：审批弹窗顶部“考核部门”偶发显示为空。
- 根因分析：列表行 `orgName` 为空时，弹窗没有从指标明细回填组织名称。
- 修复方案：审批弹窗加载明细后，从 `orgName/orgNameList` 自动兜底回填 `currentRow.orgName`。
- 涉及文件：
  - `assessment-frontend/src/views/exam/IndicatorApproval.vue`
- 验证方式：
  - 页面手工验证
  - 前端构建验证

---

### BUG-2026-05-27-001: 系统用户管理与数据库查询入口 500

- 日期：2026-05-27
- 状态：已修复
- 优先级：P0
- 来源：用户反馈
- 影响范围：系统用户管理、用户权限分配管理、系统运维/数据库查询
- 问题现象：
  - `tuke01` 使用“系统管理员-全部”登录后，点击“系统用户和权限分配管理”，后端返回“系统繁忙，请稍后重试。参考号: bcfcf4b1-c1cc-49fd-a5bd-0395548cef29”。
  - 点击“系统运维/数据库查询”，后端返回“系统繁忙，请稍后重试。参考号: c8359313-c8f1-4f35-b282-0a3233e9e296”。
- 根因分析：
  - 系统用户接口异常为运行中的后端进程仍使用旧 classpath，未加载新增的 `UnitScopeAccess` 类，重启并加载新编译产物即可恢复。
  - 数据库查询接口异常为 dev 配置中 `app.db-admin.enabled` 默认值被改为 `false`，导致 `DatabaseAdminController` 未注册，请求被当作静态资源处理后进入全局异常。
- 修复方案：
  - 保持 `UnitScopeAccess` 编译产物并重启后端服务。
  - 将 dev 环境 `app.db-admin.enabled` 默认值恢复为 `true`，仍允许通过 `APP_DB_ADMIN_ENABLED=false` 手动关闭。
- 涉及文件：
  - `assessment-backend/src/main/resources/application-dev.yml`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/security/UnitScopeAccess.java`
- 验证方式：
  - `mvn test`
  - 登录后访问 `/api/sys-user/list`
  - 登录后访问 `/api/db-admin/tables`

### BUG-2026-05-27-002: 登录页测试账号快捷填充被移除

- 日期：2026-05-27
- 状态：已修复
- 优先级：P1
- 来源：用户反馈
- 影响范围：登录页测试体验
- 问题现象：测试阶段需要保留默认测试账号展示和点击自动填充，但登录页被清理后缺少该区域。
- 根因分析：此前安全整改时移除了测试账号入口，但当前项目仍处于测试阶段，用户明确要求保留。
- 修复方案：恢复登录页“测试账号”区域，展示 `admin / 123456` 与 `tuke01 / 123456`，点击后自动填充用户名和密码。
- 涉及文件：
  - `assessment-frontend/src/views/login/LoginPage.vue`
- 验证方式：
  - `npm run build`
  - 打开登录页，点击测试账号后确认表单自动填充。

### BUG-003: 指标设定进度查询“详情”跳错页面且部分信息缺失

- 日期：2026-05-13
- 状态：已修复
- 优先级：P1
- 来源：业务联调
- 影响范围：财务管理员查看详情
- 问题现象：点击“详情”会进入无权限页面，且组织名称信息不完整。
- 根因分析：
  - 详情按钮错误跳转到仅 `DEPT_ADMIN` 可访问的页面
  - 跳转参数未携带完整上下文
- 修复方案：
  - 跳转到 `exam/indicator-approval`
  - 增加 `mode=view` 和必要上下文字段
  - 查看模式下隐藏审批按钮
- 涉及文件：
  - `assessment-frontend/src/views/exam/IndicatorProgress.vue`
  - `assessment-frontend/src/views/exam/IndicatorApproval.vue`
- 验证方式：
  - 页面手工验证
  - 前端构建验证

### BUG-004: 通知点击后无法直达目标审批对象

- 日期：2026-05-13
- 状态：已修复
- 优先级：P1
- 来源：业务联调
- 影响范围：通知中心到审批页面链路
- 问题现象：点击“去审批”后只进入列表，不能自动定位到目标记录。
- 根因分析：通知链接只指向列表路由，没有带定位参数。
- 修复方案：
  - 后端生成带 `mode=approve&examGroupId&orgId` 的链接
  - 前端读取 query 后自动打开对应审批弹窗
- 涉及文件：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizIndicatorDefinitionServiceImpl.java`
  - `assessment-frontend/src/views/exam/IndicatorApproval.vue`
  - `assessment-frontend/src/views/leader/IndicatorApprove.vue`
- 验证方式：
  - 通知链路手工验证

### BUG-005: 指标设定数据缺少 `orgName` 历史回填

- 日期：2026-05-13
- 状态：已修复
- 优先级：P1
- 来源：数据检查
- 影响范围：指标详情、进度查询、审批展示
- 问题现象：部分历史数据 `org_name` 为空。
- 根因分析：创建和更新指标时只写入 `orgId`，未同步写入 `orgName`。
- 修复方案：
  - 保存时根据 `orgId` 自动补全 `orgName`
  - 增加启动时的数据修复逻辑处理历史记录
- 涉及文件：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizIndicatorDefinitionServiceImpl.java`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/config/DataFixRunner.java`
- 验证方式：
  - 启动日志检查
  - 页面展示检查

---

### BUG-006: 业绩指标行内编辑保存全部失败（@Select 跑 UPDATE）

- 日期：2026-05-21
- 状态：已修复
- 优先级：P0
- 来源：用户反馈（tuke01 / 部门绩效管理员-图克分公司）
- 影响范围：所有用户编辑已有指标行（任意字段：序号 / 内容 / 权重 / 标准 / 备注 / 部门 / 分管领导）→ 点击行内保存全部 500。新增（POST）不受影响。
- 问题现象：业绩指标设定弹框 → 行内编辑 → 保存 → "系统繁忙，请稍后重试。参考号: xxxx"
- 根因分析：
  - `BizIndicatorDefinitionServiceImpl.updateIndicator()` 在更新主表后，调用 `indicatorOrgMapper.deleteByIndicatorId()` 与 `indicatorLeaderMapper.deleteByIndicatorId()`，先删后增关联。
  - 这两个方法的 SQL 是 `UPDATE ... SET deleted = 1 ...`（软删除），却被错误地标注为 `@Select`。
  - MyBatis 据此构造 `SqlCommandType.SELECT`，调用 JDBC `executeQuery()`；MySQL Connector/J 8.x 严格禁止 `executeQuery()` 执行 DML，抛 `SQLException: Can not issue data manipulation statements with executeQuery()`。
  - 整个事务回滚 → 全局异常处理器返回 500 → 前端 axios 拦截器 `ElMessage.error` 弹错。
- 修复方案：将两处 `@Select` 改为 `@Update`，并补充 `org.apache.ibatis.annotations.Update` 的导入。
- 涉及文件：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/mapper/BizIndicatorOrgMapper.java`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/mapper/BizIndicatorLeaderMapper.java`
- 验证方式：
  - 后端用 `mvn17.cmd spring-boot:run -Dspring-boot.run.profiles=dev` 重启
  - 以 tuke01 登录 → /dept/indicator-set → 设定目标 → 经营指标 / 标准成本 → 行内编辑序号 → 保存，预期"保存成功"
  - 同样验证编辑内容 / 权重 / 考核标准 / 备注 / 考核部门多选 / 分管领导多选，全部应保存成功
  - 提交审批流程一次走通，确认关联表数据正确（biz_indicator_org / biz_indicator_leader 旧记录 deleted=1、新记录 deleted=0）
- 备注：这是一处典型的"注解类型与 SQL 类型不匹配"问题，建议在 CI 增加静态扫描规则：`@Select` 内不得出现 `UPDATE|DELETE|INSERT` 关键字。

### BUG-007: 部门绩效管理员在"指标设定进度查询"点击详情提示无权限

- 日期：2026-05-26
- 状态：已修复
- 优先级：P1
- 来源：用户反馈（tuke01 / 部门绩效管理员-党建工作部）
- 影响范围：所有 `DEPT_ADMIN`（部门绩效管理员）以及任何未授权 `/exam/indicator-approval` 菜单的职责，进入"指标设定进度查询"后均无法查看自己部门的指标详情；通过 `router.push` 跳转，被全局守卫直接拦截到 `/dashboard`。
- 问题现象：以 `tuke01` 登录 → 菜单"指标设定进度查询" → 在结果列点击"详情" → 弹"无权访问该页面"，回退到首页。
- 根因分析：
  - `IndicatorProgress.vue` 的 `handleViewDetail` 是 `router.push('/exam/indicator-approval', { query: { mode: 'view', ... } })`。
  - `/exam/indicator-approval` 在 `router/index.ts:30` 配置 `roles: ['FIN_ADMIN']`，对应菜单只授权给财务处管理员；`DEPT_ADMIN` 的 `allowedPaths` 白名单不含该路径。
  - 全局 `router.beforeEach` 用 `userStore.hasPathAccess(to.path)` 判断，命中即跳走。
  - 业务侧本意是：部门绩效管理员应当能查看自己部门设定的业绩指标（与部门负责人审批弹框相同的视图 + 审批记录），不应该跨页跳转到财务处的审批列表。
- 修复方案：
  - 将"详情"按钮从路由跳转改为弹框就地展示，避免依赖财务处审批菜单的访问权限。
  - 弹框直接复用 `/api/indicator/list`（无 `@RequireRole` 限制，且后端 `DataScopeFilter` 会按 ORG 数据范围自动收窄到当前部门），获取该部门在该考核组下的完整指标数据。
  - 弹框结构对齐部门负责人审批弹框：考核组/部门/状态/提交人/提交时间描述区、五段审批进度条、合并大类/小类/考核标准的指标表格，并额外新增"审批记录"时间线（提交、按当前状态推断的上一次审批通过/退回，含 `rejectReason`）。
  - 全程只读，无审批按钮。
- 涉及文件：
  - `assessment-frontend/src/views/exam/IndicatorProgress.vue`
- 验证方式：
  - 前端 `vue-tsc --noEmit` 通过。
  - 以 `tuke01` 登录 → 指标设定进度查询 → 选择考核组 → 在自己部门的行点击"详情" → 弹框打开，能看到完整业绩指标表与审批进度/记录，不再提示无权限。
  - 以 `FIN_ADMIN` 登录同页，行为保持一致（弹框就地展示），不影响原"业绩指标审批"主菜单功能。
- 备注：当前数据模型只在 `biz_indicator_definition` 上保留最新一次 `approvedBy/approvedTime/rejectReason`，无独立审批流水表，"审批记录"按当前状态推断展示；如需完整多级流水，后续应建审批日志表。

### BUG-008: 考核进度查询自评完成率始终为 0%

- 日期：2026-06-01
- 状态：已修复
- 优先级：P0
- 来源：用户反馈
- 影响范围：考核进度查询页面所有部门的自评完成率、他评完成率、"查看未完成"弹框
- 问题现象：以"鄂能化公司业绩考核管理员-鄂能化"登录 → 考核进度查询 → 2026年5月业绩考核 → 党建工作部自评已完成并提交，但自评完成率仍显示 0%。
- 根因分析：进度查询计算分母时直接从月度 PERFORMANCE 考核组的 `biz_indicator_definition` 查询指标数，且未过滤 `approvalStatus`。而实际指标数据存放在关联的 INDICATOR_SET（业绩指标设定）考核组中，且只应统计 `APPROVED` 的指标。两个错误叠加导致分母=0，完成率恒为 0%。
  - `ExamProgressServiceImpl.queryProgress` 第 59-62 行：`eq(BizIndicatorDefinition::getExamGroupId, examGroupId)` 直接用月度组ID查指标，应改为解析关联的指标设定组ID。
  - `ExamProgressServiceImpl.queryUnfilledItems` 第 111-114 行：同样查的是月度组的指标。
- 修复方案：
  - 注入 `BizExamGroupMapper`，新增 `resolveIndicatorSourceGroupIds()` 方法（逻辑与 `BizSelfEvaluationServiceImpl.resolveAnnualIndicatorSourceGroupIds` 一致：根据月度组的年度和单位找到同年同单位的 INDICATOR_SET 考核组）。
  - `queryProgress` 分母改为：从指标来源组中查 `approvalStatus = "APPROVED"` 的指标数。
  - `queryUnfilledItems` 同样改为从指标来源组查已审批通过的指标。
  - 分子（自评/他评完成数）保持不变，仍按月度 examGroupId 查询，因为自评/他评记录挂的是月度组。
- 涉及文件：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamProgressServiceImpl.java`
- 验证方式：
  - 后端 `mvn compile` 通过。
  - 以"鄂能化公司业绩考核管理员-鄂能化"登录 → 考核进度查询 → 2026年5月业绩考核 → 党建工作部的自评完成率应为 100%。
  - "查看未完成"弹框应正确显示实际未完成的指标列表。
