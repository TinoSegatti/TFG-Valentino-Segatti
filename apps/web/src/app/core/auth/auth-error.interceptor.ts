import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthStateService } from './auth-state.service';

/**
 * Solo redirige al login ante 401 (token ausente, invalido o expirado).
 * Los 403 de negocio (limite de plan, sin acceso a granja, etc.) se muestran en pantalla.
 */
export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStateService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse)) {
        return throwError(() => err);
      }
      const isLogin = req.url.includes('/api/usuarios/login');
      const hadAuth = !!auth.getToken();
      if (!isLogin && hadAuth && err.status === 401) {
        auth.clearSession();
        router.navigate(['/auth/login'], {
          queryParams: { sesion: 'expirada' },
        });
      }
      return throwError(() => err);
    }),
  );
};
