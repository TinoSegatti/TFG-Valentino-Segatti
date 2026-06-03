# ADR 0005 — Soft-delete, reutilización de códigos y snapshots en transacciones

## Estado

Aceptado — 2026-06-02

## Contexto

Cuando una entidad de catálogo (materia prima, proveedor, animal, fórmula…) deja de operar, el usuario espera "darla de baja" y, eventualmente, poder reutilizar su **código** comercial para una entidad nueva (ej. el código `MAIZ` que pertenecía al maíz molido se reasigna al maíz pelletizado).

La implementación inicial (Sprint 2) usaba un `UNIQUE (id_granja, codigo)` total y rechazaba con 409 cualquier alta cuyo código ya hubiese existido, incluso si la fila estaba con `activa = false`. El usuario quedaba bloqueado.

La primera reacción fue **reactivar la fila inactiva pisando sus datos**. Funciona pero corrompe el pasado: las compras, fabricaciones, alertas ML y reportes históricos que apuntan a esa fila quedan asociadas a un producto distinto del que originalmente registraron, falsificando los KPIs y envenenando las series temporales que consume el módulo `api-ml` (RF-IA-*).

## Decisión

Adoptamos una política de **soft-delete + entidades versionadas**, complementada con **snapshots inmutables** en las tablas transaccionales.

### 1. Soft-delete

- Toda entidad de catálogo lleva un flag booleano (`activa`, `activo`) y nunca se elimina físicamente desde la API.
- El listado por defecto filtra por `WHERE <flag> = TRUE`.

### 2. Unicidad solo entre activos

- La constraint `UNIQUE (id_granja, codigo)` se sustituye por un **índice parcial UNIQUE** sobre `WHERE <flag> = TRUE` (Postgres soporta esto nativamente).
- Definido en `V003__unique_solo_activos.sql` para `t_materia_prima` y `t_proveedor`. Para cada entidad de catálogo que se cree en sprints futuros (animales, fórmulas, etc.) hay que crear el mismo tipo de índice parcial desde la migración inicial.

### 3. Crear con código reutilizado = INSERT, no UPDATE

- `Service.crear()` verifica colisión solo entre **activos**. Si no hay activo con ese código, **inserta una fila nueva** con `id` distinto generado por `IdGenerator.newId()`, independientemente de cuántas filas inactivas existan con el mismo código.
- La fila inactiva original queda **intacta** (datos congelados) y sus referencias históricas siguen siendo válidas y semánticamente coherentes.

### 4. Snapshots inmutables en transacciones

- Toda tabla transaccional que referencie una entidad de catálogo (`t_compra_detalle`, `t_fabricacion_detalle`, `t_inventario_movimiento`…) debe **copiar al momento de la transacción** los atributos relevantes para reproducir el documento años después:
  - Ej. en `t_compra_detalle`: `id_materia_prima` (FK) **y** `nombre_mp_snapshot`, `codigo_mp_snapshot`, `precio_unitario_compra`.
- Esto cumple el principio contable de **inmutabilidad del documento emitido** y nos protege incluso si en el futuro alguien decidiera permitir cambios "destructivos" en el catálogo (renombrar, fusionar).
- Implementación: definir esos campos como `NOT NULL` en cada migración nueva; popularlos en el `Service.crear()` de la transacción correspondiente; nunca actualizarlos.

## Consecuencias

### Positivas

- Integridad histórica garantizada: ninguna escritura en catálogo deforma reportes pasados.
- `api-ml` recibe series temporales limpias por `id` de catálogo.
- Auditoría y trazabilidad mantenidas sin esfuerzo adicional.
- El usuario puede reutilizar códigos sin restricción operativa.

### Negativas / costos

- Cada módulo nuevo de catálogo debe crear su índice parcial desde la primera migración.
- Cada módulo transaccional debe agregar los campos snapshot y disciplinar al equipo a popularlos.
- Reportes "por código histórico" (si se piden) deben hacer `GROUP BY codigo` y aceptar que cada bucket suma varias filas distintas.
- Si un usuario crea un código por error, se baja y se vuelve a crear, queda una fila inactiva "fantasma" en la BD. Es aceptable.

## Alternativas evaluadas y descartadas

1. **Reactivar y pisar** (lo implementado intermedio). Descartada: contamina histórico y ML.
2. **Renombrar al desactivar** (`MAIZ` → `MAIZ_archivado_<timestamp>`). Descartada: ensucia los códigos en reportes históricos, conceptualmente equivalente pero peor UX.
3. **Hard delete con `ON DELETE RESTRICT`**. Descartada: rompe la operación si hay referencias, además impide reusar el código.

## Referencias

- Migración: `services/api-domain/src/main/resources/db/migration/V003__unique_solo_activos.sql`
- Implementación: `MateriaPrimaService.crear`, `ProveedorService.crear`
- Tests: `MateriaPrimaServiceTest#crear_reusaCodigoDeBajaLogica`, `ProveedorServiceTest#crear_reusaCodigoDeBajaLogica`
