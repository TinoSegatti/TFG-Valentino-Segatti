import { Component, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { FormulaResumen } from '../../../data/models/formula.model';

@Component({
  selector: 'app-formulas',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  template: `
    <header class="toolbar">
      <h2>Formulas dietarias</h2>
      <a routerLink="nueva" class="btn-nueva">Crear formula</a>
    </header>

    <section class="lista">
      @if (cargando()) {
        <p>Cargando…</p>
      } @else if (formulas().length === 0) {
        <p class="vacio">Todavia no hay formulas cargadas.</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Codigo</th>
              <th>Descripcion</th>
              <th>Animal</th>
              <th>Costo formula</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (f of formulas(); track f.id) {
              <tr>
                <td>{{ f.codigoFormula }}</td>
                <td>{{ f.descripcionFormula }}</td>
                <td>{{ f.descripcionAnimal }} ({{ f.codigoAnimal }})</td>
                <td>$ {{ f.costoTotalFormula | number: '1.3-3' }}</td>
                <td>
                  <span [class.incompleta]="!f.completa">{{
                    f.completa ? 'Completa' : 'Incompleta'
                  }}</span>
                </td>
                <td class="acciones">
                  <a [routerLink]="[f.id]" [queryParams]="{ modo: 'ver' }">Ver</a>
                  <a [routerLink]="[f.id]" class="link">Editar</a>
                  <button type="button" class="link danger-text" (click)="eliminar(f)">
                    Eliminar
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
      }
      h2 {
        margin: 0;
      }
      a.btn-nueva {
        background: #166534;
        color: white;
        text-decoration: none;
        padding: 0.5rem 1rem;
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
      }
      .acciones {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem 0.75rem;
      }
      a,
      button.link {
        color: #166534;
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
        text-decoration: underline;
        font-size: inherit;
      }
      button.danger-text {
        color: #b91c1c;
      }
      .incompleta {
        color: #b45309;
        font-weight: 600;
      }
      .vacio {
        color: #6b7280;
      }
      .error {
        color: #b91c1c;
      }
    `,
  ],
})
export class FormulasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly formulas = signal<FormulaResumen[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargar();
  }

  eliminar(f: FormulaResumen): void {
    if (!confirm(`Eliminar la formula ${f.codigoFormula}?`)) return;
    this.api.desactivarFormula(this.idGranja, f.id).subscribe({
      next: () => this.recargar(),
      error: (err: HttpErrorResponse) =>
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la formula')),
    });
  }

  private recargar(): void {
    this.cargando.set(true);
    this.api.getFormulas(this.idGranja).subscribe({
      next: (list) => {
        this.formulas.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar las formulas');
        this.cargando.set(false);
      },
    });
  }
}
