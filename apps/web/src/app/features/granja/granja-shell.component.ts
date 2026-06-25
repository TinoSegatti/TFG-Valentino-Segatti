import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AccountNavComponent, AccountNavBack } from '../../shared/account-nav.component';

/**
 * Shell de una granja seleccionada. Reusa <app-account-nav> para que la barra
 * superior sea exactamente la misma en toda la app. Lo único que cambia es:
 *   - aparece un back-link "Mis granjas".
 *   - aparece a la derecha un chip con el nombre de la granja activa.
 *
 * La navegación de secciones de granja vive en el sidebar lateral.
 */
@Component({
  selector: 'app-granja-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, AccountNavComponent],
  template: `
    <app-account-nav [back]="backLink()" [contexto]="nombre() || 'Cargando…'" />

    <div class="layout">
      <aside class="menu glass-surface">
        <div class="brand">
          <span class="logo">R</span>
          <span class="brand-text">
            <strong>{{ nombre() || 'REFORMA' }}</strong>
            <small>Granja · ERP</small>
          </span>
        </div>

        <p class="group-label">Operación</p>
        <nav class="nav-group">
          <a routerLink="resumen" routerLinkActive="active"><i class="pi pi-th-large"></i><span>Panel principal</span><b class="dot"></b></a>
          <a routerLink="materias-primas" routerLinkActive="active"><i class="pi pi-box"></i><span>Materias primas</span><b class="dot"></b></a>
          <a routerLink="proveedores" routerLinkActive="active"><i class="pi pi-truck"></i><span>Proveedores</span><b class="dot"></b></a>
          <a routerLink="animales" routerLinkActive="active"><i class="pi pi-pause"></i><span>Animales</span><b class="dot"></b></a>
          <a routerLink="compras" routerLinkActive="active"><i class="pi pi-shopping-cart"></i><span>Compras</span><b class="dot"></b></a>
          <a routerLink="formulas" routerLinkActive="active"><i class="pi pi-sliders-h"></i><span>Fórmulas</span><b class="dot"></b></a>
          <a routerLink="fabricaciones" routerLinkActive="active"><i class="pi pi-cog"></i><span>Fabricaciones</span><b class="dot"></b></a>
          <a routerLink="inventario" routerLinkActive="active"><i class="pi pi-warehouse"></i><span>Inventario</span><b class="dot"></b></a>
        </nav>

        <div class="plan glass-card-strong">
          <div class="plan-head">
            <i class="pi pi-bolt"></i>
            <span>Plan de la cuenta</span>
          </div>
          <p class="plan-sub">Capacidad de uso del periodo</p>
          <div class="rf-bar-track"><div class="rf-bar-fill" style="width: 64%"></div></div>
        </div>
      </aside>
      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
      }
      .layout {
        display: grid;
        grid-template-columns: 254px 1fr;
        gap: 0;
      }
      .menu {
        margin: 1rem;
        padding: 1rem 0.85rem;
        border-radius: 18px;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        align-self: start;
        position: sticky;
        top: 5rem;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 0.65rem;
        padding: 0.35rem 0.5rem 0.85rem;
      }
      .logo {
        display: grid;
        place-items: center;
        width: 38px;
        height: 38px;
        border-radius: 13px;
        font-family: var(--font-display);
        font-weight: 700;
        font-size: 1.15rem;
        color: var(--reforma-accent-contrast);
        background: linear-gradient(145deg, var(--reforma-accent), var(--reforma-cyan));
        box-shadow: 0 10px 26px -8px var(--reforma-accent);
      }
      .brand-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .brand-text strong {
        color: var(--reforma-text);
        font-size: 0.95rem;
        font-weight: 700;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 12ch;
      }
      .brand-text small {
        color: var(--reforma-text-faint);
        font-size: 0.72rem;
        letter-spacing: 0.1em;
        text-transform: uppercase;
      }
      .group-label {
        margin: 0.5rem 0.6rem 0.25rem;
        font-size: 0.66rem;
        font-weight: 600;
        letter-spacing: 0.14em;
        text-transform: uppercase;
        color: var(--reforma-text-faint);
      }
      .nav-group {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }
      .menu a {
        position: relative;
        padding: 0.65rem 0.8rem;
        color: var(--reforma-text-dim);
        text-decoration: none;
        border-radius: 13px;
        border: 1px solid transparent;
        display: flex;
        align-items: center;
        gap: 0.7rem;
        font-size: 0.9rem;
        transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
      }
      .menu a i {
        width: 1.1rem;
        font-size: 0.95rem;
      }
      .menu a span {
        flex: 1;
      }
      .menu a .dot {
        width: 6px;
        height: 6px;
        border-radius: 999px;
        background: var(--reforma-accent);
        box-shadow: 0 0 10px var(--reforma-accent);
        opacity: 0;
        transition: opacity 0.15s ease;
      }
      .menu a:hover {
        background: var(--glass-bg-hover);
        color: var(--reforma-text);
      }
      .menu a.active {
        background: var(--reforma-accent-soft);
        border-color: rgba(157, 119, 244, 0.35);
        color: #ede9fe;
        font-weight: 600;
      }
      .menu a.active .dot {
        opacity: 1;
      }
      .plan {
        margin-top: 0.85rem;
        padding: 0.85rem 0.9rem;
      }
      .plan-head {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-weight: 600;
        font-size: 0.85rem;
        color: var(--reforma-text);
      }
      .plan-head i {
        color: var(--reforma-accent);
      }
      .plan-sub {
        margin: 0.35rem 0 0.6rem;
        font-size: 0.74rem;
        color: var(--reforma-text-dim);
      }
      .content {
        padding: 1.5rem;
        min-width: 0;
      }
      @media (max-width: 900px) {
        .layout {
          grid-template-columns: 1fr;
        }
        .menu {
          position: static;
        }
        .nav-group {
          flex-direction: row;
          flex-wrap: wrap;
        }
        .menu a {
          flex: 0 1 auto;
        }
        .menu a .dot {
          display: none;
        }
        .plan {
          display: none;
        }
      }
    `,
  ],
})
export class GranjaShellComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ReformaApiService);

  readonly nombre = signal<string>('');

  readonly backLink = computed<AccountNavBack>(() => ({
    label: 'Mis granjas',
    to: '/mis-plantas',
  }));

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('idGranja') ?? '';
    if (!id) {
      return;
    }
    this.api.getGranja(id).subscribe({
      next: (g) => this.nombre.set(g.nombreGranja),
      // Si falla la carga del nombre, mostramos el id como respaldo (no rompe la navegación).
      error: () => this.nombre.set(`Granja ${id}`),
    });
  }
}
