# ADR 0007 — Stack de UI (glassmorphism) y gráficos

## Estado

Aceptado — 2026-06-18

## Contexto

El frontend (Angular 19 standalone) usaba estilos inline ad-hoc por componente y no
tenía librería de gráficos. La etapa de dashboards e IA (HU-019/020/021) requiere
visualizaciones por módulo y un panel principal coherentes con una identidad visual
moderna. Se decidió adoptar un estilo **glassmorphism en modo oscuro** y unificar el
sistema de componentes y de gráficos.

## Decisión

- **PrimeNG 19** con preset **Aura** (`@primeng/themes/aura`) en **modo oscuro permanente**
  vía `darkModeSelector: '.app-dark'` (clase fija en `<html>`).
- **Tailwind CSS v4** con `@tailwindcss/postcss` (config en `.postcssrc.json`) y el plugin
  oficial **`tailwindcss-primeui`**. `cssLayer` activado (`order: 'theme, base, primeng, utilities'`)
  para que las utilidades de Tailwind y las clases globales puedan sobreescribir a los
  componentes PrimeNG.
- **ApexCharts** vía **`ng-apexcharts`** para todos los gráficos. Fijado a `~1.15.0`
  (1.16+ exige Angular 20).
- **Sistema de diseño glass** centralizado en `src/styles.css` (tokens `--glass-*`/`--reforma-*`,
  clases `.glass-card`/`.glass-surface`/`.reforma-input`/`.reforma-btn`/`.reforma-alert`).
- **Utilidades de gráficos compartidas** en `features/granja/shared/`:
  `apex-theme.ts` (paleta, ejes, tooltip, grilla) y `reforma-chart-card` (tarjeta glass
  con título/estado de carga/"sin datos").

## Consecuencias

- `@primeng/themes` figura como *deprecated* (su sucesor `@primeuix/themes` es para PrimeNG v20);
  es el path correcto y funcional para v19. Migrar al actualizar a PrimeNG 20.
- El modo oscuro global afecta a toda la app: las pantallas se migran al sistema glass de
  forma incremental (primero shell/landing/login/mis-plantas; luego catálogos, compras,
  fórmulas, fabricaciones, inventario y el panel principal).
- `provideAnimationsAsync()` se agrega para los componentes PrimeNG.

## Alternativas consideradas

- **Chart.js (ng2-charts):** más simple pero menos potente para series de predicción y
  densidad tipo dashboard. Descartado frente a ApexCharts.
- **Aislar el tema oscuro solo a los gráficos** (sin restyle global): produciría un estado
  visual incoherente (gráficos oscuros sobre pantallas claras). Descartado a favor del
  restyle global.
