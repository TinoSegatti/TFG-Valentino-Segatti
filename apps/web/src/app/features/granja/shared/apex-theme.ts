/**
 * Tema base compartido para los gráficos ApexCharts (ng-apexcharts) de REFORMA.
 *
 * Centraliza la paleta, tipografía y colores de ejes/grilla/tooltip acordes al
 * estilo glassmorphism oscuro, para que todos los gráficos de los módulos y del
 * panel principal compartan una identidad visual coherente. Cada gráfico parte de
 * estas constantes y solo sobreescribe lo específico (tipo, series, formatters).
 */
import {
  ApexChart,
  ApexGrid,
  ApexLegend,
  ApexTooltip,
  ApexDataLabels,
  ApexStroke,
  ApexFill,
} from 'ng-apexcharts';

/** Paleta de marca (violeta/cian/rosa) usada por todas las series. Ver DESIGN.md. */
export const REFORMA_CHART_PALETTE: string[] = [
  '#9d77f4', // violeta marca
  '#06b6d4', // cian
  '#f472b6', // rosa
  '#8b5cf6', // violeta oscuro
  '#ec4899', // magenta
  '#22d3ee', // cian claro
  '#a78bfa', // lavanda
  '#f0abfc', // orquídea
];

const FONT_FAMILY = "'Hanken Grotesk', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif";
const FORE_COLOR = '#9aa7b8';
const GRID_COLOR = 'rgba(255, 255, 255, 0.08)';

/** Configuración base de `chart` (fondo transparente sobre la tarjeta glass). */
export function reformaApexChart(partial: Partial<ApexChart> = {}): ApexChart {
  return {
    type: 'line',
    height: 300,
    fontFamily: FONT_FAMILY,
    foreColor: FORE_COLOR,
    background: 'transparent',
    toolbar: { show: false },
    zoom: { enabled: false },
    animations: { enabled: true, speed: 500 },
    ...partial,
  };
}

export const reformaApexGrid: ApexGrid = {
  borderColor: GRID_COLOR,
  strokeDashArray: 4,
  padding: { left: 8, right: 8 },
};

export const reformaApexTooltip: ApexTooltip = {
  theme: 'dark',
  style: { fontFamily: FONT_FAMILY },
};

export const reformaApexLegend: ApexLegend = {
  position: 'bottom',
  horizontalAlign: 'center',
  labels: { colors: FORE_COLOR },
  markers: { strokeWidth: 0 },
};

export const reformaApexDataLabels: ApexDataLabels = {
  enabled: false,
};

export const reformaApexStroke: ApexStroke = {
  curve: 'smooth',
  width: 3,
};

/** Relleno tipo "gradiente suave" para áreas/barras. */
export const reformaApexFillGradient: ApexFill = {
  type: 'gradient',
  gradient: {
    shadeIntensity: 1,
    opacityFrom: 0.45,
    opacityTo: 0.05,
    stops: [0, 90, 100],
  },
};
