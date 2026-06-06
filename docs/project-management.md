# 项目管理规范

最后更新：2026-05-14

## 1. 文档职责划分

项目管理文档统一按下面分工维护：

- `SESSION_HANDOFF.md`
  - 用途：机器重启或重新进入项目后的第一入口
  - 内容：当前做到哪、下一个任务、启动方式、当前风险

- `PROJECT_PROGRESS.md`
  - 用途：项目总览看板
  - 内容：模块进度、当前任务、下一个任务、待办和风险

- `progress.md`
  - 用途：任务级进度流水记录
  - 内容：本次刚完成什么、下一个任务是什么、阻塞和风险

- `BUG_FIX_LOG.md`
  - 用途：Bug 修复台账
  - 内容：问题现象、根因、方案、涉及文件、验证方式

- `REQUIREMENT_CHANGE_LOG.md`
  - 用途：需求变更台账
  - 内容：原需求、新需求、调整原因、实现方案、验证方式

- `CHANGELOG.md`
  - 用途：版本级变更摘要
  - 内容：适合作为版本发布或阶段变更摘要，不替代台账

## 2. 每次任务完成后的强制动作

每次完成开发任务后，必须执行：

1. 更新 `progress.md`
   - 写明本次完成的任务
   - 写明下一个任务
   - 写明当前阻塞或风险

2. 更新 `PROJECT_PROGRESS.md`
   - 更新当前总览状态
   - 更新待办列表
   - 更新“今天刚完成什么”和“下一个任务是什么”

3. 如果本次修复了 Bug，更新 `BUG_FIX_LOG.md`
   - 必须记录根因、修复方案、涉及文件、验证方式

4. 如果本次新增或调整了需求，更新 `REQUIREMENT_CHANGE_LOG.md`
   - 必须记录原需求、变更后要求、实现方案、验证方式

5. 如果属于阶段性成果或版本性调整，补充 `CHANGELOG.md`

## 3. 记录规则

### 3.1 Bug 记录规则

以下情况必须记入 `BUG_FIX_LOG.md`：

- 构建失败修复
- 页面显示错误修复
- 接口逻辑错误修复
- 数据错误修复
- 启动和环境问题修复

每条 Bug 至少要写：

- 日期
- 状态
- 优先级
- 问题现象
- 根因分析
- 修复方案
- 涉及文件
- 验证方式

### 3.2 需求记录规则

以下情况必须记入 `REQUIREMENT_CHANGE_LOG.md`：

- 新增业务功能
- 页面行为调整
- 权限或流程要求变化
- 文档管理类配套需求

每条需求至少要写：

- 日期
- 类型
- 背景
- 原需求
- 新需求或调整后要求
- 实现方案
- 涉及文件
- 验证方式

## 4. 任务结束检查清单

每次任务结束前，检查：

- [ ] 代码任务已完成
- [ ] `progress.md` 已更新
- [ ] `PROJECT_PROGRESS.md` 已更新
- [ ] 如果修了 Bug，`BUG_FIX_LOG.md` 已更新
- [ ] 如果改了需求，`REQUIREMENT_CHANGE_LOG.md` 已更新
- [ ] 如果是阶段性成果，`CHANGELOG.md` 已更新
- [ ] 已明确写出“下一个任务是什么”

## 5. 重启后的接手流程

重启或重新进入项目后，按下面顺序处理：

1. 看 `SESSION_HANDOFF.md`
2. 看 `PROJECT_PROGRESS.md`
3. 看 `progress.md`
4. 如需追溯问题，查 `BUG_FIX_LOG.md`
5. 如需追溯业务变化，查 `REQUIREMENT_CHANGE_LOG.md`

## 6. 启动命令参考

### 后端

```powershell
cd D:\qoder\assessment-backend
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

### 前端

```powershell
cd D:\qoder\assessment-frontend
npm run dev -- --host 0.0.0.0
```

## 7. 当前管理原则

- 文档不是可选项，是开发流程的一部分。
- 总览文档负责“快速知道现在在哪”。
- 台账文档负责“以后能追溯为什么这么改”。
- 每次任务结束必须写“已完成”和“下一个任务”。
