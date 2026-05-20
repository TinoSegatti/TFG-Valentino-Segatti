import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
      <p><a routerLink="/">Volver</a></p>
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
export class LoginComponent {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

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
