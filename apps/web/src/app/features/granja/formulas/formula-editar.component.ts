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
    <a [routerLink]="rutaIngredientes()" class="back">← Volver a ingredientes</a>
    <h2>Editar cabecera de formula</h2>

    @if (cargando()) {
      <p>Cargando…</p>
    } @else {
      <form (ngSubmit)="guardar()" #f="ngForm" class="formulario">
        <label>
          Codigo
          <input name="codigo" [(ngModel)]="codigoFormula" required />
        </label>
        <label>
          Descripcion
          <input name="descripcion" [(ngModel)]="descripcionFormula" required />
        </label>
        <label class="autocomplete">
          Animal
          <input
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
        <button type="submit" [disabled]="guardando() || f.invalid || !idAnimalSeleccionado()">
          Guardar
        </button>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
      </form>
    }
  `,
  styles: [
    `
      .back {
        color: #166534;
      }
      .formulario {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        max-width: 480px;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      .autocomplete {
        position: relative;
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
      }
      .dropdown li {
        padding: 0.5rem;
        cursor: pointer;
      }
      button {
        padding: 0.5rem 1rem;
        background: #166534;
        color: white;
        border: none;
        border-radius: 4px;
      }
      .error {
        color: #b91c1c;
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
