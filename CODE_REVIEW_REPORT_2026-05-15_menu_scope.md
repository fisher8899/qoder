# 代码审阅报告（菜单类别与职责范围改造）

审阅时间：2026-05-15  
审阅范围：本次“菜单类别/职责可分配菜单范围”相关改造，重点覆盖：

- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysMenuController.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/SysMenuService.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysRoleServiceImpl.java`
- `assessment-backend/src/main/resources/db/migration/V3__menu_category_scope.sql`
- `assessment-frontend/src/views/admin/MenuManagement.vue`
- `assessment-frontend/src/views/admin/RoleManagement.vue`
- `assessment-frontend/src/api/admin.ts`

## 发现的问题

### P0-1 后端当前处于“接口已改、实现缺失”的断裂状态，代码无法通过编译

这不是风格问题，这是直接阻断交付的问题。

- [SysMenuService.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/SysMenuService.java:18) 新增了 `getAssignableMenuTree(String roleType)`。
- [SysMenuController.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysMenuController.java:52) 已经开始调用这个方法。
- 但 [SysMenuServiceImpl.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java:29) 整个类里并没有这个方法的实现。

同一批改动里还有第二处同级别断裂：

- [SysRoleServiceImpl.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysRoleServiceImpl.java:168) 调用了 `SysMenuServiceImpl.isMenuAllowedForRoleType(menu, role.getRoleType())`。
- 但 [SysMenuServiceImpl.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java:259) 只有私有方法 `supportsRoleType(String menuCategory, String roleType)`，并不存在这个静态方法。

这类问题说明改造没有完成最基本的闭环检查。接口、控制器、服务调用三处没有一起收口，属于典型的“改了一半就停笔”。

影响：

- 后端一旦进入可编译环境，会直接在编译期爆炸。
- 职责分配菜单这条主链路根本不可发布。

建议：

- 先补全 `getAssignableMenuTree` 的实现。
- 把“菜单是否允许某职责类型分配”的判断逻辑抽到稳定的公共方法，不要让 `SysRoleServiceImpl` 去硬绑另一个实现类的私有/假定静态方法。
- 这类改动必须以一次后端编译通过作为提交前门槛。

### P1-2 菜单类别与需求定义不一致，`指标审批` 被错误归到单位类

用户给出的规则很明确：`指标审批` 是“部门级功能”。你们的迁移脚本却把它归到了单位类。

- [V3__menu_category_scope.sql](D:/qoder/assessment-backend/src/main/resources/db/migration/V3__menu_category_scope.sql:48) 到 [V3__menu_category_scope.sql](D:/qoder/assessment-backend/src/main/resources/db/migration/V3__menu_category_scope.sql:66) 把 `INDICATOR_APPROVAL` 放进了 `UNIT` 集合。

这不是小偏差，这是把权限模型改错了。后果很直接：

- 单位职责会拿到本不该出现的菜单；
- 部门职责分配时反而拿不到这个菜单；
- 后续如果前端按菜单类别筛选，可配置范围会与真实业务要求相反。

建议：

- 立即按需求把 `INDICATOR_APPROVAL` 调整为 `DEPT`，或明确为多选类别并写清业务边界。
- 把菜单类别映射做成可审计清单，不要继续把业务规则散落在 SQL 字面量里靠人工肉眼维护。

### P1-3 前端把 `SYSTEM` 展示成“全部”，语义是错的，而且会误导配置人员

这个命名很危险，属于看起来没问题、实际很容易把系统配坏的那种错误。

- [MenuManagement.vue](D:/qoder/assessment-frontend/src/views/admin/MenuManagement.vue:57) 到 [MenuManagement.vue](D:/qoder/assessment-frontend/src/views/admin/MenuManagement.vue:62) 把 `SYSTEM` 选项展示为“全部”。
- 但后端实际语义不是“全部角色都可用”，而是“只匹配 `SYSTEM` 职责类型”：
  - [SysMenuServiceImpl.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java:263) 到 [SysMenuServiceImpl.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java:265)

也就是说，界面文案告诉配置人员“这是全部”，系统行为却是“这是系统管理员专属”。这种错法非常阴险，因为：

- 配置当场看不出错；
- 一旦有人按字面理解去配共享菜单，结果一定偏；
- 最后背锅的是权限系统，而不是这三个字。

建议：

- 把 `SYSTEM` 文案改成“系统类”或“系统管理员”。
- 如果真要表达“所有类别都可用”，那应该是 `SYSTEM,UNIT,DEPT` 的组合状态，而不是单个 `SYSTEM`。

### P1-4 这次改造并没有实现“根据菜单类别决定数据获取方式”，只是做了菜单可见性过滤

需求原话不是“菜单多一个标签就算完”，而是“根据菜单类别，来确定获取数据的方法”。现在的实现只做到了前半句，后半句基本没落地。

- 菜单类别目前只影响菜单树过滤：
  - [SysMenuServiceImpl.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java:83)
  - [SysMenuServiceImpl.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java:250)
- 真实的数据范围控制仍完全依赖 `DataScopeContext` / `DataScopeFilter`：
  - [DataScopeFilter.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/util/DataScopeFilter.java:13)
  - [DataScopeFilter.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/util/DataScopeFilter.java:30)
  - [DataScopeFilter.java](D:/qoder/assessment-backend/src/main/java/com/ccerphr/assessment/util/DataScopeFilter.java:51)

这意味着：

- 你们现在只是把“哪些菜单能分配/能显示”重新分类了；
- 但“进入某菜单后到底按单位查，还是按部门查，还是看全部”并没有建立和菜单类别之间的强绑定；
- 对于“系统/单位/部门都可能拥有的共享菜单”，系统仍然无法仅凭菜单类别统一决定数据策略，还是得靠当前职责上下文和各业务服务各自判断。

这与用户要求的“统一模型”还有明显距离。现在叫“菜单类别能力”，不能叫“基于菜单类别统一控制数据范围”。

建议：

- 至少建立“请求路径/菜单编码 -> 预期数据作用域策略”的服务端映射。
- 对共享菜单，明确是“按当前职责上下文决定范围”，还是“按菜单预设策略决定范围”，不要继续混着来。
- 业务接口层增加断言：菜单类别与当前职责类型、数据范围类型必须一致，不一致直接拒绝。

## 开放问题 / 风险假设

1. 当前工作区是脏树，存在大量与本次任务无关的改动；本报告只对菜单类别相关改造负责，不替其他未审文件背书。
2. `MenuManagement.vue` 和 `RoleManagement.vue` 中仍存在明显历史乱码，虽然不一定都是本次引入，但已经足够影响后续维护和误判排障。
3. 迁移脚本把菜单类别规则硬编码在 SQL 里，后续一旦菜单继续扩容，这套映射极易继续漂移。

## 验证情况

- `npx vue-tsc -b`：通过。
- `mvn -q -DskipTests compile`：未能进入代码级校验，当前环境的 Maven/JDK 先报 `无效的标记: --release`。
- `npm run build`：受本机 `esbuild` 拉起子进程 `spawn EPERM` 影响，未完成整包构建。

结论很直接：这批改造在设计上有方向，但实现质量不合格。现在不是“再抛光一下”的阶段，而是先把编译断点、需求映射错误、语义误导三件硬伤收掉，再谈上线。
