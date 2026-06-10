import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import {
  FabricacionResumen,
  textoConfirmacionEliminarFabricacion,
} from '../../../data/models/fabricacion.model';

@Component({
  selector: 'app-fabricaciones',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe],
  template: `
    <header class="toolbar">
      <h2>Fabricaciones</h2>
      <a routerLink="nueva" class="btn-nueva">Nueva fabricacion</a>
    </header>

    @if (fabricacionEliminando()) {
      <section class="panel eliminar">
        <h3>Eliminar fabricacion {{ fabricacionEliminando()!.codigoFabricacion }}</h3>
        <p class="warn-adv">
          Se restaurara el stock de las materias primas usadas. El costo registrado se conserva en el
          historial hasta que se elimine la fabricacion.
        </p>
        <p>Escribi exactamente la frase siguiente para confirmar:</p>
        <code class="frase">{{ fraseEliminarEsperada() }}</code>
        <label>
          Confirmacion
          <input [(ngModel)]="textoConfirmacionEliminar" autocomplete="off" />
        </label>
        <div class="acciones-form">
          <button
            type="button"
            class="danger"
            [disabled]="!puedeConfirmarEliminar() || guardando()"
            (click)="confirmarEliminar()"
          >
            Eliminar fabricacion
          </button>
          <button type="button" class="secundario" (click)="cancelarEliminar()">Cancelar</button>
        </div>
      </section>
    }

    @if (!fabricacionEliminando()) {
      <section class="lista">
        @if (cargando()) {
          <p>Cargando…</p>
        } @else if (fabricaciones().length === 0) {
          <p class="vacio">Todavia no hay fabricaciones cargadas.</p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Codigo</th>
                <th>Fecha</th>
                <th>Descripcion</th>
                <th>Formula</th>
                <th>Veces</th>
                <th>Costo</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              @for (f of fabricaciones(); track f.id) {
                <tr [class.sin-stock]="f.sinExistencias">
                  <td>{{ f.codigoFabricacion }}</td>
                  <td>{{ f.fechaFabricacion | date: 'dd/MM/yyyy' : 'UTC' }}</td>
                  <td>{{ f.descripcionFabricacion }}</td>
                  <td>{{ f.codigoFormula ?? '—' }}</td>
                  <td>{{ f.veces | number: '1.3-3' }}</td>
                  <td>$ {{ f.costoTotalFabricacion | number: '1.3-3' }}</td>
                  <td>
                    <span [class.borrador]="f.estado === 'BORRADOR'">{{ f.estado }}</span>
                    @if (f.sinExistencias) {
                      <span class="warn-badge">Sin stock</span>
                    }
                  </td>
                  <td class="acciones">
                    <a [routerLink]="[f.id]">Ver</a>
                    <button type="button" class="link danger-text" (click)="iniciarEliminar(f)">
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
      a.btn-nueva {
        background: #166534;
        color: white;
        text-decoration: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
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
      .acciones {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
      }
      .acciones a,
      .acciones button.link {
        color: #166534;
        background: none;
        border: none;
        cursor: pointer;
        text-decoration: underline;
        padding: 0;
        font: inherit;
      }
      .danger-text {
        color: #b91c1c !important;
      }
      .borrador {
        color: #b45309;
        font-weight: 600;
      }
      .warn-badge {
        display: inline-block;
        margin-left: 0.35rem;
        font-size: 0.75rem;
        color: #b91c1c;
      }
      .sin-stock td {
        background: #fef2f2;
      }
      .panel {
        margin: 1.25rem 0;
        padding: 1rem;
        border: 1px solid #e5e7eb;
        border-radius: 6px;
        background: #fafafa;
      }
      .frase {
        display: block;
        padding: 0.5rem;
        background: #f3f4f6;
        margin: 0.5rem 0;
      }
      .warn-adv {
        color: #b45309;
      }
      .error {
        color: #b91c1c;
      }
      .vacio {
        color: #6b7280;
      }
      button.danger {
        background: #b91c1c;
        color: white;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
        cursor: pointer;
      }
      button.secundario {
        background: #e5e7eb;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
        cursor: pointer;
      }
    `,
  ],
})
export class FabricacionesComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  fabricaciones = signal<FabricacionResumen[]>([]);
  cargando = signal(true);
  guardando = signal(false);
  error = signal<string | null>(null);
  fabricacionEliminando = signal<FabricacionResumen | null>(null);
  textoConfirmacionEliminar = '';

  fraseEliminarEsperada = () => {
    const f = this.fabricacionEliminando();
    return f ? textoConfirmacionEliminarFabricacion(f.codigoFabricacion) : '';
  };

  ngOnInit(): void {
    this.cargar();
  }

  private idGranja(): string {
    return this.route.parent!.snapshot.paramMap.get('idGranja')!;
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.getFabricaciones(this.idGranja()).subscribe({
      next: (lista) => {
        this.fabricaciones.set(lista);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cargar las fabricaciones'));
        this.cargando.set(false);
      },
    });
  }

  iniciarEliminar(f: FabricacionResumen): void {
    this.fabricacionEliminando.set(f);
    this.textoConfirmacionEliminar = '';
    this.error.set(null);
  }

  cancelarEliminar(): void {
    this.fabricacionEliminando.set(null);
    this.textoConfirmacionEliminar = '';
  }

  puedeConfirmarEliminar(): boolean {
    return this.textoConfirmacionEliminar === this.fraseEliminarEsperada();
  }

  confirmarEliminar(): void {
    const f = this.fabricacionEliminando();
    if (!f || !this.puedeConfirmarEliminar()) return;
    this.guardando.set(true);
    this.api.eliminarFabricacion(this.idGranja(), f.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cancelarEliminar();
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la fabricacion'));
      },
    });
  }
}
