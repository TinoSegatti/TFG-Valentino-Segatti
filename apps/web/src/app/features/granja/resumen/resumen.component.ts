import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { MateriaPrima } from '../../../data/models/materia-prima.model';
import { Proveedor } from '../../../data/models/proveedor.model';
import { Animal } from '../../../data/models/animal.model';
import { CompraResumen } from '../../../data/models/compra.model';
import { FormulaResumen } from '../../../data/models/formula.model';
import { FabricacionResumen } from '../../../data/models/fabricacion.model';
import { InventarioItem } from '../../../data/models/inventario.model';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { areaChart, barChart, donutChart } from '../shared/apex-charts';
import { sumarPorMes, topConOtros } from '../shared/panel-utils';

interface Actividad {
  tipo: 'compra' | 'fabricacion';
  titulo: string;
  sub: string;
  fecha: string;
  monto: number;
  link: (string | number)[];
}

/**
 * Panel principal de la granja: un "collage" ejecutivo que resume todos los
 * módulos (inventario, compras, fórmulas, fabricaciones) en KPIs + gráficos,
 * siguiendo la distribución del handoff (fila de KPIs, fila área+donut, fila
 * barras+actividad). Carga todos los datos en paralelo con forkJoin.
 */
@Component({
  selector: 'app-resumen',
  standalone: true,
  imports: [DecimalPipe, RouterLink, KpiCardComponent, ChartCardComponent, ApexChartComponent],
  template: `
    <header class="head">
      <h2 class="reforma-page-title">Panel principal</h2>
      <p class="sub text-dim">Resumen ejecutivo de la operación de la granja.</p>
    </header>

    <!-- Fila de KPIs -->
    <section class="rf-grid-kpis">
      <reforma-kpi-card
        style="--rf-i: 0"
        icon="pi-warehouse"
        label="Valor de inventario"
        [value]="'$ ' + (k().valorInventario | number: '1.0-0')"
        [sub]="k().materiasStock + ' materias con stock'"
      />
      <reforma-kpi-card
        style="--rf-i: 1"
        icon="pi-shopping-cart"
        label="Gasto en compras"
        [value]="'$ ' + (k().gastoCompras | number: '1.0-0')"
        [sub]="k().totalCompras + ' compras registradas'"
        [spark]="sparkCompras()"
        accent="#06b6d4"
      />
      <reforma-kpi-card
        style="--rf-i: 2"
        icon="pi-sliders-h"
        label="Fórmulas"
        [value]="k().totalFormulas | number: '1.0-0'"
        [sub]="k().formulasCompletas + ' completas'"
      />
      <reforma-kpi-card
        style="--rf-i: 3"
        icon="pi-cog"
        label="Fabricaciones"
        [value]="k().totalFabricaciones | number: '1.0-0'"
        [sub]="(k().kilosProducidos | number: '1.0-0') + ' kg producidos'"
        [spark]="sparkFabricaciones()"
        accent="#f472b6"
      />
    </section>

    <!-- Fila 2: área de gasto + donut de valor de inventario -->
    <section class="rf-grid-2">
      <reforma-chart-card
        title="Gasto en compras por mes"
        icon="pi-chart-line"
        [loading]="cargando()"
        [empty]="!cargando() && gastoMensual().valores.length === 0"
        emptyText="Registrá compras para ver la evolución del gasto"
      >
        <reforma-apex [options]="chartGasto()" />
      </reforma-chart-card>

      <reforma-chart-card
        title="Valor de inventario por materia"
        icon="pi-chart-pie"
        [loading]="cargando()"
        [empty]="!cargando() && valorInventarioTop().valores.length === 0"
        emptyText="Inicializá el inventario para ver su distribución"
      >
        <reforma-apex [options]="chartInventario()" />
      </reforma-chart-card>
    </section>

    <!-- Fila 3: barras de producción + actividad reciente -->
    <section class="rf-grid-1-2">
      <reforma-chart-card
        title="Producción por mes"
        icon="pi-chart-bar"
        [loading]="cargando()"
        [empty]="!cargando() && produccionMensual().valores.length === 0"
        emptyText="Registrá fabricaciones para ver la producción"
      >
        <reforma-apex [options]="chartProduccion()" />
      </reforma-chart-card>

      <reforma-chart-card title="Actividad reciente" icon="pi-history" [loading]="cargando()" [empty]="!cargando() && actividad().length === 0" emptyText="Sin movimientos recientes">
        <ul class="actividad">
          @for (a of actividad(); track $index) {
            <li class="rf-rise" [style.--rf-i]="$index">
              <a [routerLink]="a.link">
                <span class="tag" [class.compra]="a.tipo === 'compra'" [class.fab]="a.tipo === 'fabricacion'">
                  {{ a.tipo === 'compra' ? 'Compra' : 'Fabricación' }}
                </span>
                <span class="cuerpo">
                  <strong>{{ a.titulo }}</strong>
                  <small>{{ a.sub }}</small>
                </span>
                <span class="monto rf-num">$ {{ a.monto | number: '1.0-0' }}</span>
              </a>
            </li>
          }
        </ul>
      </reforma-chart-card>
    </section>

    @if (error()) {
      <p class="reforma-alert reforma-alert-error"><i class="pi pi-exclamation-circle"></i> {{ error() }}</p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .head {
        margin-bottom: 1.25rem;
      }
      .reforma-page-title {
        margin: 0;
      }
      .sub {
        margin: 0.25rem 0 0;
      }
      .actividad {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
      }
      .actividad li + li {
        border-top: 1px solid var(--glass-border);
      }
      .actividad a {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.7rem 0.25rem;
        text-decoration: none;
        color: var(--reforma-text);
        transition: background 0.12s ease;
        border-radius: 8px;
      }
      .actividad a:hover {
        background: var(--glass-bg-hover);
      }
      .tag {
        flex: 0 0 auto;
        font-size: 0.68rem;
        font-weight: 600;
        padding: 0.18rem 0.5rem;
        border-radius: 999px;
        white-space: nowrap;
      }
      .tag.compra {
        color: #a5f3fc;
        background: rgba(6, 182, 212, 0.16);
      }
      .tag.fab {
        color: #f5d0fe;
        background: rgba(244, 114, 182, 0.16);
      }
      .cuerpo {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .cuerpo strong {
        font-size: 0.9rem;
        font-weight: 600;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .cuerpo small {
        font-size: 0.76rem;
        color: var(--reforma-text-dim);
      }
      .monto {
        flex: 0 0 auto;
        font-weight: 600;
        font-size: 0.88rem;
        color: var(--reforma-text);
      }
    `,
  ],
})
export class ResumenComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  private readonly materias = signal<MateriaPrima[]>([]);
  private readonly proveedores = signal<Proveedor[]>([]);
  private readonly animales = signal<Animal[]>([]);
  private readonly compras = signal<CompraResumen[]>([]);
  private readonly formulas = signal<FormulaResumen[]>([]);
  private readonly fabricaciones = signal<FabricacionResumen[]>([]);
  private readonly inventario = signal<InventarioItem[]>([]);

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  // === KPIs ===
  readonly k = computed(() => {
    const inv = this.inventario().filter((i) => i.cantidadReal > 0);
    return {
      valorInventario: inv.reduce((s, i) => s + i.valorStock, 0),
      materiasStock: inv.length,
      gastoCompras: this.compras().reduce((s, c) => s + c.totalFactura, 0),
      totalCompras: this.compras().length,
      totalFormulas: this.formulas().length,
      formulasCompletas: this.formulas().filter((f) => f.completa).length,
      totalFabricaciones: this.fabricaciones().length,
      kilosProducidos: this.fabricaciones().reduce((s, f) => s + f.veces * 1000, 0),
    };
  });

  // === Series derivadas ===
  readonly gastoMensual = computed(() =>
    sumarPorMes(this.compras(), (c) => c.fechaCompra, (c) => c.totalFactura),
  );
  readonly produccionMensual = computed(() =>
    sumarPorMes(this.fabricaciones(), (f) => f.fechaFabricacion, (f) => f.veces * 1000),
  );
  readonly valorInventarioTop = computed(() =>
    topConOtros(
      this.inventario()
        .filter((i) => i.valorStock > 0)
        .map((i) => ({ label: i.codigoMateriaPrima, valor: i.valorStock })),
      8,
    ),
  );

  readonly sparkCompras = computed(() => this.gastoMensual().valores);
  readonly sparkFabricaciones = computed(() => this.produccionMensual().valores);

  // === Configs de gráficos ===
  readonly chartGasto = computed(() =>
    areaChart({
      categories: this.gastoMensual().labels,
      series: [{ name: 'Gasto', data: this.gastoMensual().valores }],
      money: true,
      height: 300,
    }),
  );
  readonly chartInventario = computed(() =>
    donutChart({
      labels: this.valorInventarioTop().labels,
      series: this.valorInventarioTop().valores,
      totalLabel: 'Valor total',
      totalFormatter: (t) => '$ ' + Math.round(t).toLocaleString('en-US'),
      money: true,
      height: 300,
    }),
  );
  readonly chartProduccion = computed(() =>
    barChart({
      categories: this.produccionMensual().labels,
      series: [{ name: 'Kilos', data: this.produccionMensual().valores }],
      height: 300,
    }),
  );

  // === Actividad reciente (compras + fabricaciones combinadas) ===
  readonly actividad = computed<Actividad[]>(() => {
    const deCompras: Actividad[] = this.compras().map((c) => ({
      tipo: 'compra',
      titulo: `Factura ${c.numeroFactura}`,
      sub: c.nombreProveedor,
      fecha: c.fechaCompra,
      monto: c.totalFactura,
      link: ['../compras', c.id],
    }));
    const deFab: Actividad[] = this.fabricaciones().map((f) => ({
      tipo: 'fabricacion',
      titulo: f.codigoFabricacion,
      sub: f.descripcionFabricacion || f.codigoFormula || 'Fabricación',
      fecha: f.fechaFabricacion,
      monto: f.costoTotalFabricacion,
      link: ['../fabricaciones', f.id],
    }));
    return [...deCompras, ...deFab]
      .sort((a, b) => (a.fecha < b.fecha ? 1 : a.fecha > b.fecha ? -1 : 0))
      .slice(0, 6);
  });

  ngOnInit(): void {
    const g = this.idGranja;
    if (!g) {
      this.cargando.set(false);
      return;
    }
    forkJoin({
      materias: this.api.getMateriasPrimas(g).pipe(catchError(() => of([] as MateriaPrima[]))),
      proveedores: this.api.getProveedores(g).pipe(catchError(() => of([] as Proveedor[]))),
      animales: this.api.getAnimales(g).pipe(catchError(() => of([] as Animal[]))),
      compras: this.api.getCompras(g).pipe(catchError(() => of([] as CompraResumen[]))),
      formulas: this.api.getFormulas(g).pipe(catchError(() => of([] as FormulaResumen[]))),
      fabricaciones: this.api.getFabricaciones(g).pipe(catchError(() => of([] as FabricacionResumen[]))),
      inventario: this.api
        .getInventario(g)
        .pipe(catchError(() => of({ inicializado: false, items: [] as InventarioItem[] }))),
    }).subscribe({
      next: (r) => {
        this.materias.set(r.materias);
        this.proveedores.set(r.proveedores);
        this.animales.set(r.animales);
        this.compras.set(r.compras);
        this.formulas.set(r.formulas);
        this.fabricaciones.set(r.fabricaciones);
        this.inventario.set(r.inventario.items);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los datos del panel.');
        this.cargando.set(false);
      },
    });
  }
}
