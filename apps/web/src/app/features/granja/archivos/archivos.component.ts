import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import {
  ArchivoResumen,
  TIPO_ARCHIVO_LABEL,
  TipoModuloArchivo,
} from '../../../data/models/archivo.model';

/**
 * Explorador de archivos (snapshots inmutables) de la granja. Los archivos se crean
 * desde el botón "Crear archivo" de Inventario, Compras y Fórmulas; acá solo se
 * exploran y se abren (no hay edición ni borrado: son registros históricos).
 */
@Component({
  selector: 'app-archivos',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <header class="toolbar">
      <div>
        <h2 class="reforma-page-title">Archivos</h2>
        <p class="sub text-dim">
          Copias inmutables de Inventario, Compras y Fórmulas creadas desde cada módulo.
        </p>
      </div>
      <div class="filtro">
        <span class="mini text-dim">Módulo:</span>
        <select class="reforma-input" [ngModel]="filtroTipo()" (ngModelChange)="filtroTipo.set($event)">
          <option value="">Todos</option>
          <option value="INVENTARIO">Inventario</option>
          <option value="COMPRAS">Compras</option>
          <option value="FORMULAS">Fórmulas</option>
        </select>
      </div>
    </header>

    @if (cargando()) {
      <p class="reforma-empty">Cargando archivos…</p>
    } @else if (archivosFiltrados().length === 0) {
      <p class="reforma-empty">
        @if (filtroTipo()) {
          No hay archivos de {{ etiquetaTipo(filtroTipo()) }} todavía.
        } @else {
          No hay archivos todavía. Crealos con el botón "Crear archivo" en Inventario,
          Compras o Fórmulas.
        }
      </p>
    } @else {
      <div class="reforma-table-wrap">
        <table class="reforma-table">
          <thead>
            <tr>
              <th>Código</th>
              <th>Módulo</th>
              <th>Descripción</th>
              <th>Fecha de creación</th>
              <th>Creado por</th>
              <th class="num">Registros</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (a of archivosFiltrados(); track a.id) {
              <tr>
                <td>{{ a.codigoArchivo }}</td>
                <td>
                  <span class="tipo-badge" [attr.data-tipo]="a.tipo">{{ etiquetaTipo(a.tipo) }}</span>
                </td>
                <td class="descripcion">{{ a.descripcion || '—' }}</td>
                <td>{{ a.fechaCreacion | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ a.creadoPorEmail }}</td>
                <td class="num">{{ a.totalRegistros }}</td>
                <td>
                  <a class="reforma-btn-ghost reforma-btn-sm" [routerLink]="[a.id]">
                    <i class="pi pi-eye"></i> Ver
                  </a>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (error()) {
      <p class="reforma-alert reforma-alert-error">
        <i class="pi pi-exclamation-circle"></i> {{ error() }}
      </p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .toolbar {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        align-items: flex-start;
        flex-wrap: wrap;
      }
      .reforma-page-title {
        margin: 0;
      }
      .sub {
        margin: 0.25rem 0 0;
      }
      .filtro {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .filtro select {
        width: auto;
      }
      .mini {
        font-size: 0.85rem;
      }
      .descripcion {
        max-width: 28ch;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .tipo-badge {
        display: inline-flex;
        padding: 0.2rem 0.6rem;
        border-radius: 999px;
        font-size: 0.78rem;
        font-weight: 600;
        white-space: nowrap;
        color: var(--tipo-color, #9d77f4);
        background: color-mix(in srgb, var(--tipo-color, #9d77f4) 14%, transparent);
        border: 1px solid color-mix(in srgb, var(--tipo-color, #9d77f4) 45%, transparent);
      }
      .tipo-badge[data-tipo='INVENTARIO'] {
        --tipo-color: #06b6d4;
      }
      .tipo-badge[data-tipo='COMPRAS'] {
        --tipo-color: #9d77f4;
      }
      .tipo-badge[data-tipo='FORMULAS'] {
        --tipo-color: #34d399;
      }
    `,
  ],
})
export class ArchivosComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly archivos = signal<ArchivoResumen[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly filtroTipo = signal<TipoModuloArchivo | ''>('');

  readonly archivosFiltrados = computed(() => {
    const tipo = this.filtroTipo();
    return tipo ? this.archivos().filter((a) => a.tipo === tipo) : this.archivos();
  });

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.recargar();
  }

  etiquetaTipo(tipo: TipoModuloArchivo | ''): string {
    return tipo ? TIPO_ARCHIVO_LABEL[tipo] : '';
  }

  private recargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.getArchivos(this.idGranja).subscribe({
      next: (lista) => {
        this.archivos.set(lista);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudieron cargar los archivos'));
        this.cargando.set(false);
      },
    });
  }
}
