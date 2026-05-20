-- REFORMA — esquema inicial (§3.2 ESPECIFICACION_TECNICA)
-- Convención: tablas t_*, ids VARCHAR(32), timestamps TIMESTAMPTZ

CREATE TABLE t_usuarios (
    id                      VARCHAR(32) PRIMARY KEY,
    email                   VARCHAR(200) NOT NULL UNIQUE,
    password_hash           VARCHAR(100),
    google_id               VARCHAR(100) UNIQUE,
    nombre_usuario          VARCHAR(100) NOT NULL,
    apellido_usuario        VARCHAR(100) NOT NULL,
    tipo_usuario            VARCHAR(20) NOT NULL DEFAULT 'CLIENTE',
    plan_suscripcion        VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    max_granjas             INTEGER NOT NULL DEFAULT 1,
    activo                  BOOLEAN NOT NULL DEFAULT TRUE,
    email_verificado        BOOLEAN NOT NULL DEFAULT FALSE,
    token_verificacion      VARCHAR(100) UNIQUE,
    fecha_expiracion_token  TIMESTAMPTZ,
    fecha_registro          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ultimo_acceso           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    codigo_referencia       VARCHAR(50) UNIQUE,
    fecha_generacion_codigo TIMESTAMPTZ,
    codigo_expiracion       TIMESTAMPTZ,
    es_usuario_empleado     BOOLEAN NOT NULL DEFAULT FALSE,
    id_usuario_dueno        VARCHAR(32) REFERENCES t_usuarios(id),
    fecha_vinculacion       TIMESTAMPTZ,
    rol_empleado            VARCHAR(20),
    activo_como_empleado    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_usuarios_dueno ON t_usuarios(id_usuario_dueno);

CREATE TABLE t_granja (
    id              VARCHAR(32) PRIMARY KEY,
    id_usuario      VARCHAR(32) NOT NULL REFERENCES t_usuarios(id) ON DELETE CASCADE,
    nombre_granja   VARCHAR(200) NOT NULL,
    descripcion     TEXT,
    fecha_creacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activa          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_granja_usuario ON t_granja(id_usuario);

CREATE TABLE t_materia_prima (
    id                          VARCHAR(32) PRIMARY KEY,
    id_granja                   VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    codigo_materia_prima        VARCHAR(50) NOT NULL,
    nombre_materia_prima        VARCHAR(200) NOT NULL,
    precio_por_kilo             DOUBLE PRECISION NOT NULL DEFAULT 0,
    activa                      BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_ultima_actualizacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id_granja, codigo_materia_prima)
);

CREATE TABLE t_proveedor (
    id                  VARCHAR(32) PRIMARY KEY,
    id_granja           VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    codigo_proveedor    VARCHAR(50) NOT NULL,
    nombre_proveedor    VARCHAR(200) NOT NULL,
    direccion           TEXT,
    localidad           VARCHAR(100),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id_granja, codigo_proveedor)
);

CREATE TABLE t_animal (
    id                  VARCHAR(32) PRIMARY KEY,
    id_granja           VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    codigo_animal       VARCHAR(50) NOT NULL,
    descripcion_animal  VARCHAR(200) NOT NULL,
    categoria_animal    VARCHAR(100),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id_granja, codigo_animal)
);

CREATE TABLE t_formula_cabecera (
    id                      VARCHAR(32) PRIMARY KEY,
    id_granja               VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    id_animal               VARCHAR(32) NOT NULL REFERENCES t_animal(id),
    codigo_formula          VARCHAR(50) NOT NULL,
    descripcion_formula     VARCHAR(200) NOT NULL,
    peso_total_formula      DOUBLE PRECISION NOT NULL DEFAULT 1000,
    costo_total_formula     DOUBLE PRECISION NOT NULL DEFAULT 0,
    activa                  BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (id_granja, codigo_formula)
);

CREATE TABLE t_formula_detalle (
    id                                  VARCHAR(32) PRIMARY KEY,
    id_formula                          VARCHAR(32) NOT NULL REFERENCES t_formula_cabecera(id) ON DELETE CASCADE,
    id_materia_prima                    VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id),
    cantidad_kg                         DOUBLE PRECISION NOT NULL,
    porcentaje_formula                  DOUBLE PRECISION NOT NULL,
    precio_unitario_momento_creacion    DOUBLE PRECISION NOT NULL DEFAULT 0,
    costo_parcial                       DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE t_compra_cabecera (
    id                  VARCHAR(32) PRIMARY KEY,
    id_granja           VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    id_usuario          VARCHAR(32) NOT NULL REFERENCES t_usuarios(id),
    id_proveedor        VARCHAR(32) NOT NULL REFERENCES t_proveedor(id),
    numero_factura      VARCHAR(100),
    fecha_compra        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    total_factura       DOUBLE PRECISION NOT NULL DEFAULT 0,
    observaciones       TEXT,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_eliminacion   TIMESTAMPTZ,
    eliminado_por       VARCHAR(32)
);

CREATE INDEX idx_compra_granja_fecha ON t_compra_cabecera(id_granja, fecha_compra);

CREATE TABLE t_compra_detalle (
    id                              VARCHAR(32) PRIMARY KEY,
    id_compra                       VARCHAR(32) NOT NULL REFERENCES t_compra_cabecera(id) ON DELETE CASCADE,
    id_materia_prima                VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id),
    cantidad_comprada               DOUBLE PRECISION NOT NULL,
    precio_unitario                 DOUBLE PRECISION NOT NULL,
    subtotal                        DOUBLE PRECISION NOT NULL,
    precio_anterior_materia_prima   DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE t_registro_precio (
    id                      VARCHAR(32) PRIMARY KEY,
    id_materia_prima        VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id) ON DELETE CASCADE,
    precio_anterior         DOUBLE PRECISION NOT NULL,
    precio_nuevo            DOUBLE PRECISION NOT NULL,
    diferencia_porcentual   DOUBLE PRECISION NOT NULL,
    fecha_cambio            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    motivo                  TEXT
);

CREATE TABLE t_fabricacion (
    id                      VARCHAR(32) PRIMARY KEY,
    id_granja               VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    id_usuario              VARCHAR(32) NOT NULL REFERENCES t_usuarios(id),
    id_formula              VARCHAR(32) NOT NULL REFERENCES t_formula_cabecera(id),
    descripcion_fabricacion VARCHAR(200),
    cantidad_fabricacion    DOUBLE PRECISION NOT NULL,
    costo_total_fabricacion DOUBLE PRECISION NOT NULL DEFAULT 0,
    costo_por_kilo          DOUBLE PRECISION NOT NULL DEFAULT 0,
    fecha_fabricacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    observaciones           TEXT,
    sin_existencias         BOOLEAN NOT NULL DEFAULT FALSE,
    activo                  BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_eliminacion       TIMESTAMPTZ,
    eliminado_por           VARCHAR(32)
);

CREATE TABLE t_detalle_fabricacion (
    id                  VARCHAR(32) PRIMARY KEY,
    id_fabricacion      VARCHAR(32) NOT NULL REFERENCES t_fabricacion(id) ON DELETE CASCADE,
    id_materia_prima    VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id),
    cantidad_usada      DOUBLE PRECISION NOT NULL,
    precio_unitario     DOUBLE PRECISION NOT NULL,
    costo_parcial       DOUBLE PRECISION NOT NULL
);

CREATE TABLE t_inventario (
    id                          VARCHAR(32) PRIMARY KEY,
    id_granja                   VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    id_materia_prima            VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id) ON DELETE CASCADE,
    cantidad_acumulada          DOUBLE PRECISION NOT NULL DEFAULT 0,
    cantidad_sistema            DOUBLE PRECISION NOT NULL DEFAULT 0,
    cantidad_real               DOUBLE PRECISION NOT NULL DEFAULT 0,
    merma                       DOUBLE PRECISION NOT NULL DEFAULT 0,
    precio_almacen              DOUBLE PRECISION NOT NULL DEFAULT 0,
    valor_stock                 DOUBLE PRECISION NOT NULL DEFAULT 0,
    version                     INTEGER NOT NULL DEFAULT 0,
    fecha_ultima_actualizacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    observaciones               TEXT,
    UNIQUE (id_granja, id_materia_prima)
);

CREATE TABLE t_inventario_inicial (
    id                  VARCHAR(32) PRIMARY KEY,
    id_granja           VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    id_materia_prima    VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id) ON DELETE CASCADE,
    cantidad_inicial    DOUBLE PRECISION NOT NULL,
    precio_inicial      DOUBLE PRECISION NOT NULL,
    fecha_registro      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE t_archivo_cabecera (
    id                  VARCHAR(32) PRIMARY KEY,
    id_granja           VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    tabla_origen        VARCHAR(50) NOT NULL,
    fecha_archivo       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    descripcion_archivo TEXT,
    total_registros     INTEGER NOT NULL DEFAULT 0,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE t_archivo_detalle (
    id          VARCHAR(32) PRIMARY KEY,
    id_archivo  VARCHAR(32) NOT NULL REFERENCES t_archivo_cabecera(id) ON DELETE CASCADE,
    datos_json  JSONB NOT NULL
);

CREATE TABLE t_auditoria (
    id                      VARCHAR(32) PRIMARY KEY,
    id_usuario              VARCHAR(32) NOT NULL REFERENCES t_usuarios(id),
    id_granja               VARCHAR(32) REFERENCES t_granja(id) ON DELETE SET NULL,
    tabla_origen            VARCHAR(50) NOT NULL,
    id_registro             VARCHAR(32) NOT NULL,
    accion                  VARCHAR(30) NOT NULL,
    descripcion             TEXT,
    datos_anteriores_json   JSONB,
    datos_nuevos_json       JSONB,
    fecha_operacion         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address              VARCHAR(45),
    user_agent              TEXT
);

CREATE INDEX idx_auditoria_granja_fecha ON t_auditoria(id_granja, fecha_operacion);

CREATE TABLE t_suscripciones (
    id                          VARCHAR(32) PRIMARY KEY,
    id_usuario                  VARCHAR(32) NOT NULL UNIQUE REFERENCES t_usuarios(id) ON DELETE CASCADE,
    plan_suscripcion            VARCHAR(20) NOT NULL,
    estado_suscripcion          VARCHAR(30) NOT NULL DEFAULT 'ACTIVA',
    periodo_facturacion         VARCHAR(20),
    fecha_inicio                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_fin                   TIMESTAMPTZ,
    fecha_proxima_renovacion    TIMESTAMPTZ,
    precio                      DOUBLE PRECISION NOT NULL DEFAULT 0,
    moneda                      VARCHAR(3) NOT NULL DEFAULT 'ARS',
    stripe_customer_id          VARCHAR(100),
    stripe_subscription_id      VARCHAR(100),
    mercado_pago_preapproval_id VARCHAR(100) UNIQUE,
    paypal_subscription_id      VARCHAR(100),
    referencia_transferencia    VARCHAR(200),
    confirmado_transferencia    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE t_pagos (
    id                      VARCHAR(32) PRIMARY KEY,
    id_suscripcion          VARCHAR(32) NOT NULL REFERENCES t_suscripciones(id) ON DELETE CASCADE,
    metodo_pago             VARCHAR(30) NOT NULL,
    monto                   DOUBLE PRECISION NOT NULL,
    moneda                  VARCHAR(3) NOT NULL DEFAULT 'ARS',
    estado_pago             VARCHAR(30) NOT NULL,
    mercado_pago_payment_id VARCHAR(100),
    stripe_payment_id       VARCHAR(100),
    fecha_pago              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tablas IA (dominio en public; schema ml para artefactos ML vía Alembic)
CREATE TABLE t_ia_prediccion_stock (
    id                  VARCHAR(32) PRIMARY KEY,
    id_granja           VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    id_materia_prima    VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id) ON DELETE CASCADE,
    fecha_agotamiento   DATE,
    dias_restantes      INTEGER,
    nivel_alerta        VARCHAR(20),
    modelo_usado        VARCHAR(50),
    calculado_en        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id_granja, id_materia_prima)
);

CREATE TABLE t_ia_anomalia_precio (
    id                      VARCHAR(32) PRIMARY KEY,
    id_compra               VARCHAR(32) REFERENCES t_compra_cabecera(id) ON DELETE SET NULL,
    id_materia_prima        VARCHAR(32) NOT NULL REFERENCES t_materia_prima(id),
    precio_ingresado        DOUBLE PRECISION NOT NULL,
    precio_promedio_historico DOUBLE PRECISION,
    z_score                 DOUBLE PRECISION,
    clasificacion           VARCHAR(30) NOT NULL,
    usuario_confirmo        BOOLEAN,
    detectado_en            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE t_alertas_ml (
    id              VARCHAR(32) PRIMARY KEY,
    id_granja       VARCHAR(32) NOT NULL REFERENCES t_granja(id) ON DELETE CASCADE,
    tipo_alerta     VARCHAR(50) NOT NULL,
    payload_json    JSONB NOT NULL,
    leida           BOOLEAN NOT NULL DEFAULT FALSE,
    creada_en       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
