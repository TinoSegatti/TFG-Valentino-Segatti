import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { Perfil } from '../../data/models/usuario.model';

const ROL_LABEL: Record<string, string> = {
  OWNER: 'Dueño',
  ADMIN: 'Administrador (jefe)',
  EDITOR: 'Editor',
  LECTOR: 'Lector',
};

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="bar">
      <a routerLink="/mis-plantas" class="back">← Mis granjas</a>
      <h1>Mi perfil</h1>
    </header>

    @if (loading()) {
      <p class="msg">Cargando…</p>
    } @else if (error()) {
      <p class="msg error">{{ error() }}</p>
    } @else {
      @if (perfil(); as p) {
        <section class="card">
          <dl>
            <dt>Nombre</dt>
            <dd>{{ p.nombre }} {{ p.apellido }}</dd>
            <dt>Email</dt>
            <dd>{{ p.email }}</dd>
            <dt>Rol</dt>
            <dd>{{ rolLabel() }}</dd>
            <dt>Tipo de cuenta</dt>
            <dd>{{ p.esEmpleado ? 'Empleado' : 'Dueño de la cuenta' }}</dd>
            <dt>Plan</dt>
            <dd>{{ p.plan }}</dd>
          </dl>
        </section>

        <section class="card">
          <h2>Permisos</h2>
          <ul>
            @for (permiso of p.permisos; track permiso) {
              <li>{{ permiso }}</li>
            }
          </ul>
        </section>
      }
    }
  `,
  styles: [
    `
      :host {
        display: block;
        font-family: system-ui, sans-serif;
        max-width: 40rem;
        margin: 0 auto;
      }
      .bar {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 1rem;
      }
      .bar h1 {
        font-size: 1.25rem;
        margin: 0;
      }
      .back {
        color: #166534;
        text-decoration: none;
      }
      .msg {
        padding: 1rem;
      }
      .error {
        color: #b91c1c;
      }
      .card {
        margin: 0 1rem 1rem;
        padding: 1rem 1.25rem;
        border: 1px solid #e5e7eb;
        border-radius: 0.5rem;
        background: #fff;
      }
      .card h2 {
        font-size: 1rem;
        margin: 0 0 0.5rem;
      }
      dl {
        display: grid;
        grid-template-columns: 10rem 1fr;
        gap: 0.4rem 1rem;
        margin: 0;
      }
      dt {
        color: #6b7280;
      }
      dd {
        margin: 0;
        font-weight: 500;
      }
      ul {
        margin: 0;
        padding-left: 1.1rem;
      }
      li {
        margin: 0.15rem 0;
      }
    `,
  ],
})
export class PerfilComponent implements OnInit {
  private readonly api = inject(ReformaApiService);

  readonly perfil = signal<Perfil | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly rolLabel = computed(() => {
    const p = this.perfil();
    return p ? (ROL_LABEL[p.rol] ?? p.rol) : '';
  });

  ngOnInit(): void {
    this.api.getPerfil().subscribe({
      next: (p) => {
        this.perfil.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar el perfil. Volvé a ingresar.');
        this.loading.set(false);
      },
    });
  }
}
