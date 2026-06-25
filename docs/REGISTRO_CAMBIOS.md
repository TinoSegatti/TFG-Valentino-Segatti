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

### 2026-06-23 — Inventario: Precio Almacén como costo promedio ponderado (acumuladores gasto/kilos)
- **Autor/agente:** Cursor (Claude Opus 4.8)
- **Qué:** se formalizó el cálculo de `t_inventario.precio_almacen` como costo promedio ponderado de
  adquisición = **gasto acumulado / kilos acumulados**, donde la primera "iteración" del acumulador
  es el valor del stock inicial (`cantidad_inicial × precio_inicial`) y cada compra REGISTRADA suma
  su **subtotal** y sus kilos. El cálculo se reconstruye desde cero en cada recálculo, por lo que
  editar o eliminar una factura se refleja automáticamente. No interviene el consumo por
  fabricaciones ni el ajuste manual de stock.
  - **Cambio principal:** el acumulador de gasto ahora usa `SUM(cd.subtotal)` (gasto real de la
    línea) en vez de `SUM(cd.cantidadComprada × cd.precioUnitario)`, respetando la tolerancia de
    redondeo con la que se carga el detalle de compra. Afecta `totalPorMateriaPrima` y
    `sumarTotalesPorMateriaPrima` en `CompraDetalleRepository`.
  - **Claridad:** en `InventarioRecalculoService.calcularValores` se hicieron explícitos los
    acumuladores `gastoAcumulado` / `kilosAcumulados` con comentarios; se mejoró el Javadoc de
    `InventarioCalculo.precioAlmacenPonderado` (parámetros renombrados a `gastoAcumulado` /
    `kilosAcumulados`).
  - **Doc nuevo:** [`PRECIO_ALMACEN.md`](PRECIO_ALMACEN.md) — flujo de información y lógica línea por
    línea del algoritmo.
- **Archivos principales:** `services/api-domain/.../compras/repository/CompraDetalleRepository.java`,
  `services/api-domain/.../inventario/service/InventarioRecalculoService.java`,
  `services/api-domain/.../inventario/support/InventarioCalculo.java`, `docs/PRECIO_ALMACEN.md`.
- **Pendiente:** correr `make test-domain` en entorno con Docker (los tests unitarios mockean los
  totales y siguen verdes; el IT usa `subtotal = cantidad × precio`, sin cambios de expectativa).

### 2026-06-21 — Compras: anti-duplicado de materia prima en el detalle y formato monetario es-AR
- **Autor/agente:** Cursor (Claude Opus 4.8)
- **Qué:** dos arreglos en el módulo de compras.
  - **Materia prima no repetible en un detalle de compra:** cargar la misma MP en dos ítems generaba doble cómputo de kilos/dinero en inventario, historial de precios duplicado y un precio vigente de catálogo ambiguo. Ahora se bloquea en dos capas:
    - **Backend** (`CompraService.construirLineasValidadas`): se rechaza con **400** ("La materia prima X está repetida en el detalle…") usando un `Set<Long>` de ids vistos.
    - **Frontend** (`compra-detalle.component.ts`): computed `idsMateriaDuplicados` / `hayMateriasDuplicadas`, aviso por ítem y aviso global, botón "Guardar" deshabilitado (`puedeGuardarDetalle`) y guard en `guardarDetalle()` con mensaje en `conflictoDetalle`.
  - **Formato monetario es-AR:** los labels de precios mostraban formato de locale por defecto (`1,234.567`). Se registró `localeEsAr` y se agregó `{ provide: LOCALE_ID, useValue: 'es-AR' }` en `compra-detalle.component.ts` y `compras.component.ts`, de modo que los `DecimalPipe` ya existentes (con `$` antepuesto) rinden con separador de miles "." y decimales "," → **`$ 1.234,567`**.
  - **Tests:** `CompraServiceTest` — el caso "factura 30M" se actualizó para usar dos MPs distintas (Maíz + Soja) y se agregó `guardarDetalle_materiaPrimaDuplicada` (espera 400). La integración (`ComprasIntegracionIT`) ya usaba MPs distintas.
- **Archivos principales:** `services/api-domain/.../compras/service/CompraService.java` (+`CompraServiceTest`), `apps/web/.../features/granja/compras/{compra-detalle,compras}.component.ts`.
- **Pendiente:** correr `make test-domain` y `make build` en entorno con Docker (no disponible localmente al momento del cambio); smoke visual del formato y del bloqueo de duplicados.

### 2026-06-21 — Inventario: orden configurable de materias primas y precio/cantidad 0 en la inicialización
- **Autor/agente:** Cursor (Claude Opus 4.8)
- **Qué:** dos mejoras de UX en el modal de inicialización del inventario (`inventario.component.ts`), sin cambios de backend.
  - **Orden configurable:** además del orden alfabético por nombre (A–Z, comportamiento por defecto que ya venía del backend `OrderByNombreMateriaPrimaAsc`), se agregó un control segmentado "Ordenar por: Nombre (A–Z) / Código (menor a mayor)". El reordenamiento es client-side sobre `lineasIni` vía `cambiarOrdenIni()` + `ordenarLineas()`, usando `localeCompare` con `numeric: true` para el código (orden natural: `MP2` antes que `MP10`). Las líneas sin materia prima asignada (agregadas a mano) quedan al final.
  - **Precio/cantidad en 0:** al abrir el modal el precio se pre-carga en `0` cuando el catálogo tiene precio 0 (antes quedaba `null` y bloqueaba el botón "Inicializar"). La validación `lineasIniValidas()` ahora interpreta los campos vacíos como 0 y solo rechaza negativos/duplicados, permitiendo inicializar materias primas **sin existencias** (cantidad y/o precio 0). El payload normaliza `null → 0` con `?? 0`. El backend ya aceptaba 0 (`@PositiveOrZero`).
- **Archivos principales:** `apps/web/src/app/features/granja/inventario/inventario.component.ts`.
- **Pendiente:** smoke visual con `make dev` (probar ambos órdenes y la inicialización con líneas en 0).

### 2026-06-19 — Reestructuración del frontend: paneles de KPIs/gráficos y restyle estilo handoff
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** se adaptó el frontend al prototipo `design_handoff_reforma_erp` (distribución, animaciones y gráficos), poblando lo derivable de los endpoints actuales y dejando placeholders ("Disponible próximamente") donde el backend aún no agrega.
  - **Building blocks** (en `features/granja/shared/`): `kpi-card.component.ts` (`<reforma-kpi-card>` con delta/sparkline y entrada escalonada), `apex-charts.ts` (factories bar/donut/line/area/sparkline sobre `apex-theme.ts`), `apex-chart.component.ts` (`<reforma-apex>` envuelve `<apx-chart>`), `panel-utils.ts` (agregación mensual + top-N).
  - **Global:** `styles.css` (var `--font-display` Space Grotesk + `.rf-num`, grids `.rf-grid-*`, keyframes `rf-rise/grow/grow-x/draw/pulse`); `index.html` (fuente Space Grotesk).
  - **Shell:** `granja-shell.component.ts` (sidebar 254px con grupos, punto activo, tarjeta de plan) y `account-nav.component.ts` (búsqueda decorativa + notificaciones con pulso).
  - **Paneles por módulo:** panel principal `resumen` (collage con `forkJoin`), `materias-primas`, `proveedores` (carga compras), `animales`, `compras`, `formulas` (carga detalle de la fórmula más cara), `fabricaciones`, `inventario` (KPIs migrados + 2 gráficos), y `equipo` (KPIs + donut por rol).
  - **Build:** `docker compose build web` OK; budget inicial subido a 550kB en `angular.json`.
- **Archivos principales:** ver lista arriba + `apps/web/src/app/features/granja/**`, `apps/web/src/app/features/equipo/equipo.component.ts`, `apps/web/src/{styles.css,index.html}`, `apps/web/angular.json`.
- **Pendiente:** smoke visual con `make dev` (granja con datos y vacía); gráficos placeholder requieren agregación/histórico en el backend (consumo de materias, evolución de costos, toneladas por materia). Handoff detallado: `../DOCUMENTACION CURSOR/HANDOFF_graficos_restyle.md`.

### 2026-06-17 — Perfil de usuario, nombre de granja en la UI y baja del registro de logins fallidos
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** tres ajustes pedidos tras la primera ronda de pruebas.
  - **Nombre de granja en el header** (antes mostraba el id): `granja-shell.component.ts` ahora carga `GET /api/granjas/{id}` y muestra `nombreGranja` (fallback al id si falla).
  - **Perfil del usuario logueado:** nuevo `GET /api/usuarios/perfil` → `PerfilResponse` (id, email, nombre, apellido, `rol` OWNER/ADMIN/EDITOR/LECTOR, `esEmpleado`, `plan`, `idDueno`, `permisos` legibles). Backend `PerfilService` (+test); frontend pantalla `features/perfil` (ruta `/perfil`) con enlace en "Mis granjas". El JWT no lleva nombre/apellido, por eso se expone por endpoint.
  - **No se almacenan logins fallidos:** se quitó `auditarFalloLogin` y sus 4 llamados de `CredencialesUsuarioService` (login sigue devolviendo 401/403, sin escribir `LOGIN_FALLIDO`). Tests actualizados; `LOGIN_FALLIDO` retirado del dropdown de filtros y de la guía. El enum se conserva por compatibilidad.
  - **Validación:** suite **184/184** verde; en vivo: `GET /perfil` devuelve el perfil de `demo@reforma.local` (rol OWNER + permisos), `GET /api/granjas/g_demo` devuelve "Granja Demo", y un login con clave inválida (401) **no** genera fila de auditoría. Se borraron 5 filas `LOGIN_FALLIDO` históricas.
- **Archivos:** `usuarios/{controller/UsuarioAuthRestController,service/{CredencialesUsuarioService,PerfilService},dto/PerfilResponse}` (+tests), `apps/web/.../features/{granja/granja-shell.component,perfil/perfil.component,mis-plantas/*}.ts`, `data/api/reforma-api.service.ts`, `data/models/{usuario,auditoria}.model.ts`, `app.routes.ts`, `CLAUDE.md`, `docs/GUIA_PRUEBAS_MANUALES_USUARIOS.md`.

### 2026-06-17 — Plan DEMO usable (2 granjas + 2 empleados), botón "Crear granja" y retención DEMO
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** desbloqueo de las pruebas manuales (la cuenta DEMO no podía crear granjas ni equipo, y crearlas exigía Swagger).
  - **Límites DEMO ampliados** (`PlanService`): granjas 1→**2**, empleados 0→**2**. STARTER se subió a 2 granjas para no quedar por debajo de la prueba gratuita. Una cuenta DEMO ahora recorre el flujo completo (multi-granja + equipo/roles/revocación) sin upgrade.
  - **Botón "Crear granja"** en "Mis granjas" (frontend): formulario visible solo para el dueño que pega a `POST /api/granjas`, refresca la lista y muestra el 403 de límite de plan. Ya no hace falta Swagger.
  - **Retención de cuentas DEMO:** tarea `@Scheduled` diaria `LimpiezaCuentasDemoService` que **purga por completo** cada cuenta DEMO a los `DEMO_RETENCION_DIAS` (default 60) de su `fecha_registro` — granjas y datos granja-scoped, historial de precios (ML), auditoría, tokens/links y las cuentas (dueño + empleados). Borrado nativo ordenado en `PurgaCuentaDemoRepository` (anomalías de precio y auditoría antes de la cascada de `t_granja`; empleados antes del dueño). Configurable y con allowlist de exentos (`valentinosegatti@gmail.com`, `demo@reforma.local`). `@EnableScheduling` en `ReformaApplication`.
  - **Validación:** suite **181/181** verde; orden de borrado verificado en vivo contra Postgres (tenant sintético en transacción revertida); límite DEMO=2 verificado en vivo por API (201, 201, **403** "Límite de granjas alcanzado para el plan DEMO").
  - **Guía de pruebas** corregida: la cuenta con "Granja Demo" es `demo@reforma.local` (BUSINESS), no `valentinosegatti@gmail.com` (DEMO sin granjas); §4 reescrito sin Swagger; documentada la retención.
- **Archivos:** `suscripciones/service/PlanService.java` (+test), `mantenimiento/{service/LimpiezaCuentasDemoService,repository/PurgaCuentaDemoRepository}` (+test), `usuarios/repository/UsuarioRepository.java`, `ReformaApplication.java`, `application.yml`, `apps/web/.../features/mis-plantas/mis-plantas.component.ts`, `docs/GUIA_PRUEBAS_MANUALES_USUARIOS.md`, `CLAUDE.md`.
- **Limpieza de entorno (misma fecha):** se borraron **todas** las cuentas creadas por formulario (9 cuentas + 73 filas de auditoría), dejando **una sola cuenta hardcodeada: `demo@reforma.local`** (BUSINESS, `u_demo`, con `g_demo`). `DevDataLoader` se reapuntó de `valentinosegatti@gmail.com` a `demo@reforma.local` (con check por email **y** por id `u_demo`) para que no recree la cuenta del autor ni choque con el PK `u_demo` al arrancar. Exentos de purga reducidos a `demo@reforma.local`.
- **Pendiente:** definir política comercial real de límites/retención (hoy DEMO 60 días es provisional); commit + push del backlog acumulado.

### 2026-06-16 — Etapa 5: consola de auditoría (RF-AUD)
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** lectura de `t_auditoria` (que ya se llenaba pero nadie consultaba). Endpoint
  **`GET /api/auditoria`** (`@PreAuthorize` OWNER/ADMIN) con filtros opcionales `idGranja`, `idUsuario`,
  `accion`, `desde`, `hasta` (instantes ISO) + paginación (`pagina`/`tamano`, máx 100), orden por
  `fechaOperacion` desc.
  - **Scoping multi-tenant** (`AuditoriaConsultaService`): el actor solo ve la actividad de su tenant
    (dueño + sus empleados); un jefe ADMIN resuelve a su dueño; un empleado no-ADMIN → 403. Cada respuesta
    enriquece el actor (`actorEmail`/`actorNombre`) con una sola consulta por página.
  - **Fix PostgreSQL** "could not determine data type of parameter": el patrón `(:param is null or …)`
    deja parámetros sin tipo inferible (enum/timestamp). Se reemplazó por **flags booleanos** `(:filtraX = false or col = :x)`,
    de modo que cada valor solo aparece en su comparación tipada.
  - **DTOs:** `AuditoriaResponse` (incluye datos antes/después como JSON crudo) y `PaginaAuditoria`
    (no se serializa `Page` de Spring Data directamente).
  - **Frontend:** `features/auditoria` (tabla de solo lectura con filtros, paginación y JSON expandible),
    ruta `/auditoria` (authGuard), método `getAuditoria` en `reforma-api.service`, enlace en "Mis granjas"
    visible solo para dueño/jefe (junto a "Equipo").
- **Archivos:** `auditoria/{controller,service/AuditoriaConsultaService,repository,dto/{AuditoriaResponse,PaginaAuditoria}}`,
  `apps/web/.../features/auditoria/*`, `data/models/auditoria.model.ts`, `reforma-api.service.ts`,
  `app.routes.ts`, `features/mis-plantas/*`.
- **Tests:** `AuditoriaConsultaServiceTest` (scoping dueño+empleados, jefe→dueño, no-ADMIN→403). Suite **177/177**.
  `ng build` OK. Probado e2e en vivo: 401 sin token, 400 acción inválida, listado paginado (33 eventos/2 págs),
  filtros por acción y rango de fechas → 200.
- **Pendiente Etapa 5:** rate-limiting/lockout (Redis), cachear `token_version` en Redis, `X-Forwarded-For`
  en nginx para IP real del cliente, `/security-review` del diff.

### 2026-06-16 — Endurecimiento de sesión: revocación de JWT (tokenVersion)
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** se cierra el hueco de seguridad por el que un empleado desactivado o con el rol cambiado
  conservaba un JWT válido hasta su expiración (hasta 24 h). Mecanismo **tokenVersion**:
  - **Migración `V011__token_version.sql`**: columna `token_version INTEGER NOT NULL DEFAULT 0` en `t_usuarios`.
  - **Entidad `Usuario`**: campo `tokenVersion` (`@Builder.Default = 0`) + método `revocarSesiones()` (incrementa la versión).
  - **JWT**: `TokenJwtServicio.generarToken` agrega el claim `tv`; `validarToken` ahora compara el `tv` del token
    con la versión vigente en BD (`UsuarioRepository.findTokenVersionById`, proyección liviana) y lanza
    `JwtException` si no coincide → el filtro la captura y responde 401. Tokens viejos sin `tv` se asumen `0`
    (no se cortan sesiones existentes al desplegar; la columna arranca en 0).
  - **Puntos de revocación**: `EmpleadoService.cambiarRol` y `cambiarEstado(activo=false)`,
    y `RecuperacionCuentaService.confirmarReset` (un reset invalida sesiones con la clave anterior).
  - **Decisión**: chequeo dentro de `validarToken` (no en el filtro ni en el principal) → cero churn en los
    `@WebMvcTest` (ya mockean `TokenJwtServicio`); el `JwtUserPrincipal` no cambia.
- **Archivos principales:** `db/migration/V011__token_version.sql`, `usuarios/entity/Usuario.java`,
  `usuarios/repository/UsuarioRepository.java`, `auth/jwt/TokenJwtServicio.java`,
  `empleados/service/EmpleadoService.java`, `usuarios/service/RecuperacionCuentaService.java`.
- **Tests:** nuevo `TokenJwtServicioTest` (4: tv coincide, tv revocado, usuario inexistente, empleado lleva rol/idDueno);
  `EmpleadoServiceTest` (+1 reactivar no revoca) y aserciones de `tokenVersion` en cambiarRol/cambiarEstado/confirmarReset.
  Suite **174/174** verde (corrida en contenedor Maven 3.9 / temurin-21).
- **Probado e2e en vivo:** login → `GET /api/granjas` 200 → bump de `token_version` en BD → mismo token **401**
  (revocado) → re-login 200. Flyway aplicó V011 (esquema en v011).
- **Pendiente / futuro:** cachear `token_version` en Redis para ahorrar la lectura por request (hoy 1 query liviana);
  rate-limiting/lockout (Etapa 0); endpoint de cambio de password autenticado (revocar también ahí); Etapa 5
  (consola de auditoría). Nota operativa: la cuenta `valentinosegatti@gmail.com` quedó con contraseña `Demo1234!`.

### 2026-06-16 — Email SMTP real (RF-AUTH): cableado de variables
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** se completa el envío de correos real. La lógica ya existía (`SmtpEmailNotificacionService`,
  activado por `reforma.email.mode=smtp`; `application.yml` ya leía `SMTP_*`/`EMAIL_MODE`/`EMAIL_FROM`),
  pero las variables **no llegaban al contenedor**. Se agregan `EMAIL_MODE`, `SMTP_HOST`, `SMTP_PORT`,
  `SMTP_USER`, `SMTP_PASSWORD`, `EMAIL_FROM` al bloque `environment` de `api-domain` en `docker-compose.yml`
  (con defaults; `EMAIL_FROM` cae a `SMTP_USER` y luego `no-reply@reforma.local`). Documentadas en `.env.example`
  (modo `log` por defecto) y cargadas en `.env` local con Gmail App Password de `reforma.soft.co@gmail.com`
  (`EMAIL_MODE=smtp`). Nota: el App Password debe ir **sin espacios** (JavaMailSender no los limpia).
- **Archivos principales:** `docker-compose.yml`, `.env.example`, `.env` (ignorado por git).
- **Probado e2e:** `POST /solicitar-reset` con `valentinosegatti@gmail.com` → correo recibido, contraseña
  restablecida e ingreso a la plataforma OK. Detección clave: la imagen `api-domain` cacheada (build 2026-06-14)
  no contenía `SmtpEmailNotificacionService`; se requirió `docker compose up -d --build api-domain`.
- **Pendiente:** Google OAuth sigue pendiente.

### 2026-06-16 — Módulo Usuarios · Etapa 4: gestión de equipo + matriz de roles
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** se hacen efectivos los roles (antes solo se bloqueaba a LECTOR): jerarquía dueño/jefe/editor/lector
  con permisos reales, gestión de equipo y candado de creación de granjas. **Sin migración.**
  - **Granja OWNER-only:** `POST /api/granjas` ahora exige `ROLE_OWNER` (regla específica antes de la
    de GRANJA_SCOPED en `SecurityConfig`). Los empleados ven y operan las granjas del dueño, pero no las crean;
    el multi-planta sigue gateado por `PlanService.limiteGranjas`.
  - **Matriz de roles** (en `EmpleadoService`, con `autorizarAccion`/`resolverDueno`): OWNER gestiona todo el
    equipo incluido designar/quitar ADMIN; ADMIN (jefe) gestiona EDITOR/LECTOR pero **no** puede tocar a otro
    ADMIN, designar ADMIN ni modificarse a sí mismo; EDITOR/LECTOR no gestionan (bloqueados por authority).
  - **Endpoints:** `GET /api/empleados` (listar equipo), `PUT /api/empleados/{id}/rol`, `PUT /api/empleados/{id}/activo`
    (baja/alta lógica, ADR-0005), todos `@PreAuthorize("hasAnyRole('OWNER','ADMIN')")`. `POST /api/empleados`
    (invitar) ahora **delegable a jefes ADMIN** (un ADMIN no puede invitar otro ADMIN). DTOs `CambiarRolEmpleadoRequest`,
    `CambiarEstadoEmpleadoRequest`. Auditoría `CAMBIO_ROL_EMPLEADO` / `DESACTIVAR_EMPLEADO`.
  - **Login bloquea empleados desactivados:** `iniciarSesion` rechaza con 403 si `esUsuarioEmpleado && !activoComoEmpleado`
    (mitigación inmediata; la revocación de tokens en vuelo es el endurecimiento de sesión pendiente).
  - **Frontend:** pantalla `features/equipo` (listar, invitar, cambiar rol, activar/desactivar) con UI adaptada al
    rol (un jefe no ve la opción ADMIN ni acciones sobre otros ADMIN); helper `decodeJwtClaims` en `jwt.utils`;
    métodos de API (`getEmpleados`/`invitarEmpleado`/`cambiarRolEmpleado`/`cambiarEstadoEmpleado`); ruta `/equipo`
    (authGuard) + enlace en "Mis granjas" visible solo para dueño/jefe.
- **Tests:** `EmpleadoServiceTest` ampliado a 15 (invitar por ADMIN, jefe no crea/designa ADMIN, jefe no toca otro
  ADMIN, no auto-modificación, cambiar rol/estado, aislamiento de tenant 404); `CredencialesUsuarioServiceTest`
  +1 (empleado desactivado → 403). Suite unitaria **169/169 verde** (antes 158). Frontend `ng build` OK.
- **Archivos principales:** `empleados/**` (service/controller/dtos), `config/SecurityConfig.java`,
  `usuarios/service/CredencialesUsuarioService.java`, `usuarios/repository/UsuarioRepository.java`;
  frontend `features/equipo/**`, `features/mis-plantas`, `core/auth/jwt.utils.ts`, `data/api/reforma-api.service.ts`,
  `data/models/usuario.model.ts`, `app.routes.ts`.
- **Pendiente:** smoke en vivo del flujo de equipo; **endurecimiento de sesión** (revocación de JWT en vuelo al
  desactivar/bajar de rol — `tokenVersion`/`jti`+Redis); rate-limit/lockout; cookie HttpOnly para el JWT; Etapa 5
  (consola de auditoría). Confirmar si STARTER debe permitir >1 planta (hoy `limiteGranjas`: DEMO/STARTER 1, BUSINESS 3, ENTERPRISE ∞).

### 2026-06-15 — Módulo Usuarios · Etapas 2+3: invitación y cuentas de empleado
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** onboarding de empleados por invitación (token de un solo uso) + multi-tenancy efectiva
  (un empleado opera sobre las granjas de su dueño) + autorización por rol. **Sin migración** (la
  tabla `t_usuarios` de V001 ya modelaba el vínculo dueño↔empleado).
  - **Invitación (Etapa 2):** nuevo paquete `empleados/` (`EmpleadoService`, `EmpleadoRestController`,
    DTOs `InvitarEmpleadoRequest`/`AceptarInvitacionRequest`/`EmpleadoResponse`).
    `POST /api/empleados` (solo dueño, `ROLE_OWNER`) crea un empleado **pendiente** (`passwordHash=null`,
    `activoComoEmpleado=false`, snapshot no-nulo de plan/maxGranjas heredado del dueño), aplica
    plan-gating (`PlanService.limiteEmpleados`, valores **provisionales** DEMO 0 / STARTER 2 /
    BUSINESS 10 / ENTERPRISE ∞ — `// TODO ajustar`), emite token `INVITACION_EMPLEADO` (72 h) y
    envía email. Email ya existente en cualquier rol → 409 (cuentas separadas, decisión §0).
    Auditoría CREAR_EMPLEADO. Nuevo `EmailNotificacionService.enviarInvitacionEmpleado` (log del enlace
    `/auth/aceptar-invitacion?token=`).
  - **Aceptación + cuentas (Etapa 3):** `POST /api/empleados/aceptar` (público) consume el token, fija
    la contraseña (bcrypt) y activa al empleado (`emailVerificado=true`, `activoComoEmpleado=true`,
    `fechaVinculacion`). Auditoría ACEPTAR_INVITACION.
  - **JWT enriquecido:** claims `esEmpleado`/`rolEmpleado`/`idDueno` en `TokenJwtServicio`;
    `JwtUserPrincipal` expone `tenantId()` y authorities `ROLE_OWNER` (dueño) / `ROLE_ADMIN|EDITOR|LECTOR`
    (empleado). Compatibilidad con tokens viejos (claims ausentes → no-empleado).
  - **Multi-tenancy:** nuevo `SecurityUtils.requireTenantId()` (dueño efectivo). Los 8 controllers
    granja-scoped (granjas, materias-primas, proveedores, animales, compras, formulas, fabricaciones,
    inventario) ahora scopean por tenant, no por usuario → un empleado accede a las granjas del dueño.
    Se cerró el `TODO` de `GranjaAccesoService` (el tenant se resuelve en el borde del controller).
  - **Autorización por rol:** en `SecurityConfig`, toda mutación (POST/PUT/PATCH/DELETE) granja-scoped
    exige `ROLE_OWNER|ADMIN|EDITOR` → un `LECTOR` recibe 403 en escrituras (lectura y export CSV libres).
  - **Frontend:** `features/auth/aceptar-invitacion` (molde de `restablecer`), ruta + método
    `aceptarInvitacion` en `reforma-api.service.ts`, tipo `EmpleadoResponse`, aviso `?invitacion=ok` en login.
- **Tests:** nuevo `EmpleadoServiceTest` (invitar OK / 409 dup / 403 sobre límite / 403 invitador-empleado /
  aceptar OK / token inválido), `PlanServiceTest` cubre `limiteEmpleados`, 5 tests de controller
  actualizados al nuevo `JwtUserPrincipal`. Suite unitaria **158/158 verde** (antes 152). Frontend `ng build` OK.
- **Archivos principales:** `empleados/**`, `suscripciones/service/PlanService.java`,
  `usuarios/repository/UsuarioRepository.java`, `usuarios/email/**`, `auth/jwt/{TokenJwtServicio,JwtUserPrincipal}.java`,
  `auth/SecurityUtils.java`, `config/SecurityConfig.java`, `granjas/service/GranjaAccesoService.java`,
  los 8 `*RestController` granja-scoped; frontend `features/auth/aceptar-invitacion/**`, `app.routes.ts`,
  `data/api/reforma-api.service.ts`, `data/models/usuario.model.ts`, `features/auth/login`.
- **Pendiente:** smoke en vivo end-to-end (login dueño → invitar → aceptar → login empleado → EDITOR opera /
  LECTOR 403). Etapa 4 (gestión por el jefe ADMIN: `GET/PUT /api/empleados`, cambio de rol, baja lógica;
  delegar invitación a jefes; revocación de sesión al desactivar). Confirmar valores de `limiteEmpleados`.

### 2026-06-14 — Módulo Usuarios · Etapa 1: verificación de email + recuperación de contraseña
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** ciclo de vida del dueño completo (registro → verificación → recuperación), backend + frontend.
  - **BD:** `V010__token_seguridad.sql` → `t_token_seguridad` (token de un solo uso; se guarda solo
    el hash SHA-256; expiración + `consumido_en`).
  - **Tokens:** paquete `usuarios/token` (`TipoToken`, `TokenSeguridad`, repo con `invalidarVigentes`,
    `TokenSeguridadService.emitir/validarYConsumir`) + `common/util/TokenHasher` (token aleatorio 256-bit + SHA-256 hex).
  - **Email:** `EmailNotificacionService` (interfaz) + `LogEmailNotificacionService` (default,
    escribe el enlace en el log con `reforma.frontendUrl`). SMTP real queda pendiente (activable con `reforma.email.mode=smtp`).
  - **Servicios/endpoints:** registro ahora emite token + envía email de verificación
    (`emailEnviado=true`, se quitó el `token_verificacion` en claro de la entidad). Nuevo
    `RecuperacionCuentaService` + endpoints `POST /api/usuarios/{verificar-email, reenviar-verificacion,
    solicitar-reset, confirmar-reset}` (los dos de reset agregados a `PUBLIC`). `reenviar`/`solicitar`
    devuelven respuesta genérica (anti-enumeración); `confirmar-reset` además verifica el email.
    Auditoría: VERIFICACION_EMAIL / REENVIO_VERIFICACION / SOLICITUD_RESET / RESET_PASSWORD.
  - **Frontend:** `features/auth/` → `registro`, `verificar-email` (deep link), `reenviar-verificacion`,
    `olvide-password`, `restablecer`; login con enlaces y aviso `?reset=ok`. Rutas + métodos de API.
- **Tests:** `TokenHasherTest`, `TokenSeguridadServiceTest`, `RecuperacionCuentaServiceTest` +
  `CredencialesUsuarioServiceTest` actualizado. Suite unitaria **152/152 verde** (antes 138).
- **Verificación en vivo:** rebuild de `api-domain` (Flyway aplicó V010, Hibernate validate OK) y
  smoke end-to-end por HTTP: registro → login bloqueado (403) → verificación → login OK → solicitar
  reset → confirmar reset → login con nueva clave OK; clave vieja 401; reuso de token 400; tokens
  marcados consumidos. Web reconstruida (Angular build OK) y deep link `/auth/verificar-email` sirve la SPA (200).
- **Archivos principales:** `db/migration/V010__token_seguridad.sql`, `usuarios/token/**`,
  `usuarios/email/**`, `usuarios/service/RecuperacionCuentaService.java`, `usuarios/dto/*Reset*|*Verif*`,
  `usuarios/controller/UsuarioAuthRestController.java`, `config/SecurityConfig.java`,
  `common/util/TokenHasher.java`; frontend `apps/web/src/app/features/auth/**`, `app.routes.ts`,
  `data/api/reforma-api.service.ts`.
- **Pendiente:** envío SMTP real; endurecimiento Etapa 0 (rate-limit/lockout + `jti`/revocación Redis);
  `X-Forwarded-For` en nginx para IP de auditoría real. **Sigue:** Etapa 2+3 (invitación y cuentas empleado).

### 2026-06-14 — Módulo Usuarios · Etapa 0: infraestructura de auditoría
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** primera etapa del módulo de usuarios (`docs/MODULO_USUARIOS.md`). Se implementó la
  pista de auditoría sobre la tabla `t_auditoria` (ya existente en V001, antes sin uso):
  - Nuevo paquete `auditoria`: `domain/AccionAuditoria` (enum, acciones de Etapas 0–4),
    `entity/Auditoria` (mapea `t_auditoria`, JSONB vía `@JdbcTypeCode(SqlTypes.JSON)`),
    `repository/AuditoriaRepository`, `dto/AuditoriaEvento` (builder) y `service/AuditoriaService`.
  - `AuditoriaService` captura IP (`X-Forwarded-For`→`remoteAddr`) y User-Agent del request
    (best-effort, nulos fuera de contexto web) y serializa datos a JSON sin romper la operación
    de negocio. Dos semánticas: `registrar` (REQUIRED, se une a la tx del alta) y
    `registrarIndependiente` (REQUIRES_NEW, para eventos en flujos que hacen rollback, p. ej. login fallido).
  - Cableado en `CredencialesUsuarioService`: `REGISTRO` (tras `saveAndFlush` para satisfacer la
    FK `id_usuario`), `LOGIN` (login ahora es `@Transactional` read-write: además persiste
    `ultimoAcceso`, antes silenciosamente descartado por `readOnly`), y `LOGIN_FALLIDO` para
    contraseña inválida / cuenta desactivada / email no verificado (no se audita email inexistente
    porque no hay usuario referenciable por la FK).
- **Tests:** `AuditoriaServiceTest` (2) y `CredencialesUsuarioServiceTest` (6, nuevo). Suite
  unitaria completa **138/138 verde** (antes 130). Corridos vía `maven:3.9-eclipse-temurin-21-alpine`
  (no hay `mvn` local; patrón de `scripts/test-domain.ps1`).
- **Archivos principales:** `services/api-domain/src/main/java/com/reforma/domain/auditoria/**`,
  `usuarios/service/CredencialesUsuarioService.java`; tests en `auditoria/service/` y `usuarios/service/`.
- **Pendiente (resto de Etapa 0, ver `MODULO_USUARIOS.md §4`):** hash de tokens, respuestas
  anti-enumeración, `jti`/revocación en Redis y rate-limiting/lockout — se abordan junto a sus
  flujos consumidores en Etapa 1. La auditoría por `@Aspect` se difiere: hoy se invoca explícito
  desde el servicio de usuarios. **Sigue:** Etapa 1 (verificación de email + recuperación de contraseña).

### 2026-06-14 — Plan y traspaso del módulo Gestión de Usuarios
- **Autor/agente:** Claude Code (Opus 4.8)
- **Qué:** lectura del estado actual de auth/usuarios y **propuesta por etapas (0–5)** para el
  módulo completo: verificación de email, recuperación de contraseña, invitación de empleados,
  cuentas jefe, autorización por rol y consola de auditoría. **Sin implementación todavía.**
  Decisiones tomadas con el usuario: onboarding de empleados **por invitación email** y
  **cuentas separadas** (un email es dueño O empleado, nunca ambos).
- **Archivos principales:** nuevo [`docs/MODULO_USUARIOS.md`](MODULO_USUARIOS.md) (plan completo,
  archivos a leer, sketch de `V010 t_token_seguridad`, próximo paso). Sin cambios de código.
- **Pendiente:** implementar Etapa 0 + 1 (auditoría base + verificación de email, que hoy bloquea
  todo login real fuera del seed). Ver `MODULO_USUARIOS.md §6`.

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
