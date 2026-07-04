import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import {
  ArchivoDetalle,
  TIPO_ARCHIVO_LABEL,
} from '../../../data/models/archivo.model';
import { InventarioListadoResponse } from '../../../data/models/inventario.model';
import { CompraCompleta } from '../../../data/models/compra.model';
import { FormulaCompleta } from '../../../data/models/formula.model';

/**
 * Vista de solo lectura de un archivo: la cabecera (código, descripción, fecha, autor)
 * y el snapshot renderizado con la misma estructura de tabla que el módulo original.
 * No hay ninguna acción de edición: el archivo es un registro histórico inmutable.
 */
@Component({
  selector: 'app-archivo-detalle',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  template: `
    <a routerLink="../" class="reforma-back"><i class="pi pi-arrow-left"></i> Volver a archivos</a>

    @if (archivo(); as a) {
      <header class="cabecera glass-card-strong">
        <div>
          <h2 class="reforma-page-title">{{ a.codigoArchivo }}</h2>
          <p class="sub text-dim">{{ a.descripcion || 'Sin descripción' }}</p>
        </div>
        <div class="meta">
          <div><span class="mini text-dim">Módulo</span><strong>{{ etiquetaTipo() }}</strong></div>
          <div><span class="mini text-dim">Creado</span><strong>{{ a.fechaCreacion | date: 'dd/MM/yyyy HH:mm' }}</strong></div>
          <div><span class="mini text-dim">Creado por</span><strong>{{ a.creadoPorEmail }}</strong></div>
          <div><span class="mini text-dim">Registros</span><strong>{{ a.totalRegistros }}</strong></div>
        </div>
      </header>

      <p class="reforma-alert inmutable">
        <i class="pi pi-lock"></i>
        Registro inmutable — datos de {{ etiquetaTipo() }} al
        {{ a.fechaCreacion | date: 'dd/MM/yyyy HH:mm' }}. Los cambios posteriores en el módulo
        no afectan este archivo.
      </p>

      <!-- INVENTARIO -->
      @if (inventario(); as inv) {
        @if (inv.items.length === 0) {
          <p class="reforma-empty">El inventario no tenía materias primas al momento del archivo.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Materia prima</th>
                  <th class="num">Precio (vigente)</th>
                  <th class="num">Cant. acumulada</th>
                  <th class="num">Cant. en sistema</th>
                  <th class="num">Cant. real</th>
                  <th class="num">Merma</th>
                  <th class="num">Valor de stock</th>
                  <th class="num">Precio almacén</th>
                </tr>
              </thead>
              <tbody>
                @for (i of inv.items; track i.idMateriaPrima) {
                  <tr>
                    <td>{{ i.codigoMateriaPrima }}</td>
                    <td>{{ i.nombreMateriaPrima }}</td>
                    <td class="num">$ {{ i.precioPorKilo | number: '1.3-3' }}</td>
                    <td class="num">{{ i.cantidadAcumulada | number: '1.3-3' }} kg</td>
                    <td class="num">{{ i.cantidadSistema | number: '1.3-3' }} kg</td>
                    <td class="num">{{ i.cantidadReal | number: '1.3-3' }} kg</td>
                    <td class="num">{{ i.cantidadSistema - i.cantidadReal | number: '1.3-3' }} kg</td>
                    <td class="num">$ {{ i.valorStock | number: '1.2-2' }}</td>
                    <td class="num">$ {{ i.precioAlmacen | number: '1.3-3' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }

      <!-- COMPRAS -->
      @if (compras(); as lista) {
        @if (lista.length === 0) {
          <p class="reforma-empty">No había compras activas al momento del archivo.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr>
                  <th></th>
                  <th>Factura</th>
                  <th>Fecha</th>
                  <th>Proveedor</th>
                  <th>Estado</th>
                  <th class="num">Líneas</th>
                  <th class="num">Total factura</th>
                </tr>
              </thead>
              <tbody>
                @for (c of lista; track c.id) {
                  <tr class="fila-expandible" (click)="alternarExpandida(c.id)">
                    <td>
                      <i class="pi" [class.pi-chevron-right]="!estaExpandida(c.id)" [class.pi-chevron-down]="estaExpandida(c.id)"></i>
                    </td>
                    <td>{{ c.numeroFactura }}</td>
                    <td>{{ c.fechaCompra | date: 'dd/MM/yyyy' }}</td>
                    <td>{{ c.codigoProveedor }} — {{ c.nombreProveedor }}</td>
                    <td>{{ c.estado }}</td>
                    <td class="num">{{ c.lineas.length }}</td>
                    <td class="num">$ {{ c.totalFactura | number: '1.2-2' }}</td>
                  </tr>
                  @if (estaExpandida(c.id)) {
                    <tr class="fila-detalle">
                      <td colspan="7">
                        @if (c.lineas.length === 0) {
                          <p class="mini text-dim">Sin líneas (compra en borrador).</p>
                        } @else {
                          <table class="reforma-table anidada">
                            <thead>
                              <tr>
                                <th>Código</th>
                                <th>Materia prima</th>
                                <th class="num">Cantidad (kg)</th>
                                <th class="num">Precio/kg</th>
                                <th class="num">Subtotal</th>
                              </tr>
                            </thead>
                            <tbody>
                              @for (l of c.lineas; track l.idMateriaPrima) {
                                <tr>
                                  <td>{{ l.codigoMateriaPrima }}</td>
                                  <td>{{ l.nombreMateriaPrima }}</td>
                                  <td class="num">{{ l.cantidadKg | number: '1.3-3' }}</td>
                                  <td class="num">$ {{ l.precioPorKilo | number: '1.3-3' }}</td>
                                  <td class="num">$ {{ l.subtotal | number: '1.2-2' }}</td>
                                </tr>
                              }
                            </tbody>
                          </table>
                        }
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        }
      }

      <!-- FÓRMULAS -->
      @if (formulas(); as lista) {
        @if (lista.length === 0) {
          <p class="reforma-empty">No había fórmulas activas al momento del archivo.</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr>
                  <th></th>
                  <th>Código</th>
                  <th>Descripción</th>
                  <th>Animal</th>
                  <th class="num">Kg cargados</th>
                  <th class="num">Costo total</th>
                  <th>Completa</th>
                </tr>
              </thead>
              <tbody>
                @for (f of lista; track f.id) {
                  <tr class="fila-expandible" (click)="alternarExpandida(f.id)">
                    <td>
                      <i class="pi" [class.pi-chevron-right]="!estaExpandida(f.id)" [class.pi-chevron-down]="estaExpandida(f.id)"></i>
                    </td>
                    <td>{{ f.codigoFormula }}</td>
                    <td>{{ f.descripcionFormula }}</td>
                    <td>{{ f.codigoAnimal }} — {{ f.descripcionAnimal }}</td>
                    <td class="num">{{ f.sumaKg | number: '1.2-2' }} kg</td>
                    <td class="num">$ {{ f.costoTotalFormula | number: '1.2-2' }}</td>
                    <td>{{ f.completa ? 'Sí' : 'No' }}</td>
                  </tr>
                  @if (estaExpandida(f.id)) {
                    <tr class="fila-detalle">
                      <td colspan="7">
                        @if (f.lineas.length === 0) {
                          <p class="mini text-dim">Sin ingredientes cargados.</p>
                        } @else {
                          <table class="reforma-table anidada">
                            <thead>
                              <tr>
                                <th>Código</th>
                                <th>Materia prima</th>
                                <th class="num">Cantidad (kg)</th>
                                <th class="num">% fórmula</th>
                                <th class="num">Precio/kg</th>
                                <th class="num">Costo parcial</th>
                              </tr>
                            </thead>
                            <tbody>
                              @for (l of f.lineas; track l.idMateriaPrima) {
                                <tr>
                                  <td>{{ l.codigoMateriaPrima }}</td>
                                  <td>{{ l.nombreMateriaPrima }}</td>
                                  <td class="num">{{ l.cantidadKg | number: '1.2-2' }}</td>
                                  <td class="num">{{ l.porcentajeFormula | number: '1.2-2' }} %</td>
                                  <td class="num">$ {{ l.precioPorKilo | number: '1.3-3' }}</td>
                                  <td class="num">$ {{ l.costoParcial | number: '1.2-2' }}</td>
                                </tr>
                              }
                            </tbody>
                          </table>
                        }
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        }
      }
    } @else if (cargando()) {
      <p class="reforma-empty">Cargando archivo…</p>
    }

    @if (error()) {
      <p class="reforma-alert reforma-alert-error">
        <i class="pi pi-exclamation-circle"></i> {{ error() }}
      </p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .cabecera {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        flex-wrap: wrap;
        padding: 1.25rem 1.5rem;
        margin-top: 0.75rem;
      }
      .reforma-page-title {
        margin: 0;
      }
      .sub {
        margin: 0.25rem 0 0;
      }
      .meta {
        display: flex;
        gap: 1.5rem;
        flex-wrap: wrap;
        align-items: center;
      }
      .meta > div {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
      }
      .mini {
        font-size: 0.8rem;
      }
      .inmutable {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin: 1rem 0;
        padding: 0.65rem 1rem;
        border-radius: 12px;
        color: var(--reforma-cyan, #06b6d4);
        background: color-mix(in srgb, var(--reforma-cyan, #06b6d4) 10%, transparent);
        border: 1px solid color-mix(in srgb, var(--reforma-cyan, #06b6d4) 35%, transparent);
      }
      .reforma-table td {
        white-space: nowrap;
      }
      .fila-expandible {
        cursor: pointer;
      }
      .fila-expandible i {
        font-size: 0.8rem;
        color: var(--reforma-text-dim);
      }
      .fila-detalle > td {
        padding: 0.5rem 1rem 1rem 2.25rem;
      }
      table.anidada {
        margin: 0;
      }
    `,
  ],
})
export class ArchivoDetalleComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly archivo = signal<ArchivoDetalle | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  /** Ids de compras/fórmulas con el detalle de líneas desplegado. */
  readonly expandidas = signal<Set<string>>(new Set());

  readonly inventario = computed<InventarioListadoResponse | null>(() => {
    const a = this.archivo();
    return a?.tipo === 'INVENTARIO' ? (a.datos as InventarioListadoResponse) : null;
  });

  readonly compras = computed<CompraCompleta[] | null>(() => {
    const a = this.archivo();
    return a?.tipo === 'COMPRAS' ? (a.datos as CompraCompleta[]) : null;
  });

  readonly formulas = computed<FormulaCompleta[] | null>(() => {
    const a = this.archivo();
    return a?.tipo === 'FORMULAS' ? (a.datos as FormulaCompleta[]) : null;
  });

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    const idArchivo = Number(this.route.snapshot.paramMap.get('idArchivo'));
    this.api.getArchivoDetalle(this.idGranja, idArchivo).subscribe({
      next: (detalle) => {
        this.archivo.set(detalle);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cargar el archivo'));
        this.cargando.set(false);
      },
    });
  }

  etiquetaTipo(): string {
    const a = this.archivo();
    return a ? TIPO_ARCHIVO_LABEL[a.tipo] : '';
  }

  estaExpandida(id: string): boolean {
    return this.expandidas().has(id);
  }

  alternarExpandida(id: string): void {
    this.expandidas.update((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }
}
