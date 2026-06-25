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
    <a routerLink=".." class="reforma-back"><i class="pi pi-arrow-left"></i> Volver al listado</a>
    <h2 class="reforma-page-title">Nueva fórmula</h2>
    <p class="subtitulo">Completá los datos; luego cargarás los ingredientes (1000 kg).</p>

    <section class="reforma-section">
      <form (ngSubmit)="crear()" #f="ngForm" class="formulario">
        <label class="reforma-field">
          <span>Código de fórmula</span>
          <input class="reforma-input" name="codigo" [(ngModel)]="codigoFormula" maxlength="50" required />
        </label>
        <label class="reforma-field">
          <span>Descripción</span>
          <input class="reforma-input" name="descripcion" [(ngModel)]="descripcionFormula" maxlength="200" required />
        </label>

        <label class="reforma-field reforma-autocomplete">
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
        <label class="reforma-field">
          <span>Código animal</span>
          <input
            class="reforma-input"
            name="codigoAnimal"
            [(ngModel)]="codigoAnimal"
            (ngModelChange)="onCodigoAnimalChange($event)"
            required
          />
        </label>

        <div class="acciones">
          <a routerLink=".." class="reforma-btn-ghost"><i class="pi pi-times"></i> Cancelar</a>
          <button class="reforma-btn" type="submit" [disabled]="guardando() || f.invalid || !idAnimalSeleccionado()">
            <i class="pi pi-check"></i> Guardar e ir a ingredientes
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
