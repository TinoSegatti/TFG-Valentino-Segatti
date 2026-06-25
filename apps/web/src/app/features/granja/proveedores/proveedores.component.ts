import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { debounceTime, Subject } from 'rxjs';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Proveedor, ProveedorRequest } from '../../../data/models/proveedor.model';
import { CompraResumen } from '../../../data/models/compra.model';
import { CsvImportResult, descargarBlobComoArchivo } from '../../../data/models/csv.model';
import { CatalogoCsvBarComponent } from '../shared/catalogo-csv-bar.component';
import { DecimalPipe } from '@angular/common';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { barChart } from '../shared/apex-charts';
import { topConOtros } from '../shared/panel-utils';
import { OrdenTabla } from '../../../shared/orden-tabla';

/**
 * Listado, alta rápida y baja lógica de proveedores (RF-PROV-001 / RF-PROV-002).
 * Comparte estética con MateriasPrimasComponent para mantener consistencia visual.
 */
@Component({
  selector: 'app-proveedores',
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
    <h2 class="reforma-page-title">Proveedores</h2>

    <section class="rf-grid-kpis">
      <reforma-kpi-card style="--rf-i: 0" icon="pi-truck" label="Proveedores activos" [value]="stats().total | number: '1.0-0'" />
      <reforma-kpi-card style="--rf-i: 1" icon="pi-envelope" label="Con email" [value]="stats().conEmail | number: '1.0-0'" accent="#06b6d4" />
      <reforma-kpi-card style="--rf-i: 2" icon="pi-id-card" label="Con CUIT" [value]="stats().conCuit | number: '1.0-0'" accent="#f472b6" />
      <reforma-kpi-card style="--rf-i: 3" icon="pi-map-marker" label="Localidades" [value]="stats().localidades | number: '1.0-0'" accent="#34d399" />
    </section>

    <section class="rf-grid-2">
      <reforma-chart-card
        title="Gasto por proveedor"
        icon="pi-chart-bar"
        [loading]="cargandoCompras()"
        [empty]="!cargandoCompras() && gastoPorProveedor().valores.length === 0"
        emptyText="Registrá compras para ver dónde se concentra el gasto"
      >
        <reforma-apex [options]="chartGasto()" />
      </reforma-chart-card>
      <reforma-chart-card
        title="Compras por proveedor"
        icon="pi-chart-bar"
        [loading]="cargandoCompras()"
        [empty]="!cargandoCompras() && comprasPorProveedor().valores.length === 0"
        emptyText="Sin compras registradas"
      >
        <reforma-apex [options]="chartCompras()" />
      </reforma-chart-card>
    </section>

    <app-catalogo-csv-bar
      #csvBar
      [trabajando]="csvTrabajando()"
      [resultado]="csvResultado()"
      columnasAyuda="codigo, nombre, telefono, email, cuit, direccion, localidad, notas"
      (exportar)="exportarCsv()"
      (importar)="importarCsv($event)"
    />

    <section class="reforma-section alta">
      <h3 class="reforma-section-title">Alta rápida</h3>
      <form (ngSubmit)="crear()" #f="ngForm">
        <label class="reforma-field">
          <span>Código</span>
          <input class="reforma-input" name="codigo" [(ngModel)]="form.codigoProveedor" maxlength="50" required />
        </label>
        <label class="reforma-field">
          <span>Nombre</span>
          <input class="reforma-input" name="nombre" [(ngModel)]="form.nombreProveedor" maxlength="200" required />
        </label>
        <label class="reforma-field">
          <span>Teléfono</span>
          <input class="reforma-input" name="telefono" [(ngModel)]="form.telefono" maxlength="50" />
        </label>
        <label class="reforma-field">
          <span>Email</span>
          <input class="reforma-input" type="email" name="email" [(ngModel)]="form.email" maxlength="200" />
        </label>
        <label class="reforma-field">
          <span>CUIT</span>
          <input class="reforma-input" name="cuit" [(ngModel)]="form.cuit" maxlength="20" />
        </label>
        <label class="reforma-field">
          <span>Localidad</span>
          <input class="reforma-input" name="localidad" [(ngModel)]="form.localidad" maxlength="100" />
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
            placeholder="Buscar por nombre…"
            [ngModel]="filtro()"
            (ngModelChange)="onFiltroChange($event)"
          />
        </div>
      </header>

      @if (cargando()) {
        <p class="reforma-empty">Cargando…</p>
      } @else if (items().length === 0) {
        <p class="reforma-empty">Todavía no cargaste ningún proveedor (o ninguno coincide con el filtro).</p>
      } @else {
        <div class="reforma-table-wrap">
          <table class="reforma-table">
            <thead>
              <tr>
                <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('nombre')" [class.is-desc]="orden.esDesc('nombre')" (click)="orden.alternar('nombre')">Nombre</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('telefono')" [class.is-desc]="orden.esDesc('telefono')" (click)="orden.alternar('telefono')">Teléfono</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('localidad')" [class.is-desc]="orden.esDesc('localidad')" (click)="orden.alternar('localidad')">Localidad</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (p of itemsOrdenados(); track p.id) {
                <tr>
                  <td>{{ p.codigoProveedor }}</td>
                  <td>{{ p.nombreProveedor }}</td>
                  <td>{{ p.telefono ?? '—' }}</td>
                  <td>{{ p.localidad ?? '—' }}</td>
                  <td class="acciones">
                    <button type="button" class="reforma-btn-danger" (click)="desactivar(p)">
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
    `,
  ],
})
export class ProveedoresComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly items = signal<Proveedor[]>([]);
  readonly orden = new OrdenTabla();
  readonly itemsOrdenados = computed(() =>
    this.orden.ordenar(this.items(), {
      codigo: (p) => p.codigoProveedor,
      nombre: (p) => p.nombreProveedor,
      telefono: (p) => p.telefono,
      localidad: (p) => p.localidad,
    }),
  );
  readonly cargando = signal(true);
  readonly creando = signal(false);
  readonly error = signal<string | null>(null);
  readonly filtro = signal<string>('');

  readonly csvTrabajando = signal(false);
  readonly csvResultado = signal<CsvImportResult | null>(null);
  private readonly csvBar = viewChild(CatalogoCsvBarComponent);

  /**
   * Debounce de 300ms sobre el filtro de búsqueda para no martillar al backend con cada tecla.
   * Cada cambio en el input alimenta este Subject; el operador `debounceTime` espera la pausa
   * antes de disparar `recargar`.
   */
  private readonly busquedaPendiente = new Subject<string>();

  form: ProveedorRequest = vacio();

  // Compras del periodo (para los gráficos de gasto / cantidad por proveedor).
  readonly compras = signal<CompraResumen[]>([]);
  readonly cargandoCompras = signal(true);

  readonly stats = computed(() => {
    const items = this.items();
    return {
      total: items.length,
      conEmail: items.filter((p) => !!p.email?.trim()).length,
      conCuit: items.filter((p) => !!p.cuit?.trim()).length,
      localidades: new Set(items.map((p) => p.localidad?.trim()).filter(Boolean)).size,
    };
  });

  readonly gastoPorProveedor = computed(() => this.agruparCompras((c) => c.totalFactura, 8));
  readonly comprasPorProveedor = computed(() => this.agruparCompras(() => 1, 8));

  readonly chartGasto = computed(() =>
    barChart({
      categories: this.gastoPorProveedor().labels,
      series: [{ name: 'Gasto', data: this.gastoPorProveedor().valores }],
      horizontal: true,
      distributed: true,
      money: true,
      height: 300,
    }),
  );
  readonly chartCompras = computed(() =>
    barChart({
      categories: this.comprasPorProveedor().labels,
      series: [{ name: 'Compras', data: this.comprasPorProveedor().valores }],
      distributed: true,
      height: 300,
    }),
  );

  private agruparCompras(valor: (c: CompraResumen) => number, n: number) {
    const acum = new Map<string, number>();
    for (const c of this.compras()) {
      const label = c.nombreProveedor || c.codigoProveedor || '—';
      acum.set(label, (acum.get(label) ?? 0) + valor(c));
    }
    return topConOtros(
      [...acum.entries()].map(([label, v]) => ({ label, valor: v })),
      n,
    );
  }

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.busquedaPendiente
      .pipe(debounceTime(300))
      .subscribe((termino) => this.recargar(termino));
    this.recargar();
    this.api.getCompras(this.idGranja).subscribe({
      next: (list) => {
        this.compras.set(list);
        this.cargandoCompras.set(false);
      },
      error: () => this.cargandoCompras.set(false),
    });
  }

  onFiltroChange(valor: string): void {
    this.filtro.set(valor);
    this.busquedaPendiente.next(valor);
  }

  recargar(buscar?: string): void {
    this.cargando.set(true);
    this.api.getProveedores(this.idGranja, buscar?.trim() || undefined).subscribe({
      next: (list) => {
        this.items.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los proveedores');
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.form.codigoProveedor.trim() || !this.form.nombreProveedor.trim()) {
      return;
    }
    this.creando.set(true);
    this.error.set(null);
    this.api.crearProveedor(this.idGranja, this.form).subscribe({
      next: (p) => {
        this.items.update((prev) =>
          [...prev, p].sort((a, b) => a.nombreProveedor.localeCompare(b.nombreProveedor)),
        );
        this.form = vacio();
        this.creando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.creando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo crear el proveedor');
      },
    });
  }

  desactivar(p: Proveedor): void {
    if (!confirm(`¿Dar de baja al proveedor "${p.nombreProveedor}"?`)) {
      return;
    }
    this.api.desactivarProveedor(this.idGranja, p.id).subscribe({
      next: () => this.items.update((prev) => prev.filter((x) => x.id !== p.id)),
      error: () => this.error.set('No se pudo dar de baja'),
    });
  }

  exportarCsv(): void {
    this.csvTrabajando.set(true);
    this.api.exportarProveedoresCsv(this.idGranja).subscribe({
      next: (blob) => {
        descargarBlobComoArchivo(blob, `proveedores-${this.idGranja}.csv`);
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
    this.api.importarProveedoresCsv(this.idGranja, archivo).subscribe({
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

function vacio(): ProveedorRequest {
  return {
    codigoProveedor: '',
    nombreProveedor: '',
    telefono: '',
    email: '',
    cuit: '',
    direccion: '',
    localidad: '',
    notas: '',
  };
}
