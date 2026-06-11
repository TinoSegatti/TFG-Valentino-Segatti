import { Component, inject, OnInit, signal, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { FormulaResumen, textoConfirmacionEliminarFormula } from '../../../data/models/formula.model';
import { CsvImportResult, descargarBlobComoArchivo } from '../../../data/models/csv.model';
import { CatalogoCsvBarComponent } from '../shared/catalogo-csv-bar.component';

@Component({
  selector: 'app-formulas',
  standalone: true,
  imports: [RouterLink, DecimalPipe, FormsModule, CatalogoCsvBarComponent],
  template: `
    <header class="toolbar">
      <h2>Formulas dietarias</h2>
      <a routerLink="nueva" class="btn-nueva">Crear formula</a>
    </header>

    <app-catalogo-csv-bar
      #csvBar
      [trabajando]="csvTrabajando()"
      [resultado]="csvResultado()"
      columnasAyuda="codigo_formula, descripcion_formula, codigo_animal, codigo_materia_prima, cantidad_kg"
      (exportar)="exportarCsv()"
      (importar)="importarCsv($event)"
    />

    @if (formulaEliminando()) {
      <section class="panel eliminar">
        <h3>Eliminar formula {{ formulaEliminando()!.codigoFormula }}</h3>
        <p>Escribi exactamente la frase siguiente para confirmar:</p>
        <code class="frase">{{ fraseEliminarEsperada() }}</code>
        <label>
          Confirmacion
          <input [(ngModel)]="textoConfirmacionEliminar" autocomplete="off" />
        </label>
        <div class="acciones-form">
          <button
            type="button"
            class="danger"
            [disabled]="!puedeConfirmarEliminar() || guardando()"
            (click)="confirmarEliminar()"
          >
            Eliminar formula
          </button>
          <button type="button" class="secundario" (click)="cancelarEliminar()">Cancelar</button>
        </div>
      </section>
    }

    @if (!formulaEliminando()) {
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
                <td>$ {{ f.costoTotalFormula | number: '1.2-2' }}</td>
                <td>
                  <span [class.incompleta]="!f.completa">{{
                    f.completa ? 'Completa' : 'Incompleta'
                  }}</span>
                </td>
                <td class="acciones">
                  <a [routerLink]="[f.id]">Ver</a>
                  <button type="button" class="link danger-text" (click)="iniciarEliminar(f)">
                    Eliminar
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
      </section>
    }

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
      .panel.eliminar {
        margin: 1.25rem 0;
        padding: 1rem;
        border: 1px solid #e5e7eb;
        border-radius: 6px;
        background: #fafafa;
      }
      .frase {
        display: block;
        padding: 0.5rem;
        background: #fef3c7;
        border-radius: 4px;
      }
      button.danger {
        background: #b91c1c;
        color: white;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
        cursor: pointer;
      }
      button.secundario {
        background: #e5e7eb;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
        cursor: pointer;
      }
      .acciones-form {
        display: flex;
        gap: 0.75rem;
        margin-top: 0.75rem;
      }
    `,
  ],
})
export class FormulasComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly formulas = signal<FormulaResumen[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly formulaEliminando = signal<FormulaResumen | null>(null);
  readonly fraseEliminarEsperada = signal('');

  readonly csvTrabajando = signal(false);
  readonly csvResultado = signal<CsvImportResult | null>(null);
  private readonly csvBar = viewChild(CatalogoCsvBarComponent);

  textoConfirmacionEliminar = '';

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargar();
  }

  iniciarEliminar(f: FormulaResumen): void {
    this.formulaEliminando.set(f);
    this.fraseEliminarEsperada.set(textoConfirmacionEliminarFormula(f.codigoFormula));
    this.textoConfirmacionEliminar = '';
  }

  cancelarEliminar(): void {
    this.formulaEliminando.set(null);
    this.textoConfirmacionEliminar = '';
    this.fraseEliminarEsperada.set('');
  }

  puedeConfirmarEliminar(): boolean {
    return this.textoConfirmacionEliminar.trim() === this.fraseEliminarEsperada();
  }

  confirmarEliminar(): void {
    const f = this.formulaEliminando();
    if (!f || !this.puedeConfirmarEliminar()) return;
    this.guardando.set(true);
    this.api.desactivarFormula(this.idGranja, f.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cancelarEliminar();
        this.recargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo eliminar la formula'));
      },
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

  exportarCsv(): void {
    this.csvTrabajando.set(true);
    this.api.exportarFormulasCsv(this.idGranja).subscribe({
      next: (blob) => {
        descargarBlobComoArchivo(blob, `formulas-${this.idGranja}.csv`);
        this.csvTrabajando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.csvTrabajando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo exportar el CSV'));
      },
    });
  }

  importarCsv(archivo: File): void {
    this.csvTrabajando.set(true);
    this.csvResultado.set(null);
    this.error.set(null);
    this.api.importarFormulasCsv(this.idGranja, archivo).subscribe({
      next: (resultado) => {
        this.csvResultado.set(resultado);
        this.csvTrabajando.set(false);
        this.csvBar()?.reset();
        if (resultado.filasOk > 0) this.recargar();
      },
      error: (err: HttpErrorResponse) => {
        this.csvTrabajando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo importar el CSV'));
      },
    });
  }
}
