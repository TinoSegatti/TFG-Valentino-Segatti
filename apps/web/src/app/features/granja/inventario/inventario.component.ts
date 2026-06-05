import { Component, LOCALE_ID, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe, registerLocaleData } from '@angular/common';
import localeEsAr from '@angular/common/locales/es-AR';

registerLocaleData(localeEsAr);
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { InventarioItem } from '../../../data/models/inventario.model';
import { MateriaPrima } from '../../../data/models/materia-prima.model';

interface LineaInicializacion {
  idMateriaPrima: number | null;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  cantidadInicial: number | null;
  precioInicial: number | null;
}

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  providers: [{ provide: LOCALE_ID, useValue: 'es-AR' }],
  template: `
    <header class="toolbar">
      <div>
        <h2>Inventario</h2>
        <p class="sub">Cantidades y valores de las materias primas registradas.</p>
      </div>
      <div class="acciones-top">
        @if (!inicializado()) {
          <button type="button" class="btn primary" (click)="abrirInicializacion()">
            Inicializar inventario
          </button>
        } @else {
          <button type="button" class="btn" (click)="recalcular()" [disabled]="cargando()">
            Recalcular
          </button>
          <button type="button" class="btn danger" (click)="vaciar()" [disabled]="cargando()">
            Vaciar inventario
          </button>
        }
      </div>
    </header>

    @if (resumen(); as r) {
      <section class="resumen">
        <article>
          <span class="label">Materias primas</span>
          <span class="valor">{{ r.totalMaterias }}</span>
        </article>
        <article>
          <span class="label">Toneladas en stock</span>
          <span class="valor">{{ r.toneladas | number: '1.0-2' : 'es-AR' }} t</span>
        </article>
        <article>
          <span class="label">Valor total del stock</span>
          <span class="valor">$ {{ r.valorTotal | number: '1.2-2' }}</span>
        </article>
        <article>
          <span class="label">Merma acumulada</span>
          <span class="valor">{{ r.mermaTotal | number: '1.3-3' }} kg</span>
        </article>
      </section>
    }

    @if (cargando()) {
      <p>Cargando inventario…</p>
    } @else if (inventario().length === 0) {
      <p class="vacio">
        No hay materias primas activas en el catalogo. Registralas primero en Materias primas.
      </p>
    } @else {
      <div class="tabla-wrap">
        <table>
          <thead>
            <tr>
              <th>Codigo</th>
              <th>Materia prima</th>
              <th class="num">Precio (vigente)</th>
              <th class="num">Cant. acumulada</th>
              <th class="num">Cant. en sistema</th>
              <th class="num">Cant. real</th>
              <th class="num">Merma</th>
              <th class="num">Valor de stock</th>
              <th class="num">Precio almacen</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (i of inventario(); track i.idMateriaPrima) {
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
                  {{ mermaKg(i) | number: '1.3-3' : 'es-AR' }} kg
                </td>
                <td class="num">$ {{ i.valorStock | number: '1.2-2' }}</td>
                <td class="num">$ {{ i.precioAlmacen | number: '1.3-3' }}</td>
                <td>
                  <button type="button" class="link" (click)="abrirEdicion(i)">
                    Editar cantidad real
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <!-- Modal: editar cantidad real -->
    @if (modoEdicion(); as e) {
      <div class="overlay" (click)="cerrarEdicion()"></div>
      <div class="modal" role="dialog" aria-modal="true">
        <h3>Cantidad real - {{ e.nombreMateriaPrima }}</h3>
        <p class="mini">
          Sistema: {{ e.cantidadSistema | number: '1.3-3' }} kg | Precio vigente: $
          {{ e.precioPorKilo | number: '1.3-3' }}
        </p>
        <label>
          Cantidad real (kg)
          <input type="number" min="0" step="0.001" [(ngModel)]="cantidadRealEdit" />
        </label>
        <label>
          Observaciones (opcional)
          <input type="text" [(ngModel)]="observacionesEdit" maxlength="200" />
        </label>
        <p class="mini">
          Merma resultante: {{ mermaPreview() | number: '1.3-3' }} kg | Valor stock previsto: $
          {{ valorStockPreview() | number: '1.2-2' }}
        </p>
        <div class="acciones-modal">
          <button type="button" class="btn" (click)="cerrarEdicion()">Cancelar</button>
          <button
            type="button"
            class="btn primary"
            (click)="guardarCantidadReal()"
            [disabled]="guardandoEdicion()"
          >
            Guardar
          </button>
        </div>
      </div>
    }

    <!-- Modal: inicializar inventario -->
    @if (modoInicializar()) {
      <div class="overlay" (click)="cerrarInicializacion()"></div>
      <div class="modal modal-ancho" role="dialog" aria-modal="true">
        <h3>Inicializar inventario</h3>
        <p class="mini">
          Carga la cantidad y precio iniciales de cada materia prima. Esto se considera como punto
          de partida y alimenta el calculo del precio almacen.
        </p>
        <table class="ini">
          <thead>
            <tr>
              <th>Codigo</th>
              <th>Materia prima</th>
              <th class="num">Cantidad inicial (kg)</th>
              <th class="num">Precio inicial ($/kg)</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (linea of lineasIni(); track $index; let idx = $index) {
              <tr>
                <td>
                  <input
                    type="text"
                    [ngModel]="linea.codigoMateriaPrima"
                    (ngModelChange)="setCodigoLinea(idx, $event)"
                    list="lista-mp-codigo"
                    placeholder="Codigo"
                  />
                </td>
                <td>
                  <input
                    type="text"
                    [ngModel]="linea.nombreMateriaPrima"
                    (ngModelChange)="setNombreLinea(idx, $event)"
                    list="lista-mp-nombre"
                    placeholder="Nombre"
                  />
                </td>
                <td class="num">
                  <input
                    type="number"
                    min="0"
                    step="0.001"
                    [ngModel]="linea.cantidadInicial"
                    (ngModelChange)="setCantidadLinea(idx, $event)"
                  />
                </td>
                <td class="num">
                  <input
                    type="number"
                    min="0"
                    step="0.001"
                    [ngModel]="linea.precioInicial"
                    (ngModelChange)="setPrecioLinea(idx, $event)"
                  />
                </td>
                <td>
                  <button
                    type="button"
                    class="link danger-text"
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
          <button type="button" class="btn" (click)="agregarLineaIni()">+ Agregar linea</button>
          <span class="spacer"></span>
          <button type="button" class="btn" (click)="cerrarInicializacion()">Cancelar</button>
          <button
            type="button"
            class="btn primary"
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
      h2 {
        margin: 0;
      }
      .sub {
        margin: 0.25rem 0 0;
        color: #4b5563;
      }
      .acciones-top {
        display: flex;
        gap: 0.5rem;
      }
      .btn {
        padding: 0.45rem 0.9rem;
        border-radius: 4px;
        border: 1px solid #166534;
        background: white;
        color: #166534;
        cursor: pointer;
      }
      .btn.primary {
        background: #166534;
        color: white;
      }
      .btn.danger {
        border-color: #b91c1c;
        color: #b91c1c;
      }
      .btn:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .resumen {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 0.75rem;
        margin: 1rem 0;
      }
      .resumen article {
        background: #f9fafb;
        padding: 0.75rem;
        border-radius: 6px;
        border: 1px solid #e5e7eb;
        display: flex;
        flex-direction: column;
      }
      .resumen .label {
        font-size: 0.8rem;
        color: #6b7280;
      }
      .resumen .valor {
        font-size: 1.1rem;
        font-weight: 600;
      }
      .tabla-wrap {
        overflow-x: auto;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        margin-top: 0.5rem;
      }
      th,
      td {
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid #e5e7eb;
        text-align: left;
        white-space: nowrap;
      }
      th.num,
      td.num {
        text-align: right;
      }
      tr.alerta td {
        background: #fff7ed;
      }
      .merma-pos {
        color: #b45309;
      }
      .merma-neg {
        color: #1d4ed8;
      }
      button.link {
        color: #166534;
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
        text-decoration: underline;
        font-size: inherit;
      }
      button.danger-text {
        color: #b91c1c;
      }
      .vacio {
        color: #6b7280;
      }
      .error {
        color: #b91c1c;
      }
      .overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.3);
        z-index: 10;
      }
      .modal {
        position: fixed;
        top: 10vh;
        left: 50%;
        transform: translateX(-50%);
        background: white;
        padding: 1.25rem;
        border-radius: 8px;
        width: min(420px, 92vw);
        z-index: 11;
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
      }
      .modal-ancho {
        width: min(900px, 96vw);
        max-height: 80vh;
        overflow: auto;
      }
      .modal h3 {
        margin: 0 0 0.5rem;
      }
      .modal label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        margin-top: 0.75rem;
        font-size: 0.9rem;
      }
      .modal input {
        padding: 0.4rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
      }
      .mini {
        font-size: 0.85rem;
        color: #6b7280;
      }
      .acciones-modal {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        margin-top: 1rem;
      }
      .acciones-modal .spacer {
        flex: 1;
      }
      table.ini input {
        width: 100%;
        padding: 0.3rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
      }
    `,
  ],
})
export class InventarioComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly inventario = signal<InventarioItem[]>([]);
  readonly inicializado = signal(false);
  readonly materias = signal<MateriaPrima[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly modoEdicion = signal<InventarioItem | null>(null);
  cantidadRealEdit = 0;
  observacionesEdit = '';
  readonly guardandoEdicion = signal(false);

  readonly modoInicializar = signal(false);
  readonly lineasIni = signal<LineaInicializacion[]>([this.lineaVacia()]);
  readonly guardandoIni = signal(false);

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

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargar();
    this.cargarMaterias();
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
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo recalcular el inventario'));
        this.cargando.set(false);
      },
    });
  }

  vaciar(): void {
    if (!confirm('Esto eliminara todo el inventario y la inicializacion. Continuar?')) return;
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
      precioInicial: mp.precioPorKilo > 0 ? mp.precioPorKilo : null,
    }));
    this.lineasIni.set(lineas.length > 0 ? lineas : [this.lineaVacia()]);
    this.modoInicializar.set(true);
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
    let hayLineaUtil = false;
    for (const linea of lineas) {
      if (linea.idMateriaPrima == null) continue;
      if (linea.cantidadInicial == null || linea.cantidadInicial < 0) return false;
      if (linea.precioInicial == null || linea.precioInicial < 0) return false;
      if (linea.cantidadInicial === 0 && linea.precioInicial === 0) continue;
      if (idsVistos.has(linea.idMateriaPrima)) return false;
      idsVistos.add(linea.idMateriaPrima);
      hayLineaUtil = true;
    }
    return hayLineaUtil;
  }

  confirmarInicializacion(): void {
    if (!this.lineasIniValidas()) return;
    const lineasPayload = this.lineasIni().filter(
      (l) =>
        l.idMateriaPrima != null &&
        l.cantidadInicial != null &&
        l.precioInicial != null &&
        l.cantidadInicial >= 0 &&
        l.precioInicial >= 0 &&
        (l.cantidadInicial > 0 || l.precioInicial > 0),
    );
    if (lineasPayload.length === 0) return;
    const payload = {
      lineas: lineasPayload.map((l) => ({
        idMateriaPrima: l.idMateriaPrima!,
        cantidadInicial: l.cantidadInicial!,
        precioInicial: l.precioInicial!,
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
