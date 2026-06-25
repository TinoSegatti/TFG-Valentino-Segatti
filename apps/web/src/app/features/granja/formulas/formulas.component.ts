import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import {
  FormulaCompleta,
  FormulaResumen,
  textoConfirmacionEliminarFormula,
} from '../../../data/models/formula.model';
import { CsvImportResult, descargarBlobComoArchivo } from '../../../data/models/csv.model';
import { CatalogoCsvBarComponent } from '../shared/catalogo-csv-bar.component';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { barChart, donutChart } from '../shared/apex-charts';
import { topConOtros } from '../shared/panel-utils';
import { OrdenTabla } from '../../../shared/orden-tabla';

@Component({
  selector: 'app-formulas',
  standalone: true,
  imports: [
    RouterLink,
    DecimalPipe,
    FormsModule,
    CatalogoCsvBarComponent,
    KpiCardComponent,
    ChartCardComponent,
    ApexChartComponent,
  ],
  template: `
    <header class="reforma-toolbar">
      <h2 class="reforma-page-title">Fórmulas dietarias</h2>
      <a routerLink="nueva" class="reforma-btn"><i class="pi pi-plus"></i> Crear fórmula</a>
    </header>

    <section class="rf-grid-kpis">
      <reforma-kpi-card style="--rf-i: 0" icon="pi-sliders-h" label="Fórmulas" [value]="stats().total | number: '1.0-0'" />
      <reforma-kpi-card style="--rf-i: 1" icon="pi-check-circle" label="Completas" [value]="stats().completas | number: '1.0-0'" accent="#34d399" />
      <reforma-kpi-card style="--rf-i: 2" icon="pi-dollar" label="Costo promedio /lote" [value]="'$ ' + (stats().promedio | number: '1.0-0')" accent="#06b6d4" />
      <reforma-kpi-card style="--rf-i: 3" icon="pi-arrow-up" label="Más cara" [value]="'$ ' + (stats().maxCosto | number: '1.0-0')" [sub]="stats().maxCodigo" accent="#f472b6" />
    </section>

    <section class="rf-grid-2">
      <reforma-chart-card
        title="Costo por fórmula (top 10)"
        icon="pi-chart-bar"
        [loading]="cargando()"
        [empty]="!cargando() && costoPorFormula().valores.length === 0"
        emptyText="Cargá fórmulas con costo para ver el gráfico"
      >
        <reforma-apex [options]="chartCosto()" />
      </reforma-chart-card>
      <reforma-chart-card
        [title]="detalle() ? 'Composición — ' + detalle()!.codigoFormula : 'Composición de la fórmula'"
        icon="pi-chart-pie"
        [loading]="cargandoDetalle()"
        [empty]="!cargandoDetalle() && composicion().valores.length === 0"
        emptyText="Sin fórmulas completas para graficar la composición"
      >
        <reforma-apex [options]="chartComposicion()" />
      </reforma-chart-card>
    </section>

    <section class="rf-panel">
      <reforma-chart-card
        title="Evolución de costos"
        icon="pi-chart-line"
        [empty]="true"
        emptyText="Disponible próximamente — requiere histórico de costos en el backend"
      />
    </section>

    <app-catalogo-csv-bar
      #csvBar
      [trabajando]="csvTrabajando()"
      [resultado]="csvResultado()"
      columnasAyuda="codigo_formula, descripcion_formula, codigo_animal, codigo_materia_prima, cantidad_kg"
      (exportar)="exportarCsv()"
      (importar)="importarCsv($event)"
    />

    @if (formulaEliminando()) {
      <section class="reforma-section eliminar">
        <h3 class="reforma-section-title">Eliminar fórmula {{ formulaEliminando()!.codigoFormula }}</h3>
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
            <i class="pi pi-trash"></i> Eliminar fórmula
          </button>
        </div>
      </section>
    }

    @if (!formulaEliminando()) {
      <section class="lista">
        @if (cargando()) {
          <p class="reforma-empty">Cargando…</p>
        } @else if (formulas().length === 0) {
          <p class="reforma-empty">Todavía no hay fórmulas cargadas.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr>
                  <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('descripcion')" [class.is-desc]="orden.esDesc('descripcion')" (click)="orden.alternar('descripcion')">Descripción</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('animal')" [class.is-desc]="orden.esDesc('animal')" (click)="orden.alternar('animal')">Animal</th>
                  <th class="num sortable" [class.is-asc]="orden.esAsc('costo')" [class.is-desc]="orden.esDesc('costo')" (click)="orden.alternar('costo')">Costo fórmula</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('estado')" [class.is-desc]="orden.esDesc('estado')" (click)="orden.alternar('estado')">Estado</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (f of formulasOrdenadas(); track f.id) {
                  <tr>
                    <td>{{ f.codigoFormula }}</td>
                    <td>{{ f.descripcionFormula }}</td>
                    <td>{{ f.descripcionAnimal }} ({{ f.codigoAnimal }})</td>
                    <td class="num">$ {{ f.costoTotalFormula | number: '1.2-2' }}</td>
                    <td>
                      <span class="estado-chip" [class.incompleta]="!f.completa">
                        {{ f.completa ? 'Completa' : 'Incompleta' }}
                      </span>
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
        padding: 0.2rem 0.6rem;
        border-radius: 999px;
        font-size: 0.78rem;
        font-weight: 600;
        background: rgba(52, 211, 153, 0.16);
        color: #bbf7d0;
      }
      .estado-chip.incompleta {
        background: rgba(251, 191, 36, 0.16);
        color: #fde68a;
      }
    `,
  ],
})
export class FormulasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly formulas = signal<FormulaResumen[]>([]);
  readonly orden = new OrdenTabla();
  readonly formulasOrdenadas = computed(() =>
    this.orden.ordenar(this.formulas(), {
      codigo: (f) => f.codigoFormula,
      descripcion: (f) => f.descripcionFormula,
      animal: (f) => f.descripcionAnimal,
      costo: (f) => f.costoTotalFormula,
      estado: (f) => (f.completa ? 1 : 0),
    }),
  );
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly formulaEliminando = signal<FormulaResumen | null>(null);
  readonly fraseEliminarEsperada = signal('');

  // Detalle de una fórmula representativa para el donut de composición.
  readonly detalle = signal<FormulaCompleta | null>(null);
  readonly cargandoDetalle = signal(true);

  readonly stats = computed(() => {
    const fs = this.formulas();
    if (fs.length === 0) {
      return { total: 0, completas: 0, promedio: 0, maxCosto: 0, maxCodigo: '' };
    }
    const conCosto = fs.filter((f) => f.costoTotalFormula > 0);
    const promedio =
      conCosto.length > 0 ? conCosto.reduce((s, f) => s + f.costoTotalFormula, 0) / conCosto.length : 0;
    const max = fs.reduce((a, b) => (b.costoTotalFormula > a.costoTotalFormula ? b : a), fs[0]);
    return {
      total: fs.length,
      completas: fs.filter((f) => f.completa).length,
      promedio,
      maxCosto: max.costoTotalFormula,
      maxCodigo: max.codigoFormula,
    };
  });

  readonly costoPorFormula = computed(() =>
    topConOtros(
      this.formulas()
        .filter((f) => f.costoTotalFormula > 0)
        .map((f) => ({ label: f.codigoFormula, valor: f.costoTotalFormula })),
      10,
    ),
  );
  readonly composicion = computed(() => {
    const d = this.detalle();
    if (!d) return { labels: [] as string[], valores: [] as number[] };
    return topConOtros(
      d.lineas.map((l) => ({
        label: l.codigoMateriaPrima,
        valor: l.porcentajeFormula > 0 ? l.porcentajeFormula : l.cantidadKg,
      })),
      8,
    );
  });

  readonly chartCosto = computed(() =>
    barChart({
      categories: this.costoPorFormula().labels,
      series: [{ name: 'Costo', data: this.costoPorFormula().valores }],
      distributed: true,
      money: true,
      height: 300,
    }),
  );
  readonly chartComposicion = computed(() =>
    donutChart({
      labels: this.composicion().labels,
      series: this.composicion().valores,
      totalLabel: 'Materias',
      totalFormatter: () => String(this.composicion().labels.length),
      height: 300,
    }),
  );

  readonly csvTrabajando = signal(false);
  readonly csvResultado = signal<CsvImportResult | null>(null);
  private readonly csvBar = viewChild(CatalogoCsvBarComponent);

  textoConfirmacionEliminar = '';

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargar();
  }

  iniciarEliminar(f: FormulaResumen): void {
    this.formulaEliminando.set(f);
    this.fraseEliminarEsperada.set(textoConfirmacionEliminarFormula(f.codigoFormula));
    this.textoConfirmacionEliminar = '';
  }

  cancelarEliminar(): void {
    this.formulaEliminando.set(null);
    this.textoConfirmacionEliminar = '';
    this.fraseEliminarEsperada.set('');
  }

  puedeConfirmarEliminar(): boolean {
    return this.textoConfirmacionEliminar.trim() === this.fraseEliminarEsperada();
  }

  confirmarEliminar(): void {
    const f = this.formulaEliminando();
    if (!f || !this.puedeConfirmarEliminar()) return;
    this.guardando.set(true);
    this.api.desactivarFormula(this.idGranja, f.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cancelarEliminar();
        this.recargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la formula'));
      },
    });
  }

  private recargar(): void {
    this.cargando.set(true);
    this.api.getFormulas(this.idGranja).subscribe({
      next: (list) => {
        this.formulas.set(list);
        this.cargando.set(false);
        this.cargarComposicion(list);
      },
      error: () => {
        this.error.set('No se pudieron cargar las formulas');
        this.cargando.set(false);
        this.cargandoDetalle.set(false);
      },
    });
  }

  /** Carga el detalle de la fórmula completa más cara para el donut de composición. */
  private cargarComposicion(list: FormulaResumen[]): void {
    const candidatas = list.filter((f) => f.completa);
    const elegida = (candidatas.length > 0 ? candidatas : list).reduce(
      (a, b) => (b.costoTotalFormula > a.costoTotalFormula ? b : a),
      (candidatas.length > 0 ? candidatas : list)[0],
    );
    if (!elegida) {
      this.cargandoDetalle.set(false);
      return;
    }
    this.cargandoDetalle.set(true);
    this.api.getFormula(this.idGranja, elegida.id).subscribe({
      next: (d) => {
        this.detalle.set(d);
        this.cargandoDetalle.set(false);
      },
      error: () => this.cargandoDetalle.set(false),
    });
  }

  exportarCsv(): void {
    this.csvTrabajando.set(true);
    this.api.exportarFormulasCsv(this.idGranja).subscribe({
      next: (blob) => {
        descargarBlobComoArchivo(blob, `formulas-${this.idGranja}.csv`);
        this.csvTrabajando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.csvTrabajando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo exportar el CSV'));
      },
    });
  }

  importarCsv(archivo: File): void {
    this.csvTrabajando.set(true);
    this.csvResultado.set(null);
    this.error.set(null);
    this.api.importarFormulasCsv(this.idGranja, archivo).subscribe({
      next: (resultado) => {
        this.csvResultado.set(resultado);
        this.csvTrabajando.set(false);
        this.csvBar()?.reset();
        if (resultado.filasOk > 0) this.recargar();
      },
      error: (err: HttpErrorResponse) => {
        this.csvTrabajando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo importar el CSV'));
      },
    });
  }
}
