# 月度绩效考核管理系统 — 深度代码评审报告

**评审日期**:2026-05-21
**评审范围**:`assessment-backend/` (Spring Boot 3.2.5) + `assessment-frontend/` (Vue 3)
**评审方法**:源码阅读 + 攻击面分析 + 依赖 CVE 比对 + 业务边界推演

---

## 总体评分

> **62 / 100 — 业务功能基本闭环,但安全与并发是定时炸弹。**

这是一个能跑、能演示、能给业务用的系统,但**不是一个可以暴露在公网或多租户共享环境的系统**。核心病灶有三个:
1. **权限边界依赖前端传参**——多处 Service 用 DTO 里的 `orgId` / `evaluatorOrgId` 做"鉴权",再用 DTO 里的 `id` 读记录,典型 IDOR。
2. **管理员后门接口暴露了原始表 CRUD**——`DatabaseAdminController` 能直接改 `sys_user.password`、`sys_user_permission`,且不走软删除、无审计。
3. **N+1 查询遍布关键报表/进度接口**——一个部门 50 人的月度考核,接口要打几百次 SQL,生产环境一旦数据量上来必崩。

其他系统性问题:CORS 配置在生产仍允许 `localhost:*` 携带 cookie;JWT 密钥硬编码进 git;实体类带 `password` 字段直接被 Controller 返回;前端 token 放 localStorage;路由 `meta.roles` 是死代码……

**结论:开发节奏没问题,但需要立刻停一停做一轮安全加固,否则上线即事故。**

---

## 问题统计

| 严重级别 | 数量 | 说明 |
|---|---|---|
| 🔴 Critical (致命) | 6 | 可直接被利用造成数据泄露/越权/提权 |
| 🟠 High (高) | 9 | 性能崩塌、权限旁路、敏感信息泄露 |
| 🟡 Medium (中) | 8 | 架构异味、可维护性差、潜在风险 |
| 🟢 Low (低) | 5 | 代码风格、日志卫生、依赖更新 |
| **合计** | **28** | |

---

## Top 10 优先级清单(按修复 ROI 排序)

| # | ID | 严重 | 问题 | 修复成本 |
|---|---|---|---|---|
| 1 | SEC-001 | 🔴 | `DatabaseAdminController` 暴露原始表 CRUD,可改密码哈希、授权 | 中 |
| 2 | SEC-002 | 🔴 | `BizSelfEvaluationServiceImpl.saveSelfEval` IDOR — DTO.orgId 鉴权 + DTO.id 取记录 | 低 |
| 3 | SEC-003 | 🔴 | `BizPeerEvaluationServiceImpl.savePeerEval` 完全不校验 `evaluatorOrgId` | 低 |
| 4 | SEC-004 | 🔴 | `CorsConfig` 生产环境仍 `allowCredentials` + `localhost:*` 通配 | 低 |
| 5 | SEC-005 | 🔴 | JWT 密钥、DB 密码硬编码在 `application-dev.yml`,已入 git | 低 |
| 6 | SEC-006 | 🔴 | `SysUserController.detail` 返回实体,`password` 字段(BCrypt hash)随响应外泄 | 低 |
| 7 | PERF-001 | 🟠 | `MonthlyExamController.status/deptProgress` O(N²) N+1 查询 | 中 |
| 8 | PERF-002 | 🟠 | `BizPeerEvaluationServiceImpl.getStatistics` 双层成员循环 + selectCount | 中 |
| 9 | SEC-007 | 🟠 | `JwtAuthenticationFilter` 数据库权限为空时回退到 JWT 载荷角色 — 权限回收无效 | 低 |
| 10 | SEC-008 | 🟠 | `GlobalExceptionHandler` 把数据库异常细节("Duplicate entry"等)直接吐给客户端 | 低 |

---

## 🔴 致命问题(Critical)

### SEC-001 — 管理员"数据库管理"功能等于后门
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/controller/DatabaseAdminController.java`
**对应实现**:`service/impl/DatabaseAdminServiceImpl.java`

**问题描述**
该 Controller 提供 `GET /api/db-admin/table/{table}`、`POST /update`、`POST /insert`、`DELETE /delete-row` 等接口,白名单表里**包含 `sys_user`、`sys_user_permission`、`sys_role_menu`**。这意味着任何拿到 ADMIN 角色的人,可以:
- 直接 UPDATE `sys_user.password` 为任意 BCrypt 哈希(改成自己生成的 "admin123" 哈希),冒充任意用户登录;
- 直接 INSERT `sys_user_permission` 给自己任意 scope,完全绕过业务侧的发证/审批流程;
- `deleteRow` 走的是 `DELETE FROM ... WHERE id=?`(`DatabaseAdminServiceImpl.java:176`),**硬删,绕过 `@TableLogic` 软删除**,且没有任何审计日志。

**攻击场景**
ADMIN 内鬼/被钓鱼账号 → 调一次接口把 `BizAppeal` 申诉记录直接 DELETE,**业务侧完全无痕**;或把财务 `SUPERVISOR` 的密码哈希覆盖,接管财务岗。

**修复方案**
1. 这个功能本身**不应该存在于业务系统**。如果是 DBA 排障用,做成离线工具或运维 SSO 跳板,不要做 HTTP 接口。
2. 如果一定要保留,**至少**:
   - 从白名单移除一切 `sys_*` 表;
   - 写操作走软删除,强制走 Service 层;
   - 每次操作落审计表 + 关键操作走双人复核;
   - 接口加 IP 白名单 + 二次密码确认。

**修复成本**:中(建议直接下线该模块,改运维流程)

---

### SEC-002 — 自评保存 IDOR(越权改任意人的自评)
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:186-214`

**问题代码**(逻辑还原)
```java
public BizSelfEvaluation saveSelfEval(BizSelfEvaluationDTO dto, String createdBy) {
    validateOrgAccess(dto.getOrgId());            // ① 用 DTO 里自带的 orgId 做范围校验
    BizSelfEvaluation entity;
    if (dto.getId() != null) {
        entity = getById(dto.getId());            // ② 直接按 DTO 里的 id 取记录
        // ... copyProperties 然后 updateById
    }
}
```

**为什么是问题**
攻击者 Alice(org=10)想改 Bob(org=20)的自评(id=999):
- 请求体里 `orgId=10`(自己的),`id=999`(Bob 的),其他字段塞自己想要的分数;
- `validateOrgAccess(10)` 通过(Alice 当然能访问自己的 org);
- `getById(999)` 拿到 Bob 的记录,然后 `BeanUtils.copyProperties(dto, entity)` 用 DTO 字段覆盖,`updateById` 保存。
- **Bob 的自评被 Alice 改了,且新 orgId 仍是 Bob 的(因为 copy 不会改 PK 关联字段)或被改成 Alice 的——两种结果都灾难**。

**修复方案**
```java
if (dto.getId() != null) {
    entity = getById(dto.getId());
    if (entity == null) throw new BusinessException("记录不存在");
    validateOrgAccess(entity.getOrgId());          // ✅ 用 DB 里实际记录的 orgId 校验
    // 禁止 dto 修改 orgId/userId 等主键关联字段
}
```
所有 `saveXxx` 类接口都按这条规则审一遍。

**修复成本**:低(单文件修改,但需要在所有 Service 复查)

---

### SEC-003 — 互评保存完全不校验评分组织
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizPeerEvaluationServiceImpl.java:253` 附近

**问题描述**
`savePeerEval(dto)` 中 `dto.getEvaluatorOrgId()` 直接落库,**没有任何校验**该用户是否属于 `evaluatorOrgId` 部门,也没校验该部门是否在用户的数据范围内。

**攻击场景**
任意能登录的用户都可以以"任意部门"的身份给"任意被评部门"打分。互评数据完全可以伪造,月度考核结果失真。

**修复方案**
保存前必须:
1. 从 `SecurityUtil` 拿当前用户的真实组织;
2. 校验 `evaluatorOrgId` 必须 ∈ 当前用户的合法评分主体集合(取决于业务规则);
3. 禁止前端传 `evaluatorOrgId`,改由后端从用户上下文推断。

**修复成本**:低

---

### SEC-004 — CORS 配置在所有环境都允许 localhost 通配 + 带凭证
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/config/CorsConfig.java`

**问题代码**
```java
config.addAllowedOriginPattern("http://localhost:*");
config.setAllowCredentials(true);
```

**为什么是问题**
- `allowCredentials=true` + 通配 origin 是浏览器明令禁止的高危组合(规范上允许 pattern,但语义同样危险);
- 即使在生产部署,任何运行在用户本机 `http://localhost:任意端口` 的页面,都能拿着用户的 cookie/Authorization 调你的 API;
- 攻击者只需诱导用户访问一个本地起的恶意页面(钓鱼邮件附件、IDE 插件、桌面应用嵌入浏览器),即可触发 CSRF + 数据外泄。

**修复方案**
```java
@Value("${app.cors.allowed-origins}")
private List<String> allowedOrigins;          // 从 application-{profile}.yml 注入

config.setAllowedOrigins(allowedOrigins);     // 显式白名单,生产只有内网域名
config.setAllowCredentials(true);
```
`application-dev.yml` 可以放 `http://localhost:5173`,`application-prod.yml` 放真实域名。

**修复成本**:低

---

### SEC-005 — JWT 密钥与数据库密码硬编码进 git
**文件**:`assessment-backend/src/main/resources/application-dev.yml`

**问题代码**(还原)
```yaml
jwt:
  secret: ${JWT_SECRET:assessment-secret-key-2026-secure}
spring:
  datasource:
    password: ${DB_PASSWORD:root}
```

**问题**
- 默认值就是真实密钥/密码,git 历史里能搜到 → 任何能 clone 仓库的人都能伪造 JWT、连数据库;
- HS256 + 公开的密钥 = 攻击者可以签发**任意 userId / 任意 roleCode** 的 token,直接拿 ADMIN 权限登录;
- `assessment-secret-key-2026-secure` 这种短字符串本身熵不足,即使没泄露也容易被字典暴破。

**修复方案**
1. 立刻轮换 JWT secret 和 DB 密码;
2. 默认值改成会让程序启动失败的占位符(`REQUIRED_FROM_ENV`),强制运维注入环境变量;
3. 将 `application-dev.yml` 的真实凭据从 git 历史中清除(`git filter-repo` 或换库);
4. 生产用 Vault/AWS Secrets Manager;dev 用 `.env`(写进 `.gitignore`)。

**修复成本**:低(改配置)+ 中(轮换 + git 历史清理 + 通知所有签发过的 token 失效)

---

### SEC-006 — 用户详情接口返回实体类,密码哈希直接外泄
**文件**:
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserController.java`(`detail` / `list` 等方法)
- `assessment-backend/src/main/java/com/ccerphr/assessment/entity/SysUser.java`(`@Data` + `private String password;`)

**问题描述**
`SysUser` 实体上 `@Data` 自动生成 `getPassword()`,Controller 用 `Result<SysUser>` 或 `Result<List<SysUser>>` 直接返回。Jackson 默认会把 `password` 字段序列化进 JSON,BCrypt 哈希明明白白地发给前端。

**攻击场景**
- 拿到哈希 → 离线暴破弱密码;
- 哈希泄露本身已经是合规事故(等保/GDPR/个保法均有禁止性条款)。

**修复方案**
1. 短期:`SysUser.password` 上加 `@JsonIgnore`(注意不能影响登录流程读取);
2. 长期:所有对外接口禁止返回实体,统一返回 VO/DTO,Controller 层做显式字段映射。可借助 MapStruct。

**修复成本**:低(`@JsonIgnore`) / 中(全面 VO 化)

---

## 🟠 高危问题(High)

### SEC-007 — JWT 权限校验在 DB 为空时回退到 JWT 载荷
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/security/JwtAuthenticationFilter.java:70-89`

**问题**
查 `sys_user_permission` 返回空时,代码回退到 JWT 内嵌的 `fallbackRoleCode` 构造 Authentication。
**后果**:管理员收回某用户全部权限后,该用户**仍可使用旧 JWT 用原角色访问系统**直到 token 过期(可能数小时甚至更久)。

**修复**
回退路径整体删除。权限被收回 → 拒绝访问,要求重新登录。如果担心"刚发证还没生效",在发证时清缓存,而不是在校验时打补丁。

**成本**:低

---

### SEC-008 — 全局异常处理器泄露数据库 schema 提示
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/common/GlobalExceptionHandler.java:49-62`(`handleSQLIntegrity*` 分支)

**问题**
直接把 `DataIntegrityViolationException` 里的 "Duplicate entry 'xxx' for key 'sys_user.uk_username'"、"Column 'org_id' cannot be null" 等信息回写给前端。攻击者用这些信息能反推表名、唯一索引、字段约束。

**修复**
异常处理统一返回脱敏后的"操作失败,请联系管理员",原始信息只写 log。区分客户端可见消息和服务端日志消息。

**成本**:低

---

### SEC-009 — 数据范围切换接口的会话语义与 JWT 矛盾
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:237-272`(`switchRole`)

**问题**
JWT 模式声称无状态,但 `switchRole` 把 `ACTIVE_ROLE_CODE` / `ACTIVE_SCOPE_ID` 存到 `HttpSession`,后续 `DataScopeInterceptor`/`RoleCheckAspect` 又靠 session 决定数据范围。
**后果**:
- 同一 JWT 在不同 session(不同浏览器/设备)下数据范围不同,审计混乱;
- session 丢失时(session 超时、Redis 节点切换)用户突然"什么都看不见",但 token 还有效;
- 移动端/SPA 重启 → 默认范围回到第一个角色,不是最后用的角色,体验也差。

**修复**
要么彻底无状态(active role 放进新签发的 JWT 或前端记忆 + 每次 API 带过来,服务端校验);要么彻底有状态(JWT 改 server-side session)。两套混用必出事。

**成本**:中(需架构决策)

---

### SEC-010 — 文件上传:扩展名白名单 + MIME 嗅探仍不够
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizAppealController.java:128-177`

**已经做对的**:扩展名白名单、MIME 嗅探(PDF/JPEG/PNG 魔数校验)、UUID 重命名、路径穿越防护。

**不足**:
1. `.docx/.xlsx/.csv` 没有魔数校验(实际是 zip 包),可被 polyglot 攻击;
2. 没有杀毒扫描,内网钓鱼可以传带宏的 .docm 改后缀为 .docx;
3. 下载接口 `Content-Type` 用 DB 里存的 `fileType`,如果 DB 被污染(参考 SEC-001)可以伪造成 `text/html` 触发反射 XSS;
4. 上传目录 `./uploads/appeal` 如果落在 web root 下且配置 nginx 直挂目录,后果更严重。

**修复**
- docx/xlsx 至少校验 ZIP 头(50 4B 03 04);
- 下载时 `Content-Type` 用扩展名→MIME 映射,不要信 DB;
- 上传目录强制脱离 web root,所有下载走应用层;
- 集成 ClamAV 之类的扫描。

**成本**:中

---

### PERF-001 — 月度考核状态接口 O(N²) 查询
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/controller/MonthlyExamController.java`(`status`、`deptProgress` 方法)

**问题**
循环遍历部门成员,每人内部再 `selectCount` 查自评/互评是否完成,部门 50 人就是 100+ 次查询,公司 50 个部门一次刷新就 5000+ 查询。

**修复**
单条聚合 SQL:
```sql
SELECT user_id,
       COUNT(CASE WHEN type='self' THEN 1 END) AS self_done,
       COUNT(CASE WHEN type='peer' THEN 1 END) AS peer_done
FROM biz_xxx
WHERE org_id = ? AND period = ?
GROUP BY user_id
```
然后 Java 侧拿 Map 拼装。

**成本**:中

---

### PERF-002 — 互评统计接口双层成员循环
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizPeerEvaluationServiceImpl.java:330`(`getStatistics`)

**问题**
外层遍历评分方部门成员,内层遍历被评方部门成员,内层每次 `selectCount` 查"该评分人对该被评人是否打过分"。**O(M×N) 次 SQL**。

**修复**
预查一次该 period + 双方 org 的所有 peer_evaluation,Java 内存里建 `Map<evaluatorUserId, Set<targetUserId>>`,后续判断 O(1)。

**成本**:中

---

### PERF-003 — 互评任务列表/目标部门列表 N+1
**文件**:`BizPeerEvaluationServiceImpl.java:65` `getTaskList`、`:110` `getTargetDepts`

**问题**:每个部门成员都 `selectCount` 两次,放大倍数同上。
**修复**:同 PERF-002,一次查询 + 内存聚合。
**成本**:中

---

### CODE-001 — `AuthController` 直接注入 6 个 Mapper,违反分层
**文件**:`assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:45-68`

**问题**:Controller 干了 Service 该干的活(查权限、装配 userInfo Map、组装 availableRoles)。后续登录逻辑变更或需要事务、缓存,会扩散到 Controller。

**修复**:抽出 `AuthService.login()` / `AuthService.getActiveUserInfo()`,Controller 只做参数验证 + 转发。

**成本**:中

---

## 🟡 中等问题(Medium)

### ARCH-001 — 角色权限校验同时存在多套机制
- `@RequireRole` AOP 拦截
- `DataScopeInterceptor` ThreadLocal
- Spring Security 的 `hasRole`(实际几乎没用)
- Session 里的 `ACTIVE_ROLE_CODE`

多套机制并存,新功能不知道该用哪个,容易漏判。建议统一为"Security + AOP",删除 Session 那一路。

---

### ARCH-002 — 前端路由 `meta.roles` 是死代码
**文件**:`assessment-frontend/src/router/index.ts`

`meta.roles` 在每条路由都定义,但 `router.beforeEach` 实际只调 `userStore.hasPathAccess(to.path)`,而 `hasPathAccess` 只查后端给的 `allowedPaths` 列表,**完全不看 `meta.roles`**。
**后果**:开发者看到 meta 配置以为生效,实际不生效,容易误判权限。
**修复**:要么用起来(在 beforeEach 里加 role 校验),要么删干净。

---

### ARCH-003 — 实体类全部 `@Data`,业务字段和 DB 字段混在一起
影响 SEC-006 的扩散面。建议:
- 实体只放 DB 字段,不放任何 transient 计算字段;
- Controller 输入用 DTO(`@Valid`),输出用 VO;
- MapStruct 做转换。

---

### DB-001 — 软删除字段 `deleted` 在多处手写 `eq(..., 0)`
**问题**:`@TableLogic` 配置后,`selectList`/`updateById` 会自动加 `deleted=0`,但代码里手动 `LambdaQueryWrapper.eq(...::getDeleted, 0)` 的地方很多(AuthController 也有),冗余。
**修复**:删除手写过滤,信任 `@TableLogic`。`@TableLogic` 不工作的地方查 entity 注解。

---

### DB-002 — 部分批量更新没有事务边界
扫描 `BizMonthlyExamServiceImpl`、`BizReviewScoreServiceImpl` 的批量"生成考核单"动作,多个 insert/update 没有 `@Transactional`,异常时数据脏。
**修复**:批量动作统一加 `@Transactional(rollbackFor = Exception.class)`。

---

### VAL-001 — 登录接口用 `Map<String,String>` 而非 DTO
**文件**:`AuthController.java:137`
**问题**:没有 `@Valid`,用户名/密码长度、字符集等校验靠手写 `isEmpty`。建议改成 `LoginDTO` + Jakarta Validation。
**成本**:低

---

### FRONT-001 — token 存 localStorage,XSS 即提权
**文件**:`assessment-frontend/src/api/request.ts` 与 `src/stores/user.ts`

任何前端 XSS(富文本预览、第三方 SDK、依赖污染)→ 攻击者读 token → 模拟用户调任意 API。
**修复**:
- 改 HttpOnly + Secure cookie + SameSite=Strict;
- 配合后端 CSRF token(或者 SameSite=Strict 已足够 + 严格 CORS)。
- 严格 CSP:`script-src 'self'`,禁用 inline。

---

### FRONT-002 — `localStorage` 中堆放 `userInfo`/`dataScope`/`scopeId` 等可改字段
**文件**:`assessment-frontend/src/stores/user.ts`

虽然后端最终鉴权不依赖前端 storage,但攻击者改 localStorage 可能触发"看到不该看的菜单"或"按钮渲染异常",带来 UI 层信息泄露(菜单名 = 业务模块名)。
**修复**:权限控制完全后端 driven,不要在 storage 里持久化"当前角色",每次启动重新拉。

---

## 🟢 低优问题(Low)

### CFG-001 — Spring Boot 3.2.5 已有数个 CVE,建议升级
- CVE-2024-38816(StaticResource 路径穿越)
- CVE-2024-38821(Spring Security WebFlux 鉴权绕过,虽你是 MVC,但应整体盘点)
- 建议升级至 3.2.x 的最新 patch 或 3.3.x。`pom.xml` 同时把 `mybatis-plus` 升级到 3.5.7+。

### CFG-002 — `pom.xml` 没有 `dependencyManagement` 锁版本
传递依赖版本漂移,后续可能引入有 CVE 的 jar。建议加 dependency-check 插件做 CI 扫描。

### CODE-002 — 大量 `log.info("...{}", obj)` 没有判级别也无脱敏
登录失败打了 username 是合理的,但其他地方有打 `dto.toString()` 的,会把 BCrypt 哈希、token 写进日志文件。检查所有 `log.info/debug` 的实体打印。

### CODE-003 — `BizAppealController.deleteAttachment` 吃掉 IOException
```java
try { Files.deleteIfExists(...); } catch (IOException ignored) {}
```
文件没删成功,数据库记录已删,磁盘垃圾累积、且失败无人知晓。至少要 `log.warn`。

### CODE-004 — `getRoleName` 每次切换都查 DB(`AuthController.java:126`)
登录就是几个角色的事,可缓存或一次性 in 查。访问压力大时是热点。

---

## 一周修复清单(必须做)

1. **SEC-001** 下线或大幅收紧 `DatabaseAdminController`,从白名单移除 `sys_*` 表
2. **SEC-002 / SEC-003** 修复 self-eval / peer-eval 的 IDOR,统一用 DB 记录的 orgId 鉴权
3. **SEC-004** CORS 改成显式白名单,按 profile 注入
4. **SEC-005** 轮换 JWT secret + DB 密码,移除硬编码默认值
5. **SEC-006** `SysUser.password` 加 `@JsonIgnore`,SysUserController 改返回 VO
6. **SEC-007** 删除 JwtAuthenticationFilter 的角色回退分支
7. **SEC-008** 全局异常脱敏
8. **PERF-001 / PERF-002 / PERF-003** 三个 N+1 接口改聚合查询(上线前不修,QA 阶段一压测就崩)

---

## 一天修复清单(立刻能改)

> 这些都是 30 分钟内能合并的 PR,先把出血点止住。

1. `application-dev.yml` 默认值改成 `REQUIRED_FROM_ENV`,强制环境注入(SEC-005)
2. `CorsConfig` 改成读 `app.cors.allowed-origins` 列表(SEC-004)
3. `SysUser.password` 加 `@JsonIgnore`(SEC-006)
4. `JwtAuthenticationFilter` 删除 fallback 回退(SEC-007)
5. `GlobalExceptionHandler` 把 `Duplicate entry` 等替换为通用文案(SEC-008)
6. `BizPeerEvaluationServiceImpl.savePeerEval` 加 `evaluatorOrgId` 校验(SEC-003)
7. `DatabaseAdminController` 白名单删除 `sys_user`、`sys_user_permission`、`sys_role_menu`(SEC-001 临时止血)
8. 前端 `meta.roles` 要么用起来要么删掉(ARCH-002)
9. `BizAppealController.deleteAttachment` 的 `catch (IOException ignored)` 改成至少打 warn(CODE-003)
10. 检查并加上 `@Transactional` 注解(DB-002)

---

## 给团队的总体建议

1. **建立"DTO 不可信"的纪律**:所有从 DTO 来的 ID 必须先取记录再校验,**绝不**用 DTO 自带的 orgId/userId 鉴权。把这条作为 PR review 的硬性 checklist。
2. **架构上做一个抉择**:JWT 完全无状态 vs Session 完全有状态。当前的混合模式既享受不到 JWT 的水平扩展优势,又承担了 session 的不一致风险。建议彻底转 JWT,把 active role 写进 JWT 的 claim,每次切换重发 token。
3. **N+1 是这个项目的系统性问题**,不是单点。建议引入 p6spy 或 datasource-proxy,本地开发自动统计每个 HTTP 请求的 SQL 次数,超过 10 条就 warn。
4. **管理员后门必须下线**。`DatabaseAdminController` 这种东西在生产系统是底线问题,不是优化项。
5. **依赖与配置进 CI**:加 OWASP dependency-check + secret-scanning(detect-secrets / gitleaks),把 SEC-005 那种事故拦在合并前。

---

*评审者:Claude — 严格、直接、基于源码证据。如对某一条结论有异议,请提供反例代码段,我会复核。*
- 生产前检查：测试阶段保留项需重新评估。

---

## 当前测试阶段例外项（2026-05-27）

以下两项经用户确认在测试阶段需要保留，后续代码扫描和修复不要再作为当前阶段必须删除的问题处理：

1. 登录页展示测试账号并支持点击自动填充：用于快速切换 `admin / 123456`、`tuke01 / 123456` 等测试身份。
2. dev 环境启用“系统运维/数据库查询”：用于测试阶段垃圾数据清理。生产环境仍应关闭，且 `APP_DB_ADMIN_ENABLED=false` 可手动覆盖。

在用户明确确认进入清理阶段后，再移除登录页测试账号快捷入口并关闭数据库查询功能。
