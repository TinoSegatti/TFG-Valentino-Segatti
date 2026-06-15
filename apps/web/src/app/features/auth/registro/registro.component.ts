import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="auth">
      <h1>Crear cuenta</h1>
      <p>Registrate para gestionar tu granja</p>

      @if (registrado()) {
        <p class="notice">
          Tu cuenta fue creada. Te enviamos un email con un enlace para verificar tu dirección.
          Revisá tu casilla antes de ingresar.
        </p>
        <p><a routerLink="/auth/login">Ir a ingresar</a></p>
        <p><a routerLink="/auth/reenviar-verificacion">No recibí el email</a></p>
      } @else {
        <form (ngSubmit)="onSubmit()">
          <label>
            Nombre
            <input name="nombre" [(ngModel)]="nombreUsuario" required />
          </label>
          <label>
            Apellido
            <input name="apellido" [(ngModel)]="apellidoUsuario" required />
          </label>
          <label>
            Email
            <input type="email" name="email" [(ngModel)]="email" required />
          </label>
          <label>
            Contraseña
            <input type="password" name="password" [(ngModel)]="password" required />
          </label>
          <small>Mínimo 8 caracteres, con al menos una mayúscula y un número.</small>
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          <button type="submit" [disabled]="loading()">Crear cuenta</button>
        </form>
        <p><a routerLink="/auth/login">Ya tengo cuenta</a></p>
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
      small {
        color: #6b7280;
      }
      .error {
        color: #b91c1c;
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
export class RegistroComponent {
  private readonly api = inject(ReformaApiService);

  nombreUsuario = '';
  apellidoUsuario = '';
  email = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly registrado = signal(false);

  onSubmit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .registrar({
        email: this.email,
        password: this.password,
        nombreUsuario: this.nombreUsuario,
        apellidoUsuario: this.apellidoUsuario,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.registrado.set(true);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(mensajeErrorHttp(err, 'No se pudo crear la cuenta'));
        },
      });
  }
}
