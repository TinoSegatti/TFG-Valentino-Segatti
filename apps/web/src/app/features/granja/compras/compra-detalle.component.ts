import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { MateriaPrima } from '../../../data/models/materia-prima.model';
import { Proveedor } from '../../../data/models/proveedor.model';
import {
  CompraCompleta,
  dentroToleranciaCompra,
  fingerprintLineasGuardables,
  hoyIso,
  lineaDesdeCompra,
  lineaDetalleVacia,
  LineaDetalleUi,
  recalcularLineaDetalle,
  redondearCompra,
} from '../../../data/models/compra.model';
import { GRANJA_VISTA_STYLES } from '../shared/granja-vista.styles';
import { NumeroFormatoDirective } from '../../../shared/numero-formato.directive';
import { OrdenTabla } from '../../../shared/orden-tabla';
import { anomaliaEsRelevante } from '../../../data/models/anomalia.model';

@Component({
  selector: 'app-compra-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe, NumeroFormatoDirective],
  template: `
    @if (cargando()) {
      <p>Cargando factura…</p>
    } @else if (compra()) {
      <header>
        <a routerLink="../" class="back" (click)="intentarVolver($event)">← Volver al listado</a>
        <h2>Factura {{ compra()!.numeroFactura }}</h2>
      </header>

      <section class="panel">
        <div class="panel-header">
          <h3>Cabecera</h3>
          <div class="panel-acciones">
            @if (!editandoCabecera()) {
              <button
                type="button"
                class="primario"
                [disabled]="editandoDetalle()"
                (click)="editarCabecera()"
              >
                Editar
              </button>
            } @else {
              <button
                type="button"
                class="primario"
                [disabled]="guardando() || !cabeceraFormValida()"
                (click)="guardarCabecera()"
              >
                Guardar
              </button>
              <button type="button" class="secundario" [disabled]="guardando()" (click)="cancelarCabecera()">
                Cancelar
              </button>
            }
          </div>
        </div>

        @if (!editandoCabecera()) {
          <dl class="vista-datos">
            <div>
              <dt>Proveedor</dt>
              <dd>{{ compra()!.nombreProveedor }} ({{ compra()!.codigoProveedor }})</dd>
            </div>
            <div>
              <dt>Nº factura</dt>
              <dd>{{ compra()!.numeroFactura }}</dd>
            </div>
            <div>
              <dt>Fecha</dt>
              <dd>{{ compra()!.fechaCompra }}</dd>
            </div>
            <div>
              <dt>Total factura</dt>
              <dd>$ {{ compra()!.totalFactura | number: '1.2-2' }}</dd>
            </div>
            <div>
              <dt>Estado</dt>
              <dd>{{ compra()!.estado }}</dd>
            </div>
            @if (compra()!.observaciones) {
              <div class="ancho">
                <dt>Observaciones</dt>
                <dd>{{ compra()!.observaciones }}</dd>
              </div>
            }
          </dl>
        } @else {
          @if (lineasConMateria().length > 0) {
            <p class="hint">
              Si el total no coincide con la suma de subtotales, el detalle se abrirá automáticamente para ajustar.
            </p>
          }
          <form class="formulario-cabecera" (ngSubmit)="guardarCabecera()">
            <div class="fila-proveedor">
              <label class="autocomplete">
                Proveedor
                <input
                  name="nombreProveedor"
                  [(ngModel)]="nombreProveedor"
                  (ngModelChange)="onNombreProveedorChange($event)"
                  (focus)="mostrarProveedores.set(true)"
                  (blur)="cerrarProveedores()"
                  autocomplete="off"
                  required
                  placeholder="Escribí para buscar…"
                />
                @if (mostrarProveedores() && proveedoresFiltrados().length) {
                  <ul class="dropdown">
                    @for (p of proveedoresFiltrados(); track p.id) {
                      <li (mousedown)="seleccionarProveedor(p)">{{ p.nombreProveedor }}</li>
                    }
                  </ul>
                }
              </label>
              <label>
                Código de proveedor
                <input
                  name="codigoProveedor"
                  [(ngModel)]="codigoProveedor"
                  (ngModelChange)="onCodigoProveedorChange($event)"
                  maxlength="50"
                  required
                />
              </label>
            </div>
            <label>
              Nº / código de factura
              <input name="numeroFactura" [(ngModel)]="numeroFactura" maxlength="100" required />
            </label>
            <label>
              Fecha
              <input type="date" name="fechaCompra" [(ngModel)]="fechaCompra" [max]="hoy" required />
            </label>
            <label>
              Total factura ($)
              <input
                name="totalFactura"
                [appNumero]="2"
                [(ngModel)]="totalFactura"
                required
              />
            </label>
            <label class="ancho">
              Observaciones
              <input name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
            </label>
          </form>
        }

        @if (conflictoCabecera()) {
          <p class="conflicto">{{ conflictoCabecera() }}</p>
        }
        @if (errorCabecera()) {
          <p class="error">{{ errorCabecera() }}</p>
        }
      </section>

      <section class="panel">
        <div class="panel-header">
          <h3>Detalle (ítems)</h3>
          <div class="panel-acciones">
            @if (!editandoDetalle()) {
              <button
                type="button"
                class="primario"
                [disabled]="editandoCabecera()"
                (click)="editarDetalle()"
              >
                Editar
              </button>
            } @else {
              <button
                type="button"
                class="primario"
                [disabled]="!puedeGuardarDetalle() || guardando()"
                (click)="guardarDetalle()"
              >
                Guardar
              </button>
              <button type="button" class="secundario" [disabled]="guardando()" (click)="cancelarDetalle()">
                Cancelar
              </button>
            }
          </div>
        </div>

        @if (!editandoDetalle()) {
          @if (sinLineas()) {
            <p class="hint">La factura no tiene ítems cargados.</p>
          } @else {
            <table class="tabla-readonly">
              <thead>
                <tr>
                  <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('nombre')" [class.is-desc]="orden.esDesc('nombre')" (click)="orden.alternar('nombre')">Nombre</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('cantidad')" [class.is-desc]="orden.esDesc('cantidad')" (click)="orden.alternar('cantidad')">Cantidad (kg)</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('precio')" [class.is-desc]="orden.esDesc('precio')" (click)="orden.alternar('precio')">Precio/kg</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('subtotal')" [class.is-desc]="orden.esDesc('subtotal')" (click)="orden.alternar('subtotal')">Subtotal</th>
                </tr>
              </thead>
              <tbody>
                @for (linea of lineasOrdenadas(); track $index) {
                  <tr>
                    <td>{{ linea.codigo }}</td>
                    <td>{{ linea.nombre }}</td>
                    <td>{{ linea.cantidadKg | number: '1.2-2' }}</td>
                    <td>$ {{ linea.precioPorKilo | number: '1.2-2' }}</td>
                    <td>$ {{ linea.subtotal | number: '1.2-2' }}</td>
                  </tr>
                }
              </tbody>
            </table>
            <p class="totales-vista">
              Suma subtotales:
              <strong [class.ok]="totalesCuadran()" [class.bad]="!totalesCuadran()">
                $ {{ sumaSubtotales() | number: '1.2-2' }}
              </strong>
              / Total factura: <strong>$ {{ compra()!.totalFactura | number: '1.2-2' }}</strong>
            </p>
          }
        } @else {
          <p class="hint">
            Los cambios en cantidades o precios impactan el inventario y los precios vigentes del catálogo.
            Si la suma no coincide con el total de la cabecera, esta se abrirá automáticamente para ajustar.
          </p>

          @for (linea of lineas(); track i; let i = $index) {
            <div class="linea">
              <div class="mp-autocomplete">
                <label>
                  Código MP
                  <input
                    [(ngModel)]="linea.codigo"
                    [ngModelOptions]="{ standalone: true }"
                    (ngModelChange)="onCodigoMpChange(i, $event)"
                    autocomplete="off"
                  />
                </label>
                <label class="autocomplete">
                  Materia prima
                  <input
                    [(ngModel)]="linea.nombre"
                    [ngModelOptions]="{ standalone: true }"
                    (ngModelChange)="onNombreMpChange(i, $event)"
                    (focus)="abrirMp(i)"
                    (blur)="cerrarMp(i)"
                    autocomplete="off"
                    placeholder="Buscar…"
                  />
                  @if (mpAbiertoIndex() === i && materiasFiltradas(linea.nombre).length) {
                    <ul class="dropdown">
                      @for (mp of materiasFiltradas(linea.nombre); track mp.id) {
                        <li (mousedown)="seleccionarMp(i, mp)">{{ mp.nombreMateriaPrima }}</li>
                      }
                    </ul>
                  }
                </label>
              </div>

              <label>
                Cantidad (kg)
                <input
                  [appNumero]="2"
                  [ngModel]="linea.cantidadKg"
                  [ngModelOptions]="{ standalone: true }"
                  (ngModelChange)="onCampoModelChange(i, 'cantidad', $event)"
                />
              </label>
              <label>
                Precio/kg
                <input
                  [appNumero]="2"
                  [ngModel]="linea.precioPorKilo"
                  [ngModelOptions]="{ standalone: true }"
                  (ngModelChange)="onCampoModelChange(i, 'precio', $event)"
                  (blur)="evaluarAnomaliaLinea(i)"
                />
              </label>
              <label>
                Subtotal
                <input
                  [appNumero]="2"
                  [ngModel]="linea.subtotal"
                  [ngModelOptions]="{ standalone: true }"
                  (ngModelChange)="onCampoModelChange(i, 'subtotal', $event)"
                  (blur)="evaluarAnomaliaLinea(i)"
                />
              </label>
              <button type="button" class="btn-quitar" (click)="quitarLinea(i)">Quitar</button>

              @if (lineaDuplicada(linea)) {
                <p class="warn">
                  Esta materia prima ya está cargada en otro ítem. Unificá las cantidades en una sola
                  línea.
                </p>
              }
              @if (linea.advertenciaLinea) {
                <p class="warn">{{ linea.advertenciaLinea }}</p>
              }

              @if (linea.evaluandoAnomalia) {
                <p class="anomalia-eval">Evaluando precio…</p>
              } @else if (linea.anomalia && esAnomaliaRelevante(linea)) {
                <button
                  type="button"
                  class="anomalia-chip"
                  [class.alta]="linea.anomalia.clasificacion === 'ANOMALIA_ALTA'"
                  [class.atencion]="linea.anomalia.clasificacion === 'ATENCION'"
                  (click)="abrirAnomaliaPopup(i)"
                >
                  @if (linea.anomalia.requiereConfirmacion && linea.precioConfirmado) {
                    ✓ Precio confirmado — ver detalle
                  } @else if (linea.anomalia.requiereConfirmacion) {
                    ⚠ Precio inusual — confirmar
                  } @else {
                    ⚠ Precio a revisar — ver detalle
                  }
                </button>
              }
            </div>
          }

          <button type="button" class="btn-agregar" (click)="agregarLinea()">+ Agregar ítem</button>

          <footer class="totales">
            <p>
              Suma subtotales:
              <strong [class.ok]="totalesCuadran()" [class.bad]="!totalesCuadran()">
                $ {{ sumaSubtotales() | number: '1.2-2' }}
              </strong>
              / Total factura: <strong>$ {{ compra()!.totalFactura | number: '1.2-2' }}</strong>
            </p>
            @if (hayMateriasDuplicadas()) {
              <p class="warn">
                Hay materias primas repetidas en el detalle. Cada materia prima debe figurar en un
                solo ítem.
              </p>
            }
            @if (!totalesCuadran() && lineasValidas()) {
              <p class="warn">Los totales aún no coinciden (tolerancia ± $0,50).</p>
            }
            @if (hayAnomaliaSinConfirmar()) {
              <p class="warn">
                Hay precios marcados como inusualmente altos. Confirmá cada uno ("Confirmo que el
                precio es correcto") para poder guardar.
              </p>
            }
          </footer>
        }

        @if (conflictoDetalle()) {
          <p class="conflicto">{{ conflictoDetalle() }}</p>
        }
        @if (errorDetalle()) {
          <p class="error">{{ errorDetalle() }}</p>
        }
      </section>
    }

    <!-- Popup: alerta de anomalía de precio (RF-IA-ANOM-002) -->
    @if (anomaliaPopup(); as pop) {
      <div class="overlay" (click)="cerrarAnomaliaPopup()"></div>
      <div
        class="modal glass-card-strong anomalia-modal"
        [class.alta]="pop.anomalia.clasificacion === 'ANOMALIA_ALTA'"
        [class.atencion]="pop.anomalia.clasificacion === 'ATENCION'"
        role="alertdialog"
        aria-modal="true"
      >
        <h3>
          @if (pop.anomalia.clasificacion === 'ANOMALIA_ALTA') {
            Alerta de precio
          } @else {
            Atención con el precio
          }
        </h3>
        <p class="anomalia-msg">{{ pop.anomalia.mensaje }}</p>
        @if (pop.anomalia.requiereConfirmacion) {
          <label class="confirmar">
            <input
              type="checkbox"
              [ngModel]="pop.precioConfirmado"
              [ngModelOptions]="{ standalone: true }"
              (ngModelChange)="confirmarPrecioLinea(pop.index, $event)"
            />
            Confirmo que el precio es correcto
          </label>
          <div class="acciones-modal">
            <button type="button" class="reforma-btn-ghost" (click)="revisarPrecioAnomalia(pop.index)">
              Revisar precio
            </button>
            <button
              type="button"
              class="reforma-btn"
              [disabled]="!pop.precioConfirmado"
              (click)="cerrarAnomaliaPopup()"
            >
              Confirmar y continuar
            </button>
          </div>
        } @else {
          <div class="acciones-modal">
            <button type="button" class="reforma-btn" (click)="cerrarAnomaliaPopup()">Entendido</button>
          </div>
        }
      </div>
    }
  `,
  styles: [
    GRANJA_VISTA_STYLES,
    `
      :host {
        display: block;
      }
      dl.vista-datos .ancho {
        grid-column: 1 / -1;
      }
      .formulario-cabecera {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
        gap: 1rem;
        align-items: end;
      }
      .fila-proveedor {
        grid-column: 1 / -1;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
        gap: 1rem;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        font-size: 0.85rem;
        color: var(--reforma-text-dim);
      }
      label > span,
      label {
        color: var(--reforma-text-dim);
      }
      label.ancho {
        grid-column: 1 / -1;
      }
      label.autocomplete {
        position: relative;
      }
      input,
      input[type='number'],
      input[type='date'] {
        width: 100%;
        box-sizing: border-box;
        padding: 0.6rem 0.75rem;
        color: var(--reforma-text);
        background: var(--glass-bg-strong);
        border: 1px solid var(--glass-border-strong);
        border-radius: 10px;
        outline: none;
        transition: border-color 0.15s ease, box-shadow 0.15s ease;
      }
      input::placeholder {
        color: var(--reforma-text-faint);
      }
      input:focus {
        border-color: var(--reforma-accent);
        box-shadow: 0 0 0 3px var(--reforma-accent-soft);
      }
      .dropdown {
        position: absolute;
        z-index: 30;
        top: 100%;
        left: 0;
        right: 0;
        margin: 0.25rem 0 0;
        padding: 0.25rem;
        list-style: none;
        background: var(--opaque-surface);
        border: 1px solid var(--glass-border-strong);
        border-radius: 10px;
        max-height: 240px;
        overflow-y: auto;
        box-shadow: var(--glass-shadow);
        color: var(--reforma-text);
      }
      .dropdown li {
        padding: 0.5rem 0.7rem;
        border-radius: 8px;
        cursor: pointer;
        color: var(--reforma-text);
      }
      .dropdown li:hover {
        background: var(--reforma-accent-soft);
        color: #ede9fe;
      }
      .tabla-readonly {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.9rem;
      }
      .tabla-readonly th,
      .tabla-readonly td {
        padding: 0.6rem 0.75rem;
        text-align: left;
        border-bottom: 1px solid var(--glass-border);
        color: var(--reforma-text);
      }
      .tabla-readonly th {
        font-size: 0.78rem;
        color: var(--reforma-text-dim);
        font-weight: 600;
        background: rgba(255, 255, 255, 0.04);
      }
      .totales-vista {
        margin-top: 0.75rem;
        font-size: 0.9rem;
        color: var(--reforma-text-dim);
      }
      .totales-vista strong {
        color: var(--reforma-text);
      }
      .linea {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
        gap: 0.75rem;
        align-items: end;
        padding: 1rem 0;
        border-bottom: 1px solid var(--glass-border);
      }
      .mp-autocomplete {
        grid-column: span 2;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
        gap: 0.75rem;
      }
      .btn-agregar,
      .btn-quitar {
        padding: 0.5rem 0.9rem;
        border-radius: 8px;
        cursor: pointer;
        font: inherit;
        font-size: 0.85rem;
        font-weight: 600;
        transition: filter 0.12s ease, background 0.12s ease, border-color 0.12s ease;
      }
      .btn-agregar {
        color: var(--reforma-text);
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        margin-top: 1rem;
      }
      .btn-agregar:hover:not(:disabled) {
        background: var(--glass-bg-hover);
        border-color: var(--glass-border-strong);
      }
      .btn-quitar {
        color: #fecaca;
        background: rgba(248, 113, 113, 0.12);
        border: 1px solid rgba(248, 113, 113, 0.35);
      }
      .btn-quitar:hover:not(:disabled) {
        background: rgba(248, 113, 113, 0.2);
        border-color: rgba(248, 113, 113, 0.55);
      }
      .btn-agregar:disabled,
      .btn-quitar:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .totales {
        margin-top: 1rem;
        padding-top: 1rem;
        border-top: 1px solid var(--glass-border);
        color: var(--reforma-text-dim);
      }
      .totales strong,
      .totales-vista strong {
        color: var(--reforma-text);
      }
      .ok {
        color: #bbf7d0;
      }
      .bad {
        color: #fecaca;
      }
      .warn {
        color: #fde68a;
        font-size: 0.85rem;
        width: 100%;
      }
      .anomalia-eval {
        width: 100%;
        font-size: 0.8rem;
        color: var(--reforma-text-faint);
      }
      .anomalia-chip {
        width: 100%;
        margin-top: 0.25rem;
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.45rem 0.75rem;
        border-radius: 999px;
        font-size: 0.82rem;
        font-weight: 600;
        text-align: left;
        cursor: pointer;
        background: transparent;
        border: 1px solid transparent;
        transition: filter 0.15s ease;
      }
      .anomalia-chip:hover {
        filter: brightness(1.15);
      }
      .anomalia-chip.atencion {
        color: #fde68a;
        background: rgba(251, 191, 36, 0.1);
        border-color: rgba(251, 191, 36, 0.35);
      }
      .anomalia-chip.alta {
        color: #fecaca;
        background: rgba(248, 113, 113, 0.14);
        border-color: rgba(248, 113, 113, 0.5);
      }
      /* Popup de anomalía */
      .overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.55);
        backdrop-filter: blur(2px);
        z-index: 40;
      }
      .modal {
        position: fixed;
        top: 18vh;
        left: 50%;
        transform: translateX(-50%);
        padding: 1.5rem;
        width: min(460px, 92vw);
        z-index: 41;
      }
      .anomalia-modal {
        border-top: 3px solid transparent;
      }
      .anomalia-modal.atencion {
        border-top-color: #fbbf24;
      }
      .anomalia-modal.alta {
        border-top-color: #f87171;
      }
      .anomalia-modal h3 {
        margin: 0 0 0.5rem;
        color: var(--reforma-text);
      }
      .anomalia-msg {
        margin: 0;
        font-size: 0.95rem;
        line-height: 1.5;
        color: var(--reforma-text);
      }
      .anomalia-modal .confirmar {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 0.5rem;
        margin-top: 1rem;
        font-size: 0.9rem;
        color: var(--reforma-text);
        cursor: pointer;
      }
      .anomalia-modal .confirmar input {
        width: auto;
      }
      .acciones-modal {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        align-items: center;
        margin-top: 1.25rem;
      }
    `,
  ],
})
export class CompraDetalleComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly compra = signal<CompraCompleta | null>(null);
  readonly lineas = signal<LineaDetalleUi[]>([]);
  readonly orden = new OrdenTabla();
  readonly lineasOrdenadas = computed(() =>
    this.orden.ordenar(this.lineas(), {
      codigo: (l) => l.codigo,
      nombre: (l) => l.nombre,
      cantidad: (l) => l.cantidadKg,
      precio: (l) => l.precioPorKilo,
      subtotal: (l) => l.subtotal,
    }),
  );
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly proveedores = signal<Proveedor[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly errorCabecera = signal<string | null>(null);
  readonly errorDetalle = signal<string | null>(null);
  readonly conflictoCabecera = signal<string | null>(null);
  readonly conflictoDetalle = signal<string | null>(null);
  readonly mpAbiertoIndex = signal<number | null>(null);
  readonly mostrarProveedores = signal(false);
  readonly idProveedorSeleccionado = signal<number | null>(null);
  readonly editandoCabecera = signal(false);
  readonly editandoDetalle = signal(false);
  /** Índice de la línea cuya anomalía se muestra en el popup; null = popup cerrado. */
  readonly anomaliaPopupIndex = signal<number | null>(null);

  readonly hoy = hoyIso();

  nombreProveedor = '';
  codigoProveedor = '';
  numeroFactura = '';
  fechaCompra = hoyIso();
  totalFactura: number | null = null;
  observaciones = '';

  private lineasSnapshot = '';
  private sincronizandoMp = false;
  private sincronizandoProveedor = false;

  readonly sinLineas = computed(() => this.lineas().length === 0);

  readonly lineasConMateria = computed(() =>
    this.lineas().filter((l) => l.idMateriaPrima != null),
  );

  readonly sumaSubtotales = computed(() =>
    redondearCompra(
      this.lineas().reduce((acc, l) => acc + (l.subtotal ?? 0), 0),
    ),
  );

  readonly totalesCuadran = computed(() => {
    const c = this.compra();
    if (!c) return false;
    return dentroToleranciaCompra(c.totalFactura, this.sumaSubtotales());
  });

  readonly lineasValidas = computed(() => {
    const lineas = this.lineasConMateria();
    return (
      lineas.length > 0 &&
      lineas.every(
        (l) =>
          l.cantidadKg != null &&
          l.cantidadKg > 0 &&
          l.precioPorKilo != null &&
          l.subtotal != null,
      )
    );
  });

  /** Ids de materias primas que aparecen en más de un ítem del detalle. */
  readonly idsMateriaDuplicados = computed(() => {
    const conteo = new Map<number, number>();
    for (const linea of this.lineas()) {
      if (linea.idMateriaPrima == null) continue;
      conteo.set(linea.idMateriaPrima, (conteo.get(linea.idMateriaPrima) ?? 0) + 1);
    }
    const duplicados = new Set<number>();
    for (const [id, veces] of conteo) {
      if (veces > 1) duplicados.add(id);
    }
    return duplicados;
  });

  readonly hayMateriasDuplicadas = computed(() => this.idsMateriaDuplicados().size > 0);

  /** Hay alguna línea con anomalía ALTA aún sin confirmar (RF-IA-ANOM-002): bloquea el guardado. */
  readonly hayAnomaliaSinConfirmar = computed(() =>
    this.lineasConMateria().some(
      (l) => l.anomalia?.requiereConfirmacion && !l.precioConfirmado,
    ),
  );

  /**
   * Datos de la anomalía a mostrar en el popup (reactivo a la línea). Devuelve null si el popup
   * está cerrado o si la línea dejó de tener una anomalía relevante (p. ej. cambió el precio).
   */
  readonly anomaliaPopup = computed(() => {
    const idx = this.anomaliaPopupIndex();
    if (idx == null) return null;
    const linea = this.lineas()[idx];
    if (!linea?.anomalia || !anomaliaEsRelevante(linea.anomalia.clasificacion)) return null;
    return { index: idx, anomalia: linea.anomalia, precioConfirmado: linea.precioConfirmado };
  });

  readonly puedeGuardarDetalle = computed(() => {
    if (!this.lineasValidas()) return false;
    if (this.hayMateriasDuplicadas()) return false;
    if (this.hayAnomaliaSinConfirmar()) return false;
    return !this.lineasConMateria().some((l) =>
      l.advertenciaLinea?.includes('fuera de tolerancia'),
    );
  });

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  private get idCompra(): string {
    return this.route.snapshot.paramMap.get('idCompra') ?? '';
  }

  ngOnInit(): void {
    this.api.getProveedores(this.idGranja).subscribe({
      next: (list) => {
        this.proveedores.set(list);
        this.cargarCompra();
      },
      error: (err: HttpErrorResponse) => {
        this.errorCabecera.set(mensajeErrorHttp(err, 'No se pudieron cargar los proveedores'));
        this.cargando.set(false);
      },
    });
    this.api.getMateriasPrimas(this.idGranja).subscribe({
      next: (list) => this.materiasPrimas.set(list),
    });
  }

  cargarCompra(postCarga?: () => void): void {
    this.cargando.set(true);
    this.api.getCompra(this.idGranja, this.idCompra).subscribe({
      next: (c) => {
        this.compra.set(c);
        this.restaurarLineasDesdeCompra(c);
        this.establecerSnapshotLineas();
        this.editandoCabecera.set(false);
        this.editandoDetalle.set(false);
        this.cargando.set(false);
        postCarga?.();
      },
      error: (err: HttpErrorResponse) => {
        this.errorCabecera.set(mensajeErrorHttp(err, 'No se pudo cargar la compra'));
        this.cargando.set(false);
      },
    });
  }

  editarCabecera(): void {
    if (this.editandoDetalle()) {
      this.cancelarDetalle();
    }
    this.cargarCabeceraEnFormulario();
    this.errorCabecera.set(null);
    this.conflictoCabecera.set(null);
    this.editandoCabecera.set(true);
  }

  cancelarCabecera(): void {
    this.cargarCabeceraEnFormulario();
    this.errorCabecera.set(null);
    this.conflictoCabecera.set(null);
    this.editandoCabecera.set(false);
  }

  cabeceraFormValida(): boolean {
    return (
      this.idProveedorSeleccionado() != null &&
      this.numeroFactura.trim().length > 0 &&
      this.fechaCompra.trim().length > 0 &&
      this.totalFactura != null &&
      this.totalFactura > 0
    );
  }

  guardarCabecera(): void {
    if (!this.cabeceraFormValida()) return;

    const total = this.totalFactura!;
    const hayLineas = this.lineasConMateria().length > 0;
    const deberiaAjustarDetalle = hayLineas && !dentroToleranciaCompra(total, this.sumaSubtotales());

    this.conflictoCabecera.set(null);
    this.guardando.set(true);
    this.errorCabecera.set(null);
    this.api
      .actualizarCompraCabecera(this.idGranja, this.idCompra, {
        idProveedor: this.idProveedorSeleccionado()!,
        numeroFactura: this.numeroFactura.trim(),
        fechaCompra: this.fechaCompra,
        totalFactura: total,
        observaciones: this.observaciones.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.cargarCompra(deberiaAjustarDetalle ? () => this.editarDetalle() : undefined);
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.errorCabecera.set(mensajeErrorHttp(err, 'No se pudo actualizar la cabecera'));
        },
      });
  }

  editarDetalle(): void {
    if (this.editandoCabecera()) {
      this.cancelarCabecera();
    }
    const c = this.compra();
    if (c && this.lineas().length === 0 && c.estado === 'BORRADOR') {
      this.lineas.set([lineaDetalleVacia()]);
    }
    this.errorDetalle.set(null);
    this.conflictoDetalle.set(null);
    this.editandoDetalle.set(true);
  }

  cancelarDetalle(): void {
    const c = this.compra();
    if (c) {
      this.restaurarLineasDesdeCompra(c);
    }
    this.establecerSnapshotLineas();
    this.errorDetalle.set(null);
    this.conflictoDetalle.set(null);
    this.editandoDetalle.set(false);
  }

  guardarDetalle(): void {
    if (!this.puedeGuardarDetalle()) return;

    if (this.hayMateriasDuplicadas()) {
      this.conflictoDetalle.set(
        'Hay materias primas repetidas en el detalle. Cada materia prima debe figurar en un solo ítem; unificá las cantidades en una sola línea.',
      );
      return;
    }

    const deberiaAjustarCabecera = !this.totalesCuadran();

    this.conflictoDetalle.set(null);
    const body = {
      lineas: this.lineasConMateria().map((l) => ({
        idMateriaPrima: l.idMateriaPrima!,
        cantidadKg: l.cantidadKg!,
        precioPorKilo: l.precioPorKilo!,
        subtotal: l.subtotal!,
        confirmoPrecio: l.anomalia?.requiereConfirmacion ? l.precioConfirmado : undefined,
      })),
    };
    this.guardando.set(true);
    this.errorDetalle.set(null);
    this.api.guardarCompraDetalle(this.idGranja, this.idCompra, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cargarCompra(deberiaAjustarCabecera ? () => this.editarCabecera() : undefined);
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorDetalle.set(mensajeErrorHttp(err, 'No se pudo guardar el detalle'));
      },
    });
  }

  proveedoresFiltrados(): Proveedor[] {
    const term = this.nombreProveedor.trim().toLowerCase();
    if (!term) return this.proveedores();
    return this.proveedores().filter((p) => p.nombreProveedor.toLowerCase().includes(term));
  }

  onNombreProveedorChange(valor: string): void {
    if (this.sincronizandoProveedor) return;
    this.mostrarProveedores.set(true);
    const exacto = this.proveedores().find(
      (p) => p.nombreProveedor.toLowerCase() === valor.trim().toLowerCase(),
    );
    if (exacto) {
      this.aplicarProveedor(exacto);
      return;
    }
    this.idProveedorSeleccionado.set(null);
  }

  onCodigoProveedorChange(valor: string): void {
    if (this.sincronizandoProveedor) return;
    const exacto = this.proveedores().find(
      (p) => p.codigoProveedor.toLowerCase() === valor.trim().toLowerCase(),
    );
    if (exacto) {
      this.aplicarProveedor(exacto);
    } else {
      this.idProveedorSeleccionado.set(null);
    }
  }

  seleccionarProveedor(p: Proveedor): void {
    this.aplicarProveedor(p);
    this.mostrarProveedores.set(false);
  }

  cerrarProveedores(): void {
    setTimeout(() => this.mostrarProveedores.set(false), 150);
  }

  materiasFiltradas(termino: string): MateriaPrima[] {
    const t = termino.trim().toLowerCase();
    if (!t) return this.materiasPrimas();
    return this.materiasPrimas().filter(
      (m) =>
        m.nombreMateriaPrima.toLowerCase().includes(t) ||
        m.codigoMateriaPrima.toLowerCase().includes(t),
    );
  }

  abrirMp(index: number): void {
    this.mpAbiertoIndex.set(index);
  }

  cerrarMp(index: number): void {
    setTimeout(() => {
      if (this.mpAbiertoIndex() === index) {
        this.mpAbiertoIndex.set(null);
      }
    }, 150);
  }

  onCodigoMpChange(index: number, codigo: string): void {
    if (this.sincronizandoMp) return;
    const lineas = this.lineas().map((l) => ({ ...l }));
    const mp = this.materiasPrimas().find(
      (m) => m.codigoMateriaPrima.toLowerCase() === codigo.trim().toLowerCase(),
    );
    if (mp) {
      this.aplicarMp(lineas, index, mp);
    } else {
      lineas[index].idMateriaPrima = null;
    }
    this.lineas.set(lineas);
  }

  onNombreMpChange(index: number, nombre: string): void {
    if (this.sincronizandoMp) return;
    this.mpAbiertoIndex.set(index);
    const exacto = this.materiasPrimas().find(
      (m) => m.nombreMateriaPrima.toLowerCase() === nombre.trim().toLowerCase(),
    );
    const lineas = this.lineas().map((l) => ({ ...l }));
    if (exacto) {
      this.aplicarMp(lineas, index, exacto);
    } else {
      lineas[index].idMateriaPrima = null;
    }
    this.lineas.set(lineas);
  }

  seleccionarMp(index: number, mp: MateriaPrima): void {
    const lineas = this.lineas().map((l) => ({ ...l }));
    this.aplicarMp(lineas, index, mp);
    this.lineas.set(lineas);
    this.mpAbiertoIndex.set(null);
  }

  onCampoModelChange(
    index: number,
    campo: 'cantidad' | 'precio' | 'subtotal',
    valor: number | null,
  ): void {
    this.actualizarCampoLinea(index, campo, valor);
  }

  /** Evalúa el precio de la línea contra el historial al salir del campo (RF-IA-ANOM-001). */
  evaluarAnomaliaLinea(index: number): void {
    const linea = this.lineas()[index];
    if (!linea || linea.idMateriaPrima == null || linea.precioPorKilo == null || linea.precioPorKilo <= 0) {
      return;
    }
    const idMateriaPrima = linea.idMateriaPrima;
    const precio = linea.precioPorKilo;
    this.patchLinea(index, { evaluandoAnomalia: true });
    this.api
      .evaluarAnomalia(this.idGranja, { idMateriaPrima, precio, mesReferencia: this.mesDeCompra() })
      .subscribe({
        next: (a) => {
          this.patchLinea(index, {
            anomalia: a,
            evaluandoAnomalia: false,
            precioConfirmado: false,
          });
          // Si el precio es inusual, se avisa con un popup (RF-IA-ANOM-002).
          if (anomaliaEsRelevante(a.clasificacion)) {
            this.anomaliaPopupIndex.set(index);
          }
        },
        // Fail-open: si no se pudo evaluar, no se molesta al usuario ni se bloquea el guardado.
        error: () => this.patchLinea(index, { evaluandoAnomalia: false, anomalia: null }),
      });
  }

  confirmarPrecioLinea(index: number, confirmado: boolean): void {
    this.patchLinea(index, { precioConfirmado: confirmado });
  }

  /** Reabre el popup de anomalía de una línea (desde el chip de aviso). */
  abrirAnomaliaPopup(index: number): void {
    this.anomaliaPopupIndex.set(index);
  }

  cerrarAnomaliaPopup(): void {
    this.anomaliaPopupIndex.set(null);
  }

  /** El usuario quiere corregir el precio: deja la línea sin confirmar y cierra el popup. */
  revisarPrecioAnomalia(index: number): void {
    this.confirmarPrecioLinea(index, false);
    this.cerrarAnomaliaPopup();
  }

  esAnomaliaRelevante(linea: LineaDetalleUi): boolean {
    return linea.anomalia != null && anomaliaEsRelevante(linea.anomalia.clasificacion);
  }

  private patchLinea(index: number, cambios: Partial<LineaDetalleUi>): void {
    this.lineas.update((prev) => prev.map((l, i) => (i === index ? { ...l, ...cambios } : l)));
  }

  /** Mes (1-12) de la factura para la comparación estacional; undefined si no hay fecha válida. */
  private mesDeCompra(): number | undefined {
    const iso = this.compra()?.fechaCompra ?? '';
    const mes = Number(iso.slice(5, 7));
    return Number.isFinite(mes) && mes >= 1 && mes <= 12 ? mes : undefined;
  }

  lineaDuplicada(linea: LineaDetalleUi): boolean {
    return linea.idMateriaPrima != null && this.idsMateriaDuplicados().has(linea.idMateriaPrima);
  }

  agregarLinea(): void {
    this.lineas.update((prev) => [...prev, lineaDetalleVacia()]);
  }

  quitarLinea(index: number): void {
    this.lineas.update((prev) => prev.filter((_, i) => i !== index));
  }

  intentarVolver(event: Event): void {
    if (!this.puedeSalir()) {
      event.preventDefault();
    }
  }

  puedeSalir(): boolean {
    if (this.editandoCabecera()) return false;
    if (this.editandoDetalle() && this.hayCambiosSinGuardar()) return false;
    return true;
  }

  hayCambiosSinGuardar(): boolean {
    if (!this.editandoDetalle()) return false;
    return fingerprintLineasGuardables(this.lineas()) !== this.lineasSnapshot;
  }

  mensajeBloqueoSalida(): string {
    if (this.editandoCabecera()) {
      return 'Estás editando la cabecera. Guardá o cancelá los cambios antes de salir.';
    }
    if (this.editandoDetalle() && this.hayCambiosSinGuardar()) {
      return 'Tenés cambios sin guardar en el detalle. Guardá o cancelá antes de salir.';
    }
    return 'No podés salir todavía. Revisá la factura e intentá de nuevo.';
  }

  private campoLineaKey(campo: 'cantidad' | 'precio' | 'subtotal') {
    return campo === 'cantidad'
      ? 'cantidadKg'
      : campo === 'precio'
        ? 'precioPorKilo'
        : 'subtotal';
  }

  private actualizarCampoLinea(
    index: number,
    campo: 'cantidad' | 'precio' | 'subtotal',
    valor: number | null,
  ): void {
    const lineas = this.lineas().map((l) => ({ ...l }));
    const linea = lineas[index];
    if (!linea) return;
    const campoLinea = this.campoLineaKey(campo);

    if (valor == null || Number.isNaN(valor)) {
      linea[campoLinea] = null;
    } else {
      linea[campoLinea] = redondearCompra(valor);
    }

    linea.ultimoCampoEditado = campo;
    recalcularLineaDetalle(linea);
    if (campo === 'precio' || campo === 'subtotal') {
      // El precio cambió: la evaluación anterior dejó de ser válida (se re-evalúa on-blur).
      linea.anomalia = null;
      linea.precioConfirmado = false;
    }
    this.lineas.set(lineas);
  }

  private establecerSnapshotLineas(): void {
    this.lineasSnapshot = fingerprintLineasGuardables(this.lineas());
  }

  private restaurarLineasDesdeCompra(c: CompraCompleta): void {
    if (c.lineas.length > 0) {
      const lineas = c.lineas.map(lineaDesdeCompra);
      for (const linea of lineas) {
        recalcularLineaDetalle(linea);
      }
      this.lineas.set(lineas);
    } else {
      this.lineas.set([]);
    }
  }

  private cargarCabeceraEnFormulario(): void {
    const c = this.compra();
    if (!c) return;
    this.numeroFactura = c.numeroFactura;
    this.fechaCompra = c.fechaCompra;
    this.totalFactura = c.totalFactura;
    this.observaciones = c.observaciones ?? '';
    const prov = this.proveedores().find((p) => p.id === c.idProveedor);
    if (prov) {
      this.aplicarProveedor(prov);
    } else {
      this.idProveedorSeleccionado.set(c.idProveedor);
      this.nombreProveedor = c.nombreProveedor;
      this.codigoProveedor = c.codigoProveedor;
    }
  }

  private aplicarProveedor(p: Proveedor): void {
    this.sincronizandoProveedor = true;
    this.idProveedorSeleccionado.set(p.id);
    this.nombreProveedor = p.nombreProveedor;
    this.codigoProveedor = p.codigoProveedor;
    this.sincronizandoProveedor = false;
  }

  private aplicarMp(lineas: LineaDetalleUi[], index: number, mp: MateriaPrima): void {
    this.sincronizandoMp = true;
    const linea = { ...lineas[index] };
    linea.idMateriaPrima = mp.id;
    linea.codigo = mp.codigoMateriaPrima;
    linea.nombre = mp.nombreMateriaPrima;
    linea.ultimoPrecioCatalogo = mp.precioPorKilo;
    // La materia cambió: la evaluación anterior ya no aplica.
    linea.anomalia = null;
    linea.precioConfirmado = false;
    if (linea.precioPorKilo == null) {
      linea.precioPorKilo = redondearCompra(mp.precioPorKilo);
      linea.ultimoCampoEditado = 'precio';
    }
    recalcularLineaDetalle(linea);
    lineas[index] = linea;
    this.sincronizandoMp = false;
  }
}
