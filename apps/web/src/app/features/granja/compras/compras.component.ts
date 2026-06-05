import { Component, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { CompraResumen, textoConfirmacionEliminarFactura } from '../../../data/models/compra.model';

@Component({
  selector: 'app-compras',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe],
  template: `
    <header class="toolbar">
      <h2>Compras</h2>
      <a routerLink="nueva" class="btn-nueva">Nueva Factura</a>
    </header>

    @if (compraEliminando()) {
      <section class="panel eliminar">
        <h3>Eliminar factura {{ compraEliminando()!.numeroFactura }}</h3>
        <p class="warn-adv">
          Se eliminarán todos los ítems asociados. Esto impactará el inventario y los valores calculados
          cuando esos módulos estén activos.
        </p>
        <p>Escribí exactamente la frase siguiente para confirmar:</p>
        <code class="frase">{{ fraseEliminarEsperada() }}</code>
        <label>
          Confirmación
          <input [(ngModel)]="textoConfirmacionEliminar" autocomplete="off" />
        </label>
        <div class="acciones-form">
          <button
            type="button"
            class="danger"
            [disabled]="!puedeConfirmarEliminar() || guardando()"
            (click)="confirmarEliminar()"
          >
            Eliminar factura
          </button>
          <button type="button" class="secundario" (click)="cancelarEliminar()">Cancelar</button>
        </div>
      </section>
    }

    @if (!compraEliminando()) {
      <section class="lista">
        <h3>Facturas</h3>
        @if (cargando()) {
          <p>Cargando…</p>
        } @else if (compras().length === 0) {
          <p class="vacio">Todavía no hay compras cargadas.</p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Factura</th>
                <th>Fecha</th>
                <th>Proveedor</th>
                <th>Total</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              @for (c of compras(); track c.id) {
                <tr>
                  <td>{{ c.numeroFactura }}</td>
                  <td>{{ c.fechaCompra }}</td>
                  <td>{{ c.nombreProveedor }}</td>
                  <td>{{ c.totalFactura | number: '1.3-3' }}</td>
                  <td>
                    <span [class.borrador]="c.estado === 'BORRADOR'">{{ c.estado }}</span>
                  </td>
                  <td class="acciones">
                    <a [routerLink]="[c.id]">{{
                      c.estado === 'BORRADOR' ? 'Detalle' : 'Ver / editar ítems'
                    }}</a>
                    <a [routerLink]="[c.id, 'editar']" class="link">Editar</a>
                    <button type="button" class="link danger-text" (click)="iniciarEliminar(c)">
                      Eliminar
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </section>
    }

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
      }
      h2 {
        margin: 0;
      }
      .panel {
        margin: 1.25rem 0;
        padding: 1rem;
        border: 1px solid #e5e7eb;
        border-radius: 6px;
        background: #fafafa;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.85rem;
        margin-top: 0.75rem;
      }
      input {
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
      }
      a.btn-nueva {
        background: #166534;
        color: white;
        text-decoration: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
        font-size: 0.9rem;
      }
      button {
        padding: 0.5rem 1rem;
        background: #166534;
        color: white;
        border: none;
        border-radius: 4px;
        cursor: pointer;
      }
      button.secundario {
        background: #6b7280;
      }
      button.danger {
        background: #b91c1c;
      }
      a.link,
      button.link {
        background: none;
        color: #166534;
        padding: 0;
        text-decoration: underline;
        border: none;
        cursor: pointer;
        font-size: inherit;
      }
      button.danger-text,
      a.danger-text {
        color: #b91c1c;
      }
      button:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .acciones-form {
        display: flex;
        gap: 0.75rem;
        margin-top: 1rem;
      }
      .acciones {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem 0.75rem;
        align-items: center;
      }
      .error {
        color: #b91c1c;
        margin-top: 1rem;
      }
      .warn-adv {
        color: #b45309;
        font-size: 0.9rem;
        font-weight: 600;
      }
      .vacio {
        color: #6b7280;
      }
      .frase {
        display: block;
        padding: 0.5rem;
        background: #fef3c7;
        border-radius: 4px;
        margin: 0.5rem 0 1rem;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        margin-top: 1rem;
      }
      th,
      td {
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid #e5e7eb;
        text-align: left;
      }
      .borrador {
        color: #b45309;
        font-weight: 600;
      }
      a {
        color: #166534;
      }
    `,
  ],
})
export class ComprasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly compras = signal<CompraResumen[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly compraEliminando = signal<CompraResumen | null>(null);
  readonly fraseEliminarEsperada = signal('');

  textoConfirmacionEliminar = '';

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargarCompras();
  }

  iniciarEliminar(c: CompraResumen): void {
    this.compraEliminando.set(c);
    this.fraseEliminarEsperada.set(textoConfirmacionEliminarFactura(c.numeroFactura));
    this.textoConfirmacionEliminar = '';
  }

  cancelarEliminar(): void {
    this.compraEliminando.set(null);
    this.textoConfirmacionEliminar = '';
    this.fraseEliminarEsperada.set('');
  }

  puedeConfirmarEliminar(): boolean {
    return this.textoConfirmacionEliminar.trim() === this.fraseEliminarEsperada();
  }

  confirmarEliminar(): void {
    const c = this.compraEliminando();
    if (!c || !this.puedeConfirmarEliminar()) return;

    this.guardando.set(true);
    this.error.set(null);
    this.api.eliminarCompra(this.idGranja, c.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cancelarEliminar();
        this.recargarCompras();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la factura'));
      },
    });
  }

  private recargarCompras(): void {
    this.cargando.set(true);
    this.api.getCompras(this.idGranja).subscribe({
      next: (list) => {
        this.compras.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar las compras');
        this.cargando.set(false);
      },
    });
  }
}
