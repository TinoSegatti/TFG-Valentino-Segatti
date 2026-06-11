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

### 2026-06-11 — Tests de integración del import/export CSV (catálogos + fórmulas)
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:**
  - Nuevo IT `CsvImportacionIntegracionIT` (Testcontainers Postgres 16 + Flyway V001–V009, perfil `it`) que cubre las funcionalidades nuevas de CSV end-to-end Service → JPA → Postgres. 7 casos:
    - **Materias primas:** round-trip export→baja→import (vuelven como altas nuevas); mezcla de filas OK / duplicado activo / sin código sin abortar el lote (resumen `filasOk`/`filasError` con líneas correctas).
    - **Proveedores:** round-trip que conserva campos opcionales (email, cuit, localidad).
    - **Animales:** import con fila sin `descripcion` reportada sin cortar el resto.
    - **Fórmulas (CSV denormalizado):** import de fórmula completa (600+400=1000 kg) persiste cabecera+detalle y costo congelado (28000); export reproduce las líneas; suma ≠ 1000 reporta error y no persiste; cabecera inconsistente (descripción distinta dentro del mismo `codigo_formula`) invalida toda la fórmula.
  - Las aserciones de estructura de fórmula se hacen vía SQL (JdbcTemplate) para no navegar proxies lazy fuera de sesión (el import de fórmulas corre con `Propagation.NEVER`).
- **Verificación:** los 7 casos pasan contra un Postgres 16 real (Flyway aplicó las 9 migraciones). En este host Testcontainers 1.20.x/1.21.x no puede hablar con Docker Desktop 4.77 / Engine 29 por el named pipe (HTTP 400 de docker-java), por lo que la corrida se hizo apuntando a un Postgres levantado manualmente; el IT versionado conserva el patrón Testcontainers de los IT hermanos (`CatalogosIntegracionIT`, etc.). También verde `CatalogosIntegracionIT` (5/5) e `InventarioIntegracionIT` (3/3) por la misma vía.
- **Suite unitaria/servicios:** `mvn test` → 130/130 verde tras dos correcciones de tests rotos que la bloqueaban:
  - `FormulaServiceTest.importarCsv_descripcionInconsistente`: se quitaron 3 stubs innecesarios (plan/límite/count) — la inconsistencia de descripción se detecta al agrupar, antes del plan-gating, y Mockito strict fallaba por `UnnecessaryStubbingException`.
  - `InventarioRecalculoServiceTest.recalcular_preservaDiferenciaManual`: typo en el stub de fabricaciones (`500.0` → `50.0`) que daba `cantidadSistema = -350` en vez de `100` (preexistente desde `a4569e5`, no se ejecutaba por el Docker roto).
- **`ComprasIntegracionIT` saneado (4 casos):** estaba desfasado de la lógica de negocio vigente de `CompraService`, que valida en dos niveles: (1) por línea `cantidad × precio ≈ subtotal` y (2) agregada `Σ subtotales ≈ total_factura`, ambas con tolerancia ±0,50 (`CompraCalculo`). Los fixtures tenían líneas incoherentes que disparaban la validación por línea antes de la de suma. Correcciones (solo datos de prueba, sin tocar producción):
  - `guardarDetalle_sumaFueraDeTolerancia`: línea ahora coherente (9 × 100 = 900) y total de factura 1000 → ahora sí dispara el error de "suma de subtotales" (que era lo que el test pretendía verificar).
  - `guardarDetalle_sincronizaPrecioCatalogoEHistorial`: total y subtotal a 1.200 (10 × 120).
  - `guardarDetalle_facturaAntiguaNoPisaPrecioVigente`: factura antigua a 250 (5 × 50).
  - `guardarDetalle_facturaGrandeDosLineas`: conteo de detalle persistido vía SQL en vez de navegar la colección lazy fuera de sesión.
  - Resultado: 6/6 verde contra Postgres real.
- **Archivos principales:** `services/api-domain/src/test/java/com/reforma/domain/csv/CsvImportacionIntegracionIT.java`; fixes en `FormulaServiceTest.java`, `InventarioRecalculoServiceTest.java` y `ComprasIntegracionIT.java`.
- **Pendiente:** correr `make test-domain-it` en un entorno con Docker compatible con Testcontainers para validar los IT por su ruta normal.

### 2026-06-11 — Detalle de fórmula con precisión a 2 decimales
- **Autor/agente:** Cursor Agent
- **Qué:**
  - El detalle de fórmula (cantidad en kg, porcentaje, precio snapshot y costo parcial) ahora se redondea a **2 decimales** (antes 3). La tolerancia de cierre del lote pasó de `0.001` a `0.01` kg, acorde a la nueva precisión.
  - Backend: única fuente de verdad en `FormulaCalculo` (`DECIMALES = 2`, `TOLERANCIA_KG = 0.01`); afecta automáticamente guardado de detalle, import/export CSV, responses y `FormulaCostoSyncService`.
  - Frontend: `formula.model.ts` (`DECIMALES_FORMULA = 2`, `TOLERANCIA_KG_FORMULA = 0.01`), input de cantidad con `step="0.01"` y todos los pipes de fórmula a `'1.2-2'` (detalle y listado).
  - Tests: `FormulaCalculoTest` con caso de redondeo a 2 decimales.
- **Archivos principales:** `domain/formulas/support/FormulaCalculo.java`, `apps/web/.../data/models/formula.model.ts`, `apps/web/.../features/granja/formulas/formula-detalle.component.ts`, `apps/web/.../features/granja/formulas/formulas.component.ts`, `services/api-domain/src/test/.../FormulaCalculoTest.java`.
- **Pendiente:** —

### 2026-06-10 — Import/Export CSV de fórmulas con detalle (extensión RF-FORM)
- **Autor/agente:** Cursor Agent
- **Que:**
  - Formato denormalizado: una fila por ingrediente con cabecera repetida. Columnas `codigo_formula, descripcion_formula, codigo_animal, codigo_materia_prima, cantidad_kg`.
  - Backend: `FormulaService.exportarCsv` y `importarCsv`. El import agrupa por `codigo_formula` y persiste cada fórmula en su propia transacción (`Propagation.REQUIRES_NEW` vía self-injection con `@Lazy`); las fórmulas con error no abortan al resto.
  - Validaciones por grupo: cabecera consistente (descripción y código de animal iguales en todas las filas), animal activo por código, MPs activas por código, sin MPs repetidas, suma exacta = 1000 kg, código de fórmula no duplicado entre activas, plan-gating.
  - Endpoints `GET/POST /api/formulas/{idGranja}/csv` (multipart `archivo`).
  - Frontend: `ReformaApiService.{exportar,importar}FormulasCsv`, integración del componente compartido `catalogo-csv-bar` en `formulas.component.ts` (siempre visible junto al botón de "Crear formula").
  - Backend extra: `AnimalRepository.findByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue` para resolver el animal por código en el import.
  - Tests: `FormulaServiceTest` con 3 casos nuevos (mezcla OK/error, descripción inconsistente, export serializa).
- **Archivos principales:** `domain/formulas/service/FormulaService.java`, `domain/formulas/controller/FormulaRestController.java`, `domain/animales/repository/AnimalRepository.java`, `apps/web/.../data/api/reforma-api.service.ts`, `apps/web/.../features/granja/formulas/formulas.component.ts`, `services/api-domain/src/test/.../FormulaServiceTest.java`.
- **Pendiente:** smoke E2E del flujo (subir CSV con docker compose levantado); documentar el formato CSV en una pantalla de ayuda; soporte futuro de update (hoy reusar un código de fórmula activa devuelve error 409, sin opción de reemplazo).

### 2026-06-10 — Import/Export CSV de catálogos (RF-MP-004, RF-ANI-003)
- **Autor/agente:** Cursor Agent
- **Que:**
  - Utilidad compartida `common/csv/`: `CsvWriter` (RFC 4180 simplificado, CRLF, quote selectivo), `CsvReader` (acepta BOM UTF-8, celdas multilinea entrecomilladas, header case-insensitive), `CsvFields` (helpers `requerido`, `opcional`, `decimalOpcional` con formato es-AR/técnico), `CsvImportResult` + `CsvImportError`.
  - **Materias Primas:** `MateriaPrimaService.exportarCsv` (columnas `codigo, nombre, precio_por_kilo`) e `importarCsv` (alta por fila con plan-gating + validación ADR 0005; reporta resumen sin abortar). Endpoints `GET/POST /api/materias-primas/{idGranja}/csv` (multipart `archivo`).
  - **Proveedores:** mismo patrón. Columnas `codigo, nombre, telefono, email, cuit, direccion, localidad, notas`. Endpoints `GET/POST /api/proveedores/{idGranja}/csv`.
  - **Animales:** mismo patrón. Columnas `codigo, descripcion, categoria, observaciones`. Endpoints `GET/POST /api/animales/{idGranja}/csv`.
  - **Frontend:** modelo `csv.model.ts` (`CsvImportResult`, helper `descargarBlobComoArchivo`), componente reutilizable `shared/catalogo-csv-bar.component.ts` (Exportar / Importar / panel de resumen con detalles por línea). Integrado en `materias-primas`, `proveedores`, `animales`. `ReformaApiService.{exportar,importar}{MateriasPrimas,Proveedores,Animales}Csv`.
  - **Tests backend:** `CsvWriterTest`, `CsvReaderTest`, `CsvFieldsTest` + dos casos nuevos en `MateriaPrimaServiceTest` (export serializa, import mezcla filas OK / inválidas / duplicadas).
- **Archivos principales:** `common/csv/*`, `MateriaPrimaService.java` + controller, `ProveedorService.java` + controller, `AnimalService.java` + controller, `apps/web/.../data/models/csv.model.ts`, `data/api/reforma-api.service.ts`, `features/granja/shared/catalogo-csv-bar.component.ts`, `materias-primas.component.ts`, `proveedores.component.ts`, `animales.component.ts`, tests bajo `common/csv/` + `materiasprimas/service/`.
- **Pendiente:** smoke test E2E (subir CSV con docker compose levantado); replicar el patrón en fórmulas si se requiere; documentar formato CSV esperado en una sección de la web.

### 2026-06-03 — UX unificada: Ver + Eliminar en tablas, edición en paneles
- **Autor/agente:** Cursor Agent
- **Que:**
  - Listados de **Compras**, **Formulas** y **Fabricaciones**: solo acciones **Ver** y **Eliminar** (doble confirmación con frase).
  - Pantalla **Ver**: dos contenedores (Cabecera / Detalle) en modo lectura; botón **Editar** en cada panel activa edición local con Guardar/Cancelar.
  - Validación cruzada: al guardar cabecera o detalle con datos inconsistentes (ej. total factura vs suma ítems) se advierte qué sección editar.
  - Rutas `/editar` eliminadas; lógica fusionada en `*-detalle.component.ts`.
  - Estilos compartidos `shared/granja-vista.styles.ts`.
- **Archivos principales:** `compra-detalle`, `formula-detalle`, `fabricacion-detalle`, listados, `app.routes.ts`, `formula.model.ts` (frase eliminar).
- **Pendiente:** catálogos MP/proveedores/animales aún usan patrón anterior (alta rápida + baja simple).

### 2026-06-03 — Modulo Fabricaciones / egresos (RF-FAB)
- **Autor/agente:** Cursor Agent
- **Que:**
  - Migracion `V009__fabricaciones_codigo_estado.sql`: codigo de negocio, estado `BORRADOR`/`REGISTRADA`, `id_formula` nullable en borrador, snapshots de formula y MP en detalle.
  - Backend `domain/fabricaciones/`: entidades, repos, `FabricacionCalculo`, `FabricacionService`, REST `/api/fabricaciones/{idGranja}`.
  - Flujo: cabecera (codigo, fecha, descripcion) → detalle (formula por codigo/descripcion + veces). Costo = `costoTotalFormula` al momento del registro × veces (congelado; no se recalcula si cambian precios MP).
  - Consumo stock: `cantidadKg_formula × veces` por MP; hook en `InventarioRecalculoService` (resta fabricaciones `REGISTRADAS` activas).
  - `PlanService.limiteFabricaciones`: DEMO 5 / STARTER 50 / BUSINESS 500 / ENTERPRISE ∞.
  - Frontend Angular: listado (fecha `dd/MM/yyyy`), nueva cabecera, editar cabecera, detalle con autocomplete cruzado de formulas, eliminar con confirmacion.
  - Tests: `FabricacionCalculoTest`; `InventarioRecalculoServiceTest` actualizado con resta por fabricaciones.
- **Archivos principales:** `V009`, `domain/fabricaciones/*`, `InventarioRecalculoService.java`, `PlanService.java`, `apps/web/.../fabricaciones/*`, `fabricacion.model.ts`, `reforma-api.service.ts`, `app.routes.ts`, `granja-shell.component.ts`.
- **Pendiente:** `mvn test` / IT de integracion fabricacion+inventario; commit + push.

### 2026-06-05 — Estabilizacion Compras e Inventario (fixes UI + backend)
- **Autor/agente:** Cursor Agent
- **Que:**
  - **Compras — detalle factura:** calculo bidireccional cantidad x precio = subtotal en tiempo real (evento `input`, sin depender de `ngModel` roto). Suma de subtotales reactiva con un solo item; `puedeGuardar` cuando total cuadra y MP seleccionada. Snapshot `fingerprintLineasGuardables` para detectar cambios reales (no bloqueo falso al salir). Guard `CanDeactivate` + `mensajeErrorHttp()` en errores API.
  - **Compras — backend:** `saveAndFlush(cabecera)` antes de hooks de precio/inventario en `guardarDetalle` y `eliminarCabecera` (evita recalcular con datos stale en BD). Record `CompraTotalesMateriaPrima` en query de totales (fix `ArrayIndexOutOfBoundsException`).
  - **Inventario — backend:** `InventarioRecalculoService` sin `@Transactional(readOnly)` en calculos; upsert unificado con `save()`; handler HTTP 409 en optimistic locking (`GlobalExceptionHandler`).
  - **Inventario — frontend:** merma = `cantidadSistema - cantidadReal` en tabla y resumen. Resumen toneladas: suma `cantidadReal > 0` / 1000, formato `es-AR` (evita confundir `10,000 t` con diez mil). Modales inicializar / editar cantidad real.
  - **Infra web:** redirect `iventario` → `inventario`; `environment.prod.ts` `apiUrl: ''` (proxy nginx `/api`).
- **Archivos principales:** `CompraService.java`, `compra-detalle.component.ts`, `compra.model.ts`, `compras.component.ts`, `InventarioRecalculoService.java`, `InventarioService.java`, `GlobalExceptionHandler.java`, `inventario.component.ts`, `api-error.util.ts`, `compra-detalle.component.spec.ts`.
- **Pendiente:** commit + push al remoto; ejecutar `mvn test` y `mvn test -Pit` antes del merge.

### 2026-06-04 — Modulo Compras end-to-end (RF-COMP)
- **Autor/agente:** Cursor Agent
- **Que:**
  - CRUD compras: cabecera (proveedor, numero factura, fecha, total, observaciones) + detalle por lineas MP.
  - Estados `BORRADOR` / `REGISTRADA`; tolerancia ± $0,50 en lineas y total factura vs suma subtotales.
  - Snapshots inmutables en `t_compra_detalle` (codigo/nombre MP, precio anterior) — ADR 0005.
  - Migracion `V006__compras_snapshots_estado.sql`.
  - Frontend Angular: listado, nueva cabecera, editar cabecera, detalle editable con autocomplete MP, eliminar factura con frase de confirmacion, guard `compraDetalleCanDeactivate`.
  - Modelos `compra.model.ts` (`recalcularLineaDetalle`, `fingerprintLineasGuardables`, helpers de redondeo).
  - Tests: `CompraServiceTest`, `CompraRestControllerTest`, `CompraCalculoTest`, `ComprasIntegracionIT`.
- **Archivos principales:** `domain/compras/*`, `apps/web/.../compras/*`, `V006`, `reforma-api.service.ts`, `app.routes.ts`.
- **Pendiente:** ver entrada 2026-06-05 (fixes de calculo automatico y persistencia).

### 2026-06-03 — Modulo Inventario (RF-INV)
- **Autor/agente:** Cursor Agent
- **Que:**
  - Tabla de inventario con todas las MPs activas: codigo, precio vigente (sync compras), cantidad acumulada, cantidad en sistema, cantidad real editable, merma, valor de stock (real x precio vigente), precio almacen (promedio ponderado por kilos incluyendo inventario inicial).
  - Recalculo automatico al registrar/eliminar/modificar compras via `CompraPrecioMateriaPrimaService` + `InventarioRecalculoService` (preserva ajuste manual real - sistema).
  - Inicializar inventario (`t_inventario_inicial`), recalcular, vaciar, editar cantidad real por MP.
  - Frontend Angular: listado, resumen, modales inicializacion y edicion cantidad real.
  - Tests: `InventarioCalculoTest`, `InventarioRecalculoServiceTest`, `InventarioIntegracionIT`.
- **Archivos principales:** `domain/inventario/*`, `CompraPrecioMateriaPrimaService`, `CompraDetalleRepository.totalPorMateriaPrima`, `apps/web/.../inventario/*`.
- **Pendiente:** restar fabricaciones en cantidad en sistema (TODO RF-FAB); grafico de existencias opcional.

### 2026-06-04 — Modulo Formulas dietarias (RF-FORM)
- **Autor/agente:** Cursor Agent
- **Que:**
  - CRUD formulas: cabecera (codigo, descripcion, animal con autocomplete) + detalle MP/cantidad kg.
  - Regla 1000 kg obligatorios para guardar y salir del editor; aviso de kg faltantes.
  - Costo de formula = suma de (cantidad x precio x kilo vigente del catalogo).
  - Al cambiar precios por compras, `FormulaCostoSyncService` recalcula subtotales y costo total de formulas afectadas.
  - Migracion V008 (unique parcial codigo formula). Plan-gating RF-FORM-005.
  - Frontend: listado, nueva, editar cabecera, detalle ingredientes, guard CanDeactivate.
- **Archivos principales:** `domain/formulas/*`, `FormulaCostoSyncService`, `V008`, `apps/web/.../formulas/*`.

### 2026-06-04 — Compras: sincronización precio x kilo + historial ML
- **Autor/agente:** Cursor Agent
- **Qué:**
  - Al guardar el detalle de una compra **REGISTRADA**, el catálogo `precio_por_kilo` de cada MP afectada se recalcula según la compra con `fecha_compra` más reciente (no pisa el vigente si se edita una factura antigua).
  - Historial persistido en `t_registro_precio` (migración `V007`): `id_compra`, `fecha_referencia` (fecha de negocio), `origen=COMPRA` — base para series temporales de `api-ml`.
  - Servicio `CompraPrecioMateriaPrimaService`; recálculo también al eliminar compra o actualizar cabecera REGISTRADA (p. ej. cambio de fecha).
  - Tests: `CompraPrecioMateriaPrimaServiceTest` + escenarios en `ComprasIntegracionIT`.
- **Archivos principales:** `V007__registro_precio_compras_ml.sql`, `RegistroPrecio.java`, `CompraPrecioMateriaPrimaService.java`, `CompraDetalleRepository.java`, `CompraService.java`.
- **Pendiente:** consumir `t_registro_precio` desde `api-ml`; restar fabricaciones en inventario (RF-FAB).

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
