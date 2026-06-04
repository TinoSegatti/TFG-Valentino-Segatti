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
