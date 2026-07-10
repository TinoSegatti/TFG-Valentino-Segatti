import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../core/http/api-error.util';
import { PlanSuscripcion } from '../../data/models/usuario.model';
import { PeriodoFacturacion, PlanCatalogo } from '../../data/models/suscripcion.model';

const PLANES_VALIDOS: PlanSuscripcion[] = ['STARTER', 'BUSINESS', 'ENTERPRISE'];

/**
 * Pantalla de pago de la pasarela SIMULADA (solo modo `simulado`, RD-P2): reemplaza el
 * checkout hospedado de Mercado Pago en dev/demo. El backend re-valida todo en
 * confirmar-simulado — esta pantalla no otorga nada por sí misma.
 */
@Component({
  selector: 'app-checkout-simulado',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  template: `
    <div class="page">
      <section class="glass-card-strong tarjeta">
        <header>
          <span class="badge-simulado"><i class="pi pi-bolt"></i> Pasarela simulada</span>
          <h1>Confirmar pago</h1>
          <p class="mini text-dim">
            Entorno de demostración: ningún cobro real. Elegí el resultado del pago para
            continuar el flujo.
          </p>
        </header>

        @if (!parametrosValidos()) {
          <p class="reforma-alert reforma-alert-error">
            <i class="pi pi-exclamation-circle"></i>
            Falta el plan o el período a contratar. Volvé a elegir un plan.
          </p>
          <a class="reforma-btn" routerLink="/planes">Ver planes</a>
        } @else {
          <dl class="resumen">
            <dt>Plan</dt>
            <dd>{{ plan() }}</dd>
            <dt>Período</dt>
            <dd>{{ periodo() === 'MENSUAL' ? 'Mensual' : 'Anual' }}</dd>
            <dt>Total</dt>
            <dd class="total">
              @if (precio() != null) {
                $ {{ precio() | number: '1.0-0' }} ARS
              } @else {
                —
              }
            </dd>
          </dl>

          @if (error()) {
            <p class="reforma-alert reforma-alert-error">
              <i class="pi pi-exclamation-circle"></i> {{ error() }}
            </p>
          }

          <div class="acciones">
            <button
              type="button"
              class="reforma-btn aprobar"
              [disabled]="procesando()"
              (click)="confirmar('APROBADO')"
            >
              <i class="pi pi-check"></i>
              {{ procesando() ? 'Procesando…' : 'Aprobar pago' }}
            </button>
            <button
              type="button"
              class="reforma-btn-danger"
              [disabled]="procesando()"
              (click)="confirmar('RECHAZADO')"
            >
              <i class="pi pi-times"></i> Rechazar pago
            </button>
          </div>
          <a class="volver" routerLink="/planes">
            <i class="pi pi-arrow-left"></i> Volver a los planes sin pagar
          </a>
        }
      </section>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 34rem;
        margin: 0 auto;
        padding: 3rem 1.5rem;
      }
      .tarjeta {
        padding: 2rem;
        display: flex;
        flex-direction: column;
        gap: 1.1rem;
      }
      .badge-simulado {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.75rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        padding: 0.25rem 0.7rem;
        border-radius: 999px;
        background: rgba(251, 191, 36, 0.14);
        color: #fcd34d;
        border: 1px solid rgba(251, 191, 36, 0.35);
      }
      h1 {
        margin: 0.75rem 0 0.25rem;
        color: var(--reforma-text);
      }
      .mini {
        font-size: 0.85rem;
      }
      .text-dim {
        color: var(--reforma-text-dim);
      }
      .resumen {
        display: grid;
        grid-template-columns: 8rem 1fr;
        gap: 0.55rem 1rem;
        margin: 0;
        padding: 1rem 1.1rem;
        border-radius: 12px;
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
      }
      dt {
        color: var(--reforma-text-dim);
      }
      dd {
        margin: 0;
        color: var(--reforma-text);
        font-weight: 600;
      }
      dd.total {
        font-size: 1.15rem;
        font-weight: 800;
      }
      .acciones {
        display: flex;
        gap: 0.75rem;
        flex-wrap: wrap;
      }
      .acciones .reforma-btn,
      .acciones .reforma-btn-danger {
        flex: 1;
        justify-content: center;
      }
      .volver {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        color: var(--reforma-text-dim);
        font-size: 0.9rem;
        text-decoration: none;
      }
      .volver:hover {
        color: var(--reforma-text);
      }
    `,
  ],
})
export class CheckoutSimuladoComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly plan = signal<PlanSuscripcion | null>(null);
  readonly periodo = signal<PeriodoFacturacion>('MENSUAL');
  readonly catalogo = signal<PlanCatalogo[]>([]);
  readonly procesando = signal(false);
  readonly error = signal<string | null>(null);

  readonly parametrosValidos = computed(() => this.plan() !== null);

  readonly precio = computed(() => {
    const card = this.catalogo().find((c) => c.plan === this.plan());
    if (!card) return null;
    return this.periodo() === 'MENSUAL' ? card.precioMensualArs : card.precioAnualArs;
  });

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    const plan = qp.get('plan') as PlanSuscripcion | null;
    const periodo = qp.get('periodo') as PeriodoFacturacion | null;
    if (plan && PLANES_VALIDOS.includes(plan)) {
      this.plan.set(plan);
    }
    this.periodo.set(periodo === 'ANUAL' ? 'ANUAL' : 'MENSUAL');
    this.api.getPlanes().subscribe({ next: (cards) => this.catalogo.set(cards) });
  }

  confirmar(resultado: 'APROBADO' | 'RECHAZADO'): void {
    const plan = this.plan();
    if (!plan) return;
    this.procesando.set(true);
    this.error.set(null);
    this.api.confirmarCheckoutSimulado(plan, this.periodo(), resultado).subscribe({
      next: () => {
        this.router.navigate(['/planes/retorno'], {
          queryParams: { resultado: resultado === 'APROBADO' ? 'aprobado' : 'rechazado', plan },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo confirmar el pago simulado.'));
      },
    });
  }
}
