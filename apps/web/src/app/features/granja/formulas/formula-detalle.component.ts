import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
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

@Component({
  selector: 'app-formula-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe],
  template: `
    @if (cargando()) {
      <p>Cargando formula…</p>
    } @else if (formula()) {
      <header class="cabecera-readonly">
        <a routerLink="../" class="back" (click)="intentarVolver($event)">← Volver al listado</a>
        <h2>
          {{ soloLectura() ? 'Ver' : 'Editar' }} formula {{ formula()!.codigoFormula }}
        </h2>
        <dl>
          <div><dt>Descripcion</dt><dd>{{ formula()!.descripcionFormula }}</dd></div>
          <div><dt>Animal</dt><dd>{{ formula()!.descripcionAnimal }} ({{ formula()!.codigoAnimal }})</dd></div>
          <div>
            <dt>Costo de fabricacion</dt>
            <dd>$ {{ costoTotalUi() | number: '1.3-3' }}</dd>
          </div>
        </dl>
        @if (!soloLectura()) {
          <a [routerLink]="['editar']" class="link-editar">Editar cabecera</a>
        }
      </header>

      <section class="detalle" [class.solo-lectura]="soloLectura()">
        <h3>Ingredientes (lote {{ PESO_LOTE }} kg)</h3>
        @if (!soloLectura()) {
          <p class="hint">
            La suma de cantidades debe ser exactamente {{ PESO_LOTE }} kg para guardar y salir.
          </p>
        }

        @for (linea of lineas(); track $index; let i = $index) {
          <div class="linea">
            @if (!soloLectura()) {
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
                  step="0.001"
                  [(ngModel)]="linea.cantidadKg"
                  (ngModelChange)="onCantidadChange(i, $event)"
                />
              </label>
            } @else {
              <span class="mp-readonly">{{ linea.nombre }} ({{ linea.codigo }})</span>
              <span>{{ linea.cantidadKg | number: '1.3-3' }} kg</span>
            }
            <label>
              Precio/kg
              <input
                type="number"
                [readOnly]="true"
                [ngModel]="linea.precioPorKilo"
              />
            </label>
            <label>
              Subtotal
              <input type="number" [readOnly]="true" [ngModel]="linea.costoParcial" />
            </label>
            @if (!soloLectura()) {
              <button type="button" class="danger" (click)="quitarLinea(i)">Quitar</button>
            }
          </div>
        }

        @if (!soloLectura()) {
          <button type="button" class="secundario" (click)="agregarLinea()">+ Agregar ingrediente</button>
        }

        <footer class="totales">
          <p>
            Suma kg:
            <strong [class.ok]="loteCompleto()" [class.bad]="!loteCompleto()">
              {{ sumaKg() | number: '1.3-3' }}
            </strong>
            / {{ PESO_LOTE }} kg
          </p>
          @if (!loteCompleto() && lineasConMateria().length) {
            <p class="warn">Faltan {{ kilosFaltantesUi() | number: '1.3-3' }} kg para completar la formula.</p>
          }
          @if (loteCompleto() && !soloLectura()) {
            <p class="ok-hint">Lote completo. Guardá para persistir el costo de formula.</p>
          }
          <p>
            Costo total estimado:
            <strong>$ {{ costoTotalUi() | number: '1.3-3' }}</strong>
          </p>
          @if (!soloLectura()) {
            <button type="button" [disabled]="!puedeGuardar() || guardando()" (click)="guardar()">
              Guardar formula completa
            </button>
          }
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
        </footer>
      </section>
    }
  `,
  styles: [
    `
      .back {
        color: #166534;
        cursor: pointer;
      }
      dl {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
        gap: 0.75rem;
      }
      dt {
        font-size: 0.75rem;
        color: #6b7280;
      }
      dd {
        margin: 0;
        font-weight: 600;
      }
      .link-editar {
        color: #166534;
      }
      .linea {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        padding: 1rem 0;
        border-bottom: 1px solid #e5e7eb;
        align-items: flex-end;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.85rem;
      }
      .autocomplete {
        position: relative;
        min-width: 200px;
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
        max-height: 180px;
        overflow-y: auto;
      }
      .dropdown li {
        padding: 0.5rem;
        cursor: pointer;
      }
      input {
        padding: 0.4rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
      }
      input[readonly] {
        background: #f3f4f6;
      }
      .warn {
        color: #b45309;
        font-weight: 600;
      }
      .ok-hint {
        color: #166534;
      }
      .ok {
        color: #166534;
      }
      .bad {
        color: #b91c1c;
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
      button:disabled {
        opacity: 0.5;
      }
      .error {
        color: #b91c1c;
      }
      .hint {
        color: #6b7280;
      }
      .solo-lectura .linea {
        opacity: 0.95;
      }
    `,
  ],
})
export class FormulaDetalleComponent implements OnInit {
  readonly PESO_LOTE = PESO_LOTE_FORMULA_KG;

  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly formula = signal<FormulaCompleta | null>(null);
  readonly lineas = signal<LineaFormulaUi[]>([]);
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly mpAbiertoIndex = signal<number | null>(null);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly hayCambiosSinGuardar = signal(false);

  private sincronizandoMp = false;

  readonly soloLectura = computed(
    () => this.route.snapshot.queryParamMap.get('modo') === 'ver',
  );

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

  readonly puedeGuardar = computed(() => {
    if (this.soloLectura()) return false;
    return (
      this.loteCompleto() &&
      this.lineasConMateria().length > 0 &&
      this.lineasConMateria().every((l) => l.precioPorKilo > 0)
    );
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
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.api.getFormula(this.idGranja, this.idFormula).subscribe({
      next: (f) => {
        this.formula.set(f);
        if (f.lineas.length > 0) {
          this.lineas.set(f.lineas.map(lineaDesdeFormula));
        } else if (!this.soloLectura()) {
          this.lineas.set([lineaFormulaVacia()]);
        } else {
          this.lineas.set([]);
        }
        this.hayCambiosSinGuardar.set(false);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar la formula');
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

  guardar(): void {
    if (!this.puedeGuardar()) return;
    this.guardando.set(true);
    this.api
      .guardarFormulaDetalle(this.idGranja, this.idFormula, {
        lineas: this.lineasConMateria().map((l) => ({
          idMateriaPrima: l.idMateriaPrima!,
          cantidadKg: l.cantidadKg,
        })),
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.hayCambiosSinGuardar.set(false);
          void this.router.navigate(['..'], { relativeTo: this.route });
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeErrorHttp(err, 'No se pudo guardar la formula'));
        },
      });
  }

  intentarVolver(event: Event): void {
    if (!this.puedeSalir()) event.preventDefault();
  }

  puedeSalir(): boolean {
    if (this.soloLectura()) return true;
    if (!this.hayCambiosSinGuardar() && this.formula()?.completa) return true;
    if (!this.hayCambiosSinGuardar() && this.lineasConMateria().length === 0) return true;
    if (!this.hayCambiosSinGuardar() && this.loteCompleto()) return true;
    return false;
  }

  mensajeBloqueoSalida(): string {
    if (!this.loteCompleto()) {
      return `La formula debe sumar ${PESO_LOTE_FORMULA_KG} kg. Faltan ${this.kilosFaltantesUi()} kg.`;
    }
    return 'Tenés cambios sin guardar. Guardá la formula completa antes de salir.';
  }

  private marcarModificado(): void {
    this.hayCambiosSinGuardar.set(true);
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
