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
    <section class="csv-bar glass-surface">
      <div class="head">
        <i class="pi pi-file-import"></i>
        <span>Importar / exportar CSV</span>
      </div>

      <div class="acciones">
        <button
          type="button"
          class="reforma-btn-ghost reforma-btn-sm"
          [disabled]="trabajando"
          (click)="exportar.emit()"
        >
          <i class="pi pi-download"></i> Exportar CSV
        </button>

        <label class="archivo reforma-btn-ghost reforma-btn-sm">
          <i class="pi pi-paperclip"></i>
          <span>{{ archivo() ? 'Cambiar archivo' : 'Elegir archivo' }}</span>
          <input
            type="file"
            accept=".csv,text/csv"
            (change)="onArchivoSeleccionado($event)"
            [disabled]="trabajando"
            hidden
          />
        </label>

        <button
          type="button"
          class="reforma-btn reforma-btn-sm"
          [disabled]="trabajando || !archivo()"
          (click)="onImportar()"
        >
          <i class="pi pi-upload"></i> Importar CSV
        </button>

        @if (archivo()) {
          <span class="archivo-nombre" title="{{ archivo()!.name }}">
            {{ archivo()!.name }}
          </span>
        }
      </div>

      <p class="ayuda">
        Formato esperado: CSV UTF-8 con cabecera <code class="reforma-code">{{ columnasAyuda }}</code>.
        Las filas duplicadas o inválidas se reportan abajo sin abortar el resto de la importación.
      </p>

      @if (resultado) {
        <div
          class="reforma-alert resumen"
          [class.reforma-alert-ok]="resultado.filasError === 0 && resultado.filasOk > 0"
          [class.reforma-alert-warn]="resultado.filasError > 0"
        >
          <div>
            <i class="pi" [class.pi-check-circle]="resultado.filasError === 0" [class.pi-exclamation-triangle]="resultado.filasError > 0"></i>
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
                        (<code class="reforma-code">{{ e.codigo }}</code>)
                      }
                      — {{ e.mensaje }}
                    </li>
                  }
                </ul>
              </details>
            }
          </div>
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
        padding: 1rem 1.25rem;
        border-radius: var(--reforma-radius);
      }
      .head {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-bottom: 0.85rem;
        font-weight: 600;
        color: var(--reforma-text);
      }
      .head i {
        color: var(--reforma-accent);
      }
      .acciones {
        display: flex;
        flex-wrap: wrap;
        gap: 0.6rem;
        align-items: center;
      }
      .archivo {
        margin: 0;
      }
      .archivo-nombre {
        font-size: 0.82rem;
        color: var(--reforma-text-dim);
        max-width: 240px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .ayuda {
        margin: 0.75rem 0 0;
        font-size: 0.82rem;
        color: var(--reforma-text-dim);
      }
      .resumen {
        margin-top: 0.85rem;
        display: block;
      }
      .resumen i {
        margin-right: 0.35rem;
      }
      .errores {
        margin-top: 0.5rem;
      }
      .errores summary {
        cursor: pointer;
      }
      .errores ul {
        margin: 0.4rem 0 0 1.2rem;
        padding: 0;
      }
      .errores li {
        margin-bottom: 0.25rem;
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
