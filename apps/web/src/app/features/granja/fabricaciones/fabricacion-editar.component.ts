import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { hoyIsoFabricacion } from '../../../data/models/fabricacion.model';

@Component({
  selector: 'app-fabricacion-editar',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <a [routerLink]="rutaDetalle()" class="reforma-back">
      <i class="pi pi-arrow-left"></i> Volver al detalle
    </a>
    <h2 class="reforma-page-title">Editar cabecera de fabricación</h2>

    @if (cargando()) {
      <p class="reforma-empty">Cargando…</p>
    } @else {
      <section class="reforma-section">
        <form (ngSubmit)="guardar()" #f="ngForm" class="formulario">
          <label class="reforma-field">
            <span>Código</span>
            <input class="reforma-input" name="codigo" [(ngModel)]="codigoFabricacion" required maxlength="50" />
          </label>
          <label class="reforma-field">
            <span>Fecha</span>
            <input class="reforma-input" type="date" name="fecha" [(ngModel)]="fechaFabricacion" [max]="hoy" required />
          </label>
          <label class="reforma-field full">
            <span>Descripción</span>
            <input class="reforma-input" name="descripcion" [(ngModel)]="descripcionFabricacion" required maxlength="200" />
          </label>
          <label class="reforma-field full">
            <span>Observaciones</span>
            <input class="reforma-input" name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
          </label>

          <div class="acciones">
            <a [routerLink]="rutaDetalle()" class="reforma-btn-ghost">
              <i class="pi pi-times"></i> Cancelar
            </a>
            <button class="reforma-btn" type="submit" [disabled]="guardando() || f.invalid">
              <i class="pi pi-check"></i> Guardar
            </button>
          </div>
          @if (error()) {
            <p class="reforma-alert reforma-alert-error">
              <i class="pi pi-exclamation-circle"></i> {{ error() }}
            </p>
          }
        </form>
      </section>
    }
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
export class FabricacionEditarComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  codigoFabricacion = '';
  descripcionFabricacion = '';
  fechaFabricacion = '';
  observaciones = '';
  hoy = hoyIsoFabricacion();
  cargando = signal(true);
  guardando = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    const idGranja = this.route.parent!.snapshot.paramMap.get('idGranja')!;
    const idFabricacion = this.route.snapshot.paramMap.get('idFabricacion')!;
    this.api.getFabricacion(idGranja, idFabricacion).subscribe({
      next: (fab) => {
        this.codigoFabricacion = fab.codigoFabricacion;
        this.descripcionFabricacion = fab.descripcionFabricacion;
        this.fechaFabricacion = fab.fechaFabricacion;
        this.observaciones = fab.observaciones ?? '';
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeErrorHttp(err, 'No se pudo cargar la fabricacion'));
        this.cargando.set(false);
      },
    });
  }

  rutaDetalle(): string[] {
    return ['..', this.route.snapshot.paramMap.get('idFabricacion')!];
  }

  guardar(): void {
    const idGranja = this.route.parent!.snapshot.paramMap.get('idGranja')!;
    const idFabricacion = this.route.snapshot.paramMap.get('idFabricacion')!;
    this.guardando.set(true);
    this.api
      .actualizarFabricacionCabecera(idGranja, idFabricacion, {
        codigoFabricacion: this.codigoFabricacion.trim(),
        descripcionFabricacion: this.descripcionFabricacion.trim(),
        fechaFabricacion: this.fechaFabricacion,
        observaciones: this.observaciones.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.router.navigate(['..', idFabricacion], { relativeTo: this.route });
        },
        error: (err: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeErrorHttp(err, 'No se pudo guardar la cabecera'));
        },
      });
  }
}
