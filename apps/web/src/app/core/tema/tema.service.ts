import { Injectable, inject, signal } from '@angular/core';
import { ReformaApiService } from '../../data/api/reforma-api.service';
import { AuthStateService } from '../auth/auth-state.service';
import {
  CORTINA_MAX,
  CORTINA_MIN,
  FONDO_PERSONALIZADO,
  PREFERENCIAS_DEFAULT,
  PreferenciasUi,
} from '../../data/models/preferencias.model';
import { Observable, tap } from 'rxjs';

/** Un fondo de la galería curada (RD-C2: claves fijas, nunca URLs libres). */
export interface FondoTema {
  clave: string;
  etiqueta: string;
  /** Valor CSS que toma --app-fondo (color plano o gradiente; sin recursos externos). */
  css: string;
}

/**
 * Galería curada de fondos. Las claves deben coincidir con FONDOS_VALIDOS de
 * PreferenciasUiService (backend). 'default' = tema oscuro original (sin capa extra).
 */
export const FONDOS_TEMA: FondoTema[] = [
  { clave: 'default', etiqueta: 'Por defecto', css: 'none' },
  { clave: 'color-grafito', etiqueta: 'Grafito', css: '#14161c' },
  { clave: 'color-bosque', etiqueta: 'Bosque', css: '#0d1f17' },
  { clave: 'color-oceano', etiqueta: 'Océano', css: '#0b1b2b' },
  { clave: 'color-vino', etiqueta: 'Vino', css: '#23101b' },
  {
    clave: 'escena-amanecer',
    etiqueta: 'Amanecer',
    css: 'linear-gradient(180deg, #2b1055 0%, #7a3b69 45%, #c96f4a 80%, #e8a87c 100%)',
  },
  {
    clave: 'escena-campo',
    etiqueta: 'Campo',
    css: 'linear-gradient(180deg, #0f2027 0%, #203a43 40%, #2c5364 70%, #3f6b4f 100%)',
  },
  {
    clave: 'escena-noche',
    etiqueta: 'Noche estrellada',
    css: 'radial-gradient(1200px 800px at 70% -10%, #1e3a5f 0%, #0b1023 55%, #05070f 100%)',
  },
  {
    clave: 'escena-niebla',
    etiqueta: 'Niebla',
    css: 'linear-gradient(180deg, #232526 0%, #414345 60%, #6b7280 100%)',
  },
];

const PREFS_KEY = 'reforma_prefs';

/**
 * Tema visual por usuario (módulo Personalización): fondo de plataforma + cortina de
 * contraste, aplicados como CSS custom properties en el elemento raíz (RD-C4:
 * --app-fondo / --app-cortina, consumidas por las capas .app-bg-* de app.component).
 *
 * Flujo (RD-C1): al bootstrap se aplica el cache de localStorage (sin flash) y, si hay
 * sesión, se refresca desde la API. El logout limpia el cache (auth-state.service).
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly api = inject(ReformaApiService);
  private readonly auth = inject(AuthStateService);

  /** Preferencias guardadas vigentes (lo que está persistido, no la preview). */
  readonly prefs = signal<PreferenciasUi>(PREFERENCIAS_DEFAULT);

  /** Aplica el cache local y, con sesión activa, sincroniza desde la API. */
  inicializar(): void {
    this.aplicar(this.leerCache());
    if (this.auth.isAuthenticated()) {
      this.api.getPreferenciasUi().subscribe({
        next: (prefs) => {
          this.escribirCache(prefs);
          this.aplicar(prefs);
        },
        // Sin red o token vencido: se queda con el cache (la personalización no es crítica).
        error: () => undefined,
      });
    }
  }

  /** Persiste en el backend, cachea y aplica. */
  guardar(prefs: PreferenciasUi): Observable<PreferenciasUi> {
    return this.api.putPreferenciasUi(prefs).pipe(
      tap((guardadas) => {
        this.escribirCache(guardadas);
        this.aplicar(guardadas);
      }),
    );
  }

  /** Aplica sin persistir (preview en vivo de la galería/slider). */
  previsualizar(prefs: PreferenciasUi): void {
    this.setPropiedades(prefs);
  }

  /** Vuelve a la preferencia guardada (descarta la preview). */
  descartarPreview(): void {
    this.setPropiedades(this.prefs());
  }

  private aplicar(prefs: PreferenciasUi): void {
    this.prefs.set(prefs);
    this.setPropiedades(prefs);
  }

  private setPropiedades(prefs: PreferenciasUi): void {
    const raiz = document.documentElement;
    // Imagen del usuario: el data URL va directo a --app-fondo (base64: sin comillas raras).
    // La cortina se aplica igual que con la galería — es la garantía de legibilidad (RD-C3).
    const css =
      prefs.fondo === FONDO_PERSONALIZADO && prefs.imagenPersonalizada
        ? `url("${prefs.imagenPersonalizada}")`
        : FONDOS_TEMA.find((f) => f.clave === prefs.fondo && f.clave !== 'default')?.css;
    if (!css) {
      raiz.style.removeProperty('--app-fondo');
      raiz.style.removeProperty('--app-cortina');
      return;
    }
    const alfa = Math.min(CORTINA_MAX, Math.max(CORTINA_MIN, prefs.intensidadCortina));
    raiz.style.setProperty('--app-fondo', css);
    raiz.style.setProperty('--app-cortina', String(alfa));
  }

  private leerCache(): PreferenciasUi {
    try {
      const crudo = localStorage.getItem(PREFS_KEY);
      if (!crudo) return PREFERENCIAS_DEFAULT;
      const prefs = JSON.parse(crudo) as PreferenciasUi;
      return typeof prefs.fondo === 'string' && typeof prefs.intensidadCortina === 'number'
        ? prefs
        : PREFERENCIAS_DEFAULT;
    } catch {
      return PREFERENCIAS_DEFAULT;
    }
  }

  private escribirCache(prefs: PreferenciasUi): void {
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
  }
}
