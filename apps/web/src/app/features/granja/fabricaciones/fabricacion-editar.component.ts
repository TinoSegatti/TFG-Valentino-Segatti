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
    <a [routerLink]="rutaDetalle()" class="back">← Volver al detalle</a>
    <h2>Editar cabecera de fabricacion</h2>

    @if (cargando()) {
      <p>Cargando…</p>
    } @else {
      <form (ngSubmit)="guardar()" #f="ngForm" class="formulario">
        <label>
          Codigo
          <input name="codigo" [(ngModel)]="codigoFabricacion" required maxlength="50" />
        </label>
        <label>
          Fecha
          <input type="date" name="fecha" [(ngModel)]="fechaFabricacion" [max]="hoy" required />
        </label>
        <label>
          Descripcion
          <input name="descripcion" [(ngModel)]="descripcionFabricacion" required maxlength="200" />
        </label>
        <label>
          Observaciones
          <input name="observaciones" [(ngModel)]="observaciones" maxlength="2000" />
        </label>
        <button type="submit" [disabled]="guardando() || f.invalid">Guardar</button>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
      </form>
    }
  `,
  styles: [
    `
      .back {
        color: #166534;
      }
      .formulario {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        max-width: 480px;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      button[type='submit'] {
        background: #166534;
        color: white;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 4px;
        cursor: pointer;
        align-self: flex-start;
      }
      .error {
        color: #b91c1c;
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
