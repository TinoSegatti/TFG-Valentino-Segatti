import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';

import { ReformaAura } from './core/theme/reforma-preset';
import { routes } from './app.routes';
import { authErrorInterceptor } from './core/auth/auth-error.interceptor';
import { jwtAuthInterceptor } from './core/auth/jwt-auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([jwtAuthInterceptor, authErrorInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: ReformaAura,
        options: {
          // Modo oscuro permanente vía clase .app-dark en <html>.
          darkModeSelector: '.app-dark',
          // La capa primeng se ubica entre base y utilities para que las
          // utilidades de Tailwind puedan sobreescribir a los componentes.
          cssLayer: {
            name: 'primeng',
            order: 'theme, base, primeng, utilities',
          },
        },
      },
    }),
  ],
};
