import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { CompraResumen, textoConfirmacionEliminarFactura } from '../../../data/models/compra.model';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { areaChart, donutChart } from '../shared/apex-charts';
import { sumarPorMes, topConOtros } from '../shared/panel-utils';
import { OrdenTabla } from '../../../shared/orden-tabla';

@Component({
  selector: 'app-compras',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    DecimalPipe,
    KpiCardComponent,
    ChartCardComponent,
    ApexChartComponent,
  ],
  template: `
    <header class="reforma-toolbar">
      <h2 class="reforma-page-title">Compras</h2>
      <a routerLink="nueva" class="reforma-btn"><i class="pi pi-plus"></i> Nueva factura</a>
    </header>

    <section class="rf-grid-kpis">
      <reforma-kpi-card style="--rf-i: 0" icon="pi-shopping-cart" label="Compras" [value]="stats().total | number: '1.0-0'" />
      <reforma-kpi-card style="--rf-i: 1" icon="pi-dollar" label="Gasto total" [value]="'$ ' + (stats().gasto | number: '1.0-0')" accent="#06b6d4" [spark]="gastoMensual().valores" />
      <reforma-kpi-card style="--rf-i: 2" icon="pi-calculator" label="Ticket promedio" [value]="'$ ' + (stats().ticket | number: '1.0-0')" accent="#f472b6" />
      <reforma-kpi-card style="--rf-i: 3" icon="pi-calendar" label="Última compra" [value]="stats().ultima" accent="#34d399" />
    </section>

    <section class="rf-grid-2">
      <reforma-chart-card
        title="Gasto en compras por mes"
        icon="pi-chart-line"
        [loading]="cargando()"
        [empty]="!cargando() && gastoMensual().valores.length === 0"
        emptyText="Registrá compras para ver la evolución"
      >
        <reforma-apex [options]="chartGasto()" />
      </reforma-chart-card>
      <reforma-chart-card
        title="Compras por proveedor"
        icon="pi-chart-pie"
        [loading]="cargando()"
        [empty]="!cargando() && porProveedor().valores.length === 0"
        emptyText="Sin datos para mostrar"
      >
        <reforma-apex [options]="chartProveedor()" />
      </reforma-chart-card>
    </section>

    @if (compraEliminando()) {
      <section class="reforma-section eliminar">
        <h3 class="reforma-section-title">Eliminar factura {{ compraEliminando()!.numeroFactura }}</h3>
        <p class="reforma-alert reforma-alert-warn">
          <i class="pi pi-exclamation-triangle"></i>
          Se eliminarán todos los ítems asociados. Esto impactará el inventario y los valores calculados.
        </p>
        <p class="reforma-text-muted">Escribí exactamente la frase siguiente para confirmar:</p>
        <code class="reforma-confirm-frase">{{ fraseEliminarEsperada() }}</code>
        <label class="reforma-field">
          <span>Confirmación</span>
          <input class="reforma-input" [(ngModel)]="textoConfirmacionEliminar" autocomplete="off" />
        </label>
        <div class="acciones-confirmar">
          <button type="button" class="reforma-btn-ghost" (click)="cancelarEliminar()">Cancelar</button>
          <button
            type="button"
            class="reforma-btn-danger"
            [disabled]="!puedeConfirmarEliminar() || guardando()"
            (click)="confirmarEliminar()"
          >
            <i class="pi pi-trash"></i> Eliminar factura
          </button>
        </div>
      </section>
    }

    @if (!compraEliminando()) {
      <section class="lista">
        @if (cargando()) {
          <p class="reforma-empty">Cargando…</p>
        } @else if (compras().length === 0) {
          <p class="reforma-empty">Todavía no hay compras cargadas.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr>
                  <th class="sortable" [class.is-asc]="orden.esAsc('factura')" [class.is-desc]="orden.esDesc('factura')" (click)="orden.alternar('factura')">Factura</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('fecha')" [class.is-desc]="orden.esDesc('fecha')" (click)="orden.alternar('fecha')">Fecha</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('proveedor')" [class.is-desc]="orden.esDesc('proveedor')" (click)="orden.alternar('proveedor')">Proveedor</th>
                  <th class="num sortable" [class.is-asc]="orden.esAsc('total')" [class.is-desc]="orden.esDesc('total')" (click)="orden.alternar('total')">Total</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('estado')" [class.is-desc]="orden.esDesc('estado')" (click)="orden.alternar('estado')">Estado</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (c of comprasOrdenadas(); track c.id) {
                  <tr>
                    <td>{{ c.numeroFactura }}</td>
                    <td>{{ c.fechaCompra }}</td>
                    <td>{{ c.nombreProveedor }}</td>
                    <td class="num">$ {{ c.totalFactura | number: '1.2-2' }}</td>
                    <td>
                      <span class="estado-chip" [class.borrador]="c.estado === 'BORRADOR'">
                        {{ c.estado }}
                      </span>
                    </td>
                    <td class="acciones">
                      <div class="acciones-inner">
                        <a [routerLink]="[c.id]" class="reforma-btn-ghost reforma-btn-sm">
                          <i class="pi pi-eye"></i> Ver
                        </a>
                        <button type="button" class="reforma-btn-danger" (click)="iniciarEliminar(c)">
                          <i class="pi pi-trash"></i> Eliminar
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    }

    @if (error()) {
      <p class="reforma-alert reforma-alert-error">
        <i class="pi pi-exclamation-circle"></i> {{ error() }}
      </p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .eliminar {
        margin: 1.25rem 0;
      }
      .reforma-text-muted {
        color: var(--reforma-text-dim);
        margin: 0.5rem 0;
      }
      .reforma-confirm-frase {
        display: block;
        padding: 0.6rem 0.85rem;
        background: var(--glass-bg-strong);
        border: 1px solid var(--glass-border-strong);
        border-radius: 8px;
        color: var(--reforma-text);
        font-family: ui-monospace, 'SF Mono', Menlo, monospace;
        font-size: 0.85rem;
        margin-bottom: 0.75rem;
      }
      .acciones-confirmar {
        display: flex;
        gap: 0.75rem;
        justify-content: flex-end;
        margin-top: 1rem;
      }
      .lista .acciones {
        text-align: right;
        width: 1%;
        white-space: nowrap;
      }
      .lista .acciones-inner {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
      }
      .estado-chip {
        display: inline-flex;
        padding: 0.2rem 0.6rem;
        border-radius: 999px;
        font-size: 0.78rem;
        font-weight: 600;
        background: rgba(52, 211, 153, 0.16);
        color: #bbf7d0;
      }
      .estado-chip.borrador {
        background: rgba(251, 191, 36, 0.16);
        color: #fde68a;
      }
    `,
  ],
})
export class ComprasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly compras = signal<CompraResumen[]>([]);
  readonly orden = new OrdenTabla();
  readonly comprasOrdenadas = computed(() =>
    this.orden.ordenar(this.compras(), {
      factura: (c) => c.numeroFactura,
      fecha: (c) => c.fechaCompra,
      proveedor: (c) => c.nombreProveedor,
      total: (c) => c.totalFactura,
      estado: (c) => c.estado,
    }),
  );
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly compraEliminando = signal<CompraResumen | null>(null);
  readonly fraseEliminarEsperada = signal('');

  textoConfirmacionEliminar = '';

  readonly stats = computed(() => {
    const cs = this.compras();
    const gasto = cs.reduce((s, c) => s + c.totalFactura, 0);
    const ultima = cs.reduce((max, c) => (c.fechaCompra > max ? c.fechaCompra : max), '');
    return {
      total: cs.length,
      gasto,
      ticket: cs.length > 0 ? gasto / cs.length : 0,
      ultima: ultima || '—',
    };
  });

  readonly gastoMensual = computed(() =>
    sumarPorMes(this.compras(), (c) => c.fechaCompra, (c) => c.totalFactura),
  );
  readonly porProveedor = computed(() => {
    const acum = new Map<string, number>();
    for (const c of this.compras()) {
      const label = c.nombreProveedor || c.codigoProveedor || '—';
      acum.set(label, (acum.get(label) ?? 0) + c.totalFactura);
    }
    return topConOtros(
      [...acum.entries()].map(([label, v]) => ({ label, valor: v })),
      6,
    );
  });

  readonly chartGasto = computed(() =>
    areaChart({
      categories: this.gastoMensual().labels,
      series: [{ name: 'Gasto', data: this.gastoMensual().valores }],
      money: true,
      height: 300,
    }),
  );
  readonly chartProveedor = computed(() =>
    donutChart({
      labels: this.porProveedor().labels,
      series: this.porProveedor().valores,
      totalLabel: 'Gasto total',
      totalFormatter: (t) => '$ ' + Math.round(t).toLocaleString('en-US'),
      money: true,
      height: 300,
    }),
  );

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargarCompras();
  }

  iniciarEliminar(c: CompraResumen): void {
    this.compraEliminando.set(c);
    this.fraseEliminarEsperada.set(textoConfirmacionEliminarFactura(c.numeroFactura));
    this.textoConfirmacionEliminar = '';
  }

  cancelarEliminar(): void {
    this.compraEliminando.set(null);
    this.textoConfirmacionEliminar = '';
    this.fraseEliminarEsperada.set('');
  }

  puedeConfirmarEliminar(): boolean {
    return this.textoConfirmacionEliminar.trim() === this.fraseEliminarEsperada();
  }

  confirmarEliminar(): void {
    const c = this.compraEliminando();
    if (!c || !this.puedeConfirmarEliminar()) return;

    this.guardando.set(true);
    this.error.set(null);
    this.api.eliminarCompra(this.idGranja, c.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cancelarEliminar();
        this.recargarCompras();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la factura'));
      },
    });
  }

  private recargarCompras(): void {
    this.cargando.set(true);
    this.api.getCompras(this.idGranja).subscribe({
      next: (list) => {
        this.compras.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar las compras');
        this.cargando.set(false);
      },
    });
  }
}
