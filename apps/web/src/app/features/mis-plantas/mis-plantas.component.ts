import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { decodeJwtClaims } from '../../core/auth/jwt.utils';
import { Granja } from '../../data/models/granja.model';
import { AccountNavComponent } from '../../shared/account-nav.component';

@Component({
  selector: 'app-mis-plantas',
  standalone: true,
  imports: [RouterLink, AccountNavComponent],
  template: `
    <div class="page">
      <app-account-nav />

      <div class="body">
        <h1 class="reforma-page-title">Mis granjas</h1>

        @if (loading()) {
          <p class="text-dim">Cargando…</p>
        } @else if (error()) {
          <p class="reforma-alert reforma-alert-error">{{ error() }}</p>
          <p><a routerLink="/auth/login">Volver a ingresar</a></p>
        } @else {
          <ul class="granjas">
            @for (g of granjas(); track g.id) {
              <li class="glass-card">
                <a [routerLink]="['/granja', g.id]">
                  <i class="pi pi-building"></i>
                  <span>{{ g.nombreGranja }}</span>
                  <i class="pi pi-arrow-right go"></i>
                </a>
              </li>
            } @empty {
              <li class="vacio text-dim">Todavía no tenés granjas. Creá la primera abajo.</li>
            }
          </ul>

          @if (esDueno()) {
            <form class="crear glass-card" (submit)="crearGranja($event)">
              <h2>Crear granja</h2>
              <div class="fila">
                <input
                  class="reforma-input"
                  type="text"
                  placeholder="Nombre de la granja (ej. Planta Norte)"
                  [value]="nombreNueva()"
                  (input)="nombreNueva.set($any($event.target).value)"
                  [disabled]="creando()"
                  maxlength="200"
                />
                <button class="reforma-btn" type="submit" [disabled]="creando() || !nombreNueva().trim()">
                  {{ creando() ? 'Creando…' : 'Crear granja' }}
                </button>
              </div>
              @if (errorCrear()) {
                <p class="reforma-alert reforma-alert-error">{{ errorCrear() }}</p>
              }
            </form>
          }
        }
      </div>
    </div>
  `,
  styles: [
    `
      .page {
        font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
      }
      .body {
        padding: 1.5rem;
        max-width: 60rem;
        margin: 0 auto;
      }
      .granjas {
        list-style: none;
        padding: 0;
        margin: 0 0 1.5rem;
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
        gap: 1rem;
      }
      .granjas li a {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 1.1rem 1.25rem;
        color: var(--reforma-text);
        text-decoration: none;
        font-weight: 600;
      }
      .granjas li a .go {
        margin-left: auto;
        color: var(--reforma-accent);
      }
      .granjas li.vacio {
        padding: 1rem;
        grid-column: 1 / -1;
        border: 1px dashed var(--glass-border-strong);
        border-radius: 12px;
      }
      .crear {
        padding: 1.25rem;
        width: 100%;
        box-sizing: border-box;
      }
      .crear h2 {
        font-size: 1rem;
        margin: 0 0 0.75rem;
        color: var(--reforma-text);
      }
      .crear .fila {
        display: flex;
        gap: 0.5rem;
      }
      .crear .fila .reforma-input {
        flex: 1;
      }
    `,
  ],
})
export class MisPlantasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);

  readonly granjas = signal<Granja[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Estado del formulario "Crear granja".
  readonly nombreNueva = signal('');
  readonly creando = signal(false);
  readonly errorCrear = signal<string | null>(null);

  /** Crear granjas es exclusivo del dueño (los empleados operan, no crean). */
  readonly esDueno = computed(() => !decodeJwtClaims(this.auth.getToken())?.esEmpleado);

  ngOnInit(): void {
    this.api.getGranjas().subscribe({
      next: (list) => {
        this.granjas.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        const status = err?.status;
        if (status === 401) {
          this.error.set('Sesion expirada o invalida. Volve a ingresar.');
        } else {
          this.error.set('No se pudieron cargar las granjas. Verificá que el backend esté en marcha.');
        }
        this.loading.set(false);
      },
    });
  }

  crearGranja(event: Event): void {
    event.preventDefault();
    const nombre = this.nombreNueva().trim();
    if (!nombre || this.creando()) {
      return;
    }
    this.creando.set(true);
    this.errorCrear.set(null);
    this.api.crearGranja(nombre).subscribe({
      next: (granja) => {
        this.granjas.update((list) => [...list, granja].sort((a, b) =>
          a.nombreGranja.localeCompare(b.nombreGranja)));
        this.nombreNueva.set('');
        this.creando.set(false);
      },
      error: (err) => {
        const status = err?.status;
        if (status === 403) {
          this.errorCrear.set(
            err?.error?.message ??
              'Alcanzaste el límite de granjas de tu plan. Mejorá el plan para crear más.',
          );
        } else if (status === 401) {
          this.errorCrear.set('Sesión expirada. Volvé a ingresar.');
        } else {
          this.errorCrear.set('No se pudo crear la granja. Intentá de nuevo.');
        }
        this.creando.set(false);
      },
    });
  }

  logout(): void {
    this.auth.clearSession();
    window.location.href = '/auth/login';
  }
}
