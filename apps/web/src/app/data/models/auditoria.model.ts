export type AccionAuditoria =
  | 'REGISTRO'
  | 'LOGIN'
  // LOGIN_FALLIDO ya no se genera (no se almacenan logins fallidos); se conserva en el tipo
  // por compatibilidad con datos históricos, pero no se ofrece como filtro.
  | 'LOGIN_FALLIDO'
  | 'VERIFICACION_EMAIL'
  | 'REENVIO_VERIFICACION'
  | 'SOLICITUD_RESET'
  | 'RESET_PASSWORD'
  | 'CREAR_EMPLEADO'
  | 'ACEPTAR_INVITACION'
  | 'CAMBIO_ROL_EMPLEADO'
  | 'DESACTIVAR_EMPLEADO';

/** Espejo del enum backend {@code AccionAuditoria}; se usa para el filtro de la consola. */
export const ACCIONES_AUDITORIA: AccionAuditoria[] = [
  'REGISTRO',
  'LOGIN',
  'VERIFICACION_EMAIL',
  'REENVIO_VERIFICACION',
  'SOLICITUD_RESET',
  'RESET_PASSWORD',
  'CREAR_EMPLEADO',
  'ACEPTAR_INVITACION',
  'CAMBIO_ROL_EMPLEADO',
  'DESACTIVAR_EMPLEADO',
];

export interface AuditoriaRegistro {
  id: string;
  idUsuario: string;
  actorEmail: string | null;
  actorNombre: string | null;
  idGranja: string | null;
  tablaOrigen: string;
  idRegistro: string;
  accion: AccionAuditoria;
  descripcion: string | null;
  datosAnteriores: string | null;
  datosNuevos: string | null;
  fechaOperacion: string;
  ipAddress: string | null;
  userAgent: string | null;
}

export interface PaginaAuditoria {
  contenido: AuditoriaRegistro[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface FiltrosAuditoria {
  idGranja?: string;
  idUsuario?: string;
  accion?: AccionAuditoria;
  desde?: string;
  hasta?: string;
  pagina?: number;
  tamano?: number;
}
