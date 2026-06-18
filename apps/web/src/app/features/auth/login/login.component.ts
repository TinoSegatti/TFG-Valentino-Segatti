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
      <h1>REFORMA</h1>
      <p>Gestión integral de granjas</p>
      @if (sessionExpired()) {
        <p class="notice">Tu sesión expiró. Ingresá de nuevo.</p>
      }
      @if (resetOk()) {
        <p class="ok">Tu contraseña fue actualizada. Ingresá con la nueva.</p>
      }
      @if (invitacionOk()) {
        <p class="ok">Tu cuenta de empleado fue activada. Ingresá con tu nueva contraseña.</p>
      }
      <form (ngSubmit)="onSubmit()">
        <label>
          Email
          <input type="email" name="email" [(ngModel)]="email" required />
        </label>
        <label>
          Contraseña
          <input type="password" name="password" [(ngModel)]="password" required />
        </label>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
        <button type="submit" [disabled]="loading()">Ingresar</button>
      </form>
      <nav class="links">
        <a routerLink="/auth/registro">Crear cuenta</a>
        <a routerLink="/auth/olvide-password">¿Olvidaste tu contraseña?</a>
        <a routerLink="/auth/reenviar-verificacion">Reenviar verificación</a>
        <a routerLink="/">Volver</a>
      </nav>
    </main>
  `,
  styles: [
    `
      .auth {
        max-width: 24rem;
        margin: 4rem auto;
        padding: 1.5rem;
        font-family: system-ui, sans-serif;
      }
      form {
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      input {
        padding: 0.5rem;
      }
      .error {
        color: #b91c1c;
      }
      .notice {
        color: #92400e;
        background: #fef3c7;
        padding: 0.5rem 0.75rem;
        border-radius: 4px;
      }
      .ok {
        color: #065f46;
        background: #d1fae5;
        padding: 0.5rem 0.75rem;
        border-radius: 4px;
      }
      .links {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        margin-top: 1rem;
      }
      button {
        padding: 0.6rem;
        background: #166534;
        color: white;
        border: none;
        cursor: pointer;
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
