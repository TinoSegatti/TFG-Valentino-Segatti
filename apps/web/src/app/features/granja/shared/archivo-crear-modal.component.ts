import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import {
  ArchivoResumen,
  TIPO_ARCHIVO_LABEL,
  TIPO_ARCHIVO_PREFIJO,
  TipoModuloArchivo,
} from '../../../data/models/archivo.model';

/**
 * Modal compartido para crear un archivo (snapshot inmutable) del módulo indicado.
 * Lo abren Inventario, Compras y Fórmulas desde su toolbar; el snapshot lo captura
 * el backend al momento del POST, por lo que el modal solo pide código y descripción.
 */
@Component({
  selector: 'app-archivo-crear-modal',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="overlay" (click)="cerrado.emit()"></div>
    <div class="modal glass-card-strong" role="dialog" aria-modal="true">
      <h3>Crear archivo de {{ etiquetaTipo() }}</h3>
      <p class="mini text-dim">
        Se guarda una copia inmutable de los registros actuales de {{ etiquetaTipo() }}. El
        archivo queda disponible en la sección Archivos y no se modifica aunque los datos
        cambien después.
      </p>
      <label class="reforma-field">
        <span>Código del archivo</span>
        <input class="reforma-input" type="text" [(ngModel)]="codigo" maxlength="50" />
      </label>
      <label class="reforma-field">
        <span>Descripción (opcional)</span>
        <input
          class="reforma-input"
          type="text"
          [(ngModel)]="descripcion"
          maxlength="500"
          placeholder="Ej.: cierre de mes, control previo a auditoría…"
        />
      </label>
      @if (error()) {
        <p class="reforma-alert reforma-alert-error">
          <i class="pi pi-exclamation-circle"></i> {{ error() }}
        </p>
      }
      <div class="acciones-modal">
        <button type="button" class="reforma-btn-ghost" (click)="cerrado.emit()">Cancelar</button>
        <button
          type="button"
          class="reforma-btn"
          (click)="crear()"
          [disabled]="guardando() || !codigo.trim()"
        >
          <i class="pi pi-history"></i> Crear archivo
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      .overlay {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.55);
        backdrop-filter: blur(2px);
        z-index: 40;
      }
      .modal {
        position: fixed;
        top: 10vh;
        left: 50%;
        transform: translateX(-50%);
        padding: 1.5rem;
        width: min(440px, 92vw);
        z-index: 41;
      }
      .modal h3 {
        margin: 0 0 0.5rem;
        color: var(--reforma-text);
      }
      .modal .reforma-field {
        margin-top: 0.85rem;
      }
      .mini {
        font-size: 0.85rem;
        margin: 0.35rem 0;
      }
      .acciones-modal {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        align-items: center;
        margin-top: 1.25rem;
      }
    `,
  ],
})
export class ArchivoCrearModalComponent implements OnInit {
  private readonly api = inject(ReformaApiService);

  readonly tipo = input.required<TipoModuloArchivo>();
  readonly idGranja = input.required<string>();

  readonly creado = output<ArchivoResumen>();
  readonly cerrado = output<void>();

  codigo = '';
  descripcion = '';
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.codigo = this.codigoSugerido();
  }

  etiquetaTipo(): string {
    return TIPO_ARCHIVO_LABEL[this.tipo()];
  }

  crear(): void {
    const codigo = this.codigo.trim();
    if (!codigo) return;
    this.guardando.set(true);
    this.error.set(null);
    this.api
      .crearArchivo(this.idGranja(), {
        tipo: this.tipo(),
        codigoArchivo: codigo,
        descripcion: this.descripcion.trim() || undefined,
      })
      .subscribe({
        next: (archivo) => {
          this.guardando.set(false);
          this.creado.emit(archivo);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(mensajeErrorHttp(err, 'No se pudo crear el archivo'));
          this.guardando.set(false);
        },
      });
  }

  /** Código editable sugerido: PREFIJO-yyyyMMdd-HHmm (ej. INV-20260702-1430). */
  private codigoSugerido(): string {
    const ahora = new Date();
    const dosDigitos = (n: number) => String(n).padStart(2, '0');
    const fecha = `${ahora.getFullYear()}${dosDigitos(ahora.getMonth() + 1)}${dosDigitos(ahora.getDate())}`;
    const hora = `${dosDigitos(ahora.getHours())}${dosDigitos(ahora.getMinutes())}`;
    return `${TIPO_ARCHIVO_PREFIJO[this.tipo()]}-${fecha}-${hora}`;
  }
}
