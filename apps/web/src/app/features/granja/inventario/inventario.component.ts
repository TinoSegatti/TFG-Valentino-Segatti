import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { InventarioItem } from '../../../data/models/inventario.model';
import { MateriaPrima } from '../../../data/models/materia-prima.model';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { ChartCardComponent } from '../shared/chart-card.component';
import { ApexChartComponent } from '../shared/apex-chart.component';
import { barChart, donutChart, lineChart, ReformaChartOptions } from '../shared/apex-charts';
import { topConOtros, topNombrado } from '../shared/panel-utils';
import { NumeroFormatoDirective } from '../../../shared/numero-formato.directive';
import { ArchivoCrearModalComponent } from '../shared/archivo-crear-modal.component';
import { ArchivoResumen } from '../../../data/models/archivo.model';
import { OrdenTabla } from '../../../shared/orden-tabla';
import { AuthStateService } from '../../../core/auth/auth-state.service';
import { decodeJwtClaims } from '../../../core/auth/jwt.utils';
import {
  NivelAlertaStock,
  PrediccionStock,
  PrediccionStockDetalle,
  nivelAlertaColor,
  nivelAlertaLabel,
  planPermitePrediccion,
} from '../../../data/models/prediccion.model';

interface LineaInicializacion {
  idMateriaPrima: number | null;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  cantidadInicial: number | null;
  precioInicial: number | null;
}

type OrdenInicializacion = 'nombre' | 'codigo';

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    KpiCardComponent,
    ChartCardComponent,
    ApexChartComponent,
    NumeroFormatoDirective,
    ArchivoCrearModalComponent,
    RouterLink,
  ],
  template: `
    <header class="toolbar">
      <div>
        <h2 class="reforma-page-title">Inventario</h2>
        <p class="sub text-dim">Cantidades y valores de las materias primas registradas.</p>
      </div>
      <div class="acciones-top">
        @if (!inicializado()) {
          <button type="button" class="reforma-btn" (click)="abrirInicializacion()">
            <i class="pi pi-sliders-h"></i> Inicializar inventario
          </button>
        } @else {
          <button type="button" class="reforma-btn-ghost" (click)="archivoModal.set(true)" [disabled]="cargando()">
            <i class="pi pi-history"></i> Crear archivo
          </button>
          <button type="button" class="reforma-btn-ghost" (click)="recalcular()" [disabled]="cargando()">
            <i class="pi pi-refresh"></i> Recalcular
          </button>
          <button type="button" class="reforma-btn-danger" (click)="vaciar()" [disabled]="cargando()">
            <i class="pi pi-trash"></i> Vaciar inventario
          </button>
        }
      </div>
    </header>

    @if (resumen(); as r) {
      <section class="rf-grid-kpis">
        <reforma-kpi-card style="--rf-i: 0" icon="pi-box" label="Materias con stock" [value]="r.totalMaterias | number: '1.0-0'" />
        <reforma-kpi-card style="--rf-i: 1" icon="pi-database" label="Toneladas en stock" [value]="(r.toneladas | number: '1.0-2') + ' t'" accent="#06b6d4" />
        <reforma-kpi-card style="--rf-i: 2" icon="pi-dollar" label="Valor total del stock" [value]="'$ ' + (r.valorTotal | number: '1.0-0')" />
        <reforma-kpi-card style="--rf-i: 3" icon="pi-exclamation-triangle" label="Merma acumulada" [value]="(r.mermaTotal | number: '1.0-2') + ' kg'" accent="#fbbf24" />
      </section>

      <section class="rf-grid-halves">
        <reforma-chart-card
          title="Existencias por materia (kg)"
          icon="pi-chart-pie"
          [empty]="existencias().valores.length === 0"
          emptyText="Sin existencias cargadas"
        >
          <reforma-apex [options]="chartExistencias()" />
        </reforma-chart-card>
        <reforma-chart-card
          title="Valor de stock por materia"
          icon="pi-chart-bar"
          [empty]="valorStock().valores.length === 0"
          emptyText="Sin valor de stock para mostrar"
        >
          <reforma-apex [options]="chartValor()" />
        </reforma-chart-card>
      </section>
    }

    @if (cargando()) {
      <p class="reforma-empty">Cargando inventario…</p>
    } @else if (inventario().length === 0) {
      <p class="reforma-empty">
        No hay materias primas activas en el catálogo. Registralas primero en Materias primas.
      </p>
    } @else {
      <div class="reforma-table-wrap">
        <table class="reforma-table">
          <thead>
            <tr>
              <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
              <th class="sortable" [class.is-asc]="orden.esAsc('nombre')" [class.is-desc]="orden.esDesc('nombre')" (click)="orden.alternar('nombre')">Materia prima</th>
              <th class="num sortable" [class.is-asc]="orden.esAsc('precio')" [class.is-desc]="orden.esDesc('precio')" (click)="orden.alternar('precio')">Precio (vigente)</th>
              <th class="num sortable" [class.is-asc]="orden.esAsc('acumulada')" [class.is-desc]="orden.esDesc('acumulada')" (click)="orden.alternar('acumulada')">Cant. acumulada</th>
              <th class="num sortable" [class.is-asc]="orden.esAsc('sistema')" [class.is-desc]="orden.esDesc('sistema')" (click)="orden.alternar('sistema')">Cant. en sistema</th>
              <th class="num sortable" [class.is-asc]="orden.esAsc('real')" [class.is-desc]="orden.esDesc('real')" (click)="orden.alternar('real')">Cant. real</th>
              <th class="num sortable" [class.is-asc]="orden.esAsc('merma')" [class.is-desc]="orden.esDesc('merma')" (click)="orden.alternar('merma')">Merma</th>
              <th class="num sortable" [class.is-asc]="orden.esAsc('valor')" [class.is-desc]="orden.esDesc('valor')" (click)="orden.alternar('valor')">Valor de stock</th>
              <th class="num sortable" [class.is-asc]="orden.esAsc('almacen')" [class.is-desc]="orden.esDesc('almacen')" (click)="orden.alternar('almacen')">Precio almacén</th>
              @if (puedeVerPrediccion()) {
                <th>Riesgo de agotamiento</th>
              }
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (i of inventarioOrdenado(); track i.idMateriaPrima) {
              <tr [class.alerta]="i.cantidadReal <= 0">
                <td>{{ i.codigoMateriaPrima }}</td>
                <td>{{ i.nombreMateriaPrima }}</td>
                <td class="num">$ {{ i.precioPorKilo | number: '1.3-3' }}</td>
                <td class="num">{{ i.cantidadAcumulada | number: '1.3-3' }} kg</td>
                <td class="num">{{ i.cantidadSistema | number: '1.3-3' }} kg</td>
                <td class="num">{{ i.cantidadReal | number: '1.3-3' }} kg</td>
                <td
                  class="num"
                  [class.merma-pos]="mermaKg(i) > 0"
                  [class.merma-neg]="mermaKg(i) < 0"
                >
                  {{ mermaKg(i) | number: '1.3-3' }} kg
                </td>
                <td class="num">$ {{ i.valorStock | number: '1.2-2' }}</td>
                <td class="num">$ {{ i.precioAlmacen | number: '1.3-3' }}</td>
                @if (puedeVerPrediccion()) {
                  <td>
                    @if (prediccionDe(i.idMateriaPrima); as p) {
                      <span
                        class="riesgo-badge"
                        [style.--riesgo]="colorNivel(p.nivelAlerta)"
                        [title]="tituloRiesgo(p)"
                      >
                        {{ textoRiesgo(p) }}
                      </span>
                    } @else {
                      <span class="text-dim mini">—</span>
                    }
                  </td>
                }
                <td class="acciones-celda">
                  <button type="button" class="reforma-btn-ghost reforma-btn-sm" (click)="abrirEdicion(i)">
                    <i class="pi pi-pencil"></i> Editar real
                  </button>
                  @if (puedeVerPrediccion()) {
                    <button
                      type="button"
                      class="reforma-btn-ghost reforma-btn-sm"
                      (click)="abrirPrediccion(i)"
                    >
                      <i class="pi pi-chart-line"></i> Predicción
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (error()) {
      <p class="reforma-alert reforma-alert-error"><i class="pi pi-exclamation-circle"></i> {{ error() }}</p>
    }

    @if (archivoOk(); as a) {
      <p class="reforma-alert reforma-alert-ok">
        <i class="pi pi-check-circle"></i> Archivo {{ a.codigoArchivo }} creado.
        <a routerLink="../archivos">Ver archivos</a>
      </p>
    }

    <!-- Modal: crear archivo (snapshot inmutable del inventario) -->
    @if (archivoModal()) {
      <app-archivo-crear-modal
        tipo="INVENTARIO"
        [idGranja]="idGranja"
        (creado)="archivoCreado($event)"
        (cerrado)="archivoModal.set(false)"
      />
    }

    <!-- Modal: editar cantidad real -->
    @if (modoEdicion(); as e) {
      <div class="overlay" (click)="cerrarEdicion()"></div>
      <div class="modal glass-card-strong" role="dialog" aria-modal="true">
        <h3>Cantidad real — {{ e.nombreMateriaPrima }}</h3>
        <p class="mini text-dim">
          Sistema: {{ e.cantidadSistema | number: '1.3-3' }} kg · Precio vigente: $
          {{ e.precioPorKilo | number: '1.3-3' }}
        </p>
        <label class="reforma-field">
          <span>Cantidad real (kg)</span>
          <input class="reforma-input" [appNumero]="3" [(ngModel)]="cantidadRealEdit" />
        </label>
        <label class="reforma-field">
          <span>Observaciones (opcional)</span>
          <input class="reforma-input" type="text" [(ngModel)]="observacionesEdit" maxlength="200" />
        </label>
        <p class="mini text-dim">
          Merma resultante: {{ mermaPreview() | number: '1.3-3' }} kg · Valor stock previsto: $
          {{ valorStockPreview() | number: '1.2-2' }}
        </p>
        <div class="acciones-modal">
          <button type="button" class="reforma-btn-ghost" (click)="cerrarEdicion()">Cancelar</button>
          <button
            type="button"
            class="reforma-btn"
            (click)="guardarCantidadReal()"
            [disabled]="guardandoEdicion()"
          >
            Guardar
          </button>
        </div>
      </div>
    }

    <!-- Modal: predicción de agotamiento (gráfico) -->
    @if (prediccionAbierta()) {
      <div class="overlay" (click)="cerrarPrediccion()"></div>
      <div class="modal modal-ancho glass-card-strong" role="dialog" aria-modal="true">
        @if (cargandoPrediccion()) {
          <h3>Predicción de agotamiento</h3>
          <p class="mini text-dim">Calculando proyección…</p>
        } @else if (prediccionModal()) {
          @if (prediccionModal(); as d) {
          <h3>
            Predicción de agotamiento — {{ d.resumen.nombreMateriaPrima }}
            <span
              class="riesgo-badge"
              [style.--riesgo]="colorNivel(d.resumen.nivelAlerta)"
            >{{ etiquetaNivel(d.resumen.nivelAlerta) }}</span>
          </h3>
          <p class="mini text-dim">
            @if (d.resumen.nivelAlerta === 'SIN_DATOS') {
              Historial insuficiente para proyectar (se necesitan al menos 2 meses con compras o
              fabricaciones de esta materia prima).
            } @else if (d.resumen.diasRestantes != null) {
              Al ritmo actual, el stock se agota en
              <strong>{{ d.resumen.diasRestantes }} días</strong>
              @if (d.resumen.fechaAgotamiento) {
                (aprox. {{ d.resumen.fechaAgotamiento | date: 'dd/MM/yyyy' }})
              }.
            } @else {
              La tendencia es <strong>{{ tendenciaLabel(d.resumen.tendencia) }}</strong>: al ritmo
              actual no se proyecta agotamiento.
            }
          </p>

          @if (chartPrediccion(); as chart) {
            <reforma-apex [options]="chart" />
            <p class="mini text-dim zoom-hint">
              <i class="pi pi-search-plus"></i> Arrastrá sobre el gráfico para hacer zoom en un
              rango; usá la barra superior para acercar/alejar, panear o restablecer.
            </p>
          }

          <div class="pred-metricas">
            <div><span class="text-dim mini">Stock actual</span><strong>{{ d.resumen.stockActual | number: '1.0-2' }} kg</strong></div>
            <div><span class="text-dim mini">Ingreso mensual prom.</span><strong>{{ d.resumen.ingresoPromedio | number: '1.0-2' }} kg</strong></div>
            <div><span class="text-dim mini">Consumo mensual prom.</span><strong>{{ d.resumen.consumoPromedio | number: '1.0-2' }} kg</strong></div>
            <div>
              <span class="text-dim mini">Flujo neto mensual</span>
              <strong [class.neto-pos]="d.resumen.netoPromedio >= 0" [class.neto-neg]="d.resumen.netoPromedio < 0">
                {{ d.resumen.netoPromedio >= 0 ? '+' : '' }}{{ d.resumen.netoPromedio | number: '1.0-2' }} kg
              </strong>
            </div>
          </div>
          }
        } @else {
          <h3>Predicción de agotamiento</h3>
          <p class="mini text-dim">{{ errorPrediccion() ?? 'No se pudo calcular la predicción.' }}</p>
        }
        <div class="acciones-modal">
          <button type="button" class="reforma-btn" (click)="cerrarPrediccion()">Cerrar</button>
        </div>
      </div>
    }

    <!-- Modal: inicializar inventario -->
    @if (modoInicializar()) {
      <div class="overlay" (click)="cerrarInicializacion()"></div>
      <div class="modal modal-ancho glass-card-strong" role="dialog" aria-modal="true">
        <h3>Inicializar inventario</h3>
        <p class="mini text-dim">
          Cargá la cantidad y precio iniciales de cada materia prima. Es el punto de partida y
          alimenta el cálculo del precio almacén. Los campos vacíos se inicializan en 0, por lo que
          podés dejar en cero las materias primas sin existencias.
        </p>
        <div class="orden-toolbar" role="group" aria-label="Ordenar materias primas">
          <span class="mini text-dim">Ordenar por:</span>
          <button
            type="button"
            class="reforma-btn-ghost reforma-btn-sm"
            [class.activo]="ordenIni() === 'nombre'"
            [attr.aria-pressed]="ordenIni() === 'nombre'"
            (click)="cambiarOrdenIni('nombre')"
          >
            <i class="pi pi-sort-alpha-down"></i> Nombre (A–Z)
          </button>
          <button
            type="button"
            class="reforma-btn-ghost reforma-btn-sm"
            [class.activo]="ordenIni() === 'codigo'"
            [attr.aria-pressed]="ordenIni() === 'codigo'"
            (click)="cambiarOrdenIni('codigo')"
          >
            <i class="pi pi-sort-numeric-down"></i> Código (menor a mayor)
          </button>
        </div>
        <div class="reforma-table-wrap ini-wrap">
          <table class="reforma-table ini">
            <thead>
              <tr>
                <th>Código</th>
                <th>Materia prima</th>
                <th class="num">Cantidad inicial (kg)</th>
                <th class="num">Precio inicial ($/kg)</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              @for (linea of lineasIni(); track $index; let idx = $index) {
                <tr>
                  <td>
                    <input
                      class="reforma-input"
                      type="text"
                      [ngModel]="linea.codigoMateriaPrima"
                      (ngModelChange)="setCodigoLinea(idx, $event)"
                      list="lista-mp-codigo"
                      placeholder="Código"
                    />
                  </td>
                  <td>
                    <input
                      class="reforma-input"
                      type="text"
                      [ngModel]="linea.nombreMateriaPrima"
                      (ngModelChange)="setNombreLinea(idx, $event)"
                      list="lista-mp-nombre"
                      placeholder="Nombre"
                    />
                  </td>
                  <td class="num">
                    <input
                      class="reforma-input"
                      [appNumero]="3"
                      [ngModel]="linea.cantidadInicial"
                      (ngModelChange)="setCantidadLinea(idx, $event)"
                    />
                  </td>
                  <td class="num">
                    <input
                      class="reforma-input"
                      [appNumero]="3"
                      [ngModel]="linea.precioInicial"
                      (ngModelChange)="setPrecioLinea(idx, $event)"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      class="reforma-btn-danger reforma-btn-sm"
                      (click)="quitarLineaIni(idx)"
                      [disabled]="lineasIni().length <= 1"
                    >
                      Quitar
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <datalist id="lista-mp-codigo">
          @for (mp of materias(); track mp.id) {
            <option [value]="mp.codigoMateriaPrima"></option>
          }
        </datalist>
        <datalist id="lista-mp-nombre">
          @for (mp of materias(); track mp.id) {
            <option [value]="mp.nombreMateriaPrima"></option>
          }
        </datalist>
        <div class="acciones-modal">
          <button type="button" class="reforma-btn-ghost" (click)="agregarLineaIni()">
            <i class="pi pi-plus"></i> Agregar línea
          </button>
          <span class="spacer"></span>
          <button type="button" class="reforma-btn-ghost" (click)="cerrarInicializacion()">Cancelar</button>
          <button
            type="button"
            class="reforma-btn"
            (click)="confirmarInicializacion()"
            [disabled]="guardandoIni() || !lineasIniValidas()"
          >
            Inicializar
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
      .toolbar {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        align-items: flex-start;
        flex-wrap: wrap;
      }
      .reforma-page-title {
        margin: 0;
      }
      .sub {
        margin: 0.25rem 0 0;
      }
      .acciones-top {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
      .resumen {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
        gap: 1rem;
        margin: 1.25rem 0;
      }
      .kpi {
        padding: 1rem 1.25rem;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }
      .kpi .label {
        font-size: 0.82rem;
        color: var(--reforma-text-dim);
      }
      .kpi .valor {
        font-size: 1.6rem;
        font-weight: 700;
        color: var(--reforma-text);
      }
      .kpi .valor small {
        font-size: 0.9rem;
        font-weight: 500;
        color: var(--reforma-text-dim);
      }
      .reforma-table td {
        white-space: nowrap;
      }
      tr.alerta td {
        background: rgba(248, 113, 113, 0.10);
      }
      .merma-pos {
        color: var(--reforma-warn);
      }
      .merma-neg {
        color: var(--reforma-cyan);
      }
      /* Indicador de riesgo de agotamiento (RF-IA-PRED) */
      .riesgo-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.2rem 0.6rem;
        border-radius: 999px;
        font-size: 0.78rem;
        font-weight: 600;
        white-space: nowrap;
        color: var(--riesgo, #6b7280);
        background: color-mix(in srgb, var(--riesgo, #6b7280) 14%, transparent);
        border: 1px solid color-mix(in srgb, var(--riesgo, #6b7280) 45%, transparent);
      }
      .acciones-celda {
        display: flex;
        gap: 0.4rem;
        flex-wrap: wrap;
      }
      .pred-metricas {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
        gap: 0.75rem;
        margin-top: 1rem;
      }
      .pred-metricas > div {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
      }
      .neto-pos {
        color: var(--reforma-cyan);
      }
      .neto-neg {
        color: var(--reforma-warn);
      }
      .zoom-hint {
        margin-top: 0.35rem;
      }
      /* Toolbar de zoom de ApexCharts sobre fondo oscuro */
      :host ::ng-deep .apexcharts-toolbar svg {
        fill: #9aa7b8;
      }
      :host ::ng-deep .apexcharts-toolbar .apexcharts-selected svg {
        fill: var(--reforma-cyan, #06b6d4);
      }
      /* Modales */
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
        width: min(440px, 92vw);
        z-index: 41;
      }
      .modal-ancho {
        width: min(920px, 96vw);
        max-height: 80vh;
        overflow: auto;
      }
      .modal h3 {
        margin: 0 0 0.5rem;
        color: var(--reforma-text);
      }
      .modal .reforma-field {
        margin-top: 0.85rem;
      }
      .mini {
        font-size: 0.85rem;
        margin: 0.35rem 0;
      }
      .acciones-modal {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        align-items: center;
        margin-top: 1.25rem;
      }
      .acciones-modal .spacer {
        flex: 1;
      }
      .ini-wrap {
        margin-top: 0.75rem;
      }
      .orden-toolbar {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.5rem;
        margin-top: 0.85rem;
      }
      .orden-toolbar .reforma-btn-sm.activo {
        background: var(--reforma-accent, #06b6d4);
        border-color: var(--reforma-accent, #06b6d4);
        color: #0b1120;
      }
      table.ini input {
        margin: 0;
      }
    `,
  ],
})
export class InventarioComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly inventario = signal<InventarioItem[]>([]);
  readonly orden = new OrdenTabla();
  readonly inventarioOrdenado = computed(() =>
    this.orden.ordenar(this.inventario(), {
      codigo: (i) => i.codigoMateriaPrima,
      nombre: (i) => i.nombreMateriaPrima,
      precio: (i) => i.precioPorKilo,
      acumulada: (i) => i.cantidadAcumulada,
      sistema: (i) => i.cantidadSistema,
      real: (i) => i.cantidadReal,
      merma: (i) => this.mermaKg(i),
      valor: (i) => i.valorStock,
      almacen: (i) => i.precioAlmacen,
    }),
  );
  readonly inicializado = signal(false);
  readonly materias = signal<MateriaPrima[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly modoEdicion = signal<InventarioItem | null>(null);
  cantidadRealEdit = 0;
  observacionesEdit = '';
  readonly guardandoEdicion = signal(false);

  readonly archivoModal = signal(false);
  readonly archivoOk = signal<ArchivoResumen | null>(null);

  readonly modoInicializar = signal(false);
  readonly lineasIni = signal<LineaInicializacion[]>([this.lineaVacia()]);
  readonly guardandoIni = signal(false);
  readonly ordenIni = signal<OrdenInicializacion>('nombre');

  // --- IA: predicción de agotamiento (RF-IA-PRED) ---
  private readonly auth = inject(AuthStateService);
  /** Predicción por MP (idMateriaPrima -> resumen), para el indicador de riesgo de la tabla. */
  readonly predicciones = signal<Map<number, PrediccionStock>>(new Map());
  readonly prediccionModal = signal<PrediccionStockDetalle | null>(null);
  readonly prediccionAbierta = signal(false);
  readonly cargandoPrediccion = signal(false);
  readonly errorPrediccion = signal<string | null>(null);

  /** RD-03: la predicción es exclusiva de BUSINESS/ENTERPRISE (se lee el plan del JWT). */
  readonly puedeVerPrediccion = computed(() =>
    planPermitePrediccion(decodeJwtClaims(this.auth.getToken())?.planSuscripcion),
  );

  readonly chartPrediccion = computed<ReformaChartOptions | null>(() => {
    const d = this.prediccionModal();
    if (!d || d.serieHistorica.length === 0) return null;
    const hist = d.serieHistorica;
    const proy = d.serieProyeccion ?? [];
    const categorias = [...hist, ...proy].map((p) => this.mesLabel(p.mes));
    const nHist = hist.length;
    const historico = [...hist.map((p) => p.existencias), ...proy.map(() => null as number | null)];
    // La proyección arranca en el último punto real para que las líneas se conecten.
    const proyeccion = [
      ...hist.map((p, idx) => (idx === nHist - 1 ? p.existencias : (null as number | null))),
      ...proy.map((p) => p.existencias),
    ];
    const base = lineChart({
      categories: categorias,
      series: [
        { name: 'Existencias', data: historico },
        { name: 'Proyección', data: proyeccion },
      ],
      height: 300,
    });
    const hayAgotamiento = d.resumen.diasRestantes != null;
    return {
      ...base,
      // Zoom para inspeccionar la proyección: arrastrar selecciona un rango; la barra
      // ofrece +/-, paneo y reset. Solo en este gráfico (los del dashboard son estáticos).
      chart: {
        ...base.chart,
        zoom: { enabled: true, type: 'x', autoScaleYaxis: true },
        toolbar: {
          show: true,
          tools: { download: false, selection: false, zoom: true, zoomin: true, zoomout: true, pan: true, reset: true },
          autoSelected: 'zoom',
        },
      },
      colors: ['#06b6d4', '#fb923c'],
      stroke: { curve: 'straight', width: [3, 2], dashArray: [0, 6] },
      annotations: hayAgotamiento
        ? {
            yaxis: [
              {
                y: 0,
                borderColor: '#f87171',
                strokeDashArray: 4,
                label: {
                  text: 'Agotamiento',
                  style: { color: '#fff', background: '#f87171' },
                },
              },
            ],
          }
        : {},
    };
  });

  readonly resumen = computed(() => {
    const items = this.inventario();
    if (items.length === 0) return null;
    const conStock = items.filter((i) => i.cantidadReal > 0);
    const kilosReales = conStock.reduce((acc, i) => acc + i.cantidadReal, 0);
    const toneladas = kilosReales / 1000;
    const valorTotal = conStock.reduce((acc, i) => acc + i.valorStock, 0);
    const mermaTotal = conStock.reduce(
      (acc, i) => acc + (i.cantidadSistema - i.cantidadReal),
      0,
    );
    return {
      totalMaterias: conStock.length,
      toneladas,
      valorTotal,
      mermaTotal,
    };
  });

  // Distribución de existencias (kg) y valor de stock, derivadas del inventario.
  // El donut usa el nombre de la MP como etiqueta (se lee al pasar por encima); la
  // barra muestra el código en el eje y el nombre en el tooltip.
  readonly existencias = computed(() =>
    topConOtros(
      this.inventario()
        .filter((i) => i.cantidadReal > 0)
        .map((i) => ({ label: i.nombreMateriaPrima || i.codigoMateriaPrima, valor: i.cantidadReal })),
      8,
    ),
  );
  readonly valorStock = computed(() =>
    topNombrado(
      this.inventario()
        .filter((i) => i.valorStock > 0)
        .map((i) => ({
          label: i.codigoMateriaPrima,
          nombre: i.nombreMateriaPrima,
          valor: i.valorStock,
        })),
      9,
    ),
  );
  readonly chartExistencias = computed(() =>
    donutChart({
      labels: this.existencias().labels,
      series: this.existencias().valores,
      totalLabel: 'Total kg',
      totalFormatter: (t) => Math.round(t).toLocaleString('en-US') + ' kg',
      height: 280,
    }),
  );
  readonly chartValor = computed(() =>
    barChart({
      categories: this.valorStock().labels,
      series: [{ name: 'Valor', data: this.valorStock().valores }],
      distributed: true,
      money: true,
      height: 280,
      tooltipTitles: this.valorStock().nombres,
    }),
  );

  readonly mermaPreview = computed(() => {
    const item = this.modoEdicion();
    if (!item) return 0;
    return item.cantidadSistema - this.cantidadRealEdit;
  });

  readonly valorStockPreview = computed(() => {
    const item = this.modoEdicion();
    if (!item) return 0;
    return Math.max(0, this.cantidadRealEdit) * item.precioPorKilo;
  });

  protected get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  archivoCreado(archivo: ArchivoResumen): void {
    this.archivoModal.set(false);
    this.archivoOk.set(archivo);
  }

  ngOnInit(): void {
    this.recargar();
    this.cargarMaterias();
    this.cargarPredicciones();
  }

  recargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.getInventario(this.idGranja).subscribe({
      next: (resp) => {
        this.inventario.set(resp.items);
        this.inicializado.set(resp.inicializado);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cargar el inventario'));
        this.cargando.set(false);
      },
    });
  }

  recalcular(): void {
    this.cargando.set(true);
    this.api.recalcularInventario(this.idGranja).subscribe({
      next: (resp) => {
        this.inventario.set(resp.items);
        this.inicializado.set(resp.inicializado);
        this.cargando.set(false);
        this.cargarPredicciones();
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo recalcular el inventario'));
        this.cargando.set(false);
      },
    });
  }

  vaciar(): void {
    if (!confirm('Esto eliminará todo el inventario y la inicialización. ¿Continuar?')) return;
    this.cargando.set(true);
    this.api.vaciarInventario(this.idGranja).subscribe({
      next: () => this.recargar(),
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo vaciar el inventario'));
        this.cargando.set(false);
      },
    });
  }

  /** merma = cantidad en sistema - cantidad real (kg). */
  mermaKg(item: InventarioItem): number {
    return item.cantidadSistema - item.cantidadReal;
  }

  // --- IA: predicción de agotamiento ---

  /** Carga (o refresca) el resumen de predicción de todas las MPs para los badges de la tabla. */
  cargarPredicciones(): void {
    if (!this.puedeVerPrediccion()) return;
    this.api.getPrediccionesInventario(this.idGranja).subscribe({
      next: (lista) => this.predicciones.set(new Map(lista.map((p) => [p.idMateriaPrima, p]))),
      // Fail-open: si falla, la tabla simplemente no muestra el indicador de riesgo.
      error: () => this.predicciones.set(new Map()),
    });
  }

  prediccionDe(idMateriaPrima: number): PrediccionStock | undefined {
    const p = this.predicciones().get(idMateriaPrima);
    return p && p.nivelAlerta !== 'SIN_DATOS' ? p : undefined;
  }

  colorNivel(nivel: NivelAlertaStock): string {
    return nivelAlertaColor(nivel);
  }

  etiquetaNivel(nivel: NivelAlertaStock): string {
    return nivelAlertaLabel(nivel);
  }

  /** Texto compacto del badge de la tabla: días restantes, o la tendencia si no hay agotamiento. */
  textoRiesgo(p: PrediccionStock): string {
    if (p.diasRestantes != null) return `${p.diasRestantes} días`;
    if (p.tendencia === 'CRECIENTE') return '↑ creciente';
    return 'estable';
  }

  tituloRiesgo(p: PrediccionStock): string {
    if (p.diasRestantes != null) {
      const fecha = p.fechaAgotamiento ? ` (aprox. ${p.fechaAgotamiento})` : '';
      return `${nivelAlertaLabel(p.nivelAlerta)}: se agota en ${p.diasRestantes} días${fecha}`;
    }
    return `${nivelAlertaLabel(p.nivelAlerta)}: sin riesgo de agotamiento al ritmo actual`;
  }

  tendenciaLabel(t: string): string {
    if (t === 'CRECIENTE') return 'creciente';
    if (t === 'DECRECIENTE') return 'decreciente';
    return 'estable';
  }

  /** "2026-07" -> "jul 2026" (para el eje del gráfico). */
  mesLabel(mes: string): string {
    const [anio, m] = mes.split('-');
    const nombres = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    const idx = Number(m) - 1;
    return `${nombres[idx] ?? m} ${anio}`;
  }

  abrirPrediccion(item: InventarioItem): void {
    this.prediccionAbierta.set(true);
    this.prediccionModal.set(null);
    this.errorPrediccion.set(null);
    this.cargandoPrediccion.set(true);
    this.api.getPrediccionStock(this.idGranja, item.idMateriaPrima).subscribe({
      next: (detalle) => {
        this.prediccionModal.set(detalle);
        this.cargandoPrediccion.set(false);
        // Aprovecha para refrescar el badge de esa fila.
        this.predicciones.update((prev) => {
          const next = new Map(prev);
          next.set(detalle.resumen.idMateriaPrima, detalle.resumen);
          return next;
        });
      },
      error: (err: HttpErrorResponse) => {
        this.errorPrediccion.set(mensajeErrorHttp(err, 'No se pudo calcular la predicción'));
        this.cargandoPrediccion.set(false);
      },
    });
  }

  cerrarPrediccion(): void {
    this.prediccionAbierta.set(false);
    this.prediccionModal.set(null);
  }

  abrirEdicion(item: InventarioItem): void {
    this.modoEdicion.set(item);
    this.cantidadRealEdit = item.cantidadReal;
    this.observacionesEdit = '';
  }

  cerrarEdicion(): void {
    this.modoEdicion.set(null);
  }

  guardarCantidadReal(): void {
    const item = this.modoEdicion();
    if (!item) return;
    if (this.cantidadRealEdit < 0 || Number.isNaN(this.cantidadRealEdit)) {
      this.error.set('La cantidad real no puede ser negativa');
      return;
    }
    this.guardandoEdicion.set(true);
    this.api
      .actualizarCantidadRealInventario(this.idGranja, item.idMateriaPrima, {
        cantidadReal: this.cantidadRealEdit,
        observaciones: this.observacionesEdit.trim() || undefined,
      })
      .subscribe({
        next: (actualizado) => {
          this.inventario.update((items) =>
            items.map((i) => (i.idMateriaPrima === actualizado.idMateriaPrima ? actualizado : i)),
          );
          this.guardandoEdicion.set(false);
          this.cerrarEdicion();
          this.cargarPredicciones();
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(mensajeErrorHttp(err, 'No se pudo actualizar la cantidad real'));
          this.guardandoEdicion.set(false);
        },
      });
  }

  abrirInicializacion(): void {
    const lineas = this.materias().map((mp) => ({
      idMateriaPrima: mp.id,
      codigoMateriaPrima: mp.codigoMateriaPrima,
      nombreMateriaPrima: mp.nombreMateriaPrima,
      cantidadInicial: 0,
      precioInicial: mp.precioPorKilo > 0 ? mp.precioPorKilo : 0,
    }));
    const lineasIniciales = lineas.length > 0 ? lineas : [this.lineaVacia()];
    this.lineasIni.set(this.ordenarLineas(lineasIniciales, this.ordenIni()));
    this.modoInicializar.set(true);
  }

  /** Cambia el criterio de ordenamiento del listado y reordena las líneas cargadas. */
  cambiarOrdenIni(orden: OrdenInicializacion): void {
    this.ordenIni.set(orden);
    this.lineasIni.update((lineas) => this.ordenarLineas(lineas, orden));
  }

  /**
   * Ordena las líneas según el criterio elegido. El código se compara de forma natural
   * (numérica) para que "MP2" quede antes que "MP10". Las líneas sin materia prima asignada
   * quedan al final para no interferir con la edición manual.
   */
  private ordenarLineas(
    lineas: LineaInicializacion[],
    orden: OrdenInicializacion,
  ): LineaInicializacion[] {
    return [...lineas].sort((a, b) => {
      const aAsignada = a.idMateriaPrima != null;
      const bAsignada = b.idMateriaPrima != null;
      if (aAsignada !== bAsignada) return aAsignada ? -1 : 1;
      if (orden === 'codigo') {
        return a.codigoMateriaPrima.localeCompare(b.codigoMateriaPrima, 'es', {
          numeric: true,
          sensitivity: 'base',
        });
      }
      return a.nombreMateriaPrima.localeCompare(b.nombreMateriaPrima, 'es', {
        sensitivity: 'base',
      });
    });
  }

  cerrarInicializacion(): void {
    this.modoInicializar.set(false);
  }

  agregarLineaIni(): void {
    this.lineasIni.update((lineas) => [...lineas, this.lineaVacia()]);
  }

  quitarLineaIni(idx: number): void {
    this.lineasIni.update((lineas) => lineas.filter((_, i) => i !== idx));
  }

  setCodigoLinea(idx: number, valor: string): void {
    this.lineasIni.update((lineas) => {
      const copia = [...lineas];
      const linea = { ...copia[idx], codigoMateriaPrima: valor };
      const mp = this.materias().find(
        (m) => m.codigoMateriaPrima.toLowerCase() === valor.toLowerCase(),
      );
      if (mp) {
        linea.idMateriaPrima = mp.id;
        linea.nombreMateriaPrima = mp.nombreMateriaPrima;
        if (linea.precioInicial == null && mp.precioPorKilo > 0) {
          linea.precioInicial = mp.precioPorKilo;
        }
      }
      copia[idx] = linea;
      return copia;
    });
  }

  setNombreLinea(idx: number, valor: string): void {
    this.lineasIni.update((lineas) => {
      const copia = [...lineas];
      const linea = { ...copia[idx], nombreMateriaPrima: valor };
      const mp = this.materias().find(
        (m) => m.nombreMateriaPrima.toLowerCase() === valor.toLowerCase(),
      );
      if (mp) {
        linea.idMateriaPrima = mp.id;
        linea.codigoMateriaPrima = mp.codigoMateriaPrima;
        if (linea.precioInicial == null && mp.precioPorKilo > 0) {
          linea.precioInicial = mp.precioPorKilo;
        }
      }
      copia[idx] = linea;
      return copia;
    });
  }

  setCantidadLinea(idx: number, valor: number | null): void {
    this.lineasIni.update((lineas) => {
      const copia = [...lineas];
      copia[idx] = { ...copia[idx], cantidadInicial: valor };
      return copia;
    });
  }

  setPrecioLinea(idx: number, valor: number | null): void {
    this.lineasIni.update((lineas) => {
      const copia = [...lineas];
      copia[idx] = { ...copia[idx], precioInicial: valor };
      return copia;
    });
  }

  lineasIniValidas(): boolean {
    const lineas = this.lineasIni();
    if (lineas.length === 0) return false;
    const idsVistos = new Set<number>();
    let hayLineaConMateria = false;
    for (const linea of lineas) {
      if (linea.idMateriaPrima == null) continue;
      // Los campos vacíos se interpretan como 0; solo se rechazan valores negativos.
      if ((linea.cantidadInicial ?? 0) < 0) return false;
      if ((linea.precioInicial ?? 0) < 0) return false;
      if (idsVistos.has(linea.idMateriaPrima)) return false;
      idsVistos.add(linea.idMateriaPrima);
      hayLineaConMateria = true;
    }
    return hayLineaConMateria;
  }

  confirmarInicializacion(): void {
    if (!this.lineasIniValidas()) return;
    const lineasPayload = this.lineasIni().filter((l) => l.idMateriaPrima != null);
    if (lineasPayload.length === 0) return;
    const payload = {
      lineas: lineasPayload.map((l) => ({
        idMateriaPrima: l.idMateriaPrima!,
        cantidadInicial: l.cantidadInicial ?? 0,
        precioInicial: l.precioInicial ?? 0,
      })),
    };
    this.guardandoIni.set(true);
    this.api.inicializarInventario(this.idGranja, payload).subscribe({
      next: (items) => {
        this.inventario.set(items);
        this.inicializado.set(true);
        this.guardandoIni.set(false);
        this.cerrarInicializacion();
        this.recargar();
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo inicializar el inventario'));
        this.guardandoIni.set(false);
      },
    });
  }

  private cargarMaterias(): void {
    this.api.getMateriasPrimas(this.idGranja).subscribe({
      next: (lista) => this.materias.set(lista.filter((m) => m.activa)),
      error: () => this.materias.set([]),
    });
  }

  private lineaVacia(): LineaInicializacion {
    return {
      idMateriaPrima: null,
      codigoMateriaPrima: '',
      nombreMateriaPrima: '',
      cantidadInicial: null,
      precioInicial: null,
    };
  }
}
