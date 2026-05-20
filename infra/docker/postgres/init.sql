-- Extensiones y schema ML (§9.1 ESPECIFICACION_TECNICA)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS ml;

-- Rol de solo lectura para api-ml (opcional en dev)
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'reforma_ml') THEN
    CREATE ROLE reforma_ml WITH LOGIN PASSWORD 'ml_pass';
  END IF;
END
$$;

GRANT USAGE ON SCHEMA ml TO reforma_ml;
ALTER DEFAULT PRIVILEGES IN SCHEMA ml GRANT SELECT ON TABLES TO reforma_ml;
