import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { PlanSuscripcion } from '../../data/models/usuario.model';
import { Suscripcion } from '../../data/models/suscripcion.model';

type EstadoRetorno = 'verificando' | 'activa' | 'rechazado' | 'pendiente';

/**
 * Página de vuelta del checkout (RD-P11). La pantalla simulada navega acá; en modo MP
 * es la back_url del checkout hospedado. Como la confirmación puede llegar por webhook
 * (asíncrona), consulta la suscripción con un polling corto hasta verla ACTIVA y
 * refresca el perfil para mostrar el plan vigente.
 */
@Component({
  selector: 'app-planes-retorno',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page">
      <section class="glass-card-strong tarjeta">
        @switch (estado()) {
          @case ('verificando') {
            <i class="pi pi-spin pi-spinner icono verificando"></i>
            <h1>Verificando tu pago…</h1>
            <p class="text-dim">Estamos confirmando la suscripción con la pasarela.</p>
          }
          @case ('activa') {
            <i class="pi pi-check-circle icono ok"></i>
            <h1>¡Suscripción activa!</h1>
            <p class="text-dim">
              Tu plan <strong>{{ planActivo() }}</strong> ya está vigente. Gracias por confiar
              en REFORMA.
            </p>
            <div class="acciones">
              <a class="reforma-btn" routerLink="/suscripcion">Ver mi suscripción</a>
              <a class="reforma-btn-ghost" routerLink="/mis-plantas">Ir a mis granjas</a>
            </div>
          }
          @case ('rechazado') {
            <i class="pi pi-times-circle icono error"></i>
            <h1>El pago fue rechazado</h1>
            <p class="text-dim">
              No se realizó ningún cobro y tu plan actual no cambió. Podés intentarlo de nuevo
              cuando quieras.
            </p>
            <div class="acciones">
              <a class="reforma-btn" routerLink="/planes">Volver a los planes</a>
              <a class="reforma-btn-ghost" routerLink="/suscripcion">Ver mi suscripción</a>
            </div>
          }
          @case ('pendiente') {
            <i class="pi pi-clock icono verificando"></i>
            <h1>Pago en proceso</h1>
            <p class="text-dim">
              Todavía no recibimos la confirmación de la pasarela. Puede demorar unos minutos;
              revisá el estado desde "Mi suscripción".
            </p>
            <div class="acciones">
              <a class="reforma-btn" routerLink="/suscripcion">Ver mi suscripción</a>
              <a class="reforma-btn-ghost" routerLink="/planes">Volver a los planes</a>
            </div>
          }
        }
      </section>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 32rem;
        margin: 0 auto;
        padding: 4rem 1.5rem;
      }
      .tarjeta {
        padding: 2.5rem 2rem;
        text-align: center;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.75rem;
      }
      .icono {
        font-size: 3rem;
      }
      .icono.ok {
        color: var(--reforma-ok, #34d399);
      }
      .icono.error {
        color: #fb7185;
      }
      .icono.verificando {
        color: var(--reforma-accent);
      }
      h1 {
        margin: 0;
        color: var(--reforma-text);
        font-size: 1.5rem;
      }
      .text-dim {
        color: var(--reforma-text-dim);
        margin: 0;
      }
      .acciones {
        display: flex;
        gap: 0.75rem;
        margin-top: 1rem;
        flex-wrap: wrap;
        justify-content: center;
      }
    `,
  ],
})
export class RetornoComponent implements OnInit, OnDestroy {
  private static readonly MAX_INTENTOS = 8;
  private static readonly INTERVALO_MS = 1500;

  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly estado = signal<EstadoRetorno>('verificando');
  readonly planActivo = signal<PlanSuscripcion | null>(null);

  private planEsperado: PlanSuscripcion | null = null;
  private intentos = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    this.planEsperado = qp.get('plan') as PlanSuscripcion | null;
    if (qp.get('resultado') === 'rechazado') {
      this.estado.set('rechazado');
      return;
    }
    this.consultar();
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
  }

  private consultar(): void {
    this.intentos++;
    this.api.getSuscripcion().subscribe({
      next: (s) => this.evaluar(s),
      error: () => this.reintentarODarPorPendiente(),
    });
  }

  private evaluar(s: Suscripcion): void {
    const activaDelPlan =
      s.estado === 'ACTIVA' && (this.planEsperado === null || s.plan === this.planEsperado);
    if (activaDelPlan) {
      this.planActivo.set(s.planEfectivo);
      this.estado.set('activa');
      // RD-P11: refresco del perfil para que la UI muestre el plan vigente (el backend
      // gatea por BD, no por el claim del JWT, así que no hace falta re-loguear).
      this.api.getPerfil().subscribe({ next: () => undefined, error: () => undefined });
      return;
    }
    this.reintentarODarPorPendiente();
  }

  private reintentarODarPorPendiente(): void {
    if (this.intentos >= RetornoComponent.MAX_INTENTOS) {
      this.estado.set('pendiente');
      return;
    }
    this.timer = setTimeout(() => this.consultar(), RetornoComponent.INTERVALO_MS);
  }
}
