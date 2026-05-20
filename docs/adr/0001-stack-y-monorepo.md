# ADR 0001 — Stack y monorepo

## Estado

Aceptado

## Contexto

Reimplementación de REFORMA según `ESPECIFICACION_TECNICA_REIMPLEMENTACION.md`.

## Decisión

- Monorepo con `services/api-domain` (Spring Boot 3, Java 21), `services/api-ml` (FastAPI), `apps/web` (Angular 18+).
- PostgreSQL 16 único motor; Flyway en dominio, Alembic en schema `ml`.
- Docker Compose para desarrollo local.

## Consecuencias

- Contratos API versionados en `docs/api/` (pendiente OpenAPI generado).
- CI independiente por servicio en `.github/workflows/`.
