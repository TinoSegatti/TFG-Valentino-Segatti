-- Datos mínimos para tests de integración de catálogos (usuario + granja).
INSERT INTO t_usuarios (
    id, email, password_hash, nombre_usuario, apellido_usuario,
    tipo_usuario, plan_suscripcion, max_granjas, activo, email_verificado,
    es_usuario_empleado, activo_como_empleado, fecha_registro
) VALUES (
    'u_it', 'catalogo-it@test.local', 'noop',
    'IT', 'Catalogo', 'CLIENTE', 'ENTERPRISE', 10,
    true, true, false, false, NOW()
) ON CONFLICT (email) DO NOTHING;

INSERT INTO t_granja (id, id_usuario, nombre_granja, activa, fecha_creacion)
VALUES ('g_it', 'u_it', 'Granja IT', true, NOW())
ON CONFLICT (id) DO NOTHING;
