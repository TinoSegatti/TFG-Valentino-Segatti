import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { debounceTime, Subject } from 'rxjs';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { Proveedor, ProveedorRequest } from '../../../data/models/proveedor.model';

/**
 * Listado, alta rápida y baja lógica de proveedores (RF-PROV-001 / RF-PROV-002).
 * Comparte estética con MateriasPrimasComponent para mantener consistencia visual.
 */
@Component({
  selector: 'app-proveedores',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2>Proveedores</h2>

    <section class="alta">
      <h3>Alta rápida</h3>
      <form (ngSubmit)="crear()" #f="ngForm">
        <label>
          Código
          <input
            name="codigo"
            [(ngModel)]="form.codigoProveedor"
            maxlength="50"
            required
          />
        </label>
        <label>
          Nombre
          <input
            name="nombre"
            [(ngModel)]="form.nombreProveedor"
            maxlength="200"
            required
          />
        </label>
        <label>
          Teléfono
          <input name="telefono" [(ngModel)]="form.telefono" maxlength="50" />
        </label>
        <label>
          Email
          <input type="email" name="email" [(ngModel)]="form.email" maxlength="200" />
        </label>
        <label>
          CUIT
          <input name="cuit" [(ngModel)]="form.cuit" maxlength="20" />
        </label>
        <label>
          Localidad
          <input name="localidad" [(ngModel)]="form.localidad" maxlength="100" />
        </label>
        <button type="submit" [disabled]="creando() || f.invalid">Crear</button>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
      </form>
    </section>

    <section class="lista">
      <header class="lista-header">
        <h3>Activos ({{ items().length }})</h3>
        <input
          class="buscar"
          type="search"
          placeholder="Buscar por nombre…"
          [ngModel]="filtro()"
          (ngModelChange)="onFiltroChange($event)"
        />
      </header>

      @if (cargando()) {
        <p>Cargando…</p>
      } @else if (items().length === 0) {
        <p class="vacio">Todavía no cargaste ningún proveedor (o ninguno coincide con el filtro).</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Nombre</th>
              <th>Teléfono</th>
              <th>Localidad</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (p of items(); track p.id) {
              <tr>
                <td>{{ p.codigoProveedor }}</td>
                <td>{{ p.nombreProveedor }}</td>
                <td>{{ p.telefono ?? '—' }}</td>
                <td>{{ p.localidad ?? '—' }}</td>
                <td>
                  <button type="button" class="danger" (click)="desactivar(p)">Dar de baja</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      h2 {
        margin-top: 0;
      }
      .alta {
        margin-bottom: 2rem;
      }
      form {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        align-items: flex-end;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.85rem;
      }
      input {
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
      }
      button {
        padding: 0.5rem 1rem;
        background: #166534;
        color: white;
        border: none;
        border-radius: 4px;
        cursor: pointer;
      }
      button:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      button.danger {
        background: #b91c1c;
      }
      .error {
        color: #b91c1c;
        margin: 0.5rem 0 0;
      }
      .vacio {
        color: #6b7280;
      }
      .lista-header {
        display: flex;
        align-items: center;
        gap: 1rem;
      }
      .lista-header h3 {
        margin: 0;
        flex: 1;
      }
      .buscar {
        min-width: 220px;
        padding: 0.4rem 0.5rem;
        border: 1px solid #d1d5db;
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
    `,
  ],
})
export class ProveedoresComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly items = signal<Proveedor[]>([]);
  readonly cargando = signal(true);
  readonly creando = signal(false);
  readonly error = signal<string | null>(null);
  readonly filtro = signal<string>('');

  /**
   * Debounce de 300ms sobre el filtro de búsqueda para no martillar al backend con cada tecla.
   * Cada cambio en el input alimenta este Subject; el operador `debounceTime` espera la pausa
   * antes de disparar `recargar`.
   */
  private readonly busquedaPendiente = new Subject<string>();

  form: ProveedorRequest = vacio();

  // Solo computed lo dejo como expresión expresiva si en el futuro quiero KPIs derivados
  readonly hayDatos = computed(() => this.items().length > 0);

  private get idGranja(): string {
    return this.route.parent?.snapshot.paramMap.get('idGranja') ?? '';
  }

  ngOnInit(): void {
    this.busquedaPendiente
      .pipe(debounceTime(300))
      .subscribe((termino) => this.recargar(termino));
    this.recargar();
  }

  onFiltroChange(valor: string): void {
    this.filtro.set(valor);
    this.busquedaPendiente.next(valor);
  }

  recargar(buscar?: string): void {
    this.cargando.set(true);
    this.api.getProveedores(this.idGranja, buscar?.trim() || undefined).subscribe({
      next: (list) => {
        this.items.set(list);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los proveedores');
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.form.codigoProveedor.trim() || !this.form.nombreProveedor.trim()) {
      return;
    }
    this.creando.set(true);
    this.error.set(null);
    this.api.crearProveedor(this.idGranja, this.form).subscribe({
      next: (p) => {
        this.items.update((prev) =>
          [...prev, p].sort((a, b) => a.nombreProveedor.localeCompare(b.nombreProveedor)),
        );
        this.form = vacio();
        this.creando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.creando.set(false);
        this.error.set(err.error?.message ?? 'No se pudo crear el proveedor');
      },
    });
  }

  desactivar(p: Proveedor): void {
    if (!confirm(`¿Dar de baja al proveedor "${p.nombreProveedor}"?`)) {
      return;
    }
    this.api.desactivarProveedor(this.idGranja, p.id).subscribe({
      next: () => this.items.update((prev) => prev.filter((x) => x.id !== p.id)),
      error: () => this.error.set('No se pudo dar de baja'),
    });
  }
}

function vacio(): ProveedorRequest {
  return {
    codigoProveedor: '',
    nombreProveedor: '',
    telefono: '',
    email: '',
    cuit: '',
    direccion: '',
    localidad: '',
    notas: '',
  };
}
