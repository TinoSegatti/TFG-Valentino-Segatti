import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { MateriaPrima, MateriaPrimaRequest } from '../../../data/models/materia-prima.model';
import { CsvImportResult, descargarBlobComoArchivo } from '../../../data/models/csv.model';
import { CatalogoCsvBarComponent } from '../shared/catalogo-csv-bar.component';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { barChart } from '../shared/apex-charts';
import { topConOtros } from '../shared/panel-utils';
import { NumeroFormatoDirective } from '../../../shared/numero-formato.directive';
import { OrdenTabla } from '../../../shared/orden-tabla';

@Component({
  selector: 'app-materias-primas',
  standalone: true,
  imports: [
    FormsModule,
    DecimalPipe,
    CatalogoCsvBarComponent,
    KpiCardComponent,
    ChartCardComponent,
    ApexChartComponent,
    NumeroFormatoDirective,
  ],
  template: `
    <h2 class="reforma-page-title">Materias primas</h2>

    <section class="rf-grid-kpis">
      <reforma-kpi-card style="--rf-i: 0" icon="pi-box" label="Materias activas" [value]="stats().total | number: '1.0-0'" />
      <reforma-kpi-card style="--rf-i: 1" icon="pi-dollar" label="Precio promedio /kg" [value]="'$ ' + (stats().promedio | number: '1.2-2')" accent="#06b6d4" />
      <reforma-kpi-card style="--rf-i: 2" icon="pi-arrow-up" label="Más cara" [value]="'$ ' + (stats().maxPrecio | number: '1.2-2')" [sub]="stats().maxNombre" accent="#f472b6" />
      <reforma-kpi-card style="--rf-i: 3" icon="pi-arrow-down" label="Más económica" [value]="'$ ' + (stats().minPrecio | number: '1.2-2')" [sub]="stats().minNombre" accent="#34d399" />
    </section>

    <section class="rf-grid-halves">
      <reforma-chart-card
        title="Precio por kilo (top 12)"
        icon="pi-chart-bar"
        [loading]="cargando()"
        [empty]="precioChart().valores.length === 0"
        emptyText="Cargá materias primas con precio para ver el gráfico"
      >
        <reforma-apex [options]="chartPrecio()" />
      </reforma-chart-card>
      <reforma-chart-card
        title="Consumo por materia (toneladas)"
        icon="pi-chart-bar"
        [empty]="true"
        emptyText="Disponible próximamente — requiere agregación de fabricaciones en el backend"
      />
    </section>

    <app-catalogo-csv-bar
      #csvBar
      [trabajando]="csvTrabajando()"
      [resultado]="csvResultado()"
      columnasAyuda="codigo, nombre, precio_por_kilo"
      (exportar)="exportarCsv()"
      (importar)="importarCsv($event)"
    />

    <section class="reforma-section alta">
      <h3 class="reforma-section-title">Alta rápida</h3>
      <form (ngSubmit)="crear()" #f="ngForm">
        <label class="reforma-field">
          <span>Código</span>
          <input
            class="reforma-input"
            name="codigo"
            [(ngModel)]="form.codigoMateriaPrima"
            maxlength="50"
            required
          />
        </label>
        <label class="reforma-field">
          <span>Nombre</span>
          <input
            class="reforma-input"
            name="nombre"
            [(ngModel)]="form.nombreMateriaPrima"
            maxlength="200"
            required
          />
        </label>
        <label class="reforma-field precio">
          <span>Precio por kg</span>
          <input
            class="reforma-input"
            name="precio"
            [appNumero]="2"
            [(ngModel)]="form.precioPorKilo"
            required
          />
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
      <h3 class="reforma-section-title">Activas ({{ items().length }})</h3>
      @if (cargando()) {
        <p class="reforma-empty">Cargando…</p>
      } @else if (items().length === 0) {
        <p class="reforma-empty">Todavía no cargaste ninguna materia prima.</p>
      } @else {
        <div class="reforma-table-wrap">
          <table class="reforma-table">
            <thead>
              <tr>
                <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('nombre')" [class.is-desc]="orden.esDesc('nombre')" (click)="orden.alternar('nombre')">Nombre</th>
                <th class="num sortable" [class.is-asc]="orden.esAsc('precio')" [class.is-desc]="orden.esDesc('precio')" (click)="orden.alternar('precio')">Precio/kg</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (mp of itemsOrdenados(); track mp.id) {
                <tr>
                  <td>{{ mp.codigoMateriaPrima }}</td>
                  <td>{{ mp.nombreMateriaPrima }}</td>
                  <td class="num">{{ mp.precioPorKilo | number: '1.2-2' }}</td>
                  <td class="acciones">
                    <button type="button" class="reforma-btn-danger" (click)="desactivar(mp)">
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
      .reforma-field.precio {
        flex: 0 1 9rem;
      }
      form .reforma-btn {
        flex: 0 0 auto;
      }
      .lista .acciones {
        text-align: right;
        width: 1%;
        white-space: nowrap;
      }
    `,
  ],
})
export class MateriasPrimasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly items = signal<MateriaPrima[]>([]);
  readonly orden = new OrdenTabla();
  readonly itemsOrdenados = computed(() =>
    this.orden.ordenar(this.items(), {
      codigo: (m) => m.codigoMateriaPrima,
      nombre: (m) => m.nombreMateriaPrima,
      precio: (m) => m.precioPorKilo,
    }),
  );
  readonly cargando = signal(true);
  readonly creando = signal(false);
  readonly error = signal<string | null>(null);

  readonly csvTrabajando = signal(false);
  readonly csvResultado = signal<CsvImportResult | null>(null);
  private readonly csvBar = viewChild(CatalogoCsvBarComponent);

  readonly stats = computed(() => {
    const items = this.items();
    if (items.length === 0) {
      return { total: 0, promedio: 0, maxPrecio: 0, maxNombre: '', minPrecio: 0, minNombre: '' };
    }
    const conPrecio = items.filter((m) => m.precioPorKilo > 0);
    const promedio =
      conPrecio.length > 0 ? conPrecio.reduce((s, m) => s + m.precioPorKilo, 0) / conPrecio.length : 0;
    const max = conPrecio.reduce((a, b) => (b.precioPorKilo > a.precioPorKilo ? b : a), conPrecio[0]);
    const min = conPrecio.reduce((a, b) => (b.precioPorKilo < a.precioPorKilo ? b : a), conPrecio[0]);
    return {
      total: items.length,
      promedio,
      maxPrecio: max?.precioPorKilo ?? 0,
      maxNombre: max?.nombreMateriaPrima ?? '',
      minPrecio: min?.precioPorKilo ?? 0,
      minNombre: min?.nombreMateriaPrima ?? '',
    };
  });

  readonly precioChart = computed(() =>
    topConOtros(
      this.items()
        .filter((m) => m.precioPorKilo > 0)
        .map((m) => ({ label: m.codigoMateriaPrima, valor: m.precioPorKilo })),
      12,
    ),
  );
  readonly chartPrecio = computed(() =>
    barChart({
      categories: this.precioChart().labels,
      series: [{ name: 'Precio/kg', data: this.precioChart().valores }],
      distributed: true,
      money: true,
      height: 280,
    }),
  );

  form: MateriaPrimaRequest = {
    codigoMateriaPrima: '',
    nombreMateriaPrima: '',
    precioPorKilo: 0,
  };

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargar();
  }

  recargar(): void {
    this.cargando.set(true);
    this.api.getMateriasPrimas(this.idGranja).subscribe({
      next: (list) => {
        this.items.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar las materias primas');
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.form.codigoMateriaPrima.trim() || !this.form.nombreMateriaPrima.trim()) {
      return;
    }
    this.creando.set(true);
    this.error.set(null);
    this.api.crearMateriaPrima(this.idGranja, this.form).subscribe({
      next: (mp) => {
        this.items.update((prev) => [...prev, mp].sort((a, b) =>
          a.nombreMateriaPrima.localeCompare(b.nombreMateriaPrima),
        ));
        this.form = { codigoMateriaPrima: '', nombreMateriaPrima: '', precioPorKilo: 0 };
        this.creando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.creando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo crear la materia prima');
      },
    });
  }

  desactivar(mp: MateriaPrima): void {
    if (!confirm(`¿Dar de baja la materia prima "${mp.nombreMateriaPrima}"?`)) {
      return;
    }
    this.api.desactivarMateriaPrima(this.idGranja, mp.id).subscribe({
      next: () => this.items.update((prev) => prev.filter((m) => m.id !== mp.id)),
      error: () => this.error.set('No se pudo dar de baja'),
    });
  }

  exportarCsv(): void {
    this.csvTrabajando.set(true);
    this.api.exportarMateriasPrimasCsv(this.idGranja).subscribe({
      next: (blob) => {
        descargarBlobComoArchivo(blob, `materias-primas-${this.idGranja}.csv`);
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
    this.api.importarMateriasPrimasCsv(this.idGranja, archivo).subscribe({
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
