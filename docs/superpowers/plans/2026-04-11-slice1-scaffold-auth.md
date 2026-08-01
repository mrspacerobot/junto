# Slice 1 — Project Scaffold + Auth

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** User can sign up with invite code, verify via OTP, set display name, log out, generate and view invite codes.

**Architecture:** Feature-based Spring Boot 4 packages, Vue 3 SPA with generated API client. Passwordless auth via magic link + OTP.

**Approach:** TDD for all service and controller logic. Red → Green → Commit. Write test first, verify it fails, implement minimal code to pass, verify it passes, commit.

**Tech Stack:** Java 21, Spring Boot 4, Gradle (Groovy), PostgreSQL + PostGIS, Flyway, Vue 3, Vite, Tailwind, Pinia, Axios, Testcontainers

---

## Task Overview

| # | Task | Type | Key Files |
|---|------|------|-----------|
| 1 | Init monorepo + git | Scaffold | `.gitignore`, `README.md` |
| 2 | Docker Compose | Infra | `docker-compose.yml` |
| 3 | Gradle + Spring Boot init | Backend | `build.gradle`, `settings.gradle`, `JuntoApplication.java` |
| 4 | Application config | Backend | `application.yml`, `application-dev.yml`, `application-prod.yml` |
| 5 | Flyway: PostGIS + users + auth + invites + session | Backend | `V001`–`V005` migrations |
| 6 | Shared enums | Backend | `shared/enums/*.java` |
| 7 | Shared entities | Backend | `shared/entity/User.java`, `AuthToken.java`, `InviteCode.java`, `CustomFields.java` |
| 8 | Shared exceptions + handler | Backend | `shared/exception/*.java` |
| 9 | Shared pagination | Backend | `shared/pagination/CursorPage.java`, `CursorPageable.java` |
| 10 | Test infrastructure | Backend | `testFixtures/` base classes, config |
| 11 | Test fixtures (auth slice) | Backend | `UserFixture`, `AuthTokenFixture`, `InviteCodeFixture` |
| 12 | User repository + service | Backend | `user/UserRepository.java`, `UserService.java` |
| 13 | Email service | Backend | `email/EmailService.java` |
| 14 | Auth token repository + service | Backend | `auth/AuthTokenRepository.java`, `AuthService.java` |
| 15 | Invite service | Backend | `invite/InviteCodeRepository.java`, `InviteService.java` |
| 16 | Auth controller + DTOs | Backend | `auth/AuthController.java`, `auth/dto/*` |
| 17 | User controller + DTOs | Backend | `user/UserController.java`, `user/dto/*` |
| 18 | Invite controller + DTOs | Backend | `invite/InviteController.java`, `invite/dto/*` |
| 19 | Security config + CurrentUser | Backend | `auth/config/SecurityConfig.java`, `CurrentUser.java`, `CurrentUserArgumentResolver.java` |
| 20 | Springdoc OpenAPI | Backend | Springdoc config |
| 21 | Prod env validator | Backend | `shared/config/EnvironmentValidator.java` |
| 22 | Auth service unit tests | Backend test | `AuthServiceTest.java` |
| 23 | Invite service unit tests | Backend test | `InviteServiceTest.java` |
| 24 | Auth controller tests | Backend test | `AuthControllerTest.java` |
| 25 | Auth flow integration test | Backend test | `AuthFlowIntegrationTest.java` |
| 26 | Frontend scaffold | Frontend | `package.json`, `vite.config.ts`, `tailwind.config.js`, `tsconfig.json` |
| 27 | Frontend API client + error handling | Frontend | `api/client.ts`, `api/index.ts` |
| 28 | OpenAPI generation pipeline | Frontend | `package.json` scripts, `api/generated/` |
| 29 | Auth store + route guards | Frontend | `stores/auth.ts`, `router/index.ts` |
| 30 | App shell + layout | Frontend | `App.vue`, `AppShell.vue`, `NavBar.vue` |
| 31 | Login page | Frontend | `views/auth/LoginView.vue`, `components/auth/LoginForm.vue`, `OtpInput.vue` |
| 32 | Onboard page | Frontend | `views/auth/OnboardView.vue` |
| 33 | Auth store tests | Frontend test | `stores/auth.spec.ts` |
| 34 | CI pipeline | CI/CD | `.github/workflows/ci.yml` |
| 35 | Pre-push hooks | CI/CD | `.husky/pre-push`, `lint-staged` config |

---

## Task Dependencies

```
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9
                        ↓
               10 → 11 → 12 → 13 → 14 → 15 → 16 → 19 → 20 → 21
                                                       ↓
                                                  17, 18
                        ↓
               22, 23, 24, 25 (after tasks 12-18)

26 → 27 → 28 (needs backend running for first generation)
26 → 29 → 30 → 31 → 32
29 → 33
34, 35 (anytime after task 3 and 26)
```

Backend tasks (1-25) can be done first, then frontend (26-33), then CI (34-35).
Or backend and frontend scaffold in parallel (tasks 1-9 backend + task 26 frontend simultaneously).

---

## Key Implementation Notes

### Task 5 — Flyway migrations
- `V001__enable_postgis.sql`: `CREATE EXTENSION IF NOT EXISTS postgis;`
- `V002__create_users.sql`: Full users table per spec (UUID PK, email unique, display_name not null, role/status enums as text, timestamps)
- `V003__create_auth_tokens.sql`: token (UUID unique), code (6-char text), expires_at, used_at
- `V004__create_invite_codes.sql`: code format JUNTO-XXXXXX, created_by FK, used_by FK nullable
- `V005__create_spring_session_tables.sql`: Copy from Spring Session JDBC schema for PostgreSQL

### Task 14 — AuthService core logic
- `requestCode(email, inviteCode?)`: Check if email exists → if new, require invite code → validate it → generate token + code → save auth_token → send email → return success
- `verifyCode(email, code)`: Find unused token by email+code → check expiry → if new user: create user + consume invite → create session → mark token used
- `verifyToken(token)`: Same flow but via magic link URL param → redirect to frontend

### Task 19 — Security config
- Spring Security filter chain: permit `/api/auth/**`, authenticate everything else
- Spring Session JDBC with `initialize-schema: never`
- CORS: read allowed origins from config, `allowCredentials(true)`
- CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()`
- CurrentUser resolver: reads user from SecurityContext, loads from UserRepository

### Task 29 — Auth store + guards
- `useAuthStore()`: `currentUser`, `isAuthenticated`, `onboarded`, `initialized`
- `initialize()`: calls `GET /api/auth/me`, sets state
- `login()`: calls request-code then verify
- `logout()`: calls `POST /api/auth/logout`, clears state
- Route guard: `beforeEach` checks auth state, redirects as needed
