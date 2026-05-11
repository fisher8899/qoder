# 月度业绩考核管理系统代码审核报告

审核日期：2026-05-11  
审核范围：项目文档、`assessment-backend` 后端、`assessment-frontend` 前端、数据库脚本。  
说明：本次仅检查和生成报告，未修改源码。

## 一、项目文档与范围理解

当前目录包含月度业绩考核管理系统的需求文档、原型设计、Qoder 生成文档，以及前后端实现：

- 后端：`assessment-backend`，Spring Boot 3.2.5、Java 17、Spring Security、JWT、MyBatis-Plus、MySQL。
- 前端：`assessment-frontend`，Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router。
- 业务流程：年度/周期指标设定、部门自评、部门他评、财务复核、预发布、申诉、正式发布、结果查询。
- 角色：`ADMIN`、`FIN_ADMIN`、`DEPT_ADMIN`、`DEPT_LEADER`、`SUPERVISOR`。

## 二、验证结果

### 后端

命令：`mvn test`

- 首次失败：Maven 使用的是 JDK 8，项目 `pom.xml` 要求 Java 17，编译报 `无效的标记: --release`。
- 临时切换 `JAVA_HOME` 到 JDK 17 后，`mvn compile` 成功。
- `mvn test` 仍失败在 Maven testCompile 状态文件写入阶段：`target/maven-status/.../inputFiles.lst`。

建议：修正 Maven 运行环境的 `JAVA_HOME`，并清理/重建 `target` 后重新运行 `mvn test`。

### 前端

命令：`npm run build`

失败：

```text
src/views/dept/IndicatorSet.vue(837,3): error TS1128: Declaration or statement expected.
src/views/dept/IndicatorSet.vue(838,1): error TS1128: Declaration or statement expected.
```

定位：`assessment-frontend/src/views/dept/IndicatorSet.vue:837-838` 存在多余 `}`。

## 三、阻断问题

### 1. 后端缺少真实角色授权

- 严重程度：阻断
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/config/SecurityConfig.java:28-30`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizExamGroupController.java:37-113`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizIndicatorDefinitionController.java:70-89`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserController.java:42-67`

问题说明：Spring Security 当前只要求登录，业务控制器没有使用 `@RequireRole` 或 `@PreAuthorize` 做服务端角色授权。`RequireRole` 切面存在，但未在业务控制器检索到使用。

影响：任意已登录用户可直接调用系统管理、考核发布、指标审批、复核提交等接口，存在严重越权风险。

修复建议：按角色矩阵给所有敏感读写接口增加服务端授权；角色应从认证主体或数据库权限表获取，不接受前端参数决定。

### 2. 数据权限信任前端 Header，缺省为 ALL

- 严重程度：阻断
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/interceptor/DataScopeInterceptor.java:28-34`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/context/DataScopeContext.java:55-57`
  - `assessment-frontend/src/api/request.ts:35-48`

问题说明：`X-Role-Code`、`X-Data-Scope`、`X-Scope-Id`、`X-User-Id` 都由前端发送，后端未校验这些值是否属于当前 JWT 用户；Header 缺失时 `getDataScope()` 返回 `ALL`。

影响：用户可删除或伪造 Header 绕过单位/组织数据隔离，读取或修改全量数据。

修复建议：后端根据 JWT 用户 ID 查询 `sys_user_permission` 生成数据范围；Header 不应作为授权依据；无数据权限上下文时应拒绝请求。

### 3. 数据库 schema 与实体/查询不匹配

- 严重程度：阻断
- 位置：
  - `assessment-backend/src/main/resources/db/schema.sql:128-142`
  - `assessment-backend/src/main/resources/db/schema.sql:156-182`
  - `assessment-backend/src/main/resources/db/schema.sql:184-277`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/util/DataScopeFilter.java:21-65`

问题说明：业务实体和查询大量使用 `unit_id`，但 `biz_exam_group`、`biz_indicator_definition`、自评/他评/复核/申诉等表的建表脚本没有对应字段。权限表也存在 `scopeId/scopeName` 等字段与脚本不一致的风险。

影响：空库初始化后，核心查询和新增可能报 `Unknown column 'unit_id'` 或权限字段缺失错误。

修复建议：补齐迁移脚本并与实体字段一致；用空库集成测试覆盖 schema 初始化、Mapper 查询、核心新增/查询接口。

### 4. 财务管理员角色编码不一致

- 严重程度：阻断
- 位置：
  - `assessment-backend/src/main/resources/db/data.sql:120-125`
  - `assessment-frontend/src/router/index.ts:28,89-131`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizIndicatorDefinitionServiceImpl.java:201,281`

问题说明：前端路由和审批流使用 `FIN_ADMIN`，初始化数据中计划财务管理员为 `EXAM_ADMIN`。

影响：初始化用户登录后无法访问财务管理员路由；财务审批无法从 `PENDING_FINANCE` 正确流转到 `APPROVED`。

修复建议：统一角色编码，建议抽成后端枚举/前端常量；迁移初始化数据和历史数据。

### 5. 前端当前无法构建

- 严重程度：阻断
- 位置：`assessment-frontend/src/views/dept/IndicatorSet.vue:837-838`

问题说明：`editIndicatorRow` 后出现多余闭合大括号，TypeScript 编译失败。

影响：生产构建无法通过，前端无法发布。

修复建议：移除多余 `}`，并运行 `npm run build` 验证。

## 四、高严重度问题

### 6. 最终得分规则实现错误

- 严重程度：高
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:326-336`
  - `assessment-backend/src/main/resources/db/schema.sql:229-231`

问题说明：需求应为“财务复核分非空则取财务复核分，否则取他评分”；代码实际在 `adminScore` 和 `deptScore` 中取较大值。

影响：管理员调低分数会被他评分覆盖，正式考核分可能被系统性抬高。

修复建议：改为 `adminScore != null ? adminScore : deptScore`，同步修正 schema 注释，并补充单元测试。

### 7. 复核提交不会生成未人工编辑指标的最终成绩

- 严重程度：高
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizReviewScoreServiceImpl.java:273-323`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/ExamResultServiceImpl.java:132-139,193-222`

问题说明：复核提交只遍历已有 `biz_review_score` 记录；如果财务管理员未逐项保存，只有他评分的指标不会形成最终成绩。

影响：结果明细和汇总缺项，发布结果不完整。

修复建议：复核提交时按所有已审批指标 upsert `biz_review_score`，将他评平均分写入 `deptScore/finalScore`；发布前校验全量指标均有最终分。

### 8. 月度考核状态流缺少服务端状态机校验

- 严重程度：高
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizExamGroupController.java:81-113`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizExamGroupServiceImpl.java:180-188,298-355`

问题说明：发布指标、启动考核、启动他评、预发布、正式发布主要是直接更新字段，未严格校验当前状态、前置步骤完成度、复核完成度、申诉处理状态。

影响：可以跳过自评/他评/复核直接发布，导致正式结果不可信。

修复建议：实现枚举化状态机；每次状态转移前校验指标审批、自评、他评、复核、申诉处理是否完成。

### 9. 自评/他评可通过请求参数冒充其他部门

- 严重程度：高
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:41,55`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizPeerEvaluationController.java:31,36,45,55`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizPeerEvaluationServiceImpl.java:253-295`

问题说明：`orgId`、`evaluatorOrgId`、`targetOrgId` 来自请求参数或 DTO，后端未校验当前用户是否属于该组织，或是否具备对应评分关系。

影响：用户可保存或提交其他部门的自评/他评分。

修复建议：从服务端认证和权限上下文派生当前组织；校验考核组成员关系、评分关系、当前步骤和截止时间。

### 10. 申诉处理未回写评分结果

- 严重程度：高
- 位置：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizAppealServiceImpl.java:133-146,159-172`

问题说明：申诉处理只更新申诉单状态和 `new_score`，未更新对应他评/复核分，也未触发结果重算。

影响：申诉显示已处理，但正式结果仍可能使用旧分数。

修复建议：申诉成功应在同一事务中更新对应评分记录、重算复核和汇总结果，并保留调整审计。

### 11. 宽松 CORS 与敏感默认配置

- 严重程度：高
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/config/CorsConfig.java:15-18`
  - `assessment-backend/src/main/resources/application.yml:8-10,25,37-38`

问题说明：CORS 允许任意 Origin 且允许凭据；数据库默认 `root/root`；JWT 默认密钥写在配置中；MyBatis 输出 SQL 到 stdout。

影响：生产部署遗漏环境变量时安全风险高，日志可能泄露业务数据。

修复建议：生产环境使用 Origin 白名单；移除默认弱口令和默认 JWT secret；生产关闭 SQL 明文日志；启动时校验关键密钥必须由环境变量提供。

## 五、中严重度问题

### 12. 申诉分页 total 未套用数据权限

- 严重程度：中
- 位置：`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizAppealServiceImpl.java:35-65`

问题说明：列表查询使用了数据权限过滤，但 count 查询没有复用同一条件。

影响：分页总数与记录不一致，也可能泄露其他单位申诉数量。

修复建议：抽取共享查询条件构造方法，list 和 count 使用同一个 wrapper。

### 13. 文件上传校验不一致

- 严重程度：中
- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:64-80`
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:256-270`

问题说明：申诉上传有部分校验，自评附件上传较弱；下载路径和权限校验也需要加强。

影响：可能上传非预期文件类型，附件访问控制不足。

修复建议：复用统一附件服务，校验扩展名、MIME、大小、归属关系；路径 normalize 后限制在上传目录内；下载前校验当前用户权限。

## 六、优先修复建议

1. 先修 RBAC、数据权限 Header 信任、角色编码不一致。
2. 同步 schema 与实体/Mapper 查询，确保空库可启动。
3. 修复前端构建错误。
4. 修正最终得分规则、复核提交全量生成最终成绩、发布前完整性校验。
5. 补齐月度考核状态机和申诉回写评分。
6. 收紧 CORS、JWT、数据库配置和附件安全。
7. 补充后端集成测试、前端构建检查和角色权限回归测试。

## 七、建议验证命令

```powershell
cd D:\QODER\assessment-backend
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
mvn package
```

```powershell
cd D:\QODER\assessment-frontend
npm run build
```

```powershell
rg -n "@RequireRole|@PreAuthorize" assessment-backend/src/main/java/com/ccerphr/assessment
rg -n "FIN_ADMIN|EXAM_ADMIN" assessment-backend assessment-frontend
```
