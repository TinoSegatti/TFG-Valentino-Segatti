import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ReformaApiService } from '../../../data/api/reforma-api.service';

@Component({
  selector: 'app-olvide-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="auth">
      <h1>Recuperar contraseña</h1>
      @if (enviado()) {
        <p class="notice">
          Si el email corresponde a una cuenta, te enviamos instrucciones para restablecer tu
          contraseña. El enlace vence en 1 hora.
        </p>
        <p><a routerLink="/auth/login">Volver a ingresar</a></p>
      } @else {
        <p>Ingresá tu email y te enviamos un enlace para crear una nueva contraseña.</p>
        <form (ngSubmit)="onSubmit()">
          <label>
            Email
            <input type="email" name="email" [(ngModel)]="email" required />
          </label>
          <button type="submit" [disabled]="loading()">Enviar enlace</button>
        </form>
        <p><a routerLink="/auth/login">Volver</a></p>
      }
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
      .notice {
        color: #065f46;
        background: #d1fae5;
        padding: 0.5rem 0.75rem;
        border-radius: 4px;
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
export class OlvidePasswordComponent {
  private readonly api = inject(ReformaApiService);

  email = '';
  readonly loading = signal(false);
  readonly enviado = signal(false);

  onSubmit(): void {
    this.loading.set(true);
    // Respuesta genérica del backend (anti-enumeración): siempre mostramos confirmación.
    this.api.solicitarReset(this.email).subscribe({
      next: () => {
        this.loading.set(false);
        this.enviado.set(true);
      },
      error: () => {
        this.loading.set(false);
        this.enviado.set(true);
      },
    });
  }
}
