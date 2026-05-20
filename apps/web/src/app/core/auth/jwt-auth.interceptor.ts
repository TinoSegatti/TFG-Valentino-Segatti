import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStateService } from './auth-state.service';

export const jwtAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStateService);
  const header = auth.getAuthorizationHeader();
  if (header) {
    req = req.clone({ setHeaders: { Authorization: header } });
  }
  return next(req);
};
