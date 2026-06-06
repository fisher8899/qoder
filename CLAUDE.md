# CLAUDE.md

This file provides guidance for working in this repository.

## 语言规则

- 所有思考过程必须使用中文。
- 所有对话回复必须使用中文。
- 代码注释使用中文（除非有特殊原因使用英文）。

## Project Overview

Monthly Performance Assessment Management System.

Main applications:

- `assessment-backend/` - Spring Boot backend
- `assessment-frontend/` - Vue 3 frontend

## Project Management Documents

- `SESSION_HANDOFF.md`
  - First document to read after restarting the machine or reopening the project.
  - Shows current status, next task, startup commands, and current risks.

- `PROJECT_PROGRESS.md`
  - Project dashboard.
  - Shows module progress, current task, next task, todo list, and risks.

- `progress.md`
  - Task-level running progress log.
  - Must record what was just finished and what should be done next.

- `BUG_FIX_LOG.md`
  - Bug fix ledger.
  - Records bug symptoms, root causes, fixes, touched files, and validation.

- `REQUIREMENT_CHANGE_LOG.md`
  - Requirement change ledger.
  - Records new requirements and adjusted requirements, plus implementation details.

- `CHANGELOG.md`
  - Stage-level or version-level summary of changes.

- `docs/project-management.md`
  - Project documentation maintenance rules.

## Required Workflow

1. Start each session by reading:
   - `SESSION_HANDOFF.md`
   - `PROJECT_PROGRESS.md`
   - `progress.md`

2. After finishing any task:
   - update `progress.md`
   - update `PROJECT_PROGRESS.md`
   - update `BUG_FIX_LOG.md` if a bug was fixed
   - update `REQUIREMENT_CHANGE_LOG.md` if a requirement was added or changed

3. Keep “completed work” and “next task” explicit in the documents.

## Build and Run

### Backend

```powershell
cd D:\qoder\assessment-backend
.\mvn17.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

### Frontend

```powershell
cd D:\qoder\assessment-frontend
npm run dev -- --host 0.0.0.0
```

## Notes

- Frontend proxies `/api` to backend `http://localhost:8080`.
- Backend requires JDK 17 to run reliably. Use `assessment-backend\mvn17.cmd` for Maven commands.
- Backend runtime config is profile-based: use `dev` for local work and `prod` with explicit env vars for deployment.
- The workspace may contain uncommitted changes. Do not overwrite unrelated work.
