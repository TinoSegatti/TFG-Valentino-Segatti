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
import { OrdenTabla } from '../../../shared/orden-tabla';

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
                <th class="sortable" [class.is-asc]="orden.esAsc('mp')" [class.is-desc]="orden.esDesc('mp')" (click)="orden.alternar('mp')">MP</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('cantidad')" [class.is-desc]="orden.esDesc('cantidad')" (click)="orden.alternar('cantidad')">Cantidad usada (kg)</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('precio')" [class.is-desc]="orden.esDesc('precio')" (click)="orden.alternar('precio')">Precio unit. (snapshot)</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('costo')" [class.is-desc]="orden.esDesc('costo')" (click)="orden.alternar('costo')">Costo parcial</th>
              </tr>
            </thead>
            <tbody>
              @for (l of lineasOrdenadas(); track l.id) {
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
        max-width: 60rem;
      }
      .formulario-cabecera {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
        gap: 1rem;
        max-width: 32rem;
      }
      .formulario-cabecera label,
      .fila-formula label {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        font-size: 0.85rem;
        color: var(--reforma-text-dim);
      }
      .fila-formula {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
        gap: 1rem;
        margin-bottom: 1rem;
      }
      .autocomplete {
        position: relative;
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
      .incompleta {
        color: #fde68a;
        font-size: 0.8rem;
      }
      .subtitulo {
        margin: 1rem 0 0.5rem;
        font-size: 0.95rem;
        color: var(--reforma-text);
        font-weight: 600;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        margin: 0.5rem 0 1rem;
        font-size: 0.9rem;
      }
      th,
      td {
        padding: 0.6rem 0.75rem;
        border-bottom: 1px solid var(--glass-border);
        text-align: left;
        color: var(--reforma-text);
      }
      th {
        font-size: 0.78rem;
        color: var(--reforma-text-dim);
        font-weight: 600;
        background: rgba(255, 255, 255, 0.04);
      }
      .totales {
        margin-top: 1rem;
        padding-top: 1rem;
        border-top: 1px solid var(--glass-border);
        color: var(--reforma-text-dim);
      }
      .totales strong {
        color: var(--reforma-text);
      }
      .hint-inline {
        color: var(--reforma-text-faint);
        font-size: 0.85rem;
        margin-left: 0.4rem;
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

  readonly orden = new OrdenTabla();
  lineas = computed(() => this.fabricacion()?.lineas ?? []);
  lineasOrdenadas = computed(() =>
    this.orden.ordenar(this.lineas(), {
      mp: (l) => l.nombreMateriaPrima,
      cantidad: (l) => l.cantidadUsada,
      precio: (l) => l.precioUnitario,
      costo: (l) => l.costoParcial,
    }),
  );
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
