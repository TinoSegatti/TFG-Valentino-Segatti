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
