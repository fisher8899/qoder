# 紧急待办事项（P2）

以下问题本轮不进入修复提交，但需要尽快排期处理。当前结论按已确认业务规则与代码现状整理，不按脏数据反推规则。

## 1. 考核进度接口返回占位假数据

- 位置：
  - `assessment-backend/src/main/java/com/ccerphr/assessment/service/impl/BizExamGroupServiceImpl.java`
- 问题：
  - `getProgress()` 中部分状态字段为硬编码文案，不是基于真实业务表聚合。
  - 存在无意义的全表计数代码，返回结果可信度不足。
- 风险：
  - 页面展示“看起来正常”，实则误导测试与业务判断。
- 建议：
  - 按真实流程表重算状态。
  - 未实现的状态字段先下线或明确返回“未实现”。

## 2. 指标设定页面考核组过滤条件不一致

- 位置：
  - `assessment-frontend/src/views/dept/IndicatorSet.vue`
- 问题：
  - 主列表按 `INDICATOR_SET` 过滤考核组。
  - 弹框内重新加载考核组时未保持同一业务过滤条件。
- 风险：
  - 指标设定场景可能混入非本业务类型考核组。
- 建议：
  - 页面内所有考核组来源统一复用同一过滤逻辑。

## 3. 职责切换状态仍依赖前端本地缓存

- 位置：
  - `assessment-frontend/src/stores/user.ts`
  - `assessment-frontend/src/layouts/MainLayout.vue`
- 问题：
  - 当前职责选择长期保存在 localStorage。
  - 页面恢复逻辑仍有本地状态主导痕迹。
- 风险：
  - 刷新、切用户、清缓存、跨标签页时仍可能出现状态不一致。
- 建议：
  - 后续引入显式“切换当前职责”后端接口，返回当前职责快照。

## 4. 配置文件重复

- 位置：
  - `assessment-backend/src/main/resources/application.yml`
  - `assessment-backend/src/main/resources/application.properties`
- 问题：
  - 两份配置同时存在，容易产生环境漂移。
- 风险：
  - 修改时只改一份，导致实际运行配置不可预期。
- 建议：
  - 收敛为单一配置格式，环境差异通过 profile 管理。

## 5. 中文乱码与编码污染

- 位置：
  - 后端控制器、异常类、前端路由与布局文件等多处。
- 问题：
  - 注释、菜单、提示语、日志文本存在乱码。
- 风险：
  - 维护成本高，用户提示不可读，排障信息失真。
- 建议：
  - 全仓统一 UTF-8。
  - 优先清理用户可见文本、异常消息与日志文案。

## 6. 权限模型仍有存量控制器未收口

- 位置：
  - 多个后端 controller 的 `@RequireRole(...)`
  - 多个前端路由 `meta.roles`
- 问题：
  - 本轮优先修 P0/P1 主链路，不会一次性覆盖所有控制器和页面。
- 风险：
  - 其他功能仍可能存在“规则已变更，但实现仍写死角色编码”的遗留问题。
- 建议：
  - 后续做一次全仓扫描，按“能力点 + 职责类型 + 数据范围”统一整改。
