import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { mensajeErrorHttp } from '../../../core/http/api-error.util';

type Estado = 'verificando' | 'ok' | 'error';

@Component({
  selector: 'app-verificar-email',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="auth">
      <h1>Verificación de email</h1>
      @switch (estado()) {
        @case ('verificando') {
          <p>Verificando tu dirección…</p>
        }
        @case ('ok') {
          <p class="notice">¡Listo! Tu email fue verificado. Ya podés ingresar.</p>
          <p><a routerLink="/auth/login">Ir a ingresar</a></p>
        }
        @case ('error') {
          <p class="error">{{ error() }}</p>
          <p><a routerLink="/auth/reenviar-verificacion">Reenviar verificación</a></p>
        }
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
      .error {
        color: #b91c1c;
      }
      .notice {
        color: #065f46;
        background: #d1fae5;
        padding: 0.5rem 0.75rem;
        border-radius: 4px;
      }
    `,
  ],
})
export class VerificarEmailComponent implements OnInit {
  private readonly api = inject(ReformaApiService);
  private readonly route = inject(ActivatedRoute);

  readonly estado = signal<Estado>('verificando');
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.estado.set('error');
      this.error.set('Enlace inválido: falta el token de verificación.');
      return;
    }
    this.api.verificarEmail(token).subscribe({
      next: () => this.estado.set('ok'),
      error: (err: HttpErrorResponse) => {
        this.estado.set('error');
        this.error.set(
          mensajeErrorHttp(err, 'No se pudo verificar el email. El enlace puede haber expirado.'),
        );
      },
    });
  }
}
