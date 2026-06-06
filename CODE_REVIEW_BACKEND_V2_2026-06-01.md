# 月度业绩考核系统 · 后端代码审查报告（犀利版 v2）

**审查时间**：2026-06-01
**审查范围**：`D:\qoder\assessment-backend` 全量 Java 源码
**审查模型**：最新大模型直审
**总体评价**：这套代码最可怕的不是写得多烂，而是**架构上的傲慢**——把"管理员万能工具"当业务能力暴露在前端、把"权限"写死在客户端字典、把"业务"和"运维"混在同一个 Controller 里。下面是带刺的诊断。

---

## 📊 总体评分

| 维度 | 得分 | 一句话评价 |
|------|------|-----------|
| 代码质量 | 52/100 | 复制粘贴的"民主"，ServiceImpl 一水 300-500 行 |
| 安全性 | 30/100 | 不是"有漏洞"，是"压根没想安全" |
| 架构设计 | 45/100 | 数据库管理工具和业务系统住在同一个 Spring Boot 里 |
| 性能 | 55/100 | 全靠 MyBatis-Plus 兜底，N+1 普遍存在 |
| 业务逻辑 | 48/100 | 状态机缺失，并发控制看心情 |
| 日志审计 | 35/100 | 关键操作没有任何审计，想追责都追不到 |
| **总分** | **42/100** | **不及格，且不是差一分的不及格** |

---

## 🔥 致命问题（必须立即修复）

### 问题 1：`DatabaseAdminController` + `DatabaseAdminServiceImpl` —— 自杀式接口

- **文件位置**：
  - `controller/DatabaseAdminController.java:22-57`
  - `service/impl/DatabaseAdminServiceImpl.java:35-454`
- **现象**：28 张表全在白名单里随便查、随便改、随便逻辑删除。`@ConditionalOnProperty(... matchIfMissing = false)` 看似保护，但只要配置里加一行 `app.db-admin.enabled=true`，这个万能 SQL 控制台就直接挂到公网。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 写这套代码的人可能自我感觉"我做了白名单、做了一层校验、多安全啊"——但事实是：白名单 28 张表，**包括 `sys_user`、`sys_role`、`sys_user_permission` 全部业务核心表**。一旦 admin 账号泄露，等于送 `UPDATE` 权限给攻击者。
  - `wrap()` 方法用正则卡标识符，是给**真正的 SQL 注入**兜底用的；但对**业务数据破坏**（比如把 `sys_user.is_enabled` 全部改成 0、把所有 `biz_self_evaluation.status` 全部置 DRAFT）完全无能为力。
  - 最大的讽刺是：前端还有 `DatabaseBrowser.vue` 把这玩意儿封装成"友好的表格操作界面"——管理员是方便了，**审计只记了一条 "deleteRow"**。出事后连谁在什么时间改了哪条数据都查不到。
- **修复建议**：
  1. **生产环境**永远关闭（`app.db-admin.enabled` 留 false，或加 `@Profile("dev")` 限定）
  2. 上线后把 `DatabaseAdminServiceImpl` 整文件移到一个独立的 `assessment-admin-tools` 子模块，**主项目不再引用**
  3. 即便保留，所有 `updateRow` / `deleteRow` 必须写入 `sys_operation_log` 表（你表结构里已经有了！），目前**根本没接**
  4. 增加二次确认 / 操作理由 / 4-eyes 审批（重要表）

```java
// 修复样例：限制可编辑表
private static final Set<String> ALLOWED_EDIT_TABLES = Set.of(
    "sys_dict", "sys_data_sync_log", "sys_notification"
    // sys_user, sys_role, biz_exam_group 等核心表不在此列
);
```

---

### 问题 2：`resetPassword` 明文返回临时密码 —— 你这不叫"安全"，叫"礼貌的泄露"

- **文件位置**：`service/impl/SysUserServiceImpl.java:125-135`
- **现象**：`resetPassword()` 方法 return 明文密码；前端 `UserManagement.vue` 再 alert 出来。整条链路日志、HTTP 响应、浏览器历史、DevTools Network、屏幕截图到处都是密码。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 这是**整个项目里最该被打屁股的一处代码**。
  - 你说"测试阶段可以接受"——那也请加 `TODO`、加 `@Deprecated`、加注释。**没有任何标记意味着这代码会一路进生产**。
  - 更有意思的是，`SysUserServiceImpl.java:131` 用 BCrypt 加密了，`SysUserServiceImpl.java:134` 又 return 明文——**你为什么要加密一个你马上就要泄露的字符串**？这是一个糟糕的笑话。
- **修复建议**：
  1. **后端**：`resetPassword()` 不再返回明文，只返回 `void` 或一个 token。前端收到后弹出"已重置，请通知用户使用注册邮箱/手机查收"。
  2. **临时方案（必须做到）**：明文密码**绝不进入 HTTP 响应**。后端通过站内消息/邮件/SMS 发送临时密码链接 `https://.../reset?token=xxx`，用户点击后自己设置新密码。
  3. 立即给该方法加 `@Deprecated` 注释 + `// FIXME: 安全问题，禁止生产环境使用`

```java
@Deprecated
// FIXME: 返回明文密码严重违反安全规范，必须重构为发送重置链接
public String resetPassword(Long id) { ... }
```

---

### 问题 3：JWT 密钥管理 —— 检查有，但配置没说在哪

- **文件位置**：
  - `security/JwtTokenProvider.java:18-35`
  - `application.yml` / `application-dev.yml`（密钥来源未在 review 范围）
- **现象**：校验了密钥 ≥32 字节，校验了不能为空，但没说密钥**应该**从哪来。
- **风险等级**：🔴 致命（如果你把密钥写在 `application.yml` 里）
- **犀利点评**：
  - 代码检查写得很专业，但**配置侧完全是黑盒**。我看不到密钥来源。`@PostConstruct` 启动就 panic 是好事——但 panic 时打印出来的"current: X bytes"如果发到生产日志，等于公告"我密钥是 32 字节的强随机值，请来抢"。
  - 任何一个有 5 年经验的安全审计员看到这个 `validateSecret()` 第一反应是："密钥是静态的？rotate 吗？泄露检测吗？"。**你的代码里全都没回答**。
- **修复建议**：
  1. 密钥**必须**通过环境变量或 K8s Secret 注入（`app.jwt.secret=${JWT_SECRET}`），禁止写在 yml 里
  2. 增加密钥版本号字段，登录时写入 JWT claim，支持双密钥共存过渡期
  3. 校验方法里把"打印字节数"改成只 panic，不打印字节数
  4. 增加密钥轮换流程（每月自动轮换 + 黑名单旧密钥 24h）

---

### 问题 4：评分计算器硬编码 + 无状态机 —— 业务正确性自杀

- **文件位置**：
  - `util/ScoreCalculator.java`（全文件）
  - `service/impl/BizSelfEvaluationServiceImpl.java`、`BizReviewScoreServiceImpl.java`
- **现象**：`ScoreCalculator` 硬编码满分 100（基于 grep 推断），评分状态从 DRAFT 跳到 APPROVED 没有状态机约束。
- **风险等级**：🔴 致命
- **犀利点评**：
  - 一个业绩考核系统的**评分计算器硬编码满分**——这意味着所有考核方案的权重体系都被锁死了，将来想加 "A 方案 80 分 + B 方案 20 分" 之类的复合权重，要么改源码要么改不出。
  - 状态机缺失更是灾难：DRAFT 提交后进入 SUBMITTED，分管领导 review 完进入 APPROVED。这中间任何一步**理论上可以逆向**——比如 APPROVED 退回 DRAFT 而不留痕，或者 DRAFT 直接置 APPROVED 而跳过分管领导审核。这不是技术问题，**是合规问题**。
- **修复建议**：
  1. 满分、权重、计算规则都放入 `biz_exam_scheme` 表，按考核方案动态加载
  2. 引入 Spring State Machine 或自建状态流转规则表，**每次 setStatus 前必须校验 `fromState -> toState` 是否合法**
  3. 评分计算全过程留 `biz_score_audit_log`，记录每一步的输入和输出

```java
// 修复样例：状态机
private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
    "DRAFT", Set.of("SUBMITTED"),
    "SUBMITTED", Set.of("REVIEWED", "DRAFT"),  // 退回要记录原因
    "REVIEWED", Set.of("APPROVED", "REJECTED"),
    "REJECTED", Set.of("DRAFT"),
    "APPROVED", Set.of()  // 终态，不可逆
);

if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
    throw new BusinessException("非法状态流转: " + current + " -> " + target);
}
```

---

### 问题 5：业务隔离边界缺失 —— admin 工具污染业务代码库

- **文件位置**：
  - `controller/DatabaseAdminController.java`
  - `service/impl/DatabaseAdminServiceImpl.java`
  - `dto/DatabaseQueryDTO.java`、`DatabaseRowUpdateDTO.java` 等
- **现象**：上面 4 个文件加起来 500+ 行，与"业务系统"的代码混在同一个 Spring Boot 模块里。
- **风险等级**：🔴 致命（架构层面）
- **犀利点评**：
  - 这是**架构师的失职**。业务系统的 Controller、Service、Entity 应该是自洽的、围绕业务领域的；运维工具（数据库控制台、定时任务调度、缓存清理）应该独立打包。
  - 你把 `DatabaseAdminController` 放业务模块里，**任何开发者在改业务代码时都会不小心踩到它**；而它在生产环境只要被错误启用，**整个数据库的"read everything / write everything"能力就暴露了**。
  - 同样的问题可能存在于：缓存预热工具、Mock 数据生成器、日志查询 API、SQL 在线执行器。**全部都应该剥出去**。
- **修复建议**：
  1. 新建 `assessment-admin-tools` 独立 Gradle/Maven 模块，独立的 `application-tools.yml`
  2. 主项目通过 `@Profile("!tools")` 排除该模块
  3. admin-tools 部署在**内网专机**，不开公网入口
  4. 所有 admin 操作强制记录到 `sys_operation_log`（你已经有这张表，但没人写）

---

## 🟠 严重问题

### 问题 6：N+1 查询普遍存在 —— 慢查询的地狱

- **位置**：`service/impl/BizSelfEvaluationServiceImpl.java`、`BizReviewScoreServiceImpl.java` 等
- **现象**：循环里 `selectById` / `selectList`，每个外层记录都触发一次 SQL。
- **犀利点评**：
  - `BizSelfEvaluationServiceImpl` 一个 `getTaskList` 方法里，**每个 examGroup 都跑 2 次 count 查询**（submittedCount、draftCount），100 个考核组就是 200 次 SQL。
  - 你的 `getIndicators` 里也用 `evalMap` 手动做关联，本质上是在用 Java 代码模拟 SQL JOIN——这是 MyBatis-Plus 没用好的标志。
- **修复建议**：
  1. 列表页的统计查询全部改成**单条聚合 SQL**：`SELECT exam_group_id, status, COUNT(*) FROM biz_self_evaluation WHERE exam_group_id IN (?) GROUP BY exam_group_id, status`
  2. 详情页的"指标 + 自评"关联查询用 `LEFT JOIN` 一把梭
  3. 引入 `EXPLAIN` 审查，列出所有执行时间 > 50ms 的查询

---

### 问题 7：日志审计几乎为零 —— 出事查不到人

- **位置**：全部 `service/impl/*ServiceImpl.java`
- **现象**：登录失败有 log.warn，但**密码重置、角色切换、权限变更、考核评分提交、申诉处理、数据库管理操作**——所有这些**安全敏感操作**零审计。
- **犀利点评**：
  - 你的 `sys_operation_log` 表设计存在，但**没有任何 Service 在写它**——这是教科书级别的"半成品"反模式。
  - 任何一个甲方安全审计员看到这个表是空的，第一反应就是"这家公司不重视合规"；任何一个 ISO 27001 / 等保测评员看到这个会**直接判定不通过**。
  - 更有意思的是：你的 `GlobalExceptionHandler` 给每个异常都生成了 `requestId`——但**这些 requestId 也没进审计日志**。等于你做了 99% 的工作，最后 1% 没做。
- **修复建议**：
  1. 用 AOP 切面拦截所有 `@RequireRole` 注解的方法，自动写入 `sys_operation_log`（userId、roleCode、method、params、ip、ua、result、requestId）
  2. 或者直接用 Spring Security 的 `AuditLogger` 框架
  3. 关键操作（密码重置、权限变更、考核提交）单独打 INFO 级日志，包含操作前后数据快照

---

### 问题 8：`ScoreCalculator` 缺单元测试 —— 等于没写

- **位置**：`src/test/java/` 整个目录
- **现象**：只有 1 个 `GlobalExceptionHandlerTest.java`，核心业务（`ScoreCalculator`、考核流程、权限校验）**零覆盖**。
- **犀利点评**：
  - 你的 `ScoreCalculator` 是整个系统业务正确性的基石——一个满分 100、权重 A%、权重 B% 的公式，**没有测试意味着没有"正确答案"**。某天有人手抖改了一个 `*` 为 `+`，你 CI 也跑不出来。
  - 这种工具类**必须 100% 行覆盖 + 100% 分支覆盖 + 多个边界用例**（空数组、单元素、负数、超大值、浮点精度）。
- **修复建议**：
  1. 立刻补 `ScoreCalculatorTest`，覆盖以下场景：所有权重和=100、超出100、负权重、空指标、单指标、四舍五入精度
  2. 给所有 Service 加 1-2 个 happy path 单测先打底
  3. 集成测试用 Testcontainers 跑真 MySQL，验证 Mapper 的 SQL 正确性

---

## 🟡 一般问题

### 问题 9：Entity 贫血 + Service 臃肿 —— 500 行的"上帝服务"

- **位置**：`BizSelfEvaluationServiceImpl.java` 646 行、`SysUserServiceImpl.java` 145 行
- **犀利点评**：一个 Service 类超过 500 行，**基本上每一个方法都在重复"查 - 改 - 存"**。这种代码用 MyBatis-Plus 的 lambda 写起来飞快，但维护起来要命。`@Transactional` 全部堆在 Service 顶层，**事务粒度爆炸**，并发场景下死锁概率高。
- **建议**：按领域拆分为 `SelfEvaluationDomainService`（领域服务）+ `SelfEvaluationRepository`（数据访问）+ `SelfEvaluationCommandService`（编排命令）。

### 问题 10：`Map<String, Object>` 满天飞 —— 类型安全的谎言

- **位置**：`controller/AuthController.java`、`service/impl/*ServiceImpl.java`
- **现象**：参数和返回值全是 `Map<String, Object>`，DTO 没几个。
- **犀利点评**：`Result<Map<String, Object>>` 是 Java 项目的"甩锅型 API"——返回啥样全凭运行时，前端/调用方只能靠猜。**这不是 Java 程序员该写出的代码**。
- **建议**：每个 API 至少 1 个请求 DTO + 1 个响应 DTO，使用 Lombok 简化样板。`AuthController` 的 `buildUserInfo` 方法应该 return `UserInfoVO` 而不是 `Map`。

### 问题 11：日期类型不统一 —— 凌晨 4 点的 bug 制造机

- **位置**：Entity 字段混用 `Date` / `LocalDateTime` / `LocalDate` / `Timestamp`
- **犀利点评**：你 `sys_user.last_login_time` 用 `LocalDateTime`，隔壁 `biz_xxx.created_time` 用 `Date`，再隔壁用 `Timestamp`——时区、序列化、Jackson 行为处处是坑。
- **建议**：全项目统一 `LocalDateTime`（数据库 `DATETIME`），Jackson 配 `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS=false`。

---

## 🛠️ 修复路线图

**P0（本周必做）**：
1. 关闭 `DatabaseAdminController`（`app.db-admin.enabled=false`）→ 长期方案：移到独立 admin-tools 模块
2. 修复 `resetPassword` 明文返回
3. 移除 `IndicatorSet.vue` 之前的"数据库浏览器"前端入口（如还没做）
4. JWT 密钥来源审查（grep 整个 repo 确认不在 yml 里）

**P1（2 周内）**：
5. 状态机改造 + 评分计算配置化
6. N+1 查询整治
7. 审计日志 AOP 落地
8. `ScoreCalculator` 单元测试

**P2（迭代期内）**：
9. Entity 重构 + DTO 体系
10. 日期类型统一
11. 提升测试覆盖率至 ≥60%

---

## 🎯 毒舌总结

> 这套代码的"原始冲动"我能理解：业务跑通就行，先活下来再说。但**它活下来的姿势太危险了**——`DatabaseAdminController` 是给业务系统装了一个"自爆按钮"，`resetPassword` 是把用户密码当众广播，状态机缺失是给合规审计员送了"零分答卷"。

> 一个**业绩考核系统**——这种 HR / 薪酬敏感的数据系统——**没有审计日志、状态机不严谨、管理员万能工具暴露**——这三个问题任何一个单独拿出来，都够让企业被合规部门开罚单。你这三个都中。

> **不要跟我扯"测试期可以"**。测试期是指"可以容忍轻微 UI bug"，不是"可以容忍系统被攻击者一键清空"。**安全和审计从来不该分测试期/生产期**，它们要么有，要么没有。

> 立刻把这份报告**逐条读完**，P0 四个问题本周内必须出修复 PR。否则下次代码审查我建议直接叫"安全审计组"而不是"代码审查"。

---

*本报告由 Mavis 集成最新大模型直审生成，基于一手代码阅读 + 静态分析。*
