import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import {
  FabricacionCompleta,
  hoyIsoFabricacion,
} from '../../../data/models/fabricacion.model';
import { FormulaResumen } from '../../../data/models/formula.model';
import { GRANJA_VISTA_STYLES } from '../shared/granja-vista.styles';

@Component({
  selector: 'app-fabricacion-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe],
  template: `
    @if (cargando()) {
      <p>Cargando fabricacion…</p>
    } @else if (fabricacion()) {
      <a routerLink="../" class="back">← Volver al listado</a>
      <h2>Fabricacion {{ fabricacion()!.codigoFabricacion }}</h2>

      <section class="panel">
        <div class="panel-header">
          <h3>Cabecera</h3>
          <div class="panel-acciones">
            @if (!editandoCabecera()) {
              <button
                type="button"
                class="secundario"
                [disabled]="editandoDetalle()"
                (click)="iniciarEdicionCabecera()"
              >
                Editar
              </button>
            } @else {
              <button
                type="button"
                class="primario"
                [disabled]="!puedeGuardarCabecera() || guardandoCabecera()"
                (click)="guardarCabecera()"
              >
                Guardar
              </button>
              <button
                type="button"
                class="secundario"
                [disabled]="guardandoCabecera()"
                (click)="cancelarCabecera()"
              >
                Cancelar
              </button>
            }
          </div>
        </div>

        @if (!editandoCabecera()) {
          <dl class="vista-datos">
            <div>
              <dt>Codigo</dt>
              <dd>{{ fabricacion()!.codigoFabricacion }}</dd>
            </div>
            <div>
              <dt>Fecha</dt>
              <dd>{{ fabricacion()!.fechaFabricacion }}</dd>
            </div>
            <div>
              <dt>Descripcion</dt>
              <dd>{{ fabricacion()!.descripcionFabricacion }}</dd>
            </div>
            @if (fabricacion()!.observaciones) {
              <div>
                <dt>Observaciones</dt>
                <dd>{{ fabricacion()!.observaciones }}</dd>
              </div>
            }
          </dl>
        } @else {
          <div class="formulario-cabecera">
            <label>
              Codigo
              <input
                name="codigo"
                [(ngModel)]="codigoFabricacion"
                required
                maxlength="50"
              />
            </label>
            <label>
              Fecha
              <input
                type="date"
                name="fecha"
                [(ngModel)]="fechaFabricacion"
                [max]="hoy"
                required
              />
            </label>
            <label>
              Descripcion
              <input
                name="descripcion"
                [(ngModel)]="descripcionFabricacion"
                required
                maxlength="200"
              />
            </label>
            <label>
              Observaciones
              <input name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
            </label>
          </div>
        }

        @if (errorCabecera()) {
          <p class="error">{{ errorCabecera() }}</p>
        }
      </section>

      <section class="panel">
        <div class="panel-header">
          <h3>Detalle</h3>
          <div class="panel-acciones">
            @if (!editandoDetalle()) {
              <button
                type="button"
                class="secundario"
                [disabled]="editandoCabecera()"
                (click)="iniciarEdicionDetalle()"
              >
                Editar
              </button>
            } @else {
              <button
                type="button"
                class="primario"
                [disabled]="!puedeGuardarDetalle() || guardandoDetalle()"
                (click)="guardarDetalle()"
              >
                Guardar
              </button>
              <button
                type="button"
                class="secundario"
                [disabled]="guardandoDetalle()"
                (click)="cancelarDetalle()"
              >
                Cancelar
              </button>
            }
          </div>
        </div>

        <p class="hint">
          1 vez = lote de 1000 kg de producto. Ej.: 3,5 veces con 500 kg de soja descuenta 1750 kg del
          stock.
        </p>

        @if (editandoDetalle()) {
          <div class="fila-formula">
            <label>
              Codigo formula
              <input
                [(ngModel)]="codigoFormula"
                (ngModelChange)="onCodigoFormulaChange($event)"
                autocomplete="off"
              />
            </label>
            <label class="autocomplete">
              Descripcion formula
              <input
                [(ngModel)]="descripcionFormula"
                (ngModelChange)="onDescripcionFormulaChange($event)"
                (focus)="mostrarFormulas.set(true)"
                (blur)="cerrarFormulas()"
                autocomplete="off"
              />
              @if (mostrarFormulas() && formulasFiltradas().length) {
                <ul class="dropdown">
                  @for (f of formulasFiltradas(); track f.id) {
                    <li (mousedown)="seleccionarFormula(f)">
                      {{ f.descripcionFormula }} ({{ f.codigoFormula }})
                      @if (!f.completa) {
                        <span class="incompleta"> — incompleta</span>
                      }
                    </li>
                  }
                </ul>
              }
            </label>
            <label>
              Veces a fabricar
              <input
                type="number"
                step="0.001"
                min="0.001"
                [(ngModel)]="veces"
                (ngModelChange)="onVecesChange()"
              />
            </label>
          </div>
        } @else {
          <dl class="vista-datos formula-readonly">
            <div>
              <dt>Formula</dt>
              <dd>
                @if (fabricacion()!.codigoFormula) {
                  {{ fabricacion()!.codigoFormula }} — {{ fabricacion()!.descripcionFormula }}
                } @else {
                  —
                }
              </dd>
            </div>
            <div>
              <dt>Veces</dt>
              <dd>{{ fabricacion()!.veces | number: '1.3-3' }}</dd>
            </div>
            <div>
              <dt>Kg producidos</dt>
              <dd>{{ fabricacion()!.kilosProducidos | number: '1.3-3' }} kg</dd>
            </div>
          </dl>
        }

        @if (fabricacion()!.sinExistencias) {
          <p class="conflicto">Advertencia: stock insuficiente en al menos una materia prima.</p>
        }

        @if (lineas().length) {
          <h4 class="subtitulo">Materias primas consumidas</h4>
          <table>
            <thead>
              <tr>
                <th>MP</th>
                <th>Cantidad usada (kg)</th>
                <th>Precio unit. (snapshot)</th>
                <th>Costo parcial</th>
              </tr>
            </thead>
            <tbody>
              @for (l of lineas(); track l.id) {
                <tr>
                  <td>{{ l.nombreMateriaPrima }} ({{ l.codigoMateriaPrima }})</td>
                  <td>{{ l.cantidadUsada | number: '1.3-3' }}</td>
                  <td>$ {{ l.precioUnitario | number: '1.3-3' }}</td>
                  <td>$ {{ l.costoParcial | number: '1.3-3' }}</td>
                </tr>
              }
            </tbody>
          </table>
        }

        <footer class="totales">
          @if (costoUnitarioUi() != null) {
            <p>
              Costo unitario formula (al registrar):
              <strong>$ {{ costoUnitarioUi()! | number: '1.3-3' }}</strong>
            </p>
          }
          <p>
            Costo total fabricacion:
            <strong>$ {{ costoTotalUi() | number: '1.3-3' }}</strong>
            <span class="hint-inline">(no se actualiza si cambian precios de MP)</span>
          </p>
        </footer>

        @if (errorDetalle()) {
          <p class="error">{{ errorDetalle() }}</p>
        }
      </section>
    }
  `,
  styles: [
    GRANJA_VISTA_STYLES,
    `
      :host {
        display: block;
        max-width: 960px;
      }
      .formulario-cabecera {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        max-width: 480px;
      }
      .formulario-cabecera label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      .fila-formula {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        margin-bottom: 1rem;
      }
      .fila-formula label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        min-width: 180px;
      }
      .autocomplete {
        position: relative;
        flex: 1;
        min-width: 240px;
      }
      .dropdown {
        position: absolute;
        z-index: 10;
        list-style: none;
        margin: 0;
        padding: 0;
        background: white;
        border: 1px solid #d1d5db;
        width: 100%;
        max-height: 200px;
        overflow-y: auto;
      }
      .dropdown li {
        padding: 0.5rem;
        cursor: pointer;
      }
      .dropdown li:hover {
        background: #f3f4f6;
      }
      .incompleta {
        color: #b45309;
        font-size: 0.8rem;
      }
      .subtitulo {
        margin: 1rem 0 0.5rem;
        font-size: 0.95rem;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        margin: 0.5rem 0 1rem;
      }
      th,
      td {
        padding: 0.5rem;
        border-bottom: 1px solid #e5e7eb;
        text-align: left;
      }
      .totales {
        margin-top: 1rem;
        padding-top: 1rem;
        border-top: 1px solid #e5e7eb;
      }
      .hint-inline {
        color: #6b7280;
        font-size: 0.85rem;
      }
      .formula-readonly {
        margin-bottom: 0.5rem;
      }
    `,
  ],
})
export class FabricacionDetalleComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  fabricacion = signal<FabricacionCompleta | null>(null);
  formulasCatalogo = signal<FormulaResumen[]>([]);
  cargando = signal(true);
  guardandoCabecera = signal(false);
  guardandoDetalle = signal(false);
  errorCabecera = signal<string | null>(null);
  errorDetalle = signal<string | null>(null);
  editandoCabecera = signal(false);
  editandoDetalle = signal(false);

  codigoFabricacion = '';
  descripcionFabricacion = '';
  fechaFabricacion = '';
  observaciones = '';
  hoy = hoyIsoFabricacion();

  codigoFormula = '';
  descripcionFormula = '';
  idFormulaSeleccionada = signal<string | null>(null);
  veces = 1;
  mostrarFormulas = signal(false);

  lineas = computed(() => this.fabricacion()?.lineas ?? []);
  costoUnitarioUi = computed(() => {
    const fab = this.fabricacion();
    if (!fab) return null;
    if (fab.costoUnitarioFormula != null) return fab.costoUnitarioFormula;
    const sel = this.formulaSeleccionada();
    return sel?.costoTotalFormula ?? null;
  });
  costoTotalUi = computed(() => {
    const unit = this.costoUnitarioUi();
    if (unit == null || this.veces <= 0) {
      return this.fabricacion()?.costoTotalFabricacion ?? 0;
    }
    return Math.round(unit * this.veces * 1000) / 1000;
  });

  formulasFiltradas = computed(() => {
    const q = this.descripcionFormula.trim().toLowerCase();
    const lista = this.formulasCatalogo();
    if (!q) return lista.slice(0, 12);
    return lista
      .filter((f) => f.descripcionFormula.toLowerCase().includes(q))
      .slice(0, 12);
  });

  ngOnInit(): void {
    const idGranja = this.route.parent!.snapshot.paramMap.get('idGranja')!;
    const idFabricacion = this.route.snapshot.paramMap.get('idFabricacion')!;

    this.api.getFormulas(idGranja).subscribe({
      next: (formulas) => this.formulasCatalogo.set(formulas),
    });

    this.api.getFabricacion(idGranja, idFabricacion).subscribe({
      next: (fab) => {
        this.fabricacion.set(fab);
        this.aplicarFabricacionAlFormulario(fab);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorDetalle.set(mensajeErrorHttp(err, 'No se pudo cargar la fabricacion'));
        this.cargando.set(false);
      },
    });
  }

  iniciarEdicionCabecera(): void {
    const fab = this.fabricacion();
    if (!fab) return;
    this.aplicarCabeceraDesdeFabricacion(fab);
    this.errorCabecera.set(null);
    this.editandoCabecera.set(true);
  }

  cancelarCabecera(): void {
    const fab = this.fabricacion();
    if (fab) this.aplicarCabeceraDesdeFabricacion(fab);
    this.errorCabecera.set(null);
    this.editandoCabecera.set(false);
  }

  puedeGuardarCabecera(): boolean {
    return (
      this.codigoFabricacion.trim().length > 0 &&
      this.descripcionFabricacion.trim().length > 0 &&
      this.fechaFabricacion.length > 0
    );
  }

  guardarCabecera(): void {
    if (!this.puedeGuardarCabecera()) return;
    const idGranja = this.route.parent!.snapshot.paramMap.get('idGranja')!;
    const idFabricacion = this.route.snapshot.paramMap.get('idFabricacion')!;

    this.guardandoCabecera.set(true);
    this.errorCabecera.set(null);
    this.api
      .actualizarFabricacionCabecera(idGranja, idFabricacion, {
        codigoFabricacion: this.codigoFabricacion.trim(),
        descripcionFabricacion: this.descripcionFabricacion.trim(),
        fechaFabricacion: this.fechaFabricacion,
        observaciones: this.observaciones.trim() || undefined,
      })
      .subscribe({
        next: (fab) => {
          this.fabricacion.set(fab);
          this.aplicarCabeceraDesdeFabricacion(fab);
          this.guardandoCabecera.set(false);
          this.editandoCabecera.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoCabecera.set(false);
          this.errorCabecera.set(mensajeErrorHttp(err, 'No se pudo guardar la cabecera'));
        },
      });
  }

  iniciarEdicionDetalle(): void {
    const fab = this.fabricacion();
    if (!fab) return;
    this.aplicarDetalleDesdeFabricacion(fab);
    this.errorDetalle.set(null);
    this.editandoDetalle.set(true);
  }

  cancelarDetalle(): void {
    const fab = this.fabricacion();
    if (fab) this.aplicarDetalleDesdeFabricacion(fab);
    this.errorDetalle.set(null);
    this.editandoDetalle.set(false);
  }

  formulaSeleccionada(): FormulaResumen | undefined {
    const id = this.idFormulaSeleccionada();
    return this.formulasCatalogo().find((f) => f.id === id);
  }

  onCodigoFormulaChange(valor: string): void {
    const codigo = valor.trim().toLowerCase();
    const match = this.formulasCatalogo().find((f) => f.codigoFormula.toLowerCase() === codigo);
    if (match) {
      this.idFormulaSeleccionada.set(match.id);
      this.descripcionFormula = match.descripcionFormula;
    } else {
      this.idFormulaSeleccionada.set(null);
    }
  }

  onDescripcionFormulaChange(valor: string): void {
    const q = valor.trim().toLowerCase();
    const exact = this.formulasCatalogo().find(
      (f) => f.descripcionFormula.toLowerCase() === q,
    );
    if (exact) {
      this.idFormulaSeleccionada.set(exact.id);
      this.codigoFormula = exact.codigoFormula;
    }
  }

  seleccionarFormula(f: FormulaResumen): void {
    this.idFormulaSeleccionada.set(f.id);
    this.codigoFormula = f.codigoFormula;
    this.descripcionFormula = f.descripcionFormula;
    this.mostrarFormulas.set(false);
  }

  cerrarFormulas(): void {
    setTimeout(() => this.mostrarFormulas.set(false), 150);
  }

  onVecesChange(): void {
    if (this.veces < 0) this.veces = 0;
  }

  puedeGuardarDetalle(): boolean {
    const formula = this.formulaSeleccionada();
    return !!formula?.completa && this.veces > 0 && !!this.idFormulaSeleccionada();
  }

  guardarDetalle(): void {
    const idGranja = this.route.parent!.snapshot.paramMap.get('idGranja')!;
    const idFabricacion = this.route.snapshot.paramMap.get('idFabricacion')!;
    const idFormula = this.idFormulaSeleccionada();
    if (!idFormula || this.veces <= 0) return;

    this.guardandoDetalle.set(true);
    this.errorDetalle.set(null);
    this.api
      .guardarFabricacionDetalle(idGranja, idFabricacion, {
        idFormula,
        veces: this.veces,
      })
      .subscribe({
        next: (fab) => {
          this.fabricacion.set(fab);
          this.aplicarDetalleDesdeFabricacion(fab);
          this.guardandoDetalle.set(false);
          this.editandoDetalle.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoDetalle.set(false);
          this.errorDetalle.set(mensajeErrorHttp(err, 'No se pudo guardar el detalle'));
        },
      });
  }

  private aplicarFabricacionAlFormulario(fab: FabricacionCompleta): void {
    this.aplicarCabeceraDesdeFabricacion(fab);
    this.aplicarDetalleDesdeFabricacion(fab);
  }

  private aplicarCabeceraDesdeFabricacion(fab: FabricacionCompleta): void {
    this.codigoFabricacion = fab.codigoFabricacion;
    this.descripcionFabricacion = fab.descripcionFabricacion;
    this.fechaFabricacion = fab.fechaFabricacion;
    this.observaciones = fab.observaciones ?? '';
  }

  private aplicarDetalleDesdeFabricacion(fab: FabricacionCompleta): void {
    this.codigoFormula = fab.codigoFormula ?? '';
    this.descripcionFormula = fab.descripcionFormula ?? '';
    this.idFormulaSeleccionada.set(fab.idFormula);
    this.veces = fab.veces > 0 ? fab.veces : 1;
  }
}
