# 代码审查报告（2026-05-18）

## 审查范围

- 后端：`assessment-backend`（Spring Boot 3 / Spring Security / MyBatis-Plus）
- 前端：`assessment-frontend`（Vue 3 / Vite / Pinia / Axios）
- 审查方式：静态代码审查 + 本地构建/测试验证

## 审查结论

项目已经具备可运行的业务骨架，但当前代码在“权限边界、安全暴露面、文件处理、角色上下文一致性、测试覆盖”上存在多处高风险缺口。最大的隐患不是单点 bug，而是系统性的边界设计偏松：前端可以深度参与权限上下文选择，后端又暴露了过多管理型读写接口，导致权限体系虽然“看起来存在”，但真正的收口并不严。

如果这是内网系统，当前实现仍然偏危险；如果未来会进入生产或半生产环境，这些问题必须在上线前完成一轮集中治理。

---

## 主要问题（按严重级别排序）

### P0. 应用内直接暴露“数据库管理后台”，具备高破坏性，且缺少必要审计/隔离/安全护栏

**定位**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/DatabaseAdminController.java:30`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/DatabaseAdminController.java:42`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:29`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:111`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/DatabaseAdminServiceImpl.java:165`
- `assessment-frontend/src/views/admin/DatabaseBrowser.vue:32`

**问题描述**

系统把“库表浏览、任意行更新、物理删除”直接做成了业务应用的一部分。虽然控制器上加了 `@RequireRole("ADMIN")`，但这仍然是一个非常危险的设计：

- 可编辑表覆盖系统核心表和业务主表，范围过宽。
- 删除使用 `DELETE FROM` 物理删除，而不是逻辑删除或业务服务层封装。
- 更新完全绕过业务服务层、校验层、事务语义和领域规则。
- 没有操作审计、变更原因、二次确认、审批流、只读模式、生产环境开关。
- 前端默认就提供“编辑/删除”按钮，说明这不是应急脚本，而是常态化入口。

这类能力一旦被误用，后果不是“页面报错”，而是直接污染生产数据、破坏引用关系、绕过所有业务不变量。

**影响**

- 误删核心数据，且无法从业务日志还原。
- 绕过领域校验写入非法状态，后续流程出现隐性数据损坏。
- 安全事件发生时，难以区分正常业务操作与“后台直改数据”。

**修改方案**

1. 立即停止把该能力作为常规业务页面暴露给应用用户。
2. 将其拆出主应用，迁移为独立运维工具，仅限受控环境使用。
3. 若短期不能下线：
   - 仅保留只读查询。
   - 写操作增加环境开关，生产默认关闭。
   - 所有修改强制记录操作者、原值、新值、原因、时间、请求 ID。
   - 删除改为逻辑删除，且仅允许少数白名单表。
   - 禁止直接修改权限、用户、角色、菜单等系统安全相关表。
4. 任何写操作都必须回归业务 service，不能继续直接拼 SQL 落库。

---

### P1. 文件上传/下载存在明显安全缺口，包含路径遍历与弱校验上传

**定位**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:64`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizSelfEvaluationController.java:71`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizSelfEvaluationServiceImpl.java:291`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizAppealController.java:113`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizAppealController.java:180`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/BizAppealController.java:185`

**问题描述**

`/api/evaluation/self/download/{fileName}` 直接把路径参数拼到本地路径：

- `Paths.get(uploadPath, "self-eval", fileName)` 没有做 `normalize`、父目录逃逸校验、文件名白名单校验。
- 这意味着理论上存在路径遍历风险，攻击者可以尝试构造 `../` 类输入读取非目标文件。

同时，自评附件上传基本没有真正安全校验：

- `uploadAttachment(byte[] fileData, String originalFilename)` 只取扩展名，不做 MIME/魔数校验。
- 申诉上传虽然做了扩展名和 MIME 校验，但 MIME 来自客户端声明，不足以作为可信依据。
- 附件删除接口也缺少明确的“归属校验/角色校验”，只要知道附件 ID 就会尝试删文件和删记录。

**影响**

- 任意文件读取或目录穿透。
- 上传伪装文件，形成恶意脚本/木马/错误内容落盘。
- 越权删除他人附件。

**修改方案**

1. 下载接口只允许下载数据库中登记过、且归属于当前业务记录的文件。
2. 文件访问改为“附件 ID -> 服务层鉴权 -> 解析真实存储路径”，不要再暴露裸文件名路由。
3. 对文件名做严格白名单校验，只允许服务端生成的 UUID 文件名。
4. 路径必须 `normalize()` 后校验仍在指定上传根目录下。
5. 上传时增加：
   - 扩展名白名单
   - MIME 白名单
   - 文件头魔数校验
   - 最大体积限制
   - 病毒扫描或隔离区
6. 删除附件前校验当前用户是否对所属业务单据有操作权限。

---

### P1. 权限上下文过度依赖前端 header 与本地存储，存在“客户端参与服务端授权决策过深”的设计缺陷

**定位**

- `assessment-frontend/src/api/request.ts:31`
- `assessment-frontend/src/api/request.ts:38`
- `assessment-frontend/src/stores/user.ts:73`
- `assessment-frontend/src/stores/user.ts:104`
- `assessment-backend/src/main/java/com/ccerphr/assessment/interceptor/DataScopeInterceptor.java:50`
- `assessment-backend/src/main/java/com/ccerphr/assessment/interceptor/DataScopeInterceptor.java:54`
- `assessment-backend/src/main/java/com/ccerphr/assessment/interceptor/DataScopeInterceptor.java:62`
- `assessment-frontend/src/composables/useDataScope.ts:14`

**问题描述**

当前方案允许前端把 `X-Role-Code`、`X-Scope-Id`、`X-Data-Scope` 作为请求头主动带给后端，后端拦截器再据此选择本次请求的权限上下文。

虽然拦截器会校验“这个 role/scope 是否属于当前用户”，比完全信任前端好一些，但核心问题仍然在：

- 授权上下文的选择权被下放给客户端。
- 权限切换状态保存在 `localStorage`，容易被前端代码/XSS/调试篡改。
- 服务端很多逻辑直接依赖 `DataScopeContext`，而这个上下文的建立又受请求头驱动。
- 这会带来“权限混淆”“审计难以还原”“客户端和服务端状态不一致”的长期问题。

这类设计通常会在系统复杂后演化成大量隐蔽授权 bug。

**影响**

- 同一 token 在不同请求里可携带不同执行上下文，审计复杂。
- XSS 或浏览器调试可操纵当前角色/范围选择。
- 服务端授权模型难以推理，后续维护成本会越来越高。

**修改方案**

1. 服务端应以 token/session 中的“当前激活权限上下文”作为唯一可信来源。
2. 如果业务需要“切换角色/范围”，应提供专门的切换接口：
   - 服务端校验用户可切换到的上下文。
   - 切换成功后签发新 token，或在服务端 session 中更新当前上下文。
3. 普通业务请求不再接受 `X-Role-Code` / `X-Scope-Id` 作为授权依据。
4. `localStorage` 里的角色选择仅用于 UI 展示，不应用于服务端授权。

---

### P1. 多个管理类读取接口缺少角色保护，导致敏感配置和权限数据可被任意登录用户枚举

**定位**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysMenuController.java:42`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysMenuController.java:47`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysMenuController.java:55`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysRoleController.java:35`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysRoleController.java:42`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysRoleController.java:56`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysRoleController.java:93`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserPermissionController.java:39`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserPermissionController.java:49`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserPermissionController.java:83`

**问题描述**

写接口大多加了 `@RequireRole("ADMIN")`，但多个读接口对“任意已登录用户”开放。暴露内容包括：

- 菜单树、菜单明细、按角色查询菜单
- 角色列表、角色详情、角色菜单、角色继承关系
- 用户权限列表、指定用户权限、启用用户清单

这不是简单的“读接口没加注解”问题，而是安全边界只做了“防写”，没有做“防枚举、防侦察、防权限模型泄露”。

**影响**

- 普通用户可推断完整权限模型和后台能力边界。
- 低权限账号可收集高价值横向移动信息。
- 为后续越权尝试提供地图。

**修改方案**

1. 对所有管理域接口做统一分层：
   - 系统管理域：仅 `ADMIN`
   - 单位管理域：`ADMIN` / `FIN_ADMIN`
   - 业务只读域：按具体业务角色最小开放
2. 不要默认“读接口无害”。敏感元数据本身就是资产。
3. 为 controller 增加类级别权限注解，避免靠逐个方法补洞。
4. 增加基于 MockMvc 的授权测试，逐个验证未授权/低权限访问返回 403。

---

### P1. 登录态与角色/组织上下文存在先后顺序错误，可能造成 token 与实际用户上下文不一致

**定位**

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:167`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:170`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/AuthController.java:182`
- `assessment-backend/src/main/java/com/ccerphr/assessment/security/JwtAuthenticationFilter.java:70`
- `assessment-backend/src/main/java/com/ccerphr/assessment/security/RoleCheckAspect.java:24`

**问题描述**

登录流程里，token 在 `user.orgId` 回填之前就生成了：

- 先 `generateToken(...)`
- 后面才根据 `employee.deptId` 回填 `user.orgId/user.orgName`

这会导致 token claim 里的 `orgId` 可能是旧值或空值，而返回给前端的 `userInfo` 已经是新值。再结合：

- 登录响应优先把 `availableRoles` 的第一个角色塞给前端展示
- 过滤器在权限查询失败时又会回退到 JWT 中的 `roleCode`

于是系统内部会同时存在“token 角色/组织”“用户表角色/组织”“前端当前激活角色/范围”三套状态源，极易产生边界错位。

**影响**

- 某些请求按旧 token 上下文鉴权，另一些请求按前端 header 切换上下文。
- 问题出现时难以复现和排查。
- 容易形成偶发性 403、越权、查不到数据等灰色故障。

**修改方案**

1. 在生成 token 前完成用户组织信息回填和有效角色确定。
2. token 中只放“当前激活上下文”，不要同时让前端再自由决定。
3. 取消 `JwtAuthenticationFilter` 中的“权限查询失败则回退 JWT 角色”的兜底逻辑，改为明确失败并记录告警。

---

### P2. 凭证与前端状态存储方案偏弱，默认配置也不够安全

**定位**

- `assessment-backend/src/main/resources/application-dev.yml:15`
- `assessment-backend/src/main/resources/application-dev.yml:17`
- `assessment-frontend/src/api/request.ts:26`
- `assessment-frontend/src/stores/user.ts:32`
- `assessment-frontend/src/stores/user.ts:46`

**问题描述**

当前系统：

- 开发环境 JWT secret 带有硬编码默认值。
- 前端 token 放在 `localStorage`。
- 角色、scope、用户信息也长期落在 `localStorage`。

这意味着一旦前端出现 XSS，攻击者拿到的不是单一 token，而是整套可复用的身份与上下文数据。

**影响**

- XSS 会直接升级为账户接管。
- 默认 secret 若被误带入非开发环境，JWT 可被伪造。

**修改方案**

1. 移除任何默认 JWT secret，启动时强制从环境变量注入。
2. 优先改为 `HttpOnly + Secure + SameSite` Cookie 持有会话或刷新 token。
3. 尽量减少 `localStorage` 中的敏感内容，仅保留低风险 UI 缓存。
4. 配合 CSP、输入输出编码和依赖扫描一起治理前端 XSS 面。

---

### P2. 权限与菜单实现里存在“未完成/临时兜底逻辑”，会让后续行为越来越不可预测

**定位**

- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java:168`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysRoleController.java:155`

**问题描述**

两个信号非常危险：

1. `SysMenuServiceImpl.resolveMenuRoles(...)` 目前直接 `return List.of(currentRole)`，说明“按上下文决定菜单角色”的设计还没真正做完。
2. `SysRoleController.getAvailableRoles()` 在 `roleCode == null` 时直接返回全部角色，这属于明显偏宽的兜底策略。

这种“先放个兜底，后面再补”的代码一旦进生产，通常就不会被真正补齐，最终变成长期安全债务。

**影响**

- 菜单/角色边界与设计意图不一致。
- 某些场景下返回超范围角色集合。
- 前后端对“当前用户到底能切什么角色”难以形成统一语义。

**修改方案**

1. 把 `resolveMenuRoles` 设计补完整，或直接删除未使用分支，避免伪装成已支持。
2. 所有“拿不到上下文就放宽返回”的逻辑改成“拿不到上下文就拒绝/返回空”。
3. 将角色切换、菜单裁剪、数据范围裁剪统一收敛到一个清晰的授权模型。

---

### P2. 测试覆盖严重不足，当前自动化测试几乎不能为核心风险兜底

**定位**

- `assessment-backend/src/test/java/com/ccerphr/assessment/common/GlobalExceptionHandlerTest.java:20`
- `assessment-frontend/package.json:6`

**问题描述**

后端目前只看到了 1 个测试类、2 个测试用例，而且只覆盖异常处理器；前端没有测试脚本，也没有组件测试、路由守卫测试或接口契约测试。

本次验证结果：

- 后端 `mvn17.cmd test` 成功。
- 但测试内容几乎不触达权限、数据范围、文件上传、角色切换、菜单裁剪这些高风险链路。
- 前端 `npm run build` 在当前沙箱环境下因为 `esbuild` 子进程 `spawn EPERM` 未能完成，这更像环境权限限制，不足以证明代码本身有构建错误。

**影响**

- 高风险改动上线前没有自动化护栏。
- 权限回归和越权回归很难第一时间发现。

**修改方案**

1. 后端优先补：
   - 登录/鉴权链路测试
   - `DataScopeInterceptor` 权限上下文测试
   - 管理接口 401/403 授权测试
   - 文件上传/下载安全测试
2. 前端优先补：
   - 路由守卫测试
   - 用户角色切换状态测试
   - 关键页面的接口 mock 测试
3. CI 至少接入：后端单测 + 前端构建 + lint/类型检查。

---

## 架构层面的集中整改建议

### 第一阶段：先止血

1. 下线或只读化数据库管理页面与接口。
2. 封死文件路径遍历风险，附件访问改为按附件 ID 鉴权下载。
3. 给所有管理类读接口补齐角色保护。
4. 移除默认 JWT secret。

### 第二阶段：收权限模型

1. 停止让普通业务请求通过 header 指定角色/范围。
2. 改成“服务端切换上下文 -> 签发新 token / 更新 session”模式。
3. 统一角色、菜单、数据范围三者的来源和生命周期。

### 第三阶段：补测试与审计

1. 为所有安全关键路径补自动化测试。
2. 为高风险操作增加审计日志与 requestId 串联。
3. 建立“权限变更、数据直改、附件操作”的专项审计面板。

---

## 正向评价

- Spring Security、AOP、Flyway、MyBatis-Plus 这些基础设施已经接上，说明项目并不是无序堆砌。
- `DataScopeContext` / `DataScopeInterceptor` 体现了团队已经意识到数据范围隔离的重要性。
- 后端已经开始引入全局异常处理与 requestId，这对后续治理是有帮助的。

问题不在于“完全没做安全”，而在于“做了一半”，而半完成的权限系统往往比完全没有更危险，因为它会给团队一种错误的安全感。

---

## 本次验证记录

- 后端测试命令：`assessment-backend\\mvn17.cmd test`
- 结果：通过
- 前端构建命令：`assessment-frontend\\npm run build`
- 结果：当前环境触发 `esbuild` 子进程 `spawn EPERM`，未能完成；更像执行环境限制，不能据此判断源码构建失败

