import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { Perfil } from '../../data/models/usuario.model';
import { AccountNavComponent } from '../../shared/account-nav.component';
import { PersonalizacionComponent } from './personalizacion.component';

const ROL_LABEL: Record<string, string> = {
  OWNER: 'Dueño',
  ADMIN: 'Administrador (jefe)',
  EDITOR: 'Editor',
  LECTOR: 'Lector',
};

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [AccountNavComponent, PersonalizacionComponent, RouterLink],
  template: `
    <app-account-nav />

    <div class="page">
      <h1 class="reforma-page-title">Mi perfil</h1>

      @if (loading()) {
        <p class="reforma-empty">Cargando…</p>
      } @else if (error()) {
        <p class="reforma-alert reforma-alert-error">
          <i class="pi pi-exclamation-circle"></i> {{ error() }}
        </p>
      } @else if (perfil()) {
        @if (perfil(); as p) {
        <section class="glass-card identidad">
          <div class="avatar">{{ iniciales() }}</div>
          <div class="datos-cabecera">
            <h2>{{ p.nombre }} {{ p.apellido }}</h2>
            <p class="email">{{ p.email }}</p>
            <span class="chip rol">{{ rolLabel() }}</span>
          </div>
        </section>

        <div class="grid">
          <section class="glass-card">
            <h3 class="reforma-section-title">Cuenta</h3>
            <dl>
              <dt>Tipo de cuenta</dt>
              <dd>{{ p.esEmpleado ? 'Empleado' : 'Dueño de la cuenta' }}</dd>
              <dt>Plan</dt>
              <dd>
                <span class="chip plan">{{ p.plan }}</span>
                @if (!p.esEmpleado) {
                  <a class="gestionar" routerLink="/suscripcion">Gestionar</a>
                }
              </dd>
              <dt>Email</dt>
              <dd>{{ p.email }}</dd>
            </dl>
          </section>

          <section class="glass-card">
            <h3 class="reforma-section-title">Permisos</h3>
            @if (p.permisos.length > 0) {
              <ul class="permisos">
                @for (permiso of p.permisos; track permiso) {
                  <li><i class="pi pi-check"></i> {{ permiso }}</li>
                }
              </ul>
            } @else {
              <p class="reforma-empty">Sin permisos asignados.</p>
            }
          </section>
        </div>

        <app-personalizacion />
        }
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 76rem;
        margin: 0 auto;
        padding: 1.5rem;
      }
      .identidad {
        display: flex;
        align-items: center;
        gap: 1.25rem;
        padding: 1.5rem;
        margin-bottom: 1.25rem;
      }
      .avatar {
        flex: 0 0 auto;
        width: 4rem;
        height: 4rem;
        border-radius: 999px;
        display: grid;
        place-items: center;
        font-size: 1.4rem;
        font-weight: 700;
        color: var(--reforma-accent-contrast);
        background: linear-gradient(140deg, var(--reforma-accent), var(--reforma-cyan));
      }
      .datos-cabecera {
        min-width: 0;
        overflow: hidden;
      }
      .datos-cabecera h2 {
        margin: 0;
        font-size: 1.3rem;
        color: var(--reforma-text);
        overflow-wrap: break-word;
      }
      .datos-cabecera .email {
        margin: 0.2rem 0 0.5rem;
        color: var(--reforma-text-dim);
        overflow-wrap: anywhere;
      }
      .chip {
        display: inline-flex;
        align-items: center;
        padding: 0.2rem 0.6rem;
        border-radius: 999px;
        font-size: 0.8rem;
        font-weight: 600;
      }
      .chip.rol {
        background: var(--reforma-accent-soft);
        color: #ede9fe;
        border: 1px solid rgba(157, 119, 244, 0.35);
      }
      .chip.plan {
        background: rgba(6, 182, 212, 0.16);
        color: #a5f3fc;
        border: 1px solid rgba(6, 182, 212, 0.35);
      }
      .gestionar {
        margin-left: 0.6rem;
        color: var(--reforma-accent);
        font-size: 0.85rem;
        font-weight: 600;
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(26rem, 1fr));
        gap: 1.25rem;
      }
      .glass-card {
        padding: 1.25rem 1.5rem;
      }
      dl {
        display: grid;
        grid-template-columns: 9rem 1fr;
        gap: 0.6rem 1rem;
        margin: 0;
      }
      dt {
        color: var(--reforma-text-dim);
      }
      dd {
        margin: 0;
        color: var(--reforma-text);
        font-weight: 500;
        overflow-wrap: anywhere;
        min-width: 0;
      }
      .permisos {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .permisos li {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        color: var(--reforma-text);
      }
      .permisos i {
        color: var(--reforma-ok);
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

  readonly iniciales = computed(() => {
    const p = this.perfil();
    if (!p) return '';
    const a = (p.nombre?.[0] ?? '').toUpperCase();
    const b = (p.apellido?.[0] ?? '').toUpperCase();
    return (a + b) || (p.email?.[0] ?? '?').toUpperCase();
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
