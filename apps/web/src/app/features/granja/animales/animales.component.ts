import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { debounceTime, Subject } from 'rxjs';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Animal, AnimalRequest } from '../../../data/models/animal.model';

/**
 * Listado, alta rápida y baja lógica del catálogo de Animales (RF-ANI-001 / RF-ANI-002).
 * Aplica la política ADR 0005: la baja es lógica y reusar el código crea entidad nueva.
 */
@Component({
  selector: 'app-animales',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2>Animales</h2>

    <section class="alta">
      <h3>Alta rápida</h3>
      <form (ngSubmit)="crear()" #f="ngForm">
        <label>
          Código
          <input
            name="codigo"
            [(ngModel)]="form.codigoAnimal"
            maxlength="50"
            required
          />
        </label>
        <label>
          Descripción
          <input
            name="descripcion"
            [(ngModel)]="form.descripcionAnimal"
            maxlength="200"
            required
          />
        </label>
        <label>
          Categoría
          <input name="categoria" [(ngModel)]="form.categoriaAnimal" maxlength="100" />
        </label>
        <label class="full">
          Observaciones
          <textarea
            name="observaciones"
            [(ngModel)]="form.observaciones"
            maxlength="5000"
            rows="2"
          ></textarea>
        </label>
        <button type="submit" [disabled]="creando() || f.invalid">Crear</button>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
      </form>
    </section>

    <section class="lista">
      <header class="lista-header">
        <h3>Activos ({{ items().length }})</h3>
        <input
          class="buscar"
          type="search"
          placeholder="Buscar por descripción…"
          [ngModel]="filtro()"
          (ngModelChange)="onFiltroChange($event)"
        />
      </header>

      @if (cargando()) {
        <p>Cargando…</p>
      } @else if (items().length === 0) {
        <p class="vacio">Todavía no cargaste ningún animal (o ninguno coincide con el filtro).</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Descripción</th>
              <th>Categoría</th>
              <th>Observaciones</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (a of items(); track a.id) {
              <tr>
                <td>{{ a.codigoAnimal }}</td>
                <td>{{ a.descripcionAnimal }}</td>
                <td>{{ a.categoriaAnimal ?? '—' }}</td>
                <td class="obs">{{ a.observaciones ?? '—' }}</td>
                <td>
                  <button type="button" class="danger" (click)="desactivar(a)">Dar de baja</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      h2 {
        margin-top: 0;
      }
      .alta {
        margin-bottom: 2rem;
      }
      form {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        align-items: flex-end;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.85rem;
      }
      label.full {
        flex: 1 1 100%;
      }
      input,
      textarea {
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
        font: inherit;
      }
      textarea {
        resize: vertical;
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
        cursor: not-allowed;
      }
      button.danger {
        background: #b91c1c;
      }
      .error {
        color: #b91c1c;
        margin: 0.5rem 0 0;
        flex: 1 1 100%;
      }
      .vacio {
        color: #6b7280;
      }
      .lista-header {
        display: flex;
        align-items: center;
        gap: 1rem;
      }
      .lista-header h3 {
        margin: 0;
        flex: 1;
      }
      .buscar {
        min-width: 240px;
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
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
        vertical-align: top;
      }
      td.obs {
        max-width: 280px;
        white-space: pre-wrap;
        color: #4b5563;
      }
    `,
  ],
})
export class AnimalesComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly items = signal<Animal[]>([]);
  readonly cargando = signal(true);
  readonly creando = signal(false);
  readonly error = signal<string | null>(null);
  readonly filtro = signal<string>('');

  // Debounce 300ms para no martillar al backend con cada tecla del filtro.
  private readonly busquedaPendiente = new Subject<string>();

  form: AnimalRequest = vacio();

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.busquedaPendiente
      .pipe(debounceTime(300))
      .subscribe((termino) => this.recargar(termino));
    this.recargar();
  }

  onFiltroChange(valor: string): void {
    this.filtro.set(valor);
    this.busquedaPendiente.next(valor);
  }

  recargar(buscar?: string): void {
    this.cargando.set(true);
    this.api.getAnimales(this.idGranja, buscar?.trim() || undefined).subscribe({
      next: (list) => {
        this.items.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los animales');
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.form.codigoAnimal.trim() || !this.form.descripcionAnimal.trim()) {
      return;
    }
    this.creando.set(true);
    this.error.set(null);
    this.api.crearAnimal(this.idGranja, this.form).subscribe({
      next: (a) => {
        this.items.update((prev) =>
          [...prev, a].sort((x, y) => x.descripcionAnimal.localeCompare(y.descripcionAnimal)),
        );
        this.form = vacio();
        this.creando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.creando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo crear el animal');
      },
    });
  }

  desactivar(a: Animal): void {
    if (!confirm(`¿Dar de baja al animal "${a.descripcionAnimal}"?`)) {
      return;
    }
    this.api.desactivarAnimal(this.idGranja, a.id).subscribe({
      next: () => this.items.update((prev) => prev.filter((x) => x.id !== a.id)),
      error: () => this.error.set('No se pudo dar de baja'),
    });
  }
}

function vacio(): AnimalRequest {
  return {
    codigoAnimal: '',
    descripcionAnimal: '',
    categoriaAnimal: '',
    observaciones: '',
  };
}
