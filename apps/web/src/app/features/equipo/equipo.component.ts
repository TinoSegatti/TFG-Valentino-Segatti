import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { SelectModule } from 'primeng/select';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { decodeJwtClaims } from '../../core/auth/jwt.utils';
import { EmpleadoResponse, RolEmpleado } from '../../data/models/usuario.model';
import { mensajeErrorHttp } from '../../core/http/api-error.util';
import { AccountNavComponent } from '../../shared/account-nav.component';
import { DecimalPipe } from '@angular/common';
import { KpiCardComponent } from '../granja/shared/kpi-card.component';
import { ChartCardComponent } from '../granja/shared/chart-card.component';
import { ApexChartComponent } from '../granja/shared/apex-chart.component';
import { donutChart } from '../granja/shared/apex-charts';
import { OrdenTabla } from '../../shared/orden-tabla';

const ROL_LABEL: Record<string, string> = {
  OWNER: 'Dueño',
  ADMIN: 'Admin (jefe)',
  EDITOR: 'Editor',
  LECTOR: 'Lector',
};

interface OpcionRol {
  label: string;
  value: RolEmpleado;
}

/**
 * Gestión de equipo (Etapa 4). Visible para el dueño (OWNER) y el jefe (ADMIN). Las reglas de
 * jerarquía las impone el backend (un jefe no toca a otro ADMIN ni designa ADMIN); acá solo se
 * adapta la UI para no ofrecer acciones que el servidor va a rechazar.
 */
@Component({
  selector: 'app-equipo',
  standalone: true,
  imports: [
    FormsModule,
    SelectModule,
    AccountNavComponent,
    DecimalPipe,
    KpiCardComponent,
    ChartCardComponent,
    ApexChartComponent,
  ],
  template: `
    <app-account-nav />

    <main class="page">
      <h1 class="reforma-page-title">Equipo</h1>

      <section class="rf-grid-kpis">
        <reforma-kpi-card style="--rf-i: 0" icon="pi-users" label="Miembros" [value]="stats().total | number: '1.0-0'" />
        <reforma-kpi-card style="--rf-i: 1" icon="pi-check-circle" label="Activos" [value]="stats().activos | number: '1.0-0'" accent="#34d399" />
        <reforma-kpi-card style="--rf-i: 2" icon="pi-clock" label="Pendientes" [value]="stats().pendientes | number: '1.0-0'" accent="#fbbf24" />
        <reforma-kpi-card style="--rf-i: 3" icon="pi-shield" label="Admins" [value]="stats().admins | number: '1.0-0'" accent="#06b6d4" />
      </section>

      <section class="rf-panel">
        <reforma-chart-card
          title="Empleados por rol"
          icon="pi-chart-pie"
          [loading]="cargando()"
          [empty]="!cargando() && porRol().valores.length === 0"
          emptyText="Invitá empleados para ver la distribución por rol"
        >
          <reforma-apex [options]="chartRol()" />
        </reforma-chart-card>
      </section>

      @if (mensaje()) {
        <p class="reforma-alert reforma-alert-ok"><i class="pi pi-check-circle"></i> {{ mensaje() }}</p>
      }
      @if (error()) {
        <p class="reforma-alert reforma-alert-error"><i class="pi pi-exclamation-circle"></i> {{ error() }}</p>
      }

      <section class="reforma-section invitar">
        <h2 class="reforma-section-title">Invitar empleado</h2>
        <form (ngSubmit)="invitar()">
          <div class="campos">
            <label class="reforma-field">
              <span>Email</span>
              <input class="reforma-input" name="email" type="email" placeholder="empleado@email.com" [(ngModel)]="nuevoEmail" required />
            </label>
            <label class="reforma-field">
              <span>Nombre</span>
              <input class="reforma-input" name="nombre" placeholder="Nombre" [(ngModel)]="nuevoNombre" required />
            </label>
            <label class="reforma-field">
              <span>Apellido</span>
              <input class="reforma-input" name="apellido" placeholder="Apellido" [(ngModel)]="nuevoApellido" required />
            </label>
            <label class="reforma-field rol">
              <span>Rol</span>
              <p-select
                name="rol"
                [options]="opcionesNuevoRol()"
                optionLabel="label"
                optionValue="value"
                [(ngModel)]="nuevoRol"
                appendTo="body"
              />
            </label>
          </div>
          <div class="acciones">
            <button class="reforma-btn" type="submit" [disabled]="enviando()">
              <i class="pi pi-send"></i> Invitar
            </button>
          </div>
        </form>
        <small class="text-dim">Se enviará un enlace de invitación. La contraseña la fija el empleado al aceptar.</small>
      </section>

      <section class="lista">
        <h2 class="reforma-section-title">Miembros</h2>
        @if (cargando()) {
          <p class="reforma-empty">Cargando…</p>
        } @else {
          <div class="reforma-table-wrap">
            <table class="reforma-table">
              <thead>
                <tr>
                  <th class="sortable" [class.is-asc]="orden.esAsc('email')" [class.is-desc]="orden.esDesc('email')" (click)="orden.alternar('email')">Email</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('nombre')" [class.is-desc]="orden.esDesc('nombre')" (click)="orden.alternar('nombre')">Nombre</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('rol')" [class.is-desc]="orden.esDesc('rol')" (click)="orden.alternar('rol')">Rol</th>
                  <th class="sortable" [class.is-asc]="orden.esAsc('estado')" [class.is-desc]="orden.esDesc('estado')" (click)="orden.alternar('estado')">Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (e of empleadosOrdenados(); track e.id) {
                  <tr>
                    <td>{{ e.email }}</td>
                    <td>{{ e.nombreUsuario }} {{ e.apellidoUsuario }}</td>
                    <td>
                      @if (puedeGestionar(e)) {
                        <select
                          class="reforma-input rol-select"
                          [ngModel]="e.rolEmpleado"
                          (ngModelChange)="cambiarRol(e, $event)"
                        >
                          @for (op of opcionesRol(e); track op.value) {
                            <option [value]="op.value">{{ op.label }}</option>
                          }
                        </select>
                      } @else {
                        <span class="chip rol">{{ rolLabel(e.rolEmpleado) }}</span>
                      }
                    </td>
                    <td>
                      <span class="chip" [class]="estadoClase(e)">{{ estado(e) }}</span>
                    </td>
                    <td class="acciones">
                      @if (puedeGestionar(e)) {
                        <button
                          type="button"
                          [class]="e.activoComoEmpleado ? 'reforma-btn-danger' : 'reforma-btn-ghost reforma-btn-sm'"
                          (click)="cambiarEstado(e, !e.activoComoEmpleado)">
                          <i class="pi" [class.pi-ban]="e.activoComoEmpleado" [class.pi-refresh]="!e.activoComoEmpleado"></i>
                          {{ e.activoComoEmpleado ? 'Desactivar' : 'Reactivar' }}
                        </button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="reforma-empty">Todavía no hay empleados. Invitá al primero.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 64rem;
        margin: 0 auto;
        padding: 1.5rem;
      }
      .invitar {
        margin-bottom: 2rem;
      }
      .invitar form {
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .invitar .campos {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
        gap: 1rem;
        align-items: end;
      }
      .reforma-field.rol {
        min-width: 11rem;
      }
      /* Botón Invitar siempre alineado a la derecha del formulario. */
      .invitar .acciones {
        display: flex;
        justify-content: flex-end;
      }
      .invitar .acciones .reforma-btn {
        flex: 0 0 auto;
      }
      small {
        display: block;
        margin-top: 0.75rem;
        font-size: 0.82rem;
      }
      .lista .acciones {
        text-align: right;
        width: 1%;
        white-space: nowrap;
      }
      .chip {
        display: inline-flex;
        align-items: center;
        padding: 0.2rem 0.6rem;
        border-radius: 999px;
        font-size: 0.78rem;
        font-weight: 600;
      }
      .chip.rol {
        background: var(--reforma-accent-soft);
        color: #ede9fe;
      }
      .chip.ok {
        background: rgba(52, 211, 153, 0.16);
        color: #bbf7d0;
      }
      .chip.warn {
        background: rgba(251, 191, 36, 0.16);
        color: #fde68a;
      }
      .chip.off {
        background: rgba(248, 113, 113, 0.14);
        color: #fecaca;
      }
      .rol-select {
        width: auto;
        min-width: 10rem;
        padding: 0.3rem 0.6rem;
        cursor: pointer;
      }
    `,
  ],
})
export class EquipoComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);

  readonly empleados = signal<EmpleadoResponse[]>([]);
  readonly orden = new OrdenTabla();
  readonly empleadosOrdenados = computed(() =>
    this.orden.ordenar(this.empleados(), {
      email: (e) => e.email,
      nombre: (e) => `${e.nombreUsuario} ${e.apellidoUsuario}`,
      rol: (e) => e.rolEmpleado,
      estado: (e) => this.estado(e),
    }),
  );
  readonly cargando = signal(true);
  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);
  readonly mensaje = signal<string | null>(null);

  /** Rol del usuario autenticado: 'OWNER' si es dueño, o su rol de empleado (ADMIN/EDITOR/LECTOR). */
  private readonly rolActual = computed(() => {
    const claims = decodeJwtClaims(this.auth.getToken());
    return claims?.esEmpleado ? claims.rolEmpleado ?? null : 'OWNER';
  });

  /** Un dueño puede designar ADMIN; un jefe solo asigna EDITOR/LECTOR. */
  readonly rolesAsignables = computed<RolEmpleado[]>(() =>
    this.rolActual() === 'OWNER' ? ['ADMIN', 'EDITOR', 'LECTOR'] : ['EDITOR', 'LECTOR'],
  );

  /** Opciones {label,value} para el select de invitación. */
  readonly opcionesNuevoRol = computed<OpcionRol[]>(() =>
    this.rolesAsignables().map((r) => ({ label: ROL_LABEL[r] ?? r, value: r })),
  );

  nuevoEmail = '';
  nuevoNombre = '';
  nuevoApellido = '';
  nuevoRol: RolEmpleado = 'EDITOR';

  readonly stats = computed(() => {
    const es = this.empleados();
    return {
      total: es.length,
      activos: es.filter((e) => e.activoComoEmpleado && e.fechaVinculacion).length,
      pendientes: es.filter((e) => !e.fechaVinculacion).length,
      admins: es.filter((e) => e.rolEmpleado === 'ADMIN').length,
    };
  });

  readonly porRol = computed(() => {
    const acum = new Map<string, number>();
    for (const e of this.empleados()) {
      const label = ROL_LABEL[e.rolEmpleado] ?? e.rolEmpleado;
      acum.set(label, (acum.get(label) ?? 0) + 1);
    }
    return {
      labels: [...acum.keys()],
      valores: [...acum.values()],
    };
  });

  readonly chartRol = computed(() =>
    donutChart({
      labels: this.porRol().labels,
      series: this.porRol().valores,
      totalLabel: 'Miembros',
      height: 300,
    }),
  );

  ngOnInit(): void {
    this.cargar();
  }

  rolLabel(r: RolEmpleado): string {
    return ROL_LABEL[r] ?? r;
  }

  /** Opciones de rol por fila: las asignables + el rol actual si fuese ADMIN (para mostrarlo). */
  opcionesRol(e: EmpleadoResponse): OpcionRol[] {
    const base = [...this.rolesAsignables()];
    if (e.rolEmpleado === 'ADMIN' && !base.includes('ADMIN')) {
      base.unshift('ADMIN');
    }
    return base.map((r) => ({ label: ROL_LABEL[r] ?? r, value: r }));
  }

  private cargar(): void {
    this.cargando.set(true);
    this.api.getEmpleados().subscribe({
      next: (list) => {
        this.empleados.set(list);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cargar el equipo.'));
        this.cargando.set(false);
      },
    });
  }

  /** Un jefe (ADMIN) no puede gestionar a otro ADMIN; el dueño puede gestionar a todos. */
  puedeGestionar(e: EmpleadoResponse): boolean {
    return this.rolActual() === 'OWNER' || e.rolEmpleado !== 'ADMIN';
  }

  estado(e: EmpleadoResponse): string {
    if (!e.fechaVinculacion) {
      return 'Pendiente';
    }
    return e.activoComoEmpleado ? 'Activo' : 'Desactivado';
  }

  estadoClase(e: EmpleadoResponse): string {
    if (!e.fechaVinculacion) return 'warn';
    return e.activoComoEmpleado ? 'ok' : 'off';
  }

  invitar(): void {
    this.limpiarAvisos();
    this.enviando.set(true);
    this.api
      .invitarEmpleado({
        email: this.nuevoEmail,
        nombreUsuario: this.nuevoNombre,
        apellidoUsuario: this.nuevoApellido,
        rol: this.nuevoRol,
      })
      .subscribe({
        next: () => {
          this.mensaje.set('Invitación enviada a ' + this.nuevoEmail);
          this.nuevoEmail = '';
          this.nuevoNombre = '';
          this.nuevoApellido = '';
          this.nuevoRol = 'EDITOR';
          this.enviando.set(false);
          this.cargar();
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(mensajeErrorHttp(err, 'No se pudo enviar la invitación.'));
          this.enviando.set(false);
        },
      });
  }

  cambiarRol(e: EmpleadoResponse, rol: RolEmpleado): void {
    if (rol === e.rolEmpleado) {
      return;
    }
    this.limpiarAvisos();
    this.api.cambiarRolEmpleado(e.id, rol).subscribe({
      next: () => {
        this.mensaje.set('Rol actualizado.');
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cambiar el rol.'));
        this.cargar();
      },
    });
  }

  cambiarEstado(e: EmpleadoResponse, activo: boolean): void {
    this.limpiarAvisos();
    this.api.cambiarEstadoEmpleado(e.id, activo).subscribe({
      next: () => {
        this.mensaje.set(activo ? 'Empleado reactivado.' : 'Empleado desactivado.');
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cambiar el estado.'));
      },
    });
  }

  private limpiarAvisos(): void {
    this.error.set(null);
    this.mensaje.set(null);
  }
}
