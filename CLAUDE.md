# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**REFORMA** is a multi-tenant SaaS ERP platform for pig farm management (granjas porcinas). It handles purchase orders, feed formula management, inventory with weighted-average pricing, and ML-based price anomaly detection.

**Author/Student:** Valentino Segatti — Legajo SOF01992 (Universidad Siglo 21, Proyecto Final)

## Stack

| Service | Tech | Port |
|---------|------|------|
| `apps/web` | Angular 19 (standalone, signals) | 4200 |
| `services/api-domain` | Spring Boot 3.4 / Java 21 | 8080 |
| `services/api-ml` | FastAPI / Python 3.12 | 8081 |
| Database | PostgreSQL 16 | 5432 |
| Cache | Redis 7 | 6379 |

## Commands

All development commands run from `DESARROLLO/` via the root `Makefile`:

```bash
make dev              # Start full stack (docker compose)
make down             # Stop stack
make logs             # Tail all service logs
make ps               # Show container status

make test             # All tests
make test-domain      # Maven unit tests only
make test-domain-it   # Integration tests (Testcontainers)
make test-ml          # Python pytest

make migrate          # Run Flyway + Alembic migrations
make seed             # Load demo data
make reset-db         # DROP + migrate + seed (destructive)
```

**Demo credentials (dev only):** the **single hardcoded account is `demo@reforma.local` / `Demo1234!`** — BUSINESS, id `u_demo`, with farm `g_demo`. Seeded by both `scripts/seed.sh` and `DevDataLoader` (idempotent: skips if `u_demo`/that email exists). Every other account must be created via the registration form (born DEMO). `make reset-db` restores this single seed.

## Local environment & build gotchas (READ THIS)

This Windows dev box has **no local Maven**, and local Java is 25 while the project targets 21. The frontend `node_modules` was installed for **Linux** (esbuild native binary), so `ng build`/`npm` fail locally on Windows. Therefore:

- **Run backend tests in a throwaway Maven container** (don't try `mvn` locally). A working recipe: a temporary `Dockerfile.test` that mirrors the prod Dockerfile up to `dependency:go-offline` (so the cached layer is reused) and then runs `mvn test`. Default `mvn test` runs unit tests only; `*IntegracionIT` (Testcontainers) need the `it` profile.
- **Build the frontend via Docker** (`docker compose build web`), which runs `npm ci` + `ng build` on Linux. A successful image build == the Angular app compiles.
- **After ANY backend code change, rebuild the image — `docker compose up -d --build api-domain`.** A plain `docker compose up -d api-domain` (recreate) **reuses the stale image** and your changes won't be live. This is the #1 trap here; symptoms look like "my new bean/config isn't taking effect".
- **bcrypt:** the password encoder is plain `BCryptPasswordEncoder(12)` (no `{bcrypt}` prefix). To hand-set a password in the DB, generate a `$2a$`/`$2b$` hash with Python's `bcrypt` (htpasswd's `$2y$` is unreliable here) and write it with a **parameterized** query (a Python container on `desarrollo_default` with `psycopg2`) — embedding a `$2…$` hash through shell+`psql -c` corrupts it.

## Architecture

```
Angular (4200) → Spring Boot (8080) → PostgreSQL
                       ↓
                 FastAPI (8081) → scikit-learn models
```

### Multi-Tenancy
Every resource belongs to a farm (`id_granja`). `GranjaAccesoService` enforces ownership/access before any query. There is no cross-farm data leakage by design.

### Plan-Gating
Subscription limits (farms, raw materials, formulas, etc.) are enforced via `PlanService.obtenerPlanEfectivo()`. **Never** read `planSuscripcion` directly from an entity — always go through this service, which accounts for future employee plan inheritance. DEMO is a usable trial: **2 farms + 2 employees** (a trial account can walk the full team/multi-tenant flow without upgrading); kept ≥ STARTER so the paid tier is never below the trial.

### DEMO account retention (auto-purge)
A DEMO account is **fully purged** `reforma.demo.retencion-dias` days (default 60) after its `fecha_registro`: farms + all granja-scoped data, ML price history, audit rows, security tokens, and the owner + employee user rows. Daily `@Scheduled` job `LimpiezaCuentasDemoService` (each tenant in its own tx; failures isolated); ordered native deletes in `PurgaCuentaDemoRepository` (delete `t_ia_anomalia_precio` and `t_auditoria` before the granja cascade, employees before the owner — see class javadoc for the FK reasoning). Config: `DEMO_PURGA_HABILITADA`, `DEMO_RETENCION_DIAS`, `DEMO_PURGA_CRON`, `DEMO_EMAILS_EXENTOS` (default exempts `demo@reforma.local`; it's BUSINESS so not targeted anyway). Enabled by `@EnableScheduling` on `ReformaApplication`.

### Inventory & Pricing
Inventory uses weighted-average pricing (`t_inventario`). The inventory is recalculated automatically when a purchase (`t_compra_cabecera`) is saved. Always call `saveAndFlush(cabecera)` before triggering price/inventory hooks to avoid stale reads.

### Backend Pattern
Controllers → Services → Repositories (3-tier). DTOs are Java records (immutable). No ID is set on create (IDENTITY/SEQUENCE generation). `GlobalExceptionHandler` handles 400/404/409 centrally.

### Frontend Pattern
Standalone Angular 19 components. Auth state uses signals stored in `localStorage['reforma_jwt']`. RxJS for async data flows. API calls go through `reforma-api.service.ts`.

### Auth, Roles & Sessions (módulo Usuarios, Etapas 0–5)
- **JWT** carries `email`, `tipoUsuario`, `planSuscripcion`, `emailVerificado`, `esEmpleado`, `rolEmpleado`, `idDueno`, and `tv` (token version). Authorities: `ROLE_OWNER` for owners; `ROLE_ADMIN/EDITOR/LECTOR` for employees.
- **Session revocation (`tv`):** `TokenJwtServicio.validarToken` rejects (401) any JWT whose `tv` claim ≠ `t_usuarios.token_version`. Call `usuario.revocarSesiones()` to invalidate in-flight tokens — already wired on **deactivate**, **role change** (`EmpleadoService`) and **password reset** (`RecuperacionCuentaService`). Tokens with no `tv` are treated as version 0.
- **Email:** `EmailNotificacionService` has `log` (default; writes the link to the log) and `smtp` impls, selected by `reforma.email.mode` (`EMAIL_MODE` env). SMTP config (`spring.mail.*` ← `SMTP_*` env) is in `application.yml`; `docker-compose.yml` passes `EMAIL_MODE`/`SMTP_*`/`EMAIL_FROM` to `api-domain`. Real values live in the git-ignored root `.env`. **Gmail needs an App Password with no spaces** (`JavaMailSender` doesn't strip them).
- **Audit:** every sensitive op writes `t_auditoria` (`AuditoriaService`). Read it via `GET /api/auditoria` (OWNER/ADMIN, tenant-scoped, filterable + paged) — `AuditoriaConsultaService`; frontend `features/auditoria`. **Failed logins are NOT audited** (by product decision); the `LOGIN_FALLIDO` enum value is kept for historical data only.
- **Profile:** `GET /api/usuarios/perfil` (authenticated) → `PerfilResponse` (id, email, nombre, apellido, `rol` = OWNER/ADMIN/EDITOR/LECTOR, `esEmpleado`, `plan`, `idDueno`, `permisos` = human-readable labels derived from the role) — `PerfilService`; frontend `features/perfil` (route `/perfil`, link in "Mis granjas"). The JWT does NOT carry nombre/apellido, so the UI reads them from this endpoint.

## Database Conventions

- Tables: `t_` prefix, `snake_case` columns
- IDs: `BIGINT` auto-increment for catalog entities; UUIDs for users/farms (migration pending)
- **Soft deletes only**: set `activo = false`, never `DELETE`
- Reusing an inactivated code → insert a new row, never reactivate the old one
- Snapshots: `t_compra_detalle`, `t_formula_detalle`, `t_fabricacion_detalle` store price/formula at the time of creation to preserve immutable history
- Optimistic locking on `t_inventario.version` (retry on `409 Conflict`)
- Schema migrations: Flyway (`V001`–`V011` in `services/api-domain/src/main/resources/db/migration/`). `V010` = `t_token_seguridad` (hashed one-time tokens); `V011` = `t_usuarios.token_version` (session revocation)

## Key Files

| File | Purpose |
|------|---------|
| `CONTEXTO_AGENTE_IA.md` | Current implementation status, conventions, pending work — **read before starting any session** |
| `docs/REGISTRO_CAMBIOS.md` | Dated change log — **update at end of every development session** |
| `docs/MODULO_USUARIOS.md` | Staged plan + status of the Usuarios module (auth, employees, roles, sessions, audit) |
| `docs/GUIA_PRUEBAS_MANUALES_USUARIOS.md` | Manual end-to-end test script for the Usuarios module (Etapas 0–5) |
| `docs/adr/` | Architecture Decision Records (ADR-0001 through ADR-0006) |
| `services/api-domain/src/main/java/com/reforma/domain/config/SecurityConfig.java` | JWT security, CORS config |
| `apps/web/src/app/app.routes.ts` | Frontend routing |
| `apps/web/src/app/data/api/reforma-api.service.ts` | Centralized HTTP client |

## Session Conventions

1. All code changes must live inside `DESARROLLO/`.
2. After each session, add a dated entry to `docs/REGISTRO_CAMBIOS.md`.
3. External documentation (specs, diagrams) is in `../DOCUMENTACION CURSOR/` (outside `DESARROLLO/`).

## Roadmap & próximos pasos (recomendación)

Orden recomendado de trabajo, especialmente **después de correr la guía de pruebas manuales** (`docs/GUIA_PRUEBAS_MANUALES_USUARIOS.md`):

1. **Registrar resultados de las pruebas.** Completar el checklist de la guía; ante un fallo anotar paso, esperado vs. real y lo que aparezca en `make logs`. Corregir antes de avanzar.
2. **🔴 Commitear + pushear el backlog (lo más urgente).** Verificar con `git status` / `git log --oneline origin/main`: a 2026-06-17 `origin/main` estaba en el commit "cierre Etapa 1" y **las Etapas 2–5 del módulo Usuarios** (empleados, matriz de roles, multi-tenancy efectiva, revocación de sesión, consola de auditoría) + SMTP real vivían **sin commitear** en el working tree. Subir ese trabajo en commits lógicos (ver `CONTEXTO_AGENTE_IA.md` §11 para el agrupamiento sugerido) antes de empezar algo nuevo. Ya está validado (suite verde, `ng build`, smoke en vivo).
3. **Siguiente módulo funcional** (el módulo Usuarios queda completo en Etapas 0–5):
   - **Fabricaciones (RF-FAB)** — cierra el núcleo del ERP: descontar el consumo de `cantidad_sistema` del inventario. Hay base (`V009` + controller); falta la lógica. *Recomendado como próximo paso funcional.*
   - **`api-ml` (IA)** — consumir `t_registro_precio` para predicción y **detección de anomalías de precio**, con proxy desde Spring. Suele ser el diferenciador del Proyecto Final.
   - **Endurecimiento de Usuarios** (rápido, opcional): rate-limiting/lockout en Redis, cachear `token_version` en Redis, `X-Forwarded-For` en nginx, `/security-review` del diff.
   - Otros: detalle de proveedor con historial de precios (RF-PROV-003), suscripciones Mercado Pago, reportes/gráficos.
