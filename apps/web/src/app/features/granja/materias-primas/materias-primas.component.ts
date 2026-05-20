import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { MateriaPrima, MateriaPrimaRequest } from '../../../data/models/materia-prima.model';

@Component({
  selector: 'app-materias-primas',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  template: `
    <h2>Materias primas</h2>

    <section class="alta">
      <h3>Alta rápida</h3>
      <form (ngSubmit)="crear()" #f="ngForm">
        <label>
          Código
          <input
            name="codigo"
            [(ngModel)]="form.codigoMateriaPrima"
            maxlength="50"
            required
          />
        </label>
        <label>
          Nombre
          <input
            name="nombre"
            [(ngModel)]="form.nombreMateriaPrima"
            maxlength="200"
            required
          />
        </label>
        <label>
          Precio por kg
          <input
            type="number"
            name="precio"
            [(ngModel)]="form.precioPorKilo"
            min="0"
            step="0.01"
            required
          />
        </label>
        <button type="submit" [disabled]="creando() || f.invalid">Crear</button>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
      </form>
    </section>

    <section class="lista">
      <h3>Activas ({{ items().length }})</h3>
      @if (cargando()) {
        <p>Cargando…</p>
      } @else if (items().length === 0) {
        <p class="vacio">Todavía no cargaste ninguna materia prima.</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Nombre</th>
              <th class="num">Precio/kg</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (mp of items(); track mp.id) {
              <tr>
                <td>{{ mp.codigoMateriaPrima }}</td>
                <td>{{ mp.nombreMateriaPrima }}</td>
                <td class="num">{{ mp.precioPorKilo | number: '1.2-2' }}</td>
                <td>
                  <button type="button" class="danger" (click)="desactivar(mp)">
                    Dar de baja
                  </button>
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
      input {
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
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
      }
      .vacio {
        color: #6b7280;
      }
      table {
        width: 100%;
        border-collapse: collapse;
      }
      th,
      td {
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid #e5e7eb;
        text-align: left;
      }
      th.num,
      td.num {
        text-align: right;
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class MateriasPrimasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly items = signal<MateriaPrima[]>([]);
  readonly cargando = signal(true);
  readonly creando = signal(false);
  readonly error = signal<string | null>(null);

  form: MateriaPrimaRequest = {
    codigoMateriaPrima: '',
    nombreMateriaPrima: '',
    precioPorKilo: 0,
  };

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargar();
  }

  recargar(): void {
    this.cargando.set(true);
    this.api.getMateriasPrimas(this.idGranja).subscribe({
      next: (list) => {
        this.items.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar las materias primas');
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.form.codigoMateriaPrima.trim() || !this.form.nombreMateriaPrima.trim()) {
      return;
    }
    this.creando.set(true);
    this.error.set(null);
    this.api.crearMateriaPrima(this.idGranja, this.form).subscribe({
      next: (mp) => {
        this.items.update((prev) => [...prev, mp].sort((a, b) =>
          a.nombreMateriaPrima.localeCompare(b.nombreMateriaPrima),
        ));
        this.form = { codigoMateriaPrima: '', nombreMateriaPrima: '', precioPorKilo: 0 };
        this.creando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.creando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo crear la materia prima');
      },
    });
  }

  desactivar(mp: MateriaPrima): void {
    if (!confirm(`¿Dar de baja la materia prima "${mp.nombreMateriaPrima}"?`)) {
      return;
    }
    this.api.desactivarMateriaPrima(this.idGranja, mp.id).subscribe({
      next: () => this.items.update((prev) => prev.filter((m) => m.id !== mp.id)),
      error: () => this.error.set('No se pudo dar de baja'),
    });
  }
}
