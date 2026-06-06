# QODER 全栈代码扫描报告

- 扫描日期：2026-06-02
- 扫描方式：3 个独立 agent 分片只读扫描，主 agent 合并、去重并抽查关键证据
- 核心范围：`assessment-backend`、`assessment-frontend`、数据库脚本、构建与部署配置、测试覆盖、版本控制卫生
- 工作区说明：扫描时仓库存在大量未提交更新。统计为 108 个已修改文件、2 个删除文件、111 个未跟踪条目。所有结论均以当前工作区实际内容为准。
- 代码修改：未修改业务源码、配置、SQL 或测试；本报告是唯一新增文件。

## 1. 执行摘要

本轮共确认 25 个可行动项：

| 严重度 | 数量 | 说明 |
| --- | ---: | --- |
| P0 | 4 | 可造成提权、跨组织篡改或凭据暴露，建议作为发布阻断项 |
| P1 | 14 | 影响鉴权、数据一致性、部署安全或数据库升级可靠性 |
| P2 | 7 | 影响稳定性、用户体验、供应链面或工程回归能力 |
| P3 | 0 | 未纳入泛泛风格建议 |

最需要优先处理的是权限边界。当前代码中，单位管理员提权、复核评分跨组织写入、组织主数据跨单位写入均可由服务端静态逻辑确认。登录页固定测试账号、默认启用开发 profile、数据库浏览器开放敏感表更新又形成了额外的组合风险。

## 2. 验证结果

| 验证命令 | 结果 |
| --- | --- |
| `npm.cmd run build` | 通过。`vue-tsc -b` 与 Vite build 成功；存在 Sass legacy API 和大 chunk 告警，不作为本轮阻断项。 |
| `npm.cmd exec vite -- build --debug` | 通过。 |
| `npm.cmd ls mysql2 --depth=0` | 确认前端直接依赖 `mysql2@3.22.4`，源码未引用。 |
| `.\mvn17.cmd test` / `mvn.cmd test` | 未完成。沙箱网络限制导致 Maven 无法解析 `spring-boot-starter-parent:3.2.5`；未得到可用的联网执行窗口。 |
| `git diff --check` 及分目录检查 | 未发现影响结论的格式错误；局部已有改动存在少量尾随空格。 |
| `git check-ignore` | 确认 `dist`、`target`、`db-config.txt` 已忽略；运行时目录、上传目录和本地修数脚本仍存在误纳管风险。 |

未启动任何长期驻留服务，因此未执行真实浏览器端到端、真实 MySQL Flyway 迁移、工作流平台联调和多实例 JWT 验证。

## 3. P0：建议立即止血

### AUD-001 单位财务管理员可提权为系统管理员

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserPermissionController.java:76`、`assessment-backend/src/main/java/com/ccerphr/assessment/security/UnitScopeAccess.java:12`
- 影响：`FIN_ADMIN` 可进入权限分配接口，并可向本单位账号提交 `roleCode=ADMIN`。系统管理员角色后续可获得全量数据范围。
- 触发：以单位财务管理员调用 `POST /api/user-permission`，为本单位用户授予 `ADMIN`。
- 修复方案：建立“可授予角色上限”校验；只有 `ADMIN` 可授予系统级角色；更新权限时必须加载数据库原记录并校验原角色、目标角色和目标范围。
- 验证方式：新增权限矩阵测试，确认 `FIN_ADMIN -> ADMIN` 的新增和更新请求均返回 403。

### AUD-002 复核评分可跨组织写入并触发汇总

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizReviewScoreController.java:36`、`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:198`
- 影响：审核角色可提交外部组织的 `orgId`、`examGroupId` 或现有记录 `id`，覆盖其他组织评分并触发汇总。
- 触发：组织 A 审核员向 `/api/review/save` 提交组织 B 的数据，再调用 `/api/review/submit`。
- 修复方案：服务层校验当前 DataScope、考核组成员关系、指标归属；更新时禁止改变记录身份字段；提交时仅处理当前访问范围内的组织。
- 验证方式：组织 A 审核员写入、更新、提交组织 B 数据时必须返回 403，数据库保持不变。

### AUD-003 组织、员工、领导写接口缺少单位边界

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysOrganizationServiceImpl.java:103`、`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysEmployeeServiceImpl.java:68`、`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysLeaderServiceImpl.java:61`
- 影响：单位级管理员可提交外部 `unitId`，或对外部实体 `id` 执行修改、删除，间接影响账号与权限同步。
- 触发：单位 A 管理员创建、更新或删除单位 B 的组织、员工、领导记录。
- 修复方案：抽取统一的单位范围校验；创建时强制覆盖为当前单位；更新和删除前加载数据库原记录校验归属；不要只在 `unitId == null` 时自动填充。
- 验证方式：针对三类实体分别增加跨单位新增、更新、删除测试，均应返回 403。

### AUD-004 登录页发布固定测试账号，初始化 SQL 写入统一弱密码账号

- 类型：已确认缺陷；生产库中账号是否仍有效需人工确认
- 证据：`assessment-frontend/src/views/login/LoginPage.vue:33`、`assessment-frontend/src/views/login/LoginPage.vue:84`、`assessment-backend/src/main/resources/db/data.sql:122`
- 影响：前端页面直接展示 `admin / 123456`、`tuke01 / 123456`；初始化 SQL 还写入多个相同密码的测试账号。
- 触发：部署当前前端，或在目标环境执行 `data.sql`。
- 修复方案：移除登录页测试账号展示；生产禁止导入测试用户；立即核查并轮换现存账号密码；演示账号必须由明确的开发环境开关控制。
- 验证方式：对生产构建产物全文搜索 `123456`；确认旧密码无法登录。

## 4. P1：建议紧随其后修复

### AUD-005 未显式指定 profile 时默认启用开发配置

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/resources/application.yml:5`、`assessment-backend/src/main/resources/application-dev.yml:7`、`assessment-backend/src/main/resources/application-dev.yml:23`、`assessment-backend/src/main/resources/application-dev.yml:27`
- 影响：遗漏 `SPRING_PROFILES_ACTIVE=prod` 时，会启用 `root/root` 数据库默认值、公开 JWT 密钥、数据库管理入口和 Swagger。
- 触发：部署命令未传 profile。
- 修复方案：无 profile 时拒绝启动，或将默认 profile 改为受控生产配置；开发默认值只保留在本地环境文件。
- 验证方式：不传 profile 启动配置校验，确认不会加载 dev 能力。

### AUD-006 数据库浏览器暴露敏感表并允许通用更新

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:57`、`:102`、`:162`，`assessment-frontend/src/views/admin/DatabaseBrowser.vue:139`
- 影响：数据库浏览器可对 `sys_user`、权限表等执行 `SELECT *`，并更新大部分字段。密码哈希和权限相关字段可能进入响应或被绕过领域服务修改。
- 触发：`app.db-admin.enabled=true` 且管理员进入数据库浏览页。
- 修复方案：默认只读；排除安全敏感表；逐表定义返回投影与可编辑字段白名单；密码字段永不返回；关键修改必须经过领域服务和审计。
- 验证方式：接口不得返回 `password`；安全表不可选；角色、密码、权限字段不可通过通用更新接口修改。

### AUD-007 自评结果信任浏览器提交值，完整性校验可被构造指标绕过

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/dto/SelfEvalSaveDTO.java:22`、`:32`，`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:199`、`:252`
- 影响：调用者可伪造 `selfResult`，并使用任意指标 ID 补足记录数量后提交完整自评。
- 触发：绕过页面手工请求 `/api/evaluation/self/save`，提交伪造结果或非当前考核组指标，再调用 `/submit`。
- 修复方案：DTO 移除可写结果字段；后端根据明细重算；校验指标属于当前考核组已审批集合；提交时比较精确指标集合；增加必要唯一约束。
- 验证方式：伪造结果、伪造指标、缺少任一合法指标时均应拒绝提交。

### AUD-008 自评、他评、领导评分写接口缺少职责角色限制

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:49`、`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizPeerEvaluationController.java:45`、`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizLeaderEvaluationController.java:53`
- 影响：前端隐藏菜单不能替代后端鉴权。非目标职责角色在满足部分关联范围条件时，仍可能直接调用评分写接口。
- 触发：普通已登录账号绕过页面直接调用保存或提交接口。
- 修复方案：为每类写接口增加明确角色矩阵，并保留数据范围校验。
- 验证方式：目标角色成功，其他角色均返回 403。

### AUD-009 停用、重置密码和登出不能可靠使 JWT 失效

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/security/JwtAuthenticationFilter.java:48`、`assessment-backend/src/main/java/com/ccerphr/assessment/security/TokenBlacklistService.java:19`、`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysUserServiceImpl.java:125`、`assessment-frontend/src/layouts/MainLayout.vue:172`
- 影响：黑名单仅保存在单实例内存；服务重启或多实例下失效。停用和重置密码后，旧 JWT 仍可能继续调用接口；前端 UI 登出也未可靠调用后端撤销。
- 触发：保存旧 JWT，执行登出、停用账号、重置密码、服务重启或切换实例后继续请求。
- 修复方案：增加令牌版本或账号状态校验；账号安全变更时递增版本；使用 Redis 持久化 `jti` 黑名单并设置 TTL；前端登出在 `finally` 中清理本地状态。
- 验证方式：旧 JWT 在登出、停用、重置、重启及跨实例后均返回 401。

### AUD-010 工作流接口缺少授权，外部工作流身份按角色共享

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/controller/WorkflowController.java:58`、`:89`、`:103`、`:126`，`assessment-backend/src/main/java/com/ccerphr/assessment/service/WorkflowIntegrationService.java:44`、`:155`
- 影响：普通已登录用户可调用初始化、启动、审批或驳回代理接口；同角色用户共享外部工作流身份，可能读取或审批其他组织任务。
- 触发：普通用户调用 `/api/workflow/init`、`/start-indicator`；组织 A 用户提交组织 B 的 `nodeInstanceId`。
- 修复方案：为初始化、启动、审批、驳回建立角色权限；审批前验证节点属于当前真实用户和业务对象；外部审批人映射至少包含真实用户与组织维度。
- 验证方式：无权限角色返回 403；伪造节点 ID 和跨组织任务均被拒绝。

### AUD-011 工作流失败时业务状态与工作流状态可能分叉

- 类型：需联调确认风险
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizIndicatorDefinitionController.java:93`、`assessment-frontend/src/views/exam/IndicatorApproval.vue:315`、`:767`
- 影响：工作流平台异常时，业务状态仍可能推进或页面显示成功，形成不可追踪的不一致。
- 触发：在提交、审批或驳回过程中模拟工作流超时、500 或断网。
- 修复方案：由后端统一编排状态变化；使用事务回滚、outbox 或可观测待重试状态；前端不得吞掉工作流异常后提示成功。
- 验证方式：模拟外部工作流失败，业务状态不得静默推进。

### AUD-012 结果、复核摘要与进度接口存在跨范围读取

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:173`、`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:165`、`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamProgressServiceImpl.java:280`
- 影响：低权限用户可传入外部 `examGroupId` 或 `orgId`，读取其他组织结果、复核摘要或未完成项。
- 触发：组织 A 用户查询组织 B 或其他单位参数。
- 修复方案：查询和导出前统一校验考核组、组织可访问性；底层查询附加 DataScope 条件。
- 验证方式：跨组织读取返回 403 或空结果；导出接口同样受限。

### AUD-013 申诉复评访问条件阻断合法评分方

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizAppealServiceImpl.java:152`、`:165`、`:239`
- 影响：复评组织 B 获取待处理列表后，仍可能因访问判断只认可发起组织而无法处理组织 A 的申诉。
- 触发：组织 A 发起申诉，指定组织 B 复评；B 查询并提交复评分。
- 修复方案：按操作拆分权限：发起方管理申诉，评分方按 `scorerOrgId` 处理复评，无关组织禁止访问。
- 验证方式：B 可处理 A 指派的申诉，组织 C 返回 403。

### AUD-014 历史复核补录会写入执行月份

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:342`、`:365`
- 影响：补录历史考核时使用 `LocalDate.now()` 生成月份，可能污染当前月份统计。
- 触发：在当前月重新提交历史考核组。
- 修复方案：从考核组固定周期字段计算统计月份，不得使用执行时钟。
- 验证方式：补录历史考核后，仅历史月份数据发生变化。

### AUD-015 预发布没有阻断未完成考核

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizExamGroupServiceImpl.java:477`
- 影响：缺少自评、互评或复核数据时仍可进入预发布，并继续发布结果。
- 触发：对未完成考核组调用 `/api/exam-group/{id}/pre-publish`。
- 修复方案：预发布事务内调用完整性校验；如确需人工放行，增加带原因和审计记录的显式覆盖流程。
- 验证方式：分别缺少一项自评、互评、复核时均应阻断。

### AUD-016 Flyway 缺少创建基础表的 V1 基线

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/resources/application.yml:11`、`assessment-backend/src/main/resources/db/migration/README.md:7`、`assessment-backend/src/main/resources/db/migration/V2__schema_sync.sql:54`
- 影响：迁移目录只有 V2 至 V6。空库执行迁移时，V2 会修改尚未创建的基础表。
- 触发：全新环境初始化数据库。
- 修复方案：建立受控 `V1__baseline.sql`，统一基础 schema 与必要种子数据。
- 验证方式：对临时空 MySQL 库执行 `flyway migrate`。

### AUD-017 数据库升级脚本存在重复来源和漂移风险

- 类型：需人工确认升级流程
- 证据：`assessment-backend/src/main/resources/db/V2__schema_sync.sql:1`、`assessment-backend/src/main/resources/db/migration/V2__schema_sync.sql:1`、`assessment-backend/src/main/resources/db/migration_add_notification_fields.sql:4`、`assessment-backend/src/main/resources/db/schema.sql:421`
- 影响：存在内容不同的同名 V2 脚本，通知字段也有手工脚本与主 schema 重叠。误执行可能导致 schema 漂移、重复索引或升级失败。
- 触发：升级时混用手工 SQL 和 Flyway。
- 修复方案：只保留 `db/migration` 为唯一升级来源；归档未纳管脚本；记录历史库到当前版本的升级路径。
- 验证方式：分别对历史库快照和空库执行 Flyway validate、migrate。

### AUD-018 本地运行时目录、上传目录和修数脚本未完整忽略

- 类型：已确认缺陷
- 证据：`.gitignore:33`、`fix_password.sql:1`、`fix_password_fengyy.sql:1`
- 影响：`.runtime`、`mysql-sandbox-data`、`assessment-backend/uploads`、`.opencode/tmp`、注册表备份和密码重置脚本存在误纳管风险，其中可能包含私钥、证书、数据库文件和敏感 SQL。
- 触发：误执行 `git add .`。
- 修复方案：补充忽略规则；将本地修数脚本移出仓库或脱敏模板化；如曾外发则轮换相关密钥。
- 验证方式：逐项执行 `git check-ignore -v`，确认全部命中规则。

## 5. P2：安排在安全边界稳定后处理

### AUD-019 登录限流信任任意代理头，状态存储不适合生产

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:129`、`assessment-backend/src/main/java/com/ccerphr/assessment/security/LoginRateLimiter.java:26`
- 影响：攻击者可轮换 `X-Forwarded-For` 绕过 IP 限流；随机用户名使内存键增长；多实例不共享状态。
- 触发：携带不同代理头和随机用户名持续请求登录。
- 修复方案：只信任已配置反向代理；使用 Redis 限流；增加 TTL、容量上限和监控。
- 验证方式：非可信来源代理头被忽略；跨实例累计失败仍触发限流；过期键自动回收。

### AUD-020 结果摘要遇到空月度权重可能抛出 NPE

- 类型：已确认缺陷
- 证据：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:209`、`assessment-backend/src/main/resources/db/schema.sql:177`
- 影响：`weight_monthly=NULL` 时，结果摘要或导出可能在 `Collectors.toMap` 阶段返回 500。
- 触发：查询包含空月度权重指标的结果。
- 修复方案：创建指标时校验必填；汇总时按业务规则拒绝或处理空值。
- 验证方式：构造空权重指标，摘要和导出接口不得抛出未处理异常。

### AUD-021 自评附件下载使用裸链接，无法携带 Bearer token

- 类型：已确认缺陷
- 证据：`assessment-frontend/src/views/dept/SelfEvaluation.vue:222`、`:1119`、`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:87`
- 影响：`<a href>` 导航不会附加 axios 的 Authorization 头，附件下载可能被鉴权拦截。
- 触发：登录后点击自评附件链接。
- 修复方案：使用封装 HTTP 客户端下载 Blob，或签发短时下载 URL。
- 验证方式：登录后可下载；无 token 请求仍被拒绝。

### AUD-022 前端菜单权限缓存可残留或被本地篡改

- 类型：已确认缺陷；页面越权影响受后端接口鉴权强度约束
- 证据：`assessment-frontend/src/stores/user.ts:49`、`:224`、`assessment-frontend/src/router/index.ts:72`、`assessment-frontend/src/api/request.ts:62`、`assessment-backend/src/main/java/com/ccerphr/assessment/common/GlobalExceptionHandler.java:45`
- 影响：路由守卫信任 `localStorage.allowedPaths`；401 清理未统一走 store，业务异常也常以业务码响应。切换账号或撤权后可能保留旧菜单和页面入口。
- 触发：篡改本地缓存、撤销权限或切换账号后访问旧页面。
- 修复方案：登录、角色切换和 401 时统一刷新或清空权限缓存；路由缓存只用于展示；后端仍必须独立鉴权。
- 验证方式：修改本地缓存不得获得接口能力；撤权后刷新立即失效。

### AUD-023 领导管理查询参数被重复包装

- 类型：已确认缺陷
- 证据：`assessment-frontend/src/api/admin.ts:23`、`assessment-frontend/src/api/request.ts:82`
- 影响：请求可能形成嵌套参数而非后端期望的 `unitId`，单位筛选失效并返回过宽数据。
- 触发：领导管理页按单位筛选人员。
- 修复方案：调用改为 `http.get('/leader/employees', { unitId })`。
- 验证方式：网络请求应为 `?unitId=<id>`，响应仅包含目标单位人员。

### AUD-024 浏览器前端误引入未使用的 Node 数据库驱动

- 类型：已确认缺陷
- 证据：`assessment-frontend/package.json:15`、`assessment-frontend/package-lock.json:2142`
- 影响：`mysql2` 未被源码使用，却扩大供应链面并制造 lockfile 噪声。
- 触发：安装前端依赖。
- 修复方案：移除 `mysql2` 并重新生成 lockfile。
- 验证方式：`npm ls mysql2 --depth=0` 不再列出依赖；`npm run build` 通过。

### AUD-025 高风险链路缺少自动化测试

- 类型：已确认缺陷
- 证据：`assessment-backend/src/test/java/com/ccerphr/assessment/common/GlobalExceptionHandlerTest.java:12`、`assessment-frontend/package.json:6`
- 影响：后端大量业务源码仅有少量测试文件，前端没有测试脚本。权限、迁移、生产 profile、数据库浏览器等高风险路径缺少回归保护。
- 触发：权限、配置、迁移或核心流程改动。
- 修复方案：先补权限矩阵、跨单位访问、空库/历史库 Flyway、prod profile、JWT 撤销、数据库管理权限测试；再补前端关键流程测试。
- 验证方式：将后端测试、前端单测和关键 E2E 纳入 CI。

## 6. 已检查但未发现新增缺陷的重点模块

- `SecurityConfig` 与 CORS：未确认新的匿名放行或通配跨域缺陷。
- `DatabaseAdmin` SQL 构造：表名存在白名单，值使用参数绑定；未确认直接 SQL 注入入口。风险集中在敏感表和敏感字段开放过宽。
- MyBatis 参数绑定与动态查询：未确认新的直接 SQL 注入入口。
- 生产配置：`application-prod.yml` 中数据库与 JWT 使用环境变量，Swagger 和数据库管理默认关闭。
- DataScope 基础角色匹配：精确匹配逻辑本身未发现新增缺陷；主要问题是部分业务服务未调用范围校验。
- 考核组写接口：已存在 `UnitScopeAccess` 范围校验。
- 前端源码：未发现 `v-html`、`eval` 或实际导入 `mysql2`。
- 构建配置：JDK 17 enforcer 配置合理；前端 TypeScript 构建通过。

## 7. 推荐修复批次

### 第一批：立即止血，建议先修

1. `AUD-001` 单位管理员提权。
2. `AUD-002` 复核评分跨组织写入。
3. `AUD-003` 组织主数据跨单位写入。
4. `AUD-004` 固定测试账号与弱密码展示。
5. `AUD-005` 默认 dev profile。
6. `AUD-006` 数据库浏览器敏感表与通用更新。
7. `AUD-018` 敏感本地文件误纳管风险。

依赖关系：先封堵权限与入口，再做密码轮换和仓库清理。否则清理期间仍可能通过现有入口重新扩大权限。

### 第二批：业务正确性与联调

1. `AUD-007` 至 `AUD-015`：自评、职责角色、JWT、工作流、跨范围读取、申诉、历史月份、预发布完整性。
2. `AUD-016`、`AUD-017`：数据库迁移统一。必须在独立 MySQL 快照上验证，不能直接在正式库试跑。
3. `AUD-021`、`AUD-023`：前端附件和领导筛选接口修复。

依赖关系：工作流修复需要后端授权、真实用户映射和状态一致性方案一起设计；数据库迁移需要先确定现网基线。

### 第三批：工程治理

1. `AUD-019` 登录限流生产化。
2. `AUD-020` 空权重稳定性处理。
3. `AUD-022` 前端缓存收敛。
4. `AUD-024` 移除无关依赖。
5. `AUD-025` 建立自动化回归。

## 8. 建议决策方式

建议先确认第一批 7 项是否全部进入修复范围。若要进一步压缩首轮工作量，最低限度也应先处理 `AUD-001` 至 `AUD-006`，并在修复后进行一次角色矩阵回归测试。
