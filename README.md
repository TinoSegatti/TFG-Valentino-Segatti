# REFORMA — ERP SaaS para granjas porcinas

Trabajo Final de Grado — **Valentino Segatti** (Legajo SOF01992), Universidad Siglo 21.

Plataforma **multi-tenant** para la gestión integral de granjas porcinas: catálogos,
compras de materias primas, fórmulas de alimento, inventario con precio ponderado,
informes, suscripciones con Mercado Pago y módulos de IA (predicción de agotamiento
de stock y detección de anomalías de precio).

---

## Stack

| Capa | Tecnología | Puerto dev |
|------|------------|------------|
| Frontend | Angular 19 (standalone, signals) | 4200 |
| API de dominio | Spring Boot 3.4, Java 21, JPA, Flyway, JWT | 8080 |
| API de ML | FastAPI, Python 3.12, scikit-learn | 8081 |
| Base de datos | PostgreSQL 16 | 5432 |
| Cache | Redis 7 | 6379 |

Flujo: navegador → Angular → `api-domain` (REST + JWT) → PostgreSQL.
`api-domain` llama a `api-ml` con JWT servicio-a-servicio (ADR 0004).

## Puesta en marcha

Requisitos: Docker Desktop y `make`.

```bash
cp .env.example .env    # completar secrets (JWT, SMTP, Mercado Pago)
make dev                # levanta postgres, redis, api-domain, api-ml y web
make seed               # datos demo
```

| URL | Servicio |
|-----|----------|
| http://localhost:4200 | Aplicación web |
| http://localhost:8080/swagger-ui.html | API de dominio (OpenAPI) |
| http://localhost:8081/docs | API de ML |

Usuario demo (perfil `dev`): `demo@reforma.local` / `Demo1234!`

Otros comandos: `make down`, `make logs`, `make ps`, `make migrate`, `make reset-db`
(destructivo). `make help` los lista todos.

## Tests

```bash
make test-domain      # unitarios api-domain (Maven en Docker)
make test-domain-it   # integración con Postgres Testcontainers
make test-ml          # pytest api-ml
```

CI por servicio en `.github/workflows/` (`api-domain-ci`, `api-ml-ci`, `web-ci`).

## Estructura

```text
.
├── services/
│   ├── api-domain/   Spring Boot: auth, catálogos, compras, fórmulas,
│   │                 inventario, archivos, informes, pagos, auditoría
│   └── api-ml/       FastAPI: anomalías de precio y predicción de stock
├── apps/web/         Angular 19
├── infra/docker/     init.sql de Postgres (pgcrypto, schema ml)
├── scripts/          seed.sh, reset-db.sh, test-domain.ps1
├── docs/             planes de módulo, ADRs y registro de cambios
├── docker-compose.yml
└── Makefile
```

## Módulos

- **Usuarios y seguridad** — registro con verificación de email, recuperación de
  contraseña, JWT con revocación de sesión, empleados por granja, auditoría.
- **Catálogos** — materias primas, proveedores y animales, con import/export CSV.
- **Compras** — cabecera y detalle, sincronización de precios e historial.
- **Fórmulas** — receta de 1000 kg, costeo automático al cambiar precios, CSV.
- **Inventario** — cantidad sistema vs. real, merma, valor de stock y precio de
  almacén por promedio ponderado.
- **Archivos** — snapshots inmutables de inventario, compras y fórmulas.
- **Informe de estado** — reporte por período con gráficos, export CSV y HTML.
- **IA** — anomalías de precio de compra (Z-Score estacional) y predicción de
  agotamiento de stock (BUSINESS/ENTERPRISE).
- **Pagos** — suscripciones con Mercado Pago: checkout, webhook, ciclo de vida,
  job de vencimientos y período de gracia.
- **Personalización** — tema y fondo por usuario.

Cada módulo aplica los límites del plan de la cuenta (DEMO / STARTER / BUSINESS /
ENTERPRISE) en el servidor, no solo en la UI.

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [`docs/REGISTRO_CAMBIOS.md`](docs/REGISTRO_CAMBIOS.md) | Historial cronológico de cambios |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura (stack, ML, JWT s2s, soft delete, IDs, UI) |

Los planes de módulo, los límites por plan y las guías de prueba manual son documentos
de trabajo internos: viven en `docs/` pero no se publican en el repositorio.
