# 代码审核报告：月度业绩考核系统

**审核日期**：2026-05-20  
**审核视角**：20 年资深技术架构师  
**审核范围**：`assessment-backend`、`assessment-frontend`  
**审核方式**：静态代码审查、配置审查、数据库脚本审查、构建验证、测试覆盖扫描  
**结论级别**：Blocker，不建议进入任何正式验收或生产发布

## 一、总评：现在不是“质量不够好”，而是“工程底座不成立”

这个项目已经具备一套看得见的业务外壳：登录、角色、菜单、指标设定、审批、自评、他评、复核、申诉、结果查询。问题是，外壳下面的工程底座非常脆：后端当前无法编译，认证授权模型混乱，数据库管理能力被塞进业务系统，数据迁移与种子数据边界不清，前端依赖本地缓存塑造权限体验，测试覆盖几乎不存在。

一句话判断：这不是可以带病上线的系统，而是一个已经累积架构债、权限债、数据债的业务原型。继续往上堆功能，只会把问题从“修代码”升级成“修事故”。

## 二、发布裁决

**当前发布建议：禁止发布。**

硬性理由如下：

1. 后端 `mvn test` 编译失败，系统连最基本的构建门禁都没有通过。
2. 约 160 个后端控制器映射方法中，仅约 51 处使用 `@RequireRole`，授权覆盖明显不完整。
3. 业务系统内置数据库浏览/修改/删除接口，且允许触达 `sys_user`、`sys_role`、`sys_user_permission` 等核心安全表。
4. 登录页和数据库种子数据暴露测试账号与统一密码，安全边界形同摆设。
5. 前端 `any` 命中约 464 处，接口契约基本靠默契，不靠类型系统。
6. 后端测试文件仅 1 个，前端测试 0 个。如此关键的绩效、权限、评分系统没有测试护栏，是管理失职级别的工程风险。

## 三、验证结果

### 1. 后端构建失败

命令：`mvn test`  
结果：失败。

失败点集中在 `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizIndicatorDefinitionServiceImpl.java`，从第 401 行类已经结束，第 402 行开始又追加了一段重复的 `convertToVO` 方法及后续代码，导致 javac 报出 47 个 `需要 class、interface、enum 或 record` 语法错误。

证据：

- `BizIndicatorDefinitionServiceImpl.java:398-401`：已有 `convertToVO` 方法并关闭类。
- `BizIndicatorDefinitionServiceImpl.java:402-499`：类外又出现方法、审批逻辑、进度查询逻辑。

这是最低级、也最致命的问题。一个后端服务不能编译，任何“功能基本可用”的说法都没有工程意义。

### 2. 前端构建通过但质量告警明显

命令：`npm run build`  
结果：通过。

但存在两类告警：

- Sass legacy JS API 与 `@import` 弃用告警，位置包括 `src/assets/styles/global.scss` 与 `src/layouts/MainLayout.vue`。
- 主 chunk `assets/index-*.js` 约 1.18MB，超过 Vite 默认 500KB 警戒线，说明分包策略缺失。

前端能打包，不等于前端工程健康。当前只是“能产物”，不是“可长期维护”。

## 四、Blocker 问题

### B1. 后端源码结构已经破坏，当前版本不可构建

**位置**：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizIndicatorDefinitionServiceImpl.java:398-499`

第 401 行出现类结束符，随后第 402 行又出现重复方法和业务逻辑。这不是普通 bug，这是代码合并失控。它说明当前分支缺少最基本的 CI 编译检查，也说明人工合并后没有做一次干净构建。

**影响**：

- 后端无法编译，测试无法运行，服务不可可信部署。
- 已运行服务可能来自旧的 `target/classes`，不是当前源码产物，存在“源码与运行物不一致”的严重交付风险。
- 任何基于当前代码的功能验证都失去意义，因为验证对象可能不是当前代码。

**最优方案**：

1. 立即修复 `BizIndicatorDefinitionServiceImpl` 的类结构，删除重复的类外代码，将审批、进度查询逻辑放回类内部。
2. 在仓库根部建立强制门禁：后端 `mvn test`、前端 `npm run build` 必须在提交前或合并前通过。
3. CI 中加入 `mvn -q test` 和 `npm run build`，失败即禁止合并。
4. 禁止提交未编译验证的服务端代码。这里没有讨论空间。

### B2. 认证授权模型混用 JWT 与 HttpSession，权限上下文设计摇摆

**位置**：

- `SecurityConfig.java:33-39`
- `AuthController.java:136-209`
- `AuthController.java:235-267`
- `DataScopeInterceptor.java:44-76`
- `RoleCheckAspect.java:24-35`

系统配置了 `SessionCreationPolicy.STATELESS`，同时又在登录和切换角色时写入 `HttpSession` 的 `ACTIVE_ROLE_CODE`、`ACTIVE_SCOPE_ID`。这是一种架构自相矛盾：嘴上说无状态，身体却依赖会话。

更糟糕的是，`RoleCheckAspect` 在拿不到 `DataScopeContext` 时会从 `Authentication` 的 authorities 中取第一个角色作为兜底。用户多角色时，“当前到底是谁”这件事会随请求路径、会话状态、权限列表排序发生漂移。

**影响**：

- 横向扩容后，如果没有强会话粘滞或集中式 Session，角色切换会随机失效。
- 浏览器多标签、多设备登录时，活动角色上下文不可预测。
- 代码层无法证明某个接口执行时使用的是哪个角色与哪个数据范围。
- 审计无法还原“用户当时以哪个角色操作”。

**最优方案**：

1. 选定一种模型，不要混用。建议使用无状态 JWT + 服务端权限版本号。
2. 角色切换接口不写 Session，而是签发新的 access token，token 内包含 `activeRoleCode`、`activeScopeId`、`permissionVersion`。
3. 所有接口只从 Spring Security 的 Authentication Principal 获取当前用户、当前角色、当前数据范围。
4. 权限变更时提升 `permissionVersion`，旧 token 自动失效或要求刷新。
5. 用 `@PreAuthorize` 或统一权限注解替代散落的自定义 AOP 判断，并补充 MockMvc 权限矩阵测试。

### B3. 业务系统内置数据库管理台，这是生产事故入口

**位置**：

- `DatabaseAdminController.java:20-54`
- `DatabaseAdminServiceImpl.java:35-64`
- `DatabaseAdminServiceImpl.java:100-176`
- `V4__database_admin_menu.sql:1-19`
- `assessment-frontend/src/views/admin/DatabaseBrowser.vue`

系统提供 `/api/db-admin/tables`、`/api/db-admin/rows`、`/api/db-admin/row` 更新和删除接口。虽然表名做了白名单，但白名单中包含 `sys_user`、`sys_role`、`sys_user_permission`、`sys_role_menu`、`sys_menu` 等安全核心表。

这不是“方便管理员排查问题”，这是把数据库控制台开进了业务系统。更严重的是，`deleteRow` 使用物理删除：

- `DatabaseAdminServiceImpl.java:176`：`DELETE FROM ... WHERE id = ?`

项目其他实体普遍采用 `deleted` 逻辑删除字段，但这个接口绕开了业务约束、绕开了领域校验、绕开了审计、绕开了逻辑删除。

**影响**：

- 管理员误操作可以直接破坏账号、角色、菜单、绩效主数据。
- 被盗用的管理员账号可以在业务系统里完成持久化提权。
- 数据库改动不经过业务日志，事后无法追责。
- 安全表和业务表被同一权限面暴露，攻击面扩大。

**最优方案**：

1. 立即下线 `DatabaseAdminController`、`DatabaseAdminService`、前端 `DatabaseBrowser` 路由和菜单。
2. 如确需数据运维，移到独立运维平台，单独域名、单独认证、单独 IP 白名单、单独审计、单独数据库账号。
3. 业务库账号按最小权限拆分：应用账号不得拥有 DDL 权限，不得拥有跨表任意 DELETE 权限。
4. 所有生产数据修复走迁移脚本或工单化审计流程，不能通过网页直接改库。

### B4. 登录页和种子数据暴露测试账号，默认密码进入代码库

**位置**：

- `LoginPage.vue:43-88`
- `data.sql:122-124`
- `application-dev.yml:6-17`

登录页公开展示测试账号，点击后自动填入 `123456`。数据库种子数据中也写入了多名测试用户，注释直接说明“密码均为 123456”。开发环境还默认使用 MySQL `root/root` 与固定 JWT secret。

**影响**：

- 只要该前端被误部署到可访问环境，账号枚举和撞库几乎是送上门。
- 测试账号容易穿透到预生产或生产环境。
- 固定 JWT secret 会导致所有环境 token 签名边界薄弱。

**最优方案**：

1. 登录页删除测试账号区域，禁止前端暴露任何账号提示。
2. 种子账号迁移到 `dev-only` 初始化脚本，生产 profile 禁止执行。
3. 首次部署通过后台初始化一次性管理员账号，并强制首次登录改密。
4. `JWT_SECRET`、`DB_PASSWORD`、管理员初始密码全部来自密钥管理系统或部署环境，不允许代码内默认。

### B5. 授权覆盖不完整，接口安全依赖人为记忆

**位置示例**：

- `SysEmployeeController.java:23-55`
- `SysIndicatorCategoryController.java:23-55`
- `BizSelfEvaluationController.java:39-95`
- `SecurityConfig.java:35-37`

系统级安全配置只要求登录：`anyRequest().authenticated()`。真正的角色与数据范围约束靠控制器方法上的 `@RequireRole`、手写 `UnitScopeAccess`、手写 `DataScopeFilter` 组合完成。

扫描结果显示，控制器映射方法约 160 个，`@RequireRole` 约 51 处。大量接口只有“已登录”门槛，没有明确角色门槛。

典型例子：

- `SysEmployeeController` 的新增、修改、删除没有 `@RequireRole`。
- `SysIndicatorCategoryController` 的新增、修改、删除没有 `@RequireRole`。
- 自评上传、下载、删除附件接口缺少方法级角色声明。

**影响**：

- 任何遗漏一个注解的接口都可能成为越权入口。
- 权限策略分散在 Controller、Aspect、Interceptor、Service，多点维护必然漏。
- 后续新增功能时，开发人员无法确定“正确授权姿势”。

**最优方案**：

1. 建立权限矩阵表：角色、菜单、接口、数据范围、读写能力一一对应。
2. 接口默认拒绝，显式授权。不要靠“已登录即可”作为业务接口默认策略。
3. 使用统一注解表达动作级权限，例如 `@RequirePermission(resource="employee", action="write")`。
4. 数据范围过滤进入统一 repository/query layer，避免散落在 Service 手写。
5. 用自动化测试覆盖每个角色访问关键接口的 allow/deny 结果。

## 五、高风险问题

### H1. 数据权限过滤不是系统能力，而是到处手写

**位置**：

- `DataScopeFilter.java`
- `DataScopeInterceptor.java:79-104`
- `BizSelfEvaluationServiceImpl.java:340-365`
- `BizIndicatorDefinitionServiceImpl.java:490-497`

数据范围依赖 `ThreadLocal` 保存，然后由各服务自行调用 `DataScopeFilter.apply...`。这类设计的问题不是现在一定错，而是它无法防止未来漏调用。权限系统只要靠开发人员记忆，就已经失败了一半。

**最优方案**：

1. 将数据范围做成 MyBatis 拦截器或统一查询规格对象，所有查询默认带租户/单位/组织约束。
2. 对写操作建立统一 `DomainAccessPolicy`，写入前按聚合根校验权限。
3. 对 `ALL`、`UNIT`、`ORG` 建立清晰的领域模型，不要用字符串在各处硬编码。

### H2. 数据库迁移、schema、种子数据边界混乱

**位置**：

- `application.yml:9-14`
- `db/schema.sql`
- `db/data.sql`
- `db/V2__schema_sync.sql`
- `db/migration/V2__schema_sync.sql`
- `db/migration_add_notification_fields.sql`

Flyway 配置只扫描 `classpath:db/migration`，但资源目录下同时存在 `schema.sql`、`data.sql`、根目录 `V2__schema_sync.sql`、`migration_add_notification_fields.sql`。这说明团队对“谁负责建表、谁负责迁移、谁负责初始化数据”没有清晰边界。

**影响**：

- 不同环境的数据库状态不可复现。
- 手工修库与 Flyway 迁移混杂，迟早出现 checksum、重复字段、漏字段问题。
- 测试数据可能进入生产。

**最优方案**：

1. 只保留 `db/migration/V*__*.sql` 作为结构迁移权威来源。
2. `schema.sql`、`data.sql` 仅允许测试 profile 使用，或移出主包。
3. 所有迁移脚本必须可重复验证，不要依赖手工历史状态。
4. 建立空库启动测试：CI 每次用空 MySQL 跑 Flyway migrate，再跑集成测试。

### H3. 评分逻辑存在重复加权风险，财务结果不可审计

**位置**：

- `BizReviewScoreServiceImpl.java:151-155`
- `BizReviewScoreServiceImpl.java:253-255`
- `BizReviewScoreServiceImpl.java:387-391`

`calcFinalScore` 从注释看已经与月度权重有关，而 `generateMonthlyScores` 又把 `finalScore` 乘以 `weightMonthly`。如果 `calcFinalScore` 已经加权，那么这里就是二次加权；如果没有加权，注释和字段含义就是误导。

对于绩效系统，分数计算不是普通业务逻辑，它是财务、组织评价和人员利益的核心规则。现在的实现没有规则对象，没有版本号，没有审计快照，靠代码里的临时计算拼装结果。

**最优方案**：

1. 建立 `ScorePolicy` 领域服务，统一定义自评、他评、复核、最终得分、月度汇总公式。
2. 每次发布评分规则都要有版本号，计算结果记录 `ruleVersion`。
3. 汇总表保存输入快照、公式版本、计算时间、操作人，支持事后重算和追溯。
4. 用参数化单元测试覆盖典型分值、边界分值、空值、权重合计异常。

### H4. 文件上传虽有白名单，但仍缺少生产级防护

**位置**：

- `BizSelfEvaluationController.java:68-88`
- `BizSelfEvaluationServiceImpl.java:44-57`
- `BizSelfEvaluationServiceImpl.java:273-310`
- `application.yml:5-8`

附件上传有扩展名和魔数校验，这是好的起点。但仍缺少生产级要求：没有病毒扫描，没有对象存储隔离，没有访问授权下载签名，没有文件大小按业务细分，没有异步清理，没有上传审计。

**最优方案**：

1. 文件存储迁移到对象存储或隔离文件服务，应用只保存 fileId。
2. 下载必须校验业务记录权限，不允许裸文件路径暴露。
3. 引入杀毒扫描或内容安全网关。
4. 附件表独立建模，包含 owner、业务类型、hash、大小、contentType、上传人、扫描状态。

### H5. 前端权限只是体验层，且缓存策略会制造错觉

**位置**：

- `router/index.ts:71-110`
- `stores/user.ts:31-62`
- `stores/user.ts:119-170`

前端路由守卫依赖 `allowedPaths` 本地缓存和 `/menu/current` 返回结果。用户本地缓存角色、菜单、数据范围、token。前端可以做体验裁剪，但不能承担任何安全职责。当前代码容易让团队误以为“菜单不可见就是权限安全”，这是典型误判。

**最优方案**：

1. 前端只做展示级控制，后端必须做每个接口的强制鉴权。
2. 权限缓存要有版本号，角色切换或权限变更后强制失效。
3. 前端删除长期保存的数据范围和角色快照，只保留必要 token 与展示信息。
4. 更优方案是使用 HttpOnly Secure SameSite Cookie 存 token，降低 XSS 窃取风险。

## 六、中风险问题

### M1. 编码污染严重，中文内容在多处变成乱码

**位置示例**：

- `CorsConfig.java:23-34`
- `RoleCheckAspect.java:21-35`
- `SecurityUtil.java:8-72`
- `LoginPage.vue:5-44`
- 多个 SQL 脚本注释与字面量

源码中大量中文注释和消息已呈现 mojibake。乱码不是审美问题，而是工程协作问题：它会让异常消息不可读、业务含义不可审、SQL 脚本不可维护。

**最优方案**：

1. 全仓库统一 UTF-8，`.editorconfig` 固化编码。
2. 修复已污染文件，不要继续在乱码上迭代。
3. CI 增加编码检查，禁止 GBK/UTF-8 混写。

### M2. DTO 与实体边界薄弱，接口直接吃实体

**位置示例**：

- `SysEmployeeController.java:38-46`
- `SysIndicatorCategoryController.java:39-47`
- `SysMenuController.java:63-72`
- `SysRoleController.java:76-85`

控制器直接接收实体对象，意味着外部请求可以影响不该由客户端控制的字段，例如 `id`、`deleted`、`createdTime`、`updatedTime`、系统归属字段。即使 service 做了一些处理，这种边界仍然脆。

**最优方案**：

1. 每个写接口使用明确的 CreateDTO、UpdateDTO。
2. 服务端填充审计字段、租户字段、创建人字段。
3. 实体只在持久化层使用，不直接暴露给 API。

### M3. 类型系统被 `any` 掏空

**位置**：`assessment-frontend/src` 多处，扫描约 464 个 `any` 命中。

Vue + TypeScript 的价值在于把接口契约前移到编译期。当前大量 `any` 使 TypeScript 退化成有语法高亮的 JavaScript。接口字段一改，前端不会编译失败，只会运行时出错。

**最优方案**：

1. 从 API 层开始补全请求/响应类型。
2. 使用 OpenAPI 生成 TypeScript 类型，避免前后端手抄契约。
3. 禁止新增 `any`，旧代码分模块递减。

### M4. 前端包体过大，缺少分包策略

**位置**：`assessment-frontend/vite.config.ts`

生产构建中主 chunk 超过 1MB。Element Plus、业务页面、store 和通用依赖没有有效拆分。现在用户量小可能无感，部署到弱网或内网 VPN 场景会明显拖慢首屏。

**最优方案**：

1. Vite 配置 `manualChunks` 拆分 `vue`、`element-plus`、`vendor`。
2. 对重页面继续懒加载，减少首页同步依赖。
3. 引入 bundle analyzer，把包体纳入 CI 阈值。

### M5. 测试覆盖几乎为零

**扫描结果**：

- 后端测试文件：1 个。
- 前端测试文件：0 个。

对一个包含权限、审批、评分、申诉的系统来说，这种覆盖率不可接受。没有测试，就没有重构安全感；没有重构安全感，就只能继续堆补丁；补丁堆多了，系统就会变成现在这样。

**最优方案**：

1. 先补 20 个关键后端测试：登录、角色切换、权限拒绝、数据范围、评分公式、审批状态机、附件权限。
2. 前端补 API mock 与路由守卫测试。
3. 对评分公式建立 golden case，业务确认后固化为测试。

## 七、优先级整改路线

### 0-2 天：止血

1. 修复 `BizIndicatorDefinitionServiceImpl` 编译错误，确保 `mvn test` 通过。
2. 下线数据库管理台，删除菜单入口和后端接口暴露。
3. 删除登录页测试账号，移除默认密码提示。
4. 将 `JWT_SECRET`、数据库密码、初始化管理员密码从代码默认值中移除。
5. 增加 CI：后端编译测试、前端构建必须通过。

### 3-7 天：补安全骨架

1. 重构 JWT/Session 混用问题，角色切换改为签发新 token 或集中式服务端会话。
2. 建立接口权限矩阵，把 160 个映射方法逐一纳入授权表。
3. 将数据范围过滤下沉为统一机制，不允许业务查询漏过滤。
4. 管理类写接口全部改 DTO，不再接收实体。
5. 增加 MockMvc 权限测试，覆盖核心角色。

### 2-4 周：还工程债

1. 统一 Flyway 迁移体系，拆分生产迁移和开发种子数据。
2. 重建评分规则领域模型，固化规则版本与审计快照。
3. 前端 API 类型化，禁止新增 `any`。
4. 修复编码污染，统一 UTF-8。
5. 建立质量门禁：覆盖率、bundle size、ESLint、TypeScript strict、后端 Checkstyle 或 SpotBugs。

## 八、建议的目标架构

### 后端

1. Controller 只负责协议适配：参数校验、DTO 输入输出。
2. Application Service 编排业务用例：审批、自评、评分、申诉。
3. Domain Service 承载核心规则：权限策略、评分策略、状态机。
4. Repository/Mapper 负责数据访问，并统一注入数据范围。
5. Security 层统一生成当前用户上下文，不允许业务层从 request/session/header 到处取值。

### 前端

1. API 层由 OpenAPI 生成类型，禁止裸 `any` 扩散。
2. 路由守卫只做体验控制，安全判断以服务端为准。
3. 权限菜单和角色状态加版本号，权限变更立即失效。
4. 构建分包，Element Plus 与业务页面拆开。
5. 登录页清爽处理，不出现测试账号、密码、环境暗示。

### 数据库

1. Flyway 是唯一结构迁移来源。
2. 生产不执行 `data.sql` 测试数据。
3. 安全表操作必须走业务服务，禁止通用 DB Admin。
4. 关键表增加审计字段和操作日志。
5. 评分结果保存规则版本和输入快照。

## 九、最终判断

这个系统目前最危险的地方，不是某个 bug，而是缺少明确的工程边界：认证和会话边界不清，业务和运维边界不清，迁移和种子数据边界不清，实体和 DTO 边界不清，体验权限和真实权限边界不清。

最优路线不是继续修零散页面，而是先做一次架构级收口：让代码能编译，让权限可证明，让数据库不可被业务页面任意改，让评分规则可审计，让测试能挡住回归。完成这些之前，任何上线都不是技术决策，而是在赌生产环境替团队发现问题。

