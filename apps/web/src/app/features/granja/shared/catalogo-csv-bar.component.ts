import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CsvImportResult } from '../../../data/models/csv.model';

/**
 * Barra reutilizable de import/export CSV para los catálogos (Materias Primas, Proveedores,
 * Animales). Encapsula la UI común: botón Exportar, input file + botón Importar, panel de
 * resumen tras importar (filas OK / filas con error + detalle por línea).
 *
 * <p>El componente padre es responsable de la lógica:
 * <ul>
 *   <li>al click en Exportar emite `(exportar)`;</li>
 *   <li>al elegir archivo + click en Importar emite `(importar)` con el File;</li>
 *   <li>el padre setea `[resultado]` para mostrar el resumen y `[trabajando]` para
 *       deshabilitar los controles.</li>
 * </ul>
 */
@Component({
  selector: 'app-catalogo-csv-bar',
  standalone: true,
  template: `
    <section class="csv-bar">
      <div class="acciones">
        <button
          type="button"
          class="csv"
          [disabled]="trabajando"
          (click)="exportar.emit()"
        >
          Exportar CSV
        </button>

        <label class="archivo">
          <input
            type="file"
            accept=".csv,text/csv"
            (change)="onArchivoSeleccionado($event)"
            [disabled]="trabajando"
          />
        </label>

        <button
          type="button"
          class="csv"
          [disabled]="trabajando || !archivo()"
          (click)="onImportar()"
        >
          Importar CSV
        </button>

        @if (archivo()) {
          <span class="archivo-nombre" title="{{ archivo()!.name }}">
            {{ archivo()!.name }}
          </span>
        }
      </div>

      <p class="ayuda">
        Formato esperado: CSV UTF-8 con cabecera <code>{{ columnasAyuda }}</code>. Las filas
        duplicadas o inválidas se reportan abajo sin abortar el resto de la importación.
      </p>

      @if (resultado) {
        <div
          class="resumen"
          [class.ok]="resultado.filasError === 0 && resultado.filasOk > 0"
          [class.alerta]="resultado.filasError > 0"
        >
          <strong>Resultado del último import:</strong>
          {{ resultado.filasOk }} fila{{ resultado.filasOk === 1 ? '' : 's' }} importada{{
            resultado.filasOk === 1 ? '' : 's'
          }}, {{ resultado.filasError }} con error.

          @if (resultado.errores.length > 0) {
            <details class="errores">
              <summary>Ver {{ resultado.errores.length }} detalle(s)</summary>
              <ul>
                @for (e of resultado.errores; track $index) {
                  <li>
                    Línea {{ e.linea }}
                    @if (e.codigo) {
                      (<code>{{ e.codigo }}</code>)
                    }
                    — {{ e.mensaje }}
                  </li>
                }
              </ul>
            </details>
          }
        </div>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
        margin: 1.5rem 0;
      }
      .csv-bar {
        padding: 0.75rem 1rem;
        border: 1px solid #e5e7eb;
        border-radius: 6px;
        background: #f9fafb;
      }
      .acciones {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        align-items: center;
      }
      button.csv {
        padding: 0.45rem 0.9rem;
        background: #1d4ed8;
        color: white;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        font-size: 0.9rem;
      }
      button.csv:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .archivo input[type='file'] {
        font-size: 0.85rem;
      }
      .archivo-nombre {
        font-size: 0.8rem;
        color: #374151;
        max-width: 240px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .ayuda {
        margin: 0.6rem 0 0;
        font-size: 0.8rem;
        color: #6b7280;
      }
      code {
        background: #e5e7eb;
        padding: 0 0.3rem;
        border-radius: 3px;
        font-size: 0.78rem;
      }
      .resumen {
        margin-top: 0.75rem;
        padding: 0.6rem 0.75rem;
        border-radius: 4px;
        font-size: 0.88rem;
      }
      .resumen.ok {
        background: #ecfdf5;
        border: 1px solid #6ee7b7;
        color: #065f46;
      }
      .resumen.alerta {
        background: #fef3c7;
        border: 1px solid #fcd34d;
        color: #92400e;
      }
      .errores {
        margin-top: 0.5rem;
      }
      .errores ul {
        margin: 0.4rem 0 0 1.2rem;
        padding: 0;
      }
      .errores li {
        margin-bottom: 0.25rem;
        color: #b91c1c;
      }
    `,
  ],
})
export class CatalogoCsvBarComponent {
  @Input() trabajando = false;
  @Input() resultado: CsvImportResult | null = null;
  @Input() columnasAyuda = '';

  @Output() exportar = new EventEmitter<void>();
  @Output() importar = new EventEmitter<File>();

  protected readonly archivo = signal<File | null>(null);

  onArchivoSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files && input.files.length > 0 ? input.files[0] : null;
    this.archivo.set(f);
  }

  onImportar(): void {
    const f = this.archivo();
    if (!f) return;
    this.importar.emit(f);
  }

  reset(): void {
    this.archivo.set(null);
  }
}
