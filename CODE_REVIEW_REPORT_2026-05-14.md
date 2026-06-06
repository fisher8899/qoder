# 月度业绩考核管理系统代码审核报告

审核日期：2026-05-14  
审核范围：`assessment-backend`、`assessment-frontend`、数据库脚本、当前工作区近期改动  
审核方式：静态审阅、关键链路追踪、启动验证、配置与脚本交叉核对  

## 结论

这套代码已经具备“能演示、能联调、部分流程可跑”的基础，但离“可上线、可审计、可长期维护”还有明显距离。最严重的问题不在页面细节，而在服务端安全边界失真：后端当前把“JWT 已登录”当成主要边界，角色授权、数据范围、部门归属和业务流转约束仍然大面积依赖前端参数与前端状态。这意味着系统表面上有权限体系，实质上关键业务仍可被越权调用或伪造上下文绕过。

第二层问题是业务状态机和结果结算链路不闭合。月度考核从指标审批、自评、他评、复核、申诉到发布，多个环节只更新状态字段，不验证前置条件，也没有保证结果表完整生成。这样一来，系统很容易在“状态已到下一步”但“数据并未完整闭环”的情况下继续推进，最终让结果页、导出、申诉处理和实际评分产生偏差。

第三层问题是环境和工程质量。编码乱码、默认口令展示、默认密钥、CORS 全开、Maven/JDK 环境不一致、前端构建依赖系统权限，这些问题单看不一定致命，但叠加在一起说明项目当前还停留在开发期工程状态，缺少上线前的约束与收口。

## 重点发现

### P0-1 服务端缺少真实 RBAC 授权，业务接口基本是“登录即可调用”

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/config/SecurityConfig.java:28-30`  
`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizIndicatorDefinitionController.java:43-90`  
`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizExamGroupController.java:33-113`

问题：
`SecurityConfig` 只要求 `.anyRequest().authenticated()`，控制器层几乎没有 `@RequireRole`、`@PreAuthorize` 或服务端角色矩阵校验。像指标创建、删除、提交审批、审批通过/退回、考核组启动/发布这类敏感接口，服务端没有把“谁可以做什么”真正钉死。

影响：
任意已登录用户只要能构造请求，就有机会直接调用管理员、审批人、财务复核人的接口。前端菜单隐藏不是安全控制，抓包或脚本调用即可绕过。

建议：
1. 为所有写接口和敏感读接口建立后端角色矩阵，优先在控制器或服务层统一收口。
2. 角色判定必须来自 `SecurityContext` / JWT / 服务端权限表，不能来自查询参数或前端 store。
3. 为审批、发布、退回、重启、重评分等动作补充审计字段和操作日志。

### P0-2 数据权限完全信任前端 Header，可伪造为任意范围

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/interceptor/DataScopeInterceptor.java:28-52`  
`assessment-backend/src/main/java/com/ccerphr/assessment/context/DataScopeContext.java:55-58`  
`assessment-frontend/src/api/request.ts:33-52`

问题：
前端每次请求主动发送 `X-Role-Code`、`X-Data-Scope`、`X-Scope-Id`、`X-User-Id`，后端拦截器直接信任并写入 `ThreadLocal`。如果 Header 缺失，`DataScopeContext.getDataScope()` 默认回落为 `ALL`。

影响：
这是当前系统最危险的实现之一。攻击者只要篡改 Header，就可能把自己伪装成任意角色、任意组织、任意用户；如果某些请求没带这组 Header，后端还会把范围视为“全部数据”。这会直接击穿单位/部门隔离。

建议：
1. 彻底移除前端注入数据权限 Header 的机制。
2. 后端在 JWT 验证通过后，根据 `userId` 查询 `sys_user_permission` 生成数据范围上下文。
3. 对无权限上下文的请求默认拒绝，不允许回退成 `ALL`。
4. `DataScopeContext` 默认值应改为“无权限”而不是“全量权限”。

### P0-3 审批接口直接信任前端传入 `roleCode`

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizIndicatorDefinitionController.java:76-79`

问题：
审批列表接口 `approvalList` 直接接收 `@RequestParam String roleCode` 并传入服务层。也就是说，当前用户看什么审批列表，不是后端根据登录身份判定，而是前端告诉后端“我是什么角色”。

影响：
任何登录用户都可以尝试传入 `FIN_ADMIN`、`SUPERVISOR`、`DEPT_LEADER` 等角色代码，读取或探测其他审批视图数据。

建议：
1. 删除该接口的 `roleCode` 外部入参。
2. 服务端从认证上下文解析当前用户有效角色。
3. 若允许一人多角色切换，也应由服务端验证“当前选择角色”是否属于该用户的有效角色集合。

### P0-4 自评/他评/复核核心业务字段直接来自请求参数，缺少归属校验

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:167-223`  
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizPeerEvaluationServiceImpl.java:253-296`  
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:190-255`

问题：
`orgId`、`evaluatorOrgId`、`targetOrgId`、`indicatorId` 等关键归属字段来自 DTO，保存时没有严格校验“当前用户是否属于该组织”“当前组织是否在该考核组中”“当前组织是否确实拥有对目标组织评分的资格”。

影响：
用户可伪造请求，对其他部门的自评、他评、复核数据进行写入或覆盖。即使前端页面限制了操作入口，服务端也没有建立硬约束。

建议：
1. 所有组织归属从服务端上下文推导，不从前端信任。
2. 保存前校验考核组成员关系、评分关系、当前步骤、截止时间、指标归属。
3. 为 `exam_group_id + org_id + indicator_id`、`exam_group_id + evaluator_org_id + target_org_id + indicator_id` 建唯一索引，防止脏写。

### P1-1 最终得分规则实现错误，当前逻辑会“取高分”

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:326-336`  
`assessment-backend/src/main/resources/db/schema.sql:229-231`

问题：
`calcFinalScore` 当前逻辑是：如果管理分存在且部门分更高，则取部门分。也就是 `max(adminScore, deptScore)`。这和常见复核语义不一致，也与当前注释保持一致地暴露了错误设计。

影响：
如果财务/管理员故意下调分数，系统会被更高的部门分覆盖，导致复核形同虚设，正式结果虚高。

建议：
1. 明确业务规则。如果复核分优先，则改为 `adminScore != null ? adminScore : deptScore`。
2. 修正数据库注释和前后端文案，避免“代码错但注释也跟着错”。
3. 为“仅他评”“仅复核”“复核低于他评”“复核高于他评”补单测。

### P1-2 复核提交不会补齐未人工编辑项，结果表可能天然不完整

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:273-323`  
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:193-222`

问题：
`submitReview` 只遍历已存在的 `biz_review_score` 记录，不会为“有他评结果但复核人未逐条保存”的指标自动补建复核记录。

影响：
结果汇总和导出只看 `biz_review_score`，因此未被人工点开的指标可能永远不进入最终结果。系统会出现“流程已复核完成，但结果表缺项”的隐蔽错误。

建议：
1. 复核提交时按“已审批通过的全部指标”做 upsert。
2. 对缺失复核记录的项，用他评分均分回填 `deptScore/finalScore`。
3. 发布前增加完整性校验：每个组织、每个指标都必须有最终分。

### P1-3 申诉处理没有回写评分链路，状态已处理但结果仍可能是旧分

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizAppealServiceImpl.java:133-172`

问题：
无论 `handleAppeal` 还是 `reScoreAppeal`，都只是更新申诉单 `newScore/status/handledBy`，没有同步更新对应的他评/复核/汇总结果，也没有触发重算。

影响：
界面会显示申诉“已处理”，但最终结果、历史结果、导出报表仍可能继续使用旧分数，业务层面相当于假处理。

建议：
1. 申诉处理必须与评分回写在同一事务内完成。
2. 明确申诉变更作用于哪一层分数，是 `peerScore`、`adminScore` 还是 `finalScore`。
3. 对受影响组织触发结果重算，并保留调整审计日志。

### P1-4 月度考核状态流转没有真正的状态机，几乎可以跳步骤发布

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizExamGroupServiceImpl.java:178-369`

问题：
`startGroup`、`publishIndicator`、`startExam`、`startPeerEval`、`prePublish`、`publish`、`cancelPrePublish` 主要是直接改 `status/currentStep`，几乎不验证前置条件是否完成。

影响：
可以在指标未审批、自评未提交、他评未完成、复核未闭环、申诉未处理的情况下，直接推进到预发布或正式发布。最终状态不可信。

建议：
1. 抽象显式状态机，定义合法迁移图。
2. 每次迁移前校验前置数据完整性和当前状态。
3. 发布动作前增加“阻断式检查清单”，不满足则拒绝。

### P1-5 结果查询把多条他评压成单条，明细页可能显示错分

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:93-100,125-139`

问题：
结果明细页把 `BizPeerEvaluation` 直接按 `targetOrgId_indicatorId` 放入 `peerMap`，如果同一指标有多个他评部门，Map 里只保留最后一条，不做平均。

影响：
结果页展示的 `peerScore` 不是实际他评分均值，而是某一条覆盖后的值，和复核页的部门均分逻辑不一致，容易让用户误判。

建议：
1. 明细页统一按同口径计算他评分均值。
2. 结果页、复核页、导出页必须复用同一个评分聚合函数。

### P1-6 结果汇总未过滤审批状态，可能把草稿指标带入汇总

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:200-207`

问题：
汇总时查询部门全部指标定义，没有限制 `approvalStatus = APPROVED`。

影响：
如果历史草稿或退回中的指标仍在表里，类别映射和权重映射会混入非正式指标，导致汇总口径漂移。

建议：
汇总、历史查询、导出统一只基于“已审批通过且进入正式考核口径”的指标集合。

### P1-7 考核进度接口存在明显错误实现，返回值不可信

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizExamGroupServiceImpl.java:383-394`

问题：
`getProgress()` 中构造了指标查询 wrapper，但实际却执行了 `getBaseMapper().selectCount(null)`，变量 `total` 也未参与后续真实计算。返回的 `selfEvalStatus/peerEvalStatus/reviewStatus` 还是固定文案。

影响：
这是典型“页面有进度，后台没逻辑”的实现。若前端据此展示项目状态，会误导业务操作。

建议：
1. 重写该接口，按指标、自评、他评、复核、申诉的真实数据统计。
2. 删除无效变量和占位返回，避免伪完成逻辑继续留在主干。

### P1-8 数据库脚本与 Java 实体/过滤逻辑不一致，空库初始化存在高风险

位置：
`assessment-backend/src/main/resources/db/schema.sql:128-289`  
`assessment-backend/src/main/java/com/ccerphr/assessment/util/DataScopeFilter.java`  
多处 `Biz*::getUnitId` 调用

问题：
多个核心业务表建表脚本中看不到 `unit_id`，但服务层大量按 `getUnitId()` 做数据过滤和自动填充。当前运行能通过，更多像是历史库结构已漂移，而不是脚本与代码一致。

影响：
新环境按当前 `schema.sql` 初始化后，极可能在查询或保存阶段报列不存在，或者数据权限逻辑完全失效。

建议：
1. 用当前实体字段反向校准 schema。
2. 引入正式迁移工具，禁止手改脚本与现网库长期漂移。
3. 用空库自动初始化跑一次集成测试，确保首启可用。

### P1-9 角色编码仍存在历史不一致痕迹

位置：
`assessment-backend/src/main/resources/db/data.sql:120-125`

问题：
测试数据里财务管理员仍是 `EXAM_ADMIN`，而近期文档和前端逻辑已多次提到统一为 `FIN_ADMIN`。

影响：
新库初始化后，财务角色的路由、审批列表、流程跳转仍可能失配。这个问题说明角色码还没有被“单点定义”。

建议：
1. 统一角色码常量源。
2. 清理初始化脚本、历史脚本、前端路由、后端服务中的旧码值。
3. 增加启动时角色字典自检。

### P2-1 CORS 全开放并允许携带凭证，默认密钥和默认口令仍在代码/页面中暴露

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/config/CorsConfig.java:14-18`  
`assessment-backend/src/main/resources/application.yml:10,37`  
`assessment-frontend/src/views/login/LoginPage.vue:43-88`  
`assessment-backend/src/main/resources/db/data.sql:119-125`

问题：
后端允许任意源跨域并允许凭证；数据库密码和 JWT secret 有弱默认值；登录页直接展示测试账号，且默认密码固定为 `123456`；初始化脚本中也写入了统一测试口令。

影响：
开发环境方便，生产环境危险。最糟糕的情况不是“被人不知道系统怎么登”，而是“系统自己把入口、账号和默认口令写在页面上”。

建议：
1. 生产环境使用 Origin 白名单。
2. 去掉默认 JWT secret 和默认数据库口令回退值，改为启动时强校验。
3. 测试账号提示只能存在于开发 profile。
4. 首次登录强制改密，禁用统一初始密码长期存在。

### P2-2 附件上传缺少类型、大小、归属和下载权限校验

位置：
`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:255-274`

问题：
上传逻辑只按原始扩展名落盘，没有 MIME 校验、大小控制、归属关系校验和下载授权闭环。

影响：
可能上传不受控文件；如果下载接口也缺少约束，后续会形成附件越权访问问题。

建议：
1. 建统一附件服务。
2. 校验扩展名、MIME、大小、所属业务单据和所属用户。
3. 下载时二次校验当前用户是否有权访问该附件。

### P2-3 中文乱码已侵入源码、脚本、页面和历史日志，维护成本正在上升

位置：
`assessment-frontend/src/views/login/LoginPage.vue`  
`assessment-backend/src/main/resources/db/schema.sql`  
工作区多份文档与日志

问题：
当前不只是显示乱码，而是源码中文常量、SQL 注释、文档记录都存在编码污染。

影响：
后续任何“靠检索文本定位逻辑”的维护都会变差，部分编译器、导出报表、日志监控和 API 文案也会持续受影响。

建议：
1. 统一仓库编码为 UTF-8。
2. 把前后端源码、SQL、文档、日志模板分批清洗。
3. 在 `.editorconfig`、IDE、Git 属性里固化编码策略。

## 本轮新增观察

1. 后端必须以 JDK 17 运行，但本机 `mvn -v` 默认仍落在 Java 8。构建和运行成功依赖额外手动切换 `JAVA_HOME`。这是环境脆弱点，不是单次偶发。
2. 前端 `vite/esbuild` 在当前机器上对进程权限较敏感，说明本地启动依赖宿主权限环境，CI 若未对齐也容易不稳定。
3. 本轮改动量主要集中在前端审批页面，说明体验层在快速迭代；但服务端的授权和状态边界并没有同步加固，形成“界面越来越复杂，底层约束仍然薄弱”的风险反差。

## 修复优先级建议

### 第一阶段：先堵漏洞

1. 移除前端注入的权限 Header，后端改为基于 JWT 和权限表生成权限上下文。
2. 为所有敏感接口补上服务端 RBAC。
3. 关闭生产默认密钥、默认口令、开放 CORS。

### 第二阶段：补业务闭环

1. 重写月度考核状态机。
2. 修正最终得分规则。
3. 复核提交做全量 upsert。
4. 申诉处理同步回写评分并触发重算。

### 第三阶段：补工程可靠性

1. 校准 schema 与实体。
2. 清理乱码。
3. 增加空库初始化测试、权限回归测试、结果口径测试。
4. 固化 Java 17 / Node / Maven 运行环境。

## 建议补充的测试

1. 权限回归：普通用户调用管理员接口应返回 403。
2. 数据范围回归：伪造 Header 不应扩大数据范围。
3. 结果口径：多他评人场景下，结果页、复核页、导出页分数一致。
4. 状态流转：缺自评、缺他评、缺复核、存在未处理申诉时，发布动作必须失败。
5. 空库启动：按 `schema.sql + data.sql` 启动后，核心接口可用。

## 总评

当前项目最需要的不是再继续堆页面，而是尽快把“服务端安全边界”和“评分结果闭环”补牢。否则后续每增加一个功能页，都会继续建立在脆弱的权限模型和不完全可信的结果链路上，维护成本和事故概率都会同步上升。
