import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { InformeEstado, SeccionInformeCsv } from '../../../data/models/informe.model';
import { descargarBlobComoArchivo } from '../../../data/models/csv.model';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { areaChart, barChart } from '../shared/apex-charts';
import { construirInformeHtml } from './informe-html.builder';

type PresetPeriodo = 30 | 90 | 365 | 'custom';

/**
 * Informe de estado (RF-REP-001/002/003): analítica por secciones calculada por el
 * backend para un período. La vista renderiza las secciones con los gráficos del
 * Sprint 3 y permite descargar el informe como HTML autocontenido
 * (informe-html.builder) y cada sección como CSV.
 */
@Component({
  selector: 'app-informe',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, KpiCardComponent, ChartCardComponent, ApexChartComponent],
  template: `
    <header class="toolbar">
      <div>
        <h2 class="reforma-page-title">Informe de estado</h2>
        <p class="sub text-dim">
          Analítica de compras, inventario, consumos e IA del período para la toma de decisiones.
        </p>
      </div>
    </header>

    <section class="periodo glass-surface">
      <div class="presets">
        <span class="mini text-dim">Período:</span>
        @for (p of presets; track p.valor) {
          <button
            type="button"
            class="reforma-btn-ghost reforma-btn-sm"
            [class.activo]="preset() === p.valor"
            (click)="elegirPreset(p.valor)"
          >
            {{ p.etiqueta }}
          </button>
        }
      </div>
      @if (preset() === 'custom') {
        <div class="fechas">
          <label>
            <span class="mini text-dim">Desde</span>
            <input type="date" class="reforma-input" [ngModel]="desde()" (ngModelChange)="desde.set($event)" />
          </label>
          <label>
            <span class="mini text-dim">Hasta</span>
            <input type="date" class="reforma-input" [ngModel]="hasta()" (ngModelChange)="hasta.set($event)" />
          </label>
          <button type="button" class="reforma-btn reforma-btn-sm" [disabled]="cargando()" (click)="generar()">
            <i class="pi pi-refresh"></i> Generar
          </button>
        </div>
      }
      @if (informe(); as inf) {
        <div class="descarga">
          <span class="mini text-dim rango">{{ inf.desde | date: 'dd/MM/yyyy' }} — {{ inf.hasta | date: 'dd/MM/yyyy' }}</span>
          <button type="button" class="reforma-btn reforma-btn-sm" (click)="descargarHtml()">
            <i class="pi pi-download"></i> Descargar informe HTML
          </button>
        </div>
      }
    </section>

    @if (error()) {
      <p class="reforma-alert reforma-alert-error">
        <i class="pi pi-exclamation-circle"></i> {{ error() }}
      </p>
    }

    @if (cargando()) {
      <p class="reforma-empty">Generando informe…</p>
    } @else {
      @if (informe(); as inf) {
      <!-- 1. Resumen general -->
      <section class="seccion">
        <div class="seccion-head">
          <h3>1 · Resumen general</h3>
        </div>
        <div class="rf-grid-kpis">
          <reforma-kpi-card
            style="--rf-i: 0"
            icon="pi-shopping-cart"
            label="Compras del período"
            [value]="inf.resumen.compras + ''"
            [sub]="'$ ' + (inf.resumen.gastoTotal | number: '1.0-0') + ' de gasto'"
          />
          <reforma-kpi-card
            style="--rf-i: 1"
            icon="pi-warehouse"
            label="Valor de stock actual"
            [value]="'$ ' + (inf.resumen.valorStock | number: '1.0-0')"
            [sub]="(inf.resumen.mermaTotal | number: '1.0-0') + ' kg de merma'"
            accent="#06b6d4"
          />
          <reforma-kpi-card
            style="--rf-i: 2"
            icon="pi-cog"
            label="Fabricaciones"
            [value]="inf.resumen.fabricaciones + ''"
            [sub]="(inf.resumen.kgProducidos | number: '1.0-0') + ' kg producidos'"
            accent="#f472b6"
          />
        </div>
      </section>

      <!-- 2. Proveedores -->
      <section class="seccion">
        <div class="seccion-head">
          <h3>2 · Proveedores</h3>
          <button type="button" class="reforma-btn-ghost reforma-btn-sm" (click)="descargarCsv('proveedores')">
            <i class="pi pi-download"></i> CSV
          </button>
        </div>
        @if (inf.proveedores.proveedores.length === 0) {
          <p class="reforma-empty">Sin compras registradas en el período.</p>
        } @else {
          <div class="rf-grid-2">
            <reforma-chart-card title="Gasto por proveedor" icon="pi-chart-bar">
              <reforma-apex [options]="chartProveedores()" />
            </reforma-chart-card>
            <div class="reforma-table-wrap">
              <table class="reforma-table">
                <thead>
                  <tr><th>Proveedor</th><th class="num">Compras</th><th class="num">Monto</th><th class="num">Kg</th><th>Materia principal</th></tr>
                </thead>
                <tbody>
                  @for (p of inf.proveedores.proveedores; track p.codigo) {
                    <tr>
                      <td>{{ p.nombre }}</td>
                      <td class="num">{{ p.compras }}</td>
                      <td class="num">$ {{ p.monto | number: '1.0-0' }}</td>
                      <td class="num">{{ p.kg | number: '1.0-0' }}</td>
                      <td>{{ p.materiaPrincipal || '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      </section>

      <!-- 3. Inventario -->
      <section class="seccion">
        <div class="seccion-head">
          <h3>3 · Inventario (estado actual)</h3>
          <button type="button" class="reforma-btn-ghost reforma-btn-sm" (click)="descargarCsv('inventario')">
            <i class="pi pi-download"></i> CSV
          </button>
        </div>
        @if (inventarioConStock().length === 0) {
          <p class="reforma-empty">Sin materias primas con stock.</p>
        } @else {
          <div class="rf-grid-2">
            <reforma-chart-card title="Valor de stock por materia prima" icon="pi-chart-bar">
              <reforma-apex [options]="chartInventario()" />
            </reforma-chart-card>
            <div class="reforma-table-wrap">
              <table class="reforma-table">
                <thead>
                  <tr><th>Materia prima</th><th class="num">Cant. real (kg)</th><th class="num">Merma (kg)</th><th class="num">Valor</th></tr>
                </thead>
                <tbody>
                  @for (i of inventarioConStock(); track i.codigoMateriaPrima) {
                    <tr>
                      <td>{{ i.nombreMateriaPrima }}</td>
                      <td class="num">{{ i.cantidadReal | number: '1.0-0' }}</td>
                      <td class="num">{{ i.merma | number: '1.0-0' }}</td>
                      <td class="num">$ {{ i.valorStock | number: '1.0-0' }}</td>
                    </tr>
                  }
                </tbody>
                <tfoot>
                  <tr>
                    <th colspan="3">Total</th>
                    <th class="num">$ {{ inf.inventario.valorTotal | number: '1.0-0' }}</th>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
        }
      </section>

      <!-- 4. Compras -->
      <section class="seccion">
        <div class="seccion-head">
          <h3>4 · Compras</h3>
          <button type="button" class="reforma-btn-ghost reforma-btn-sm" (click)="descargarCsv('compras')">
            <i class="pi pi-download"></i> CSV
          </button>
        </div>
        @if (inf.compras.materias.length === 0) {
          <p class="reforma-empty">Sin compras registradas en el período.</p>
        } @else {
          <reforma-chart-card title="Evolución mensual del gasto" icon="pi-chart-line">
            <reforma-apex [options]="chartEvolucion()" />
          </reforma-chart-card>
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr><th>Materia prima</th><th class="num">Kg</th><th class="num">Monto</th><th class="num">Precio mín.</th><th class="num">Precio máx.</th><th class="num">Precio prom.</th></tr>
              </thead>
              <tbody>
                @for (m of inf.compras.materias; track m.codigo) {
                  <tr>
                    <td>{{ m.nombre }}</td>
                    <td class="num">{{ m.kg | number: '1.0-0' }}</td>
                    <td class="num">$ {{ m.monto | number: '1.0-0' }}</td>
                    <td class="num">$ {{ m.precioMin | number: '1.2-2' }}</td>
                    <td class="num">$ {{ m.precioMax | number: '1.2-2' }}</td>
                    <td class="num">$ {{ m.precioPromedio | number: '1.2-2' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <!-- 5. Consumos -->
      <section class="seccion">
        <div class="seccion-head">
          <h3>5 · Consumos y producción</h3>
          <button type="button" class="reforma-btn-ghost reforma-btn-sm" (click)="descargarCsv('consumos')">
            <i class="pi pi-download"></i> CSV
          </button>
        </div>
        @if (inf.consumos.formulas.length === 0 && inf.consumos.materias.length === 0) {
          <p class="reforma-empty">Sin fabricaciones registradas en el período.</p>
        } @else {
          <div class="rf-grid-2">
            <reforma-chart-card title="Consumo por materia prima (kg)" icon="pi-chart-bar">
              <reforma-apex [options]="chartConsumos()" />
            </reforma-chart-card>
            <div class="reforma-table-wrap">
              <table class="reforma-table">
                <thead>
                  <tr><th>Fórmula</th><th class="num">Fabricaciones</th><th class="num">Kg producidos</th><th class="num">Costo total</th></tr>
                </thead>
                <tbody>
                  @for (f of inf.consumos.formulas; track f.codigo) {
                    <tr>
                      <td>{{ f.descripcion }} <small class="text-dim">({{ f.codigo }})</small></td>
                      <td class="num">{{ f.fabricaciones }}</td>
                      <td class="num">{{ f.kgProducidos | number: '1.0-0' }}</td>
                      <td class="num">$ {{ f.costoTotal | number: '1.0-0' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      </section>

      <!-- 6. IA -->
      <section class="seccion">
        <div class="seccion-head">
          <h3>6 · Inteligencia artificial</h3>
          <button type="button" class="reforma-btn-ghost reforma-btn-sm" (click)="descargarCsv('anomalias')">
            <i class="pi pi-download"></i> CSV anomalías
          </button>
        </div>

        <h4 class="sub-seccion">Alertas de precio del período</h4>
        @if (inf.ia.anomalias.length === 0) {
          <p class="reforma-empty">Sin alertas de precio en el período.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr><th>Factura</th><th>Fecha</th><th>Materia prima</th><th class="num">Precio ingresado</th><th class="num">Prom. histórico</th><th>Decisión</th></tr>
              </thead>
              <tbody>
                @for (a of inf.ia.anomalias; track $index) {
                  <tr>
                    <td>{{ a.numeroFactura }}</td>
                    <td>{{ a.fechaCompra | date: 'dd/MM/yyyy' }}</td>
                    <td>{{ a.nombreMateriaPrima }}</td>
                    <td class="num">$ {{ a.precioIngresado | number: '1.2-2' }}</td>
                    <td class="num">
                      @if (a.precioPromedioHistorico != null) {
                        $ {{ a.precioPromedioHistorico | number: '1.2-2' }}
                      } @else { — }
                    </td>
                    <td>{{ a.usuarioConfirmo == null ? '—' : a.usuarioConfirmo ? 'Confirmada' : 'Corregida' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }

        <h4 class="sub-seccion">Predicción de agotamiento de stock</h4>
        @if (!inf.ia.prediccionesDisponibles) {
          <p class="reforma-alert reforma-alert-warn">
            <i class="pi pi-lock"></i>
            La predicción de agotamiento de stock está disponible en los planes BUSINESS y ENTERPRISE.
          </p>
        } @else if (prediccionesConRiesgo().length === 0) {
          <p class="reforma-empty">Sin materias primas con riesgo de agotamiento calculado.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr><th>Materia prima</th><th>Nivel</th><th class="num">Días restantes</th><th>Fecha estimada</th></tr>
              </thead>
              <tbody>
                @for (p of prediccionesConRiesgo(); track p.codigoMateriaPrima) {
                  <tr>
                    <td>{{ p.nombreMateriaPrima }}</td>
                    <td><span class="nivel" [attr.data-nivel]="p.nivelAlerta">{{ p.nivelAlerta }}</span></td>
                    <td class="num">{{ p.diasRestantes ?? '—' }}</td>
                    <td>{{ p.fechaAgotamiento ? (p.fechaAgotamiento | date: 'dd/MM/yyyy') : '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
      }
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .toolbar {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        align-items: flex-start;
        flex-wrap: wrap;
        margin-bottom: 1rem;
      }
      .reforma-page-title {
        margin: 0;
      }
      .sub {
        margin: 0.25rem 0 0;
      }
      .mini {
        font-size: 0.85rem;
      }
      .periodo {
        border-radius: var(--reforma-radius);
        padding: 0.85rem 1rem;
        display: flex;
        flex-wrap: wrap;
        gap: 0.85rem;
        align-items: center;
        margin-bottom: 1.5rem;
      }
      .presets {
        display: flex;
        align-items: center;
        gap: 0.45rem;
        flex-wrap: wrap;
      }
      .presets .activo {
        background: var(--reforma-accent-soft);
        border-color: rgba(157, 119, 244, 0.45);
        color: #ede9fe;
      }
      .fechas {
        display: flex;
        align-items: flex-end;
        gap: 0.6rem;
        flex-wrap: wrap;
      }
      .fechas label {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }
      .fechas input {
        width: auto;
      }
      .descarga {
        margin-left: auto;
        display: flex;
        align-items: center;
        gap: 0.75rem;
      }
      .rango {
        white-space: nowrap;
      }
      .seccion {
        margin-bottom: 1.75rem;
      }
      .seccion-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 1rem;
        margin-bottom: 0.6rem;
      }
      .seccion-head h3 {
        margin: 0;
        font-size: 1.05rem;
        color: var(--reforma-text);
      }
      .sub-seccion {
        margin: 1.1rem 0 0.5rem;
        font-size: 0.92rem;
        color: var(--reforma-text-dim);
        font-weight: 600;
      }
      .rf-grid-2 {
        align-items: start;
      }
      .nivel {
        font-size: 0.72rem;
        font-weight: 700;
        padding: 0.14rem 0.5rem;
        border-radius: 999px;
        background: var(--glass-bg-hover);
        color: var(--reforma-text-dim);
        white-space: nowrap;
      }
      .nivel[data-nivel='CRITICO'] {
        color: #fca5a5;
        background: rgba(220, 38, 38, 0.18);
      }
      .nivel[data-nivel='ALERTA'] {
        color: #fdba74;
        background: rgba(234, 88, 12, 0.18);
      }
      .nivel[data-nivel='ATENCION'] {
        color: #fde047;
        background: rgba(202, 138, 4, 0.16);
      }
      .nivel[data-nivel='NORMAL'],
      .nivel[data-nivel='SIN_RIESGO'],
      .nivel[data-nivel='CRECIENTE'] {
        color: #86efac;
        background: rgba(22, 163, 74, 0.16);
      }
    `,
  ],
})
export class InformeComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly presets: { valor: PresetPeriodo; etiqueta: string }[] = [
    { valor: 30, etiqueta: '30 días' },
    { valor: 90, etiqueta: '90 días' },
    { valor: 365, etiqueta: '12 meses' },
    { valor: 'custom', etiqueta: 'Personalizado' },
  ];

  readonly preset = signal<PresetPeriodo>(90);
  readonly desde = signal<string>('');
  readonly hasta = signal<string>('');
  readonly informe = signal<InformeEstado | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly nombreGranja = signal<string>('');

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  // === Derivados para tablas/gráficos ===
  readonly inventarioConStock = computed(() =>
    (this.informe()?.inventario.items ?? []).filter((i) => i.cantidadReal > 0 || i.valorStock > 0),
  );

  /** Predicciones con señal útil (excluye SIN_DATOS para no llenar la tabla de ruido). */
  readonly prediccionesConRiesgo = computed(() =>
    (this.informe()?.ia.predicciones ?? []).filter((p) => p.nivelAlerta && p.nivelAlerta !== 'SIN_DATOS'),
  );

  readonly chartProveedores = computed(() => {
    const proveedores = this.informe()?.proveedores.proveedores ?? [];
    return barChart({
      categories: proveedores.map((p) => p.codigo),
      tooltipTitles: proveedores.map((p) => p.nombre),
      series: [{ name: 'Gasto', data: proveedores.map((p) => Math.round(p.monto)) }],
      horizontal: true,
      distributed: true,
      money: true,
      height: 280,
    });
  });

  readonly chartInventario = computed(() => {
    const top = [...this.inventarioConStock()]
      .sort((a, b) => b.valorStock - a.valorStock)
      .slice(0, 10);
    return barChart({
      categories: top.map((i) => i.codigoMateriaPrima),
      tooltipTitles: top.map((i) => i.nombreMateriaPrima),
      series: [{ name: 'Valor', data: top.map((i) => Math.round(i.valorStock)) }],
      money: true,
      height: 280,
    });
  });

  readonly chartEvolucion = computed(() => {
    const evolucion = this.informe()?.compras.evolucionMensual ?? [];
    return areaChart({
      categories: evolucion.map((p) => p.mes),
      series: [{ name: 'Gasto', data: evolucion.map((p) => Math.round(p.monto)) }],
      money: true,
      height: 280,
    });
  });

  readonly chartConsumos = computed(() => {
    const materias = (this.informe()?.consumos.materias ?? []).slice(0, 10);
    return barChart({
      categories: materias.map((m) => m.codigo),
      tooltipTitles: materias.map((m) => m.nombre),
      series: [{ name: 'Kg', data: materias.map((m) => Math.round(m.kgConsumidos)) }],
      height: 280,
    });
  });

  ngOnInit(): void {
    const g = this.idGranja;
    if (!g) {
      return;
    }
    this.api.getGranja(g).subscribe({
      next: (granja) => this.nombreGranja.set(granja.nombreGranja),
      error: () => this.nombreGranja.set(g),
    });
    this.elegirPreset(90);
  }

  elegirPreset(preset: PresetPeriodo): void {
    this.preset.set(preset);
    if (preset === 'custom') {
      // Precarga el rango vigente para que el usuario lo ajuste.
      const inf = this.informe();
      if (inf) {
        this.desde.set(inf.desde);
        this.hasta.set(inf.hasta);
      }
      return;
    }
    const hoy = new Date();
    const desde = new Date(hoy);
    desde.setDate(hoy.getDate() - preset);
    this.desde.set(desde.toISOString().slice(0, 10));
    this.hasta.set(hoy.toISOString().slice(0, 10));
    this.generar();
  }

  generar(): void {
    const g = this.idGranja;
    if (!g || !this.desde() || !this.hasta()) {
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.api.getInformeEstado(g, this.desde(), this.hasta()).subscribe({
      next: (informe) => {
        this.informe.set(informe);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo generar el informe'));
        this.cargando.set(false);
      },
    });
  }

  descargarHtml(): void {
    const inf = this.informe();
    if (!inf) {
      return;
    }
    const html = construirInformeHtml(this.nombreGranja() || inf.idGranja, inf);
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    descargarBlobComoArchivo(blob, `informe_${inf.idGranja}_${inf.desde}_${inf.hasta}.html`);
  }

  descargarCsv(seccion: SeccionInformeCsv): void {
    const inf = this.informe();
    if (!inf) {
      return;
    }
    this.api.exportarInformeCsv(this.idGranja, seccion, inf.desde, inf.hasta).subscribe({
      next: (blob) =>
        descargarBlobComoArchivo(blob, `informe_${seccion}_${inf.idGranja}_${inf.desde}_${inf.hasta}.csv`),
      error: (err: HttpErrorResponse) =>
        this.error.set(mensajeErrorHttp(err, 'No se pudo exportar el CSV')),
    });
  }
}
