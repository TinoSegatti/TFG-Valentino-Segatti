import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import {
  ACCIONES_AUDITORIA,
  AccionAuditoria,
  AuditoriaRegistro,
} from '../../data/models/auditoria.model';
import { mensajeErrorHttp } from '../../core/http/api-error.util';

/**
 * Consola de auditoría (Etapa 5). Solo lectura, visible para el dueño (OWNER) y el jefe (ADMIN).
 * El scoping al tenant y la autorización los impone el backend (`/api/auditoria`).
 */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <header class="bar">
      <h1>Auditoría</h1>
      <a routerLink="/mis-plantas">Volver</a>
    </header>

    <main class="contenido">
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }

      <section class="filtros">
        <select name="accion" [(ngModel)]="fAccion">
          <option [ngValue]="undefined">Todas las acciones</option>
          @for (a of acciones; track a) {
            <option [ngValue]="a">{{ a }}</option>
          }
        </select>
        <input name="idGranja" placeholder="ID de granja" [(ngModel)]="fIdGranja" />
        <input name="idUsuario" placeholder="ID de usuario" [(ngModel)]="fIdUsuario" />
        <label>Desde <input name="desde" type="date" [(ngModel)]="fDesde" /></label>
        <label>Hasta <input name="hasta" type="date" [(ngModel)]="fHasta" /></label>
        <button type="button" (click)="filtrar()" [disabled]="cargando()">Filtrar</button>
        <button type="button" class="sec" (click)="limpiar()" [disabled]="cargando()">Limpiar</button>
      </section>

      @if (cargando()) {
        <p>Cargando…</p>
      } @else {
        <p class="total">{{ totalElementos() }} evento(s)</p>
        <table>
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Actor</th>
              <th>Acción</th>
              <th>Descripción</th>
              <th>Granja</th>
              <th>IP</th>
              <th>Datos</th>
            </tr>
          </thead>
          <tbody>
            @for (r of registros(); track r.id) {
              <tr>
                <td>{{ r.fechaOperacion | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                <td>
                  <span class="actor">{{ r.actorNombre || '—' }}</span>
                  <small>{{ r.actorEmail || r.idUsuario }}</small>
                </td>
                <td><span class="chip">{{ r.accion }}</span></td>
                <td>{{ r.descripcion || '—' }}</td>
                <td>{{ r.idGranja || '—' }}</td>
                <td>{{ r.ipAddress || '—' }}</td>
                <td>
                  @if (r.datosNuevos || r.datosAnteriores) {
                    <details>
                      <summary>ver</summary>
                      @if (r.datosAnteriores) {
                        <p class="json-lbl">Antes</p>
                        <pre>{{ r.datosAnteriores }}</pre>
                      }
                      @if (r.datosNuevos) {
                        <p class="json-lbl">Después</p>
                        <pre>{{ r.datosNuevos }}</pre>
                      }
                    </details>
                  } @else {
                    —
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7">No hay eventos para los filtros aplicados.</td></tr>
            }
          </tbody>
        </table>

        <nav class="paginacion">
          <button type="button" (click)="irA(pagina() - 1)" [disabled]="pagina() <= 0">‹ Anterior</button>
          <span>Página {{ pagina() + 1 }} de {{ totalPaginas() || 1 }}</span>
          <button
            type="button"
            (click)="irA(pagina() + 1)"
            [disabled]="pagina() + 1 >= totalPaginas()">
            Siguiente ›
          </button>
        </nav>
      }
    </main>
  `,
  styles: [
    `
      .bar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 1rem;
        font-family: system-ui, sans-serif;
      }
      .contenido {
        padding: 0 1rem 2rem;
        font-family: system-ui, sans-serif;
        max-width: 78rem;
      }
      .filtros {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
        align-items: center;
        margin-bottom: 1rem;
      }
      .filtros input,
      .filtros select {
        padding: 0.4rem;
      }
      label {
        font-size: 0.85rem;
        color: #374151;
      }
      .total {
        color: #6b7280;
        font-size: 0.85rem;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.9rem;
      }
      th,
      td {
        text-align: left;
        padding: 0.5rem;
        border-bottom: 1px solid #e5e7eb;
        vertical-align: top;
      }
      .actor {
        display: block;
      }
      small {
        color: #6b7280;
      }
      .chip {
        background: #eef2ff;
        color: #3730a3;
        padding: 0.15rem 0.45rem;
        border-radius: 0.5rem;
        font-size: 0.78rem;
        white-space: nowrap;
      }
      pre {
        background: #f9fafb;
        padding: 0.4rem;
        border-radius: 0.3rem;
        max-width: 24rem;
        overflow: auto;
        font-size: 0.78rem;
      }
      .json-lbl {
        margin: 0.3rem 0 0;
        font-weight: 600;
        font-size: 0.78rem;
      }
      button {
        padding: 0.4rem 0.7rem;
        background: #166534;
        color: white;
        border: none;
        cursor: pointer;
        border-radius: 0.3rem;
      }
      button.sec {
        background: #6b7280;
      }
      button:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .paginacion {
        display: flex;
        gap: 1rem;
        align-items: center;
        margin-top: 1rem;
      }
      .error {
        color: #b91c1c;
      }
    `,
  ],
})
export class AuditoriaComponent implements OnInit {
  private readonly api = inject(ReformaApiService);

  readonly acciones = ACCIONES_AUDITORIA;

  readonly registros = signal<AuditoriaRegistro[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly pagina = signal(0);
  readonly totalPaginas = signal(0);
  readonly totalElementos = signal(0);

  private readonly tamano = 20;

  fAccion?: AccionAuditoria;
  fIdGranja = '';
  fIdUsuario = '';
  fDesde = '';
  fHasta = '';

  ngOnInit(): void {
    this.cargar();
  }

  filtrar(): void {
    this.irA(0);
  }

  limpiar(): void {
    this.fAccion = undefined;
    this.fIdGranja = '';
    this.fIdUsuario = '';
    this.fDesde = '';
    this.fHasta = '';
    this.irA(0);
  }

  irA(pagina: number): void {
    this.pagina.set(Math.max(0, pagina));
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api
      .getAuditoria({
        accion: this.fAccion,
        idGranja: this.fIdGranja.trim() || undefined,
        idUsuario: this.fIdUsuario.trim() || undefined,
        desde: this.aInstante(this.fDesde, false),
        hasta: this.aInstante(this.fHasta, true),
        pagina: this.pagina(),
        tamano: this.tamano,
      })
      .subscribe({
        next: (p) => {
          this.registros.set(p.contenido);
          this.totalPaginas.set(p.totalPaginas);
          this.totalElementos.set(p.totalElementos);
          this.cargando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(mensajeErrorHttp(err, 'No se pudo cargar la auditoría.'));
          this.cargando.set(false);
        },
      });
  }

  /** Convierte una fecha (YYYY-MM-DD) a instante ISO: inicio o fin del día en hora local. */
  private aInstante(fecha: string, finDelDia: boolean): string | undefined {
    if (!fecha) {
      return undefined;
    }
    const hora = finDelDia ? 'T23:59:59.999' : 'T00:00:00.000';
    return new Date(fecha + hora).toISOString();
  }
}
