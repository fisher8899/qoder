# 月度业绩考核管理系统 - 深度代码审核报告

**审核日期**: 2026-05-16  
**审核人**: 资深代码审核专家（20年经验）  
**审核范围**: `assessment-backend`、`assessment-frontend`、根目录脚本与工具类  
**审核态度**: 犀利、直接、不留情面

---

## 一、总体评价

**直接说结论：这套代码能在演示环境跑起来，但离生产环境还有十万八千里。**

核心问题不是"代码不够优雅"，而是"安全边界形同虚设"和"业务逻辑漏洞百出"。更糟糕的是，团队花了大量精力写项目文档、修边界bug，却把最根本的架构缺陷留在了原地。

我看过太多"先上线再说"的项目，最后都是这种状态：页面功能都演示过了，领导也点头了，但真正的安全审计一做，立刻就暴露出"任何人登录后都能当管理员"这种低级问题。

---

## 二、致命级问题（P0 - 必须立即修复）

### P0-1：数据库管理接口是裸奔的核武器

**位置**: `DatabaseAdminController.java` + `DatabaseAdminServiceImpl.java`

**问题描述**:

你们真的在系统里放了一个"数据库查询"功能？允许管理员直接在界面上浏览、编辑、删除任意业务表数据？

```java
// DatabaseAdminServiceImpl.java:67
String sql = "DELETE FROM " + wrap(normalizedTable) + " WHERE `id` = ?";
int affected = jdbcTemplate.update(sql, id);
```

这是在干什么？直接拼接SQL删除？虽然做了表名白名单，但：

1. **没有操作日志审计** - 谁删了什么数据？什么时候删的？删完之后怎么追溯？
2. **没有数据备份机制** - 用户点了删除，数据就真没了
3. **没有业务约束校验** - 直接删除 `sys_user` 表里的用户？关联数据怎么处理？
4. **前端暴露了删除按钮** - `DatabaseBrowser.vue:79` 直接渲染删除按钮，没有任何二次确认之外的防护

更离谱的是，登录页直接把测试账号和密码写死在页面上：

```vue
// LoginPage.vue:66
const testAccounts = [
  { username: 'admin', roleName: '系统管理员' },
  ...
]
// 密码都是 123456，点击直接填充
```

**影响**: 任何能访问系统的人都能看到账号密码，配合这个数据库管理功能，就是一场灾难。

**建议**: 
- 立即下线数据库管理功能，这是运维工具，不是业务功能
- 删除登录页的测试账号提示
- 所有敏感操作必须有审计日志

---

### P0-2：敏感信息硬编码在代码库中

**位置**: 多处

**问题描述**:

你们把密码、密钥、数据库连接串全部写进了代码和配置文件：

```yaml
# application.yml
spring:
  datasource:
    username: root
    password: ${DB_PASSWORD:root}  # 默认密码是 root
app:
  jwt:
    secret: ${JWT_SECRET:assessment-secret-key-2026-secure}  # 默认密钥明文写死
```

```sql
-- fix_password.sql
UPDATE sys_user SET password='...' WHERE username='tuke01';
```

```text
-- db_pwd.txt（这个文件是怎么进代码库的？）
admin    $2a$10$zfr3JSLSDbA3VMd0iZo5RuD
tuke01   .zmdr9k7uOCQb376NoUnuTJ8iAt6Z5
```

**影响**: 任何能访问代码库的人都能看到数据库密码、JWT密钥、用户密码哈希前缀。这已经不是"安全隐患"，这是"自杀式配置"。

**建议**:
1. 立即删除所有包含敏感信息的txt文件
2. 所有密钥必须通过环境变量注入，禁止有默认值
3. 检查Git历史，确认敏感信息没有被提交到远程仓库

---

### P0-3：SecurityConfig 只防君子不防小人

**位置**: `SecurityConfig.java`

**问题描述**:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**", "/swagger-ui.html", "/swagger-ui/**", "/api-docs/**").permitAll()
    .anyRequest().authenticated()  // 只要登录就能访问所有接口
)
```

这意味着什么？**任何登录用户都能调用任何接口**。你们写了角色系统、权限表、数据范围，但在最关键的安全配置里，全都绕过去了。

虽然部分Controller加了 `@RequireRole("ADMIN")`，但：
- `BizExamGroupController` 的发布、启动、预发布接口没有角色限制
- `BizIndicatorDefinitionController` 的审批接口没有角色限制
- `BizReviewScoreController` 的复核提交接口没有角色限制

**影响**: 任何部门绩效管理员都能调用考核管理接口，提交复核，发布结果。

**建议**: 立即为所有管理接口添加角色限制，不要指望前端路由守卫。

---

### P0-4：数据范围验证形同虚设

**位置**: `DataScopeInterceptor.java` + `DataScopeContext.java`

**问题描述**:

虽然代码看起来做了数据范围验证：
```java
// DataScopeInterceptor.java
String hintRoleCode = normalizeText(request.getHeader("X-Role-Code"));
String hintScopeIdText = normalizeText(request.getHeader("X-Scope-Id"));
```

前端也确实发了这些Header：
```typescript
// request.ts
if (userStore.activeRoleCode) {
  config.headers['X-Role-Code'] = userStore.activeRoleCode
}
config.headers['X-Scope-Id'] = String(userStore.scopeId)
```

**但问题在于**：前端发的Header是从localStorage读的，localStorage里的数据是用户登录时存的。攻击者只需要打开浏览器开发者工具，改一下localStorage，就能：
1. 把 `activeRoleCode` 改成 `ADMIN`
2. 把 `scopeId` 改成任意单位ID
3. 刷新页面，直接获得该单位的数据访问权限

**更离谱的是** `DataScopeContext` 的默认值：
```java
// DataScopeContext.java:56
public static String getDataScope() {
    DataScopeInfo info = CONTEXT.get();
    return info != null ? info.getDataScope() : "ALL";  // 默认是全部！
}
```

如果ThreadLocal为空，默认返回"ALL"？这是在开玩笑吗？

**影响**: 攻击者可以伪造任意角色、任意数据范围，绕过所有隔离。

**建议**: 
1. 从请求头读取的权限信息只能作为"提示"，必须与数据库权限表比对验证
2. ThreadLocal为空时应该抛出异常，而不是返回"ALL"

---

## 三、严重级问题（P1 - 尽快修复）

### P1-1：指标审批角色可以伪造

**位置**: `BizIndicatorDefinitionController.java:76` + `BizIndicatorDefinitionServiceImpl.java`

**问题描述**:
```java
// Controller 直接接收 roleCode 参数
public Result<PageResult<IndicatorVO>> getApprovalList(
    @RequestParam(required = false) String roleCode  // 这是从前端传的！
) {
    return Result.success(indicatorDefinitionService.getApprovalList(queryDTO, roleCode));
}
```

审批时：
```java
// IndicatorApprovalDTO 里的 roleCode 也是前端传的
String roleCode = dto.getRoleCode();
String nextStatus = getNextApprovalStatus(indicator.getApprovalStatus(), roleCode);
```

**影响**: 任何用户都可以伪造 `roleCode=FIN_ADMIN`，直接跳过部门负责人和分管领导审批。

**建议**: 角色必须从SecurityContext获取，不能接受前端参数。

---

### P1-2：申诉处理没有事务保证

**位置**: `BizAppealServiceImpl.java:159`

**问题描述**:
```java
@Transactional(rollbackFor = Exception.class)
public void handleAppeal(AppealHandleDTO dto, String handledBy) {
    // ... 更新申诉状态
    
    // 回写评分到 BizReviewScore
    updateReviewScore(appeal, dto.getNewScore());
}

private void updateReviewScore(BizAppeal appeal, BigDecimal newScore) {
    // ... 查询并更新 BizReviewScore
    
    // 如果不存在，创建新记录
    if (reviewScore != null) {
        reviewScoreMapper.updateById(reviewScore);
    } else {
        reviewScoreMapper.insert(reviewScore);  // 这里可能失败
    }
}
```

申诉处理和评分回写是两个独立操作，如果中间发生异常：
1. 申诉状态已更新为"HANDLED"
2. 评分回写失败
3. 结果：申诉显示已处理，但分数没变

**建议**: 所有操作必须在同一个事务中，任何一步失败都要回滚。

---

### P1-3：复核提交逻辑不完整

**位置**: `BizReviewScoreServiceImpl.java:273`

**问题描述**:
```java
public void submitReview(Long examGroupId, String reviewer) {
    // 只遍历已存在的 biz_review_score 记录
    LambdaQueryWrapper<BizReviewScore> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(BizReviewScore::getExamGroupId, examGroupId);
    List<BizReviewScore> list = list(wrapper);
    
    // 如果财务管理员没有逐条保存，那些指标就不会有记录
    // 这里不会为它们自动创建记录
}
```

**影响**: 复核提交后，只有被手动编辑过的指标会有最终分数，其他指标丢失。

---

### P1-4：自评/他评可以冒充其他部门

**位置**: `BizSelfEvaluationServiceImpl.java` + `BizPeerEvaluationServiceImpl.java`

**问题描述**:

虽然添加了 `validateOrgAccess()` 方法，但验证逻辑有漏洞：
```java
private void validateOrgAccess(Long targetOrgId) {
    String dataScope = DataScopeContext.getDataScope();
    if ("ALL".equals(dataScope)) {
        return;  // 如果是ALL，直接放行
    }
    // ...
}
```

如果攻击者把Header改成"ALL"（见P0-4），这里就直接放行了。

---

### P1-5：状态机形同虚设

**位置**: `BizExamGroupServiceImpl.java`

**问题描述**:

虽然加了状态校验：
```java
public void prePublish(Long id) {
    // 状态机校验：仅"进行中"状态可预发布
    if (!ExamStatus.IN_PROGRESS.getCode().equals(group.getStatus())) {
        throw new BusinessException("仅进行中状态的考核组可预发布");
    }
}
```

但**没有校验前置条件是否完成**：
- 指标是否全部审批通过？
- 自评是否全部提交？
- 他评是否全部完成？
- 复核是否全部提交？
- 申诉是否全部处理？

**影响**: 可以从"待启动"直接跳到"已发布"，跳过所有中间步骤。

---

## 四、中等级问题（P2 - 计划修复）

### P2-1：源码中文乱码严重

**位置**: 多处Java文件

**问题描述**:
```java
// BizIndicatorDefinitionServiceImpl.java 里的注释全部是乱码
// 闂佽桨鑳舵晶晶妤€鐣垫笟鈧幊鐘诲礃閵婏妇鍑介柡澶嗘櫅濞诧箓骞?
```

这不是编码问题，是**从一开始就用错了编码**。这种代码根本没法维护。

---

### P2-2：评分上限设置错误

**位置**: `SelfEvalSaveDTO.java` + `PeerEvalSaveDTO.java`

**问题描述**:
```java
@DecimalMax("999.99")  // 绩效评分应该是0-100
private BigDecimal selfScore;
```

绩效考核评分怎么可能超过100分？设置成999.99是在干什么？

---

### P2-3：结果查询压掉多条他评

**位置**: `ExamResultServiceImpl.java`

**问题描述**:
```java
// 多个他评部门，Map只保留最后一条
peerMap.put(eval.getTargetOrgId() + "_" + eval.getIndicatorId(), eval);
```

如果同一指标有多个部门打分，结果页只显示最后一个。

---

### P2-4：数据库查询功能SQL注入风险

**位置**: `DatabaseAdminServiceImpl.java`

虽然做了表名白名单，但字段值的处理很随意：
```java
private Object normalizeValue(Object value) {
    return value;  // 直接返回，没有做任何校验
}
```

---

## 五、代码质量问题

### CQ-1：硬编码字符串满天飞

```java
// 状态用字符串比较
if ("SUBMITTED".equals(entity.getStatus()))  // 为什么不用枚举？
if ("ALL".equals(dataScope))  // 魔法字符串

// 角色编码散落各处
"ADMIN", "FIN_ADMIN", "DEPT_ADMIN", "DEPT_LEADER", "SUPERVISOR"  // 应该用常量类
```

### CQ-2：异常处理随意

```java
// JwtAuthenticationFilter.java
} catch (Exception e) {
    log.warn("Failed to query user permissions for {}: {}", userId, e.getMessage());
}
// 吞掉异常，返回空列表？
```

### CQ-3：日志输出敏感信息

```yaml
# application.yml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 生产环境输出SQL？
```

### CQ-4：前端权限判断只在前端做

```typescript
// router/index.ts
router.beforeEach(async (to, from, next) => {
  if (userStore.hasPathAccess(to.path)) {
    next()
    return
  }
  // ...
})
```

前端路由守卫只能防君子，不能防攻击者直接调API。

---

## 六、架构设计问题

### AR-1：没有真正的RBAC

你们有 `sys_role`、`sys_menu`、`sys_role_menu`、`sys_user_permission` 四张表，但：
- 角色和权限的关系是硬编码在业务代码里的
- 菜单权限和数据权限混在一起
- 没有统一的权限服务，每个Controller自己判断

### AR-2：数据范围逻辑分散

数据范围判断散落在：
- `DataScopeInterceptor` - 拦截器
- `DataScopeContext` - ThreadLocal
- `DataScopeFilter` - 工具类
- 各Service实现类 - 业务逻辑

没有统一的权限服务，改一个地方漏三个地方。

### AR-3：缺少审计日志

除了 `sys_operation_log` 表存在，但：
- 没有统一切面记录操作日志
- 关键操作（审批、发布、删除）没有审计记录
- 数据库管理功能完全没有日志

---

## 七、修复优先级

| 优先级 | 问题 | 预计工时 |
|-------|------|---------|
| P0 | 下线数据库管理功能 | 0.5天 |
| P0 | 删除敏感信息，配置环境变量 | 0.5天 |
| P0 | SecurityConfig添加角色限制 | 1天 |
| P0 | 修复数据范围验证逻辑 | 2天 |
| P1 | 审批角色从服务端获取 | 1天 |
| P1 | 申诉处理事务保证 | 0.5天 |
| P1 | 复核提交补齐记录 | 1天 |
| P1 | 状态机前置校验 | 1天 |
| P2 | 修复乱码 | 2天 |
| P2 | 评分上限修正 | 0.5天 |
| P2 | 他评结果聚合 | 1天 |

---

## 八、结论

这套代码目前的状态是：**功能演示通过，安全审计不通过，生产部署风险极高**。

如果这是一次代码审核，我的结论是：**不通过，需要重新整改后复审**。

如果这是一次上线前的安全评审，我的建议是：**拒绝上线，先修P0问题**。

---

*审核人：资深代码审核专家*  
*审核日期：2026-05-16*
