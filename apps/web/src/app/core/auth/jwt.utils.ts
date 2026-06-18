export interface JwtClaims {
  email?: string;
  tipoUsuario?: string;
  planSuscripcion?: string;
  esEmpleado?: boolean;
  rolEmpleado?: 'ADMIN' | 'EDITOR' | 'LECTOR';
  idDueno?: string;
  exp?: number;
}

/** Decodifica el payload del JWT (sin verificar firma). Devuelve null si no es parseable. */
export function decodeJwtClaims(token: string | null | undefined): JwtClaims | null {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    return JSON.parse(atob(parts[1])) as JwtClaims;
  } catch {
    return null;
  }
}

/** Valida formato y expiración del JWT almacenado en el cliente (sin verificar firma). */
export function isJwtUsable(token: string | null | undefined): token is string {
  if (!token) {
    return false;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return false;
  }
  try {
    const payload = JSON.parse(atob(parts[1])) as { exp?: number };
    if (typeof payload.exp !== 'number') {
      return false;
    }
    return payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}
