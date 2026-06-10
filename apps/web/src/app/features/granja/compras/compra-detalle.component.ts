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

@Component({
  selector: 'app-compra-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe],
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
              <dd>$ {{ compra()!.totalFactura | number: '1.3-3' }}</dd>
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
              Si modificás el total, deberás ajustar el detalle para que la suma de subtotales coincida.
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
                type="number"
                name="totalFactura"
                [(ngModel)]="totalFactura"
                step="0.001"
                min="0.001"
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
                  <th>Código</th>
                  <th>Nombre</th>
                  <th>Cantidad (kg)</th>
                  <th>Precio/kg</th>
                  <th>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                @for (linea of lineas(); track $index) {
                  <tr>
                    <td>{{ linea.codigo }}</td>
                    <td>{{ linea.nombre }}</td>
                    <td>{{ linea.cantidadKg | number: '1.3-3' }}</td>
                    <td>$ {{ linea.precioPorKilo | number: '1.3-3' }}</td>
                    <td>$ {{ linea.subtotal | number: '1.3-3' }}</td>
                  </tr>
                }
              </tbody>
            </table>
            <p class="totales-vista">
              Suma subtotales:
              <strong [class.ok]="totalesCuadran()" [class.bad]="!totalesCuadran()">
                $ {{ sumaSubtotales() | number: '1.3-3' }}
              </strong>
              / Total factura: <strong>$ {{ compra()!.totalFactura | number: '1.3-3' }}</strong>
            </p>
          }
        } @else {
          <p class="hint">
            Los cambios en cantidades o precios impactan el inventario y los precios vigentes del catálogo.
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
                  type="number"
                  step="0.001"
                  [value]="valorInput(linea.cantidadKg)"
                  (input)="onCampoInput($event, i, 'cantidad')"
                  (blur)="onCampoBlur(i, 'cantidad')"
                />
              </label>
              <label>
                Precio/kg
                <input
                  type="number"
                  step="0.001"
                  [value]="valorInput(linea.precioPorKilo)"
                  (input)="onCampoInput($event, i, 'precio')"
                  (blur)="onCampoBlur(i, 'precio')"
                />
              </label>
              <label>
                Subtotal
                <input
                  type="number"
                  step="0.001"
                  [value]="valorInput(linea.subtotal)"
                  (input)="onCampoInput($event, i, 'subtotal')"
                  (blur)="onCampoBlur(i, 'subtotal')"
                />
              </label>
              <button type="button" class="btn-quitar" (click)="quitarLinea(i)">Quitar</button>

              @if (linea.advertenciaLinea) {
                <p class="warn">{{ linea.advertenciaLinea }}</p>
              }
            </div>
          }

          <button type="button" class="btn-agregar" (click)="agregarLinea()">+ Agregar ítem</button>

          <footer class="totales">
            <p>
              Suma subtotales:
              <strong [class.ok]="totalesCuadran()" [class.bad]="!totalesCuadran()">
                $ {{ sumaSubtotales() | number: '1.3-3' }}
              </strong>
              / Total factura: <strong>$ {{ compra()!.totalFactura | number: '1.3-3' }}</strong>
            </p>
            @if (!totalesCuadran() && lineasValidas()) {
              <p class="warn">Los totales aún no coinciden (tolerancia ± $0,50).</p>
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
  `,
  styles: [
    `
      :host {
        display: block;
      }
      dl.vista-datos .ancho {
        grid-column: 1 / -1;
      }
      .formulario-cabecera {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        align-items: flex-end;
      }
      .fila-proveedor {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        width: 100%;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.85rem;
      }
      label.ancho {
        flex: 1;
        min-width: 240px;
      }
      label.autocomplete {
        position: relative;
        min-width: 220px;
      }
      input {
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
        min-width: 100px;
      }
      .dropdown {
        position: absolute;
        z-index: 10;
        top: 100%;
        left: 0;
        right: 0;
        margin: 0;
        padding: 0;
        list-style: none;
        background: white;
        border: 1px solid #d1d5db;
        border-radius: 4px;
        max-height: 180px;
        overflow-y: auto;
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
      }
      .dropdown li {
        padding: 0.4rem 0.55rem;
        cursor: pointer;
      }
      .dropdown li:hover {
        background: #ecfdf5;
      }
      .tabla-readonly {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.9rem;
      }
      .tabla-readonly th,
      .tabla-readonly td {
        padding: 0.5rem 0.75rem;
        text-align: left;
        border-bottom: 1px solid #e5e7eb;
      }
      .tabla-readonly th {
        font-size: 0.75rem;
        color: #6b7280;
        font-weight: 600;
      }
      .totales-vista {
        margin-top: 0.75rem;
        font-size: 0.9rem;
      }
      .linea {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        align-items: flex-end;
        padding: 1rem 0;
        border-bottom: 1px solid #e5e7eb;
      }
      .mp-autocomplete {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
      }
      .btn-agregar,
      .btn-quitar {
        padding: 0.45rem 0.9rem;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        font-size: 0.85rem;
      }
      .btn-agregar {
        background: #374151;
        color: white;
        margin-top: 1rem;
      }
      .btn-quitar {
        background: #b91c1c;
        color: white;
      }
      .btn-agregar:disabled,
      .btn-quitar:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .totales {
        margin-top: 1rem;
        padding-top: 1rem;
        border-top: 1px solid #e5e7eb;
      }
      .ok {
        color: #166534;
      }
      .bad {
        color: #b91c1c;
      }
      .warn {
        color: #b45309;
        font-size: 0.85rem;
        width: 100%;
      }
    `,
    GRANJA_VISTA_STYLES,
  ],
})
export class CompraDetalleComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly compra = signal<CompraCompleta | null>(null);
  readonly lineas = signal<LineaDetalleUi[]>([]);
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

  readonly puedeGuardarDetalle = computed(() => {
    if (!this.lineasValidas()) return false;
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

  cargarCompra(): void {
    this.cargando.set(true);
    this.api.getCompra(this.idGranja, this.idCompra).subscribe({
      next: (c) => {
        this.compra.set(c);
        this.restaurarLineasDesdeCompra(c);
        this.establecerSnapshotLineas();
        this.editandoCabecera.set(false);
        this.editandoDetalle.set(false);
        this.cargando.set(false);
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
    if (hayLineas && !dentroToleranciaCompra(total, this.sumaSubtotales())) {
      this.conflictoCabecera.set(
        'El total de la cabecera no coincide con la suma del detalle. Editá la sección Detalle para ajustar los ítems.',
      );
      return;
    }

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
          this.editandoCabecera.set(false);
          this.cargarCompra();
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

    if (!this.totalesCuadran()) {
      this.conflictoDetalle.set(
        'La suma de subtotales no coincide con el total de la cabecera. Editá la sección Cabecera para ajustar el total, o modificá los ítems del detalle.',
      );
      return;
    }

    this.conflictoDetalle.set(null);
    const body = {
      lineas: this.lineasConMateria().map((l) => ({
        idMateriaPrima: l.idMateriaPrima!,
        cantidadKg: l.cantidadKg!,
        precioPorKilo: l.precioPorKilo!,
        subtotal: l.subtotal!,
      })),
    };
    this.guardando.set(true);
    this.errorDetalle.set(null);
    this.api.guardarCompraDetalle(this.idGranja, this.idCompra, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.editandoDetalle.set(false);
        this.cargarCompra();
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

  onCampoInput(
    event: Event,
    index: number,
    campo: 'cantidad' | 'precio' | 'subtotal',
  ): void {
    const raw = (event.target as HTMLInputElement).value;
    const valor = raw.trim() === '' ? null : Number(raw);
    this.actualizarCampoLinea(index, campo, valor);
  }

  onCampoBlur(index: number, campo: 'cantidad' | 'precio' | 'subtotal'): void {
    const linea = this.lineas()[index];
    if (!linea) return;
    this.actualizarCampoLinea(index, campo, linea[this.campoLineaKey(campo)]);
  }

  valorInput(valor: number | null): string | number {
    return valor ?? '';
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
    if (linea.precioPorKilo == null) {
      linea.precioPorKilo = redondearCompra(mp.precioPorKilo);
      linea.ultimoCampoEditado = 'precio';
    }
    recalcularLineaDetalle(linea);
    lineas[index] = linea;
    this.sincronizandoMp = false;
  }
}
