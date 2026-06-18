import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { decodeJwtClaims } from '../../core/auth/jwt.utils';
import { Granja } from '../../data/models/granja.model';

@Component({
  selector: 'app-mis-plantas',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="bar">
      <h1>Mis granjas</h1>
      <nav>
        <a routerLink="/perfil">Perfil</a>
        @if (puedeGestionarEquipo()) {
          <a routerLink="/equipo">Equipo</a>
          <a routerLink="/auditoria">Auditoría</a>
        }
        <button type="button" (click)="logout()">Salir</button>
      </nav>
    </header>
    @if (loading()) {
      <p>Cargando…</p>
    } @else if (error()) {
      <p class="error">{{ error() }}</p>
      <p><a routerLink="/auth/login">Volver a ingresar</a></p>
    } @else {
      <ul>
        @for (g of granjas(); track g.id) {
          <li>
            <a [routerLink]="['/granja', g.id]">{{ g.nombreGranja }}</a>
          </li>
        } @empty {
          <li class="vacio">Todavía no tenés granjas. Creá la primera abajo.</li>
        }
      </ul>

      @if (esDueno()) {
        <form class="crear" (submit)="crearGranja($event)">
          <h2>Crear granja</h2>
          <div class="fila">
            <input
              type="text"
              placeholder="Nombre de la granja (ej. Planta Norte)"
              [value]="nombreNueva()"
              (input)="nombreNueva.set($any($event.target).value)"
              [disabled]="creando()"
              maxlength="200"
            />
            <button type="submit" [disabled]="creando() || !nombreNueva().trim()">
              {{ creando() ? 'Creando…' : 'Crear granja' }}
            </button>
          </div>
          @if (errorCrear()) {
            <p class="error">{{ errorCrear() }}</p>
          }
        </form>
      }
    }
  `,
  styles: [
    `
      .bar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 1rem;
        font-family: system-ui, sans-serif;
      }
      ul {
        list-style: none;
        padding: 1rem;
      }
      .error {
        color: #b91c1c;
      }
      .vacio {
        color: #6b7280;
      }
      .crear {
        padding: 1rem;
        max-width: 32rem;
        font-family: system-ui, sans-serif;
      }
      .crear h2 {
        font-size: 1rem;
        margin: 0 0 0.5rem;
      }
      .crear .fila {
        display: flex;
        gap: 0.5rem;
      }
      .crear input {
        flex: 1;
        padding: 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 0.375rem;
      }
      .crear button {
        padding: 0.5rem 1rem;
        cursor: pointer;
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

  /** Solo el dueño (OWNER) y el jefe (ADMIN) gestionan el equipo. */
  readonly puedeGestionarEquipo = computed(() => {
    const claims = decodeJwtClaims(this.auth.getToken());
    return !claims?.esEmpleado || claims.rolEmpleado === 'ADMIN';
  });

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
