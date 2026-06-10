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

**Demo credentials (dev only):** `demo@reforma.local` / `Demo1234!`

## Architecture

```
Angular (4200) → Spring Boot (8080) → PostgreSQL
                       ↓
                 FastAPI (8081) → scikit-learn models
```

### Multi-Tenancy
Every resource belongs to a farm (`id_granja`). `GranjaAccesoService` enforces ownership/access before any query. There is no cross-farm data leakage by design.

### Plan-Gating
Subscription limits (farms, raw materials, formulas, etc.) are enforced via `PlanService.obtenerPlanEfectivo()`. **Never** read `planSuscripcion` directly from an entity — always go through this service, which accounts for future employee plan inheritance.

### Inventory & Pricing
Inventory uses weighted-average pricing (`t_inventario`). The inventory is recalculated automatically when a purchase (`t_compra_cabecera`) is saved. Always call `saveAndFlush(cabecera)` before triggering price/inventory hooks to avoid stale reads.

### Backend Pattern
Controllers → Services → Repositories (3-tier). DTOs are Java records (immutable). No ID is set on create (IDENTITY/SEQUENCE generation). `GlobalExceptionHandler` handles 400/404/409 centrally.

### Frontend Pattern
Standalone Angular 19 components. Auth state uses signals stored in `localStorage['reforma_jwt']`. RxJS for async data flows. API calls go through `reforma-api.service.ts`.

## Database Conventions

- Tables: `t_` prefix, `snake_case` columns
- IDs: `BIGINT` auto-increment for catalog entities; UUIDs for users/farms (migration pending)
- **Soft deletes only**: set `activo = false`, never `DELETE`
- Reusing an inactivated code → insert a new row, never reactivate the old one
- Snapshots: `t_compra_detalle`, `t_formula_detalle`, `t_fabricacion_detalle` store price/formula at the time of creation to preserve immutable history
- Optimistic locking on `t_inventario.version` (retry on `409 Conflict`)
- Schema migrations: Flyway (`V001`–`V009` in `services/api-domain/src/main/resources/db/migration/`)

## Key Files

| File | Purpose |
|------|---------|
| `CONTEXTO_AGENTE_IA.md` | Current implementation status, conventions, pending work — **read before starting any session** |
| `docs/REGISTRO_CAMBIOS.md` | Dated change log — **update at end of every development session** |
| `docs/adr/` | Architecture Decision Records (ADR-0001 through ADR-0006) |
| `services/api-domain/src/main/java/com/reforma/domain/config/SecurityConfig.java` | JWT security, CORS config |
| `apps/web/src/app/app.routes.ts` | Frontend routing |
| `apps/web/src/app/data/api/reforma-api.service.ts` | Centralized HTTP client |

## Session Conventions

1. All code changes must live inside `DESARROLLO/`.
2. After each session, add a dated entry to `docs/REGISTRO_CAMBIOS.md`.
3. External documentation (specs, diagrams) is in `../DOCUMENTACION CURSOR/` (outside `DESARROLLO/`).
