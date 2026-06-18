export type TipoUsuario = 'CLIENTE' | 'ADMINISTRADOR';
export type PlanSuscripcion = 'DEMO' | 'STARTER' | 'BUSINESS' | 'ENTERPRISE';

export interface Usuario {
  id: string;
  email: string;
  nombreUsuario: string;
  apellidoUsuario: string;
  tipoUsuario: TipoUsuario;
  planSuscripcion: PlanSuscripcion;
  maxGranjas: number;
  emailVerificado: boolean;
}

export interface AuthResponse {
  usuario: Usuario;
  token: string;
}

export type RolEmpleado = 'ADMIN' | 'EDITOR' | 'LECTOR';

/** Perfil del usuario autenticado (GET /api/usuarios/perfil). rol = OWNER | ADMIN | EDITOR | LECTOR. */
export interface Perfil {
  id: string;
  email: string;
  nombre: string;
  apellido: string;
  rol: 'OWNER' | RolEmpleado;
  esEmpleado: boolean;
  plan: PlanSuscripcion;
  idDueno: string | null;
  permisos: string[];
}

export interface EmpleadoResponse {
  id: string;
  email: string;
  nombreUsuario: string;
  apellidoUsuario: string;
  rolEmpleado: RolEmpleado;
  emailVerificado: boolean;
  activoComoEmpleado: boolean;
  fechaVinculacion: string | null;
}
