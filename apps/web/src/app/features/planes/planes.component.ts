import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { mensajeErrorHttp } from '../../core/http/api-error.util';
import { AccountNavComponent } from '../../shared/account-nav.component';
import { PlanSuscripcion } from '../../data/models/usuario.model';
import {
  CambioPlanImpacto,
  PeriodoFacturacion,
  PlanCatalogo,
  Suscripcion,
} from '../../data/models/suscripcion.model';

/** Orden comercial de los planes (mismo criterio que el backend: ordinal del enum). */
const ORDEN: Record<PlanSuscripcion, number> = { DEMO: 0, STARTER: 1, BUSINESS: 2, ENTERPRISE: 3 };

const NOMBRE_RECURSO: Record<string, string> = {
  empleados: 'Empleados',
  granjas: 'Granjas',
  materiasPrimas: 'Materias primas',
  proveedores: 'Proveedores',
  animales: 'Animales',
  formulas: 'Fórmulas',
  fabricaciones: 'Fabricaciones',
  archivos: 'Archivos',
};

@Component({
  selector: 'app-planes',
  standalone: true,
  imports: [DecimalPipe, RouterLink, AccountNavComponent],
  template: `
    @if (autenticado()) {
      <app-account-nav />
    }

    <div class="page">
      <header class="cabecera">
        <h1 class="reforma-page-title">Planes y precios</h1>
        <p class="sub">
          Elegí el plan que acompaña el tamaño de tu operación. Podés cambiar cuando quieras:
          los upgrades aplican al instante y los downgrades al final del ciclo ya pagado.
        </p>

        <div class="toggle-periodo" role="radiogroup" aria-label="Período de facturación">
          <button
            type="button"
            [class.activo]="periodo() === 'MENSUAL'"
            (click)="periodo.set('MENSUAL')"
          >
            Mensual
          </button>
          <button
            type="button"
            [class.activo]="periodo() === 'ANUAL'"
            (click)="periodo.set('ANUAL')"
          >
            Anual <span class="badge-ahorro">2 meses gratis</span>
          </button>
        </div>
      </header>

      @if (esEmpleado()) {
        <p class="reforma-alert">
          <i class="pi pi-info-circle"></i>
          La suscripción la gestiona el dueño de la cuenta.
        </p>
      }
      @if (pendienteBanner(); as banner) {
        <p class="reforma-alert reforma-alert-warn banner-pendiente">
          <i class="pi pi-clock"></i>
          <span>{{ banner }}</span>
          <a routerLink="/suscripcion">Ver mi suscripción</a>
        </p>
      }
      @if (error()) {
        <p class="reforma-alert reforma-alert-error">
          <i class="pi pi-exclamation-circle"></i> {{ error() }}
        </p>
      }

      @if (cargando()) {
        <p class="reforma-empty">Cargando planes…</p>
      } @else {
        <div class="cards">
          @for (card of catalogo(); track card.plan) {
            <section
              class="glass-card card"
              [class.actual]="esPlanActual(card.plan)"
              [class.destacado]="card.plan === 'BUSINESS'"
            >
              @if (card.plan === 'BUSINESS') {
                <span class="cinta">Recomendado</span>
              }
              <h2>{{ card.plan }}</h2>

              <p class="precio">
                @if (card.plan === 'DEMO') {
                  <span class="monto">Gratis</span>
                  <span class="detalle">prueba con purga a los 60 días</span>
                } @else {
                  <span class="monto">
                    $ {{ precioMostrado(card) | number: '1.0-0' }}
                  </span>
                  <span class="detalle">
                    ARS / {{ periodo() === 'MENSUAL' ? 'mes' : 'año' }}
                  </span>
                }
              </p>

              <ul class="limites">
                <li><i class="pi pi-building"></i> {{ limite(card.limites.granjas) }} granjas</li>
                <li><i class="pi pi-users"></i> {{ limite(card.limites.empleados) }} empleados</li>
                <li>
                  <i class="pi pi-box"></i>
                  {{ limite(card.limites.materiasPrimas) }} materias primas
                </li>
                <li><i class="pi pi-book"></i> {{ limite(card.limites.formulas) }} fórmulas</li>
                <li>
                  <i class="pi pi-cog"></i>
                  {{ limite(card.limites.fabricaciones) }} fabricaciones
                </li>
                <li><i class="pi pi-truck"></i> {{ limite(card.limites.proveedores) }} proveedores</li>
                <li>
                  <i class="pi pi-chart-line" [class.no]="!card.prediccionStock"></i>
                  Predicción IA de stock {{ card.prediccionStock ? 'incluida' : 'no incluida' }}
                </li>
              </ul>

              <div class="cta">
                @if (esPlanActual(card.plan)) {
                  <span class="chip-actual"><i class="pi pi-check"></i> Tu plan actual</span>
                } @else if (card.plan === 'DEMO') {
                  <span class="mini text-dim">
                    Para volver a DEMO cancelá tu suscripción desde "Mi suscripción".
                  </span>
                } @else if (!autenticado()) {
                  <a class="reforma-btn" routerLink="/auth/registro">Crear cuenta</a>
                } @else if (!esEmpleado()) {
                  <button
                    type="button"
                    class="reforma-btn"
                    [disabled]="procesando()"
                    (click)="elegirPlan(card.plan)"
                  >
                    {{ ctaLabel(card.plan) }}
                  </button>
                }
              </div>
            </section>
          }
        </div>
      }
    </div>

    <!-- Modal de impacto (downgrade, RD-P6.c) -->
    @if (modalPlan(); as destino) {
      <div class="overlay" (click)="cerrarModal()"></div>
      <div class="modal glass-card-strong" role="dialog" aria-modal="true">
        <h3>Cambiar a {{ destino }}</h3>

        @if (cargandoImpacto()) {
          <p class="mini text-dim">Calculando impacto del cambio…</p>
        } @else {
          @if (impacto(); as imp) {
          <p class="mini text-dim">
            El cambio se aplica el {{ fecha(imp.aplicaDesde) }}; hasta entonces conservás tu plan
            actual sin costo extra.
          </p>

          @if (imp.bloqueantes.length > 0) {
            <div class="impacto bloqueantes">
              <h4><i class="pi pi-ban"></i> Necesitás resolver esto antes de confirmar</h4>
              <ul>
                @for (b of imp.bloqueantes; track b.recurso + (b.granja ?? '')) {
                  <li>
                    {{ nombreRecurso(b.recurso) }}: tenés {{ b.cantidadActual }} activos y
                    {{ destino }} admite {{ b.limiteDestino }} — desactivá {{ b.excedente }}.
                  </li>
                }
              </ul>
              <a class="reforma-btn-ghost" routerLink="/equipo">
                <i class="pi pi-users"></i> Gestionar equipo
              </a>
            </div>
          }

          @if (imp.advertencias.length > 0) {
            <div class="impacto advertencias">
              <h4><i class="pi pi-exclamation-triangle"></i> Datos que quedan sobre el límite</h4>
              <p class="mini text-dim">
                No se borra nada: solo no vas a poder crear más de estos recursos hasta quedar
                dentro del límite del nuevo plan.
              </p>
              <ul>
                @for (a of imp.advertencias; track a.recurso + (a.granja ?? '')) {
                  <li>
                    {{ nombreRecurso(a.recurso) }}@if (a.granja) { ({{ a.granja }})}:
                    {{ a.cantidadActual }} / {{ a.limiteDestino }} (+{{ a.excedente }})
                  </li>
                }
              </ul>
            </div>
          }

          @if (imp.bloqueantes.length === 0 && imp.advertencias.length === 0) {
            <p class="reforma-alert reforma-alert-ok">
              <i class="pi pi-check-circle"></i> Sin impacto: todos tus datos quedan dentro de los
              límites del nuevo plan.
            </p>
          }

          @if (errorModal()) {
            <p class="reforma-alert reforma-alert-error">
              <i class="pi pi-exclamation-circle"></i> {{ errorModal() }}
            </p>
          }

          <div class="acciones-modal">
            <button type="button" class="reforma-btn-ghost" (click)="cerrarModal()">
              Cancelar
            </button>
            <button
              type="button"
              class="reforma-btn"
              [disabled]="imp.bloqueantes.length > 0 || procesando()"
              (click)="confirmarCambio(destino)"
            >
              Confirmar cambio
            </button>
          </div>
          } @else if (errorModal()) {
            <p class="reforma-alert reforma-alert-error">
              <i class="pi pi-exclamation-circle"></i> {{ errorModal() }}
            </p>
            <div class="acciones-modal">
              <button type="button" class="reforma-btn-ghost" (click)="cerrarModal()">
                Cerrar
              </button>
            </div>
          }
        }
      </div>
    }
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
      .cabecera {
        text-align: center;
        margin-bottom: 1.5rem;
      }
      .sub {
        color: var(--reforma-text-dim);
        max-width: 44rem;
        margin: 0.5rem auto 1.25rem;
      }
      .toggle-periodo {
        display: inline-flex;
        gap: 0.25rem;
        padding: 0.25rem;
        border-radius: 999px;
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
      }
      .toggle-periodo button {
        border: none;
        background: transparent;
        color: var(--reforma-text-dim);
        padding: 0.45rem 1.1rem;
        border-radius: 999px;
        cursor: pointer;
        font-weight: 600;
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
      }
      .toggle-periodo button.activo {
        background: var(--reforma-accent-soft);
        color: #ede9fe;
        border: 1px solid rgba(157, 119, 244, 0.35);
      }
      .badge-ahorro {
        font-size: 0.7rem;
        font-weight: 700;
        padding: 0.1rem 0.5rem;
        border-radius: 999px;
        background: rgba(52, 211, 153, 0.18);
        color: #6ee7b7;
        border: 1px solid rgba(52, 211, 153, 0.35);
      }
      .banner-pendiente {
        display: flex;
        align-items: center;
        gap: 0.6rem;
      }
      .banner-pendiente a {
        color: var(--reforma-accent);
        font-weight: 600;
      }
      .cards {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(16.5rem, 1fr));
        gap: 1.25rem;
        align-items: stretch;
      }
      .card {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: 0.9rem;
        padding: 1.5rem;
      }
      .card.destacado {
        border-color: rgba(157, 119, 244, 0.5);
      }
      .card.actual {
        outline: 2px solid rgba(6, 182, 212, 0.5);
        outline-offset: -2px;
      }
      .cinta {
        position: absolute;
        top: -0.7rem;
        right: 1rem;
        font-size: 0.72rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        padding: 0.2rem 0.6rem;
        border-radius: 999px;
        background: linear-gradient(140deg, var(--reforma-accent), var(--reforma-cyan));
        color: var(--reforma-accent-contrast);
      }
      .card h2 {
        margin: 0;
        font-size: 1.15rem;
        letter-spacing: 0.06em;
        color: var(--reforma-text);
      }
      .precio {
        margin: 0;
        display: flex;
        flex-direction: column;
      }
      .precio .monto {
        font-size: 1.7rem;
        font-weight: 800;
        color: var(--reforma-text);
      }
      .precio .detalle {
        color: var(--reforma-text-dim);
        font-size: 0.85rem;
      }
      .limites {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.45rem;
        flex: 1;
      }
      .limites li {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        color: var(--reforma-text);
        font-size: 0.92rem;
      }
      .limites i {
        color: var(--reforma-accent);
        font-size: 0.85rem;
      }
      .limites i.no {
        color: var(--reforma-text-dim);
      }
      .cta {
        margin-top: 0.25rem;
      }
      .cta .reforma-btn {
        width: 100%;
        justify-content: center;
      }
      .chip-actual {
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
        padding: 0.45rem 0.9rem;
        border-radius: 999px;
        background: rgba(6, 182, 212, 0.16);
        color: #a5f3fc;
        border: 1px solid rgba(6, 182, 212, 0.35);
        font-weight: 600;
        font-size: 0.9rem;
      }
      .mini {
        font-size: 0.85rem;
      }
      .text-dim {
        color: var(--reforma-text-dim);
      }
      .overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.55);
        backdrop-filter: blur(2px);
        z-index: 40;
      }
      .modal {
        position: fixed;
        top: 10vh;
        left: 50%;
        transform: translateX(-50%);
        padding: 1.5rem;
        width: min(520px, 92vw);
        max-height: 78vh;
        overflow: auto;
        z-index: 41;
      }
      .modal h3 {
        margin: 0 0 0.5rem;
        color: var(--reforma-text);
      }
      .impacto {
        margin-top: 0.9rem;
        padding: 0.9rem 1rem;
        border-radius: 12px;
      }
      .impacto h4 {
        margin: 0 0 0.5rem;
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.95rem;
      }
      .impacto ul {
        margin: 0.25rem 0 0.6rem;
        padding-left: 1.1rem;
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        color: var(--reforma-text);
        font-size: 0.9rem;
      }
      .impacto.bloqueantes {
        background: rgba(251, 113, 133, 0.1);
        border: 1px solid rgba(251, 113, 133, 0.35);
      }
      .impacto.bloqueantes h4 {
        color: #fda4af;
      }
      .impacto.advertencias {
        background: rgba(251, 191, 36, 0.08);
        border: 1px solid rgba(251, 191, 36, 0.3);
      }
      .impacto.advertencias h4 {
        color: #fcd34d;
      }
      .acciones-modal {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        align-items: center;
        margin-top: 1.25rem;
      }
    `,
  ],
})
export class PlanesComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);

  readonly catalogo = signal<PlanCatalogo[]>([]);
  readonly suscripcion = signal<Suscripcion | null>(null);
  readonly periodo = signal<PeriodoFacturacion>('MENSUAL');
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  /** true cuando GET /api/suscripcion devolvió 403 (empleado: solo lectura del catálogo). */
  readonly esEmpleado = signal(false);
  readonly procesando = signal(false);

  // Modal de impacto (downgrade)
  readonly modalPlan = signal<PlanSuscripcion | null>(null);
  readonly impacto = signal<CambioPlanImpacto | null>(null);
  readonly cargandoImpacto = signal(false);
  readonly errorModal = signal<string | null>(null);

  readonly autenticado = computed(() => this.auth.isAuthenticated());

  readonly pendienteBanner = computed(() => {
    const s = this.suscripcion();
    if (!s?.planPendiente) return null;
    return s.estado === 'CANCELADA'
      ? `Tu suscripción está cancelada: pasás a DEMO el ${this.fecha(s.fechaFinPeriodo)}.`
      : `Tenés un cambio a ${s.planPendiente} programado para el ${this.fecha(s.fechaFinPeriodo)}.`;
  });

  ngOnInit(): void {
    this.api.getPlanes().subscribe({
      next: (cards) => {
        this.catalogo.set(cards);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar el catálogo de planes.');
        this.cargando.set(false);
      },
    });
    if (this.autenticado()) {
      this.api.getSuscripcion().subscribe({
        next: (s) => this.suscripcion.set(s),
        error: (err: HttpErrorResponse) => {
          if (err.status === 403) this.esEmpleado.set(true);
        },
      });
    }
  }

  precioMostrado(card: PlanCatalogo): number {
    return this.periodo() === 'MENSUAL' ? card.precioMensualArs : card.precioAnualArs;
  }

  limite(valor: number | null): string {
    return valor == null ? 'Ilimitadas/os' : String(valor);
  }

  nombreRecurso(recurso: string): string {
    return NOMBRE_RECURSO[recurso] ?? recurso;
  }

  fecha(iso: string | null): string {
    return iso ? new Date(iso).toLocaleDateString('es-AR') : '—';
  }

  esPlanActual(plan: PlanSuscripcion): boolean {
    const s = this.suscripcion();
    if (!s) return false;
    // Con suscripción gestionada ACTIVA cuenta también el período elegido en el toggle.
    if (s.gestionada && s.estado === 'ACTIVA' && s.plan) {
      return s.plan === plan && s.periodo === this.periodo();
    }
    return s.planEfectivo === plan;
  }

  ctaLabel(plan: PlanSuscripcion): string {
    const s = this.suscripcion();
    const actual = s?.planEfectivo ?? 'DEMO';
    if (plan === actual) return 'Cambiar período';
    return ORDEN[plan] > ORDEN[actual] ? 'Mejorar plan' : 'Bajar de plan';
  }

  /** CTA de una card: upgrade/cambio de período van directo al checkout; downgrade abre el modal. */
  elegirPlan(plan: PlanSuscripcion): void {
    const s = this.suscripcion();
    const actual = s?.planEfectivo ?? 'DEMO';
    if (ORDEN[plan] < ORDEN[actual]) {
      this.abrirModalImpacto(plan);
      return;
    }
    this.iniciarCheckout(plan);
  }

  private abrirModalImpacto(plan: PlanSuscripcion): void {
    this.modalPlan.set(plan);
    this.impacto.set(null);
    this.errorModal.set(null);
    this.cargandoImpacto.set(true);
    this.api.getCambioImpacto(plan).subscribe({
      next: (imp) => {
        this.impacto.set(imp);
        this.cargandoImpacto.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorModal.set(mensajeErrorHttp(err, 'No se pudo calcular el impacto del cambio.'));
        this.cargandoImpacto.set(false);
      },
    });
  }

  cerrarModal(): void {
    this.modalPlan.set(null);
    this.impacto.set(null);
    this.errorModal.set(null);
  }

  confirmarCambio(plan: PlanSuscripcion): void {
    this.iniciarCheckout(plan);
  }

  private iniciarCheckout(plan: PlanSuscripcion): void {
    this.procesando.set(true);
    this.errorModal.set(null);
    this.error.set(null);
    this.api.checkoutSuscripcion(plan, this.periodo()).subscribe({
      next: (r) => {
        this.procesando.set(false);
        if (r.requierePago && r.urlPago) {
          // Simulado: URL propia (/planes/checkout-simulado). MP: checkout hospedado externo.
          window.location.href = r.urlPago;
          return;
        }
        // Downgrade programado sin cobro (RD-P5): mostrar el estado resultante.
        this.cerrarModal();
        this.suscripcion.set(r.suscripcion);
        this.router.navigate(['/suscripcion'], { queryParams: { programado: plan } });
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(false);
        const msg = mensajeErrorHttp(err, 'No se pudo iniciar la contratación.');
        if (this.modalPlan()) this.errorModal.set(msg);
        else this.error.set(msg);
      },
    });
  }
}
