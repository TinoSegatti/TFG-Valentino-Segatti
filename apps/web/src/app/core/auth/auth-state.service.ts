import { Injectable, signal, computed } from '@angular/core';
import { isJwtUsable } from './jwt.utils';

const TOKEN_KEY = 'reforma_jwt';

@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly tokenSignal = signal<string | null>(
    typeof localStorage !== 'undefined' ? localStorage.getItem(TOKEN_KEY) : null,
  );

  readonly isAuthenticated = computed(() => isJwtUsable(this.tokenSignal()));

  getToken(): string | null {
    return this.tokenSignal();
  }

  setSession(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.tokenSignal.set(token);
  }

  clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokenSignal.set(null);
    // Personalización: el fondo es del usuario, no del dispositivo — al salir se limpia
    // el cache y se restauran las custom properties para que el login vea el default.
    // (Directo acá, sin inyectar TemaService, para no acoplar auth → api.)
    localStorage.removeItem('reforma_prefs');
    document.documentElement.style.removeProperty('--app-fondo');
    document.documentElement.style.removeProperty('--app-cortina');
  }

  getAuthorizationHeader(): string | null {
    const t = this.tokenSignal();
    return isJwtUsable(t) ? `Bearer ${t}` : null;
  }
}
