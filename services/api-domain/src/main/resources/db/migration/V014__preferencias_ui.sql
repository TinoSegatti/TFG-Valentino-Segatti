-- Módulo Personalización (RD-C1): preferencias de UI por usuario (fondo + intensidad de
-- cortina), en JSONB sobre t_usuarios — sin tabla nueva. NULL = usuario sin personalizar
-- (la UI aplica el default).
ALTER TABLE t_usuarios ADD COLUMN preferencias_ui JSONB;
