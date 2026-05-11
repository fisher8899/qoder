# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Monthly Performance Assessment Management System (月度业绩考核管理系统) - a full-stack enterprise application for managing employee performance evaluations.

## Build & Run Commands

### Backend (assessment-backend/)
```bash
cd assessment-backend
mvn spring-boot:run          # Run development server on port 8080
mvn clean package            # Build JAR
mvn test                     # Run tests
```

### Frontend (assessment-frontend/)
```bash
cd assessment-frontend
npm run dev                  # Run development server on port 3000
npm run build                # Build for production
npm run preview              # Preview production build
```

## Architecture

### Monorepo Structure
- `assessment-backend/` - Spring Boot 3.2.5 REST API (Java 17)
- `assessment-frontend/` - Vue 3 + TypeScript SPA (Vite)
- `月度业绩考核原型设计初稿/` - UI prototypes and design docs

### Backend Stack
- **Framework**: Spring Boot 3.2.5 with Spring Security
- **ORM**: MyBatis Plus 3.5.5 with MySQL
- **Auth**: JWT tokens (jjwt 0.12.5), BCrypt password hashing
- **API Docs**: SpringDoc OpenAPI at `/swagger-ui.html` and `/api-docs`
- **Utilities**: Apache POI (Excel), Pinyin4j (Chinese conversion)

### Frontend Stack
- **Framework**: Vue 3 with `<script setup>` SFCs
- **State**: Pinia stores (`stores/user.ts`, `stores/app.ts`)
- **UI**: Element Plus 2.x with SCSS theming
- **HTTP**: Axios with interceptors (`api/request.ts`)
- **Routing**: Vue Router with role-based guards

### API Convention
All responses wrapped in `Result<T>`:
```java
{ code: 200, message: "操作成功", data: T }
```
Frontend axios interceptor throws on `code !== 200`.

### Authentication Flow
1. POST `/api/auth/login` returns JWT token + user info
2. Token stored in localStorage, sent as `Authorization: Bearer <token>`
3. Additional headers for data scope: `X-Role-Code`, `X-Data-Scope`, `X-Scope-Id`

### Role-Based Access Control

**Roles** (stored in `SysUser.roleCode`, `SysUserRole`, `SysUserPermission`):
- `ADMIN` - System administrator (full access)
- `FIN_ADMIN` - Performance assessment admin (plan/finance dept)
- `DEPT_ADMIN` - Department performance admin
- `DEPT_LEADER` - Department head
- `SUPERVISOR` - Supervisor/分管领导

**Data Scopes** (`SysUserPermission.dataScope`):
- `ALL` - All data
- `UNIT` - Limited to specific unit
- `ORG` - Limited to specific organization

### Key Business Entities

**System Tables** (Sys* prefix):
- `SysUser`, `SysEmployee` - Users and employees
- `SysOrganization`, `SysUnit` - Org structure
- `SysRole`, `SysRoleMenu`, `SysUserPermission` - Permissions
- `SysMenu`, `SysDict`, `SysIndicatorCategory` - Reference data

**Business Tables** (Biz* prefix):
- `BizExamGroup`, `BizExamGroupMember` - Assessment groups
- `BizIndicatorDefinition` - Performance indicators
- `BizSelfEvaluation`, `BizPeerEvaluation` - Self/peer reviews
- `BizReviewScore`, `BizMonthlyScore` - Scores
- `BizAppeal`, `BizAppealAttachment` - Appeals process

### Frontend Route Structure
Routes organized by role (`router/index.ts`):
- `/admin/*` - ADMIN, FIN_ADMIN routes
- `/exam/*` - FIN_ADMIN, DEPT_ADMIN routes
- `/dept/*` - DEPT_ADMIN routes
- `/leader/*` - DEPT_LEADER routes
- `/supervisor/*` - SUPERVISOR routes

Route guard checks `meta.roles` array and `userStore.allowedPaths`.

### Development Notes

- Frontend proxies `/api/*` to backend at `localhost:8080` (see `vite.config.ts`)
- Backend MySQL connection: `localhost:3306/assessment_db` (see `application.yml`)
- File uploads stored in `./uploads` directory
- JWT secret and expiration configured in `app.jwt.*` properties
