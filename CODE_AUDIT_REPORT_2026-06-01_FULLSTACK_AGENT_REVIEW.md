# 前后端代码全面审核报告

> 审核日期：2026-06-01  
> 审核范围：`assessment-backend`（Spring Boot / Java）与 `assessment-frontend`（Vue / TypeScript）  
> 审核方式：主审静态审查 + 前端生产构建 + 后端全量清理编译与测试 + 前端依赖漏洞扫描  
> 审核立场：按生产系统标准审查，不替明显风险找借口。

> Agent 执行说明：已按要求安排独立资深审查 Agent 对前后端进行第二视角审核。该 Agent 在多次收口指令后仍未返回可用审查摘要，已终止任务。本报告只收录主审已逐项复核的证据，不混入未完成 Agent 的未经验证结论。

## 一、执行摘要

当前代码已经具备可运行的业务骨架，前端生产构建与后端全量编译均能通过。但“能编译”不等于“能放心上线”。本轮审查未确认无需前置条件即可直接利用的 P0 问题，但确认了多项 P1 级风险：数据库浏览器暴露过度、默认开发配置过于危险、多个对象级权限校验缺失、复核评分可被跨对象篡改、工作流审批身份设计失真，以及测试数据中保留固定默认密码。

最刺眼的问题不是某一行代码写得难看，而是授权规则散落在 Controller、Service、拦截器和前端菜单里，缺少统一、可证明的安全边界。这样的系统在功能增加后非常容易出现“这个接口记得校验，旁边那个忘了”的低级事故。本轮已经抓到多个实例。

### 风险统计

| 级别 | 数量 | 说明 |
| --- | ---: | --- |
| P0 | 0 | 未确认无前置条件即可直接攻破系统的问题 |
| P1 | 8 | 上线前必须处理，涉及敏感数据、评分完整性、认证授权和部署安全 |
| P2 | 10 | 应进入近期修复计划，涉及可用性、审计、依赖风险和防御纵深 |
| P3 | 3 | 工程质量与性能优化项 |

## 二、验证结果

### 已执行命令

```powershell
cd assessment-frontend
npm run build
npm audit --omit=dev --json

cd assessment-backend
.\mvn17.cmd clean test
```

### 验证结论

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 前端生产构建 | 通过 | Vite 构建成功，但主包约 `1,180.32 kB`，触发大包警告 |
| 后端全量清理编译 | 通过 | 重新编译 `201` 个 Java 源文件 |
| 后端测试 | 通过但覆盖严重不足 | 仅 `1` 个测试文件、`2` 个异常处理用例 |
| 前端测试 | 缺失 | 未发现前端单元测试或组件测试 |
| 前端生产依赖扫描 | 失败 | `axios@1.15.2` 存在已披露漏洞，`npm audit` 返回高危项 |

## 三、P1：上线前必须处理

### P1-01 数据库浏览器不是“运维工具”，而是一个带网页界面的核心数据改写器

**证据**

- `assessment-backend/src/main/resources/application.yml:5-6`：未指定 profile 时默认启用 `dev`。
- `assessment-backend/src/main/resources/application-dev.yml:25-26`：开发 profile 默认启用 `app.db-admin.enabled=true`。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:35-64`：白名单包含 `sys_user`、`sys_user_permission`、`sys_role_menu`、`biz_review_score`、`biz_monthly_score` 等高敏表。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:100-106`：查询使用 `SELECT *`，读取整行数据。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:66`、`:134-163`：除少数技术字段外，其他字段默认可编辑。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:170-186`：支持逻辑删除记录。

**问题描述**

这不是普通后台查询页。管理员可以读取 `sys_user.password` 的 BCrypt 哈希，直接改写权限、角色、评分、月度汇总、通知和业务状态。更糟糕的是，编辑规则采用“默认允许”，只排除了 `id`、时间戳和 `deleted`。这相当于把生产数据库的核心表交给前端通用表格操作。

**影响**

- 管理员账号一旦被盗，攻击者无需懂业务接口即可批量篡改评分和权限。
- `sys_user.password` 哈希可被导出后离线爆破。
- 可直接把密码字段写成非 BCrypt 内容，导致账号不可登录或认证异常。
- 修改不会经过业务状态机、字段校验、审计规则和事务边界。

**专业解决方案**

1. 生产构建彻底移除该 Controller，不能只依赖配置开关。
2. 将数据库浏览能力拆成只读、脱敏、最小白名单的诊断接口。
3. 永久排除 `sys_user`、权限表、评分表、汇总表和审计表。
4. 所有写操作改为专用业务接口，并强制记录操作人、变更前后值、来源 IP、请求 ID。
5. 如确需紧急运维写入，使用独立堡垒机和数据库审计，不要通过业务 Web 应用实现。

### P1-02 默认启动就是开发模式，错误部署一次就会把危险能力带到线上

**证据**

- `assessment-backend/src/main/resources/application.yml:5-6`：默认 profile 为 `dev`。
- `assessment-backend/src/main/resources/application-dev.yml:6-8`：数据库默认连接 `root/root`。
- `assessment-backend/src/main/resources/application-dev.yml:22`：JWT 提供固定开发密钥。
- `assessment-backend/src/main/resources/application-dev.yml:23-26`：Swagger 与数据库浏览器默认开启。
- `assessment-backend/src/main/resources/application-prod.yml:6-19`：生产配置虽然要求环境变量，但只有显式切换 `prod` 才生效。

**问题描述**

当前配置把“安全启动”做成了可选项，把“危险启动”做成了默认项。任何漏写 `SPRING_PROFILES_ACTIVE=prod` 的部署脚本，都会启动 Swagger、数据库浏览器、固定 JWT 密钥和 `root/root` 数据库默认值。生产事故最喜欢这种“只要少配一个环境变量就全开”的设计。

**影响**

- 配置疏漏可直接升级为敏感接口暴露。
- 固定 JWT 密钥一旦进入可访问环境，攻击者可伪造令牌。
- 数据库弱口令扩大横向移动风险。

**专业解决方案**

1. 删除 `spring.profiles.default: dev`，未设置 profile 时拒绝启动。
2. 开发 profile 也不要提供数据库密码和 JWT 密钥默认值，改为本地 `.env` 或安全配置中心注入。
3. 增加启动时安全断言：生产环境发现 docs、db-admin、弱 JWT 密钥或默认数据库账号时立即失败。
4. 在 CI/CD 中增加部署配置测试。

### P1-03 自评详情存在对象级越权读取

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:44-47`：接口直接接受客户端提交的 `examGroupId` 和 `orgId`。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:146-195`：查询自评分数、完成情况和附件信息前没有调用现成的 `validateOrgAccess()`。
- 同文件 `:377-403`：项目其实已经实现了组织范围校验，但读取路径没有使用。

**问题描述**

任何已登录用户都可以枚举 `orgId`，读取其他部门的自评完成情况、自评分数和附件元数据。校验函数明明已经写好了，却只在保存、提交、下载和删除时调用，读取接口被漏掉。

**影响**

- 跨部门泄露未公开评分和考核材料信息。
- 为后续定向篡改、社工或内部舞弊提供数据基础。

**专业解决方案**

1. 在 `getTaskList()` 和 `getIndicators()` 入口调用 `validateOrgAccess(orgId)`。
2. 不要让 Controller 自由接收普通用户的 `orgId`；优先从服务端 `DataScopeContext` 推导。
3. 增加组织 A 用户访问组织 B 数据时返回 `403` 的集成测试。

### P1-04 结果、汇总、导出和进度接口存在成组的数据范围缺口

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:171-228`：`querySummary()` 按考核组遍历全部部门，没有应用数据范围过滤。
- 同文件 `:290-292`：汇总导出直接复用未过滤的 `querySummary()`。
- 同文件 `:363-411`：历史查询允许传入任意 `orgId` 计算总分，未校验目标组织是否在当前范围内。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamProgressServiceImpl.java:107-165`：`queryUnfilledItems()` 接收任意组织 ID，未应用数据范围过滤。
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/MonthlyExamController.java:54-118`、`:122-156`：按任意 `examGroupId` 查询全组进度，未验证考核组是否属于当前数据范围。

**问题描述**

数据权限实现并不完整：部分列表查询加了过滤，部分汇总、导出、历史和明细查询却没有。攻击者不需要突破认证，只需要修改请求参数。权限系统如果只能保护菜单点击，保护不了 API 参数，就是装饰品。

**影响**

- 部门用户可越权获取其他组织评分汇总、未填项和考核进度。
- 导出接口会放大泄露范围。

**专业解决方案**

1. 抽出统一的 `ScopeGuard`，对 `examGroupId`、`orgId`、附件 ID、通知 ID 做对象级授权。
2. 所有查询先收敛可见组织 ID，再访问明细、汇总和导出。
3. 导出必须复用已经授权后的查询对象，不能另起一条“方便但裸奔”的查询路径。
4. 为每个 API 增加跨组织负向测试。

### P1-05 复核评分保存接口允许跨对象篡改，且 DTO 基本没有校验

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizReviewScoreController.java:36-53`：保存与批量保存只校验角色，不校验对象归属，也没有 `@Valid`。
- `assessment-backend/src/main/java/com/ccerphr/assessment/dto/ReviewScoreSaveDTO.java:8-14`：ID、考核组、组织、指标、分数和备注均无 Bean Validation。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:198-220`：客户端传入已有 `id` 时直接加载该记录；不存在时可按客户端提供的任意三元组新建。
- 同文件 `:223-262`：未验证评分记录、考核组、组织和指标之间的归属关系，也未执行数据范围校验。

**问题描述**

这是评分完整性问题，不是普通代码味道。具备允许角色的用户可以构造请求修改不属于自己范围的复核记录，或者创建组合关系错误的评分记录。当前实现把“客户端传什么”当成“业务事实是什么”。

**影响**

- 跨组织改分。
- 伪造不一致评分记录，污染月度汇总。
- 批量保存接口可放大破坏范围。

**专业解决方案**

1. DTO 增加 `@NotNull`、`@DecimalMin("0")`、`@DecimalMax("100")`、备注长度限制。
2. 根据服务端上下文校验当前角色、单位、组织和考核组范围。
3. `id` 存在时校验记录原始三元组与请求三元组完全一致。
4. 新建时验证指标确实属于该考核组和组织。
5. 提交汇总前校验评分完整性，并写不可抵赖的审计日志。

### P1-06 工作流代理接口授权过宽，初始化接口对所有登录用户开放

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/WorkflowController.java:44-47`：可按任意角色查询工作流用户 ID。
- 同文件 `:55-66`：任意登录用户可启动指标审批工作流。
- 同文件 `:85-104`：任意登录用户可提交审批或驳回动作。
- 同文件 `:111-145`：任意登录用户可查询任意实例历史，并调用初始化接口改写运行时工作流 ID。
- 该 Controller 没有任何 `@RequireRole` 或对象级授权。

**问题描述**

工作流 Controller 是一个高权限代理，却把能力开放给了所有已登录用户。外部工作流平台是否还有二次校验不能成为这里放弃校验的理由；业务系统必须先证明调用者有权发起、查看或审批目标对象。

**影响**

- 伪造审批实例。
- 越权查询审批历史。
- 滥用初始化接口改变运行时工作流目标。
- 审批接口成为跨系统攻击面。

**专业解决方案**

1. `/init` 仅允许运维初始化流程调用，并从业务 API 中移除。
2. 启动工作流前校验指标、考核组、组织和当前用户关系。
3. 审批前查询任务归属，确认节点确实属于当前用户。
4. 历史查询前校验实例与当前可见业务对象的关联。
5. 给每个动作记录业务对象 ID、工作流实例 ID、节点 ID、用户 ID、角色和请求 ID。

### P1-07 工作流审批身份按角色共享，审计责任链失真

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/config/WorkflowConfig.java:24-29`：每个角色写死一个共享 UUID。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/WorkflowIntegrationService.java:38-44`：当前用户工作流身份仅按角色代码查找。
- 同文件 `:145-152`：审批动作把这个共享 UUID 作为 `approver_id` 发送到工作流平台。

**问题描述**

多个实际用户只要角色相同，就会共用同一个工作流审批身份。系统无法可靠回答“到底是谁审批的”。这会让审批留痕、责任认定和撤销追踪全部变得含糊。

**影响**

- 不同人员之间可能互相看到或处理同角色任务。
- 审计记录无法映射到真实个人。
- 发生争议时缺少可信证据链。

**专业解决方案**

1. 建立本系统用户 ID 与工作流平台用户 UUID 的一对一映射。
2. 角色只用于授权，不得作为审批人身份。
3. 对历史共享身份数据制定迁移和补录策略。

### P1-08 初始化脚本保留固定测试账号与固定密码哈希

**证据**

- `assessment-backend/src/main/resources/db/data.sql:118-124`：脚本明确声明测试账号密码相同，并写入 `admin` 等固定账号的同一 BCrypt 哈希。

**问题描述**

固定管理员口令进入版本库后，就不再是秘密。只要该初始化脚本在可访问环境执行，攻击者就拥有一份公开的登录字典。尤其当前默认 profile 还是开发模式，这两个问题叠加后风险更高。

**影响**

- 测试、演示、预生产环境被直接登录。
- 环境复用或错误初始化时把弱口令带进生产。

**专业解决方案**

1. 从通用初始化脚本移除可登录账号。
2. 演示账号放入单独的 `demo` profile 数据包。
3. 首次启动使用一次性随机管理员密码，并强制首次登录修改。
4. 对现有环境立即轮换密码并检查登录审计。

## 四、P2：近期必须整改

### P2-01 通知“标记已读”存在 IDOR

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysNotificationController.java:42-45`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysNotificationServiceImpl.java:33-38`

**问题描述**

接口只按通知 ID 更新，没有校验 `recipientUserId` 是否等于当前用户。任何登录用户都可以枚举 ID，把别人的通知标成已读。

**修复方案**

更新条件必须包含 `id` 与当前用户 ID；不存在或不属于当前用户时返回 `404` 或 `403`。增加越权测试。

### P2-02 页面登出没有调用后端撤销接口，JWT 实际仍然有效

**证据**

- `assessment-frontend/src/layouts/MainLayout.vue:172-176`：登出只调用 `userStore.logout()`。
- `assessment-frontend/src/api/auth.ts:16-18`：已经实现 `/auth/logout` API，但界面没有调用。
- `assessment-backend/src/main/resources/application.yml:31-32`：JWT 默认有效期为 `14,400,000 ms`，即 4 小时。
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:190-199`：后端撤销逻辑只有调用接口才会执行。

**问题描述**

用户点击退出后，旧令牌在剩余有效期内仍可继续访问 API。界面看起来退出了，服务端并不知道。

**修复方案**

前端登出先 `await logout()`，再清理本地状态；失败时也要清理本地状态并记录告警。更进一步，使用短期 access token 与可撤销 refresh token。

### P2-03 登录限流可被伪造转发头绕过，而且多实例部署时各算各的

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:125-138`：无条件信任 `X-Forwarded-For` 和 `X-Real-IP`。
- `assessment-backend/src/main/java/com/ccerphr/assessment/security/LoginRateLimiter.java:24-50`：限流状态只存在单机内存。

**问题描述**

如果应用可被直接访问，攻击者每次伪造不同的 `X-Forwarded-For` 即可绕过 IP 限流。多实例部署后，请求轮转也能稀释限流效果。

**修复方案**

仅在受信任反向代理后解析转发头；否则使用连接源 IP。限流迁移到 Redis，并同时按 IP、用户名、设备特征和失败次数执行策略。

### P2-04 JWT 黑名单只存在单机内存，重启或负载均衡后撤销失效

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/security/TokenBlacklistService.java:19-41`

**问题描述**

黑名单保存在当前 JVM 的 `ConcurrentHashMap`。应用重启后丢失，多实例之间也不共享。

**修复方案**

使用 Redis 存储撤销记录，TTL 与 token 剩余有效期一致；或切换到短 access token + 可撤销 refresh token 模型。

### P2-05 自评加权结果信任客户端提交值

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/dto/SelfEvalSaveDTO.java:31-32`：允许客户端提交 `selfResult`。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:233-239`：只要客户端传值，后端就直接保存，不再计算。
- `assessment-frontend/src/views/dept/SelfEvaluation.vue:843-863`：前端自行计算结果。

**问题描述**

核心评分结果由浏览器提供，后端仅在客户端不传值时才计算。浏览器不是可信计算环境，任何人都可以改请求。

**修复方案**

后端始终根据原始分数、权重和规则计算结果；客户端计算只能用于预览。保存时忽略客户端传入的 `selfResult`，并增加一致性测试。

### P2-06 自评附件上传会产生未关联孤儿文件

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:77-85`：上传接口只接收文件，不接收自评记录 ID，也不校验组织归属。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:309-325`：文件上传后立即落盘并返回 key。
- 同文件 `:50-68`：允许压缩包格式。

**问题描述**

任何登录用户都可反复上传文件而不绑定业务记录。即使单文件受全局 20MB 限制，持续调用仍会造成存储堆积。压缩包还会增加后续人工下载和解压风险。

**修复方案**

上传时要求业务记录 ID 并执行对象级授权；增加用户级配额、总量限制、定期清理未绑定文件；压缩包进行恶意文件扫描，必要时禁止上传。

### P2-07 数据范围过滤工具采用“异常值放行”，防御方向反了

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/util/DataScopeFilter.java:16-17`、`:35-36`、`:57-58`：当 `scopeId` 为空或为 `0` 时直接返回，不加任何过滤。

**问题描述**

正常权限配置路径虽然会限制 UNIT/ORG 的 scopeId，但过滤工具本身是 fail-open。只要历史脏数据、手工改库、未来新增入口或迁移脚本制造出异常权限，查询就可能退化为全量可见。

**修复方案**

只有明确 `dataScope=ALL` 才允许不加过滤；UNIT/ORG 缺少 scopeId 时必须抛出 `403` 或强制 `WHERE 1=0`。

### P2-08 工作流 HTTP 客户端缺少超时、认证和持久化设计

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/service/WorkflowIntegrationService.java:27-30`：直接 `new RestTemplate()`，没有连接和读取超时配置。
- 同文件 `:82-90`、`:116-120`、`:154-162`：调用工作流平台时没有认证头。
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/WorkflowController.java:142-144`：初始化只修改内存配置。

**问题描述**

工作流平台卡住时可能长期占用业务线程；系统重启后运行时写入的 workflow ID 消失；跨系统调用也没有可见的服务间认证。

**修复方案**

使用配置化 HTTP 客户端，设置连接、读取、整体超时和有限重试；加入服务间认证；工作流 ID 持久化到配置中心或数据库；增加熔断与监控。

### P2-09 结果查询和导出缺少资源边界，认证用户可制造高负载

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/dto/ResultQueryDTO.java:11-12`：分页参数无上限。
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:144-150`：在内存中手工分页。
- 同文件 `:231-287`：导出明细时固定请求 `99999` 条，使用内存工作簿并对每列执行 `autoSizeColumn()`。
- 同文件 `:171-228`：汇总查询存在按部门循环查询。

**问题描述**

这是典型的“数据量小时没感觉，数据量一上来就拖死接口”。认证用户可通过大分页和反复导出制造 CPU、内存和数据库压力。

**修复方案**

限制分页大小；数据库分页；导出使用流式工作簿 `SXSSFWorkbook`；限制导出行数；大导出转异步任务；消除循环查询。

### P2-10 前端依赖扫描确认 `axios@1.15.2` 存在已披露漏洞

**证据**

- `assessment-frontend/package.json:13`
- 执行 `npm audit --omit=dev --json` 返回 `axios` 高危漏洞，修复版本可用。

**问题描述**

当前锁定版本包含已披露问题。部分公告主要影响 Node 代理场景，但继续保留存在公告的版本没有合理收益。

**修复方案**

升级至修复版本，重新执行 `npm audit --omit=dev` 与 `npm run build`，并将依赖扫描纳入 CI。

## 五、P3：工程质量与性能

### P3-01 前端把 `mysql2` 放进生产依赖

**证据**

- `assessment-frontend/package.json:15`

浏览器项目不应依赖 MySQL 驱动。当前看起来未被业务代码使用，但会增加供应链面积和维护噪声。删除依赖并重新生成 lockfile。

### P3-02 前端主包过大

**证据**

- `npm run build` 输出：主包约 `1,180.32 kB`，gzip 后约 `380.21 kB`，触发 Vite 大包警告。

按路由和重型组件拆包，评估 Element Plus 按需引入，使用 `rollupOptions.output.manualChunks` 做稳定拆分。

### P3-03 调试日志进入业务页面和数据权限链路

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/interceptor/DataScopeInterceptor.java:108-109`、`:121-137`
- `assessment-frontend/src/views/dept/SelfEvaluation.vue:907-920`

当前日志会持续输出用户权限匹配过程、自评自动保存 payload 和后端响应。开发阶段方便，生产阶段会制造噪声并扩大敏感信息落盘范围。使用结构化日志、环境级别控制和字段脱敏。

## 六、测试与架构判断

### 测试覆盖严重不足

当前后端有 `201` 个主 Java 文件，却只有 `1` 个测试文件和 `2` 个异常处理测试；前端 `66` 个源文件，没有发现测试文件。对一个包含 JWT、角色切换、数据范围、审批、评分、附件、导出和数据库运维入口的系统，这种覆盖率接近于没有安全网。

### 必补测试矩阵

| 场景 | 必测内容 |
| --- | --- |
| 认证 | 登录失败限流、禁用账号、伪造 JWT、过期 JWT、登出后旧 JWT 失效 |
| 角色切换 | 无权限角色切换、跨 scope 切换、脏权限数据、UNIT/ORG scopeId 缺失 |
| 自评 | 跨组织读取、跨组织保存、附件下载越权、客户端伪造 selfResult |
| 他评 | evaluatorOrgId 越权、targetOrgId 越权、指标不属于目标组织、提交后修改 |
| 复核 | 跨组织改分、跨考核组改分、伪造指标关联、批量保存部分失败回滚 |
| 结果与进度 | 汇总越权、导出越权、历史 orgId 越权、未填项越权、大分页压力 |
| 通知 | 修改他人通知 ID、重复标记已读 |
| 工作流 | 越权启动、越权审批、越权历史查询、平台超时、平台失败重试 |
| 数据库浏览器 | 生产环境 Controller 不存在、敏感表不可见、敏感字段脱敏 |

## 七、建议修复顺序

### 第一批：立即止血

1. 移除生产数据库浏览器，默认 profile 改为安全失败。
2. 轮换默认账号与 JWT 密钥，拆出演示数据。
3. 修复自评读取、结果汇总、导出、进度、通知的对象级越权。
4. 修复复核评分保存的对象归属校验和 DTO 校验。
5. 限制工作流 Controller 权限，取消共享审批身份。

### 第二批：一周内完成

1. 前端登出接通后端撤销接口，黑名单迁移到 Redis。
2. 登录限流迁移到 Redis，只信任受控代理转发头。
3. 后端独立计算自评结果。
4. 附件上传绑定业务记录并清理孤儿文件。
5. 升级 `axios`，删除前端 `mysql2`。

### 第三批：随后补齐

1. 建立统一对象级授权组件，不再靠各 Service 自觉。
2. 补齐负向权限测试与集成测试。
3. 改造导出性能和前端拆包。
4. 清理调试日志并建立审计日志规范。

## 八、最终结论

当前版本可以用于继续开发和联调，但不建议直接上线。最需要警惕的是：权限系统已经有不少正确实现，却仍存在多条旁路。它不是完全没有门禁，而是有些门装了锁，有些门只贴了“请勿进入”的纸条。上线前必须统一授权模型，并用负向测试证明跨组织、跨角色、跨对象访问全部被拒绝。
