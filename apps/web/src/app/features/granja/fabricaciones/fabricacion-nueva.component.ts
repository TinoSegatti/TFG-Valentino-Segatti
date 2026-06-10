import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { hoyIsoFabricacion } from '../../../data/models/fabricacion.model';

@Component({
  selector: 'app-fabricacion-nueva',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <a routerLink=".." class="back">← Volver al listado</a>
    <h2>Nueva fabricacion</h2>
    <p class="subtitulo">Completá la cabecera; luego elegirás la formula y las veces a fabricar.</p>

    <form (ngSubmit)="crear()" #f="ngForm" class="formulario">
      <label>
        Codigo de fabricacion
        <input name="codigo" [(ngModel)]="codigoFabricacion" maxlength="50" required />
      </label>
      <label>
        Fecha
        <input type="date" name="fecha" [(ngModel)]="fechaFabricacion" [max]="hoy" required />
      </label>
      <label class="ancho">
        Descripcion
        <input name="descripcion" [(ngModel)]="descripcionFabricacion" maxlength="200" required />
      </label>
      <label class="ancho">
        Observaciones
        <input name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
      </label>

      <div class="acciones-form">
        <button type="submit" [disabled]="guardando() || f.invalid">Guardar e ir al detalle</button>
        <a routerLink=".." class="btn-cancelar">Cancelar</a>
      </div>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }
    </form>
  `,
  styles: [
    `
      :host {
        display: block;
        max-width: 900px;
      }
      .back {
        color: #166534;
        text-decoration: none;
      }
      .subtitulo {
        color: #6b7280;
      }
      .formulario {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        padding: 1rem;
        border: 1px solid #e5e7eb;
        border-radius: 6px;
        background: #fafafa;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.85rem;
        min-width: 200px;
      }
      label.ancho {
        flex: 1 1 100%;
      }
      .acciones-form {
        display: flex;
        gap: 0.75rem;
        flex: 1 1 100%;
      }
      button[type='submit'] {
        background: #166534;
        color: white;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
        cursor: pointer;
      }
      .btn-cancelar {
        color: #374151;
        align-self: center;
      }
      .error {
        color: #b91c1c;
        flex: 1 1 100%;
      }
    `,
  ],
})
export class FabricacionNuevaComponent {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  codigoFabricacion = '';
  descripcionFabricacion = '';
  fechaFabricacion = hoyIsoFabricacion();
  observaciones = '';
  hoy = hoyIsoFabricacion();
  guardando = signal(false);
  error = signal<string | null>(null);

  crear(): void {
    const idGranja = this.route.parent!.snapshot.paramMap.get('idGranja')!;
    this.guardando.set(true);
    this.error.set(null);
    this.api
      .crearFabricacionCabecera(idGranja, {
        codigoFabricacion: this.codigoFabricacion.trim(),
        descripcionFabricacion: this.descripcionFabricacion.trim(),
        fechaFabricacion: this.fechaFabricacion,
        observaciones: this.observaciones.trim() || undefined,
      })
      .subscribe({
        next: (fab) => {
          this.guardando.set(false);
          this.router.navigate(['..', fab.id], { relativeTo: this.route });
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeErrorHttp(err, 'No se pudo crear la fabricacion'));
        },
      });
  }
}
