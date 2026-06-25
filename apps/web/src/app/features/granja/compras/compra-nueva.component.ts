import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Proveedor } from '../../../data/models/proveedor.model';
import { hoyIso } from '../../../data/models/compra.model';
import { NumeroFormatoDirective } from '../../../shared/numero-formato.directive';

@Component({
  selector: 'app-compra-nueva',
  standalone: true,
  imports: [FormsModule, RouterLink, NumeroFormatoDirective],
  template: `
    <a routerLink=".." class="reforma-back"><i class="pi pi-arrow-left"></i> Volver al listado</a>
    <h2 class="reforma-page-title">Nueva factura</h2>
    <p class="subtitulo">Completá la cabecera; luego cargarás los ítems en el detalle.</p>

    <section class="reforma-section">
      <form (ngSubmit)="crearCabecera()" #f="ngForm" class="formulario">
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
            name="totalFactura"
            [appNumero]="2"
            [(ngModel)]="totalFactura"
            required
          />
        </label>
        <label class="reforma-field full">
          <span>Observaciones</span>
          <input class="reforma-input" name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
        </label>

        <div class="acciones">
          <a routerLink=".." class="reforma-btn-ghost"><i class="pi pi-times"></i> Cancelar</a>
          <button class="reforma-btn" type="submit" [disabled]="guardando() || f.invalid || !idProveedorSeleccionado()">
            <i class="pi pi-check"></i> Guardar cabecera e ir al detalle
          </button>
        </div>
        @if (error()) {
          <p class="reforma-alert reforma-alert-error">
            <i class="pi pi-exclamation-circle"></i> {{ error() }}
          </p>
        }
      </form>
    </section>
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
      .subtitulo {
        color: var(--reforma-text-dim);
        margin: 0 0 1.25rem;
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
export class CompraNuevaComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly proveedores = signal<Proveedor[]>([]);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly mostrarProveedores = signal(false);
  readonly idProveedorSeleccionado = signal<number | null>(null);

  readonly hoy = hoyIso();

  nombreProveedor = '';
  codigoProveedor = '';
  numeroFactura = '';
  fechaCompra = hoyIso();
  totalFactura: number | null = null;
  observaciones = '';

  private sincronizandoProveedor = false;

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.api.getProveedores(this.idGranja).subscribe({
      next: (list) => this.proveedores.set(list),
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

  crearCabecera(): void {
    const idProv = this.idProveedorSeleccionado();
    if (!idProv || !this.numeroFactura.trim() || !this.totalFactura) return;

    this.guardando.set(true);
    this.error.set(null);
    this.api
      .crearCompraCabecera(this.idGranja, {
        idProveedor: idProv,
        numeroFactura: this.numeroFactura.trim(),
        fechaCompra: this.fechaCompra,
        totalFactura: this.totalFactura,
        observaciones: this.observaciones.trim() || undefined,
      })
      .subscribe({
        next: (compra) => {
          this.guardando.set(false);
          void this.router.navigate(['..', compra.id], { relativeTo: this.route });
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(err.error?.message ?? 'No se pudo crear la cabecera');
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
