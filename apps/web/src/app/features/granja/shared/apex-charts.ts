/**
 * Factories de configuración para los gráficos ApexCharts (ng-apexcharts) de
 * REFORMA. Construyen objetos de opciones completos y coherentes con el estilo
 * glassmorphism del handoff (barras con degradado acento, donuts con total al
 * centro, líneas/áreas que se "dibujan"), partiendo del tema base de
 * `apex-theme.ts`. Cada pantalla llama a una factory y le pasa series/labels.
 *
 * Uso típico en un componente:
 *   protected readonly chart = computed(() =>
 *     barChart({ categories: this.cats(), series: [{ name: 'Precio', data: this.vals() }] }));
 *   <apx-chart [series]="chart().series" [chart]="chart().chart" ... />
 *
 * Para simplificar el binding, se expone un objeto plano con todas las claves
 * que `<apx-chart>` acepta como inputs.
 */
import {
  ApexAnnotations,
  ApexAxisChartSeries,
  ApexNonAxisChartSeries,
  ApexChart,
  ApexXAxis,
  ApexYAxis,
  ApexDataLabels,
  ApexStroke,
  ApexFill,
  ApexGrid,
  ApexLegend,
  ApexTooltip,
  ApexPlotOptions,
  ApexResponsive,
} from 'ng-apexcharts';
import {
  REFORMA_CHART_PALETTE,
  reformaApexChart,
  reformaApexGrid,
  reformaApexLegend,
  reformaApexTooltip,
  reformaApexDataLabels,
  reformaApexStroke,
  reformaApexFillGradient,
} from './apex-theme';

/** Conjunto de inputs que se enlazan a <apx-chart>. */
export interface ReformaChartOptions {
  series: ApexAxisChartSeries | ApexNonAxisChartSeries;
  chart: ApexChart;
  xaxis?: ApexXAxis;
  yaxis?: ApexYAxis;
  labels?: string[];
  colors?: string[];
  dataLabels?: ApexDataLabels;
  stroke?: ApexStroke;
  fill?: ApexFill;
  grid?: ApexGrid;
  legend?: ApexLegend;
  tooltip?: ApexTooltip;
  plotOptions?: ApexPlotOptions;
  responsive?: ApexResponsive[];
  annotations?: ApexAnnotations;
}

const AXIS_LABEL_STYLE = { colors: '#9aa7b8', fontSize: '11px' };

function moneyFormatter(v: number): string {
  return '$ ' + Math.round(v).toLocaleString('en-US');
}

/**
 * Construye el tooltip SIN dejar `y: undefined`. Setear `tooltip.y = undefined`
 * hace que ApexCharts después lea `tooltip.y.formatter` y lance
 * "Cannot read properties of undefined (reading 'formatter')". La clave `y` solo
 * debe existir cuando hay formatter.
 *
 * `tooltipTitles` (opcional, paralelo a las categorías del eje X) reemplaza el
 * título del tooltip por un texto más descriptivo: así el eje puede mostrar el
 * código corto de cada columna y al pasar por encima se ve el nombre completo.
 */
function buildTooltip(money?: boolean, tooltipTitles?: string[]): ApexTooltip {
  const t: ApexTooltip = { ...reformaApexTooltip };
  if (money) {
    t.y = { formatter: moneyFormatter };
  }
  if (tooltipTitles) {
    t.x = {
      formatter: (val: number, opts?: { dataPointIndex?: number }): string => {
        const i = opts?.dataPointIndex;
        return (i != null ? tooltipTitles[i] : undefined) ?? String(val);
      },
    };
  }
  return t;
}

/** Barras verticales (con degradado acento → cian, animación de crecimiento). */
export function barChart(opts: {
  categories: string[];
  series: ApexAxisChartSeries;
  height?: number;
  horizontal?: boolean;
  distributed?: boolean;
  money?: boolean;
  colors?: string[];
  /** Títulos alternativos para el tooltip, paralelos a `categories` (eje = código, hover = nombre). */
  tooltipTitles?: string[];
}): ReformaChartOptions {
  return {
    series: opts.series,
    chart: reformaApexChart({ type: 'bar', height: opts.height ?? 300 }),
    colors: opts.colors ?? REFORMA_CHART_PALETTE,
    plotOptions: {
      bar: {
        horizontal: opts.horizontal ?? false,
        distributed: opts.distributed ?? false,
        borderRadius: 6,
        borderRadiusApplication: 'end',
        columnWidth: '55%',
        barHeight: '65%',
      },
    },
    dataLabels: reformaApexDataLabels,
    stroke: { show: false } as ApexStroke,
    fill: {
      type: 'gradient',
      gradient: {
        shade: 'dark',
        type: (opts.horizontal ? 'horizontal' : 'vertical') as 'horizontal' | 'vertical',
        opacityFrom: 0.95,
        opacityTo: 0.55,
        stops: [0, 100],
      },
    },
    grid: reformaApexGrid,
    legend: { ...reformaApexLegend, show: !opts.distributed && opts.series.length > 1 },
    tooltip: buildTooltip(opts.money, opts.tooltipTitles),
    xaxis: {
      categories: opts.categories,
      labels: { style: AXIS_LABEL_STYLE, rotate: -35, trim: true, hideOverlappingLabels: false },
      axisBorder: { show: false },
      axisTicks: { show: false },
    },
    yaxis: {
      labels: {
        style: AXIS_LABEL_STYLE,
        formatter: (v: number) => (opts.money ? moneyFormatter(v) : Math.round(v).toLocaleString('en-US')),
      },
    },
  };
}

/** Donut con total al centro + leyenda (segmentos que se dibujan). */
export function donutChart(opts: {
  labels: string[];
  series: number[];
  height?: number;
  totalLabel?: string;
  totalFormatter?: (total: number) => string;
  colors?: string[];
  money?: boolean;
}): ReformaChartOptions {
  return {
    series: opts.series,
    chart: reformaApexChart({ type: 'donut', height: opts.height ?? 300 }),
    labels: opts.labels,
    colors: opts.colors ?? REFORMA_CHART_PALETTE,
    dataLabels: { enabled: true, style: { fontSize: '11px' }, formatter: (val: number) => `${Math.round(val)}%` },
    stroke: { width: 0 } as ApexStroke,
    legend: { ...reformaApexLegend, position: 'bottom' },
    tooltip: buildTooltip(opts.money),
    plotOptions: {
      pie: {
        donut: {
          size: '68%',
          labels: {
            show: true,
            total: {
              show: true,
              label: opts.totalLabel ?? 'Total',
              color: '#9aa7b8',
              fontSize: '12px',
              formatter: (w: { globals: { seriesTotals: number[] } }) => {
                const total = w.globals.seriesTotals.reduce((a: number, b: number) => a + b, 0);
                return opts.totalFormatter ? opts.totalFormatter(total) : Math.round(total).toLocaleString('en-US');
              },
            },
            value: { color: '#f1f5f9', fontFamily: "'Space Grotesk', sans-serif", fontSize: '20px' },
          },
        },
      },
    },
  };
}

/** Línea (multi-serie) — para evoluciones temporales. */
export function lineChart(opts: {
  categories: string[];
  series: ApexAxisChartSeries;
  height?: number;
  money?: boolean;
}): ReformaChartOptions {
  return {
    series: opts.series,
    chart: reformaApexChart({ type: 'line', height: opts.height ?? 300 }),
    colors: REFORMA_CHART_PALETTE,
    dataLabels: reformaApexDataLabels,
    stroke: reformaApexStroke,
    grid: reformaApexGrid,
    legend: reformaApexLegend,
    tooltip: buildTooltip(opts.money),
    xaxis: {
      categories: opts.categories,
      labels: { style: AXIS_LABEL_STYLE },
      axisBorder: { show: false },
      axisTicks: { show: false },
    },
    yaxis: {
      labels: {
        style: AXIS_LABEL_STYLE,
        formatter: (v: number) => (opts.money ? moneyFormatter(v) : Math.round(v).toLocaleString('en-US')),
      },
    },
  };
}

/** Área con relleno degradado (la línea de ingresos del handoff). */
export function areaChart(opts: {
  categories: string[];
  series: ApexAxisChartSeries;
  height?: number;
  money?: boolean;
}): ReformaChartOptions {
  return {
    ...lineChart(opts),
    chart: reformaApexChart({ type: 'area', height: opts.height ?? 300 }),
    fill: reformaApexFillGradient,
  };
}

/** Sparkline minimalista para el pie de una KPI card. */
export function sparkline(data: number[], color = REFORMA_CHART_PALETTE[0]): ReformaChartOptions {
  return {
    series: [{ name: '', data }],
    chart: reformaApexChart({ type: 'area', height: 48, sparkline: { enabled: true }, animations: { enabled: true, speed: 900 } }),
    colors: [color],
    stroke: { curve: 'smooth', width: 2 },
    fill: { type: 'gradient', gradient: { opacityFrom: 0.4, opacityTo: 0.0, stops: [0, 100] } },
    tooltip: { enabled: false } as ApexTooltip,
    dataLabels: { enabled: false },
  };
}
