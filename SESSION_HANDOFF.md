# 启动交接说明

更新时间：2026-05-22

## 你现在最先该看什么

如果机器重启，或者重新开始本项目开发，请按这个顺序看：

1. `SESSION_HANDOFF.md`
2. `PROJECT_PROGRESS.md`
3. `progress.md`
4. `BUG_FIX_LOG.md`
5. `REQUIREMENT_CHANGE_LOG.md`
6. `CODE_REVIEW_REPORT.md`

## 当前做到哪了

- 当前阶段：业绩指标设定模块 BUG 修复 + 代码审核 + 业务流程时序图补建
- 最近刚完成：
  - **BUG-006 修复**：指标行内编辑保存全部 500 的根因定位与修复（`@Select` → `@Update`）
  - **CODE_REVIEW_REPORT.md**：全面代码审核，28 个问题（🔴×6 / 🟠×9 / 🟡×8 / 🟢×5）
  - **indicator-flow-diagram.html**：业绩指标设定 6 张业务流程时序图
- 当前主任务：重启后端，验证 BUG-006 修复
- 后续大任务：继续按 CODE_REVIEW_REPORT.md 推进质量修复与交付收口

## 当前最重要的下一步

1. **重启后端验证 BUG-006**：
   ```powershell
   cd D:\qoder\assessment-backend
   .\mvn17.cmd spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   以 `tuke01` 登录 → `/dept/indicator-set` → 设定目标 → 经营指标 / 标准成本 → 编辑序号 → 保存 → 预期"保存成功"

2. **按 CODE_REVIEW_REPORT.md 推进修复**：
   - 当天必修（止血）：SEC-001 ~ SEC-005（管理员后门、IDOR 越权、CORS/密钥泄露）
   - 一周清单：N+1 查询、前端安全加固、代码规范
   - 报告位置：`CODE_REVIEW_REPORT.md`

3. 每完成一个任务，立刻更新：
   - `PROJECT_PROGRESS.md`
   - `progress.md`
   - `BUG_FIX_LOG.md`，如果修了 Bug
   - `REQUIREMENT_CHANGE_LOG.md`，如果改了需求

4. 后续研发管理优先参考：
   - `CODE_REVIEW_REPORT.md`
   - `TASK_BOARD.md`
   - `RISK_REGISTER.md`
   - `DELIVERY_PLAN.md`

## 当前已知风险

- 后端运行依赖 JDK 17，本机 Maven 默认仍指向 JDK 8；运行 Maven 前必须使用 `mvn17.cmd`
- 仓库中存在未提交改动，注意不要误覆盖
- CODE_REVIEW_REPORT 中 SEC-001 ~ SEC-005 均为严重级别，未修复前有安全风险
- 代码与文档仍有部分中文乱码

## 启动命令

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

## 本轮文档管理约定

- 每次任务结束，必须写“已完成”和“下一个任务”。
- Bug 修复和需求变更必须分账记录。
- 总览文档用于快速查看，详细文档用于追溯。
