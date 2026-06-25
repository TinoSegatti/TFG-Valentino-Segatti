import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { SelectModule } from 'primeng/select';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import {
  ACCIONES_AUDITORIA,
  AccionAuditoria,
  AuditoriaRegistro,
} from '../../data/models/auditoria.model';
import { mensajeErrorHttp } from '../../core/http/api-error.util';
import { AccountNavComponent } from '../../shared/account-nav.component';
import { OrdenTabla } from '../../shared/orden-tabla';

/**
 * Consola de auditoría (Etapa 5). Solo lectura, visible para el dueño (OWNER) y el jefe (ADMIN).
 * El scoping al tenant y la autorización los impone el backend (`/api/auditoria`).
 */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [FormsModule, DatePipe, SelectModule, AccountNavComponent],
  template: `
    <app-account-nav />

    <main class="page">
      <h1 class="reforma-page-title">Auditoría</h1>

      @if (error()) {
        <p class="reforma-alert reforma-alert-error"><i class="pi pi-exclamation-circle"></i> {{ error() }}</p>
      }

      <section class="reforma-section filtros">
        <label class="reforma-field accion">
          <span>Acción</span>
          <p-select
            name="accion"
            [options]="acciones"
            [(ngModel)]="fAccion"
            placeholder="Todas las acciones"
            [showClear]="true"
            [filter]="true"
            appendTo="body"
          />
        </label>
        <label class="reforma-field">
          <span>ID de granja</span>
          <input class="reforma-input" name="idGranja" placeholder="g_…" [(ngModel)]="fIdGranja" />
        </label>
        <label class="reforma-field">
          <span>ID de usuario</span>
          <input class="reforma-input" name="idUsuario" placeholder="u_…" [(ngModel)]="fIdUsuario" />
        </label>
        <label class="reforma-field fecha">
          <span>Desde</span>
          <input class="reforma-input" name="desde" type="date" [(ngModel)]="fDesde" />
        </label>
        <label class="reforma-field fecha">
          <span>Hasta</span>
          <input class="reforma-input" name="hasta" type="date" [(ngModel)]="fHasta" />
        </label>
        <div class="filtros-acciones">
          <button type="button" class="reforma-btn" (click)="filtrar()" [disabled]="cargando()">
            <i class="pi pi-filter"></i> Filtrar
          </button>
          <button type="button" class="reforma-btn-ghost" (click)="limpiar()" [disabled]="cargando()">
            <i class="pi pi-times"></i> Limpiar
          </button>
        </div>
      </section>

      @if (cargando()) {
        <p class="reforma-empty">Cargando…</p>
      } @else {
        <p class="total text-dim">{{ totalElementos() }} evento(s)</p>
        <div class="reforma-table-wrap">
          <table class="reforma-table">
            <thead>
              <tr>
                <th class="sortable" [class.is-asc]="orden.esAsc('fecha')" [class.is-desc]="orden.esDesc('fecha')" (click)="orden.alternar('fecha')">Fecha</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('actor')" [class.is-desc]="orden.esDesc('actor')" (click)="orden.alternar('actor')">Actor</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('accion')" [class.is-desc]="orden.esDesc('accion')" (click)="orden.alternar('accion')">Acción</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('descripcion')" [class.is-desc]="orden.esDesc('descripcion')" (click)="orden.alternar('descripcion')">Descripción</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('granja')" [class.is-desc]="orden.esDesc('granja')" (click)="orden.alternar('granja')">Granja</th>
                <th class="sortable" [class.is-asc]="orden.esAsc('ip')" [class.is-desc]="orden.esDesc('ip')" (click)="orden.alternar('ip')">IP</th>
                <th>Datos</th>
              </tr>
            </thead>
            <tbody>
              @for (r of registrosOrdenados(); track r.id) {
                <tr>
                  <td class="nowrap">{{ r.fechaOperacion | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td>
                    <span class="actor">{{ r.actorNombre || '—' }}</span>
                    <small class="text-dim">{{ r.actorEmail || r.idUsuario }}</small>
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
                <tr><td colspan="7" class="reforma-empty">No hay eventos para los filtros aplicados.</td></tr>
              }
            </tbody>
          </table>
        </div>

        <nav class="paginacion">
          <button type="button" class="reforma-btn-ghost" (click)="irA(pagina() - 1)" [disabled]="pagina() <= 0">
            <i class="pi pi-chevron-left"></i> Anterior
          </button>
          <span class="text-dim">Página {{ pagina() + 1 }} de {{ totalPaginas() || 1 }}</span>
          <button
            type="button"
            class="reforma-btn-ghost"
            (click)="irA(pagina() + 1)"
            [disabled]="pagina() + 1 >= totalPaginas()">
            Siguiente <i class="pi pi-chevron-right"></i>
          </button>
        </nav>
      }
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 80rem;
        margin: 0 auto;
        padding: 1.5rem;
      }
      .filtros {
        display: flex;
        gap: 1rem;
        flex-wrap: wrap;
        align-items: flex-end;
        margin-bottom: 1.25rem;
      }
      .reforma-field {
        flex: 1 1 11rem;
        min-width: 8rem;
      }
      .reforma-field.fecha {
        flex: 0 1 10rem;
      }
      .filtros-acciones {
        display: flex;
        gap: 0.5rem;
        align-items: flex-end;
      }
      .total {
        font-size: 0.85rem;
        margin: 0 0 0.75rem;
      }
      .actor {
        display: block;
      }
      .nowrap {
        white-space: nowrap;
      }
      .chip {
        background: var(--reforma-accent-soft);
        color: #ede9fe;
        padding: 0.15rem 0.5rem;
        border-radius: 999px;
        font-size: 0.76rem;
        white-space: nowrap;
      }
      details summary {
        cursor: pointer;
        color: var(--reforma-accent);
      }
      pre {
        background: rgba(0, 0, 0, 0.35);
        border: 1px solid var(--glass-border);
        color: var(--reforma-text);
        padding: 0.5rem;
        border-radius: 8px;
        max-width: 24rem;
        overflow: auto;
        font-size: 0.78rem;
      }
      .json-lbl {
        margin: 0.4rem 0 0.2rem;
        font-weight: 600;
        font-size: 0.78rem;
        color: var(--reforma-text-dim);
      }
      .paginacion {
        display: flex;
        gap: 1rem;
        align-items: center;
        justify-content: center;
        margin-top: 1.25rem;
      }
      td {
        vertical-align: top;
      }
    `,
  ],
})
export class AuditoriaComponent implements OnInit {
  private readonly api = inject(ReformaApiService);

  readonly acciones = ACCIONES_AUDITORIA;

  readonly registros = signal<AuditoriaRegistro[]>([]);
  readonly orden = new OrdenTabla();
  readonly registrosOrdenados = computed(() =>
    this.orden.ordenar(this.registros(), {
      fecha: (r) => r.fechaOperacion,
      actor: (r) => r.actorNombre || r.actorEmail || r.idUsuario,
      accion: (r) => r.accion,
      descripcion: (r) => r.descripcion,
      granja: (r) => r.idGranja,
      ip: (r) => r.ipAddress,
    }),
  );
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
