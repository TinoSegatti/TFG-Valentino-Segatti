import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';

/**
 * Preset de PrimeNG (Aura) tematizado a la identidad de REFORMA:
 * primario violeta (#9d77f4) y superficies oscuras tipo slate acordes al fondo
 * glassmorphism (#0a0a0f). Ver DESIGN.md para la paleta y el racional.
 *
 * Solo se overridean tokens semánticos clave; el resto se hereda de Aura.
 */
export const ReformaAura = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#f5f3ff',
      100: '#ede9fe',
      200: '#ddd6fe',
      300: '#c4b5fd',
      400: '#a78bfa',
      500: '#9d77f4', // violeta de marca
      600: '#8b5cf6',
      700: '#7c3aed',
      800: '#6d28d9',
      900: '#5b21b6',
      950: '#2e1065',
    },
    colorScheme: {
      dark: {
        primary: {
          color: '{primary.500}',
          contrastColor: '#0a0a0f',
          hoverColor: '{primary.400}',
          activeColor: '{primary.300}',
        },
        highlight: {
          background: 'rgba(157, 119, 244, 0.16)',
          focusBackground: 'rgba(157, 119, 244, 0.24)',
          color: '#ede9fe',
          focusColor: '#ffffff',
        },
        // Ramp de superficies (slate oscuro; 950 = fondo de la app).
        surface: {
          0: '#ffffff',
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
          950: '#0a0a0f',
        },
      },
    },
  },
});
