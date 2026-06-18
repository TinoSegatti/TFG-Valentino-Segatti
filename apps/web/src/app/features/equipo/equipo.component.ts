import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { decodeJwtClaims } from '../../core/auth/jwt.utils';
import { EmpleadoResponse, RolEmpleado } from '../../data/models/usuario.model';
import { mensajeErrorHttp } from '../../core/http/api-error.util';

/**
 * Gestión de equipo (Etapa 4). Visible para el dueño (OWNER) y el jefe (ADMIN). Las reglas de
 * jerarquía las impone el backend (un jefe no toca a otro ADMIN ni designa ADMIN); acá solo se
 * adapta la UI para no ofrecer acciones que el servidor va a rechazar.
 */
@Component({
  selector: 'app-equipo',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <header class="bar">
      <h1>Equipo</h1>
      <a routerLink="/mis-plantas">Volver</a>
    </header>

    <main class="contenido">
      @if (mensaje()) {
        <p class="ok">{{ mensaje() }}</p>
      }
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }

      <section class="invitar">
        <h2>Invitar empleado</h2>
        <form (ngSubmit)="invitar()">
          <input name="email" type="email" placeholder="Email" [(ngModel)]="nuevoEmail" required />
          <input name="nombre" placeholder="Nombre" [(ngModel)]="nuevoNombre" required />
          <input name="apellido" placeholder="Apellido" [(ngModel)]="nuevoApellido" required />
          <select name="rol" [(ngModel)]="nuevoRol">
            @for (r of rolesAsignables(); track r) {
              <option [value]="r">{{ r }}</option>
            }
          </select>
          <button type="submit" [disabled]="enviando()">Invitar</button>
        </form>
        <small>Se enviará un enlace de invitación. La contraseña la fija el empleado al aceptar.</small>
      </section>

      <section class="lista">
        <h2>Miembros</h2>
        @if (cargando()) {
          <p>Cargando…</p>
        } @else {
          <table>
            <thead>
              <tr><th>Email</th><th>Nombre</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr>
            </thead>
            <tbody>
              @for (e of empleados(); track e.id) {
                <tr>
                  <td>{{ e.email }}</td>
                  <td>{{ e.nombreUsuario }} {{ e.apellidoUsuario }}</td>
                  <td>
                    <select
                      [ngModel]="e.rolEmpleado"
                      (ngModelChange)="cambiarRol(e, $event)"
                      [disabled]="!puedeGestionar(e)">
                      @for (r of rolesAsignables(); track r) {
                        <option [value]="r">{{ r }}</option>
                      }
                      @if (e.rolEmpleado === 'ADMIN') {
                        <option value="ADMIN">ADMIN</option>
                      }
                    </select>
                  </td>
                  <td>{{ estado(e) }}</td>
                  <td>
                    @if (puedeGestionar(e)) {
                      <button type="button" (click)="cambiarEstado(e, !e.activoComoEmpleado)">
                        {{ e.activoComoEmpleado ? 'Desactivar' : 'Reactivar' }}
                      </button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="5">Todavía no hay empleados. Invitá al primero.</td></tr>
              }
            </tbody>
          </table>
        }
      </section>
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
        max-width: 60rem;
      }
      section {
        margin-bottom: 2rem;
      }
      form {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
      input,
      select {
        padding: 0.4rem;
      }
      table {
        width: 100%;
        border-collapse: collapse;
      }
      th,
      td {
        text-align: left;
        padding: 0.5rem;
        border-bottom: 1px solid #e5e7eb;
      }
      button {
        padding: 0.4rem 0.7rem;
        background: #166534;
        color: white;
        border: none;
        cursor: pointer;
      }
      small {
        color: #6b7280;
      }
      .error {
        color: #b91c1c;
      }
      .ok {
        color: #166534;
      }
    `,
  ],
})
export class EquipoComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);

  readonly empleados = signal<EmpleadoResponse[]>([]);
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

  nuevoEmail = '';
  nuevoNombre = '';
  nuevoApellido = '';
  nuevoRol: RolEmpleado = 'EDITOR';

  ngOnInit(): void {
    this.cargar();
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
