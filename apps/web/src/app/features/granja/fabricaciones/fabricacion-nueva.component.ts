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
    <a routerLink=".." class="reforma-back"><i class="pi pi-arrow-left"></i> Volver al listado</a>
    <h2 class="reforma-page-title">Nueva fabricación</h2>
    <p class="subtitulo">Completá la cabecera; luego elegirás la fórmula y las veces a fabricar.</p>

    <section class="reforma-section">
      <form (ngSubmit)="crear()" #f="ngForm" class="formulario">
        <label class="reforma-field">
          <span>Código de fabricación</span>
          <input class="reforma-input" name="codigo" [(ngModel)]="codigoFabricacion" maxlength="50" required />
        </label>
        <label class="reforma-field">
          <span>Fecha</span>
          <input class="reforma-input" type="date" name="fecha" [(ngModel)]="fechaFabricacion" [max]="hoy" required />
        </label>
        <label class="reforma-field full">
          <span>Descripción</span>
          <input class="reforma-input" name="descripcion" [(ngModel)]="descripcionFabricacion" maxlength="200" required />
        </label>
        <label class="reforma-field full">
          <span>Observaciones</span>
          <input class="reforma-input" name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
        </label>

        <div class="acciones">
          <a routerLink=".." class="reforma-btn-ghost"><i class="pi pi-times"></i> Cancelar</a>
          <button class="reforma-btn" type="submit" [disabled]="guardando() || f.invalid">
            <i class="pi pi-check"></i> Guardar e ir al detalle
          </button>
        </div>
        @if (error()) {
          <p class="reforma-alert reforma-alert-error">
            <i class="pi pi-exclamation-circle"></i> {{ error() }}
          </p>
        }
      </form>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
        max-width: 64rem;
      }
      .reforma-back {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        color: var(--reforma-accent);
        text-decoration: none;
        font-size: 0.9rem;
        margin-bottom: 0.5rem;
      }
      .reforma-back:hover {
        color: var(--reforma-text);
      }
      .subtitulo {
        color: var(--reforma-text-dim);
        margin: 0 0 1.25rem;
      }
      .formulario {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
        gap: 1rem;
        align-items: end;
      }
      .reforma-field.full {
        grid-column: 1 / -1;
      }
      .acciones {
        grid-column: 1 / -1;
        display: flex;
        gap: 0.75rem;
        justify-content: flex-end;
        margin-top: 0.5rem;
      }
      .reforma-alert {
        grid-column: 1 / -1;
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
