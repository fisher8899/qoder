# 代码审核报告 (2026-05-20)

**审核专家：** 资深代码架构专家
**审核日期：** 2026-05-20
**项目范围：** `assessment-backend`, `assessment-frontend`
**审核级别：** 深度架构级审核

---

## 一、 总体评价：金玉其外，败絮其中

项目目前构建了一套看似完整的业务流程（指标管理、考核执行、绩效申诉等），并引入了 Spring Security、AOP 等工业级组件。然而，在架构底层，系统存在**致命的安全缺陷**和**典型的性能反模式**。

最核心的问题在于：**安全边界完全建立在客户端的“自觉性”之上**。如果说一个安全的系统应该是“堡垒”，那么当前项目就像是一个“虽然锁了门，但钥匙就挂在门把手上，且门把手会告诉路人怎么拧开它”的破屋。

---

## 二、 核心风险点（按严重程度排序）

### 1. 架构级崩溃：客户端驱动的授权模型 (CCA Pattern)
**定位：**
- 后端：`DataScopeInterceptor.java:50-54`, `RoleCheckAspect.java:24`
- 前端：`request.ts:31-42`

**深度剖析：**
系统允许前端通过 `X-Role-Code` 和 `X-Scope-Id` 等 Header 直接指挥后端“我当前是谁”以及“我能看哪里的数据”。
- **越权风险：** 虽然后端有校验“该角色是否属于用户”，但这种**由客户端决定当前激活角色**的设计属于典型的“授权逻辑下移”。一旦用户拥有多个角色（如：既是普通员工又是管理员），恶意用户可以通过脚本频繁切换角色上下文，探测系统对不同角色的处理差异。
- **状态不一致：** `RoleCheckAspect` 在拿到不到上下文时会降级到 JWT 中的第一个权限，这导致“同一用户、同一 Token、不同接口”可能在不同的权限上下文中运行，系统行为不可预测。

**改进方案：**
- **必须收口：** 取消所有 `X-` 开头的权限指令 Header。
- **服务端维护：** 引入 `RoleSwitch` 专用接口。用户切换角色后，服务端在 Redis/Session 或签发新的 JWT 中存储**当前唯一激活上下文**。
- **无状态校验：** 所有的业务接口鉴权，只允许从服务端经过验证的 Security Context 中获取，禁止通过 Request 参数或 Header 动态指定。

### 2. 基础设施自杀：业务应用内置“超级管理工具”
**定位：**
- `DatabaseAdminServiceImpl.java`
- `DatabaseAdminController.java`

**深度剖析：**
在业务代码里手写一个“数据库网页版管理后台”，这是极不专业的表现。
- **白名单失效：** `ALLOWED_TABLES` 虽然做了限制，但包含了 `sys_user` 等核心安全表。这意味着管理员可以通过业务界面，直接修改自己的权限、重置他人密码，且**完全绕过业务审计日志**。
- **审计真空：** `deleteRow` (line 170) 直接执行 `DELETE FROM` 物理删除。在生产环境下，如果管理员误点或恶意删除，数据将永久丢失，且在应用层无法追溯“谁在什么时候删除了什么”。
- **运维风险：** 这种工具属于运维域 (Management Plane)，不属于业务域 (Control/Data Plane)。将其混在一起，增加了受攻击面。

**改进方案：**
- **彻底物理隔离：** 立即从 `assessment-backend` 中删除此 Controller 和 Service。
- **专业工具替代：** 使用 Navicat, DBeaver 或成熟的运维监控面板，并通过网络防火墙 (IP 白名单) 进行二次防护。

### 3. 性能杀手：隐藏在 Stream 中的 N+1 查询
**定位：**
- `BizIndicatorDefinitionServiceImpl.java:342-365` (convertToVO 方法)

**深度剖析：**
在 `queryPage` 分页查询时，代码对每一行记录都调用了 `convertToVO`。
- **恐怖的查询量：** 在 `convertToVO` 中，为了获取一个名称，竟然又分别调用了 `examGroupMapper.selectById`、`indicatorOrgMapper.selectByIndicatorId` 和 `indicatorLeaderMapper.selectByIndicatorId`。
- **后果：** 如果分页每页 20 条数据，查询一次列表将产生 $1 + 20 \times 3 = 61$ 次数据库交互。一旦用户并发增加，数据库连接池将迅速枯竭，系统响应时间呈指数级上升。

**改进方案：**
- **SQL 关联查询：** 在 Mapper 层使用 `LEFT JOIN` 一次性查出所需关联数据。
- **批量预取：** 如果不想使用 JOIN，应先收集本页所有 `indicatorId`，通过 `IN` 查询一次性查出所有的 `ExamGroup`、`Org` 和 `Leader`，在内存中进行 Map 映射填充。

---

## 三、 代码质量与技术债

### 1. 编码乱码与注释规范
- `BizIndicatorDefinitionServiceImpl.java` 多处注释（如 81、154 行）存在明显的字符集转换乱码。这说明团队内部的 IDE 编码（UTF-8 vs GBK）不统一，会直接导致 Git 冲突和生产环境编译隐患。
- **建议：** 强制所有工程使用 UTF-8，并在 Git 钩子中增加非 ASCII 字符检测。

### 2. 业务逻辑与安全校验的耦合
- `normalizeOwnerFields` (line 188) 将“权限校验”和“字段格式化”混在一起。这种逻辑应该抽离到专门的验证器或拦截器中，Service 层应专注于领域逻辑。

### 3. 文件处理的“路径遍历”幽灵
- 依然存在的 `fileName` 直接拼接风险。虽然之前的审计提到过，但目前的修复看起来只是在某些地方加了白名单。
- **方案：** 所有文件下载必须通过“文件 ID”索引，禁止在 URL 中出现原始文件名或相对路径。

---

## 四、 最终整改路线图 (Action Plan)

| 优先级 | 任务 | 目标 |
| :--- | :--- | :--- |
| **Urgent** | **下线 DB Admin** | 删除 `DatabaseAdminController` 和相应 Service，封死物理删除入口。 |
| **High** | **重构权限 Header** | 前端停止发送 `X-Role-Code`，后端改为从 Session/JWT 强认证获取。 |
| **High** | **消除 N+1 查询** | 修改 `BizIndicatorDefinitionServiceImpl`，改用批量查询模式。 |
| **Medium** | **规范化注释** | 修复源码中的乱码，统一团队编码标准。 |
| **Medium** | **补齐单元测试** | 针对权限切换逻辑补齐 MockMvc 测试，确保护栏有效。 |

---
**结论：** 
该系统目前处于“带病运行”状态。如果不立即执行上述整改，尤其是 **权限收口** 和 **DB Admin 下线**，该项目在任何正式环境上线都将面临极高的安全合规风险。
