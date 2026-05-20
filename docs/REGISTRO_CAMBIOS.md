# Registro de cambios — DESARROLLO REFORMA

> Actualizar este archivo al final de **cada sesión de desarrollo** o cuando se mergee una feature relevante.  
> El contexto vivo del proyecto está en [`../CONTEXTO_AGENTE_IA.md`](../CONTEXTO_AGENTE_IA.md).

Formato sugerido por entrada:

```markdown
### YYYY-MM-DD — Título breve
- **Autor/agente:** …
- **Qué:** …
- **Archivos principales:** …
- **Pendiente:** …
```

---

## Entradas

### 2026-05-19 — Sprint 2 #1: módulo Materias Primas

- **Qué:** Primer recurso de dominio funcional end-to-end (RF-MP-001 a RF-MP-003).
- **Backend (`api-domain`):**
  - Entidad `MateriaPrima` + repo con `findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc`, `countByGranjaIdAndActivaTrue`, unique `(idGranja, codigoMateriaPrima)`.
  - DTOs `MateriaPrimaRequest` / `MateriaPrimaResponse` con validación Jakarta.
  - `PlanService.limiteMateriasPrimas`: DEMO 10 / STARTER 30 / BUSINESS 100 / ENTERPRISE ∞.
  - `MateriaPrimaService` con: `validarAcceso(granja)`, plan-gating, código único, baja lógica.
  - `MateriaPrimaRestController` `GET/POST/PUT/DELETE /api/materias-primas/{idGranja}[/{id}]`.
  - Test `PlanServiceTest` — verifica límites de granjas y materias primas por plan.
- **Frontend (`apps/web`):**
  - `data/models/materia-prima.model.ts` + nuevos métodos en `ReformaApiService`.
  - `granja-shell` convertido en layout con `<router-outlet>`, menú lateral.
  - Sub-rutas: `granja/:idGranja/resumen` (stub) y `granja/:idGranja/materias-primas` (lista + alta + baja).
  - Componente standalone con `signals`, `FormsModule`, `DecimalPipe`.
- **Build verificado:** `npm run build` OK (chunks de `materias-primas-component`, `resumen-component`, `granja-shell-component`).
- **Pendiente cercano:** auditoría al crear/actualizar/desactivar MP; import/export CSV (RF-MP-004); edición inline en UI; tests de servicio con repo mock.

### 2026-05-19 — Kickoff Sprint 1 (cimientos del monorepo)

- **Qué se hizo:**
  - Monorepo con `services/api-domain` (Spring Boot 3 + Java 21), `services/api-ml` (FastAPI), `apps/web` (Angular 19).
  - Docker Compose (Postgres 16, Redis 7, tres servicios).
  - Flyway `V001__init_schema.sql` con esquema completo `t_*` + tablas IA.
  - Endpoints: health, registro/login JWT, CRUD básico granjas con límite por plan.
  - Frontend: login, mis granjas, shell de granja, interceptor JWT.
  - CI GitHub Actions por servicio; ADR 0001–0004; scripts seed/reset.
- **Archivos raíz:** `Makefile`, `docker-compose.yml`, `.env.example`, `README.md`.
- **Pendiente:** ver §4.3 de `CONTEXTO_AGENTE_IA.md`.

### 2026-05-19 — Repo GitHub `TinoSegatti/TFG-Valentino`

- **Qué:** Inicializado git en la raíz del proyecto (`PROYECTO FINAL/`) y publicado primer commit en `main`.
- **Decisión:** **Monorepo único**. Front y back conviven en `DESARROLLO/` (alineado con §11.1 de la especificación). No se crean repos separados por ahora.
- **`.gitignore` y `.gitattributes`** trasladados a la raíz con rutas `DESARROLLO/...`. Excluyen `node_modules`, `target`, `dist`, `.angular`, `.env`.
- **Remote:** `origin` = https://github.com/TinoSegatti/TFG-Valentino.git
- **Commit:** `chore: kickoff TFG-Valentino (Sprint 1 cimientos)` — 131 archivos.

### 2026-05-19 — Reubicación a carpeta `DESARROLLO/`

- **Qué:** Todo el código de la plataforma movido desde la raíz del proyecto y desde `DESARROLLO TODO/` hacia **`DESARROLLO/`** (única ubicación válida).
- **Documentación:** Creados `CONTEXTO_AGENTE_IA.md` y este registro.
- **Raíz del proyecto (`PROYECTO FINAL/`):** Solo `README.md` índice + carpetas académicas (`DOCUMENTACION CURSOR`, `ENTREGAS`, `IMAGENES`).
