import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Animal } from '../../../data/models/animal.model';
import { MateriaPrima } from '../../../data/models/materia-prima.model';
import {
  FormulaCompleta,
  kilosFaltantes,
  lineaDesdeFormula,
  lineaFormulaVacia,
  LineaFormulaUi,
  PESO_LOTE_FORMULA_KG,
  recalcularLineaFormula,
  redondearFormula,
  sumaKgCompleta,
} from '../../../data/models/formula.model';
import { GRANJA_VISTA_STYLES } from '../shared/granja-vista.styles';
import { OrdenTabla } from '../../../shared/orden-tabla';

@Component({
  selector: 'app-formula-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe],
  template: `
    @if (cargando()) {
      <p>Cargando formula…</p>
    } @else if (formula()) {
      <header class="pagina-cabecera">
        <a routerLink="../" class="back" (click)="intentarVolver($event)">← Volver al listado</a>
        <h2>Formula {{ formula()!.codigoFormula }}</h2>
      </header>

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
                [disabled]="guardandoCabecera() || !puedeGuardarCabecera()"
                (click)="guardarCabecera()"
              >
                Guardar
              </button>
              <button
                type="button"
                class="secundario"
                [disabled]="guardandoCabecera()"
                (click)="cancelarEdicionCabecera()"
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
              <dd>{{ formula()!.codigoFormula }}</dd>
            </div>
            <div>
              <dt>Descripcion</dt>
              <dd>{{ formula()!.descripcionFormula }}</dd>
            </div>
            <div>
              <dt>Animal</dt>
              <dd>{{ formula()!.descripcionAnimal }} ({{ formula()!.codigoAnimal }})</dd>
            </div>
            <div>
              <dt>Costo de fabricacion</dt>
              <dd>$ {{ formula()!.costoTotalFormula | number: '1.2-2' }}</dd>
            </div>
          </dl>
        } @else {
          <div class="formulario-cabecera">
            <label>
              Codigo
              <input [(ngModel)]="codigoFormula" autocomplete="off" />
            </label>
            <label>
              Descripcion
              <input [(ngModel)]="descripcionFormula" autocomplete="off" />
            </label>
            <label class="autocomplete">
              Animal
              <input
                [(ngModel)]="nombreAnimal"
                (ngModelChange)="onNombreAnimalChange($event)"
                (focus)="mostrarAnimales.set(true)"
                (blur)="cerrarAnimales()"
                autocomplete="off"
              />
              @if (mostrarAnimales() && animalesFiltrados().length) {
                <ul class="dropdown">
                  @for (a of animalesFiltrados(); track a.id) {
                    <li (mousedown)="seleccionarAnimal(a)">{{ a.descripcionAnimal }}</li>
                  }
                </ul>
              }
            </label>
          </div>
          @if (errorCabecera()) {
            <p class="error">{{ errorCabecera() }}</p>
          }
        }
      </section>

      <section class="panel">
        <div class="panel-header">
          <h3>Detalle (ingredientes · lote {{ PESO_LOTE }} kg)</h3>
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
                [disabled]="guardandoDetalle() || !puedeGuardarDetalle()"
                (click)="guardarDetalle()"
              >
                Guardar
              </button>
              <button
                type="button"
                class="secundario"
                [disabled]="guardandoDetalle()"
                (click)="cancelarEdicionDetalle()"
              >
                Cancelar
              </button>
            }
          </div>
        </div>

        @if (!editandoDetalle()) {
          @if (lineas().length) {
            <table class="tabla-vista">
              <thead>
                <tr>
                  <th class="sortable" [class.is-asc]="orden.esAsc('codigo')" [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Codigo MP</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('nombre')" [class.is-desc]="orden.esDesc('nombre')" (click)="orden.alternar('nombre')">Materia prima</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('cantidad')" [class.is-desc]="orden.esDesc('cantidad')" (click)="orden.alternar('cantidad')">Cantidad (kg)</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('precio')" [class.is-desc]="orden.esDesc('precio')" (click)="orden.alternar('precio')">Precio/kg</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('subtotal')" [class.is-desc]="orden.esDesc('subtotal')" (click)="orden.alternar('subtotal')">Subtotal</th>
                </tr>
              </thead>
              <tbody>
                @for (linea of lineasOrdenadas(); track $index) {
                  <tr>
                    <td>{{ linea.codigo }}</td>
                    <td>{{ linea.nombre }}</td>
                    <td>{{ linea.cantidadKg | number: '1.2-2' }}</td>
                    <td>$ {{ linea.precioPorKilo | number: '1.2-2' }}</td>
                    <td>$ {{ linea.costoParcial | number: '1.2-2' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <p class="hint">Sin ingredientes cargados.</p>
          }

          <footer class="totales-vista">
            <p>
              Suma kg:
              <strong [class.ok]="loteCompleto()" [class.bad]="!loteCompleto()">
                {{ sumaKg() | number: '1.2-2' }}
              </strong>
              / {{ PESO_LOTE }} kg
            </p>
            @if (!loteCompleto() && lineasConMateria().length) {
              <p class="warn">
                Faltan {{ kilosFaltantesUi() | number: '1.2-2' }} kg para completar la formula.
              </p>
            }
            <p>
              Costo total estimado:
              <strong>$ {{ costoTotalUi() | number: '1.2-2' }}</strong>
            </p>
          </footer>
        } @else {
          <p class="hint">
            La suma de cantidades debe ser exactamente {{ PESO_LOTE }} kg para guardar.
          </p>

          @for (linea of lineas(); track $index; let i = $index) {
            <div class="linea">
              <div class="mp-autocomplete">
                <label>
                  Codigo MP
                  <input
                    [(ngModel)]="linea.codigo"
                    (ngModelChange)="onCodigoMpChange(i, $event)"
                    autocomplete="off"
                  />
                </label>
                <label class="autocomplete">
                  Materia prima
                  <input
                    [(ngModel)]="linea.nombre"
                    (ngModelChange)="onNombreMpChange(i, $event)"
                    (focus)="abrirMp(i)"
                    (blur)="cerrarMp(i)"
                    autocomplete="off"
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
                  step="0.01"
                  [(ngModel)]="linea.cantidadKg"
                  (ngModelChange)="onCantidadChange(i, $event)"
                />
              </label>
              <label>
                Precio/kg
                <input type="number" [readOnly]="true" [ngModel]="linea.precioPorKilo" />
              </label>
              <label>
                Subtotal
                <input type="number" [readOnly]="true" [ngModel]="linea.costoParcial" />
              </label>
              <button type="button" class="danger" (click)="quitarLinea(i)">Quitar</button>
            </div>
            @if (linea.advertenciaLinea) {
              <p class="warn-linea">⚠ {{ linea.advertenciaLinea }}</p>
            }
          }

          <button type="button" class="btn-secundario" (click)="agregarLinea()">
            + Agregar ingrediente
          </button>

          <footer class="totales">
            <p>
              Suma kg:
              <strong [class.ok]="loteCompleto()" [class.bad]="!loteCompleto()">
                {{ sumaKg() | number: '1.2-2' }}
              </strong>
              / {{ PESO_LOTE }} kg
            </p>
            @if (!loteCompleto() && lineasConMateria().length) {
              <p class="warn">
                Faltan {{ kilosFaltantesUi() | number: '1.2-2' }} kg para completar la formula.
              </p>
            }
            @if (loteCompleto()) {
              <p class="ok-hint">Lote completo. Guardá para persistir el costo de formula.</p>
            }
            <p>
              Costo total estimado:
              <strong>$ {{ costoTotalUi() | number: '1.2-2' }}</strong>
            </p>
            @if (conflictoDetalle()) {
              <p class="conflicto">{{ conflictoDetalle() }}</p>
            }
            @if (errorDetalle()) {
              <p class="error">{{ errorDetalle() }}</p>
            }
          </footer>
        }
      </section>
    }
  `,
  styles: [
    GRANJA_VISTA_STYLES,
    `
      :host {
        display: block;
      }
      .pagina-cabecera {
        margin-bottom: 0.5rem;
      }
      .formulario-cabecera {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
        gap: 1rem;
      }
      .formulario-cabecera label {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        font-size: 0.85rem;
        color: var(--reforma-text-dim);
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
      input[readonly] {
        background: var(--glass-bg);
        color: var(--reforma-text-dim);
      }
      .tabla-vista {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.9rem;
      }
      .tabla-vista th,
      .tabla-vista td {
        padding: 0.6rem 0.75rem;
        border-bottom: 1px solid var(--glass-border);
        text-align: left;
        color: var(--reforma-text);
      }
      .tabla-vista th {
        font-size: 0.78rem;
        color: var(--reforma-text-dim);
        font-weight: 600;
        background: rgba(255, 255, 255, 0.04);
      }
      .linea {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
        gap: 0.75rem;
        align-items: end;
        padding: 1rem 0;
        border-bottom: 1px solid var(--glass-border);
      }
      .mp-autocomplete {
        grid-column: span 2;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
        gap: 0.75rem;
      }
      .linea label {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        font-size: 0.85rem;
        color: var(--reforma-text-dim);
      }
      .totales,
      .totales-vista {
        margin-top: 1rem;
        padding-top: 1rem;
        border-top: 1px solid var(--glass-border);
        color: var(--reforma-text-dim);
      }
      .totales strong,
      .totales-vista strong {
        color: var(--reforma-text);
      }
      .btn-secundario,
      button.danger {
        padding: 0.5rem 0.9rem;
        border-radius: 8px;
        cursor: pointer;
        font: inherit;
        font-size: 0.85rem;
        font-weight: 600;
        transition: filter 0.12s ease, background 0.12s ease, border-color 0.12s ease;
      }
      .btn-secundario {
        color: var(--reforma-text);
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        margin-top: 0.75rem;
      }
      .btn-secundario:hover:not(:disabled) {
        background: var(--glass-bg-hover);
        border-color: var(--glass-border-strong);
      }
      button.danger {
        color: #fecaca;
        background: rgba(248, 113, 113, 0.12);
        border: 1px solid rgba(248, 113, 113, 0.35);
      }
      button.danger:hover:not(:disabled) {
        background: rgba(248, 113, 113, 0.2);
        border-color: rgba(248, 113, 113, 0.55);
      }
      .warn {
        color: #fde68a;
        font-weight: 600;
      }
      .warn-linea {
        margin: 0.25rem 0 0.5rem;
        color: #fde68a;
        font-size: 0.82rem;
      }
      .ok-hint {
        color: #bbf7d0;
      }
      .ok {
        color: #bbf7d0;
      }
      .bad {
        color: #fecaca;
      }
    `,
  ],
})
export class FormulaDetalleComponent implements OnInit {
  readonly PESO_LOTE = PESO_LOTE_FORMULA_KG;

  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly formula = signal<FormulaCompleta | null>(null);
  readonly lineas = signal<LineaFormulaUi[]>([]);
  readonly orden = new OrdenTabla();
  readonly lineasOrdenadas = computed(() =>
    this.orden.ordenar(this.lineas(), {
      codigo: (l) => l.codigo,
      nombre: (l) => l.nombre,
      cantidad: (l) => l.cantidadKg,
      precio: (l) => l.precioPorKilo,
      subtotal: (l) => l.costoParcial,
    }),
  );
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly animales = signal<Animal[]>([]);
  readonly mpAbiertoIndex = signal<number | null>(null);
  readonly mostrarAnimales = signal(false);
  readonly idAnimalSeleccionado = signal<number | null>(null);

  readonly cargando = signal(true);
  readonly guardandoCabecera = signal(false);
  readonly guardandoDetalle = signal(false);
  readonly errorCabecera = signal<string | null>(null);
  readonly errorDetalle = signal<string | null>(null);
  readonly conflictoDetalle = signal<string | null>(null);

  readonly editandoCabecera = signal(false);
  readonly editandoDetalle = signal(false);
  readonly hayCambiosSinGuardar = signal(false);

  codigoFormula = '';
  descripcionFormula = '';
  nombreAnimal = '';

  private sincronizandoMp = false;

  readonly lineasConMateria = computed(() =>
    this.lineas().filter((l) => l.idMateriaPrima != null && l.cantidadKg > 0),
  );

  readonly sumaKg = computed(() =>
    redondearFormula(this.lineasConMateria().reduce((s, l) => s + l.cantidadKg, 0)),
  );

  readonly kilosFaltantesUi = computed(() => kilosFaltantes(this.sumaKg()));

  readonly costoTotalUi = computed(() =>
    redondearFormula(this.lineasConMateria().reduce((s, l) => s + l.costoParcial, 0)),
  );

  readonly loteCompleto = computed(() => sumaKgCompleta(this.sumaKg()));

  readonly animalesFiltrados = computed(() => {
    const t = this.nombreAnimal.trim().toLowerCase();
    if (!t) return this.animales();
    return this.animales().filter((a) => a.descripcionAnimal.toLowerCase().includes(t));
  });

  readonly puedeGuardarCabecera = computed(() => {
    return (
      this.codigoFormula.trim().length > 0 &&
      this.descripcionFormula.trim().length > 0 &&
      this.idAnimalSeleccionado() != null
    );
  });

  readonly puedeGuardarDetalle = computed(() => {
    return this.loteCompleto() && this.lineasConMateria().length > 0;
  });

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  private get idFormula(): string {
    return this.route.snapshot.paramMap.get('idFormula') ?? '';
  }

  ngOnInit(): void {
    this.api.getMateriasPrimas(this.idGranja).subscribe({
      next: (list) => this.materiasPrimas.set(list),
    });
    this.api.getAnimales(this.idGranja).subscribe({
      next: (list) => this.animales.set(list),
    });
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.api.getFormula(this.idGranja, this.idFormula).subscribe({
      next: (f) => {
        this.formula.set(f);
        this.restaurarLineasDesdeFormula(f);
        this.hayCambiosSinGuardar.set(false);
        this.cargando.set(false);
      },
      error: () => {
        this.errorDetalle.set('No se pudo cargar la formula');
        this.cargando.set(false);
      },
    });
  }

  iniciarEdicionCabecera(): void {
    const f = this.formula();
    if (!f) return;
    this.codigoFormula = f.codigoFormula;
    this.descripcionFormula = f.descripcionFormula;
    this.nombreAnimal = f.descripcionAnimal;
    this.idAnimalSeleccionado.set(f.idAnimal);
    this.errorCabecera.set(null);
    this.editandoCabecera.set(true);
  }

  cancelarEdicionCabecera(): void {
    this.editandoCabecera.set(false);
    this.errorCabecera.set(null);
    this.mostrarAnimales.set(false);
  }

  guardarCabecera(): void {
    if (!this.puedeGuardarCabecera()) return;
    const idAnimal = this.idAnimalSeleccionado();
    if (!idAnimal) return;

    this.guardandoCabecera.set(true);
    this.errorCabecera.set(null);
    this.api
      .actualizarFormulaCabecera(this.idGranja, this.idFormula, {
        codigoFormula: this.codigoFormula.trim(),
        descripcionFormula: this.descripcionFormula.trim(),
        idAnimal,
      })
      .subscribe({
        next: () => {
          this.guardandoCabecera.set(false);
          this.editandoCabecera.set(false);
          this.cargar();
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoCabecera.set(false);
          this.errorCabecera.set(mensajeErrorHttp(err, 'No se pudo guardar la cabecera'));
        },
      });
  }

  iniciarEdicionDetalle(): void {
    const f = this.formula();
    if (this.lineas().length === 0) {
      if (f && f.lineas.length > 0) {
        this.restaurarLineasDesdeFormula(f);
      } else {
        this.lineas.set([lineaFormulaVacia()]);
      }
    }
    this.conflictoDetalle.set(null);
    this.errorDetalle.set(null);
    this.hayCambiosSinGuardar.set(false);
    this.editandoDetalle.set(true);
  }

  cancelarEdicionDetalle(): void {
    const f = this.formula();
    if (f) this.restaurarLineasDesdeFormula(f);
    this.editandoDetalle.set(false);
    this.conflictoDetalle.set(null);
    this.errorDetalle.set(null);
    this.hayCambiosSinGuardar.set(false);
    this.mpAbiertoIndex.set(null);
  }

  guardarDetalle(): void {
    if (!this.loteCompleto()) {
      this.conflictoDetalle.set(
        `Completá el detalle: la suma de ingredientes debe ser exactamente ${PESO_LOTE_FORMULA_KG} kg. Faltan ${this.kilosFaltantesUi()} kg.`,
      );
      return;
    }
    if (!this.puedeGuardarDetalle()) return;

    this.guardandoDetalle.set(true);
    this.conflictoDetalle.set(null);
    this.errorDetalle.set(null);
    this.api
      .guardarFormulaDetalle(this.idGranja, this.idFormula, {
        lineas: this.lineasConMateria().map((l) => ({
          idMateriaPrima: l.idMateriaPrima!,
          cantidadKg: l.cantidadKg,
        })),
      })
      .subscribe({
        next: () => {
          this.guardandoDetalle.set(false);
          this.editandoDetalle.set(false);
          this.hayCambiosSinGuardar.set(false);
          this.cargar();
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoDetalle.set(false);
          this.errorDetalle.set(mensajeErrorHttp(err, 'No se pudo guardar el detalle'));
        },
      });
  }

  cerrarAnimales(): void {
    setTimeout(() => this.mostrarAnimales.set(false), 150);
  }

  onNombreAnimalChange(nombre: string): void {
    this.mostrarAnimales.set(true);
    const a = this.animales().find(
      (x) => x.descripcionAnimal.toLowerCase() === nombre.trim().toLowerCase(),
    );
    this.idAnimalSeleccionado.set(a?.id ?? null);
  }

  seleccionarAnimal(a: Animal): void {
    this.idAnimalSeleccionado.set(a.id);
    this.nombreAnimal = a.descripcionAnimal;
    this.mostrarAnimales.set(false);
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

  abrirMp(i: number): void {
    this.mpAbiertoIndex.set(i);
  }

  cerrarMp(i: number): void {
    setTimeout(() => {
      if (this.mpAbiertoIndex() === i) this.mpAbiertoIndex.set(null);
    }, 150);
  }

  onCodigoMpChange(i: number, codigo: string): void {
    if (this.sincronizandoMp) return;
    this.marcarModificado();
    const lineas = [...this.lineas()];
    const mp = this.materiasPrimas().find(
      (m) => m.codigoMateriaPrima.toLowerCase() === codigo.trim().toLowerCase(),
    );
    if (mp) this.aplicarMp(lineas, i, mp);
    else lineas[i].idMateriaPrima = null;
    this.lineas.set(lineas);
  }

  onNombreMpChange(i: number, nombre: string): void {
    if (this.sincronizandoMp) return;
    this.marcarModificado();
    this.mpAbiertoIndex.set(i);
    const lineas = [...this.lineas()];
    const mp = this.materiasPrimas().find(
      (m) => m.nombreMateriaPrima.toLowerCase() === nombre.trim().toLowerCase(),
    );
    if (mp) this.aplicarMp(lineas, i, mp);
    else lineas[i].idMateriaPrima = null;
    this.lineas.set(lineas);
  }

  seleccionarMp(i: number, mp: MateriaPrima): void {
    this.marcarModificado();
    const lineas = [...this.lineas()];
    this.aplicarMp(lineas, i, mp);
    this.lineas.set(lineas);
    this.mpAbiertoIndex.set(null);
  }

  onCantidadChange(i: number, valor: number | null): void {
    this.marcarModificado();
    const lineas = [...this.lineas()];
    lineas[i].cantidadKg = valor != null && !Number.isNaN(valor) ? redondearFormula(valor) : 0;
    lineas[i] = recalcularLineaFormula(lineas[i]);
    this.lineas.set(lineas);
  }

  agregarLinea(): void {
    this.marcarModificado();
    this.lineas.update((p) => [...p, lineaFormulaVacia()]);
  }

  quitarLinea(i: number): void {
    this.marcarModificado();
    this.lineas.update((p) => p.filter((_, idx) => idx !== i));
  }

  intentarVolver(event: Event): void {
    if (!this.puedeSalir()) event.preventDefault();
  }

  puedeSalir(): boolean {
    return !(this.editandoDetalle() && this.hayCambiosSinGuardar());
  }

  mensajeBloqueoSalida(): string {
    if (this.editandoDetalle() && this.hayCambiosSinGuardar()) {
      return 'Tenés cambios sin guardar en el detalle. Guardá o cancelá la edición antes de salir.';
    }
    return 'No podés salir todavía.';
  }

  private marcarModificado(): void {
    if (this.editandoDetalle()) {
      this.hayCambiosSinGuardar.set(true);
    }
  }

  private restaurarLineasDesdeFormula(f: FormulaCompleta): void {
    if (f.lineas.length > 0) {
      this.lineas.set(f.lineas.map(lineaDesdeFormula));
    } else {
      this.lineas.set([]);
    }
  }

  private aplicarMp(lineas: LineaFormulaUi[], index: number, mp: MateriaPrima): void {
    this.sincronizandoMp = true;
    const linea = lineas[index];
    linea.idMateriaPrima = mp.id;
    linea.codigo = mp.codigoMateriaPrima;
    linea.nombre = mp.nombreMateriaPrima;
    linea.precioPorKilo = redondearFormula(mp.precioPorKilo);
    lineas[index] = recalcularLineaFormula(linea);
    this.sincronizandoMp = false;
  }
}
