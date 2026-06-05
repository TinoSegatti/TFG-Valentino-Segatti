import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { MateriaPrima } from '../../../data/models/materia-prima.model';
import {
  CompraCompleta,
  dentroToleranciaCompra,
  fingerprintLineasGuardables,
  lineaDesdeCompra,
  lineaDetalleVacia,
  LineaDetalleUi,
  recalcularLineaDetalle,
  redondearCompra,
  textoConfirmacionEliminarFactura,
} from '../../../data/models/compra.model';

@Component({
  selector: 'app-compra-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe],
  template: `
    @if (cargando()) {
      <p>Cargando factura…</p>
    } @else if (compra()) {
      <header class="cabecera-readonly">
        <a routerLink="../" class="back" (click)="intentarVolver($event)">← Volver al listado</a>
        <h2>Detalle de factura {{ compra()!.numeroFactura }}</h2>
        <dl>
          <div><dt>Proveedor</dt><dd>{{ compra()!.nombreProveedor }} ({{ compra()!.codigoProveedor }})</dd></div>
          <div><dt>Fecha</dt><dd>{{ compra()!.fechaCompra }}</dd></div>
          <div><dt>Total factura</dt><dd>$ {{ compra()!.totalFactura | number: '1.3-3' }}</dd></div>
          <div><dt>Estado</dt><dd>{{ compra()!.estado }}</dd></div>
        </dl>
        @if (compra()!.estado === 'BORRADOR') {
          <p class="hint">
            Completá los ítems hasta que la suma de subtotales coincida con el total y guardá la factura.
          </p>
        }
      </header>

      @if (sinLineas()) {
        <section class="sin-items">
          <p class="warn">
            La factura no tiene ítems. Podés agregar uno nuevo o eliminar la cabecera para volver al listado.
          </p>
          <button type="button" class="secundario" (click)="agregarLinea()">+ Agregar ítem</button>
          <div class="eliminar-cabecera">
            <h3>Eliminar factura vacía</h3>
            <p class="warn-adv">
              Se eliminarán los datos de la cabecera. Cuando exista inventario, esta acción revertirá los
              movimientos asociados.
            </p>
            <p>Escribí exactamente:</p>
            <code class="frase">{{ fraseEliminarEsperada() }}</code>
            <label>
              Confirmación
              <input [(ngModel)]="textoConfirmacionEliminar" autocomplete="off" />
            </label>
            <button
              type="button"
              class="danger"
              [disabled]="!puedeConfirmarEliminar() || guardando()"
              (click)="eliminarCabecera()"
            >
              Eliminar cabecera y salir
            </button>
          </div>
        </section>
      } @else {
        <section class="detalle-editable">
          <h3>Ítems de la factura</h3>
          <p class="hint-nota">
            Los cambios en cantidades o precios impactan el inventario y los precios vigentes del catalogo.
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
              <button type="button" class="danger" (click)="quitarLinea(i)">Quitar</button>

              @if (linea.advertenciaLinea) {
                <p class="warn">{{ linea.advertenciaLinea }}</p>
              }
            </div>
          }

          <button type="button" class="secundario" (click)="agregarLinea()">+ Agregar ítem</button>

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
            <button type="button" [disabled]="!puedeGuardar() || guardando()" (click)="guardar()">
              {{ compra()!.estado === 'REGISTRADA' ? 'Guardar cambios' : 'Guardar factura completa' }}
            </button>
            @if (error()) {
              <p class="error">{{ error() }}</p>
            }
          </footer>
        </section>
      }
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .back {
        color: #166534;
        text-decoration: none;
        font-size: 0.9rem;
        cursor: pointer;
      }
      h2 {
        margin: 0.5rem 0 1rem;
      }
      dl {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
        gap: 0.75rem;
        margin: 0;
      }
      dt {
        font-size: 0.75rem;
        color: #6b7280;
      }
      dd {
        margin: 0.15rem 0 0;
        font-weight: 600;
      }
      .hint,
      .hint-nota {
        color: #6b7280;
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
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.85rem;
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
      }
      .dropdown li {
        padding: 0.4rem 0.55rem;
        cursor: pointer;
      }
      .dropdown li:hover {
        background: #ecfdf5;
      }
      button {
        padding: 0.45rem 0.9rem;
        background: #166534;
        color: white;
        border: none;
        border-radius: 4px;
        cursor: pointer;
      }
      button.secundario {
        background: #374151;
        margin-top: 1rem;
      }
      button.danger {
        background: #b91c1c;
      }
      button:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .totales,
      .eliminar-cabecera {
        margin-top: 1.5rem;
        padding-top: 1rem;
        border-top: 2px solid #e5e7eb;
      }
      .eliminar-cabecera {
        max-width: 520px;
      }
      .frase {
        display: block;
        padding: 0.5rem;
        background: #fef3c7;
        border-radius: 4px;
        margin: 0.5rem 0 1rem;
      }
      .ok {
        color: #166534;
      }
      .bad {
        color: #b91c1c;
      }
      .warn,
      .warn-adv {
        color: #b45309;
        font-size: 0.85rem;
        width: 100%;
      }
      .warn-adv {
        font-weight: 600;
      }
      .error {
        color: #b91c1c;
      }
    `,
  ],
})
export class CompraDetalleComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly compra = signal<CompraCompleta | null>(null);
  readonly lineas = signal<LineaDetalleUi[]>([]);
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly mpAbiertoIndex = signal<number | null>(null);
  readonly facturaEliminada = signal(false);
  readonly fraseEliminarEsperada = signal('');

  textoConfirmacionEliminar = '';

  private lineasSnapshot = '';
  private sincronizandoMp = false;

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

  readonly puedeGuardar = computed(() => {
    if (!this.totalesCuadran() || !this.lineasValidas()) return false;
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
    this.api.getMateriasPrimas(this.idGranja).subscribe({
      next: (list) => this.materiasPrimas.set(list),
    });
    this.cargarCompra();
  }

  cargarCompra(): void {
    this.cargando.set(true);
    this.api.getCompra(this.idGranja, this.idCompra).subscribe({
      next: (c) => {
        this.compra.set(c);
        this.fraseEliminarEsperada.set(textoConfirmacionEliminarFactura(c.numeroFactura));
        if (c.lineas.length > 0) {
          const lineas = c.lineas.map(lineaDesdeCompra);
          for (const linea of lineas) {
            recalcularLineaDetalle(linea);
          }
          this.lineas.set(lineas);
        } else if (c.estado === 'BORRADOR') {
          this.lineas.set([lineaDetalleVacia()]);
        } else {
          this.lineas.set([]);
        }
        this.establecerSnapshotLineas();
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar la compra');
        this.cargando.set(false);
      },
    });
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

  agregarLinea(): void {
    this.lineas.update((prev) => [...prev, lineaDetalleVacia()]);
  }

  quitarLinea(index: number): void {
    this.lineas.update((prev) => prev.filter((_, i) => i !== index));
  }

  guardar(): void {
    if (!this.puedeGuardar()) return;
    const body = {
      lineas: this.lineasConMateria().map((l) => ({
        idMateriaPrima: l.idMateriaPrima!,
        cantidadKg: l.cantidadKg!,
        precioPorKilo: l.precioPorKilo!,
        subtotal: l.subtotal!,
      })),
    };
    this.guardando.set(true);
    this.error.set(null);
    this.api.guardarCompraDetalle(this.idGranja, this.idCompra, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.establecerSnapshotLineas();
        void this.router.navigate(['..'], { relativeTo: this.route });
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo guardar el detalle'));
      },
    });
  }

  puedeConfirmarEliminar(): boolean {
    return this.textoConfirmacionEliminar.trim() === this.fraseEliminarEsperada();
  }

  eliminarCabecera(): void {
    if (!this.puedeConfirmarEliminar()) return;
    this.guardando.set(true);
    this.api.eliminarCompra(this.idGranja, this.idCompra).subscribe({
      next: () => {
        this.facturaEliminada.set(true);
        this.guardando.set(false);
        void this.router.navigate(['../'], { relativeTo: this.route });
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la factura'));
      },
    });
  }

  intentarVolver(event: Event): void {
    if (!this.puedeSalir()) {
      event.preventDefault();
    }
  }

  puedeSalir(): boolean {
    if (this.facturaEliminada()) return true;
    if (this.sinLineas()) return false;
    return !this.hayCambiosSinGuardar();
  }

  hayCambiosSinGuardar(): boolean {
    return fingerprintLineasGuardables(this.lineas()) !== this.lineasSnapshot;
  }

  mensajeBloqueoSalida(): string {
    if (this.sinLineas()) {
      return 'La factura no tiene ítems. Eliminá la cabecera o agregá al menos un ítem antes de salir.';
    }
    if (!this.totalesCuadran()) {
      return 'La suma de subtotales debe coincidir con el total de la factura (tolerancia ± $0,50) antes de salir.';
    }
    if (!this.puedeGuardar()) {
      return 'Revisá las advertencias en las líneas (cantidad × precio vs subtotal) antes de guardar y salir.';
    }
    if (this.hayCambiosSinGuardar()) {
      return 'Tenés cambios sin guardar. Usá «Guardar factura completa» y luego podés volver al listado.';
    }
    return 'No podés salir todavía. Revisá la factura e intentá de nuevo.';
  }

  private establecerSnapshotLineas(): void {
    this.lineasSnapshot = fingerprintLineasGuardables(this.lineas());
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
