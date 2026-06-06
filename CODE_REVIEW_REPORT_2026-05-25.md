# 绩效考核系统 代码审查报告

> 审查日期：2026-05-25 | 审查范围：后端（Java/Spring Boot）+ 前端（Vue 3/TypeScript）

---

## 总览

| 维度 | 评分 | 主要风险 |
|------|------|----------|
| **安全性** | 3/10 | JWT 硬编码密钥、CORS 缺陷、无登录限速、Token 存 localStorage |
| **后端架构** | 5/10 | Controller 堆积业务逻辑、N+1 查询风暴、事务缺失 |
| **前端架构** | 4/10 | 2200 行巨型组件、200+ 处 any、客户端全量加载 |
| **整体可维护性** | 5/10 | Map 返回值泛滥、重复代码多、类型保护缺失 |

**综合评估**：项目功能基本完整，安全与性能存在多处致命缺陷，需在上线前集中修复。

---

## 一、安全问题（最高优先级）

### CRITICAL

#### S1. JWT Secret 硬编码回退值
- **文件**：`assessment-backend/src/main/resources/application-dev.yml:17`
- **问题**：`app.jwt.secret: ${JWT_SECRET:assessment-secret-key-2026-secure}`，环境变量未设置时使用公开弱密钥。攻击者可直接伪造任意用户 JWT。
- **修复**：
```yaml
app:
  jwt:
    secret: ${JWT_SECRET}  # 删除回退值，缺失时启动失败
```
```java
@PostConstruct
public void validateSecret() {
    if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
        throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
    }
}
```

#### S2. CORS 配置存在双重缺陷
- **文件**：`CorsConfig.java:30` + `SecurityConfig.java:28-41`
- **问题1**：`addAllowedOriginPattern("http://localhost:*")` 在所有环境生效，生产环境无效保护
- **问题2**：SecurityFilterChain 未调用 `.cors()`，浏览器 preflight 请求可能绕过 JWT 认证
- **修复**：
```java
// CorsConfig.java - 限定 dev profile
@Value("${spring.profiles.active:prod}")
private String activeProfile;

if ("dev".equals(activeProfile)) {
    config.addAllowedOriginPattern("http://localhost:*");
}

// SecurityConfig.java - 集成 CORS
http.cors(cors -> cors.configurationSource(request -> {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.setAllowCredentials(true);
    return config;
}))
```

#### S3. 数据库浏览器生产环境不应存在
- **文件**：`assessment-frontend/src/views/admin/DatabaseBrowser.vue`
- **问题**：前端直接暴露数据库表名，`tableName` 和 `id` 透传后端，可执行任意表的增删改。虽然后端有白名单，但该功能不应出现在生产环境。
- **修复**：生产构建时通过环境变量排除此路由，或加管理员二次认证。

#### S4. Token 存储在 localStorage
- **文件**：`assessment-frontend/src/api/request.ts:26`, `src/stores/user.ts:47`
- **问题**：JWT 存 localStorage，任何 XSS 可窃取 token 劫持会话。
- **修复**：迁移至 `httpOnly` + `Secure` cookie（需后端配合 Set-Cookie），或至少用 `sessionStorage`。

### HIGH

#### S5. 登录接口无速率限制
- **文件**：`assessment-backend/.../AuthController.java:136`
- **问题**：`/api/auth/login` 无任何频率限制，可无限暴力破解。
- **修复**：
```java
// 基于 ConcurrentHashMap + 滑动窗口
private final ConcurrentHashMap<String, List<Long>> loginAttempts = new ConcurrentHashMap<>();

String clientIp = request.getRemoteAddr();
if (isRateLimited(clientIp, 5, 60_000)) {
    throw new BusinessException(429, "登录尝试过于频繁，请稍后再试");
}
```

#### S6. JWT 无法吊销，logout 形同虚设
- **文件**：`AuthController.java:321-324`
- **问题**：logout 直接返回 success，JWT 24 小时内持续有效。用户改密/被禁用后旧 token 仍可用。
- **修复**：维护 token 黑名单（Redis 或内存），缩短有效期至 2-4 小时 + refresh token。

#### S7. DatabaseAdmin 物理删除绕过软删除
- **文件**：`DatabaseAdminServiceImpl.java:176`
- **问题**：`DELETE FROM` 直接物理删除，系统全局配置了 `logic-delete-field: deleted`，违反设计约束。
- **修复**：改为 `UPDATE SET deleted = 1 WHERE id = ?`。

#### S8. 用户密码哈希泄露风险
- **文件**：`AuthController.java:148-152`
- **问题**：`selectOne` 返回完整 SysUser 实体，password 字段若无 `@JsonIgnore` 可能被序列化。
- **修复**：在 SysUser 实体的 password 字段添加 `@JsonIgnore`。

### MEDIUM

#### S9. Session Fixation 风险
- **文件**：`AuthController.java:137`
- **问题**：login 写 session 前未调用 `session.invalidate()`。
- **修复**：验证密码成功后先 invalidate 旧 session，再创建新 session。

#### S10. 错误信息泄露角色编码
- **文件**：`RoleCheckAspect.java:35`
- **修复**：改为通用提示 `"无权限访问该资源"`。

#### S11. BCryptPasswordEncoder 未声明为 Bean
- **文件**：`AuthController.java:52`
- **修复**：在 SecurityConfig 中声明 `@Bean PasswordEncoder`。

#### S12. DataScopeContext 默认返回 "ALL" — 无权限时隐式授权
- **文件**：`DataScopeContext.java:37`
- **修复**：默认返回 null，让调用方显式处理。

#### S13. JWT 过期时间 24 小时过长
- **文件**：`application.yml:30`
- **修复**：缩短至 2-4 小时，配合 refresh token 机制。

#### S14. 硬编码默认密码提示
- **文件**：`assessment-frontend/.../UserManagement.vue:427`
- **问题**：`ElMessage.success('密码已重置为123456')` 明文暴露默认密码。
- **修复**：改为 `ElMessage.success('密码已重置')`。

#### S15. 缺少安全响应头
- **文件**：`SecurityConfig.java`
- **修复**：添加 `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`。

---

## 二、后端性能与架构问题

### CRITICAL

#### P1. MonthlyExamController — O(N^2) 查询风暴
- **文件**：`MonthlyExamController.java:56-167`
- **问题**：`status()` 和 `deptProgress()` 在嵌套循环中逐条 `selectCount`。N=20 时产生 400+ 条 SQL，接口直接超时。
- **修复**：移入 Service 层，改用 `GROUP BY` 批量聚合查询，内存中组装结果。

#### P2. AuthController 堆积全部业务逻辑（约 200 行）
- **文件**：`AuthController.java` 全文
- **问题**：直接注入 6 个 Mapper，login 100 行、getAvailableRoles 三层 fallback、getRoleName 循环内查 DB（N+1）。严重违反分层架构。
- **修复**：新建 AuthService，迁移全部业务逻辑。getRoleName 改为批量查角色表后内存映射。

#### P3. SysRoleServiceImpl.deleteRole() 缺少事务
- **文件**：`SysRoleServiceImpl.java:85-92`
- **问题**：先删角色再删菜单关联，第二步失败则产生孤儿数据，无 `@Transactional`。
- **修复**：添加 `@Transactional(rollbackFor = Exception.class)`。

#### P4. wrap() 方法未转义反引号 — SQL 注入风险
- **文件**：`DatabaseAdminServiceImpl.java:415-417`
- **问题**：列名来自用户输入 JSON key，仅校验是否在 columnMap 中。若含反引号可构造恶意 SQL。
- **修复**：`wrap()` 中替换反引号 `` ` `` 为 ``` `` ```。

### HIGH

#### P5. SysRoleController.getChildren() — N+1 查询
- **文件**：`SysRoleController.java:116-134`
- **修复**：收集所有 childRoleId，一次 `listByIds` 查询后内存组装。

#### P6. BizReviewScoreServiceImpl.generateMonthlyScores() — 循环内流式查找 + 逐条插入
- **文件**：`BizReviewScoreServiceImpl.java:341-427`
- **修复**：将 indicators 预转为 Map，批量插入使用 `saveBatch`。

#### P7. BizReviewScoreServiceImpl.getReviewList() — 循环内逐条查指标定义
- **文件**：`BizReviewScoreServiceImpl.java:98-160`
- **修复**：一次查出所有 orgId 的指标定义，按 orgId 分组后内存处理。

#### P8. AuthController.getUserInfo() — 手动解析 JWT
- **文件**：`AuthController.java:275-319`
- **问题**：从 Header 手动截取 token 并解析，与 SecurityFilter 重复。
- **修复**：通过 `SecurityUtil.getCurrentUserId()` 获取。

#### P9. BizPeerEvaluationServiceImpl.getStatistics() — O(N^2) 嵌套循环
- **文件**：`BizPeerEvaluationServiceImpl.java:330-366`
- **问题**：N=10 时产生 180 条 SQL。
- **修复**：一次查出所有记录，内存中按 orgId 分组组装。

### MEDIUM

#### P10. 整数除法精度丢失（多处）
- **文件**：`MonthlyExamController.java:110-115`, `BizReviewScoreServiceImpl.java:188` 等
- **问题**：`completed * 100 / total` 整数除法截断小数，7/9 显示 77% 而非 77.78%。
- **修复**：`(int)(completed * 10000 / total) / 100.0` 或 BigDecimal。

#### P11. getProgress() 死代码
- **文件**：`BizExamGroupServiceImpl.java:430`
- **问题**：`selectCount(null)` 查总数无过滤，赋值给局部变量但未使用。进度数据全部是占位值。

#### P12. getByUserId() 期望多条但只返回一条
- **文件**：`SysUserPermissionServiceImpl.java:105-109`
- **问题**：`getOne()` 多条记录时抛异常。用户完全可能有多角色权限。
- **修复**：改为返回 `List<SysUserPermission>`。

#### P13. submitSelfEval/submitPeerEval 循环内逐条 update
- **文件**：`BizSelfEvaluationServiceImpl.java:260-270`, `BizPeerEvaluationServiceImpl.java:317-326`
- **修复**：使用 `updateBatchById` 或自定义批量 SQL。

#### P14. getAppealList + countAppealList 重复查询构建
- **文件**：`BizAppealServiceImpl.java:47-78`
- **修复**：提取公共 `buildAppealQueryWrapper` 方法。

#### P15. 大量 Map<String, Object> 返回值
- **位置**：几乎所有 Controller 和 Service
- **问题**：丧失编译期类型检查，前端对接易出错。
- **修复**：为每个接口定义 VO/DTO 类。

#### P16. DataScopeInterceptor 使用原始 JDBC
- **文件**：`DataScopeInterceptor.java:146-180`
- **问题**：绕过连接池管理和 MyBatis 逻辑删除机制。
- **修复**：改用 SysOrganizationMapper 查询。

---

## 三、前端架构与质量问题

### CRITICAL

#### F1. IndicatorSet.vue 2217 行 — 职责严重超标
- **文件**：`assessment-frontend/src/views/dept/IndicatorSet.vue`（约 70KB）
- **问题**：单文件包含指标列表、编辑弹框、大类管理、小类管理、考核内容编辑、权重计算、审批流程、批量导入导出 + 300 行 CSS + 70 处 any。
- **修复**：拆分为 5-6 个子组件 + 独立 composable：
  - `IndicatorCategoryCards.vue`
  - `SubCategoryList.vue`
  - `IndicatorTable.vue`
  - `IndicatorEditDialog.vue`
  - `WeightSummary.vue`
  - `useIndicatorLogic.ts`

### HIGH

#### F2. 全局 any 泛滥（约 200+ 处）
- **位置**：`api/admin.ts` 全文、`stores/user.ts:34`、多个 view 组件
- **问题**：API 层参数和返回值几乎全是 any，组件内 tableData、options 等全部 `any[]`，完全丧失 TypeScript 保护。
- **修复**：优先完善 `types.ts` 中的 interface 定义并在 API 层使用；组件内 `ref<UserInfo[]>([])`。

#### F3. IndicatorApproval.vue 与 IndicatorSet.vue 大量重复（约 800 行）
- **文件**：`src/views/exam/IndicatorApproval.vue` vs `src/views/dept/IndicatorSet.vue`
- **修复**：提取共享组件 `IndicatorViewer.vue`，通过 `mode` prop 区分编辑/审批模式。

#### F4. 客户端分页 + size:1000 全量加载
- **文件**：`IndicatorSet.vue:548-551`
- **修复**：改为服务端分页，将 queryParams 传给 API。

### MEDIUM

#### F5. router beforeEach 每次导航可能发 HTTP 请求
- **文件**：`router/index.ts:95-106`
- **问题**：allowedPaths 缓存未命中时每次路由跳转都调 `menuApi.current()`。
- **修复**：增加请求去重标志位或持久化缓存。

#### F6. API 错误处理不完整
- **位置**：多个 view 组件的 loadData 函数
- **问题**：只有 `.finally()` 无 `.catch()`，异常时 Promise rejection 被静默吞掉。
- **修复**：增加 `.catch()` 记录错误。

#### F7. 直接修改 reactive 行数据
- **文件**：`UserManagement.vue:49`, `IndicatorSet.vue:1024-1036`
- **问题**：`el-switch` 的 `v-model="row.isEnabled"` 直接 mutation，可能导致意外响应式副作用。
- **修复**：使用事件配合本地状态，或失败回滚时用 `nextTick` 确保 DOM 更新。

#### F8. DataTable.vue 分页状态与 props 不同步
- **文件**：`DataTable.vue:85-87`
- **问题**：`ref(props.currentPage)` 初始化后 watch 仅监听 props 变化，内部状态可能失同步。
- **修复**：使用 `toRefs(props)` 或 computed getter。

### LOW

#### F9. deepClone 函数冗余
- **文件**：`utils/index.ts`
- **修复**：使用 `structuredClone` 或 `JSON.parse(JSON.stringify())`。

#### F10. 硬编码业务数据
- **文件**：`IndicatorSet.vue:729-739`（emoji 映射）、`MainLayout.vue:143`（公司名缩写）
- **修复**：移到后端字典或配置文件。

#### F11. 缺少 onUnmounted 清理
- **位置**：所有组件
- **问题**：虽 Vue 自动处理响应式依赖，但定时器/事件监听器需手动清理。

---

## 四、代码亮点

值得肯定的设计：

1. **BizAppealController 文件上传**：做了 MIME 类型检测（magic bytes）和路径遍历防护，安全意识好
2. **SysUserPermissionController 数据范围校验**：`assertUserInScope` + `isUserVisible` 防越权
3. **DatabaseAdmin 白名单**：ALLOWED_TABLES 限制可访问表，降低注入风险
4. **申诉流程状态机**：DRAFT → PENDING_REEVAL → HANDLED，状态转换清晰
5. **DataScopeInterceptor 从 Session 读取角色**：正确做法，防止客户端伪造权限
6. **前端路由守卫**：基于菜单的动态权限控制设计思路正确
7. **useUserStore 角色匹配**：ensureActiveRole 考虑了多种场景
8. **全局拦截器**：统一处理 401/403，避免组件重复处理
9. **GlobalExceptionHandler**：requestId 追踪，生产排障友好

---

## 五、修复优先级建议

### P0 — 立即修复（影响安全/可用性）

| # | 问题 | 影响 |
|---|------|------|
| S1 | JWT Secret 硬编码 | 攻击者可伪造任意用户身份 |
| S2 | CORS + Security 缺陷 | 生产环境 CORS 形同虚设 |
| P1 | MonthlyExam 查询风暴 | 考核组 >20 人时接口不可用 |
| P3 | deleteRole 无事务 | 数据一致性破坏 |
| S5 | 登录无限速 | 可暴力破解密码 |

### P1 — 短期修复（1-2 周内）

| # | 问题 | 影响 |
|---|------|------|
| S3 | DatabaseBrowser 生产暴露 | 数据完整性风险 |
| S4 | Token 存 localStorage | XSS 可劫持会话 |
| S6 | JWT 无法吊销 | 改密/禁用后旧 token 仍有效 |
| S7 | DatabaseAdmin 物理删除 | 绕过软删除设计 |
| P2 | AuthController 架构违规 | 不可测试不可维护 |
| P4 | wrap() SQL 注入 | 列名注入攻击 |
| F1 | IndicatorSet.vue 巨型组件 | 无法维护 |
| P5-P9 | 多处 N+1 查询 | 性能瓶颈 |

### P2 — 中期优化（迭代中逐步修复）

| # | 问题 | 影响 |
|---|------|------|
| F2 | 全局 any 泛滥 | 类型安全缺失 |
| F3 | 800 行重复代码 | 维护成本高 |
| P10-P16 | 精度丢失、死代码、Map 返回值 | 代码质量 |
| S9-S15 | Session Fixation、安全头等 | 防御纵深 |

---

## 六、总结

项目功能基本完整，业务流程（指标管理 → 自评 → 他评 → 复核 → 申诉）覆盖较全。代码亮点包括文件上传安全、数据权限控制、申诉状态机等。

但**安全层面存在多个致命缺陷**（JWT 硬编码 + CORS 缺陷 + 无登录限速 = 可远程伪造管理员身份），**后端存在严重性能问题**（O(N^2) 查询风暴），**前端存在巨型组件和类型保护缺失**。

建议立即集中修复 P0 清单中的 5 个问题，再按 P1 → P2 顺序逐步治理。预计 P0 修复工作量约 2-3 人天。
