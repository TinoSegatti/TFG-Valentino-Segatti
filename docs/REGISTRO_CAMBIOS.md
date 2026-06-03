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

### 2026-06-02 — Estabilización catálogos (MP, Proveedores, Animales)
- **Autor/agente:** Cursor Agent
- **Qué:**
  - Tests unitarios reforzados: mock de `save()` simula `IDENTITY` (`EntidadConIdMocks`), asserts de `Long id`.
  - `CatalogosIntegracionIT`: 5 tests con Postgres 16 (Testcontainers) + Flyway V001–V005 (id BIGINT, soft-delete ADR 0005).
  - Perfil Maven `-Pit` para integración; `mvn test` excluye `*IntegracionIT` por defecto.
  - CI: paso adicional `mvn test -Pit`. `make test-domain` y `scripts/test-domain.ps1` para ejecutar en local.
  - **Gate antes de Compras:** catálogos en verde (unit + `-Pit`) → recién ahí Sprint Compras.
- **Pendiente:** ejecutar `.\scripts\test-domain.ps1` con Docker Desktop activo y confirmar verde en tu máquina.

### 2026-06-02 — Refactor: ID BIGINT autoincremental vs código de negocio (ADR 0006)
- **Autor/agente:** Cursor Agent
- **Qué:**
  - Separación explícita **id** (Long, PK autoincremental, irrepetible) vs **codigo** (String, usuario, único entre activos por granja) en Materias Primas, Proveedores y Animales.
  - Migración `V005__catalogo_ids_bigint_identity.sql`: convierte PK de `t_materia_prima`, `t_proveedor`, `t_animal` y columnas FK relacionadas a `BIGINT IDENTITY`; vacía datos dependientes (dev: re-seed o alta desde UI).
  - Entidades JPA: `@GeneratedValue(strategy = GenerationType.IDENTITY) private Long id`; eliminado `IdGenerator` en `crear()` de los tres servicios.
  - Repositories `JpaRepository<*, Long>`; DTOs `Long id`; controllers `@PathVariable Long id*`.
  - Tests y frontend (`id: number`) actualizados; rutas PUT/DELETE usan id numérico (`/1`, `/999`).
  - ADR `docs/adr/0006-id-numerico-vs-codigo-negocio.md`. Usuario/Granja siguen VARCHAR(32) por ahora.
- **Archivos principales:** entidades/repositories/services/controllers/dto de `materiasprimas`, `proveedores`, `animales`; `V005`; tests; `apps/web` models + `reforma-api.service.ts`; `scripts/seed.sh`.
- **Pendiente:** migrar Usuario/Granja a BIGINT cuando se unifique el modelo de IDs en todo el sistema.

### 2026-06-02 — Sprint 2 #5: módulo Animales end-to-end
- **Autor/agente:** Cursor Agent (Claude Opus 4.7)
- **Qué:**
  - Implementado catálogo de Animales (RF-ANI-001/002) end-to-end siguiendo el mismo patrón de Materias Primas y Proveedores.
  - **Backend:** entity `Animal` (codigo, descripcion, categoria opcional, **observaciones** TEXT opcional, activo, fechas), repository, DTOs con validación Jakarta, service con plan-gating y política ADR 0005, controller REST `/api/animales/{idGranja}`.
  - **Migración `V004__animales_extras.sql`**: agrega `observaciones TEXT` y `fecha_ultima_actualizacion`, dropea el UNIQUE total `t_animal_id_granja_codigo_animal_key` y crea índice parcial `uq_animal_granja_codigo_activo WHERE activo=TRUE`.
  - **Plan-gating** `PlanService.limiteAnimales`: DEMO 5 / STARTER 20 / BUSINESS 100 / ENTERPRISE ∞.
  - **Frontend:** modelo TypeScript, métodos en `ReformaApiService` (`getAnimales`, `crearAnimal`, `actualizarAnimal`, `desactivarAnimal`), componente standalone `AnimalesComponent` con alta inline (incluye textarea para observaciones), búsqueda con debounce 300ms y baja lógica con confirm. Ruta `granja/:idGranja/animales` y entrada "Animales" en el menú lateral.
  - **Tests:** 20 nuevos (11 service + 8 controller + 1 PlanService). Total backend: **65 tests verdes**.
  - Smoke test E2E verificado: alta `CERDA` → baja lógica → re-alta con mismo código → la BD tiene dos filas distintas (una inactiva con datos originales, otra activa), confirmando ADR 0005.
- **Archivos principales:**
  - `services/api-domain/src/main/resources/db/migration/V004__animales_extras.sql`
  - `services/api-domain/src/main/java/com/reforma/domain/animales/{package-info, entity/Animal, repository/AnimalRepository, dto/AnimalRequest, dto/AnimalResponse, service/AnimalService, controller/AnimalRestController}.java`
  - `services/api-domain/src/main/java/com/reforma/domain/suscripciones/service/PlanService.java` (método `limiteAnimales`)
  - `services/api-domain/src/test/java/com/reforma/domain/animales/{service/AnimalServiceTest, controller/AnimalRestControllerTest}.java`
  - `services/api-domain/src/test/java/com/reforma/domain/suscripciones/service/PlanServiceTest.java`
  - `apps/web/src/app/data/models/animal.model.ts`
  - `apps/web/src/app/data/api/reforma-api.service.ts`
  - `apps/web/src/app/features/granja/animales/animales.component.ts`
  - `apps/web/src/app/app.routes.ts`
  - `apps/web/src/app/features/granja/granja-shell.component.ts`
- **Pendiente:** módulo de Compras (Sprint 2, RF-COMP-001/002/003) — incluirá los **snapshots inmutables** definidos en ADR 0005.

### 2026-06-02 — Sprint 2 #4: ADR 0005 soft-delete + entidades versionadas
- **Autor/agente:** Cursor Agent (Claude Opus 4.7)
- **Qué:**
  - Revertida la estrategia de "reactivar y pisar" que se había probado en MaterialesPrimas y Proveedores. Quedó documentado por qué (riesgo de envenenar el histórico, KPIs y series de `api-ml`).
  - Adoptamos política de **soft-delete + entidad nueva al reutilizar código**. La fila inactiva queda congelada con datos y referencias originales; la reutilización del código crea otra fila con `id` distinto.
  - Migración `V003__unique_solo_activos.sql`: drop de las constraints `UNIQUE (id_granja, codigo)` y reemplazo por índices parciales UNIQUE con predicado `WHERE activa/o = TRUE`. Verificado en Postgres del entorno dev.
  - Borradas anotaciones `@UniqueConstraint` de las entities para que Flyway sea la única fuente de verdad sobre constraints.
  - `MateriaPrimaService.crear` y `ProveedorService.crear` simplificados: chequean colisión solo entre activos (`existsBy...AndActivaTrue`) e insertan fila nueva siempre. `actualizar` usa la misma semántica para detectar cambios de código duplicado.
  - `MateriaPrimaRepository` y `ProveedorRepository`: eliminados `findByGranjaIdAndCodigo...IgnoreCase`; agregados `existsByGranjaIdAndCodigo...IgnoreCaseAndActivaTrue/AndActivoTrue`.
  - Tests actualizados (45 tests verdes): `crear_codigoDuplicadoActiva`, `crear_reusaCodigoDeBajaLogica` reemplazan a los antiguos `crear_reactivaSiExiste*` y `crear_reactivacionBloqueadaPorPlan`. Los tests de `actualizar_codigoDuplicado` apuntan al nuevo método.
  - ADR `docs/adr/0005-soft-delete-y-snapshots.md` documenta la decisión + la convención de **snapshots inmutables en transacciones** que se aplicará desde el módulo de Compras en adelante.
  - Rebuild del contenedor `api-domain` con la nueva imagen; Flyway aplicó V003 OK.
- **Archivos principales:**
  - `services/api-domain/src/main/resources/db/migration/V003__unique_solo_activos.sql`
  - `services/api-domain/src/main/java/com/reforma/domain/materiasprimas/{entity/MateriaPrima.java, repository/MateriaPrimaRepository.java, service/MateriaPrimaService.java}`
  - `services/api-domain/src/main/java/com/reforma/domain/proveedores/{entity/Proveedor.java, repository/ProveedorRepository.java, service/ProveedorService.java}`
  - `services/api-domain/src/test/java/com/reforma/domain/{materiasprimas, proveedores}/service/*ServiceTest.java`
  - `docs/adr/0005-soft-delete-y-snapshots.md`
- **Pendiente:**
  - Aplicar la convención de snapshots en `t_compra_detalle` cuando se implemente el módulo de Compras.
  - Replicar el patrón de índice parcial UNIQUE en los próximos módulos de catálogo (animales, fórmulas, lotes…) desde la primera migración.

### 2026-06-02 — Sprint 2 #3: módulo Proveedores end-to-end

- **Autor/agente:** Cursor (Claude Opus 4.7).
- **Qué:** Segundo recurso de dominio del Sprint 2 implementado end-to-end (RF-PROV-001 / RF-PROV-002), siguiendo el patrón estandarizado por Materias Primas.
- **Backend (`api-domain`):**
  - **Migración** `V002__proveedores_extras.sql`: agrega columnas opcionales `telefono`, `email`, `cuit`, `notas` y `fecha_ultima_actualizacion` a `t_proveedor` (RF-PROV-001 pedía datos opcionales que V001 no contemplaba).
  - **Entidad** `Proveedor` con unique `(idGranja, codigoProveedor)`; **repo** `ProveedorRepository` con búsqueda por nombre case-insensitive (`...AndNombreProveedorContainingIgnoreCase...`).
  - **DTOs** `ProveedorRequest` (con `@Email`, `@Size`, `@NotBlank`) y `ProveedorResponse`.
  - `PlanService.limiteProveedores`: DEMO 5 / STARTER 15 / BUSINESS 50 / ENTERPRISE ∞.
  - `ProveedorService`: valida acceso, plan-gating, unicidad case-insensitive solo cuando el código cambia, normaliza vacíos a null, baja lógica.
  - `ProveedorRestController` `GET/POST/PUT/DELETE /api/proveedores/{idGranja}[/{id}]`, con `?buscar=` opcional en el GET.
  - **Tests nuevos:** `ProveedorServiceTest` (10), `ProveedorRestControllerTest` (8).
- **Frontend (`apps/web`):**
  - `data/models/proveedor.model.ts` + 4 métodos en `ReformaApiService` (`getProveedores`, `crearProveedor`, `actualizarProveedor`, `desactivarProveedor`).
  - `features/granja/proveedores/proveedores.component.ts` standalone con signals + búsqueda con debounce 300 ms.
  - Ruta `granja/:idGranja/proveedores` + entrada en el menú lateral de `granja-shell`.
- **Tests completados de Materias Primas:**
  - `MateriaPrimaServiceTest` (12 casos: happy path, trim, código duplicado, sin acceso, límite DEMO, plan ENTERPRISE sin tope, actualización con/sin cambio de código, baja lógica, 404).
  - `MateriaPrimaRestControllerTest` (9 casos cubriendo 200/201/204/400/403/404/409).
  - Fix transversal: `HealthControllerTest` ahora declara `@MockBean TokenJwtServicio` para que el contexto reducido del `@WebMvcTest` pueda construir el `JwtAuthenticationFilter` (era el motivo por el que ese test fallaba desde el Sprint 1).
- **Resultado:** `mvn test` ⇒ **43 tests, 0 errores, BUILD SUCCESS**. `npm run build` ⇒ OK, nuevo chunk lazy `proveedores-component`.
- **Archivos principales:**
  - `services/api-domain/src/main/resources/db/migration/V002__proveedores_extras.sql`
  - `services/api-domain/src/main/java/com/reforma/domain/proveedores/**`
  - `services/api-domain/src/test/java/com/reforma/domain/proveedores/**`
  - `services/api-domain/src/test/java/com/reforma/domain/materiasprimas/{service,controller}/*Test.java`
  - `apps/web/src/app/{data/models/proveedor.model.ts, data/api/reforma-api.service.ts, features/granja/proveedores/, app.routes.ts, features/granja/granja-shell.component.ts}`
- **Pendiente cercano:** detalle de proveedor con historial de precios y promedios por MP (RF-PROV-003), edición inline desde la UI, auditoría de cambios, próximo módulo: **Compras** (RF-COMP-001/002/003).

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
