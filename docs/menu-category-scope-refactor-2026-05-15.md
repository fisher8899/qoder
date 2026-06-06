# 菜单类别与数据范围统一模型改造归档

日期：2026-05-15  
状态：已完成并通过构建验证  
维护人：Codex

## 1. 背景

原系统已有职责类型 `role_type` 和用户数据范围 `data_scope/scope_id`，但功能菜单没有统一的类别属性。职责定义时可选菜单与数据范围控制之间没有形成稳定模型，容易出现“职责能看到不该看到的菜单”或“菜单能打开但数据范围不匹配”的维护风险。

本次按业务要求改为：通过菜单类别统一决定职责可配置菜单，并由职责类型进一步约束数据范围。

## 2. 模型结论

该模型可落地，但必须满足两个约束：

1. 菜单类别不能只在前端过滤，后端保存职责菜单时必须再次校验。
2. 数据范围不能信任前端传值，后端必须按职责类型强制归一。

已按以上约束完成实现。

## 3. 菜单类别定义

| 类别 | 编码 | 含义 | 数据范围 |
|---|---|---|---|
| 全部/系统 | `SYSTEM` | 系统管理员使用，默认可配置全部菜单 | `ALL` |
| 单位类 | `UNIT` | 单位管理员使用 | `UNIT` |
| 部门类 | `DEPT` | 部门管理员、部门负责人、分管领导使用 | `ORG` |

`menu_category` 支持逗号分隔多选，例如 `SYSTEM,UNIT`。

## 4. 本次菜单归类

### 系统管理员专属

- 功能/菜单定义
- 职责定义
- 单位管理
- 数据同步

### 单位类菜单

- 系统用户管理
- 权限分配管理
- 分管领导维护
- 考核组织管理
- 人员管理
- 指标大类管理
- 考核组管理
- 业绩指标审批
- 月度考核管理
- 复核评估
- 申诉管理
- 指标设定进度查询
- 考核进度查询
- 考核结果查询

### 部门类菜单

- 指标审批
- 评估打分
- 业绩指标设定
- 月度考核自评
- 部门他评打分
- 申诉重新评估
- 申诉反馈
- 部门考核结果查询
- 分管领导进度查询
- 历史考核查询

## 5. 实现方案

### 后端

- `sys_menu` 增加 `menu_category` 字段。
- 新增 Flyway 迁移 `V3__menu_category_scope.sql`：
  - 添加 `menu_category`
  - 回填存量菜单类别
  - 补齐缺失菜单：系统用户管理、人员管理、指标设定进度查询
  - 调整部分菜单排序
- `SysMenuServiceImpl`：
  - 补回菜单服务实现
  - 当前用户菜单按职责有效菜单返回
  - 职责分配菜单支持按 `roleType` 过滤可选树
  - 菜单新增/编辑时规范化类别值
- `SysRoleServiceImpl`：
  - 保存职责菜单时后端校验菜单类别
  - 系统职责可保存全部菜单
  - 单位职责只能保存单位类菜单
  - 部门职责只能保存部门类菜单
- `SysUserPermissionController`：
  - 新增/编辑用户权限时按职责类型强制数据范围：
    - `SYSTEM -> ALL`
    - `UNIT -> UNIT`
    - `DEPT -> ORG`
  - 管理权限分配接口要求当前用户为系统管理员或单位范围职责。

### 前端

- 功能/菜单定义页增加“菜单类别”多选。
- 菜单树展示类别标签。
- 职责分配菜单弹窗按当前职责类型加载可选菜单。
- 主布局加载菜单改为调用 `/menu/current`，避免仅凭前端角色码取菜单。

## 6. 涉及文件

### 后端

- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysMenuServiceImpl.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/SysMenuService.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysMenuController.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/SysRoleServiceImpl.java`
- `assessment-backend/src/main/java/com/ccerphr/assessment/controller/SysUserPermissionController.java`
- `assessment-backend/src/main/resources/db/schema.sql`
- `assessment-backend/src/main/resources/db/data.sql`
- `assessment-backend/src/main/resources/db/migration/V3__menu_category_scope.sql`

### 前端

- `assessment-frontend/src/api/admin.ts`
- `assessment-frontend/src/layouts/MainLayout.vue`
- `assessment-frontend/src/views/admin/MenuManagement.vue`
- `assessment-frontend/src/views/admin/RoleManagement.vue`

## 7. 验证结果

### 后端

命令：

```powershell
cd D:\qoder\assessment-backend
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -q -DskipTests package
```

结果：通过。

注意：本机 `mvn -version` 默认仍指向 JDK 8，直接执行 Maven 会报 `无效的标记: --release`。必须先切换 `JAVA_HOME` 到 JDK 17。

### 前端

命令：

```powershell
cd D:\qoder\assessment-frontend
npm run build
```

结果：通过。

遗留警告：Sass legacy API / `@import` deprecation、部分 chunk 体积超过 500KB，非本次阻断项。

## 8. 当前剩余事项

1. 在真实数据库上启动后确认 Flyway `V3__menu_category_scope.sql` 已执行成功。
2. 用系统管理员进入“功能/菜单定义”，检查菜单类别回填是否符合预期。
3. 用系统管理员进入“职责定义”：
   - 单位职责只能看到单位类菜单
   - 部门职责只能看到部门类菜单
   - 系统职责能看到全部菜单
4. 做一轮角色菜单与数据范围手工回归：
   - `ADMIN`
   - `FIN_ADMIN`
   - `DEPT_ADMIN`
   - `DEPT_LEADER`
   - `SUPERVISOR`
5. 若目标库已有旧职责菜单配置，重点检查旧配置中是否存在类别不匹配菜单。

## 9. 重启后从哪里继续

重启后先看：

1. `SESSION_HANDOFF.md`
2. `PROJECT_PROGRESS.md`
3. `progress.md`
4. 本文档：`docs/menu-category-scope-refactor-2026-05-15.md`

下一步建议：

1. 启动后端，确认迁移执行。
2. 启动前端，按角色做菜单可见性和数据范围回归。
3. 回归通过后，继续推进权限模型剩余专项和发布前测试收口。
