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
    <a routerLink="../.." class="reforma-back"><i class="pi pi-arrow-left"></i> Volver al listado</a>
    <h2 class="reforma-page-title">Editar cabecera</h2>

    @if (cargando()) {
      <p class="reforma-empty">Cargando…</p>
    } @else {
      @if (cantidadLineas() > 0) {
        <p class="reforma-alert reforma-alert-warn">
          <i class="pi pi-info-circle"></i>
          Si modificás el total, deberás ajustar el detalle para que la suma de subtotales coincida.
        </p>
      }
      <section class="reforma-section">
        <form (ngSubmit)="guardar()" #f="ngForm" class="formulario">
          <label class="reforma-field reforma-autocomplete proveedor">
            <span>Proveedor</span>
            <input
              class="reforma-input"
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
          <label class="reforma-field">
            <span>Código de proveedor</span>
            <input
              class="reforma-input"
              name="codigoProveedor"
              [(ngModel)]="codigoProveedor"
              (ngModelChange)="onCodigoProveedorChange($event)"
              maxlength="50"
              required
            />
          </label>

          <label class="reforma-field">
            <span>Nº / código de factura</span>
            <input class="reforma-input" name="numeroFactura" [(ngModel)]="numeroFactura" maxlength="100" required />
          </label>
          <label class="reforma-field">
            <span>Fecha</span>
            <input class="reforma-input" type="date" name="fechaCompra" [(ngModel)]="fechaCompra" [max]="hoy" required />
          </label>
          <label class="reforma-field">
            <span>Total factura ($)</span>
            <input
              class="reforma-input"
              type="number"
              name="totalFactura"
              [(ngModel)]="totalFactura"
              step="0.001"
              min="0.001"
              required
            />
          </label>
          <label class="reforma-field full">
            <span>Observaciones</span>
            <input class="reforma-input" name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
          </label>

          <div class="acciones">
            <a routerLink="../.." class="reforma-btn-ghost"><i class="pi pi-times"></i> Cancelar</a>
            <button class="reforma-btn" type="submit" [disabled]="guardando() || f.invalid || !idProveedorSeleccionado()">
              <i class="pi pi-check"></i> Guardar cambios
            </button>
          </div>
          @if (error()) {
            <p class="reforma-alert reforma-alert-error">
              <i class="pi pi-exclamation-circle"></i> {{ error() }}
            </p>
          }
        </form>
      </section>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        max-width: 64rem;
      }
      .reforma-back {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        color: var(--reforma-accent);
        text-decoration: none;
        font-size: 0.9rem;
        margin-bottom: 0.5rem;
      }
      .reforma-back:hover {
        color: var(--reforma-text);
      }
      .formulario {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
        gap: 1rem;
        align-items: end;
      }
      .reforma-field.full {
        grid-column: 1 / -1;
      }
      .acciones {
        grid-column: 1 / -1;
        display: flex;
        gap: 0.75rem;
        justify-content: flex-end;
        margin-top: 0.5rem;
      }
      .reforma-alert {
        grid-column: 1 / -1;
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
