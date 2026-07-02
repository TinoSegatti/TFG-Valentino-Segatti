import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { debounceTime, Subject } from 'rxjs';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Animal, AnimalRequest } from '../../../data/models/animal.model';
import { CsvImportResult, descargarBlobComoArchivo } from '../../../data/models/csv.model';
import { CatalogoCsvBarComponent } from '../shared/catalogo-csv-bar.component';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { barChart, donutChart } from '../shared/apex-charts';
import { topConOtros } from '../shared/panel-utils';
import { OrdenTabla } from '../../../shared/orden-tabla';

/**
 * Listado, alta rápida y baja lógica del catálogo de Animales (RF-ANI-001 / RF-ANI-002).
 * Aplica la política ADR 0005: la baja es lógica y reusar el código crea entidad nueva.
 */
@Component({
  selector: 'app-animales',
  standalone: true,
  imports: [
    FormsModule,
    DecimalPipe,
    CatalogoCsvBarComponent,
    KpiCardComponent,
    ChartCardComponent,
    ApexChartComponent,
  ],
  template: `
    <h2 class="reforma-page-title">Animales</h2>

    <section class="rf-grid-kpis">
      <reforma-kpi-card style="--rf-i: 0" icon="pi-pause" label="Animales activos" [value]="stats().total | number: '1.0-0'" />
      <reforma-kpi-card style="--rf-i: 1" icon="pi-tags" label="Categorías" [value]="stats().categorias | number: '1.0-0'" accent="#06b6d4" />
      <reforma-kpi-card style="--rf-i: 2" icon="pi-comment" label="Con observaciones" [value]="stats().conObs | number: '1.0-0'" accent="#f472b6" />
      <reforma-kpi-card style="--rf-i: 3" icon="pi-question-circle" label="Sin categoría" [value]="stats().sinCategoria | number: '1.0-0'" accent="#fbbf24" />
    </section>

    <section class="rf-grid-halves">
      <reforma-chart-card
        title="Distribución por categoría"
        icon="pi-chart-pie"
        [loading]="cargando()"
        [empty]="!cargando() && porCategoria().valores.length === 0"
        emptyText="Cargá animales para ver su distribución"
      >
        <reforma-apex [options]="chartDonut()" />
      </reforma-chart-card>
      <reforma-chart-card
        title="Animales por categoría"
        icon="pi-chart-bar"
        [loading]="cargando()"
        [empty]="!cargando() && porCategoria().valores.length === 0"
        emptyText="Sin datos para mostrar"
      >
        <reforma-apex [options]="chartBar()" />
      </reforma-chart-card>
    </section>

    <app-catalogo-csv-bar
      #csvBar
      [trabajando]="csvTrabajando()"
      [resultado]="csvResultado()"
      columnasAyuda="codigo, descripcion, categoria, observaciones"
      (exportar)="exportarCsv()"
      (importar)="importarCsv($event)"
    />

    <section class="reforma-section alta">
      <h3 class="reforma-section-title">Alta rápida</h3>
      <form (ngSubmit)="crear()" #f="ngForm">
        <label class="reforma-field">
          <span>Código</span>
          <input class="reforma-input" name="codigo" [(ngModel)]="form.codigoAnimal" maxlength="50" required />
        </label>
        <label class="reforma-field">
          <span>Descripción</span>
          <input class="reforma-input" name="descripcion" [(ngModel)]="form.descripcionAnimal" maxlength="200" required />
        </label>
        <label class="reforma-field">
          <span>Categoría</span>
          <input class="reforma-input" name="categoria" [(ngModel)]="form.categoriaAnimal" maxlength="100" />
        </label>
        <label class="reforma-field full">
          <span>Observaciones</span>
          <textarea
            class="reforma-input"
            name="observaciones"
            [(ngModel)]="form.observaciones"
            maxlength="5000"
            rows="2"
          ></textarea>
        </label>
        <button class="reforma-btn" type="submit" [disabled]="creando() || f.invalid">
          <i class="pi pi-plus"></i> Crear
        </button>
      </form>
      @if (error()) {
        <p class="reforma-alert reforma-alert-error">
          <i class="pi pi-exclamation-circle"></i> {{ error() }}
        </p>
      }
    </section>

    <section class="lista">
      <header class="lista-header">
        <h3 class="reforma-section-title">Activos ({{ items().length }})</h3>
        <div class="buscar-wrap">
          <i class="pi pi-search"></i>
          <input
            class="reforma-input buscar"
            type="search"
            placeholder="Buscar por descripción…"
            [ngModel]="filtro()"
            (ngModelChange)="onFiltroChange($event)"
          />
        </div>
      </header>

      @if (cargando()) {
        <p class="reforma-empty">Cargando…</p>
      } @else if (items().length === 0) {
        <p class="reforma-empty">Todavía no cargaste ningún animal (o ninguno coincide con el filtro).</p>
      } @else {
        <div class="reforma-table-wrap">
          <table class="reforma-table">
            <thead>
              <tr>
                <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('descripcion')" [class.is-desc]="orden.esDesc('descripcion')" (click)="orden.alternar('descripcion')">Descripción</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('categoria')" [class.is-desc]="orden.esDesc('categoria')" (click)="orden.alternar('categoria')">Categoría</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('observaciones')" [class.is-desc]="orden.esDesc('observaciones')" (click)="orden.alternar('observaciones')">Observaciones</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              @for (a of itemsOrdenados(); track a.id) {
                <tr>
                  <td>{{ a.codigoAnimal }}</td>
                  <td>{{ a.descripcionAnimal }}</td>
                  <td>{{ a.categoriaAnimal ?? '—' }}</td>
                  <td class="obs">{{ a.observaciones ?? '—' }}</td>
                  <td class="acciones">
                    <button type="button" class="reforma-btn-danger" (click)="desactivar(a)">
                      <i class="pi pi-trash"></i> Dar de baja
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .alta {
        margin-bottom: 2rem;
      }
      form {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        align-items: flex-end;
      }
      .reforma-field {
        flex: 1 1 12rem;
        min-width: 9rem;
      }
      .reforma-field.full {
        flex: 1 1 100%;
      }
      textarea.reforma-input {
        resize: vertical;
        font: inherit;
      }
      form .reforma-btn {
        flex: 0 0 auto;
      }
      .lista-header {
        display: flex;
        align-items: center;
        gap: 1rem;
        margin-bottom: 1rem;
        flex-wrap: wrap;
      }
      .lista-header .reforma-section-title {
        margin: 0;
        flex: 1;
      }
      .buscar-wrap {
        position: relative;
        display: flex;
        align-items: center;
      }
      .buscar-wrap i {
        position: absolute;
        left: 0.7rem;
        color: var(--reforma-text-faint);
        pointer-events: none;
      }
      .buscar {
        min-width: 240px;
        padding-left: 2rem;
      }
      .lista .acciones {
        text-align: right;
        width: 1%;
        white-space: nowrap;
      }
      td.obs {
        max-width: 280px;
        white-space: pre-wrap;
        color: var(--reforma-text-dim);
      }
    `,
  ],
})
export class AnimalesComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly items = signal<Animal[]>([]);
  readonly orden = new OrdenTabla();
  readonly itemsOrdenados = computed(() =>
    this.orden.ordenar(this.items(), {
      codigo: (a) => a.codigoAnimal,
      descripcion: (a) => a.descripcionAnimal,
      categoria: (a) => a.categoriaAnimal,
      observaciones: (a) => a.observaciones,
    }),
  );
  readonly cargando = signal(true);
  readonly creando = signal(false);
  readonly error = signal<string | null>(null);
  readonly filtro = signal<string>('');

  readonly csvTrabajando = signal(false);
  readonly csvResultado = signal<CsvImportResult | null>(null);
  private readonly csvBar = viewChild(CatalogoCsvBarComponent);

  // Debounce 300ms para no martillar al backend con cada tecla del filtro.
  private readonly busquedaPendiente = new Subject<string>();

  form: AnimalRequest = vacio();

  readonly stats = computed(() => {
    const items = this.items();
    const cats = new Set(items.map((a) => a.categoriaAnimal?.trim()).filter(Boolean));
    return {
      total: items.length,
      categorias: cats.size,
      conObs: items.filter((a) => !!a.observaciones?.trim()).length,
      sinCategoria: items.filter((a) => !a.categoriaAnimal?.trim()).length,
    };
  });

  readonly porCategoria = computed(() => {
    const acum = new Map<string, number>();
    for (const a of this.items()) {
      const label = a.categoriaAnimal?.trim() || 'Sin categoría';
      acum.set(label, (acum.get(label) ?? 0) + 1);
    }
    return topConOtros(
      [...acum.entries()].map(([label, v]) => ({ label, valor: v })),
      8,
    );
  });

  readonly chartDonut = computed(() =>
    donutChart({
      labels: this.porCategoria().labels,
      series: this.porCategoria().valores,
      totalLabel: 'Animales',
      height: 280,
    }),
  );
  readonly chartBar = computed(() =>
    barChart({
      categories: this.porCategoria().labels,
      series: [{ name: 'Animales', data: this.porCategoria().valores }],
      distributed: true,
      height: 280,
    }),
  );

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.busquedaPendiente
      .pipe(debounceTime(300))
      .subscribe((termino) => this.recargar(termino));
    this.recargar();
  }

  onFiltroChange(valor: string): void {
    this.filtro.set(valor);
    this.busquedaPendiente.next(valor);
  }

  recargar(buscar?: string): void {
    this.cargando.set(true);
    this.api.getAnimales(this.idGranja, buscar?.trim() || undefined).subscribe({
      next: (list) => {
        this.items.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los animales');
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.form.codigoAnimal.trim() || !this.form.descripcionAnimal.trim()) {
      return;
    }
    this.creando.set(true);
    this.error.set(null);
    this.api.crearAnimal(this.idGranja, this.form).subscribe({
      next: (a) => {
        this.items.update((prev) =>
          [...prev, a].sort((x, y) => x.descripcionAnimal.localeCompare(y.descripcionAnimal)),
        );
        this.form = vacio();
        this.creando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.creando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo crear el animal');
      },
    });
  }

  desactivar(a: Animal): void {
    if (!confirm(`¿Dar de baja al animal "${a.descripcionAnimal}"?`)) {
      return;
    }
    this.api.desactivarAnimal(this.idGranja, a.id).subscribe({
      next: () => this.items.update((prev) => prev.filter((x) => x.id !== a.id)),
      error: () => this.error.set('No se pudo dar de baja'),
    });
  }

  exportarCsv(): void {
    this.csvTrabajando.set(true);
    this.api.exportarAnimalesCsv(this.idGranja).subscribe({
      next: (blob) => {
        descargarBlobComoArchivo(blob, `animales-${this.idGranja}.csv`);
        this.csvTrabajando.set(false);
      },
      error: () => {
        this.error.set('No se pudo exportar el CSV');
        this.csvTrabajando.set(false);
      },
    });
  }

  importarCsv(archivo: File): void {
    this.csvTrabajando.set(true);
    this.csvResultado.set(null);
    this.api.importarAnimalesCsv(this.idGranja, archivo).subscribe({
      next: (resultado) => {
        this.csvResultado.set(resultado);
        this.csvTrabajando.set(false);
        this.csvBar()?.reset();
        if (resultado.filasOk > 0) this.recargar();
      },
      error: (err: HttpErrorResponse) => {
        this.csvTrabajando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo importar el CSV');
      },
    });
  }
}

function vacio(): AnimalRequest {
  return {
    codigoAnimal: '',
    descripcionAnimal: '',
    categoriaAnimal: '',
    observaciones: '',
  };
}
