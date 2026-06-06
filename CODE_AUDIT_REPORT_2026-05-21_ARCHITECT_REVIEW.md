# 代码审核报告 2026-05-21

## 项目

- 后端：`assessment-backend`，Spring Boot 3.2 + Spring Security + MyBatis-Plus
- 前端：`assessment-frontend`，Vue 3 + Vite + Pinia + Element Plus
- 审核视角：架构安全、权限边界、数据一致性、可运维性、工程质量

## 总体结论

这套系统现在不是“有一些瑕疵”，而是**核心控制面混乱**。

后端把认证、授权、数据范围、角色切换、会话状态、JWT 状态几套机制硬拼在一起，表面上都在工作，实际上是靠偶然条件维持平衡。只要部署方式、代理策略、Cookie 策略、角色数量或人员权限结构稍微变化，就会出现权限错乱、接口偶发 403、菜单与真实权限不一致、越权写入、生产数据被误改这类事故。

更直接一点说：**这不是“功能多导致复杂”，这是边界设计失控。**

## 审核方式

- 代码静态审计：控制器、服务、鉴权、数据权限、前端路由与请求层
- 编译/运行验证：
  - 后端 `mvn test` 已通过，现有测试 `2` 个
  - 后端 `http://localhost:8080/api-docs` 返回 `200`
  - 前端 `npm run build` 在当前机器上触发 `spawn EPERM`，这是本机执行权限问题，不能据此证明业务代码一定能稳定构建

## 结论分级

- `P0`：必须立即整改，否则线上迟早出事故
- `P1`：高风险设计缺陷，应在本轮治理中完成
- `P2`：中风险质量问题，会持续制造维护成本和灰度故障

## 核心问题

### P0-1 写接口授权门槛严重不足，业务写能力暴露给“任意已登录用户”

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysEmployeeController.java:38,44,50`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysLeaderController.java:73,79,85`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysIndicatorCategoryController.java:39,45,51`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysOrganizationController.java:78,85,92`

这些控制器的新增、修改、删除接口没有 `@RequireRole` 之类的硬授权注解。个别接口依赖服务层数据范围过滤，个别接口依赖手写方法判断，但**没有形成统一、刚性的访问控制边界**。

**问题本质**

系统把“能看到什么”误当成了“能改什么”。这两件事完全不是一回事。读权限过滤不能替代写权限校验。

**影响**

- 普通业务角色只要拿到 token，就可能调用本不该拥有的维护接口
- 组织、人员、领导、指标分类等基础数据可被非管理员改写
- 问题不会稳定复现，因为是否成功还取决于当前数据范围上下文，排查成本极高

**最优方案**

1. 立即为所有写接口建立统一授权策略：控制器层必须加 `@RequireRole` 或等价的统一访问控制注解。
2. 把“管理员能力”“单位范围管理能力”“部门业务能力”拆开，不允许控制器自行各写一套判断。
3. 对写操作增加对象级权限校验：不仅校验角色，还要校验目标对象是否在当前操作者可管理范围内。
4. 建立一份接口权限矩阵，禁止新增接口时绕过矩阵直接上线。

---

### P0-2 认证模型自相矛盾：你把系统声明成无状态，却又依赖 Session 存角色上下文

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/config/SecurityConfig.java:34`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:203,204,209,210,237,267,268,274,321`
- `assessment-backend/src/main/java/com/ccerphr/assessment/interceptor/DataScopeInterceptor.java:47,52,53,59,71,73`
- `assessment-frontend/src/api/request.ts:19,28`
- `assessment-frontend/src/layouts/MainLayout.vue:161,164`

`SecurityConfig` 明确配置了 `SessionCreationPolicy.STATELESS`，但 `AuthController` 又把 `ACTIVE_ROLE_CODE`、`ACTIVE_SCOPE_ID` 写进 `HttpSession`，`DataScopeInterceptor` 再从 Session 里读当前角色上下文。

前端请求层只显式发送 JWT：

- `assessment-frontend/src/api/request.ts:28`

并没有明确设置跨域 `withCredentials`。在本地通过 Vite 代理时，Cookie 可能“刚好可用”；一旦前后端分域部署，这套上下文传递机制就会立即变成不稳定系统。

**问题本质**

这是典型的“半无状态架构”灾难：JWT 负责身份，Session 负责当前职责，拦截器再把职责塞进 ThreadLocal，前端又在本地缓存一份角色状态。四份状态源同时存在，谁是准的，没有统一答案。

**影响**

- 角色切换后菜单、接口、数据范围可能短时间不一致
- 部分请求在某些部署形态下拿不到 Session，直接 403
- 同一 token 在不同机器、不同代理、不同浏览器策略下表现不一致
- 问题极难定位，因为代码看起来“没有报错”，只是行为漂移

**最优方案**

二选一，不能继续混用：

1. 纯 JWT 模式：
   - 当前激活角色、范围版本号写入 token
   - 切换角色后重新签发 token
   - 服务端完全移除 Session 依赖
2. 纯 Session 模式：
   - 不再把 JWT 作为主认证载体
   - 前后端统一按会话认证工作

对这个项目，我建议选 **纯 JWT 模式**。原因很简单：你已经用了前后端分离栈，现在再回头抱 Session，只会让系统继续烂下去。

---

### P0-3 数据库浏览器是带管理员入口的生产自毁按钮

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:35-63`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:66`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:124`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:170`
- `assessment-frontend/src/views/admin/DatabaseBrowser.vue`

允许在线浏览和直接修改的表包括：

- `sys_user`
- `sys_user_permission`
- `sys_role`
- `sys_role_menu`
- `sys_menu`
- 以及多张核心业务表

而“不可编辑字段”只有：

- `id`
- `created_time`
- `updated_time`
- `deleted`

这意味着管理员可以直接改密码字段、角色字段、权限字段、菜单字段、状态字段；删除更是物理删除，不走软删、不走业务校验、不走审计流程。

**问题本质**

这不是“运维辅助工具”，这是**绕过业务层规则的后门式数据改写器**。

**影响**

- 任何线上脏数据都可能被“现场手改”，从此失去可追溯性
- 软删设计被直接破坏
- 用户、角色、菜单、权限之间的业务约束会被绕过
- 一次误删就可能导致整条审批链、评分链、组织链断裂

**最优方案**

1. 生产环境彻底下线这个功能。
2. 如果必须保留，只允许查看，不允许改写系统控制表。
3. 所有变更必须走显式的业务服务，带审计日志、操作人、前后值、回滚策略。
4. 删除操作改为软删或逻辑归档，严禁通用物理删。

这个模块不该继续美化，它应该被收权、分层、审计化。否则它迟早成为事故入口。

---

### P1-1 默认密码和自动建号策略非常粗暴，已经踩进安全红线

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysOrganizationServiceImpl.java:198,211,214`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysUserServiceImpl.java:24,119,124,127,130,135`

当前系统存在两条危险路径：

1. 组织分配负责人时自动创建账号，默认密码固定为 `123456`
2. 管理员重置密码时默认密码仍然是 `123456`

还顺手做了拼音用户名自动生成，这让账号可猜性进一步上升。

**问题本质**

这不是便捷，这是把身份安全当脚本生成字段处理。

**影响**

- 批量弱口令
- 账号名高度可预测
- 新建账号和重置账号都没有强制改密
- 审计上无法过关，实战上更扛不住撞库和社工

**最优方案**

1. 彻底移除固定默认密码。
2. 改为一次性激活链接或随机高强度初始密码。
3. 首次登录必须改密。
4. 新账号创建和密码重置必须留完整审计记录。
5. 禁止在业务服务里静默自动建号，改为显式的用户开通流程。

---

### P1-2 角色模型是分裂的，系统没有一个可信的“当前角色真相源”

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/security/JwtAuthenticationFilter.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/security/RoleCheckAspect.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:70,196,203,237,274`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserController.java:119,147,148`
- `assessment-frontend/src/stores/user.ts:119,162,170,184`

现状是：

- `sys_user.roleCode` 存一个主角色
- `sys_user_role` 存多角色
- `sys_user_permission` 存带范围的有效角色
- JWT claim 里只放一个 `roleCode`
- Session 里又放一个当前激活角色
- 前端本地存储再缓存一个当前角色

这不是多角色设计，这是多份真相互相打架。

**影响**

- 菜单角色、接口角色、数据范围角色可能各自说不同的话
- 用户改角色后，部分地方立即生效，部分地方要刷新，部分地方继续读旧值
- 任何“为什么我有菜单却调不了接口”之类的问题都会变成常态

**最优方案**

1. 明确单一真相源：建议以“用户有效权限集 + 当前激活角色上下文”为准。
2. `sys_user.roleCode` 只能作为兼容字段或废弃字段，不能继续充当业务真相。
3. 所有鉴权、菜单、数据范围、审批流都从统一权限快照读取。
4. 角色切换必须是一次明确的状态变更，而不是前端本地猜测。

---

### P1-3 源码字符集已经污染，用户提示和日志文本出现大面积乱码

**证据**

- `assessment-backend/src/main/java/com/ccerphr/assessment/common/Result.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java`
- `assessment-frontend/src/views/login/LoginPage.vue`
- `assessment-frontend/src/api/request.ts`

现在大量中文字符串已经不是“显示不好看”，而是**源码层编码污染**。这会直接造成：

- 用户提示乱码
- 前后端消息不一致
- 日志可读性极差
- 后续再修国际化时成本翻倍

**最优方案**

1. 统一源码、构建、IDE、Git、Maven、Node 的字符集为 `UTF-8`。
2. 全量修复乱码字面量，禁止继续带脏编码提交。
3. 用户文案与错误码分离，业务层尽量返回稳定错误码，前端负责展示文案。

---

### P1-4 测试覆盖近乎空白，最近这次编译事故本来完全可以在提交前被挡住

**证据**

- 当前仅发现一个测试文件：
  - `assessment-backend/src/test/java/com/ccerphr/assessment/common/GlobalExceptionHandlerTest.java`
- 当前测试总量：`2` 个

这次后端编译失败的根因是类结构损坏和缺失导入。这种错误如果连最基本的 CI 编译和冒烟测试都没有，那不是偶发，而是流程缺失。

**问题本质**

你们现在不是“测试少”，而是**没有工程护栏**。

**最优方案**

1. 后端至少建立三层护栏：
   - `mvn test`
   - Spring 上下文启动测试
   - 权限链路与关键服务用例
2. 前端至少建立：
   - `npm run build`
   - 路由守卫与权限切换单测
   - 关键页面冒烟测试
3. 提交前和合并前必须自动执行。

---

### P2-1 前端角色切换存在明显竞态，权限刷新时序不可靠

**证据**

- `assessment-frontend/src/layouts/MainLayout.vue:157-166`
- `assessment-frontend/src/stores/user.ts:162-184`

`handleRoleChange` 调用了异步的 `userStore.switchRoleByScope(roleCode, scopeId)`，但没有 `await`，随后立刻执行：

- `loadPendingEvalCount()`
- `loadPendingAppealCount()`
- `loadMenus()`
- `router.push('/dashboard')`

这会让页面在旧上下文、旧菜单、旧数据范围下先跑一轮请求，然后再被 `window.location.reload()` 强制刷新。

**影响**

- 菜单闪烁
- 角色切换后偶发 403
- 页面短暂展示旧角色数据

**最优方案**

1. `handleRoleChange` 必须 `await userStore.switchRoleByScope(...)`
2. 切换角色后不要再 `window.location.reload()` 粗暴重载
3. 用统一的权限快照刷新流程：切换成功 -> 拉取新权限 -> 刷新菜单 -> 导航落点

## 架构整改优先顺序

### 第一阶段：一周内必须完成

1. 给所有写接口补齐硬权限校验。
2. 关停或只读化数据库浏览器。
3. 废除固定默认密码 `123456`。
4. 冻结继续扩展 Session + JWT 混用方案。

### 第二阶段：两周内完成

1. 重构认证与角色切换模型，确定单一真相源。
2. 把数据范围控制统一收口到一个权限上下文模型。
3. 修复源码乱码和字符集配置。

### 第三阶段：一个月内完成

1. 建立后端编译、测试、启动、权限冒烟 CI。
2. 建立前端构建与权限链路测试。
3. 建立审计日志、密码治理、配置治理规范。

## 最终判断

这套系统当前最大的问题不是某个类写坏了，也不是哪条 SQL 不优雅，而是**控制面设计没有收敛**。

如果继续在这个基础上堆需求，结果只会是：

- 权限越来越像玄学
- 数据越来越依赖人工修表
- 故障越来越难复现
- 开发越来越不敢动核心链路

最优解不是继续打补丁，而是先把三条主线收口：

1. 认证与角色上下文统一
2. 写权限与数据范围统一
3. 运维工具与业务规则隔离

不先做这三件事，后面所有“优化”“加功能”“提效率”都只是给事故加速。
