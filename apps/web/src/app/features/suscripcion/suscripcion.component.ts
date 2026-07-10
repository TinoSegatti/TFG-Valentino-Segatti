import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../core/http/api-error.util';
import { AccountNavComponent } from '../../shared/account-nav.component';
import { PaginaPagos, Suscripcion } from '../../data/models/suscripcion.model';

const ESTADO_LABEL: Record<string, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  ACTIVA: 'Activa',
  CANCELADA: 'Cancelada',
  EXPIRADA: 'Expirada',
};

const PAGO_LABEL: Record<string, string> = {
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  PENDIENTE: 'Pendiente',
  DEVUELTO: 'Devuelto',
};

/** Gestión de la suscripción del dueño (RD-P7/P10). Los empleados ven un aviso (403 del backend). */
@Component({
  selector: 'app-suscripcion',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, AccountNavComponent],
  template: `
    <app-account-nav />

    <div class="page">
      <h1 class="reforma-page-title">Mi suscripción</h1>

      @if (avisoProgramado()) {
        <p class="reforma-alert reforma-alert-ok">
          <i class="pi pi-check-circle"></i> {{ avisoProgramado() }}
        </p>
      }

      @if (cargando()) {
        <p class="reforma-empty">Cargando…</p>
      } @else if (esEmpleado()) {
        <p class="reforma-alert">
          <i class="pi pi-info-circle"></i>
          La suscripción la gestiona el dueño de la cuenta.
        </p>
      } @else {
        @if (error()) {
          <p class="reforma-alert reforma-alert-error">
            <i class="pi pi-exclamation-circle"></i> {{ error() }}
          </p>
        }
        @if (suscripcion(); as s) {
        <!-- Banner de cambio programado (downgrade o cancelación) -->
        @if (s.planPendiente) {
          <div class="reforma-alert reforma-alert-warn banner">
            <i class="pi pi-clock"></i>
            <span>
              @if (s.estado === 'CANCELADA') {
                Suscripción cancelada: tu plan {{ s.plan }} sigue vigente hasta el
                {{ s.fechaFinPeriodo | date: 'dd/MM/yyyy' }} y después la cuenta pasa a DEMO.
              } @else {
                Cambio programado: pasás a {{ s.planPendiente }} el
                {{ s.fechaFinPeriodo | date: 'dd/MM/yyyy' }}.
              }
            </span>
            <button
              type="button"
              class="reforma-btn-ghost"
              [disabled]="procesando()"
              (click)="reactivar()"
            >
              Mantener mi plan
            </button>
          </div>
        }

        <div class="grid">
          <section class="glass-card">
            <h3 class="reforma-section-title">Estado</h3>
            <dl>
              <dt>Plan vigente</dt>
              <dd><span class="chip plan">{{ s.planEfectivo }}</span></dd>
              @if (s.gestionada) {
                <dt>Estado</dt>
                <dd>
                  <span class="chip" [class]="'chip estado-' + s.estado">
                    {{ estadoLabel(s.estado) }}
                  </span>
                </dd>
                <dt>Período</dt>
                <dd>{{ s.periodo === 'ANUAL' ? 'Anual' : 'Mensual' }}</dd>
                <dt>Precio</dt>
                <dd>$ {{ s.precioArs | number: '1.0-0' }} ARS</dd>
                <dt>Ciclo actual</dt>
                <dd>
                  {{ s.fechaInicio | date: 'dd/MM/yyyy' }} —
                  {{ s.fechaFinPeriodo | date: 'dd/MM/yyyy' }}
                </dd>
                @if (s.ultimoCobroEstado) {
                  <dt>Último cobro</dt>
                  <dd>
                    {{ pagoLabel(s.ultimoCobroEstado) }}
                    ({{ s.ultimoCobroFecha | date: 'dd/MM/yyyy' }})
                  </dd>
                }
              } @else {
                <dt>Estado</dt>
                <dd>
                  {{
                    s.planEfectivo === 'DEMO'
                      ? 'Cuenta de prueba (sin suscripción contratada)'
                      : 'Sin suscripción gestionada'
                  }}
                </dd>
              }
            </dl>

            <div class="acciones">
              <a class="reforma-btn" routerLink="/planes">
                <i class="pi pi-arrow-up-right"></i>
                {{ s.gestionada && s.estado === 'ACTIVA' ? 'Cambiar de plan' : 'Ver planes' }}
              </a>
              @if (s.gestionada && s.estado === 'ACTIVA' && !s.planPendiente) {
                <button
                  type="button"
                  class="reforma-btn-danger"
                  [disabled]="procesando()"
                  (click)="modalCancelar.set(true)"
                >
                  Cancelar suscripción
                </button>
              }
            </div>
          </section>

          <section class="glass-card">
            <h3 class="reforma-section-title">Historial de pagos</h3>
            @if (pagos(); as pg) {
              @if (pg.contenido.length === 0) {
                <p class="reforma-empty">Sin pagos registrados todavía.</p>
              } @else {
                <div class="tabla-wrap">
                  <table class="reforma-table">
                    <thead>
                      <tr>
                        <th>Fecha</th>
                        <th>Descripción</th>
                        <th class="num">Monto</th>
                        <th>Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (p of pg.contenido; track p.id) {
                        <tr>
                          <td>{{ p.fechaPago | date: 'dd/MM/yyyy HH:mm' }}</td>
                          <td>{{ p.descripcion }}</td>
                          <td class="num">$ {{ p.montoArs | number: '1.2-2' }}</td>
                          <td>
                            <span class="chip" [class]="'chip pago-' + p.estado">
                              {{ pagoLabel(p.estado) }}
                            </span>
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                @if (pg.totalPaginas > 1) {
                  <div class="paginacion">
                    <button
                      type="button"
                      class="reforma-btn-ghost"
                      [disabled]="pg.pagina === 0"
                      (click)="cargarPagos(pg.pagina - 1)"
                    >
                      <i class="pi pi-chevron-left"></i>
                    </button>
                    <span class="mini text-dim">
                      Página {{ pg.pagina + 1 }} de {{ pg.totalPaginas }}
                    </span>
                    <button
                      type="button"
                      class="reforma-btn-ghost"
                      [disabled]="pg.pagina + 1 >= pg.totalPaginas"
                      (click)="cargarPagos(pg.pagina + 1)"
                    >
                      <i class="pi pi-chevron-right"></i>
                    </button>
                  </div>
                }
              }
            } @else {
              <p class="reforma-empty">Cargando pagos…</p>
            }
          </section>
        </div>
        }
      }
    </div>

    <!-- Modal: confirmar cancelación (RD-P7) -->
    @if (modalCancelar()) {
      <div class="overlay" (click)="modalCancelar.set(false)"></div>
      <div class="modal glass-card-strong" role="dialog" aria-modal="true">
        <h3>Cancelar suscripción</h3>
        <p>Al confirmar:</p>
        <ul class="consecuencias">
          <li>
            <i class="pi pi-calendar"></i>
            Tu plan {{ suscripcion()?.plan }} sigue vigente hasta el
            {{ fechaFin() }} (no hay más cobros).
          </li>
          <li>
            <i class="pi pi-arrow-down"></i>
            Ese día la cuenta pasa a <strong>DEMO</strong>: límites de prueba y, si tu equipo
            supera los 2 integrantes, los más recientes se desactivan.
          </li>
          <li>
            <i class="pi pi-trash"></i>
            Como cuenta DEMO, tus datos se conservan 60 días y luego se eliminan
            definitivamente.
          </li>
          <li>
            <i class="pi pi-refresh"></i>
            Podés arrepentirte con "Mantener mi plan" hasta el fin del ciclo, o recontratar
            cuando quieras.
          </li>
        </ul>
        @if (errorModal()) {
          <p class="reforma-alert reforma-alert-error">
            <i class="pi pi-exclamation-circle"></i> {{ errorModal() }}
          </p>
        }
        <div class="acciones-modal">
          <button type="button" class="reforma-btn-ghost" (click)="modalCancelar.set(false)">
            Volver
          </button>
          <button
            type="button"
            class="reforma-btn-danger"
            [disabled]="procesando()"
            (click)="cancelar()"
          >
            {{ procesando() ? 'Cancelando…' : 'Sí, cancelar' }}
          </button>
        </div>
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 84rem;
        margin: 0 auto;
        padding: 1.5rem;
      }
      .banner {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        margin-bottom: 1.25rem;
        flex-wrap: wrap;
      }
      .banner span {
        flex: 1;
        min-width: 16rem;
      }
      /* El historial necesita más ancho que el estado: reparto 2/3 en pantallas anchas. */
      .grid {
        display: grid;
        grid-template-columns: minmax(22rem, 2fr) minmax(0, 3fr);
        gap: 1.25rem;
      }
      @media (max-width: 64rem) {
        .grid {
          grid-template-columns: 1fr;
        }
      }
      .glass-card {
        padding: 1.25rem 1.5rem;
      }
      dl {
        display: grid;
        grid-template-columns: 9rem 1fr;
        gap: 0.6rem 1rem;
        margin: 0 0 1rem;
      }
      dt {
        color: var(--reforma-text-dim);
      }
      dd {
        margin: 0;
        color: var(--reforma-text);
        font-weight: 500;
      }
      .chip {
        display: inline-flex;
        align-items: center;
        padding: 0.15rem 0.6rem;
        border-radius: 999px;
        font-size: 0.8rem;
        font-weight: 600;
      }
      .chip.plan {
        background: rgba(6, 182, 212, 0.16);
        color: #a5f3fc;
        border: 1px solid rgba(6, 182, 212, 0.35);
      }
      .chip.estado-ACTIVA,
      .chip.pago-APROBADO {
        background: rgba(52, 211, 153, 0.14);
        color: #6ee7b7;
        border: 1px solid rgba(52, 211, 153, 0.35);
      }
      .chip.estado-CANCELADA,
      .chip.pago-PENDIENTE,
      .chip.estado-PENDIENTE_PAGO {
        background: rgba(251, 191, 36, 0.12);
        color: #fcd34d;
        border: 1px solid rgba(251, 191, 36, 0.3);
      }
      .chip.estado-EXPIRADA,
      .chip.pago-RECHAZADO,
      .chip.pago-DEVUELTO {
        background: rgba(251, 113, 133, 0.12);
        color: #fda4af;
        border: 1px solid rgba(251, 113, 133, 0.35);
      }
      .acciones {
        display: flex;
        gap: 0.75rem;
        flex-wrap: wrap;
      }
      .tabla-wrap {
        overflow-x: auto;
      }
      /* Cada pago en un solo renglón (monto incluido); si no entra, scrollea el wrap. */
      .reforma-table th,
      .reforma-table td {
        white-space: nowrap;
      }
      .num {
        text-align: right;
      }
      .paginacion {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.75rem;
        margin-top: 0.75rem;
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
        width: min(480px, 92vw);
        z-index: 41;
      }
      .modal h3 {
        margin: 0 0 0.5rem;
        color: var(--reforma-text);
      }
      .consecuencias {
        list-style: none;
        margin: 0.5rem 0 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.6rem;
        color: var(--reforma-text);
        font-size: 0.92rem;
      }
      .consecuencias li {
        display: flex;
        gap: 0.6rem;
        align-items: flex-start;
      }
      .consecuencias i {
        color: var(--reforma-accent);
        margin-top: 0.15rem;
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
export class SuscripcionComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly suscripcion = signal<Suscripcion | null>(null);
  readonly pagos = signal<PaginaPagos | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly esEmpleado = signal(false);
  readonly procesando = signal(false);
  readonly modalCancelar = signal(false);
  readonly errorModal = signal<string | null>(null);

  /** Aviso al llegar desde /planes con un downgrade recién programado. */
  readonly avisoProgramado = computed(() => {
    const plan = this.route.snapshot.queryParamMap.get('programado');
    const s = this.suscripcion();
    return plan && s?.planPendiente === plan
      ? `Listo: el cambio a ${plan} queda programado para el fin del ciclo actual.`
      : null;
  });

  ngOnInit(): void {
    this.cargarEstado();
    this.cargarPagos(0);
  }

  estadoLabel(estado: string | null): string {
    return estado ? (ESTADO_LABEL[estado] ?? estado) : '—';
  }

  pagoLabel(estado: string | null): string {
    return estado ? (PAGO_LABEL[estado] ?? estado) : '—';
  }

  fechaFin(): string {
    const iso = this.suscripcion()?.fechaFinPeriodo;
    return iso ? new Date(iso).toLocaleDateString('es-AR') : '—';
  }

  cargarPagos(pagina: number): void {
    this.api.getPagosSuscripcion(pagina, 10).subscribe({
      next: (pg) => this.pagos.set(pg),
      error: () => this.pagos.set({ contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 }),
    });
  }

  cancelar(): void {
    this.procesando.set(true);
    this.errorModal.set(null);
    this.api.cancelarSuscripcion().subscribe({
      next: (s) => {
        this.procesando.set(false);
        this.modalCancelar.set(false);
        this.suscripcion.set(s);
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(false);
        this.errorModal.set(mensajeErrorHttp(err, 'No se pudo cancelar la suscripción.'));
      },
    });
  }

  reactivar(): void {
    this.procesando.set(true);
    this.error.set(null);
    this.api.reactivarSuscripcion().subscribe({
      next: (s) => {
        this.procesando.set(false);
        this.suscripcion.set(s);
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo revertir el cambio programado.'));
      },
    });
  }

  private cargarEstado(): void {
    this.api.getSuscripcion().subscribe({
      next: (s) => {
        this.suscripcion.set(s);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.esEmpleado.set(true);
        } else {
          this.error.set(mensajeErrorHttp(err, 'No se pudo cargar la suscripción.'));
        }
        this.cargando.set(false);
      },
    });
  }
}
