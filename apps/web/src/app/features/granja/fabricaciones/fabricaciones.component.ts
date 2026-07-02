import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import {
  FabricacionResumen,
  MateriaPrimaConsumo,
  textoConfirmacionEliminarFabricacion,
} from '../../../data/models/fabricacion.model';
import { FormulaResumen } from '../../../data/models/formula.model';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { barChart, donutChart } from '../shared/apex-charts';
import { sumarPorMes, topConOtros, topNombrado } from '../shared/panel-utils';
import { OrdenTabla } from '../../../shared/orden-tabla';

@Component({
  selector: 'app-fabricaciones',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    DecimalPipe,
    DatePipe,
    KpiCardComponent,
    ChartCardComponent,
    ApexChartComponent,
  ],
  template: `
    <header class="reforma-toolbar">
      <h2 class="reforma-page-title">Fabricaciones</h2>
      <a routerLink="nueva" class="reforma-btn"><i class="pi pi-plus"></i> Nueva fabricación</a>
    </header>

    <section class="rf-grid-kpis">
      <reforma-kpi-card style="--rf-i: 0" icon="pi-cog" label="Fabricaciones" [value]="stats().total | number: '1.0-0'" />
      <reforma-kpi-card style="--rf-i: 1" icon="pi-database" label="Kilos producidos" [value]="(stats().kilos | number: '1.0-0') + ' kg'" accent="#06b6d4" [spark]="produccionMensual().valores" />
      <reforma-kpi-card style="--rf-i: 2" icon="pi-dollar" label="Costo total" [value]="'$ ' + (stats().costo | number: '1.0-0')" accent="#f472b6" />
      <reforma-kpi-card style="--rf-i: 3" icon="pi-calculator" label="Costo promedio /kg" [value]="'$ ' + (stats().costoKilo | number: '1.2-2')" accent="#34d399" />
    </section>

    <section class="rf-grid-2">
      <reforma-chart-card
        title="Producción por mes (kg)"
        icon="pi-chart-bar"
        [loading]="cargando()"
        [empty]="!cargando() && produccionMensual().valores.length === 0"
        emptyText="Registrá fabricaciones para ver la producción"
      >
        <reforma-apex [options]="chartProduccion()" />
      </reforma-chart-card>
      <reforma-chart-card
        title="Consumo de materias primas (kg)"
        icon="pi-chart-pie"
        [loading]="cargandoConsumo()"
        [empty]="!cargandoConsumo() && consumo().valores.length === 0"
        emptyText="Registrá fabricaciones para ver el consumo de materias primas"
      >
        <reforma-apex [options]="chartConsumo()" />
      </reforma-chart-card>
    </section>

    <section class="rf-panel">
      <reforma-chart-card
        title="Fórmulas más fabricadas (kg producidos)"
        icon="pi-chart-bar"
        [loading]="cargando()"
        [empty]="!cargando() && formulasMasFabricadas().valores.length === 0"
        emptyText="Registrá fabricaciones para ver las fórmulas más producidas"
      >
        <reforma-apex [options]="chartFormulasMasFabricadas()" />
      </reforma-chart-card>
    </section>

    @if (fabricacionEliminando()) {
      <section class="reforma-section eliminar">
        <h3 class="reforma-section-title">Eliminar fabricación {{ fabricacionEliminando()!.codigoFabricacion }}</h3>
        <p class="reforma-alert reforma-alert-warn">
          <i class="pi pi-exclamation-triangle"></i>
          Se restaurará el stock de las materias primas usadas. El costo registrado se conserva en el historial hasta que se elimine la fabricación.
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
            <i class="pi pi-trash"></i> Eliminar fabricación
          </button>
        </div>
      </section>
    }

    @if (!fabricacionEliminando()) {
      <section class="lista">
        @if (cargando()) {
          <p class="reforma-empty">Cargando…</p>
        } @else if (fabricaciones().length === 0) {
          <p class="reforma-empty">Todavía no hay fabricaciones cargadas.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr>
                  <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('fecha')" [class.is-desc]="orden.esDesc('fecha')" (click)="orden.alternar('fecha')">Fecha</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('descripcion')" [class.is-desc]="orden.esDesc('descripcion')" (click)="orden.alternar('descripcion')">Descripción</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('formula')" [class.is-desc]="orden.esDesc('formula')" (click)="orden.alternar('formula')">Fórmula</th>
                  <th class="num sortable" [class.is-asc]="orden.esAsc('veces')" [class.is-desc]="orden.esDesc('veces')" (click)="orden.alternar('veces')">Veces</th>
                  <th class="num sortable" [class.is-asc]="orden.esAsc('costo')" [class.is-desc]="orden.esDesc('costo')" (click)="orden.alternar('costo')">Costo</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('estado')" [class.is-desc]="orden.esDesc('estado')" (click)="orden.alternar('estado')">Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (f of fabricacionesOrdenadas(); track f.id) {
                  <tr [class.sin-stock]="f.sinExistencias">
                    <td>{{ f.codigoFabricacion }}</td>
                    <td>{{ f.fechaFabricacion | date: 'dd/MM/yyyy' : 'UTC' }}</td>
                    <td>{{ f.descripcionFabricacion }}</td>
                    <td>{{ f.codigoFormula ?? '—' }}</td>
                    <td class="num">{{ f.veces | number: '1.3-3' }}</td>
                    <td class="num">$ {{ f.costoTotalFabricacion | number: '1.3-3' }}</td>
                    <td>
                      <span class="estado-chip" [class.borrador]="f.estado === 'BORRADOR'">
                        {{ f.estado }}
                      </span>
                      @if (f.sinExistencias) {
                        <span class="estado-chip warn"><i class="pi pi-exclamation-triangle"></i> Sin stock</span>
                      }
                    </td>
                    <td class="acciones">
                      <div class="acciones-inner">
                        <a [routerLink]="[f.id]" class="reforma-btn-ghost reforma-btn-sm">
                          <i class="pi pi-eye"></i> Ver
                        </a>
                        <button type="button" class="reforma-btn-danger" (click)="iniciarEliminar(f)">
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
        align-items: center;
        gap: 0.25rem;
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
      .estado-chip.warn {
        margin-left: 0.35rem;
        background: rgba(248, 113, 113, 0.16);
        color: #fecaca;
      }
      tr.sin-stock td {
        background: rgba(248, 113, 113, 0.06);
      }
    `,
  ],
})
export class FabricacionesComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  fabricaciones = signal<FabricacionResumen[]>([]);
  readonly orden = new OrdenTabla();
  readonly fabricacionesOrdenadas = computed(() =>
    this.orden.ordenar(this.fabricaciones(), {
      codigo: (f) => f.codigoFabricacion,
      fecha: (f) => f.fechaFabricacion,
      descripcion: (f) => f.descripcionFabricacion,
      formula: (f) => f.codigoFormula,
      veces: (f) => f.veces,
      costo: (f) => f.costoTotalFabricacion,
      estado: (f) => f.estado,
    }),
  );
  cargando = signal(true);
  guardando = signal(false);
  error = signal<string | null>(null);
  fabricacionEliminando = signal<FabricacionResumen | null>(null);
  textoConfirmacionEliminar = '';

  // Consumo agregado de materias primas en todas las fabricaciones (donut).
  readonly consumoMaterias = signal<MateriaPrimaConsumo[]>([]);
  readonly cargandoConsumo = signal(true);

  readonly stats = computed(() => {
    const fs = this.fabricaciones();
    const kilos = fs.reduce((s, f) => s + f.veces * 1000, 0);
    const costo = fs.reduce((s, f) => s + f.costoTotalFabricacion, 0);
    return {
      total: fs.length,
      kilos,
      costo,
      costoKilo: kilos > 0 ? costo / kilos : 0,
    };
  });

  readonly produccionMensual = computed(() =>
    sumarPorMes(this.fabricaciones(), (f) => f.fechaFabricacion, (f) => f.veces * 1000),
  );
  readonly chartProduccion = computed(() =>
    barChart({
      categories: this.produccionMensual().labels,
      series: [{ name: 'Kilos', data: this.produccionMensual().valores }],
      height: 300,
    }),
  );

  // Donut de consumo de MP (kilos usados en fabricaciones), top 8 + "Otros".
  // La etiqueta usa el nombre de la MP para que se lea al pasar por encima de cada porción.
  readonly consumo = computed(() =>
    topConOtros(
      this.consumoMaterias().map((c) => ({
        label: c.nombreMateriaPrima || c.codigoMateriaPrima,
        valor: c.totalKg,
      })),
      8,
    ),
  );
  readonly chartConsumo = computed(() =>
    donutChart({
      labels: this.consumo().labels,
      series: this.consumo().valores,
      totalLabel: 'Total consumido',
      totalFormatter: (t) => Math.round(t).toLocaleString('es-AR') + ' kg',
      height: 300,
    }),
  );

  // Catálogo de fórmulas (para mapear código → nombre en el tooltip de la barra;
  // el resumen de fabricación solo trae el código de fórmula).
  readonly formulasCatalogo = signal<FormulaResumen[]>([]);
  private readonly nombreFormulaPorCodigo = computed(() => {
    const map = new Map<string, string>();
    for (const f of this.formulasCatalogo()) {
      map.set(f.codigoFormula, f.descripcionFormula);
    }
    return map;
  });

  // Fórmulas más fabricadas históricamente, por kilos producidos (veces × 1000).
  // Eje = código de fórmula, tooltip = nombre (descripción).
  readonly formulasMasFabricadas = computed(() => {
    const nombreDe = this.nombreFormulaPorCodigo();
    const acum = new Map<string, number>();
    for (const f of this.fabricaciones()) {
      const clave = f.codigoFormula ?? '—';
      acum.set(clave, (acum.get(clave) ?? 0) + f.veces * 1000);
    }
    return topNombrado(
      [...acum.entries()].map(([label, valor]) => ({
        label,
        nombre: nombreDe.get(label) ?? label,
        valor,
      })),
      10,
    );
  });
  readonly chartFormulasMasFabricadas = computed(() =>
    barChart({
      categories: this.formulasMasFabricadas().labels,
      series: [{ name: 'Kilos producidos', data: this.formulasMasFabricadas().valores }],
      distributed: true,
      height: 300,
      tooltipTitles: this.formulasMasFabricadas().nombres,
    }),
  );

  fraseEliminarEsperada = () => {
    const f = this.fabricacionEliminando();
    return f ? textoConfirmacionEliminarFabricacion(f.codigoFabricacion) : '';
  };

  ngOnInit(): void {
    this.cargar();
  }

  private idGranja(): string {
    return this.route.parent!.snapshot.paramMap.get('idGranja')!;
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.getFabricaciones(this.idGranja()).subscribe({
      next: (lista) => {
        this.fabricaciones.set(lista);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cargar las fabricaciones'));
        this.cargando.set(false);
      },
    });
    this.cargarConsumo();
    this.cargarFormulas();
  }

  /** Catálogo de fórmulas para mapear código → nombre en el tooltip de la barra. */
  private cargarFormulas(): void {
    this.api.getFormulas(this.idGranja()).subscribe({
      next: (lista) => this.formulasCatalogo.set(lista),
      error: () => this.formulasCatalogo.set([]),
    });
  }

  /** Carga el consumo agregado de materias primas en todas las fabricaciones (donut). */
  private cargarConsumo(): void {
    this.cargandoConsumo.set(true);
    this.api.getFabricacionesConsumoMaterias(this.idGranja()).subscribe({
      next: (consumo) => {
        this.consumoMaterias.set(consumo);
        this.cargandoConsumo.set(false);
      },
      error: () => this.cargandoConsumo.set(false),
    });
  }

  iniciarEliminar(f: FabricacionResumen): void {
    this.fabricacionEliminando.set(f);
    this.textoConfirmacionEliminar = '';
    this.error.set(null);
  }

  cancelarEliminar(): void {
    this.fabricacionEliminando.set(null);
    this.textoConfirmacionEliminar = '';
  }

  puedeConfirmarEliminar(): boolean {
    return this.textoConfirmacionEliminar === this.fraseEliminarEsperada();
  }

  confirmarEliminar(): void {
    const f = this.fabricacionEliminando();
    if (!f || !this.puedeConfirmarEliminar()) return;
    this.guardando.set(true);
    this.api.eliminarFabricacion(this.idGranja(), f.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cancelarEliminar();
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la fabricacion'));
      },
    });
  }
}
