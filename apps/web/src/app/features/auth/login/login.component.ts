import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { AuthStateService } from '../../../core/auth/auth-state.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="auth">
      <section class="card glass-card">
        <span class="brand">REFORMA</span>
        <p class="lead">Gestión integral de granjas</p>
        @if (sessionExpired()) {
          <p class="reforma-alert reforma-alert-warn">Tu sesión expiró. Ingresá de nuevo.</p>
        }
        @if (resetOk()) {
          <p class="reforma-alert reforma-alert-ok">Tu contraseña fue actualizada. Ingresá con la nueva.</p>
        }
        @if (invitacionOk()) {
          <p class="reforma-alert reforma-alert-ok">Tu cuenta de empleado fue activada. Ingresá con tu nueva contraseña.</p>
        }
        <form (ngSubmit)="onSubmit()">
          <label>
            Email
            <input class="reforma-input" type="email" name="email" [(ngModel)]="email" required />
          </label>
          <label>
            Contraseña
            <input class="reforma-input" type="password" name="password" [(ngModel)]="password" required />
          </label>
          @if (error()) {
            <p class="reforma-alert reforma-alert-error">{{ error() }}</p>
          }
          <button class="reforma-btn" type="submit" [disabled]="loading()">
            {{ loading() ? 'Ingresando…' : 'Ingresar' }}
          </button>
        </form>
        <nav class="links">
          <a routerLink="/auth/registro">Crear cuenta</a>
          <a routerLink="/auth/olvide-password">¿Olvidaste tu contraseña?</a>
          <a routerLink="/auth/reenviar-verificacion">Reenviar verificación</a>
          <a routerLink="/">Volver</a>
        </nav>
      </section>
    </main>
  `,
  styles: [
    `
      .auth {
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1.5rem;
        font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
      }
      .card {
        width: 100%;
        max-width: 24rem;
        padding: 2rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .brand {
        font-weight: 800;
        letter-spacing: 0.2em;
        color: var(--reforma-accent);
        font-size: 1.25rem;
      }
      .lead {
        margin: -0.5rem 0 0.5rem;
        color: var(--reforma-text-dim);
      }
      form {
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        color: var(--reforma-text-dim);
        font-size: 0.9rem;
      }
      .links {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        margin-top: 0.5rem;
        font-size: 0.9rem;
      }
    `,
  ],
})
export class LoginComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  email = 'demo@reforma.local';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly sessionExpired = signal(false);
  readonly resetOk = signal(false);
  readonly invitacionOk = signal(false);

  ngOnInit(): void {
    this.sessionExpired.set(this.route.snapshot.queryParamMap.get('sesion') === 'expirada');
    this.resetOk.set(this.route.snapshot.queryParamMap.get('reset') === 'ok');
    this.invitacionOk.set(this.route.snapshot.queryParamMap.get('invitacion') === 'ok');
  }

  onSubmit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.login(this.email, this.password).subscribe({
      next: (res) => {
        this.auth.setSession(res.token);
        this.router.navigate(['/mis-plantas']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message ?? 'No se pudo iniciar sesión');
      },
      complete: () => this.loading.set(false),
    });
  }
}
