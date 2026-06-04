import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Animal } from '../../../data/models/animal.model';

@Component({
  selector: 'app-formula-nueva',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <a routerLink=".." class="back">← Volver al listado</a>
    <h2>Nueva formula</h2>
    <p class="subtitulo">Completá los datos; luego cargarás los ingredientes (1000 kg).</p>

    <form (ngSubmit)="crear()" #f="ngForm" class="formulario">
      <label>
        Codigo de formula
        <input name="codigo" [(ngModel)]="codigoFormula" maxlength="50" required />
      </label>
      <label>
        Descripcion
        <input name="descripcion" [(ngModel)]="descripcionFormula" maxlength="200" required />
      </label>
      <div class="fila-animal">
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
            placeholder="Buscar animal…"
          />
          @if (mostrarAnimales() && animalesFiltrados().length) {
            <ul class="dropdown">
              @for (a of animalesFiltrados(); track a.id) {
                <li (mousedown)="seleccionarAnimal(a)">{{ a.descripcionAnimal }} ({{ a.codigoAnimal }})</li>
              }
            </ul>
          }
        </label>
        <label>
          Codigo animal
          <input
            name="codigoAnimal"
            [(ngModel)]="codigoAnimal"
            (ngModelChange)="onCodigoAnimalChange($event)"
            required
          />
        </label>
      </div>

      <div class="acciones-form">
        <button type="submit" [disabled]="guardando() || f.invalid || !idAnimalSeleccionado()">
          Guardar e ir a ingredientes
        </button>
        <a routerLink=".." class="btn-cancelar">Cancelar</a>
      </div>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }
    </form>
  `,
  styles: [
    `
      :host {
        display: block;
        max-width: 900px;
      }
      .back {
        color: #166534;
        text-decoration: none;
      }
      .subtitulo {
        color: #6b7280;
      }
      .formulario {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
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
      }
      .fila-animal {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        width: 100%;
      }
      .autocomplete {
        position: relative;
        flex: 1;
        min-width: 200px;
      }
      input {
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
      }
      .dropdown {
        position: absolute;
        z-index: 10;
        list-style: none;
        margin: 0;
        padding: 0;
        background: white;
        border: 1px solid #d1d5db;
        max-height: 200px;
        overflow-y: auto;
        width: 100%;
      }
      .dropdown li {
        padding: 0.5rem;
        cursor: pointer;
      }
      .dropdown li:hover {
        background: #ecfdf5;
      }
      button {
        padding: 0.5rem 1rem;
        background: #166534;
        color: white;
        border: none;
        border-radius: 4px;
        cursor: pointer;
      }
      button:disabled {
        opacity: 0.5;
      }
      .btn-cancelar {
        color: #374151;
        text-decoration: none;
        padding: 0.5rem 1rem;
      }
      .acciones-form {
        display: flex;
        gap: 0.75rem;
        width: 100%;
      }
      .error {
        color: #b91c1c;
        width: 100%;
      }
    `,
  ],
})
export class FormulaNuevaComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly animales = signal<Animal[]>([]);
  readonly mostrarAnimales = signal(false);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly idAnimalSeleccionado = signal<number | null>(null);

  codigoFormula = '';
  descripcionFormula = '';
  nombreAnimal = '';
  codigoAnimal = '';

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.api.getAnimales(this.idGranja).subscribe({
      next: (list) => this.animales.set(list),
    });
  }

  animalesFiltrados(): Animal[] {
    const t = this.nombreAnimal.trim().toLowerCase();
    if (!t) return this.animales();
    return this.animales().filter(
      (a) =>
        a.descripcionAnimal.toLowerCase().includes(t) ||
        a.codigoAnimal.toLowerCase().includes(t),
    );
  }

  cerrarAnimales(): void {
    setTimeout(() => this.mostrarAnimales.set(false), 150);
  }

  onCodigoAnimalChange(codigo: string): void {
    const a = this.animales().find(
      (x) => x.codigoAnimal.toLowerCase() === codigo.trim().toLowerCase(),
    );
    if (a) this.aplicarAnimal(a);
    else this.idAnimalSeleccionado.set(null);
  }

  onNombreAnimalChange(nombre: string): void {
    this.mostrarAnimales.set(true);
    const a = this.animales().find(
      (x) => x.descripcionAnimal.toLowerCase() === nombre.trim().toLowerCase(),
    );
    if (a) this.aplicarAnimal(a);
    else this.idAnimalSeleccionado.set(null);
  }

  seleccionarAnimal(a: Animal): void {
    this.aplicarAnimal(a);
    this.mostrarAnimales.set(false);
  }

  crear(): void {
    const idAnimal = this.idAnimalSeleccionado();
    if (!idAnimal) return;
    this.guardando.set(true);
    this.api
      .crearFormulaCabecera(this.idGranja, {
        codigoFormula: this.codigoFormula.trim(),
        descripcionFormula: this.descripcionFormula.trim(),
        idAnimal,
      })
      .subscribe({
        next: (f) => {
          this.guardando.set(false);
          void this.router.navigate(['/granja', this.idGranja, 'formulas', f.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeErrorHttp(err, 'No se pudo crear la formula'));
        },
      });
  }

  private aplicarAnimal(a: Animal): void {
    this.idAnimalSeleccionado.set(a.id);
    this.codigoAnimal = a.codigoAnimal;
    this.nombreAnimal = a.descripcionAnimal;
  }
}
