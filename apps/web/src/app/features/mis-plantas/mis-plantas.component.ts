import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { Granja } from '../../data/models/granja.model';

@Component({
  selector: 'app-mis-plantas',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="bar">
      <h1>Mis granjas</h1>
      <button type="button" (click)="logout()">Salir</button>
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
          <li>No hay granjas activas. Creá la primera desde la API o el seed.</li>
        }
      </ul>
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
    `,
  ],
})
export class MisPlantasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);

  readonly granjas = signal<Granja[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

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

  logout(): void {
    this.auth.clearSession();
    window.location.href = '/auth/login';
  }
}
