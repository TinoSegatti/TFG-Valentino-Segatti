import { Injectable, signal, computed } from '@angular/core';

const TOKEN_KEY = 'reforma_jwt';

@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly tokenSignal = signal<string | null>(
    typeof localStorage !== 'undefined' ? localStorage.getItem(TOKEN_KEY) : null,
  );

  readonly isAuthenticated = computed(() => !!this.tokenSignal());

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
  }

  getAuthorizationHeader(): string | null {
    const t = this.tokenSignal();
    return t ? `Bearer ${t}` : null;
  }
}
