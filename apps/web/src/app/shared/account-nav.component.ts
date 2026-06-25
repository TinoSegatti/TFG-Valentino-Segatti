import { Component, computed, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthStateService } from '../core/auth/auth-state.service';
import { decodeJwtClaims } from '../core/auth/jwt.utils';

/**
 * Navbar global de la cuenta. Es el ÚNICO navbar de la app: se monta tanto
 * en el shell de "Mis granjas" / Perfil / Equipo / Auditoría como dentro del
 * shell de granja (con un back-link y el nombre de la granja activa).
 *
 * Inputs opcionales:
 *  - back: link de regreso contextual (ej. volver a "Mis granjas" desde una granja).
 *  - contexto: etiqueta a la derecha del navbar (ej. nombre de la granja activa).
 */
export interface AccountNavBack {
  label: string;
  to: string | string[];
}

@Component({
  selector: 'app-account-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="bar glass-surface">
      <a routerLink="/mis-plantas" class="brand">REFORMA</a>

      <nav>
        <a routerLink="/mis-plantas" routerLinkActive="active">
          <i class="pi pi-building"></i><span>Mis granjas</span>
        </a>
        <a routerLink="/perfil" routerLinkActive="active">
          <i class="pi pi-user"></i><span>Perfil</span>
        </a>
        @if (puedeGestionarEquipo()) {
          <a routerLink="/equipo" routerLinkActive="active">
            <i class="pi pi-users"></i><span>Equipo</span>
          </a>
          <a routerLink="/auditoria" routerLinkActive="active">
            <i class="pi pi-history"></i><span>Auditoría</span>
          </a>
        }
      </nav>

      <button class="icon-btn notif" type="button" title="Notificaciones" aria-label="Notificaciones">
        <i class="pi pi-bell"></i>
        <span class="pulse"></span>
      </button>

      @if (contexto()) {
        <span class="contexto" [title]="contexto()!">{{ contexto() }}</span>
      }

      <button class="reforma-btn-ghost" type="button" (click)="logout()">
        <i class="pi pi-sign-out"></i> Salir
      </button>
    </header>
  `,
  styles: [
    `
      .bar {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 0.8rem 1.5rem;
        border-left: none;
        border-right: none;
        border-top: none;
        position: sticky;
        top: 0;
        z-index: 20;
        flex-wrap: wrap;
      }
      .brand {
        font-weight: 800;
        letter-spacing: 0.16em;
        color: var(--reforma-accent);
        text-decoration: none;
      }
      nav {
        display: flex;
        gap: 0.35rem;
        align-items: center;
        flex: 1;
        flex-wrap: wrap;
      }
      nav a {
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
        padding: 0.45rem 0.8rem;
        border-radius: 10px;
        border: 1px solid transparent;
        color: var(--reforma-text-dim);
        text-decoration: none;
        font-size: 0.92rem;
        transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
      }
      nav a:hover {
        background: var(--glass-bg-hover);
        color: var(--reforma-text);
      }
      nav a.active {
        background: var(--reforma-accent-soft);
        border-color: rgba(157, 119, 244, 0.35);
        color: #ede9fe;
      }
      .icon-btn {
        position: relative;
        display: inline-grid;
        place-items: center;
        width: 40px;
        height: 40px;
        border-radius: 12px;
        color: var(--reforma-text-dim);
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        cursor: pointer;
        transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
      }
      .icon-btn:hover {
        color: var(--reforma-accent);
        border-color: var(--glass-border-strong);
        background: var(--glass-bg-hover);
      }
      .notif .pulse {
        position: absolute;
        top: 9px;
        right: 9px;
        width: 7px;
        height: 7px;
        border-radius: 999px;
        background: #fb7185;
        animation: rf-pulse 2s ease-in-out infinite;
      }
      .contexto {
        max-width: 22ch;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: var(--reforma-text);
        font-weight: 600;
        padding: 0.35rem 0.7rem;
        background: var(--glass-bg-strong);
        border: 1px solid var(--glass-border);
        border-radius: 999px;
        font-size: 0.85rem;
      }
    `,
  ],
})
export class AccountNavComponent {
  private readonly auth = inject(AuthStateService);

  readonly back = input<AccountNavBack | null>(null);
  readonly contexto = input<string | null>(null);

  readonly puedeGestionarEquipo = computed(() => {
    const claims = decodeJwtClaims(this.auth.getToken());
    return !claims?.esEmpleado || claims.rolEmpleado === 'ADMIN';
  });

  logout(): void {
    this.auth.clearSession();
    window.location.href = '/auth/login';
  }
}
