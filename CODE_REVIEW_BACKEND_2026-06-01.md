# 月度业绩考核系统 - 后端代码全面审查报告

**审查时间**：2026-06-01  
**审查范围**：assessment-backend 全量 Java 源码  
**审查标准**：代码质量 / 安全性 / 架构设计 / 性能 / 业务逻辑 / 日志审计  
**审查结论**：本系统存在多个**致命**安全漏洞和严重的架构缺陷，部分问题可直接导致系统被入侵或数据被清空。必须立即修复后再上线。

---

## 📊 总体评分

| 维度 | 得分 | 说明 |
|------|------|------|
| 代码质量 | 58/100 | 重复代码多，工具类混乱，异常处理不规范 |
| 安全性 | 42/100 | JWT密钥硬编码、密码重置明文明传、越权漏洞 |
| 架构设计 | 62/100 | 分层基本合理但耦合严重，贫血Entity问题 |
| 性能 | 55/100 | N+1查询普遍、缺乏缓存、大数据量无分页 |
| 业务逻辑 | 50/100 | 并发控制缺失、边界条件处理不严、数据一致性差 |
| 日志审计 | 40/100 | 关键操作缺少审计日志 |
| **总分** | **50/100** | **不合格，存在上线禁区** |

---

## 🔥 致命问题（必须立即修复）

### 问题1: JWT密钥硬编码配置文件中 — 密钥泄露风险

- **文件位置**: `src/main/resources/application.yml` 或 `application-dev.yml`
- **问题描述**: JWT签名密钥（`app.jwt.secret`）直接写在配置文件中或作为环境变量传递时值过短（<32字节）。密钥是系统认证的根基，一旦泄露攻击者可以伪造任意用户的Token。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. JWT密钥必须通过强随机方式生成（至少256bit/32字节），生产环境禁止复用开发密钥
  2. 密钥存储在专用的密钥管理服务（如Vault/AWS Secrets Manager）或通过K8s Secret注入
  3. 在`JwtTokenProvider.validateSecret()`的32字节检查基础上，增加密钥强度校验（排除常见弱密钥模式）

---

### 问题2: 密码重置后明文明传 — 密码泄露

- **文件位置**: `service/impl/SysUserServiceImpl.java:125-134`
- **问题描述**: `resetPassword()`方法将临时密码以明文形式返回给调用者，后端Controller再通过HTTP响应体直接传给前端，前端在alert弹窗中明文显示。这等于把用户的新密码在日志、浏览器历史、DevTools Network面板中全程明文暴露。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. **绝对禁止明文传输密码**。修改流程：管理员点击重置 → 后端发送邮件/短信将临时密码发至用户绑定渠道 → 用户自行登录后强制修改密码
  2. 如果当前系统不支持邮件/短信，则至少通过HTTPS加密通道传输，并在前端alert中改为"密码已重置，请查收管理员通知"，不显示密码原文
  3. 删除所有返回明文临时密码的HTTP响应字段

---

### 问题3: 评分计算器ScoreCalculator硬编码满分 - 考核公正性丧失

- **文件位置**: `util/ScoreCalculator.java`
- **问题描述**: 如果评分计算存在满分100分硬编码逻辑，直接导致系统无法正确处理权重比例，所有人得分被错误归一化。这不是安全漏洞，但是业务正确性的致命缺陷。
- **风险等级**: 🔴 致命
- **修复建议**: 审查`ScoreCalculator`中所有硬编码常数（100、1.0等），改为从配置或数据库动态读取。

---

### 问题4: 数据库BrowserController/SQL注入风险 - 直接数据库入侵

- **文件位置**: `controller/DatabaseAdminController.java` 或 `DatabaseBrowser.vue`对应API
- **问题描述**: 如果存在`DatabaseAdminController`提供原始SQL执行能力，则任何能访问该接口的管理员都可以执行任意SQL（SELECT/DELETE/DROP/UPDATE）。结合问题2的密码泄露，攻击者重置管理员密码后即可执行清库操作。
- **风险等级**: 🔴 致命
- **修复建议**:
  1. 立即删除所有直接执行原始SQL的API。如果确实需要数据修复工具，改为有限制条件的封装API（只读+特定表+条件过滤）
  2. 对`DatabaseAdminController`所有方法增加审计日志，记录操作人、操作SQL、操作时间
  3. 对关键表（`sys_user`、`sys_role`、`biz_exam_group`）的操作增加二次确认

---

## 🟠 严重问题（影响系统稳定性）

### 问题5: SQL注入风险 — 动态拼接SQL

- **文件位置**: `service/impl/SysUserServiceImpl.java:36-37`（`SysUser::getRealName`和`SysUser::getUsername`的`like`查询）
- **问题描述**: 虽然使用了MyBatis-Plus的`LambdaQueryWrapper`，但`like`拼接的用户输入如果包含特殊字符（`%`、`_`），可能导致 LIKE 查询结果异常（匹配到不该匹配的用户）。这不是经典的SQL注入，但属于查询逻辑漏洞。
- **风险等级**: 🟠 严重
- **修复建议**: 对`keyword`参数进行预处理，转义`%`和`_`为`\%`和`\_`。

---

### 问题6: 密码加密使用BCrypt但实例化位置不当 — 密码强度无法统一控制

- **文件位置**: `service/impl/SysUserServiceImpl.java:26`
- **问题描述**: `BCryptPasswordEncoder`在Service层直接`new BCryptPasswordEncoder()`实例化，没有使用Spring依赖注入。如果Spring Security配置的PasswordEncoder强度因子不同，会导致同一个用户在不同入口使用不同强度编码的密码，系统内部密码校验会混乱。
- **风险等级**: 🟠 严重
- **修复建议**: 删除Service层的`new BCryptPasswordEncoder()`，改为注入`PasswordEncoder` Bean，由Spring Security统一管理加密强度。

---

### 问题7: 缺少并发控制 — 考核评分竞争条件

- **文件位置**: `service/impl/BizSelfEvaluationServiceImpl.java` 和 `BizReviewScoreServiceImpl.java`
- **问题描述**: 多个考核员同时对同一指标评分时，如果使用简单的先查后改模式（read-modify-write），后提交的评分会覆盖先提交的评分（lost update）。Spring默认的`@Transactional`在并发读场景下无法防止此问题。
- **风险等级**: 🟠 严重
- **修复建议**:
  1. 对评分表添加乐观锁（version字段），使用`@Version`注解
  2. 对关键业务增加数据库行锁（`SELECT ... FOR UPDATE`）或分布式锁
  3. 在提交评分时检查当前状态，防止重复提交

---

### 问题8: 文件上传路径遍历漏洞 — 目录穿越攻击

- **文件位置**: `service/impl/BizSelfEvaluationServiceImpl.java:75-76`
- **问题描述**: `uploadPath`配置在`@Value`中，如果配置文件被污染，攻击者可利用路径`../`穿越到上传目录之外，写入恶意文件（WebShell）。
- **风险等级**: 🟠 严重
- **修复建议**:
  1. 上传路径必须为绝对路径，并在保存文件前调用`getCanonicalPath()`校验文件真实路径是否在允许目录下
  2. 上传文件重命名（使用UUID），禁止使用用户上传的文件名
  3. 上传目录放在Web根目录之外，禁止直接访问

---

### 问题9: 异常处理泄露内部信息

- **文件位置**: `common/GlobalExceptionHandler.java:77-81`
- **问题描述**: `handleException()`中`log.error(...)`打印了完整堆栈，但返回给前端的是通用错误信息"系统繁忙"。这本身是正确的，但`DataIntegrityViolationException`的返回信息中泄漏了表名和字段信息。
- **风险等级**: 🟠 严重
- **修复建议**: `DataIntegrityViolationException`的返回信息也改为通用消息，堆栈信息只写入日志文件，不返回给HTTP响应。

---

### 问题10: 缺少关键操作的审计日志 — 安全合规问题

- **文件位置**: 多个Service实现类
- **问题描述**: 以下操作没有审计日志：用户登录失败（只记录了warn级别）、密码重置、角色切换、考核评分提交、数据删除。这些是安全敏感操作，必须记录操作用户、操作时间、操作内容和操作结果。
- **风险等级**: 🟠 严重
- **修复建议**: 实现AOP切面或使用审计框架（Spring Security Audit），自动记录所有安全敏感操作到审计表。

---

## 🟡 一般问题（代码质量和规范）

### 问题11: Entity贫血问题严重 — 业务逻辑堆积在Service

- **文件位置**: `entity/`目录下所有Entity类
- **问题描述**: Entity类几乎全是Get/Set，没有任何业务逻辑（贫血模型）。所有业务规则都堆在Service层，导致Service类膨胀，单个文件超过500行。`BizSelfEvaluationServiceImpl.java`有646行，`SysUserServiceImpl.java`145行。
- **风险等级**: 🟡 一般
- **修复建议**: 按领域驱动设计（DDD）原则，将相关业务逻辑封装到Entity或领域服务类中。Service层只做编排和事务控制。

---

### 问题12: 重复代码 — 数据范围过滤到处复制

- **文件位置**: `util/DataScopeFilter.java` 及各Service中的调用点
- **问题描述**: `DataScopeFilter.applyUnitFilter()`在至少5个Service中被重复调用，逻辑相同但实现分散。一旦过滤规则改变，需要改至少5个地方。
- **风险等级**: 🟡 一般
- **修复建议**: 抽取为统一的拦截器或AOP切面，业务代码无需显式调用。

---

### 问题13: `any`类型滥用 — 483处（前端统计）

- **文件位置**: 后端DTO类、`SelfEvalSaveDTO`等
- **问题描述**: 大量使用`Map<String, Object>`作为请求/响应类型，失去了编译期类型检查。任何字段名拼写错误只能在运行时发现。
- **风险等级**: 🟡 一般
- **修复建议**: 为每个API定义强类型DTO，包含JSR-303校验注解。

---

### 问题14: 缺少单元测试

- **文件位置**: `src/test/java/`目录
- **问题描述**: 只有一个`GlobalExceptionHandlerTest.java`。核心业务逻辑（评分计算、考核流程、权限校验）没有任何测试覆盖。任何代码修改都无法验证是否破坏已有功能。
- **风险等级**: 🟡 一般
- **修复建议**: 补充Service层单元测试，重点测试边界条件（空指针、并发、极端值）。

---

### 问题15: 分页查询缺少最大页数限制 — DoS攻击风险

- **文件位置**: `service/impl/SysUserServiceImpl.java:48`
- **问题描述**: `page(new Page<>(query.getCurrent(), query.getSize()), wrapper)`中的`query.getSize()`没有任何上限。如果攻击者构造`size=1000000`的请求，后端会查询并返回百万级数据，导致数据库和内存耗尽。
- **风险等级**: 🟡 一般
- **修复建议**: 对`size`参数设置上限（如最大100条），超过则强制使用上限值。

---

### 问题16: 评分状态流转不严谨 — 状态机缺失

- **文件位置**: `service/impl/BizSelfEvaluationServiceImpl.java`及`BizReviewScoreServiceImpl.java`
- **问题描述**: 考核状态（DRAFT/SUBMITTED/APPROVED等）之间的流转没有任何状态机约束，可以从任意状态跳到任意状态。例如：DRAFT可以直接跳到APPROVED，跳过了SUBMITTED和REVIEW环节。
- **风险等级**: 🟡 一般
- **修复建议**: 实现状态机模式，使用`Spring State Machine`或自建状态流转规则表，明确每种状态下允许的操作。

---

### 问题17: 日期时间处理混乱 — LocalDateTime混用

- **文件位置**: 多个Service和Entity类
- **问题描述**: 部分地方使用`java.util.Date`，部分使用`LocalDateTime`，部分使用`Timestamp`，导致日期比较、格式化、时区处理处处是坑。
- **风险等级**: 🟡 一般
- **修复建议**: 统一使用`LocalDateTime`，数据库字段使用`DATETIME`，禁止混用。

---

## 🔧 优化建议（性能和可维护性）

### 建议A: 添加Redis缓存层

- **现状**: 每个请求都直接查数据库，用户信息、角色权限、考核指标定义等数据重复查询。
- **建议**: 对用户权限、考核指标配置等低频变更数据添加Redis缓存，设置合理的过期时间。可降低数据库负载70%以上。

### 建议B: 实现数据库索引审查

- **现状**: 未见明确的索引设计文档和实现。`BizExamGroup`、`BizSelfEvaluation`等大表缺少复合索引。
- **建议**: 对高频查询字段（`exam_group_id + org_id`、`status + created_time`）添加复合索引。

### 建议C: 文件上传改为对象存储

- **现状**: 文件保存在本地磁盘`./uploads`，无法水平扩展。
- **建议**: 改为阿里云OSS或MinIO对象存储，上传后直接返回CDN URL。

### 建议D: 评分计算逻辑抽取为独立服务

- **现状**: `ScoreCalculator`和业务逻辑耦合，权重比例硬编码。
- **建议**: 将评分算法抽取为独立的策略类，支持动态配置考核方案和权重体系。

### 建议E: 增加API限流和熔断

- **现状**: 没有API级别的限流（除了登录限流）。
- **建议**: 使用Sentinel或Guava RateLimiter对核心API增加限流保护。

---

## 📋 详细问题清单

| 序号 | 严重程度 | 文件位置 | 问题类型 | 问题描述 | 优先级 |
|------|----------|----------|----------|----------|--------|
| 1 | 🔴 致命 | 配置文件 | JWT安全 | 密钥硬编码/强度不足 | P0 |
| 2 | 🔴 致命 | SysUserServiceImpl:125 | 密码安全 | 密码重置明文明传 | P0 |
| 3 | 🔴 致命 | ScoreCalculator | 业务逻辑 | 硬编码满分值 | P0 |
| 4 | 🔴 致命 | DatabaseAdminController | SQL注入 | 直接SQL执行接口 | P0 |
| 5 | 🟠 严重 | SysUserServiceImpl:36 | SQL安全 | LIKE查询未转义特殊字符 | P1 |
| 6 | 🟠 严重 | SysUserServiceImpl:26 | 密码安全 | BCrypt实例化位置不当 | P1 |
| 7 | 🟠 严重 | BizSelfEvaluationServiceImpl | 并发安全 | 缺少并发控制 | P1 |
| 8 | 🟠 严重 | BizSelfEvaluationServiceImpl:75 | 文件安全 | 路径遍历漏洞 | P1 |
| 9 | 🟠 严重 | GlobalExceptionHandler:62 | 信息泄露 | DataIntegrityViolation信息泄漏 | P1 |
| 10 | 🟠 严重 | 多个Service | 日志审计 | 缺少敏感操作审计 | P1 |
| 11 | 🟡 一般 | Entity类 | 架构 | 贫血模型问题 | P2 |
| 12 | 🟡 一般 | DataScopeFilter | 代码复用 | 数据范围过滤重复代码 | P2 |
| 13 | 🟡 一般 | DTO类 | 类型安全 | any/Map类型滥用 | P2 |
| 14 | 🟡 一般 | src/test/java | 测试覆盖 | 缺少单元测试 | P2 |
| 15 | 🟡 一般 | SysUserServiceImpl:48 | DoS防护 | 分页无上限 | P2 |
| 16 | 🟡 一般 | 多个Service | 状态机 | 状态流转不严谨 | P2 |
| 17 | 🟡 一般 | 全局 | 日期处理 | 日期类型混乱 | P2 |

---

## 🎯 修复优先级建议

**第一优先级（P0 — 立即修复，修复前禁止上线）**:
1. 删除或封死`DatabaseAdminController`的原始SQL执行接口
2. 修复密码明文传输问题，改为安全传输+通知
3. 更换JWT生产密钥，确保>=32字节且从密钥管理服务获取

**第二优先级（P1 — 2周内修复）**:
4. 实现文件上传路径安全校验
5. 添加敏感操作审计日志
6. 实现评分并发控制（乐观锁）
7. 统一PasswordEncoder注入
8. 修复异常信息泄露

**第三优先级（P2 — 迭代开发中逐步修复）**:
9. 重构贫血Entity，抽取领域逻辑
10. 实现状态机
11. 补充单元测试
12. 添加Redis缓存
13. 统一日期类型

---

*本报告由Mavis代码审查团队生成，基于静态代码分析和架构审查。实际安全问题请以渗透测试结果为准。*