import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';

@Component({
  selector: 'app-restablecer',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="auth">
      <h1>Nueva contraseña</h1>
      @if (!token()) {
        <p class="error">Enlace inválido: falta el token. Solicitá uno nuevo.</p>
        <p><a routerLink="/auth/olvide-password">Recuperar contraseña</a></p>
      } @else {
        <form (ngSubmit)="onSubmit()">
          <label>
            Nueva contraseña
            <input type="password" name="password" [(ngModel)]="password" required />
          </label>
          <label>
            Repetir contraseña
            <input type="password" name="confirmar" [(ngModel)]="confirmar" required />
          </label>
          <small>Mínimo 8 caracteres, con al menos una mayúscula y un número.</small>
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          <button type="submit" [disabled]="loading()">Guardar contraseña</button>
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
      small {
        color: #6b7280;
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
export class RestablecerComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  password = '';
  confirmar = '';
  readonly token = signal<string | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token'));
  }

  onSubmit(): void {
    const token = this.token();
    if (!token) {
      return;
    }
    if (this.password !== this.confirmar) {
      this.error.set('Las contraseñas no coinciden.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.confirmarReset(token, this.password).subscribe({
      next: () => this.router.navigate(['/auth/login'], { queryParams: { reset: 'ok' } }),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          mensajeErrorHttp(err, 'No se pudo restablecer la contraseña. El enlace puede haber expirado.'),
        );
      },
    });
  }
}
