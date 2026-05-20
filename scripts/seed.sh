#!/usr/bin/env bash
# Seed mínimo §11.6.1 — requiere Postgres en localhost:5432
set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${POSTGRES_USER:-reforma}"
PGPASSWORD="${POSTGRES_PASSWORD:-change_me_local}"
PGDATABASE="${POSTGRES_DB:-reforma}"
export PGPASSWORD

echo "Aplicando seed en ${PGDATABASE}@${PGHOST}..."

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 <<'SQL'
-- Password: Demo1234! (bcrypt cost 12 — generar en prod con Spring)
INSERT INTO t_usuarios (id, email, password_hash, nombre_usuario, apellido_usuario,
  tipo_usuario, plan_suscripcion, max_granjas, activo, email_verificado,
  es_usuario_empleado, activo_como_empleado, fecha_registro)
VALUES (
  'u_demo', 'demo@reforma.local',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.G2oX9K5Y5Y5Y5Yu',
  'Demo', 'Reforma', 'CLIENTE', 'BUSINESS', 3, true, true, false, false, NOW()
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO t_granja (id, id_usuario, nombre_granja, descripcion, activa, fecha_creacion)
VALUES ('g_demo', 'u_demo', 'Granja Demo', 'Granja para pruebas locales', true, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_materia_prima (id, id_granja, codigo_materia_prima, nombre_materia_prima, precio_por_kilo, activa) VALUES
('mp_maiz', 'g_demo', 'MAIZ', 'Maíz molido', 35.50, true),
('mp_soja', 'g_demo', 'SOJA', 'Harina de soja', 52.30, true),
('mp_vit', 'g_demo', 'VITA', 'Premezcla vitamínica', 280.00, true)
ON CONFLICT (id) DO NOTHING;
SQL

echo "Seed SQL aplicado (catálogos opcionales)."
echo "Usuario demo: se crea automáticamente al arrancar api-domain con perfil dev (demo@reforma.local / Demo1234!)."
