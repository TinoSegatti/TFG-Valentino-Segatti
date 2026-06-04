import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Proveedor } from '../../../data/models/proveedor.model';
import { hoyIso } from '../../../data/models/compra.model';

@Component({
  selector: 'app-compra-editar',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <a routerLink="../.." class="back">← Volver al listado</a>
    <h2>Editar cabecera</h2>
    @if (cargando()) {
      <p>Cargando…</p>
    } @else {
      @if (cantidadLineas() > 0) {
        <p class="warn">
          Si modificás el total, deberás ajustar el detalle para que la suma de subtotales coincida.
        </p>
      }
      <form (ngSubmit)="guardar()" #f="ngForm" class="formulario">
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

        <div class="acciones-form">
          <button type="submit" [disabled]="guardando() || f.invalid || !idProveedorSeleccionado()">
            Guardar cambios
          </button>
          <a routerLink="../.." class="btn-cancelar">Cancelar</a>
        </div>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
      </form>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        max-width: 900px;
      }
      .back {
        color: #166534;
        text-decoration: none;
        font-size: 0.9rem;
      }
      h2 {
        margin: 0.75rem 0 1rem;
      }
      .warn {
        color: #b45309;
        font-size: 0.9rem;
        margin-bottom: 1rem;
      }
      .formulario {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        align-items: flex-end;
        padding: 1rem;
        border: 1px solid #e5e7eb;
        border-radius: 6px;
        background: #fafafa;
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
        min-width: 260px;
      }
      input {
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
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
        max-height: 200px;
        overflow-y: auto;
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
      }
      .dropdown li {
        padding: 0.45rem 0.6rem;
        cursor: pointer;
      }
      .dropdown li:hover {
        background: #ecfdf5;
      }
      button {
        padding: 0.5rem 1rem;
        background: #166534;
        color: white;
        border: none;
        border-radius: 4px;
        cursor: pointer;
      }
      button:disabled {
        opacity: 0.5;
      }
      .btn-cancelar {
        display: inline-flex;
        align-items: center;
        padding: 0.5rem 1rem;
        background: #6b7280;
        color: white;
        text-decoration: none;
        border-radius: 4px;
        font-size: 0.9rem;
      }
      .acciones-form {
        display: flex;
        gap: 0.75rem;
        width: 100%;
        align-items: center;
      }
      .error {
        color: #b91c1c;
        width: 100%;
      }
    `,
  ],
})
export class CompraEditarComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly proveedores = signal<Proveedor[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly mostrarProveedores = signal(false);
  readonly idProveedorSeleccionado = signal<number | null>(null);
  readonly cantidadLineas = signal(0);

  readonly hoy = hoyIso();

  nombreProveedor = '';
  codigoProveedor = '';
  numeroFactura = '';
  fechaCompra = hoyIso();
  totalFactura: number | null = null;
  observaciones = '';

  private sincronizandoProveedor = false;
  private totalOriginal: number | null = null;

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
      error: () => {
        this.error.set('No se pudieron cargar los proveedores');
        this.cargando.set(false);
      },
    });
  }

  private cargarCompra(): void {
    this.api.getCompra(this.idGranja, this.idCompra).subscribe({
      next: (c) => {
        this.totalOriginal = c.totalFactura;
        this.cantidadLineas.set(c.lineas.length);
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
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar la factura');
        this.cargando.set(false);
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

  guardar(): void {
    const idProv = this.idProveedorSeleccionado();
    if (!idProv || !this.numeroFactura.trim() || !this.totalFactura) return;

    this.guardando.set(true);
    this.error.set(null);
    this.api
      .actualizarCompraCabecera(this.idGranja, this.idCompra, {
        idProveedor: idProv,
        numeroFactura: this.numeroFactura.trim(),
        fechaCompra: this.fechaCompra,
        totalFactura: this.totalFactura,
        observaciones: this.observaciones.trim() || undefined,
      })
      .subscribe({
        next: (compra) => {
          this.guardando.set(false);
          const totalCambio = this.totalOriginal !== compra.totalFactura;
          if (compra.requiereAjusteDetalle || (totalCambio && compra.lineas.length > 0)) {
            void this.router.navigate(['..'], { relativeTo: this.route });
          } else {
            void this.router.navigate(['../..'], { relativeTo: this.route });
          }
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(err.error?.message ?? 'No se pudo actualizar la cabecera');
        },
      });
  }

  private aplicarProveedor(p: Proveedor): void {
    this.sincronizandoProveedor = true;
    this.idProveedorSeleccionado.set(p.id);
    this.nombreProveedor = p.nombreProveedor;
    this.codigoProveedor = p.codigoProveedor;
    this.sincronizandoProveedor = false;
  }
}
