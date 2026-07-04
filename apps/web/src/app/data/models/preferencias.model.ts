/**
 * Preferencias de UI del usuario (módulo Personalización) — espejo de
 * PreferenciasUiRequest/Response del backend. `fondo` es una clave de la galería
 * curada (ver FONDOS_TEMA en core/tema/tema.service.ts) o `'personalizada'`, en cuyo
 * caso `imagenPersonalizada` trae la imagen del usuario como data URL (comprimida
 * antes de subir; tope ~1 MB en el backend). `intensidadCortina` es el alfa de la
 * cortina de contraste en [0.35, 0.85]: se aplica SIEMPRE sobre el fondo, así una
 * imagen muy clara no rompe la legibilidad del texto.
 */
export interface PreferenciasUi {
  fondo: string;
  intensidadCortina: number;
  imagenPersonalizada?: string | null;
}

/** Clave del fondo cuyo valor real viaja en `imagenPersonalizada`. */
export const FONDO_PERSONALIZADO = 'personalizada';

/** Tope del data URL (~1 MB de imagen binaria); coincide con el backend. */
export const IMAGEN_MAX_CHARS = 1_400_000;

export const CORTINA_MIN = 0.35;
export const CORTINA_MAX = 0.85;

export const PREFERENCIAS_DEFAULT: PreferenciasUi = {
  fondo: 'default',
  intensidadCortina: 0.55,
};
