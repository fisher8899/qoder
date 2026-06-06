# 代码审核报告 (2026-05-25)

## 1. 审核概况
本次审核针对 `assessment-backend` (Spring Boot 3.2) 和 `assessment-frontend` (Vue 3) 进行了全面的深度扫描。重点关注系统安全性、架构一致性、生产环境可靠性以及代码质量。

## 2. 核心风险项 (P0 - 必须在发布前解决)

### 2.1 架构性矛盾：Stateless 配置与 Session 依赖
- **发现问题**：`SecurityConfig` 配置了 `SessionCreationPolicy.STATELESS`，明确告知 Spring Security 不使用 Session。然而，`AuthController` 和 `DataScopeInterceptor` 却严重依赖 `HttpSession` 来存储 `ACTIVE_ROLE_CODE` 和 `ACTIVE_SCOPE_ID`。
- **后果**：
    - 在多实例（集群）部署时，由于 Session 无法在节点间共享（除非配置分布式 Session），用户在切换节点后会丢失权限上下文。
    - 前端 `request.ts` 已移除 Header 中的角色信息，完全依赖 Session。如果线上环境配置了严格的跨域或反向代理策略，Session Cookie 可能失效。
- **建议**：将活跃角色和范围 ID 编码进 JWT Token，或使用 Redis 统一管理 Session。

### 2.2 垂直权限控制缺失
- **发现问题**：大量 Controller 方法（如 `SysEmployeeController`, `SysLeaderController` 等）没有任何安全注解（如 `@PreAuthorize`）。虽然服务层有部分数据过滤，但接口层面的权限控制极其薄弱。
- **后果**：恶意用户可能通过猜测接口地址和 ID，跨过前端限制直接调用敏感操作（如删除人员、修改权限）。
- **建议**：在所有 Controller 方法上添加 `@PreAuthorize("hasAnyRole('...')")` 注解。

## 3. 代码质量与性能 (P1 - 建议上线前优化)

### 3.1 循环内数据库查询 (N+1 问题)
- **发现位置**：`BizExamGroupServiceImpl.getProgress` (第 414 行起)。
- **发现问题**：在循环考核组成员时，对每个成员都执行了 `countIndicatorByOrg` 查询。
- **建议**：使用 SQL 的 `GROUP BY` 一次性查出所有组织的指标计数，在内存中进行 Map 映射。

### 3.2 文件上传路径安全
- **发现问题**：`application.yml` 中的 `uploadPath` (默认 `D:/uploads/`) 具有硬编码嫌疑。
- **建议**：在生产环境配置中使用相对路径或专用的对象存储服务（OSS），并确保路径在 Linux 环境下（如 `/var/lib/assessment/uploads/`）同样有效。

## 4. 前端代码分析
- **路由守卫**：`router.beforeEach` 实现了动态菜单抓取和权限检查，用户体验良好。
- **状态管理**：Pinia 使用规范，`userStore` 集中处理了角色切换逻辑。
- **风险点**：前端角色切换逻辑 (`switchRoleByScope`) 依赖后端 Session 变更。若后端 Session 过期而前端 LocalStorage 的 Token 仍有效，会导致数据范围越权或请求报错。

## 5. 生产环境准备度
- **数据库**：目前仅见 `application.yml` 基础配置。建议增加连接池（HikariCP）调优参数。
- **日志**：代码中存在 `System.out.println` 或 `printStackTrace`（见 `SysOrganizationServiceImpl` 等），应统一替换为 `SLF4J` 日志记录。
- **跨域**：`CorsConfig` 已配置，但应确保生产环境下 `allowCredentials` 与 Cookie 策略匹配。

## 6. 审核结论
**风险级别：中高**
本项目在业务逻辑实现上较为完整，但**安全性架构存在底层冲突**（Stateless vs Session）。在当前状态下发布到公网或多节点环境将面临严重的安全和稳定性问题。

**操作建议：**
1. 立即修正 Security 架构，确保 JWT 携带完整上下文或启用 Redis Session。
2. 为所有管理类接口补充 `@PreAuthorize` 保护。
3. 优化 `BizExamGroupServiceImpl` 中的 N+1 查询问题。
