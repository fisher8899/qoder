# 变更日志 (CHANGELOG)

> 本文档记录所有需求变更、技术调整和重要修改
> 格式遵循 [Keep a Changelog](https://keepachangelog.com/)

---

## [0.5.0] - 2026-05-15

### 新增

- `sys_menu.menu_category` 菜单类别字段，支持 `SYSTEM/UNIT/DEPT` 逗号分隔多选。
- Flyway 迁移 `V3__menu_category_scope.sql`，用于添加菜单类别字段、补齐缺失菜单并回填存量菜单类别。
- 专项归档文档 `docs/menu-category-scope-refactor-2026-05-15.md`。

### 变更

#### 需求变更 #2: 菜单类别驱动职责菜单与数据范围统一模型

- **变更日期**: 2026-05-15
- **提出人**: 用户提出
- **原状态**: 菜单没有类别属性，职责可选菜单与数据范围控制未统一建模。
- **新状态**:
  - 系统职责可选全部菜单
  - 单位职责只可选单位类菜单
  - 部门职责只可选部门类菜单
  - 用户权限分配按职责类型强制数据范围
- **解决方案**:
  - 后端菜单服务按职责类型返回可分配菜单
  - 后端职责菜单保存时强制校验菜单类别
  - 前端菜单定义页增加类别多选
  - 前端职责定义页按职责类型加载菜单树
  - 主菜单加载改为 `/menu/current`
- **验证方式**:
  - 后端 `mvn -q -DskipTests package` 通过
  - 前端 `npm run build` 通过

### 安全

- 职责菜单分配增加服务端校验，避免绕过前端分配错误类别菜单。
- 用户权限分配按职责类型归一数据范围：
  - `SYSTEM -> ALL`
  - `UNIT -> UNIT`
  - `DEPT -> ORG`

---

## [0.4.0] - 2026-05-14

### 安全 (Security)

#### P0-2: 数据权限修复
- **问题**: `DataScopeInterceptor` 从 HTTP Header 读取权限信息无任何校验，攻击者可伪造 `X-Role-Code: ADMIN` + `X-Data-Scope: ALL` 绕过全部数据隔离
- **修复**: 改造 `DataScopeInterceptor`，从 `SecurityContextHolder` 获取用户ID，查询 `sys_user_permission` 表验证前端发送的角色和范围是否在用户有效权限列表中
- **影响文件**: `interceptor/DataScopeInterceptor.java`, `mapper/SysUserPermissionMapper.java`

#### P0-1: RBAC 角色授权
- **问题**: 22 个 Controller 所有接口无任何角色控制，任意认证用户可调用系统管理接口
- **修复**:
  - `SecurityConfig` 增加路径级别 `hasRole()` 限制
  - `JwtAuthenticationFilter` 查询用户所有角色存入 `Authentication`
  - 关键 Controller 方法添加 `@RequireRole` 注解
- **影响文件**: `config/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`, `security/RoleCheckAspect.java`, 多个 Controller

#### P0-9: CORS 安全配置
- **问题**: `CorsConfig` 使用 `addAllowedOriginPattern("*")` 允许任意来源
- **修复**: 改为从配置文件读取允许的域名，开发环境允许 `localhost:*`，生产环境配置具体域名
- **影响文件**: `config/CorsConfig.java`

### 新增

- `SecurityUtil.java` - 从 `SecurityContextHolder` 获取当前用户信息的工具类
- `V2__schema_sync.sql` - 数据库迁移脚本，安全添加缺失列

### 变更

#### P0-3: Schema 与实体同步
- **问题**: 8 个 `biz_*` 表缺少 `unit_id` 列，`DataScopeFilter` 的数据隔离逻辑无法生效
- **修复**:
  - `schema.sql` 添加缺失列：`unit_id`、`role_type`、`scope_id`、`scope_name`、`sort_code`
  - 新增 `sys_role_child` 建表语句
  - `data.sql` 为 `sys_role` 添加 `role_type` 初始值
- **影响文件**: `db/schema.sql`, `db/data.sql`

#### P1-4: 最终得分规则修复
- **问题**: `calcFinalScore` 使用 `max(adminScore, deptScore)`，与业务规则不符
- **修复**: 改为优先取 `adminScore`，为空时取 `deptScore`
- **影响文件**: `service/impl/BizReviewScoreServiceImpl.java`

#### P1-5: 复核提交生成全量成绩
- **问题**: 复核提交后未生成 `biz_monthly_score` 汇总数据
- **修复**: 在 `submitReview` 方法末尾增加 `generateMonthlyScores()` 方法
- **影响文件**: `service/impl/BizReviewScoreServiceImpl.java`

#### P1-6: 月度考核状态机
- **问题**: 考核组状态变更方法无前置状态校验，可从任意状态执行
- **修复**: 在每个状态变更方法中增加前置状态校验
- **影响文件**: `service/impl/BizExamGroupServiceImpl.java`

#### P1-7: 自评冒充防护
- **问题**: 自评/他评保存提交时未校验 `orgId` 是否在用户数据范围内，可冒充其他部门
- **修复**: 创建 `validateOrgAccess()` 方法校验数据范围权限
- **影响文件**: `service/impl/BizSelfEvaluationServiceImpl.java`

#### P1-8: 申诉处理回写评分
- **问题**: 申诉处理后未将新分数回写到 `BizReviewScore`
- **修复**: 添加 `updateReviewScore()` 方法，申诉处理后更新评分记录
- **影响文件**: `service/impl/BizAppealServiceImpl.java`

---

## [0.3.0] - 2026-05-13

### 修复

- 修复指标设定进度查询页面"考核组织名称"为空的问题
  - **根因**：`BizIndicatorDefinitionServiceImpl.createIndicator/updateIndicator` 保存指标时只设置了 `orgId`，从未设置 `orgName` 字段，导致数据库中 `org_name` 始终为 NULL
  - **修复**：添加 `fillOrgName()` 方法，在保存时根据 `orgId` 从 `sys_organization` 表自动查询填充 `orgName`
  - **数据修复**：添加 `DataFixRunner` 启动时自动修复历史数据

### 新增

- `DataFixRunner.java` - 启动时自动执行数据修复脚本

---

## [0.2.0] - 2026-05-13

### 新增

- 添加项目管理文档体系 (progress.md, CHANGELOG.md, project-management.md)

### 变更

#### 需求变更 #1: 角色编码统一

- **变更日期**: 2026-05-12
- **提出人**: 代码审核发现
- **原状态**: 前端使用`FIN_ADMIN`, 数据库初始化数据使用`EXAM_ADMIN`
- **新状态**: 统一为`FIN_ADMIN`
- **影响范围**:
  - `assessment-backend/src/main/resources/db/data.sql` - 角色初始化数据
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/*.java` - 角色判断逻辑
- **解决方案**: 修改初始化SQL，统一角色编码
- **验证方式**: 新用户登录验证角色路由可访问

#### 技术调整 #1: 数据权限过滤优化

- **调整日期**: 2026-05-12
- **提出人**: 安全审核
- **问题描述**: Service层数据权限过滤不完整
- **解决方案**: 在各Service实现类中增加数据权限过滤逻辑
- **影响文件**:
  - `BizExamGroupServiceImpl.java`
  - `BizIndicatorDefinitionServiceImpl.java`
  - `SysOrganizationService.java`
  - `SysNotificationService.java`
- **验证方式**: 不同角色用户登录，验证数据隔离

### 修复

- 修复 `IndicatorSet.vue` 多余闭合大括号导致构建失败
- 修复 `JwtTokenProvider` 异常处理逻辑
- 修复组织管理相关接口权限校验

---

## [0.1.0] - 2026-05-11

### 新增

- 代码审核报告生成 (`CODE_REVIEW_REPORT.md`)

### 修复

- 前端TypeScript编译错误修复

---

## [0.0.1] - 2026-04-30

### 新增

- 项目初始化
- Spring Boot后端框架搭建
- Vue 3前端框架搭建
- 数据库Schema设计
- 基础CRUD功能实现
- JWT认证集成
- 角色路由守卫

---

## 变更类型说明

| 类型 | 说明 |
|------|------|
| 新增 (Added) | 新功能、新特性 |
| 变更 (Changed) | 现有功能的修改 |
| 修复 (Fixed) | Bug修复 |
| 移除 (Removed) | 删除的功能 |
| 安全 (Security) | 安全相关的修复 |
| 废弃 (Deprecated) | 即将移除的功能 |

---

## 需求变更记录模板

```markdown
#### 需求变更 #N: [变更标题]

- **变更日期**: YYYY-MM-DD
- **提出人**: [提出人/来源]
- **原状态**: [原有实现/行为]
- **新状态**: [新的实现/行为]
- **原因**: [变更原因]
- **影响范围**: [涉及的模块/文件]
- **解决方案**: [技术方案]
- **验证方式**: [如何验证变更正确]
- **关联问题**: [相关Issue/问题编号]
```

---

## 技术调整记录模板

```markdown
#### 技术调整 #N: [调整标题]

- **调整日期**: YYYY-MM-DD
- **提出人**: [提出人/来源]
- **问题描述**: [原有问题]
- **解决方案**: [技术方案]
- **影响文件**: [涉及的文件列表]
- **验证方式**: [如何验证调整正确]
- **性能影响**: [如有性能影响需说明]
```
