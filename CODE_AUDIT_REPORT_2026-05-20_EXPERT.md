# 代码审核报告 - 专家级深度审查

**项目名称**: 中煤鄂能化综合考评管理系统（月度业绩考核管理）  
**审查日期**: 2026-05-20  
**审查级别**: 全面深入审查  
**审查专家**: 20年资深代码审查专家  

---

## 一、项目概览

### 1.1 技术栈
| 层级 | 技术选型 | 版本 |
|------|----------|------|
| 后端框架 | Spring Boot | 3.2.5 |
| ORM | MyBatis Plus | 3.5.5 |
| 安全框架 | Spring Security + JWT | - |
| 数据库 | MySQL | - |
| 前端框架 | Vue 3 | 3.4.31 |
| UI组件库 | Element Plus | 2.13.7 |
| 构建工具 | Vite | 5.3.4 |

### 1.2 代码规模
- **后端 Java 文件**: ~150+ 个
- **前端 Vue/TS 文件**: ~60+ 个
- **数据库表**: 25+ 张

---

## 二、严重安全问题 (Critical)

### 2.1 🔴 数据库凭据明文泄露
**文件**: `db-config.txt`
```
host=localhost
port=3306
database=assessment_db
username=root
password=root
```

**风险**: 数据库 root 密码以明文形式存储在代码仓库中，任何有代码访问权限的人都能获取数据库完全控制权。

**专家建议**:
1. **立即处理**: 将此文件加入 `.gitignore` 并从 Git 历史中清除
2. **密码轮换**: 立即更改数据库密码
3. **环境变量化**: 使用环境变量或配置中心管理敏感信息
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

---

### 2.2 🔴 测试账号密码硬编码
**文件**: `LoginPage.vue` (第43-55行)
```javascript
const testAccounts = [
  { username: 'admin', roleName: '系统管理员' },
  { username: 'wangfang', roleName: '考核管理员' },
  // ...
]
// 密码均为 123456
```

**风险**: 
- 生产环境暴露测试账号
- 弱密码策略（123456）
- 可能被恶意利用进行未授权访问

**专家建议**:
1. **生产环境移除**: 使用环境变量控制是否显示测试账号
2. **强制密码策略**: 实施密码复杂度要求
3. **账号锁定机制**: 添加登录失败次数限制
```vue
<!-- 生产环境不显示测试账号 -->
<div v-if="isDev" class="test-accounts">
  <!-- ... -->
</div>
```

---

### 2.3 🔴 SQL 注入风险 - 数据库管理接口
**文件**: `DatabaseAdminServiceImpl.java`

虽然使用了白名单机制限制可访问的表，但 `deleteRow` 方法直接拼接 SQL：
```java
String sql = "DELETE FROM " + wrap(normalizedTable) + " WHERE `id` = ?";
```

**风险**: 
- 虽然有白名单保护，但直接暴露数据库操作接口极其危险
- 误操作可能导致数据丢失
- 缺少审计日志

**专家建议**:
1. **限制访问**: 仅允许开发/测试环境访问
2. **添加审计**: 记录所有数据库操作
3. **软删除**: 改用软删除而非物理删除
4. **二次确认**: 关键操作需要二次确认机制
```java
@DeleteMapping("/row")
@RequireRole("ADMIN")
@AuditLog(operation = "DATABASE_DELETE")
public Result<Void> deleteRow(@RequestParam String tableName, @RequestParam Long id) {
    // 添加二次确认token验证
    // 记录操作日志
    databaseAdminService.deleteRow(tableName, id);
    return Result.success();
}
```

---

### 2.4 🔴 CORS 配置过于宽松
**文件**: `CorsConfig.java` (第30行)
```java
config.addAllowedOriginPattern("http://localhost:*");
```

**风险**: 任何本地端口的恶意脚本都可以发起跨域请求。

**专家建议**:
```java
// 生产环境严格限制来源
@Value("${app.cors.allowed-origins}")
private String allowedOrigins;

@Bean
public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    // 不使用通配符，明确列出允许的来源
    Arrays.stream(allowedOrigins.split(","))
          .forEach(origin -> config.addAllowedOrigin(origin.trim()));
    // 移除: config.addAllowedOriginPattern("http://localhost:*");
}
```

---

## 三、高危问题 (High)

### 3.1 🟠 架构分层违规 - Controller 直接操作 Mapper
**文件**: `AuthController.java`

Controller 层直接注入了 6 个 Mapper：
```java
private final SysUserMapper userMapper;
private final SysEmployeeMapper employeeMapper;
private final SysOrganizationMapper organizationMapper;
private final SysUserRoleMapper sysUserRoleMapper;
private final SysUserPermissionMapper sysUserPermissionMapper;
private final SysRoleMapper sysRoleMapper;
```

**问题**:
- 违反单一职责原则
- 业务逻辑散落在 Controller 中
- 难以进行单元测试

**专家建议**: 重构为标准三层架构
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }
}

@Service
public class AuthServiceImpl implements AuthService {
    // 所有业务逻辑集中在此
}
```

---

### 3.2 🟠 JWT Token 未实现黑名单机制
**文件**: `JwtTokenProvider.java`

当前实现的问题：
- Token 一旦签发，在过期前始终有效
- 用户登出后 Token 仍然可用
- 无法实现强制下线功能

**专家建议**:
```java
@Service
public class TokenBlacklistService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public void blacklistToken(String token, long expiration) {
        redisTemplate.opsForValue().set(
            "token:blacklist:" + token, 
            "1", 
            expiration, 
            TimeUnit.MILLISECONDS
        );
    }
    
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey("token:blacklist:" + token)
        );
    }
}
```

---

### 3.3 🟠 前端权限控制可被绕过
**文件**: `router/index.ts`

路由守卫仅检查 `allowedPaths`，但该数据存储在 localStorage 中：
```typescript
const allowedPaths = ref<string[]>(readJson<string[]>(STORAGE_KEYS.allowedPaths, []))
```

**风险**: 用户可以手动修改 localStorage 绕过前端权限控制。

**专家建议**:
1. **前端权限仅做展示控制**，真正的权限校验必须在后端
2. **敏感操作二次验证**: 关键接口调用时后端再次验证权限
3. **Token 携带权限信息**: 避免频繁查询权限表

---

### 3.4 🟠 N+1 查询性能问题
**文件**: `ExamResultServiceImpl.java` (第171-229行)

`querySummary` 方法存在严重的 N+1 查询问题：
```java
for (BizExamGroupMember member : members) {
    // 每个成员执行 2 次查询
    List<BizReviewScore> reviews = reviewScoreMapper.selectList(reviewWrapper);
    List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper);
}
```

**性能影响**: 如果有 N 个成员，将执行 2N+1 次数据库查询。

**专家建议**: 使用批量查询优化
```java
@Override
public List<ResultSummaryVO> querySummary(Long examGroupId) {
    // 1. 批量查询所有成员
    List<BizExamGroupMember> members = memberMapper.selectList(...);
    List<Long> orgIds = members.stream()
        .map(BizExamGroupMember::getOrgId)
        .collect(Collectors.toList());
    
    // 2. 批量查询所有复核分数（一次查询）
    Map<Long, List<BizReviewScore>> reviewMap = reviewScoreMapper.selectList(
        new LambdaQueryWrapper<BizReviewScore>()
            .eq(BizReviewScore::getExamGroupId, examGroupId)
            .in(BizReviewScore::getOrgId, orgIds)
    ).stream().collect(Collectors.groupingBy(BizReviewScore::getOrgId));
    
    // 3. 批量查询所有指标定义（一次查询）
    Map<Long, List<BizIndicatorDefinition>> indicatorMap = indicatorDefinitionMapper.selectList(...)
        .stream().collect(Collectors.groupingBy(BizIndicatorDefinition::getOrgId));
    
    // 4. 内存中组装结果
    return members.stream().map(member -> {
        ResultSummaryVO vo = new ResultSummaryVO();
        // 使用预加载的数据组装
        return vo;
    }).collect(Collectors.toList());
}
```

---

### 3.5 🟠 手动分页效率低下
**文件**: `ExamResultServiceImpl.java` (第144-157行)

```java
// 手动分页 - 先查全部再截取
List<ResultDetailVO> all = new ArrayList<>();
// ... 填充所有数据
int from = (int) ((current - 1) * size);
int to = (int) Math.min(from + size, total);
List<ResultDetailVO> records = from < total ? all.subList(from, to) : new ArrayList<>();
```

**问题**: 每次分页查询都加载全部数据到内存，数据量大时会导致内存溢出。

**专家建议**: 使用数据库层面的分页
```sql
SELECT * FROM biz_indicator_definition 
WHERE exam_group_id = ? AND org_id = ?
ORDER BY sort_code
LIMIT ? OFFSET ?
```

---

## 四、中危问题 (Medium)

### 4.1 🟡 缺少输入验证
**文件**: 多个 DTO 类

部分 DTO 缺少参数校验注解：
```java
public class ResultQueryDTO {
    private Long examGroupId;  // 缺少 @NotNull
    private Long orgId;
    private Long current;      // 缺少 @Min(1)
    private Long size;         // 缺少 @Max(100)
}
```

**专家建议**:
```java
public class ResultQueryDTO {
    @NotNull(message = "考核组ID不能为空")
    private Long examGroupId;
    
    @Min(value = 1, message = "页码最小为1")
    private Long current = 1L;
    
    @Min(value = 1) @Max(value = 100, message = "每页最多100条")
    private Long size = 10L;
}
```

---

### 4.2 🟡 异常处理不够精细
**文件**: `GlobalExceptionHandler.java`

所有业务异常都返回 500 状态码：
```java
public BusinessException(String message) {
    super(message);
    this.code = 500;  // 应该区分不同类型的业务异常
}
```

**专家建议**: 定义业务异常枚举
```java
public enum ErrorCode {
    USER_NOT_FOUND(404, "用户不存在"),
    INVALID_PASSWORD(401, "密码错误"),
    PERMISSION_DENIED(403, "权限不足"),
    DATA_NOT_FOUND(404, "数据不存在"),
    DUPLICATE_DATA(409, "数据重复");
    
    private final int code;
    private final String message;
}
```

---

### 4.3 🟡 前端状态管理混乱
**文件**: `stores/user.ts`

localStorage 中存储了过多的权限信息：
```typescript
const STORAGE_KEYS = {
  userInfo: 'userInfo',
  token: 'token',
  dataScope: 'dataScope',
  scopeId: 'scopeId',
  scopeName: 'scopeName',
  allowedPaths: 'allowedPaths',
  activeRoleCode: 'activeRoleCode',
  activeScopeId: 'activeScopeId',
  activeRoleName: 'activeRoleName'
}
```

**问题**:
- 敏感权限信息暴露在客户端
- 状态同步困难
- 容易出现不一致

**专家建议**:
```typescript
// 仅存储必要的认证信息
const STORAGE_KEYS = {
  token: 'token',
  refreshToken: 'refreshToken'
}

// 权限信息通过 API 实时获取
const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])
  
  // 权限信息不持久化，每次刷新重新获取
  async function fetchPermissions() {
    const res = await api.getPermissions()
    permissions.value = res.data
  }
})
```

---

### 4.4 🟡 缺少日志审计
**问题**: 关键业务操作缺少审计日志

**专家建议**: 实现 AOP 审计日志
```java
@Aspect
@Component
public class AuditLogAspect {
    
    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String operator = SecurityUtil.getCurrentUserName();
        String operation = auditLog.value();
        Object[] args = joinPoint.getArgs();
        
        log.info("审计日志 - 操作人: {}, 操作: {}, 参数: {}", operator, operation, args);
        
        Object result = joinPoint.proceed();
        
        log.info("审计日志 - 操作完成, 结果: {}", result);
        return result;
    }
}
```

---

### 4.5 🟡 缓存策略缺失
**问题**: 频繁查询的数据没有缓存

**专家建议**: 对热点数据添加缓存
```java
@Service
public class SysOrganizationServiceImpl {
    
    @Cacheable(value = "orgCache", key = "#orgId")
    public SysOrganization getById(Long orgId) {
        return organizationMapper.selectById(orgId);
    }
    
    @CacheEvict(value = "orgCache", key = "#org.id")
    public void update(SysOrganization org) {
        organizationMapper.updateById(org);
    }
}
```

---

## 五、低危问题 (Low)

### 5.1 🟢 代码规范问题

#### 5.1.1 命名不规范
```java
// 当前
private static final String INVALID_CREDENTIALS = "用户名或密码错误";

// 建议：常量应使用全大写，消息文本应使用枚举或资源文件
private static final String MSG_INVALID_CREDENTIALS = "用户名或密码错误";
```

#### 5.1.2 魔法数字
```java
// 当前
dto.setCurrent(1L);
dto.setSize(9999L);

// 建议
private static final long DEFAULT_PAGE_SIZE = 10L;
private static final long MAX_EXPORT_SIZE = 10000L;
```

#### 5.1.3 重复代码
多个 Service 实现中存在相似的分页查询逻辑，建议抽取基类：
```java
public abstract class BaseService<T> {
    
    protected PageResult<T> paginate(List<T> all, long current, long size) {
        // 通用分页逻辑
    }
}
```

---

### 5.2 🟢 单元测试缺失
**问题**: 项目缺少单元测试和集成测试

**专家建议**: 至少覆盖以下场景：
```java
@SpringBootTest
class AuthServiceTest {
    
    @Test
    void login_withValidCredentials_shouldReturnToken() {
        // 测试正常登录
    }
    
    @Test
    void login_withInvalidPassword_shouldThrowException() {
        // 测试密码错误
    }
    
    @Test
    void login_withDisabledAccount_shouldThrowException() {
        // 测试禁用账号
    }
}
```

---

### 5.3 🟢 API 文档不完整
**问题**: 虽然引入了 springdoc，但 Controller 缺少 Swagger 注解

**专家建议**:
```java
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户登录、登出、角色切换等接口")
public class AuthController {
    
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录获取Token")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        // ...
    }
}
```

---

## 六、架构改进建议

### 6.1 引入微服务架构（长期规划）
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   API Gateway   │    │   Auth Service  │    │  User Service   │
│    (Spring      │───▶│    (JWT/OAuth)  │    │   (用户管理)     │
│    Cloud)       │    └─────────────────┘    └─────────────────┘
└────────┬────────┘
         │
         ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Exam Service   │    │ Report Service  │    │ Notification    │
│   (考核管理)     │    │   (报表导出)     │    │    Service      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 6.2 引入 Redis 缓存层
```yaml
# application.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
```

### 6.3 实现消息队列（异步处理）
```java
// 考核结果计算异步化
@Service
public class ExamResultCalculator {
    
    @RabbitListener(queues = "exam.result.calculate")
    public void calculateResult(CalculateMessage message) {
        // 异步计算考核结果
    }
}
```

---

## 七、安全加固清单

| 序号 | 加固项 | 优先级 | 状态 |
|------|--------|--------|------|
| 1 | 移除明文密码配置 | P0 | 待处理 |
| 2 | 移除测试账号显示 | P0 | 待处理 |
| 3 | 实施密码复杂度策略 | P0 | 待处理 |
| 4 | 添加登录失败锁定 | P1 | 待处理 |
| 5 | 实现 Token 黑名单 | P1 | 待处理 |
| 6 | 添加操作审计日志 | P1 | 待处理 |
| 7 | 限制数据库管理接口 | P1 | 待处理 |
| 8 | 收紧 CORS 配置 | P2 | 待处理 |
| 9 | 添加 Rate Limiting | P2 | 待处理 |
| 10 | 实施数据加密存储 | P2 | 待处理 |

---

## 八、性能优化清单

| 序号 | 优化项 | 预期收益 | 复杂度 |
|------|--------|----------|--------|
| 1 | 解决 N+1 查询 | 减少 80% 数据库查询 | 中 |
| 2 | 实现数据库分页 | 内存使用减少 90% | 低 |
| 3 | 添加 Redis 缓存 | 响应时间减少 50% | 中 |
| 4 | 前端组件懒加载 | 首屏加载减少 40% | 低 |
| 5 | API 响应压缩 | 带宽减少 60% | 低 |
| 6 | 数据库索引优化 | 查询速度提升 3-5 倍 | 中 |

---

## 九、代码质量评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 安全性 | 45/100 | 存在多个严重安全漏洞 |
| 架构设计 | 65/100 | 基本合理，但有分层违规 |
| 代码规范 | 70/100 | 大部分规范，少量命名问题 |
| 性能 | 55/100 | 存在 N+1 和内存分页问题 |
| 可维护性 | 65/100 | 结构清晰，但缺少测试 |
| **综合评分** | **60/100** | **需要重点改进安全和性能** |

---

## 十、整改优先级建议

### 紧急（1-2天内）
1. ✅ 移除 `db-config.txt` 中的明文密码
2. ✅ 生产环境禁用测试账号显示
3. ✅ 更改所有默认密码

### 重要（1周内）
1. 实施输入验证注解
2. 解决 N+1 查询问题
3. 添加关键操作审计日志

### 改进（2-4周）
1. 重构 Controller 层代码
2. 实现 Redis 缓存
3. 补充单元测试

### 长期（1-3个月）
1. 引入微服务架构
2. 实现完整的权限管理系统
3. 建立 CI/CD 流水线

---

## 十一、总结

本项目整体架构合理，功能实现完整，但在**安全性**和**性能**方面存在明显短板。建议团队：

1. **立即处理**所有 P0 级安全问题
2. **优先解决**性能瓶颈（N+1 查询、内存分页）
3. **逐步完善**代码质量和测试覆盖

通过本次审查发现的问题，按照优先级逐步整改，可以显著提升系统的安全性、性能和可维护性。

---

**审查专家签字**: ________________  
**日期**: 2026-05-20  
**版本**: v1.0
