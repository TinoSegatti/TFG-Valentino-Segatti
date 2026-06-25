import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Animal } from '../../../data/models/animal.model';

@Component({
  selector: 'app-formula-editar',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <a [routerLink]="rutaIngredientes()" class="reforma-back">
      <i class="pi pi-arrow-left"></i> Volver a ingredientes
    </a>
    <h2 class="reforma-page-title">Editar cabecera de fórmula</h2>

    @if (cargando()) {
      <p class="reforma-empty">Cargando…</p>
    } @else {
      <section class="reforma-section">
        <form (ngSubmit)="guardar()" #f="ngForm" class="formulario">
          <label class="reforma-field">
            <span>Código</span>
            <input class="reforma-input" name="codigo" [(ngModel)]="codigoFormula" required />
          </label>
          <label class="reforma-field full">
            <span>Descripción</span>
            <input class="reforma-input" name="descripcion" [(ngModel)]="descripcionFormula" required />
          </label>
          <label class="reforma-field reforma-autocomplete full">
            <span>Animal</span>
            <input
              class="reforma-input"
              name="animal"
              [(ngModel)]="nombreAnimal"
              (ngModelChange)="onNombreAnimalChange($event)"
              (focus)="mostrarAnimales.set(true)"
              (blur)="cerrarAnimales()"
              autocomplete="off"
              required
            />
            @if (mostrarAnimales() && animalesFiltrados().length) {
              <ul class="dropdown">
                @for (a of animalesFiltrados(); track a.id) {
                  <li (mousedown)="seleccionarAnimal(a)">{{ a.descripcionAnimal }}</li>
                }
              </ul>
            }
          </label>

          <div class="acciones">
            <a [routerLink]="rutaIngredientes()" class="reforma-btn-ghost">
              <i class="pi pi-times"></i> Cancelar
            </a>
            <button class="reforma-btn" type="submit" [disabled]="guardando() || f.invalid || !idAnimalSeleccionado()">
              <i class="pi pi-check"></i> Guardar
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
        max-width: 48rem;
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
export class FormulaEditarComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly animales = signal<Animal[]>([]);
  readonly mostrarAnimales = signal(false);
  readonly idAnimalSeleccionado = signal<number | null>(null);

  codigoFormula = '';
  descripcionFormula = '';
  nombreAnimal = '';

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  get idFormula(): string {
    return this.route.snapshot.paramMap.get('idFormula') ?? '';
  }

  /** Ruta absoluta: evita que ['..', id] resuelva mal entre rutas hermanas. */
  rutaIngredientes(): string[] {
    return ['/granja', this.idGranja, 'formulas', this.idFormula];
  }

  ngOnInit(): void {
    this.api.getAnimales(this.idGranja).subscribe({
      next: (list) => this.animales.set(list),
    });
    this.api.getFormula(this.idGranja, this.idFormula).subscribe({
      next: (f) => {
        this.codigoFormula = f.codigoFormula;
        this.descripcionFormula = f.descripcionFormula;
        this.nombreAnimal = f.descripcionAnimal;
        this.idAnimalSeleccionado.set(f.idAnimal);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar la formula');
        this.cargando.set(false);
      },
    });
  }

  animalesFiltrados(): Animal[] {
    const t = this.nombreAnimal.trim().toLowerCase();
    if (!t) return this.animales();
    return this.animales().filter((a) => a.descripcionAnimal.toLowerCase().includes(t));
  }

  cerrarAnimales(): void {
    setTimeout(() => this.mostrarAnimales.set(false), 150);
  }

  onNombreAnimalChange(nombre: string): void {
    this.mostrarAnimales.set(true);
    const a = this.animales().find(
      (x) => x.descripcionAnimal.toLowerCase() === nombre.trim().toLowerCase(),
    );
    if (a) this.idAnimalSeleccionado.set(a.id);
  }

  seleccionarAnimal(a: Animal): void {
    this.idAnimalSeleccionado.set(a.id);
    this.nombreAnimal = a.descripcionAnimal;
    this.mostrarAnimales.set(false);
  }

  guardar(): void {
    const idAnimal = this.idAnimalSeleccionado();
    if (!idAnimal) return;
    this.guardando.set(true);
    this.api
      .actualizarFormulaCabecera(this.idGranja, this.idFormula, {
        codigoFormula: this.codigoFormula.trim(),
        descripcionFormula: this.descripcionFormula.trim(),
        idAnimal,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          void this.router.navigate(this.rutaIngredientes());
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeErrorHttp(err, 'No se pudo guardar'));
        },
      });
  }
}
