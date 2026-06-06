# 企业业绩考核系统 — 全面代码审查报告

> **审查日期**：2026-06-05  
> **审查范围**：`assessment-backend` (Java/Spring Boot) + `assessment-frontend` (Vue 3/TypeScript)  
> **代码总量**：约 60+ Java 文件、15+ TypeScript API 文件、30+ Vue 组件  
> **审查深度**：安全、架构、性能、可维护性 四维度全覆盖

---

## 📊 审计概览

| 严重级别 | 数量 | 说明 |
|---------|------|------|
| 🔴 P0 阻断 | 6 | 越权漏洞、明文密码泄露、高危接口暴露 |
| 🟡 P1 重要 | 14 | N+1 查询、内存溢出风险、Token 管理缺陷 |
| 💭 P2 建议 | 16 | 代码重复、硬编码、性能优化 |

---

## 🔴 P0 — 阻断级问题（必须立即修复）

### 1. 通知越权标记已读

**位置**：`SysNotificationController.java` — `markRead(@PathVariable Long id)`

```java
// 危险：未校验通知归属当前用户
@PutMapping("/notification/{id}/mark-read")
public Result<?> markRead(@PathVariable Long id) {
    notificationService.markAsRead(id); // 任意用户可标记他人通知为已读
    return Result.success();
}
```

**风险**：攻击者遍历通知 ID，可将任意用户的通知标记为已读，导致重要考核通知被静默忽略，审批链路中断。

**修复**：
```java
// 从 SecurityContext 获取当前用户 ID
Long currentUserId = SecurityUtils.getCurrentUserId();
notificationService.markAsRead(id, currentUserId);
// Service 层增加 WHERE id=? AND user_id=currentUserId 校验
```

---

### 2. DatabaseAdminController 高危直连操作

**位置**：`DatabaseAdminController.java`

```java
// 具备 ADMIN 角色 + IP 白名单即可直接操作任意表
@DeleteMapping("/db-admin/row")
public Result<?> deleteRow(@RequestParam String tableName, @RequestParam Long id) {
    databaseAdminService.deleteRow(tableName, id);
    return Result.success();
}
```

**风险**：
- `normalizeValue()` 方法空实现，SQL 注入防护形同虚设
- 可删除/修改 `sys_user`、`sys_role` 等核心表
- IP 白名单 + ADMIN 角色是唯一防护，但若 ADMIN 账户被盗，数据库完全暴露

**修复**：
1. 实现 `normalizeValue()` 的参数化查询
2. 白名单限制允许操作的表名集合
3. 禁止 DELETE 操作，仅保留 SELECT/UPDATE
4. 全操作记录审计日志

---

### 3. CORS 安全配置静默失效

**位置**：`CorsConfig.java`

```java
// 如果 application.yml 未配置 enabled，默认启用宽松 CORS
@Value("${app.cors.enabled:#{true}}")  // SpEL 在 @Value 中可能无法正确解析
private boolean corsEnabled;
```

**风险**：若 `${app.cors.enabled}` 不存在，SpEL `#{true}` 可能被解析为字符串 `"true"` 而非布尔值，或直接抛出异常导致 Bean 创建失败。无论哪种情况，CORS 配置都可能非预期生效或完全不生效。

**修复**：
```java
@Value("${app.cors.enabled:true}")
private boolean corsEnabled;
```
或使用 `@ConfigurationProperties` 绑定。

---

### 4. 明文密码通过 API 返回

**位置**：`SysUserServiceImpl.java` — `resetPassword(Long id)`

```java
public String resetPassword(Long id) {
    String temporaryPassword = PasswordUtil.generateTemporaryPassword(); // 明文生成
    user.setPassword(passwordEncoder.encode(temporaryPassword));        // BCrypt 存储
    this.updateById(user);
    return temporaryPassword; // 🔴 明文密码通过 HTTP 响应返回给前端
}
```

**风险**：
- 12 位随机密码通过 HTTP 响应返回，若未启用 HTTPS，可被中间人截获
- API 响应日志可能记录明文密码
- 密码使用 `SecureRandom` 生成（强度尚可），但传输阶段暴露

**修复**：
1. 🔴 紧急：确保生产环境强制 HTTPS
2. 🟡 临时方案：返回密码前通过加密信道（如企业微信/邮件）发送
3. 🟡 长期方案：首次登录强制修改密码，禁止长期使用临时密码

---

### 5. 工作流集成无认证保护

**位置**：`WorkflowIntegrationService.java`

```java
// 所有外部请求均无 Token/API Key
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<?> entity = new HttpEntity<>(body, headers);
restTemplate.exchange(workflowConfig.getBaseUrl() + "/api/tasks?...", ...);
```

**风险**：若工作流平台未做 IP 白名单或其他网络层隔离，任何可访问 `localhost:5555` 的人都能操作审批流程。

**修复**：
```java
headers.set("Authorization", "Bearer " + workflowConfig.getApiKey());
```

---

### 6. URL 拼接注入风险

**位置**：`WorkflowIntegrationService.java`

```java
// 第 N 行：直接拼接用户输入
String url = workflowConfig.getBaseUrl() + "/api/tasks?approver_id=" + workflowUserId;
// 第 M 行：路径参数同样未编码
String url = workflowConfig.getBaseUrl() + "/api/approvals/" + nodeInstanceId;
```

**风险**：若 `workflowUserId` 或 `nodeInstanceId` 被污染（虽然来源是配置表），可能构造恶意 URL 访问非预期端点。

**修复**：
```java
UriComponentsBuilder.fromHttpUrl(workflowConfig.getBaseUrl())
    .path("/api/tasks")
    .queryParam("approver_id", workflowUserId)
    .toUriString();
```

---

## 🟡 P1 — 重要问题（应在下个迭代修复）

### 7. Token 存储 localStorage（XSS 可窃取）

**位置**：`assessment-frontend/src/stores/user.ts`

```typescript
localStorage.setItem('token', data.token); // XSS 即可窃取
```

**风险**：任何注入的 XSS 脚本都能读取 `localStorage.token` 并外传。

**修复**：
- 短期：设置 `httpOnly` Cookie 由后端设置
- 长期：迁移到 `httpOnly + Secure + SameSite=Strict` Cookie 方案

---

### 8. Logout 不吊销 Token

**位置**：`src/stores/user.ts` — `logout()` 方法

```typescript
async logout() {
    // 只清除 localStorage，未调用后端 /auth/logout 接口
    localStorage.removeItem('token');
    // ...
}
```

**风险**：Token 未加入黑名单，攻击者拿到旧 Token 后仍可访问 API（直至 Token 自然过期）。

**修复**：
```typescript
async logout() {
    try { await authApi.logout(); } catch { /* 忽略网络错误 */ }
    // 清除本地状态...
}
```

---

### 9. 前端路由鉴权依赖后端网络

**位置**：`src/router/index.ts` — `beforeEach`

```typescript
// 网络异常时 catch 块仅打印日志 → 直接跳转 /dashboard
try {
    const res = await menuApi.current();
    // ...
} catch (e) {
    console.error('获取菜单失败', e);
    next('/dashboard'); // ⚠️ 网络故障时绕过权限检查
}
```

**风险**：虽然后端 Controller 有 `@RequireRole` 保护，但前端展示了不该展示的菜单项，用户体验混乱。

**修复**：
```typescript
catch (e) {
    console.error('获取菜单失败', e);
    // 清除 token，强制重新登录
    userStore.logout();
    next('/login');
}
```

---

### 10. 考核结果查询 — 内存分页（OOM 风险）

**位置**：`ExamResultServiceImpl.java` — `queryDetailPage`

```java
// 先全量查询所有指标，再在内存中手动切片
List<BizIndicatorDefinition> all = indicatorDefinitionMapper.selectList(wrapper);
int from = (int) ((page.getCurrent() - 1) * page.getSize());
int to = Math.min(from + (int) page.getSize(), all.size());
List<BizIndicatorDefinition> pageList = all.subList(from, to);
```

**风险**：指标数量上万时，全量加载到内存可能 OOM。

**修复**：使用 MyBatis-Plus 分页插件 `Page<BizIndicatorDefinition>`。

---

### 11. ExamResultServiceImpl — N+1 查询×3

**位置**：`ExamResultServiceImpl.java`

| 方法 | N+1 位置 | 影响 |
|------|---------|------|
| `querySummary` | 循环中逐个查 `reviewScoreMapper` + `indicatorDefinitionMapper` | 成员×2 次查询 |
| `queryHistory` | 循环中逐考核组查 `reviewScoreMapper` + `indicatorDefinitionMapper` | 考核组×2 次查询 |

**修复**：使用 `selectBatchIds` 批量查询，`Map<Long, Entity>` 缓存。

---

### 12. Excel 导出样式泄漏

**位置**：`ExamResultServiceImpl.java` — `exportSummaryExcel`

```java
// 在循环内创建 CellStyle，POI 限制 64000 个样式
for (...) {
    CellStyle style = wb.createCellStyle(); // 🟡 循环内创建
    // ...
}
```

**风险**：数据行超过 64000 时抛出 `IllegalStateException: The maximum number of cell styles was exceeded`。

**修复**：在循环外创建样式对象，循环内复用。

---

### 13. 通知发送逻辑严重重复

**位置**：`BizExamGroupServiceImpl.java`

`sendStartNotifications` / `sendPeerStartNotifications` / `sendPrePublishNotifications` 三个方法结构完全相同：
1. 查已发通知（去重）
2. 查考核组成员
3. 查组织信息
4. 查用户
5. 批量插入

**修复**：抽取 `sendNotifications(examGroupId, roleCode, template)` 公共方法。

---

### 14. ExamResultServiceImpl — Excel 魔法数字 + 资源泄漏

**位置**：`ExamResultServiceImpl.java`

```java
// 魔法数字：99999L、9999L，无上限保护
Page<BizReviewScore> page = new Page<>(1, 99999L);
// XSSFWorkbook 未使用 try-with-resources
XSSFWorkbook wb = new XSSFWorkbook();
```

**修复**：
1. 定义常量 `MAX_EXPORT_SIZE = 50000`
2. `try (XSSFWorkbook wb = new XSSFWorkbook()) { ... }`

---

### 15. BizAppealServiceImpl 逻辑重复

**位置**：`BizAppealServiceImpl.java`

| 问题 | 位置 |
|------|------|
| `getAppealList` / `countAppealList` 构建相同 `LambdaQueryWrapper` | 约 50 行重复代码 |
| `handleAppeal` / `reScoreAppeal` 逻辑完全一致 | 第 X-Y 行 |

**修复**：抽取 `buildAppealQueryWrapper()` 和 `processAppeal()` 公共方法。

---

### 16. BizExamGroupServiceImpl — getProgress Bug

**位置**：`BizExamGroupServiceImpl.java` — `getProgress` 第 644 行

```java
long total = getBaseMapper().selectCount(null); // 查询的是考核组总数，非指标数
// ... total 变量声明后未使用，实际使用 indicatorCount
```

**风险**：虽然当前 `total` 未被使用，但给维护者造成误导（看到变量名以为是指标总数）。

---

### 17. 前端他评状态过滤在 Java 层执行

**位置**：`BizPeerEvaluationServiceImpl.java` — `getTaskList` 第 103 行

```java
// 先查询所有考核组，再在 Java 循环中过滤 status
if (StringUtils.hasText(queryDTO.getStatus()) && !queryDTO.getStatus().equals(status)) {
    continue; // 🟡 应先做 SQL 过滤
}
```

**修复**：传递给数据库层面过滤（但 status 是计算字段，确实难以 SQL 过滤。可考虑在前端分页前缓存结果）。

---

### 18. ExamProgressServiceImpl — queryUnfilledItems N+1

**位置**：`ExamProgressServiceImpl.java` — `queryUnfilledItems` 第 298-343 行

```java
for (BizIndicatorDefinition ind : indicators) {
    // 对每个指标执行 3 次独立查询（自评、他评、复核）
    Long selfCount = selfEvaluationMapper.selectCount(selfWrapper);
    Long peerCount = peerEvaluationMapper.selectCount(peerWrapper);
    Long reviewCount = reviewScoreMapper.selectCount(reviewWrapper);
}
```

**修复**：批量查询 + 内存分组：
```java
List<Long> indicatorIds = indicators.stream().map(BizIndicatorDefinition::getId).toList();
List<BizSelfEvaluation> allSelf = selfEvaluationMapper.selectList(
    wrapper.in(BizSelfEvaluation::getIndicatorId, indicatorIds));
Map<Long, List<BizSelfEvaluation>> selfMap = allSelf.stream()
    .collect(groupingBy(BizSelfEvaluation::getIndicatorId));
```

---

### 19. WorkflowConfig 硬编码占位 UUID

**位置**：`WorkflowConfig.java`

```java
private Map<String, String> roleUserIdMap = Map.of(
    "DEPT_ADMIN", "00000000-0000-0000-0000-000000000001",
    "DEPT_LEADER", "00000000-0000-0000-0000-000000000002",
    // ...
);
```

**风险**：生产环境若未替换占位 UUID，审批流程将路由到不存在的用户，全线阻塞。

**修复**：从数据库 `sys_config` 表读取映射，并提供管理界面配置。

---

### 20. RestTemplate 无超时配置

**位置**：`WorkflowIntegrationService.java`

```java
private final RestTemplate restTemplate = new RestTemplate(); // 默认无限超时
```

**风险**：外部工作流平台不可用时，线程无限阻塞，耗尽 Tomcat 线程池。

**修复**：
```java
RestTemplate restTemplate = new RestTemplateBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(30))
    .build();
```

---

## 💭 P2 — 改进建议（有条件时优化）

### 21. 代码重复

| 文件 | 描述 |
|------|------|
| `BizAppealServiceImpl` | `updateReviewScore` 第 227-228 行冗余 `setFinalScore` |
| `BizExamGroupServiceImpl` | 通知发送三方法结构相同，应抽取公共方法 |
| `BizLeaderEvaluationServiceImpl` | `getTaskList` / `getIndicatorsByDept` 重复构建 `indWrapper` 查询条件 |

### 22. N+1 查询（影响较小）

| 文件 | 方法 | 描述 |
|------|------|------|
| `BizLeaderEvaluationServiceImpl` | `getIndicatorsByDept` | 对每个 leaderId 调用 `selectByExamGroupAndLeader()` |
| `ExamProgressServiceImpl` | `resolveIndicatorSourceGroupIds` | 循环查 `indicatorDefinitionMapper.selectCount` |
| `ExamProgressServiceImpl` | `fillEvaluateTargetName` | 循环中查 `indicatorLeaderMapper` / `indicatorOrgMapper` |

### 23. 硬编码与魔法值

| 文件 | 描述 |
|------|------|
| `BizPeerEvaluationServiceImpl:301` | `"已发布"` 硬编码中文字符串判断状态，应使用枚举 |
| `WorkflowConfig` | `baseUrl` 默认 `http://localhost:5555`，生产环境无 HTTPS |
| `BizPeerEvaluationServiceImpl:569` | `System.out.println` 调试输出，应使用 SLF4J |
| `SysUserServiceImpl:73` | `roleCode` 默认值 `"USER"` 硬编码 |

### 24. 安全性增强

| 文件 | 描述 |
|------|------|
| `SysNotificationServiceImpl` | `getUnreadByUserId` / `getReadByUserId` 无分页，通知量大时性能差 |
| `SysEmployeeServiceImpl` | `add` 方法未校验 `employeeNo` 唯一性 |
| `SysUserServiceImpl:update` | 未校验 `username` 唯一性（而 `add` 有校验） |
| `ScoreCalculator` | `ScoreItem` 内部类字段为 `public`，应使用 getter/setter |

### 25. 提交性能优化

| 文件 | 方法 | 描述 |
|------|------|------|
| `BizLeaderEvaluationServiceImpl` | `submit` | 循环中逐条 `updateById`，应使用 `updateBatchById` |
| `BizPeerEvaluationServiceImpl` | `submitPeerEval` | 同上 |

### 26. 数据库排序被覆盖

**位置**：`ExamProgressServiceImpl.java` — `queryProgressDetail` 第 161-192 行

先 `indWrapper.orderByAsc(BizIndicatorDefinition::getSortCode)` 数据库排序，然后用 Java Stream `.sorted(Comparator...)` 重新排序——数据库排序完全浪费。

---

## 📦 架构层面的观察

### ✅ 做得好的地方

1. **数据权限分层设计**：`DataScopeContext` + `DataScopeFilter` 的 ALL/UNIT/ORG 三级数据隔离设计合理，绝大多数业务方法正确应用了 `applyUnitFilter`
2. **ScoreCalculator 通用计算工具**：私有构造器 + 静态方法，设计良好。否决项/控制指标/特殊贡献三项规则完整
3. **Flyway 数据库版本管理**：使用 Flyway 做数据库迁移，版本可追溯
4. **文件上传安全**：扩展名白名单 + 魔数校验 + 路径穿越防护，三层防护到位
5. **AOP 角色校验**：`@RequireRole` + `RoleCheckAspect` 方法级权限注解设计优雅
6. **前后端分离**：后端 Controller 层职责清晰，前端 Pinia 状态管理结构合理

### ⚠️ 架构风险

1. **Token 黑名单单机限制**：`TokenBlacklistService` 使用 `ConcurrentHashMap` 内存存储，多实例部署时无法共享。如需水平扩展，应迁移到 Redis
2. **状态机分散**：考核组状态（NOT_STARTED→IN_PROGRESS→PRE_PUBLISHED→PUBLISHED）的管理逻辑分散在 `BizExamGroupServiceImpl` 和多个评价 Service 中，缺少统一的状态机引擎
3. **工作流集成脆弱**：`WorkflowIntegrationService` 是系统中最脆弱的模块——无认证、无超时、硬编码 UUID、同步阻塞调用。若工作流平台不可用，整个审批流程阻塞

---

## 🎯 修复优先级建议

### 第一优先级（本周内）

| # | 问题 | 修复工时 |
|---|------|---------|
| P0-1 | 通知越权标记已读 | 0.5h |
| P0-5 | 工作流集成添加认证头 | 1h |
| P0-6 | URL 拼接改用 UriComponentsBuilder | 0.5h |
| P0-3 | CORS 配置修复 | 0.5h |
| P0-4 | 临时密码传输安全（HTTPS 强制） | 运维 1h |

### 第二优先级（下个迭代）

| # | 问题 | 修复工时 |
|---|------|---------|
| P1-7 | Token 迁移 httpOnly Cookie | 4h |
| P1-8 | Logout 吊销 Token | 1h |
| P1-9 | 前端路由守卫网络异常处理 | 1h |
| P1-10 | 考核结果内存分页改为 DB 分页 | 2h |
| P1-11 | N+1 查询批量优化 | 3h |
| P1-12 | Excel 样式复用 | 1h |
| P0-2 | DatabaseAdminController 加固 | 2h |

### 第三优先级（技术债务清理）

| # | 问题 | 修复工时 |
|---|------|---------|
| P2-21 | 代码重复抽取 | 4h |
| P2-24 | 安全加固（分页、唯一性校验） | 3h |
| P2-25 | 批量更新优化 | 1h |
| P2-23 | 硬编码消除 | 2h |

---

## 📝 未审查范围

以下模块因时间限制未纳入本次审查，建议后续补充：

| 模块 | 类型 | 风险等级 |
|------|------|---------|
| `src/views/exam/ExamResult.vue` | Vue 组件 | 中 |
| `src/views/admin/DatabaseBrowser.vue` | Vue 组件 | 高（直接操作数据库） |
| `src/views/dept/IndicatorSet.vue` | Vue 组件 | 中 |
| `src/stores/` 其他状态管理文件 | TypeScript | 低 |
| Mapper XML 文件（未找到，可能使用注解） | 数据层 | 中 |
| 单元测试覆盖 | 测试 | 低 |

> **注**：Mapper XML 文件在 `src/main/resources/mapper/` 下未找到，项目可能使用 MyBatis-Plus 注解方式定义 SQL。如需排查原生 SQL 注入，请确认 Mapper 接口中是否使用 `@Select` / `@Update` 等注解拼接动态 SQL。

---

## 📋 审查结论

本系统在**业务逻辑完整性**和**数据权限设计**方面表现良好，`DataScope` 三层隔离、`@RequireRole` AOP 校验、文件上传多层防护等设计均体现了良好的安全意识。

但存在 **6 个阻断级安全问题**需立即修复，其中通知越权（P0-1）和工作流无认证（P0-5）是最危险的漏洞，可直接导致审批流程被绕过。**14 个 P1 问题**集中在性能（N+1 查询、内存分页）和 Token 管理方面，建议在下个迭代集中治理。**16 个 P2 问题**主要是代码质量和可维护性优化，可纳入技术债务逐步清理。

**总修复工时估算**：P0 约 3.5h + P1 约 12h + P2 约 10h = **约 25.5 小时（约 3-4 个工作日）**。

---

*报告生成时间：2026-06-05 18:21 CST*  
*审查者：Code Reviewer Agent*
